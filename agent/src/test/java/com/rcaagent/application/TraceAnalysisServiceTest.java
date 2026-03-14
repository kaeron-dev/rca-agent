package com.rcaagent.application;

import com.rcaagent.domain.*;
import com.rcaagent.domain.exception.TraceNotFoundException;
import com.rcaagent.ports.out.MetricsRepository;
import com.rcaagent.ports.out.RcaAnalyzer;
import com.rcaagent.ports.out.TraceRepository;
import com.rcaagent.ports.out.RcaReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceAnalysisServiceTest {

    @Mock private TraceRepository traceRepository;
    @Mock private MetricsRepository metricsRepository;
    @Mock private RcaAnalyzer rcaAnalyzer;
    @Mock private RcaReportRepository rcaReportRepository;

    private TraceAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new TraceAnalysisService(traceRepository, metricsRepository, rcaAnalyzer, rcaReportRepository);
    }

    private Span span(String spanId, String parent, String operation, String svc, long durationMs) {
        return new Span(spanId, parent, operation, svc, durationMs, SpanStatus.OK);
    }

    private RcaReport stubReport(String traceId, String operation, long durationMs, long baselineMs, double factor, double confidence) {
        return new RcaReport(traceId, "root cause", operation, durationMs, baselineMs, factor, "recommendation", confidence);
    }

    @Test
    @DisplayName("cached high-confidence report → returns cached report, skips analysis")
    void analyze_cachedReport_returnsImmediate() {
        var traceId = "trace-cached";
        var cached = stubReport(traceId, "op", 100, 50, 2.0, 0.95);

        when(rcaReportRepository.findByTraceId(traceId)).thenReturn(Optional.of(cached));

        var result = service.analyze(traceId);

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(traceRepository, metricsRepository, rcaAnalyzer);
    }

    @Test
    @DisplayName("fresh analysis → saves result to repository")
    void analyze_freshAnalysis_savesToRepo() {
        var traceId  = "trace-new";
        var root     = span("s1", null, "POST /orders", "order-service", 120L);
        var tree     = new SpanTree(traceId, List.of(root));
        var report   = stubReport(traceId, "POST /orders", 120L, 100L, 1.2, 0.8);

        when(rcaReportRepository.findByTraceId(traceId)).thenReturn(Optional.empty());
        when(traceRepository.findByTraceId(traceId)).thenReturn(tree);
        when(metricsRepository.getBaseline(anyString())).thenReturn(100L);
        when(rcaAnalyzer.analyze(any(TraceContext.class))).thenReturn(report);

        service.analyze(traceId);

        verify(rcaReportRepository).save(report);
    }

    @Test
    @DisplayName("anomalous trace → context contains correct span")
    void analyze_anomalousTrace_contextContainsCorrectSpan() {
        var traceId   = "trace-anomaly";
        var root      = span("s1", null, "POST /orders",   "order-service",    100L);
        var dbQuery   = span("s2", "s1", "db.query",       "payment-service",  4750L);
        var tree      = new SpanTree(traceId, List.of(root, dbQuery));
        var expected  = stubReport(traceId, "db.query", 4750L, 54L, 87.9, 0.9);

        when(rcaReportRepository.findByTraceId(traceId)).thenReturn(Optional.empty());
        when(traceRepository.findByTraceId(traceId)).thenReturn(tree);
        when(metricsRepository.getBaseline(anyString())).thenReturn(54L);

        var captor = ArgumentCaptor.forClass(TraceContext.class);
        when(rcaAnalyzer.analyze(captor.capture())).thenReturn(expected);

        service.analyze(traceId);

        var ctx = captor.getValue();
        assertThat(ctx.anomalySpan().operationName()).isEqualTo("db.query");
    }

    @Test
    @DisplayName("blank traceId → IllegalArgumentException")
    void analyze_blankTraceId_throws() {
        assertThatThrownBy(() -> service.analyze(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TraceRepository throws TraceNotFoundException → propagates")
    void analyze_traceNotFound_propagates() {
        var traceId = "nonexistent";
        when(rcaReportRepository.findByTraceId(traceId)).thenReturn(Optional.empty());
        when(traceRepository.findByTraceId(traceId)).thenThrow(new TraceNotFoundException(traceId));

        assertThatThrownBy(() -> service.analyze(traceId))
            .isInstanceOf(TraceNotFoundException.class);
    }
}

package com.rcaagent.application;

import com.rcaagent.domain.*;
import com.rcaagent.domain.exception.TraceNotFoundException;
import com.rcaagent.ports.out.MetricsRepository;
import com.rcaagent.ports.out.RcaAnalyzer;
import com.rcaagent.ports.out.TraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceAnalysisServiceTest {

    @Mock private TraceRepository traceRepository;
    @Mock private MetricsRepository metricsRepository;
    @Mock private RcaAnalyzer rcaAnalyzer;

    private TraceAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new TraceAnalysisService(traceRepository, metricsRepository, rcaAnalyzer);
    }

    private Span span(String spanId, String parent, String operation, String svc, long durationMs) {
        return new Span(spanId, parent, operation, svc, durationMs, SpanStatus.OK);
    }

    private RcaReport stubReport(String traceId, String operation, long durationMs, long baselineMs, double factor) {
        return new RcaReport(traceId, "root cause", operation, durationMs, baselineMs, factor, "recommendation", 0.9);
    }

    @Test
    @DisplayName("normal trace → returns RcaReport from analyzer")
    void analyze_normalTrace_returnsReport() {
        var traceId  = "trace-001";
        var root     = span("s1", null, "POST /orders",   "order-service",  120L);
        var payment  = span("s2", "s1", "POST /payments", "payment-service", 80L);
        var tree     = new SpanTree(traceId, List.of(root, payment));
        var expected = stubReport(traceId, "POST /orders", 120L, 100L, 1.2);

        when(traceRepository.findByTraceId(traceId)).thenReturn(tree);
        when(metricsRepository.getBaseline("order-service")).thenReturn(100L);
        when(rcaAnalyzer.analyze(any(TraceContext.class))).thenReturn(expected);

        var result = service.analyze(traceId);

        assertThat(result).isEqualTo(expected);
        verify(traceRepository).findByTraceId(traceId);
        verify(metricsRepository).getBaseline("order-service");
        verify(rcaAnalyzer).analyze(any(TraceContext.class));
    }

    @Test
    @DisplayName("anomalous trace → slowest span is db.query, context reflects correct anomaly")
    void analyze_anomalousTrace_contextContainsCorrectSpan() {
        var traceId   = "trace-anomaly";
        // db.query es el más lento — root span tiene duración menor
        var root      = span("s1", null, "POST /orders",   "order-service",    100L);
        var dbQuery   = span("s2", "s1", "db.query",       "payment-service",  4750L);
        var inventory = span("s3", "s1", "POST /reserve",  "inventory-service",  35L);
        var tree      = new SpanTree(traceId, List.of(root, dbQuery, inventory));
        var expected  = stubReport(traceId, "db.query", 4750L, 54L, 87.9);

        when(traceRepository.findByTraceId(traceId)).thenReturn(tree);
        when(metricsRepository.getBaseline("payment-service")).thenReturn(54L);

        var captor = ArgumentCaptor.forClass(TraceContext.class);
        when(rcaAnalyzer.analyze(captor.capture())).thenReturn(expected);

        service.analyze(traceId);

        var ctx = captor.getValue();
        assertThat(ctx.anomalySpan().operationName()).isEqualTo("db.query");
        assertThat(ctx.anomalySpan().serviceName()).isEqualTo("payment-service");
        assertThat(ctx.anomalyFactor()).isGreaterThan(80.0);
    }

    @Test
    @DisplayName("blank traceId → IllegalArgumentException before calling any port")
    void analyze_blankTraceId_throwsBeforeCallingPorts() {
        assertThatThrownBy(() -> service.analyze(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traceId");

        verifyNoInteractions(traceRepository, metricsRepository, rcaAnalyzer);
    }

    @Test
    @DisplayName("null traceId → IllegalArgumentException before calling any port")
    void analyze_nullTraceId_throwsBeforeCallingPorts() {
        assertThatThrownBy(() -> service.analyze(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traceId");

        verifyNoInteractions(traceRepository, metricsRepository, rcaAnalyzer);
    }

    @Test
    @DisplayName("TraceRepository throws TraceNotFoundException → exception propagates")
    void analyze_traceNotFound_propagatesException() {
        var traceId = "nonexistent-trace";
        when(traceRepository.findByTraceId(traceId))
            .thenThrow(new TraceNotFoundException(traceId));

        assertThatThrownBy(() -> service.analyze(traceId))
            .isInstanceOf(TraceNotFoundException.class)
            .hasMessageContaining(traceId);

        verifyNoInteractions(metricsRepository, rcaAnalyzer);
    }

    @Test
    @DisplayName("constructor with null port → IllegalArgumentException")
    void constructor_nullPort_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TraceAnalysisService(null, metricsRepository, rcaAnalyzer))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traceRepository");

        assertThatThrownBy(() -> new TraceAnalysisService(traceRepository, null, rcaAnalyzer))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("metricsRepository");

        assertThatThrownBy(() -> new TraceAnalysisService(traceRepository, metricsRepository, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rcaAnalyzer");
    }
}

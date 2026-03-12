package com.rcaagent.adapters.in;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.exception.TraceNotFoundException;
import com.rcaagent.ports.in.AnalyzeTraceUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnalyzeController.
 * Tests HTTP behavior — not business logic (that's TraceAnalysisServiceTest).
 *
 * What to study here:
 *   - @InjectMocks: Mockito creates the controller and injects the mock port
 *   - We verify HTTP status codes, not domain logic
 *   - Controller depends on the port interface — we mock AnalyzeTraceUseCase, not TraceAnalysisService
 */
@ExtendWith(MockitoExtension.class)
class AnalyzeControllerTest {

    @Mock
    private AnalyzeTraceUseCase analyzeTraceUseCase;

    @InjectMocks
    private AnalyzeController controller;

    private RcaReport stubReport(String traceId) {
        return new RcaReport(traceId, "root cause", "db.query", 4750L, 54L, 87.9, "recommendation", 0.94);
    }

    @Test
    @DisplayName("valid traceId → 200 OK with RcaReport")
    void analyze_validTrace_returns200() {
        var traceId  = "trace-001";
        var expected = stubReport(traceId);
        when(analyzeTraceUseCase.analyze(traceId)).thenReturn(expected);

        var response = controller.analyze(traceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(analyzeTraceUseCase).analyze(traceId);
    }

    @Test
    @DisplayName("trace not found → 404 NOT FOUND")
    void analyze_traceNotFound_returns404() {
        var traceId = "nonexistent";
        when(analyzeTraceUseCase.analyze(traceId)).thenThrow(new TraceNotFoundException(traceId));

        var response = controller.analyze(traceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("blank traceId → 400 BAD REQUEST")
    void analyze_blankTraceId_returns400() {
        when(analyzeTraceUseCase.analyze("")).thenThrow(new IllegalArgumentException("traceId must not be blank"));

        var response = controller.analyze("");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET endpoint delegates to analyze")
    void analyzeGet_delegatesToAnalyze() {
        var traceId  = "trace-002";
        var expected = stubReport(traceId);
        when(analyzeTraceUseCase.analyze(traceId)).thenReturn(expected);

        var response = controller.analyzeGet(traceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }
}

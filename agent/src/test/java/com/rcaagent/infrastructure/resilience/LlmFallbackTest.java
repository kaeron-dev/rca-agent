package com.rcaagent.infrastructure.resilience;

import com.rcaagent.adapters.out.llm.LangChain4jRcaAdapter;
import com.rcaagent.adapters.out.llm.ModelSelectionStrategy;
import com.rcaagent.domain.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for LLM resilience patterns — fallback, retry exhaustion, bulkhead.
 *
 * Note: @Retry and @Bulkhead require Spring AOP proxy — bypassed when calling directly.
 * These tests validate the fallback method contract and degraded response shape.
 *
 * What to study here:
 *   - confidence=0.0 signals degraded response to the caller
 *   - Span data is always preserved — partial info is better than nothing
 *   - The system never throws to the controller — always returns something useful
 *   - Each exception type (timeout, connection refused, runtime) triggers fallback correctly
 */
@ExtendWith(MockitoExtension.class)
class LlmFallbackTest {

    @Mock private ModelSelectionStrategy mockStrategy;
    @Mock private ChatLanguageModel mockModel;

    private TraceContext buildContext(String traceId) {
        var span = new Span("s1", null, "db.query", "payment-service", 4750L, SpanStatus.OK);
        var tree = new SpanTree(traceId, List.of(span));
        return new TraceContext(tree, span, 54L);
    }

    // --- Fallback contract ---

    @Test
    @DisplayName("LLM timeout → fallback returns degraded report with confidence 0.0")
    void fallback_llmTimeout_returnsDegradedReport() {
        var context = buildContext("trace-001");
        var adapter = new LangChain4jRcaAdapter(mockStrategy);

        var report = adapter.fallback(context, new RuntimeException("LLM connection timeout"));

        assertThat(report.traceId()).isEqualTo("trace-001");
        assertThat(report.confidence()).isEqualTo(0.0);
        assertThat(report.rootCause()).contains("LLM unavailable");
        assertThat(report.anomalySpan()).isEqualTo("db.query");
        assertThat(report.durationMs()).isEqualTo(4750L);
        assertThat(report.baselineMs()).isEqualTo(54L);
    }

    @Test
    @DisplayName("LLM connection refused → fallback preserves span data for manual investigation")
    void fallback_connectionRefused_preservesSpanData() {
        var context = buildContext("trace-002");
        var adapter = new LangChain4jRcaAdapter(mockStrategy);

        var report = adapter.fallback(context,
                new RuntimeException("Connection refused: http://localhost:11434"));

        assertThat(report.recommendation()).contains("payment-service");
        assertThat(report.anomalyFactor()).isGreaterThan(80.0);
        assertThat(report.confidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("bulkhead full → fallback returns degraded report, never throws")
    void fallback_bulkheadFull_neverThrows() {
        var context = buildContext("trace-003");
        var adapter = new LangChain4jRcaAdapter(mockStrategy);

        var bulkheadException = new RuntimeException("Bulkhead 'llm' is full");

        assertThatCode(() -> adapter.fallback(context, bulkheadException))
                .doesNotThrowAnyException();

        var report = adapter.fallback(context, bulkheadException);
        assertThat(report.confidence()).isEqualTo(0.0);
        assertThat(report.traceId()).isEqualTo("trace-003");
    }

    // --- Retry exhaustion simulation ---

    @Test
    @DisplayName("LLM model fails transiently — retry simulation via mock")
    void retry_transientFailure_modelRetriedCorrectly() {
        var llmResponse = """
                {
                  "rootCause": "Missing index on payment_transactions.user_id",
                  "recommendation": "Add index on user_id — reduces scan from 15k to ~10 rows",
                  "confidence": 0.91
                }
                """;

        when(mockModel.generate(anyString()))
                .thenThrow(new RuntimeException("transient timeout"))
                .thenReturn(llmResponse);

        // First call fails
        assertThatThrownBy(() -> mockModel.generate("prompt"))
                .hasMessage("transient timeout");

        // Second call succeeds — simulates what retry would do
        var result = mockModel.generate("prompt");
        assertThat(result).contains("Missing index");

        verify(mockModel, times(2)).generate(anyString());
    }

    @Test
    @DisplayName("all retries exhausted → fallback called, degraded report returned")
    void retry_allAttemptsExhausted_fallbackCalled() {
        var context = buildContext("trace-005");
        var adapter = new LangChain4jRcaAdapter(mockStrategy);

        var report = adapter.fallback(context,
                new RuntimeException("Max retries (3) exhausted after timeout"));

        assertThat(report.confidence()).isEqualTo(0.0);
        assertThat(report.rootCause()).contains("LLM unavailable");
        assertThat(report.recommendation()).contains("payment-service");
    }

    // --- Degraded response shape ---

    @Test
    @DisplayName("degraded report — anomalyFactor computed correctly from span data")
    void fallback_degradedReport_anomalyFactorCorrect() {
        var context = buildContext("trace-006");
        var adapter = new LangChain4jRcaAdapter(mockStrategy);

        var report = adapter.fallback(context, new RuntimeException("timeout"));

        assertThat(report.anomalyFactor())
                .as("anomalyFactor should reflect span deviation even in degraded mode")
                .isGreaterThan(87.0)
                .isLessThan(89.0);
    }

    @Test
    @DisplayName("degraded report — never returns null fields")
    void fallback_degradedReport_noNullFields() {
        var context = buildContext("trace-007");
        var adapter = new LangChain4jRcaAdapter(mockStrategy);

        var report = adapter.fallback(context, new RuntimeException("timeout"));

        assertThat(report.traceId()).isNotNull().isNotBlank();
        assertThat(report.rootCause()).isNotNull().isNotBlank();
        assertThat(report.anomalySpan()).isNotNull().isNotBlank();
        assertThat(report.recommendation()).isNotNull().isNotBlank();
    }
}

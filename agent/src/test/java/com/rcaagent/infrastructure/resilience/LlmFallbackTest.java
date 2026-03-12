package com.rcaagent.infrastructure.resilience;

import com.rcaagent.adapters.out.llm.LangChain4jRcaAdapter;
import com.rcaagent.adapters.out.llm.ModelSelectionStrategy;
import com.rcaagent.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for LLM fallback behavior.
 *
 * Note: @Retry and @Bulkhead require Spring AOP — bypassed when calling directly.
 * These tests validate the fallback method contract directly.
 *
 * What to study here:
 *   - confidence=0.0 signals degraded response to the caller
 *   - Span data is preserved — partial info is better than nothing
 *   - The system never throws to the controller — always returns something useful
 */
@ExtendWith(MockitoExtension.class)
class LlmFallbackTest {

    @Mock private ModelSelectionStrategy mockStrategy;

    private TraceContext buildContext(String traceId) {
        var span = new Span("s1", null, "db.query", "payment-service", 4750L, SpanStatus.OK);
        var tree = new SpanTree(traceId, List.of(span));
        return new TraceContext(tree, span, 54L);
    }

    @Test
    @DisplayName("LLM unavailable → fallback returns degraded report with confidence 0.0")
    void fallback_llmUnavailable_returnsDegradedReport() {
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
    @DisplayName("degraded report contains span data for manual investigation")
    void fallback_containsSpanDataForManualInvestigation() {
        var context = buildContext("trace-002");
        var adapter = new LangChain4jRcaAdapter(mockStrategy);

        var report = adapter.fallback(context, new RuntimeException("timeout"));

        assertThat(report.recommendation()).contains("payment-service");
        assertThat(report.anomalyFactor()).isGreaterThan(80.0);
    }
}

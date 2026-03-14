package com.rcaagent.infrastructure.resilience;

import com.rcaagent.adapters.out.llm.LangChain4jRcaAdapter;
import com.rcaagent.domain.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for LLM resilience patterns — fallback, retry exhaustion, bulkhead.
 */
@ExtendWith(MockitoExtension.class)
class LlmFallbackTest {

    @Mock private ChatLanguageModel mainModel;
    @Mock private ChatLanguageModel backupModel;

    private TraceContext buildContext(String traceId) {
        var span = new Span("s1", null, "db.query", "payment-service", 4750L, SpanStatus.OK);
        var tree = new SpanTree(traceId, List.of(span));
        return new TraceContext(tree, span, 54L);
    }

    @Test
    @DisplayName("LLM timeout → fallback returns degraded report with confidence 0.0")
    void fallback_llmTimeout_returnsDegradedReport() {
        var context = buildContext("trace-001");
        var adapter = new LangChain4jRcaAdapter(mainModel, backupModel);

        var report = adapter.fallback(context, new RuntimeException("LLM connection timeout"));

        assertThat(report.traceId()).isEqualTo("trace-001");
        assertThat(report.confidence()).isEqualTo(0.0);
        assertThat(report.rootCause()).contains("LLM unavailable");
    }

    @Test
    @DisplayName("LLM connection refused → fallback preserves span data for manual investigation")
    void fallback_connectionRefused_preservesSpanData() {
        var context = buildContext("trace-002");
        var adapter = new LangChain4jRcaAdapter(mainModel, backupModel);

        var report = adapter.fallback(context,
                new RuntimeException("Connection refused: http://localhost:11434"));

        assertThat(report.recommendation()).contains("payment-service");
        assertThat(report.confidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("bulkhead full → fallback returns degraded report, never throws")
    void fallback_bulkheadFull_neverThrows() {
        var context = buildContext("trace-003");
        var adapter = new LangChain4jRcaAdapter(mainModel, backupModel);

        var bulkheadException = new RuntimeException("Bulkhead 'llm' is full");

        assertThatCode(() -> adapter.fallback(context, bulkheadException))
                .doesNotThrowAnyException();

        var report = adapter.fallback(context, bulkheadException);
        assertThat(report.confidence()).isEqualTo(0.0);
        assertThat(report.traceId()).isEqualTo("trace-003");
    }
}

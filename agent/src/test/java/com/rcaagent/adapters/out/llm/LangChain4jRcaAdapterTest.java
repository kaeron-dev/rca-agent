package com.rcaagent.adapters.out.llm;

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
 * Unit tests for LangChain4jRcaAdapter with mocked LLM.
 * No real LLM calls — fast, deterministic, no API keys needed.
 *
 * What to study here:
 *   - We mock ChatLanguageModel — the LLM is just another dependency
 *   - We verify the adapter correctly wires prompt → LLM → parser
 *   - We verify the strategy is called to select the model
 */
@ExtendWith(MockitoExtension.class)
class LangChain4jRcaAdapterTest {

    @Mock private ChatLanguageModel mainModel;
    @Mock private ChatLanguageModel backupModel;

    private TraceContext buildContext(String traceId, long durationMs, long baselineMs) {
        var span = new Span("s1", null, "db.query", "payment-service", durationMs, SpanStatus.OK);
        var tree = new SpanTree(traceId, List.of(span));
        return new TraceContext(tree, span, baselineMs);
    }

    @Test
    @DisplayName("valid main LLM response → RcaReport with correct fields")
    void analyze_validMainLlmResponse_returnsReport() {
        var context = buildContext("trace-001", 4750L, 54L);
        var llmResponse = """
                {
                  "rootCause": "Missing index on payment_transactions.user_id",
                  "recommendation": "Add composite index on user_id and created_at",
                  "confidence": 0.94
                }
                """;

        when(mainModel.generate(anyString())).thenReturn(llmResponse);

        var adapter = new LangChain4jRcaAdapter(mainModel, backupModel);
        var report  = adapter.analyze(context);

        assertThat(report.traceId()).isEqualTo("trace-001");
        assertThat(report.rootCause()).isEqualTo("Missing index on payment_transactions.user_id");
        assertThat(report.confidence()).isEqualTo(0.94);

        verify(mainModel).generate(anyString());
        verifyNoInteractions(backupModel);
    }

    @Test
    @DisplayName("main model fails (e.g. quota) → automatically uses backup model")
    void analyze_mainModelFails_switchesToBackup() {
        var context = buildContext("trace-002", 4750L, 54L);
        var llmResponse = """
                {
                  "rootCause": "Backup analysis",
                  "recommendation": "Backup rec",
                  "confidence": 0.8
                }
                """;

        when(mainModel.generate(anyString())).thenThrow(new RuntimeException("Quota exceeded"));
        when(backupModel.generate(anyString())).thenReturn(llmResponse);

        var adapter = new LangChain4jRcaAdapter(mainModel, backupModel);
        var report  = adapter.analyze(context);

        assertThat(report.traceId()).isEqualTo("trace-002");
        assertThat(report.rootCause()).isEqualTo("Backup analysis");
        assertThat(report.confidence()).isEqualTo(0.8);

        verify(mainModel).generate(anyString());
        verify(backupModel).generate(anyString());
    }

    @Test
    @DisplayName("malformed LLM response → partial report, no exception")
    void analyze_malformedLlmResponse_returnsPartialReport() {
        var context = buildContext("trace-003", 4750L, 54L);

        when(mainModel.generate(anyString())).thenReturn("Sorry, I cannot analyze this trace.");

        var adapter = new LangChain4jRcaAdapter(mainModel, backupModel);
        var report  = adapter.analyze(context);

        assertThat(report.traceId()).isEqualTo("trace-003");
        assertThat(report.confidence()).isEqualTo(0.0);
        assertThat(report.recommendation()).isEqualTo("Manual investigation required");
    }
}

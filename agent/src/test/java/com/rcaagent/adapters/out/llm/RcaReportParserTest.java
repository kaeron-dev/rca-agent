package com.rcaagent.adapters.out.llm;

import com.rcaagent.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for RcaReportParser.
 */
class RcaReportParserTest {

    private TraceContext buildContext(String traceId) {
        var span = new Span("s1", null, "db.query", "payment-service", 4750L, SpanStatus.OK);
        var tree = new SpanTree(traceId, List.of(span));
        return new TraceContext(tree, span, 54L);
    }

    @Test
    @DisplayName("valid JSON response → RcaReport with correct fields")
    void parse_validJson_returnsCorrectReport() {
        var context = buildContext("trace-001");
        var llmResponse = """
                {
                  "rootCause": "Missing index on payment_transactions.user_id",
                  "recommendation": "Add composite index on user_id and created_at columns",
                  "confidence": 0.94,
                  "anomalyType": "DATABASE_SLOW_QUERY"
                }
                """;

        var report = RcaReportParser.parse(llmResponse, context);

        assertThat(report.traceId()).isEqualTo("trace-001");
        assertThat(report.anomalyType()).isEqualTo("DATABASE_SLOW_QUERY");
        assertThat(report.confidence()).isEqualTo(0.94);
    }

    @Test
    @DisplayName("malformed JSON → returns partial report with confidence 0.0, no exception")
    void parse_malformedJson_returnsPartialReport() {
        var context = buildContext("trace-003");

        var report = RcaReportParser.parse("This is not JSON at all", context);

        assertThat(report.traceId()).isEqualTo("trace-003");
        assertThat(report.confidence()).isEqualTo(0.0);
        assertThat(report.rootCause()).contains("Analysis error");
    }

    @Test
    @DisplayName("infer anomalyType from rootCause if UNKNOWN")
    void parse_inferType_whenUnknown() {
        var context = buildContext("trace-005");
        var llmResponse = """
                {
                  "rootCause": "Slow SQL execution detected",
                  "recommendation": "Fix DB",
                  "confidence": 0.8,
                  "anomalyType": "UNKNOWN"
                }
                """;

        var report = RcaReportParser.parse(llmResponse, context);

        assertThat(report.anomalyType()).isEqualTo("DATABASE_SLOW_QUERY");
    }
}

package com.rcaagent.adapters.out;

import com.rcaagent.adapters.out.tempo.SpanTreeMapper;
import com.rcaagent.adapters.out.tempo.TempoSpanResponse;
import com.rcaagent.domain.SpanStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SpanTreeMapper.
 * WebClient HTTP calls tested in integration tests (Phase 3, Issue #38).
 */
class TempoTraceAdapterTest {

    private TempoSpanResponse.TempoSpan buildSpan(String spanId, String parentId,
                                                   String name, String startNano, String endNano, int statusCode) {
        return new TempoSpanResponse.TempoSpan(
                spanId, parentId, name, "SPAN_KIND_SERVER",
                startNano, endNano,
                List.of(),
                new TempoSpanResponse.TempoStatus(statusCode)
        );
    }

    private TempoSpanResponse buildResponse(String traceId, String serviceName,
                                             TempoSpanResponse.TempoSpan... spans) {
        var resource = Map.<String, Object>of(
            "attributes", List.of(
                Map.of("key", "service.name", "value", Map.of("stringValue", serviceName))
            )
        );
        var scopeSpan    = new TempoSpanResponse.TempoScopeSpan(List.of(spans));
        var resourceSpan = new TempoSpanResponse.TempoResourceSpan(List.of(scopeSpan), resource);
        var trace        = new TempoSpanResponse.TempoTrace(List.of(resourceSpan));
        return new TempoSpanResponse(trace);
    }

    @Test
    @DisplayName("maps Tempo response → SpanTree with correct traceId and span count")
    void from_validResponse_mapsCorrectly() {
        // start=0, end=120_000_000 ns → 120ms
        var span = buildSpan("s1", null, "POST /orders", "0", "120000000", 2);
        var response = buildResponse("trace-001", "order-service", span);

        var tree = SpanTreeMapper.from("trace-001", response);

        assertThat(tree.traceId()).isEqualTo("trace-001");
        assertThat(tree.spans()).hasSize(1);
        assertThat(tree.spans().get(0).operationName()).isEqualTo("POST /orders");
        assertThat(tree.spans().get(0).serviceName()).isEqualTo("order-service");
        assertThat(tree.spans().get(0).durationMs()).isEqualTo(120L);
        assertThat(tree.spans().get(0).status()).isEqualTo(SpanStatus.OK);
    }

    @Test
    @DisplayName("maps status code 1 → ERROR")
    void from_statusCode1_mapsToError() {
        // start=0, end=500_000_000 ns → 500ms
        var span = buildSpan("s1", null, "POST /payments", "0", "500000000", 1);
        var response = buildResponse("trace-002", "payment-service", span);

        var tree = SpanTreeMapper.from("trace-002", response);

        assertThat(tree.spans().get(0).status()).isEqualTo(SpanStatus.ERROR);
    }

    @Test
    @DisplayName("slowestSpan returns span with highest durationMs")
    void slowestSpan_returnsCorrectSpan() {
        // fast: start=0, end=35_000_000 ns → 35ms
        // slow: start=0, end=4_750_000_000 ns → 4750ms
        var fast = buildSpan("s1", null, "POST /orders", "0", "35000000",   2);
        var slow = buildSpan("s2", "s1", "db.query",     "0", "4750000000", 2);
        var response = buildResponse("trace-003", "payment-service", fast, slow);

        var tree = SpanTreeMapper.from("trace-003", response);

        assertThat(tree.slowestSpan().operationName()).isEqualTo("db.query");
        assertThat(tree.slowestSpan().durationMs()).isEqualTo(4750L);
    }

    @Test
    @DisplayName("unknown status code → UNSET")
    void from_unknownStatusCode_mapsToUnset() {
        var span = buildSpan("s1", null, "GET /health", "0", "5000000", 99);
        var response = buildResponse("trace-004", "order-service", span);

        var tree = SpanTreeMapper.from("trace-004", response);

        assertThat(tree.spans().get(0).status()).isEqualTo(SpanStatus.UNSET);
    }
}

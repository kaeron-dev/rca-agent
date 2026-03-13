package com.rcaagent.adapters.out.tempo;

import com.rcaagent.domain.Span;
import com.rcaagent.domain.SpanStatus;
import com.rcaagent.domain.SpanTree;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps Tempo API response → domain SpanTree.
 *
 * SRP: single reason to change — Tempo response format changes.
 * The domain never sees Tempo types.
 */
public class SpanTreeMapper {

    private SpanTreeMapper() {}

    public static SpanTree from(String traceId, TempoSpanResponse response) {
        List<Span> spans = response.resourceSpans().stream()
                .flatMap(rs -> rs.scopeSpans().stream()
                        .flatMap(ss -> ss.spans().stream()
                                .map(s -> toSpan(s, extractServiceName(rs.resource())))))
                .filter(Objects::nonNull)
                .toList();

        return new SpanTree(traceId, spans);
    }

    private static Span toSpan(TempoSpanResponse.TempoSpan raw, String serviceName) {
        long durationMs = computeDurationMs(raw.startTimeUnixNano(), raw.endTimeUnixNano());
        if (durationMs < 0) return null;

        return new Span(
                raw.spanId(),
                raw.parentSpanId(),
                raw.name(),
                serviceName,
                durationMs,
                toStatus(raw.status())
        );
    }

    private static long computeDurationMs(String startNano, String endNano) {
        if (startNano == null || endNano == null) return -1;
        try {
            long start = Long.parseLong(startNano);
            long end   = Long.parseLong(endNano);
            return (end - start) / 1_000_000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static SpanStatus toStatus(TempoSpanResponse.TempoStatus status) {
        if (status == null || status.code() == null) return SpanStatus.UNSET;
        return switch (status.code()) {
            case 1  -> SpanStatus.ERROR;
            case 2  -> SpanStatus.OK;
            default -> SpanStatus.UNSET;
        };
    }

    private static String extractServiceName(Map<String, Object> resource) {
        if (resource == null) return "unknown";
        Object attrs = resource.get("attributes");
        if (attrs instanceof List<?> list) {
            return list.stream()
                    .filter(a -> a instanceof Map<?,?> m && "service.name".equals(m.get("key")))
                    .map(a -> ((Map<?,?>) a).get("value"))
                    .filter(v -> v instanceof Map<?,?> m && m.containsKey("stringValue"))
                    .map(v -> (String) ((Map<?,?>) v).get("stringValue"))
                    .findFirst()
                    .orElse("unknown");
        }
        return "unknown";
    }
}

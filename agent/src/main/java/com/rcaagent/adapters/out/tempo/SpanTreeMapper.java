package com.rcaagent.adapters.out.tempo;

import com.rcaagent.domain.Span;
import com.rcaagent.domain.SpanStatus;
import com.rcaagent.domain.SpanTree;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SpanTreeMapper {

    private SpanTreeMapper() {}

    public static SpanTree from(String traceId, TempoSpanResponse response) {
        if (response == null || response.trace() == null) return new SpanTree(traceId, List.of());
        var resourceSpans = response.trace().resourceSpans();
        if (resourceSpans == null) return new SpanTree(traceId, List.of());

        List<Span> spans = resourceSpans.stream()
                .filter(rs -> rs.scopeSpans() != null)
                .flatMap(rs -> rs.scopeSpans().stream()
                        .filter(ss -> ss.spans() != null)
                        .flatMap(ss -> ss.spans().stream()
                                .map(s -> toSpan(s, extractServiceName(rs.resource())))))
                .filter(Objects::nonNull)
                .toList();

        return new SpanTree(traceId, spans);
    }

    private static Span toSpan(TempoSpanResponse.TempoSpan raw, String serviceName) {
        long startNano = parseNano(raw.startTimeUnixNano());
        long endNano   = parseNano(raw.endTimeUnixNano());
        if (startNano < 0 || endNano < 0) return null;

        long durationMs  = (endNano - startNano) / 1_000_000;
        long startTimeMs = startNano / 1_000_000;
        if (durationMs < 0) return null;

        return new Span(
                raw.spanId(),
                raw.parentSpanId(),
                raw.name(),
                serviceName,
                durationMs,
                0L,
                toStatus(raw.status()),
                startTimeMs,
                extractAttributes(raw.attributes())
        );
    }

    private static long parseNano(String nano) {
        if (nano == null) return -1;
        try { return Long.parseLong(nano); }
        catch (NumberFormatException e) { return -1; }
    }

    private static Map<String, String> extractAttributes(List<Map<String, Object>> rawAttrs) {
        if (rawAttrs == null || rawAttrs.isEmpty()) return Map.of();
        return rawAttrs.stream()
                .filter(a -> a.get("key") instanceof String && a.get("value") instanceof Map)
                .collect(Collectors.toMap(
                        a -> (String) a.get("key"),
                        a -> {
                            Map<?, ?> v = (Map<?, ?>) a.get("value");
                            if (v.get("stringValue") != null) return v.get("stringValue").toString();
                            if (v.get("intValue")    != null) return v.get("intValue").toString();
                            if (v.get("boolValue")   != null) return v.get("boolValue").toString();
                            if (v.get("doubleValue") != null) return v.get("doubleValue").toString();
                            return "";
                        },
                        (first, dup) -> first
                ));
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
        if (!(attrs instanceof List<?> list)) return "unknown";
        return list.stream()
                .filter(a -> a instanceof Map<?,?> m && "service.name".equals(m.get("key")))
                .map(a -> ((Map<?,?>) a).get("value"))
                .filter(v -> v instanceof Map<?,?> m && m.containsKey("stringValue"))
                .map(v -> (String) ((Map<?,?>) v).get("stringValue"))
                .findFirst()
                .orElse("unknown");
    }
}

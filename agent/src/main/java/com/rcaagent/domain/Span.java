package com.rcaagent.domain;

import java.util.Map;

public record Span(
        String spanId,
        String parentSpanId,
        String operationName,
        String serviceName,
        long durationMs,
        long baselineMs,
        SpanStatus status,
        long startTimeMs,
        Map<String, String> attributes
) {
    public Span {
        attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }

    /** Minimal constructor — used by SpanTreeMapper */
    public Span(String spanId, String parentSpanId, String operationName,
                String serviceName, long durationMs, SpanStatus status) {
        this(spanId, parentSpanId, operationName, serviceName, durationMs, 0L, status, 0L, Map.of());
    }

    public boolean isRoot() { return parentSpanId == null || parentSpanId.isBlank(); }
    public boolean isError() { return status != null && status.isError(); }
    public boolean isAnomalous(double threshold) { return baselineMs > 0 && durationMs > (long)(threshold * baselineMs); }

    public Span withBaseline(long baseline) {
        return new Span(spanId, parentSpanId, operationName, serviceName, durationMs, baseline, status, startTimeMs, attributes);
    }
}

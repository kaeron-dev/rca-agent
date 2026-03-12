package com.rcaagent.domain;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a single span in a distributed trace.
 * A span captures one operation within a service.
 *
 * baselineMs  — historical P95 for this operation. 0 if no baseline exists yet.
 * startTimeMs — epoch millis when the span started. 0 if not provided by the backend.
 * attributes  — OTel span attributes (db.statement, db.rows_returned, http.status_code, etc.)
 */
public record Span(
        String spanId,
        String parentSpanId,
        String operationName,
        String serviceName,
        long durationMs,
        SpanStatus status,
        long baselineMs,
        long startTimeMs,
        Map<String, String> attributes
) {
    public Span {
        attributes = attributes != null ? Collections.unmodifiableMap(attributes) : Map.of();
    }

    /**
     * Backwards-compatible constructor for existing code and tests that don't have baseline/time data.
     */
    public Span(String spanId, String parentSpanId, String operationName,
                String serviceName, long durationMs, SpanStatus status) {
        this(spanId, parentSpanId, operationName, serviceName, durationMs, status, 0L, 0L, Map.of());
    }

    public boolean isRoot() {
        return parentSpanId == null || parentSpanId.isBlank();
    }

    public boolean isError() {
        return status.isError();
    }

    /**
     * A span is anomalous only if baseline data exists and duration exceeds the given threshold.
     * If baselineMs is 0 (no baseline), the span is treated as structural context only.
     */
    public boolean isAnomalous(double thresholdFactor) {
        return baselineMs > 0 && durationMs > (long) (thresholdFactor * baselineMs);
    }

    public String getAttribute(String key) {
        return attributes.getOrDefault(key, "");
    }
}

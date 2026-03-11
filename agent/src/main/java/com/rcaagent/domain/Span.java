package com.rcaagent.domain;

/**
 * Represents a single span in a distributed trace.
 * A span captures one operation within a service.
 */
public record Span(
        String spanId,
        String parentSpanId,
        String operationName,
        String serviceName,
        long durationMs,
        SpanStatus status
) {
    public boolean isRoot() {
        return parentSpanId == null || parentSpanId.isBlank();
    }

    public boolean isError() {
        return status.isError();
    }
}

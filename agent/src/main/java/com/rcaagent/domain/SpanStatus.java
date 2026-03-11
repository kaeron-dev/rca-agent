package com.rcaagent.domain;

/**
 * OpenTelemetry span status codes.
 * https://opentelemetry.io/docs/specs/otel/trace/api/#set-status
 */
public enum SpanStatus {
    OK,
    ERROR,
    UNSET;

    public boolean isError() {
        return this == ERROR;
    }
}

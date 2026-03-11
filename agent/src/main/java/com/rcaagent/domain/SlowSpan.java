package com.rcaagent.domain;

public record SlowSpan(
        String spanId,
        String operationName,
        String serviceName,
        long durationMs,
        long baselineMs
) implements AnomalyType {

    public double anomalyFactor() {
        return (double) durationMs / baselineMs;
    }
}

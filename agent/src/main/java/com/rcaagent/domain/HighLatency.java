package com.rcaagent.domain;

public record HighLatency(
        String serviceName,
        long p99Ms,
        long baselineP99Ms
) implements AnomalyType {

    public double anomalyFactor() {
        return (double) p99Ms / baselineP99Ms;
    }
}

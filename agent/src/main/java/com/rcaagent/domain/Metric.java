package com.rcaagent.domain;

import java.time.Instant;

/**
 * Represents a single metric data point for a service.
 *
 * threshold — the alert threshold for this metric. 0 if not configured.
 *             The prompt skips metrics where threshold is 0.
 */
public record Metric(
        String serviceName,
        String metricName,
        double value,
        Instant timestamp,
        double threshold
) {
    /**
     * Backwards-compatible constructor for existing code and tests without threshold.
     */
    public Metric(String serviceName, String metricName, double value, Instant timestamp) {
        this(serviceName, metricName, value, timestamp, 0.0);
    }

    public boolean exceedsThreshold() {
        return threshold > 0 && value > threshold;
    }
}

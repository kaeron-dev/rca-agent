package com.rcaagent.domain;

import java.time.Instant;

/**
 * Represents a single metric data point for a service.
 */
public record Metric(
        String serviceName,
        String metricName,
        double value,
        Instant timestamp
) {}

package com.rcaagent.domain;

import com.rcaagent.domain.validation.DomainGuard;

/**
 * The output of the RCA Agent — a structured root cause analysis report.
 * Produced by TraceAnalysisService after querying Tempo, Prometheus, and the LLM.
 *
 * anomalyType — one of: DATABASE_SLOW_QUERY, HIGH_LATENCY_DOWNSTREAM,
 *               RESOURCE_EXHAUSTION, CASCADE_FAILURE, UNKNOWN.
 *               Used by the evaluation framework to compute per-type accuracy.
 */
public record RcaReport(
        String traceId,
        String rootCause,
        String anomalySpan,
        long durationMs,
        long baselineMs,
        double anomalyFactor,
        String recommendation,
        double confidence,
        String anomalyType
) {
    public RcaReport {
        DomainGuard.requireNonBlank(traceId, "traceId");
        DomainGuard.requireNonBlank(rootCause, "rootCause");
        DomainGuard.requirePositive(durationMs, "durationMs");
        DomainGuard.requireBetween(confidence, 0.0, 1.0, "confidence");
        if (anomalyType == null || anomalyType.isBlank()) anomalyType = "UNKNOWN";
    }

    /**
     * Backwards-compatible constructor for existing code without anomalyType.
     */
    public RcaReport(String traceId, String rootCause, String anomalySpan,
                     long durationMs, long baselineMs, double anomalyFactor,
                     String recommendation, double confidence) {
        this(traceId, rootCause, anomalySpan, durationMs, baselineMs,
             anomalyFactor, recommendation, confidence, "UNKNOWN");
    }

    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    public boolean isAnomaly() {
        return anomalyFactor >= 2.0;
    }
}

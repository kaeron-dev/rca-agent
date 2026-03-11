package com.rcaagent.domain;

import com.rcaagent.domain.validation.DomainGuard;

/**
 * The output of the RCA Agent — a structured root cause analysis report.
 * Produced by TraceAnalysisService after querying Tempo, Prometheus, and the LLM.
 */
public record RcaReport(
        String traceId,
        String rootCause,
        String anomalySpan,
        long durationMs,
        long baselineMs,
        double anomalyFactor,
        String recommendation,
        double confidence
) {
    public RcaReport {
        DomainGuard.requireNonBlank(traceId, "traceId");
        DomainGuard.requireNonBlank(rootCause, "rootCause");
        DomainGuard.requirePositive(durationMs, "durationMs");
        DomainGuard.requireBetween(confidence, 0.0, 1.0, "confidence");
    }

    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    public boolean isAnomaly() {
        return anomalyFactor >= 2.0;
    }
}

package com.rcaagent.domain;

import com.rcaagent.domain.validation.DomainGuard;

/**
 * Enriched trace data passed to the RcaAnalyzer port.
 * anomalyFactor() is computed here — domain logic lives with domain data (Tell Don't Ask).
 */
public record TraceContext(
        SpanTree spanTree,
        Span anomalySpan,
        long baselineMs
) {
    public TraceContext {
        DomainGuard.requireNonNull(spanTree, "spanTree");
        DomainGuard.requireNonNull(anomalySpan, "anomalySpan");
        DomainGuard.requirePositive(baselineMs, "baselineMs");
    }

    public double anomalyFactor() {
        return Math.max(1.0, (double) anomalySpan.durationMs() / baselineMs);
    }

    public String traceId() {
        return spanTree.traceId();
    }
}

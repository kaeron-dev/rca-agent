package com.rcaagent.domain;

import com.rcaagent.domain.validation.DomainGuard;

public record TraceContext(
        SpanTree spanTree,
        Span anomalySpan,
        long baselineMs,
        double anomalyFactor
) {
    public TraceContext {
        DomainGuard.requireNonNull(spanTree, "spanTree");
        DomainGuard.requireNonNull(anomalySpan, "anomalySpan");
        DomainGuard.requirePositive(baselineMs, "baselineMs");
        DomainGuard.requireMin(anomalyFactor, 1.0, "anomalyFactor");
    }

    public String traceId() {
        return spanTree.traceId();
    }
}

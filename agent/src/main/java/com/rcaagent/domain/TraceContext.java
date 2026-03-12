package com.rcaagent.domain;

import com.rcaagent.domain.validation.DomainGuard;

import java.util.Collections;
import java.util.List;

/**
 * Enriched trace data passed to the RcaAnalyzer port.
 *
 * anomalyFactor()    — computed here (Tell Don't Ask).
 * anomalyThreshold   — configurable multiplier. A span is anomalous if durationMs > threshold * baselineMs.
 *                      Default: 2.0. Lower values increase sensitivity for high-criticality services.
 * metrics            — system metrics at the time of the anomaly (CPU, heap, thread pool, etc.)
 *                      Empty list if Prometheus had no data for this trace window.
 */
public record TraceContext(
        SpanTree spanTree,
        Span anomalySpan,
        long baselineMs,
        double anomalyThreshold,
        List<Metric> metrics
) {
    public TraceContext {
        DomainGuard.requireNonNull(spanTree, "spanTree");
        DomainGuard.requireNonNull(anomalySpan, "anomalySpan");
        DomainGuard.requirePositive(baselineMs, "baselineMs");
        if (anomalyThreshold <= 0) throw new IllegalArgumentException("anomalyThreshold must be > 0");
        metrics = metrics != null ? Collections.unmodifiableList(metrics) : List.of();
    }

    /**
     * Backwards-compatible constructor — uses default threshold of 2.0 and empty metrics.
     * Existing tests and code continue to work without changes.
     */
    public TraceContext(SpanTree spanTree, Span anomalySpan, long baselineMs) {
        this(spanTree, anomalySpan, baselineMs, 2.0, List.of());
    }

    public double anomalyFactor() {
        return Math.max(1.0, (double) anomalySpan.durationMs() / baselineMs);
    }

    public double baselineDeviationPct() {
        return (anomalyFactor() - 1.0) * 100.0;
    }

    public String traceId() {
        return spanTree.traceId();
    }
}

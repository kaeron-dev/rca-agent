package com.rcaagent.domain;

import java.time.Instant;

/**
 * A single evaluation run result — produced by EvaluationService.
 *
 * correct — true if actualAnomalyType matches expectedAnomalyType
 *           AND actualRootCauseSpan matches expectedRootCauseSpan.
 * Used by the benchmark script and persisted to H2 for trend analysis.
 */
public record EvaluationEntry(
        String traceId,
        String expectedRootCauseSpan,
        String actualRootCauseSpan,
        String expectedAnomalyType,
        String actualAnomalyType,
        double confidence,
        boolean correct,
        Instant evaluatedAt
) {
    public static EvaluationEntry of(
            String traceId,
            String expectedRootCauseSpan,
            String expectedAnomalyType,
            RcaReport report
    ) {
        boolean spanMatch = expectedRootCauseSpan.equalsIgnoreCase(report.anomalySpan())
                || report.rootCause().toLowerCase().contains(
                        expectedRootCauseSpan.replace("none", "").trim().toLowerCase());
        boolean typeMatch = expectedAnomalyType.equalsIgnoreCase(report.anomalyType());

        return new EvaluationEntry(
                traceId,
                expectedRootCauseSpan,
                report.anomalySpan(),
                expectedAnomalyType,
                report.anomalyType(),
                report.confidence(),
                spanMatch && typeMatch,
                Instant.now()
        );
    }
}

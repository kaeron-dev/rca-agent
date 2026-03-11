package com.rcaagent.domain;

import com.rcaagent.domain.validation.DomainGuard;

import java.util.Comparator;
import java.util.List;

/**
 * Represents the complete span tree of a distributed trace.
 */
public record SpanTree(
        String traceId,
        List<Span> spans
) {
    public SpanTree {
        DomainGuard.requireNonBlank(traceId, "traceId");
        if (spans == null || spans.isEmpty())
            throw new IllegalArgumentException("spans must not be empty");
    }
    public Span slowestSpan() {
        return spans.stream()
                .max(Comparator.comparingLong(Span::durationMs))
                .orElseThrow(() -> new IllegalStateException("SpanTree has no spans"));
    }

    public List<Span> spansForService(String serviceName) {
        return spans.stream()
                .filter(s -> serviceName.equals(s.serviceName()))
                .toList();
    }

    public long totalDurationMs() {
        return spans.stream()
                .filter(Span::isRoot)
                .mapToLong(Span::durationMs)
                .sum();
    }
}

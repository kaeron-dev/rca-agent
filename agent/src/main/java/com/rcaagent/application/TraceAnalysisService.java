package com.rcaagent.application;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.SpanTree;
import com.rcaagent.domain.TraceContext;
import com.rcaagent.domain.validation.DomainGuard;
import com.rcaagent.ports.in.AnalyzeTraceUseCase;
import com.rcaagent.ports.out.MetricsRepository;
import com.rcaagent.ports.out.RcaAnalyzer;
import com.rcaagent.ports.out.TraceRepository;
import org.springframework.stereotype.Service;

/**
 * Core use case — orchestrates the RCA analysis pipeline.
 *
 * Knows WHAT to do — not HOW to connect to external systems.
 * Depends only on port interfaces — testable without Docker.
 */
@Service
public class TraceAnalysisService implements AnalyzeTraceUseCase {

    private final TraceRepository traceRepository;
    private final MetricsRepository metricsRepository;
    private final RcaAnalyzer rcaAnalyzer;

    public TraceAnalysisService(
            TraceRepository traceRepository,
            MetricsRepository metricsRepository,
            RcaAnalyzer rcaAnalyzer
    ) {
        DomainGuard.requireNonNull(traceRepository, "traceRepository");
        DomainGuard.requireNonNull(metricsRepository, "metricsRepository");
        DomainGuard.requireNonNull(rcaAnalyzer, "rcaAnalyzer");
        this.traceRepository = traceRepository;
        this.metricsRepository = metricsRepository;
        this.rcaAnalyzer = rcaAnalyzer;
    }

    /**
     * Analysis pipeline:
     *   1. Fetch span tree from trace backend
     *   2. Identify the slowest span
     *   3. Fetch historical baseline for that service
     *   4. Build TraceContext — anomalyFactor computed inside TraceContext
     *   5. Delegate to LLM analyzer
     */
    @Override
    public RcaReport analyze(String traceId) {
        DomainGuard.requireNonBlank(traceId, "traceId");

        SpanTree spanTree = traceRepository.findByTraceId(traceId);
        var anomalySpan = spanTree.slowestSpan();
        long baselineMs = metricsRepository.getBaseline(anomalySpan.serviceName());

        var context = new TraceContext(spanTree, anomalySpan, baselineMs);

        return rcaAnalyzer.analyze(context);
    }
}

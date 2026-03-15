package com.rcaagent.application;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.Span;
import com.rcaagent.domain.SpanTree;
import com.rcaagent.domain.TraceContext;
import com.rcaagent.domain.validation.DomainGuard;
import com.rcaagent.domain.exception.TraceNotFoundException;
import com.rcaagent.ports.in.AnalyzeTraceUseCase;
import com.rcaagent.ports.out.MetricsRepository;
import com.rcaagent.ports.out.RcaAnalyzer;
import com.rcaagent.ports.out.TraceRepository;
import com.rcaagent.ports.out.RcaReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TraceAnalysisService implements AnalyzeTraceUseCase {

    private static final Logger log = LoggerFactory.getLogger(TraceAnalysisService.class);

    private final TraceRepository traceRepository;
    private final MetricsRepository metricsRepository;
    private final RcaAnalyzer rcaAnalyzer;
    private final RcaReportRepository rcaReportRepository;
    private final EvaluationService evaluationService;

    public TraceAnalysisService(
            TraceRepository traceRepository,
            MetricsRepository metricsRepository,
            RcaAnalyzer rcaAnalyzer,
            RcaReportRepository rcaReportRepository,
            EvaluationService evaluationService
    ) {
        DomainGuard.requireNonNull(traceRepository,      "traceRepository");
        DomainGuard.requireNonNull(metricsRepository,    "metricsRepository");
        DomainGuard.requireNonNull(rcaAnalyzer,          "rcaAnalyzer");
        DomainGuard.requireNonNull(rcaReportRepository,  "rcaReportRepository");
        DomainGuard.requireNonNull(evaluationService,    "evaluationService");
        this.traceRepository     = traceRepository;
        this.metricsRepository   = metricsRepository;
        this.rcaAnalyzer         = rcaAnalyzer;
        this.rcaReportRepository = rcaReportRepository;
        this.evaluationService   = evaluationService;
    }

    @Override
    public RcaReport analyze(String traceId) {
        DomainGuard.requireNonBlank(traceId, "traceId");

        // 1. Check if we already have a high-confidence report for this trace
        var existingReport = rcaReportRepository.findByTraceId(traceId);
        if (existingReport.isPresent() && existingReport.get().confidence() >= 0.7) {
            log.info("Returning cached high-confidence RCA report for traceId: {}", traceId);
            return existingReport.get();
        }

        // 2. Perform fresh analysis if no cache or low confidence
        log.info("Performing fresh RCA analysis for traceId: {}", traceId);
        
        TraceContext context;
        try {
            SpanTree rawTree = traceRepository.findByTraceId(traceId);
            List<Span> enrichedSpans = rawTree.spans().stream()
                    .map(span -> span.withBaseline(metricsRepository.getBaseline(span.serviceName())))
                    .toList();
            SpanTree enrichedTree = new SpanTree(traceId, enrichedSpans);
            Span anomalySpan      = enrichedTree.maxDeviationSpan();
            context = new TraceContext(enrichedTree, anomalySpan, anomalySpan.baselineMs());
        } catch (TraceNotFoundException e) {
            log.info("Trace {} not found in Tempo, checking evaluation dataset as fallback...", traceId);
            context = evaluationService.findContextById(traceId)
                    .orElseThrow(() -> new TraceNotFoundException("Trace not found in Tempo or Dataset: " + traceId));
        }

        RcaReport newReport = rcaAnalyzer.analyze(context);

        // 3. Persist ONLY if result is high confidence (avoid caching failures)
        if (newReport.confidence() >= 0.7) {
            rcaReportRepository.save(newReport);
        }

        return newReport;
    }
}

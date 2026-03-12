package com.rcaagent.adapters.in;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.exception.TraceNotFoundException;
import com.rcaagent.ports.in.AnalyzeTraceUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound adapter — exposes the RCA analysis use case as a REST endpoint.
 *
 * What to study here:
 *   - Depends on AnalyzeTraceUseCase (port), never on TraceAnalysisService directly
 *   - This is the entry point from the outside world into the hexagonal architecture
 *   - Error handling: TraceNotFoundException → 404, unexpected → 500
 *   - SRP: only handles HTTP concerns — no business logic here
 */
@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeController.class);

    private final AnalyzeTraceUseCase analyzeTraceUseCase;

    public AnalyzeController(AnalyzeTraceUseCase analyzeTraceUseCase) {
        this.analyzeTraceUseCase = analyzeTraceUseCase;
    }

    /**
     * POST /api/analyze/{traceId}
     * Triggers RCA analysis for the given trace ID.
     * Returns 200 with RcaReport, 404 if trace not found, 500 on unexpected error.
     */
    @PostMapping("/analyze/{traceId}")
    public ResponseEntity<RcaReport> analyze(@PathVariable String traceId) {
        log.info("RCA analysis requested for traceId: {}", traceId);
        try {
            RcaReport report = analyzeTraceUseCase.analyze(traceId);
            log.info("RCA analysis completed for traceId: {} — confidence: {}", traceId, report.confidence());
            return ResponseEntity.ok(report);
        } catch (TraceNotFoundException e) {
            log.warn("Trace not found: {}", traceId);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid traceId: {}", traceId);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /api/analyze/{traceId}
     * Convenience endpoint for browser/curl testing.
     */
    @GetMapping("/analyze/{traceId}")
    public ResponseEntity<RcaReport> analyzeGet(@PathVariable String traceId) {
        return analyze(traceId);
    }
}

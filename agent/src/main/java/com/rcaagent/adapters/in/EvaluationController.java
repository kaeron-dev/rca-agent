package com.rcaagent.adapters.in;

import com.rcaagent.application.EvaluationService;
import com.rcaagent.domain.AccuracyReport;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the evaluation pipeline.
 *
 * GET /api/evaluate      → returns AccuracyReport as JSON
 * GET /api/evaluate/md   → returns AccuracyReport as Markdown (for benchmark script)
 */
@RestController
@RequestMapping("/api/evaluate")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping
    public AccuracyReport evaluate() {
        return evaluationService.evaluate();
    }

    @GetMapping(value = "/md", produces = MediaType.TEXT_PLAIN_VALUE)
    public String evaluateMarkdown() {
        return evaluationService.evaluate().toMarkdown();
    }
}

package com.rcaagent.adapters.out.llm;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.TraceContext;
import com.rcaagent.ports.out.RcaAnalyzer;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter — implements RcaAnalyzer using LangChain4j.
 *
 * Resilience patterns applied:
 *   @Retry    — retries up to 3 times with exponential backoff (1s, 2s, 4s)
 *   @Bulkhead — max 5 concurrent LLM calls, others wait 2s or get rejected
 *
 * Fallback chain:
 *   Primary LLM fails → retry 3x → bulkhead full → degraded response
 *   The system always returns something useful.
 *
 * What to study here:
 *   - @Retry prevents hammering a struggling LLM with immediate retries
 *   - @Bulkhead prevents thread exhaustion under high load
 *   - fallbackMethod returns a partial RcaReport — better than an error
 */
@Component
public class LangChain4jRcaAdapter implements RcaAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jRcaAdapter.class);

    private final ModelSelectionStrategy strategy;

    public LangChain4jRcaAdapter(ModelSelectionStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    @Retry(name = "llm", fallbackMethod = "fallback")
    @Bulkhead(name = "llm", fallbackMethod = "fallback")
    public RcaReport analyze(TraceContext context) {
        var model    = strategy.select(context);
        var prompt   = RcaPromptBuilder.build(context);

        log.info("Analyzing trace {} — anomalyFactor: {:.1f}x — model: {}",
                context.traceId(), context.anomalyFactor(), model.getClass().getSimpleName());

        var response = model.generate(prompt);

        log.debug("LLM response for trace {}: {}", context.traceId(), response);

        return RcaReportParser.parse(response, context);
    }

    /**
     * Fallback — called after all retries exhausted or bulkhead full.
     * Returns degraded response with the span data but no LLM interpretation.
     */
    public RcaReport fallback(TraceContext context, Exception e) {
        log.warn("LLM unavailable for trace {} — returning degraded response. Reason: {}",
                context.traceId(), e.getMessage());

        return new RcaReport(
                context.traceId(),
                "LLM unavailable — manual investigation required for span: "
                        + context.anomalySpan().operationName()
                        + " in " + context.anomalySpan().serviceName(),
                context.anomalySpan().operationName(),
                context.anomalySpan().durationMs(),
                context.baselineMs(),
                context.anomalyFactor(),
                "LLM service unavailable. Check " + context.anomalySpan().serviceName()
                        + " — span took " + context.anomalyFactor() + "x longer than baseline.",
                0.0
        );
    }
}

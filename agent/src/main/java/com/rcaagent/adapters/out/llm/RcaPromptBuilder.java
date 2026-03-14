package com.rcaagent.adapters.out.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcaagent.domain.Metric;
import com.rcaagent.domain.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * High-precision prompt builder optimized for quality RCA analysis.
 * Prevents LLM laziness by using abstract placeholders and strict rules.
 */
public class RcaPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(RcaPromptBuilder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RcaPromptBuilder() {}

    public static String build(TraceContext context) {
        return build(context, false);
    }

    public static String build(TraceContext context, boolean isLite) {
        if (isLite) {
            return buildLite(context);
        }
        return buildStandard(context);
    }

    private static String buildStandard(TraceContext context) {
        var anomaly = context.anomalySpan();
        return """
                # TASK: Root Cause Analysis (RCA)
                # RESPONSE: STRICT JSON ONLY. NO PROSE.
                
                # DATA TO ANALYZE:
                - Trace: %s
                - Target: %s in %s
                - Duration: %d ms (Baseline: %d ms)
                - Deviation: %.1f%%
                
                # EVIDENCE (SPAN TREE):
                %s
                
                # EVIDENCE (METRICS):
                %s
                
                # CLASSIFICATION RULES:
                - If duration > 2x baseline and span name contains 'db' or 'query' -> DATABASE_SLOW_QUERY
                - If duration > 2x baseline and span name is an HTTP call -> HIGH_LATENCY_DOWNSTREAM
                - If a metric value > limit -> RESOURCE_EXHAUSTION
                
                # OUTPUT REQUIREMENTS:
                - 'rootCause': Must be a detailed technical explanation (e.g., 'Slow SQL execution on payments table').
                - 'recommendation': Must be a concrete action (e.g., 'Add index on transaction_id').
                - 'anomalyType': Must be exactly one: [DATABASE_SLOW_QUERY, HIGH_LATENCY_DOWNSTREAM, RESOURCE_EXHAUSTION, CASCADE_FAILURE].
                
                # JSON STRUCTURE (Fill fields based on data above):
                {
                  "rootCauseSpan": "service: operation",
                  "rootCause": "<DETAILED_EXPLANATION>",
                  "anomalyType": "<TYPE>",
                  "confidence": 0.9,
                  "baselineDeviationPct": %.1f,
                  "evidence": ["<EVIDENCE_1>", "<EVIDENCE_2>"],
                  "recommendation": "<ACTIONABLE_FIX>"
                }
                """.formatted(
                context.traceId(),
                anomaly.operationName(),
                anomaly.serviceName(),
                anomaly.durationMs(),
                context.baselineMs(),
                context.baselineDeviationPct(),
                buildCompactSpanTree(context),
                buildMetrics(context.metrics()),
                context.baselineDeviationPct()
        );
    }

    private static String buildLite(TraceContext context) {
        var anomaly = context.anomalySpan();
        String status = anomaly.status() != null ? anomaly.status().name() : "UNKNOWN";
        
        return """
                # RCA TASK (LITE)
                Analyze why %s in %s is failing.
                - Duration: %d ms (baseline %d ms)
                - Status: %s
                - Anomaly type could be: [DATABASE_SLOW_QUERY, HIGH_LATENCY_DOWNSTREAM, ERROR_RESPONSE]
                
                Return ONLY JSON:
                {
                  "rootCause": "Short reason why (check status and duration)",
                  "anomalyType": "Choose one of the three above",
                  "confidence": 0.8,
                  "recommendation": "Fix action"
                }
                """.formatted(
                anomaly.operationName(),
                anomaly.serviceName(),
                anomaly.durationMs(),
                context.baselineMs(),
                status
        );
    }

    private static String buildCompactSpanTree(TraceContext context) {
        var spans = context.spanTree().spans().stream()
                .sorted((a, b) -> Long.compare(b.durationMs(), a.durationMs()))
                .map(s -> "{\"svc\":\"" + s.serviceName() + "\",\"op\":\"" + s.operationName() + "\",\"ms\":" + s.durationMs() + ",\"base\":" + s.baselineMs() + "}")
                .collect(Collectors.joining(","));
        return "[" + spans + "]";
    }

    private static String buildMetrics(List<Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) return "[]";
        return metrics.stream()
                .map(m -> "{\"name\":\"" + m.metricName() + "\",\"val\":" + m.value() + ",\"limit\":" + m.threshold() + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }
}

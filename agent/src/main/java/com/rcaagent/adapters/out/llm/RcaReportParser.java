package com.rcaagent.adapters.out.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Parsing Engine for LLM responses.
 * Decouples the raw LLM output from the domain RcaReport.
 *
 * Features:
 *   - Defensive Parsing: Extracts JSON blocks from surrounding prose.
 *   - Intelligent Heuristics: Infers anomaly types when LLM classification is absent or genric.
 *   - Error Resilience: Returns partial reports instead of throwing exceptions on malformed input.
 */
public class RcaReportParser {

    private static final Logger log = LoggerFactory.getLogger(RcaReportParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RcaReportParser() {}

    /**
     * Translates LLM text to a structured RcaReport.
     * Uses heuristic fallback if the LLM output is incomplete.
     */
    @SuppressWarnings("unchecked")
    public static RcaReport parse(String llmResponse, TraceContext context) {
        try {
            String json = extractJson(llmResponse);
            Map<String, Object> parsed = MAPPER.readValue(json, Map.class);

            String rootCause      = (String) parsed.getOrDefault("rootCause", "Unknown root cause");
            String recommendation = (String) parsed.getOrDefault("recommendation", "No recommendation available");
            double confidence     = toDouble(parsed.getOrDefault("confidence", 0.5));
            String anomalyType    = (String) parsed.getOrDefault("anomalyType", "UNKNOWN");

            // Heuristic Fallback: Improve classification for small/lazy models
            if ("UNKNOWN".equalsIgnoreCase(anomalyType) || anomalyType.contains("<")) {
                anomalyType = inferAnomalyType(rootCause, context);
            }

            return new RcaReport(
                    context.traceId(),
                    rootCause,
                    context.anomalySpan().operationName(),
                    context.anomalySpan().durationMs(),
                    context.baselineMs(),
                    context.anomalyFactor(),
                    recommendation,
                    confidence,
                    anomalyType
            );
        } catch (Exception e) {
            log.warn("Failed to parse LLM response. Response: {}", llmResponse);
            return new RcaReport(
                    context.traceId(),
                    "Analysis error: " + e.getMessage(),
                    context.anomalySpan().operationName(),
                    context.anomalySpan().durationMs(),
                    context.baselineMs(),
                    context.anomalyFactor(),
                    "Manual investigation required",
                    0.0,
                    "UNKNOWN"
            );
        }
    }

    /**
     * Heuristic inference based on keywords in the root cause explanation.
     */
    private static String inferAnomalyType(String rootCause, TraceContext context) {
        String cause = rootCause.toLowerCase();
        if (cause.contains("sql") || cause.contains("database") || cause.contains("db.") || cause.contains("query")) {
            return "DATABASE_SLOW_QUERY";
        }
        if (cause.contains("http") || cause.contains("downstream") || cause.contains("service call")) {
            return "HIGH_LATENCY_DOWNSTREAM";
        }
        if (cause.contains("cpu") || cause.contains("memory") || cause.contains("limit") || cause.contains("exhausted")) {
            return "RESOURCE_EXHAUSTION";
        }
        return "UNKNOWN";
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start == -1 || end == -1) throw new IllegalArgumentException("No JSON block found");
        return text.substring(start, end + 1);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (Exception e) { return 0.5; }
    }
}

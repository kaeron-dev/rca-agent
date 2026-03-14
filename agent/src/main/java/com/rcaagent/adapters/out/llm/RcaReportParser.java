package com.rcaagent.adapters.out.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Parses the LLM JSON response into a domain RcaReport.
 * Includes heuristic fallback for perezoso/small models.
 */
public class RcaReportParser {

    private static final Logger log = LoggerFactory.getLogger(RcaReportParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RcaReportParser() {}

    @SuppressWarnings("unchecked")
    public static RcaReport parse(String llmResponse, TraceContext context) {
        try {
            String json = extractJson(llmResponse);
            Map<String, Object> parsed = MAPPER.readValue(json, Map.class);

            String rootCause      = (String) parsed.getOrDefault("rootCause", "Unknown root cause");
            String recommendation = (String) parsed.getOrDefault("recommendation", "No recommendation available");
            double confidence     = toDouble(parsed.getOrDefault("confidence", 0.5));
            String anomalyType    = (String) parsed.getOrDefault("anomalyType", "UNKNOWN");

            // Heuristic Fallback: Si el modelo 1b es perezoso y no clasifica bien, ayudamos.
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
            log.warn("Failed to parse LLM response as JSON. Response: {}", llmResponse);
            return new RcaReport(
                    context.traceId(),
                    "Parsing error: " + e.getMessage(),
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
        if (start == -1 || end == -1) throw new IllegalArgumentException("No JSON found");
        return text.substring(start, end + 1);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (Exception e) { return 0.5; }
    }
}

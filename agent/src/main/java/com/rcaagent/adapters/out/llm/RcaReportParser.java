package com.rcaagent.adapters.out.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Parses the LLM JSON response into a domain RcaReport.
 *
 * What to study here:
 *   - SRP: parsing is isolated here — LangChain4jRcaAdapter doesn't parse
 *   - Defensive parsing: LLMs don't always return perfect JSON
 *   - Fallback: if parsing fails, returns a partial report with the raw LLM text
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

            return new RcaReport(
                    context.traceId(),
                    rootCause,
                    context.anomalySpan().operationName(),
                    context.anomalySpan().durationMs(),
                    context.baselineMs(),
                    context.anomalyFactor(),
                    recommendation,
                    confidence
            );
        } catch (Exception e) {
            log.warn("Failed to parse LLM response as JSON. Returning partial report. Response: {}", llmResponse);
            return new RcaReport(
                    context.traceId(),
                    "LLM response could not be parsed: " + llmResponse.substring(0, Math.min(100, llmResponse.length())),
                    context.anomalySpan().operationName(),
                    context.anomalySpan().durationMs(),
                    context.baselineMs(),
                    context.anomalyFactor(),
                    "Manual investigation required",
                    0.0
            );
        }
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start == -1 || end == -1) throw new IllegalArgumentException("No JSON found in LLM response");
        return text.substring(start, end + 1);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (Exception e) { return 0.5; }
    }
}

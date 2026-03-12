package com.rcaagent.adapters.out.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcaagent.domain.Metric;
import com.rcaagent.domain.Span;
import com.rcaagent.domain.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the causal reasoning prompt for RCA analysis.
 *
 * What to study here:
 *   - SRP: prompt construction is a single responsibility — isolated here
 *   - The prompt instructs the LLM to return strict JSON — parseable by RcaReportParser
 *   - Causal reasoning: we give the LLM evidence (span data + baseline deviation)
 *     and ask it to reason about cause, not just describe the symptom
 *   - anomalyThreshold is passed from TraceContext — no hardcoded 2x rule
 */
public class RcaPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(RcaPromptBuilder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RcaPromptBuilder() {}

    public static String build(TraceContext context) {
        var anomaly = context.anomalySpan();
        var parent  = findParent(context);

        return """
                You are an expert SRE performing root cause analysis on a distributed trace anomaly.
                Your response must be a single valid JSON object. Any text, markdown, or explanation outside the JSON object is a critical failure.

                TRACE CONTEXT:
                - Trace ID: %s
                - Total duration: %d ms
                - Baseline P95: %d ms
                - Deviation: %.1fx above baseline
                - Baseline deviation: %.1f%% (pre-calculated — copy exact value into baselineDeviationPct, do not recalculate)
                - Anomaly threshold: %.1fx (a span is anomalous if durationMs > threshold x baselineMs)
                - Total spans: %d

                ANOMALY SPAN:
                - Operation: %s
                - Service: %s
                - Duration: %d ms
                - Is root span: %s
                - Parent span operation: %s (duration: %d ms, baseline: %d ms)

                SPAN TREE (JSON):
                %s
                Each span contains: spanId, parentSpanId, service, operation, startTimeMs, durationMs, baselineMs, attributes.
                Relevant attributes: db.rows_returned, db.statement, http.status_code, http.url.
                If startTimeMs is absent from any span: skip time window comparisons and rely on parentSpanId structure alone.
                If baselineMs is 0 or absent for a span: do not classify that span as anomalous — use it as structural context only.

                SYSTEM METRICS AT TIME OF ANOMALY:
                %s
                Each metric contains: name, value, unit, threshold.

                ANOMALY TYPE DEFINITIONS — use exactly one of these values: DATABASE_SLOW_QUERY, HIGH_LATENCY_DOWNSTREAM, RESOURCE_EXHAUSTION, CASCADE_FAILURE, UNKNOWN.
                - DATABASE_SLOW_QUERY: anomalous span is a db operation. Evidence includes db.rows_returned unusually high, db.statement without WHERE clause, or duration >> baseline with no resource exhaustion confirmed by metrics.
                - HIGH_LATENCY_DOWNSTREAM: anomalous span is an HTTP call to another service and that service's internal spans are the root cause.
                - RESOURCE_EXHAUSTION: at least one system metric exceeds its threshold field at the time of the anomaly. This type takes priority over DATABASE_SLOW_QUERY or HIGH_LATENCY_DOWNSTREAM if metrics confirm resource pressure.
                - CASCADE_FAILURE: two or more anomalous spans exist in parallel branches AND their common upstream ancestor span is itself within its baseline (not anomalous). The ancestor is the propagation channel, not the cause. If the ancestor is also anomalous, classify by the nature of the ancestor instead.
                - UNKNOWN: insufficient data to classify with confidence >= 0.5. Use this if no span qualifies as anomalous.

                ANALYSIS STEPS — follow all steps in order. Step 2 always executes regardless of Step 1 conclusion.

                Step 1 — Build the causal chain:
                A span is anomalous only if its baselineMs is present and > 0, and its durationMs > (anomaly threshold x baselineMs).
                If baselineMs is 0 or absent: treat the span as structural context only — do not classify it as anomalous.
                If NO span qualifies as anomalous: set anomalyType to UNKNOWN, confidence to 0.4, rootCause to "INSUFFICIENT_DATA", rootCauseSpan to "none", and explain in evidence. Proceed to output.
                If the anomaly span is NOT the root span: check if its parent span is also anomalous per the rule above.
                If parent is also anomalous: move the root cause UP to the parent. Repeat until you find the highest anomalous ancestor.
                If the anomaly span IS the root span: it is the direct root cause candidate — record it and continue to Step 2.
                If the root cause span has multiple anomalous children: note all affected children in the evidence array.
                If two or more anomalous spans exist in parallel branches (different parentSpanIds at the same level):
                  - If startTimeMs is present: compare startTimeMs and startTimeMs + durationMs to confirm time windows overlap.
                  - If startTimeMs is absent: treat parallel branches as overlapping by default.
                  - If overlap confirmed: find the common upstream ancestor.
                  - If the ancestor is within its baseline (not anomalous): use CASCADE_FAILURE — the ancestor is the propagation channel.
                  - If the ancestor is also anomalous: classify by the nature of the ancestor (check Step 2 for RESOURCE_EXHAUSTION, otherwise use the ancestor span type).
                  - If no common upstream span is found: proceed to Step 2 to check resource exhaustion.

                Step 2 — Cross-reference system metrics (always execute):
                The anomaly window is defined as: anomaly span startTimeMs to startTimeMs + durationMs.
                If startTimeMs is absent: use total trace duration as the anomaly window.
                Check each metric against its threshold field during the anomaly window.
                If a metric has no threshold value: skip it — do not apply assumptions.
                If any metric exceeds its threshold during the anomaly window: override the anomalyType from Step 1 with RESOURCE_EXHAUSTION. Update rootCauseSpan to the anomalous span with the highest durationMs during the anomaly window — it is most likely the consumer of the exhausted resource.
                If no metric exceeds threshold: confirm the root cause identified in Step 1.

                Step 3 — Assign confidence using this scale:
                - 0.9–1.0: single unambiguous root cause span, metrics confirm, causal chain is clear.
                - 0.7–0.89: probable root cause, at least one metric or span attribute supports it.
                - 0.5–0.69: hypothesis only — state what data is missing in the evidence array.
                - below 0.5: set rootCause to "INSUFFICIENT_DATA", rootCauseSpan to "none", and explain in evidence. Use anomalyType UNKNOWN.

                Step 4 — Format evidence:
                Provide between 2 and 4 items. Each item must follow this exact pattern:
                "[span or metric]: [observed value] vs [baseline or threshold] — [interpretation]"
                If the root cause span has multiple anomalous children: include one evidence item per affected child.
                Example: "payment-service db.query: 4750ms vs baseline 80ms — 5837%% deviation, likely unbounded result set"

                Step 5 — Format recommendation:
                The recommendation must:
                - Name the specific span or metric that is the root cause.
                - Describe the concrete action (add index, increase pool size, add cache, add circuit breaker).
                - State the expected impact of that action.
                Max 200 chars.
                If rootCause is "INSUFFICIENT_DATA": set recommendation to "Collect baseline data for anomalous spans before analysis."

                Output rules:
                - Your entire response must be a single valid JSON object.
                - No text before or after the JSON.
                - No markdown formatting, no code blocks, no backticks.
                - No comments inside the JSON.
                - The value of rootCauseSpan must follow the format "service-name: operation-name" exactly, or "none" if rootCause is "INSUFFICIENT_DATA".
                - The value of anomalyType must be exactly one of: DATABASE_SLOW_QUERY, HIGH_LATENCY_DOWNSTREAM, RESOURCE_EXHAUSTION, CASCADE_FAILURE, UNKNOWN.
                - The value of confidence must be a float between 0.0 and 1.0.
                - The value of baselineDeviationPct must be a float copied exactly from TRACE CONTEXT.

                {
                  "rootCauseSpan": "service-name: operation-name",
                  "rootCause": "concise description, max 100 chars",
                  "anomalyType": "DATABASE_SLOW_QUERY",
                  "confidence": 0.95,
                  "baselineDeviationPct": 0.0,
                  "evidence": [
                    "span or metric: observed value vs baseline or threshold — interpretation"
                  ],
                  "recommendation": "span or metric — action — expected impact, max 200 chars"
                }
                """.formatted(
                context.traceId(),
                context.spanTree().totalDurationMs(),
                context.baselineMs(),
                context.anomalyFactor(),
                context.baselineDeviationPct(),
                context.anomalyThreshold(),
                context.spanTree().spans().size(),
                anomaly.operationName(),
                anomaly.serviceName(),
                anomaly.durationMs(),
                anomaly.isRoot() ? "yes" : "no",
                parent != null ? parent.operationName() : "none",
                parent != null ? parent.durationMs() : 0L,
                parent != null ? parent.baselineMs() : 0L,
                buildSpanTree(context),
                buildMetrics(context.metrics())
        );
    }

    private static Span findParent(TraceContext context) {
        if (context.anomalySpan().isRoot()) return null;
        String parentId = context.anomalySpan().parentSpanId();
        return context.spanTree().spans().stream()
                .filter(s -> s.spanId().equals(parentId))
                .findFirst()
                .orElse(null);
    }

    private static String buildSpanTree(TraceContext context) {
        var spans = context.spanTree().spans().stream()
                .sorted((a, b) -> Long.compare(b.durationMs(), a.durationMs()))
                .map(s -> {
                    try {
                        var node = MAPPER.createObjectNode()
                                .put("spanId",       s.spanId())
                                .put("parentSpanId", s.parentSpanId() != null ? s.parentSpanId() : "")
                                .put("service",      s.serviceName())
                                .put("operation",    s.operationName())
                                .put("startTimeMs",  s.startTimeMs())
                                .put("durationMs",   s.durationMs())
                                .put("baselineMs",   s.baselineMs());
                        if (!s.attributes().isEmpty()) {
                            var attrs = MAPPER.createObjectNode();
                            s.attributes().forEach(attrs::put);
                            node.set("attributes", attrs);
                        }
                        return MAPPER.writeValueAsString(node);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize span {}", s.spanId());
                        return "{}";
                    }
                })
                .collect(Collectors.joining(",\n  "));
        return "[\n  " + spans + "\n]";
    }

    private static String buildMetrics(List<Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) return "[]";
        var items = metrics.stream()
                .map(m -> {
                    try {
                        return MAPPER.writeValueAsString(
                                MAPPER.createObjectNode()
                                        .put("name",      m.metricName())
                                        .put("value",     m.value())
                                        .put("threshold", m.threshold())
                                        .put("service",   m.serviceName())
                        );
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize metric {}", m.metricName());
                        return "{}";
                    }
                })
                .collect(Collectors.joining(",\n  "));
        return "[\n  " + items + "\n]";
    }
}

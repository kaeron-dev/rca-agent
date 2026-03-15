package com.rcaagent.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcaagent.adapters.out.evaluation.EvaluationPersistenceAdapter;
import com.rcaagent.domain.*;
import com.rcaagent.ports.out.RcaAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs the labeled dataset through the RCA agent and computes accuracy metrics.
 * Persists results to H2 for trend analysis across prompt iterations.
 *
 * What to study here:
 *   - SRP: evaluation is a separate service — TraceAnalysisService is not modified
 *   - The dataset is loaded from classpath — no external dependencies needed
 *   - Results are persisted to H2 — enables accuracy trend tracking across prompt changes
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RcaAnalyzer rcaAnalyzer;
    private final EvaluationPersistenceAdapter persistenceAdapter;
    private final ResourceLoader resourceLoader;
    private final double confidenceThreshold;

    public EvaluationService(
            RcaAnalyzer rcaAnalyzer,
            EvaluationPersistenceAdapter persistenceAdapter,
            ResourceLoader resourceLoader,
            @Value("${rca.confidence.threshold:0.75}") double confidenceThreshold
    ) {
        this.rcaAnalyzer          = rcaAnalyzer;
        this.persistenceAdapter   = persistenceAdapter;
        this.resourceLoader       = resourceLoader;
        this.confidenceThreshold  = confidenceThreshold;
    }

    public Optional<TraceContext> findContextById(String traceId) {
        return loadDataset().stream()
                .filter(t -> traceId.equals(t.get("traceId")))
                .findFirst()
                .map(this::buildTraceContext);
    }

    public AccuracyReport evaluate() {
        var dataset = loadDataset();
        log.info("Starting evaluation — {} traces in dataset", dataset.size());

        var entries = dataset.stream()
                .map(trace -> {
                    try {
                        // Pausa de 5s para respetar el Rate Limit de Gemini Free (15 RPM)
                        Thread.sleep(5000);
                        return evaluateTrace(trace);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Optional.<EvaluationEntry>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        persistenceAdapter.saveAll(entries);

        var report = AccuracyReport.from(entries, confidenceThreshold);
        log.info("Evaluation complete — accuracy: {}%",
                String.format("%.1f", report.overallAccuracy() * 100));
        return report;
    }

    private Optional<EvaluationEntry> evaluateTrace(Map<String, Object> trace) {
        String traceId = (String) trace.get("traceId");
        try {
            var context = buildTraceContext(trace);
            var report  = rcaAnalyzer.analyze(context);
            var entry   = EvaluationEntry.of(
                    traceId,
                    (String) trace.get("expectedRootCauseSpan"),
                    (String) trace.get("expectedAnomalyType"),
                    report
            );
            log.debug("Trace {} — expected: {} / actual: {} — {}",
                    traceId,
                    trace.get("expectedAnomalyType"),
                    report.anomalyType(),
                    entry.correct() ? "CORRECT" : "WRONG");
            return Optional.of(entry);
        } catch (Exception e) {
            log.warn("Failed to evaluate trace {} — skipping. Reason: {}", traceId, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private TraceContext buildTraceContext(Map<String, Object> trace) {
        String traceId = (String) trace.get("traceId");
        var rawSpans   = (List<Map<String, Object>>) trace.get("spans");
        var rawMetrics = (List<Map<String, Object>>) trace.getOrDefault("metrics", List.of());

        var spans = rawSpans.stream()
                .map(this::buildSpan)
                .collect(Collectors.toList());

        var metrics = rawMetrics.stream()
                .map(this::buildMetric)
                .collect(Collectors.toList());

        var spanTree    = new SpanTree(traceId, spans);
        var anomalySpan = spanTree.slowestSpan();
        long baselineMs = anomalySpan.baselineMs() > 0 ? anomalySpan.baselineMs() : 200L;

        return new TraceContext(spanTree, anomalySpan, baselineMs, 2.0, metrics);
    }

    @SuppressWarnings("unchecked")
    private Span buildSpan(Map<String, Object> raw) {
        var attrs = (Map<String, Object>) raw.getOrDefault("attributes", Map.of());
        var stringAttrs = attrs.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));

        return new Span(
                (String) raw.get("spanId"),
                (String) raw.getOrDefault("parentSpanId", null),
                (String) raw.get("operationName"),
                (String) raw.get("serviceName"),
                toLong(raw.get("durationMs")),
                toLong(raw.getOrDefault("baselineMs", 0)),
                SpanStatus.OK,
                toLong(raw.getOrDefault("startTimeMs", 0)),
                stringAttrs
        );
    }

    private Metric buildMetric(Map<String, Object> raw) {
        return new Metric(
                (String) raw.get("serviceName"),
                (String) raw.get("metricName"),
                toDouble(raw.get("value")),
                Instant.now(),
                toDouble(raw.getOrDefault("threshold", 0.0))
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadDataset() {
        Resource resource = resourceLoader.getResource("classpath:/dataset/labeled_traces.json");
        try (InputStream is = resource.getInputStream()) {
            return MAPPER.readValue(is, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to load dataset from classpath:/dataset/labeled_traces.json. Error: {}", e.getMessage());
            throw new IllegalStateException("Failed to load evaluation dataset", e);
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }
}

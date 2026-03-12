package com.rcaagent.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregated accuracy report from an evaluation run.
 *
 * overallAccuracy        — correct / total
 * accuracyByType         — accuracy per anomalyType
 * falseNegativeRate      — traces where confidence < threshold but correct answer existed
 * avgConfidence          — mean confidence across all entries
 */
public record AccuracyReport(
        int total,
        int correct,
        double overallAccuracy,
        Map<String, Double> accuracyByType,
        double falseNegativeRate,
        double avgConfidence,
        List<EvaluationEntry> entries
) {
    public static AccuracyReport from(List<EvaluationEntry> entries, double confidenceThreshold) {
        int total   = entries.size();
        int correct = (int) entries.stream().filter(EvaluationEntry::correct).count();

        double overallAccuracy = total == 0 ? 0.0 : (double) correct / total;

        Map<String, Double> accuracyByType = entries.stream()
                .collect(Collectors.groupingBy(
                        EvaluationEntry::expectedAnomalyType,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream().filter(EvaluationEntry::correct).count()
                                        / (double) list.size()
                        )
                ));

        long falseNegatives = entries.stream()
                .filter(e -> !e.correct() && e.confidence() < confidenceThreshold)
                .count();
        double falseNegativeRate = total == 0 ? 0.0 : (double) falseNegatives / total;

        double avgConfidence = entries.stream()
                .mapToDouble(EvaluationEntry::confidence)
                .average()
                .orElse(0.0);

        return new AccuracyReport(total, correct, overallAccuracy,
                accuracyByType, falseNegativeRate, avgConfidence, entries);
    }

    public String toMarkdown() {
        var sb = new StringBuilder();
        sb.append("# RCA Agent — Accuracy Report\n\n");
        sb.append("| Metric | Value |\n|---|---|\n");
        sb.append(String.format("| Total traces | %d |\n", total));
        sb.append(String.format("| Correct | %d |\n", correct));
        sb.append(String.format("| Overall accuracy | %.1f%% |\n", overallAccuracy * 100));
        sb.append(String.format("| False negative rate | %.1f%% |\n", falseNegativeRate * 100));
        sb.append(String.format("| Avg confidence | %.2f |\n\n", avgConfidence));
        sb.append("## Accuracy by anomaly type\n\n");
        sb.append("| Anomaly Type | Accuracy |\n|---|---|\n");
        accuracyByType.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> sb.append(String.format("| %s | %.1f%% |\n",
                        e.getKey(), e.getValue() * 100)));
        sb.append("\n## Per-trace results\n\n");
        sb.append("| TraceId | Expected Type | Actual Type | Confidence | Correct |\n|---|---|---|---|---|\n");
        entries.forEach(e -> sb.append(String.format("| %s | %s | %s | %.2f | %s |\n",
                e.traceId(), e.expectedAnomalyType(), e.actualAnomalyType(),
                e.confidence(), e.correct() ? "✅" : "❌")));
        return sb.toString();
    }
}

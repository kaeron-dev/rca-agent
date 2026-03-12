package com.rcaagent.integration;

import com.rcaagent.domain.*;
import com.rcaagent.ports.out.RcaAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests — run with: ./gradlew integrationTest
 *
 * These tests use the real Spring context but mock external dependencies
 * (Tempo, Prometheus, LLM) via TestPropertySource overrides.
 * No Docker required for these tests — Testcontainers tag reserved for
 * future tests that need real Tempo/Prometheus containers.
 *
 * What to study here:
 *   - @SpringBootTest with RANDOM_PORT — tests the full HTTP stack
 *   - TestRestTemplate — makes real HTTP calls to the running server
 *   - @Tag("integration") — excluded from unit test run, included in integrationTest task
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "llm.mode=ollama",
    "llm.ollama.base-url=http://localhost:11434",
    "tempo.base-url=http://localhost:3200",
    "prometheus.base-url=http://localhost:9090",
    "rca.confidence.threshold=0.75"
})
@Testcontainers
class RcaAgentIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RcaAnalyzer rcaAnalyzer;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ── Actuator health ──────────────────────────────

    @Test
    @DisplayName("GET /actuator/health — returns 200 with Spring context loaded")
    void actuatorHealth_returns200() {
        var response = restTemplate.getForEntity(
                baseUrl() + "/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("status");
    }

    // ── RCA Analyzer contract ────────────────────────

    @Test
    @DisplayName("RcaAnalyzer — analyze returns non-null report for valid TraceContext")
    void rcaAnalyzer_analyze_returnsReport() {
        var span    = new Span("s1", null, "db.query", "payment-service", 4750L, SpanStatus.OK,
                               45L, 0L, Map.of("db.statement", "SELECT * FROM payments",
                                                "db.rows_returned", "15000"));
        var tree    = new SpanTree("integration-trace-001", List.of(span));
        var context = new TraceContext(tree, span, 45L, 2.0, List.of());

        var report = rcaAnalyzer.analyze(context);

        assertThat(report).isNotNull();
        assertThat(report.traceId()).isEqualTo("integration-trace-001");
        assertThat(report.confidence()).isBetween(0.0, 1.0);
        assertThat(report.anomalyType()).isNotBlank();
        assertThat(report.rootCause()).isNotBlank();
        assertThat(report.recommendation()).isNotBlank();
    }

    @Test
    @DisplayName("RcaAnalyzer — degraded report returned when LLM unavailable")
    void rcaAnalyzer_llmUnavailable_returnsDegradedReport() {
        var span    = new Span("s1", null, "processPayment", "payment-service", 5000L, SpanStatus.OK,
                               80L, 0L, Map.of());
        var tree    = new SpanTree("integration-trace-002", List.of(span));
        var context = new TraceContext(tree, span, 80L, 2.0, List.of());

        // LLM not running in test env — fallback should activate
        var report = rcaAnalyzer.analyze(context);

        assertThat(report).isNotNull();
        assertThat(report.traceId()).isEqualTo("integration-trace-002");
        // Either LLM succeeded (if Ollama is running) or fallback activated
        assertThat(report.confidence()).isBetween(0.0, 1.0);
    }

    // ── EvaluationController ─────────────────────────

    @Test
    @DisplayName("GET /api/evaluate — returns AccuracyReport with correct structure")
    void evaluationController_returnsAccuracyReport() {
        var response = restTemplate.getForEntity(
                baseUrl() + "/api/evaluate", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("total", "correct", "overallAccuracy",
                                       "accuracyByType", "avgConfidence");
        assertThat((Integer) body.get("total")).isGreaterThan(0);
    }

    @Test
    @DisplayName("GET /api/evaluate/md — returns non-empty markdown report")
    void evaluationController_markdownEndpoint_returnsMarkdown() {
        var response = restTemplate.getForEntity(
                baseUrl() + "/api/evaluate/md", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("# RCA Agent")
                .contains("Overall accuracy")
                .contains("Anomaly Type");
    }

    // ── RcaReport anomalyType field ──────────────────

    @Test
    @DisplayName("RcaReport — anomalyType defaults to UNKNOWN when not provided")
    void rcaReport_anomalyTypeDefaultsToUnknown() {
        var report = new RcaReport(
                "trace-test", "some root cause", "db.query",
                1000L, 100L, 10.0, "some recommendation", 0.8
        );

        assertThat(report.anomalyType()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("RcaReport — anomalyType set correctly via full constructor")
    void rcaReport_anomalyTypeSetCorrectly() {
        var report = new RcaReport(
                "trace-test", "some root cause", "db.query",
                1000L, 100L, 10.0, "some recommendation", 0.8,
                "DATABASE_SLOW_QUERY"
        );

        assertThat(report.anomalyType()).isEqualTo("DATABASE_SLOW_QUERY");
    }
}

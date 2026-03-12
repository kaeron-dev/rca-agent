package com.rcaagent.integration;

import com.rcaagent.adapters.out.tempo.TempoTraceAdapter;
import com.rcaagent.domain.exception.TraceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for TempoTraceAdapter using Testcontainers.
 *
 * @Tag("integration") — excluded from ./gradlew test
 * Run explicitly with: ./gradlew integrationTest (requires Docker)
 *
 * What to study here:
 *   - Separating unit and integration tests is a standard practice
 *   - Anyone can run unit tests without Docker
 *   - Integration tests run in CI with Docker or locally on demand
 */
@Tag("integration")
@Testcontainers
class TempoTraceAdapterIntegrationTest {

    @Container
    static GenericContainer<?> tempo = new GenericContainer<>(
            DockerImageName.parse("grafana/tempo:2.6.1"))
            .withExposedPorts(3200)
            .waitingFor(Wait.forHttp("/ready").forPort(3200).forStatusCode(200));

    @Test
    @DisplayName("nonexistent traceId → throws TraceNotFoundException")
    void findByTraceId_nonexistent_throwsTraceNotFoundException() {
        var baseUrl = "http://localhost:" + tempo.getMappedPort(3200);
        var adapter = new TempoTraceAdapter(baseUrl);

        assertThatThrownBy(() -> adapter.findByTraceId("nonexistent-trace-id"))
                .isInstanceOf(TraceNotFoundException.class)
                .hasMessageContaining("nonexistent-trace-id");
    }
}

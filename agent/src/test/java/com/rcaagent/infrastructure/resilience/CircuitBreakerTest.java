package com.rcaagent.infrastructure.resilience;

import com.rcaagent.adapters.out.tempo.TempoTraceAdapter;
import com.rcaagent.domain.exception.TraceNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Circuit Breaker behavior on TempoTraceAdapter.
 *
 * Simulates Tempo being down — all calls throw, circuit opens after threshold.
 *
 * What to study here:
 *   - @SpringBootTest loads the full Spring context — needed for Resilience4j AOP
 *   - CircuitBreakerRegistry lets us inspect circuit state programmatically
 *   - After threshold failures the circuit opens — subsequent calls fail fast without hitting Tempo
 *   - OPEN state protects downstream: no threads blocked waiting for a dead service
 */
@SpringBootTest
@TestPropertySource(properties = {
    "resilience4j.circuitbreaker.instances.tempo.sliding-window-size=3",
    "resilience4j.circuitbreaker.instances.tempo.failure-rate-threshold=100",
    "resilience4j.circuitbreaker.instances.tempo.minimum-number-of-calls=3",
    "resilience4j.circuitbreaker.instances.tempo.wait-duration-in-open-state=60s"
})
class CircuitBreakerTest {

    @Autowired
    private TempoTraceAdapter tempoTraceAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        // Reset to CLOSED before each test — state persists across tests in same context
        circuitBreakerRegistry.circuitBreaker("tempo").transitionToClosedState();
    }

    @Test
    @DisplayName("circuit transitions to OPEN after threshold failures — Tempo down simulation")
    void circuitBreaker_afterThresholdFailures_transitionsToOpen() {
        // Simulate Tempo being down — 3 consecutive failures
        for (int i = 1; i <= 3; i++) {
            int attempt = i;
            assertThatThrownBy(() -> tempoTraceAdapter.findByTraceId("nonexistent-" + attempt))
                    .isInstanceOf(TraceNotFoundException.class);
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tempo");
        assertThat(cb.getState())
                .as("Circuit should be OPEN after 3 consecutive failures")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("circuit OPEN — subsequent calls fail immediately without calling Tempo")
    void circuitBreaker_whenOpen_failsImmediatelyWithoutCallingTempo() {
        // Force circuit open
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tempo");
        cb.transitionToOpenState();

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> tempoTraceAdapter.findByTraceId("any-trace-id"))
                .isInstanceOf(Exception.class);
        long elapsed = System.currentTimeMillis() - start;

        // Should fail fast — well under the 5s timeout configured for Tempo
        assertThat(elapsed)
                .as("OPEN circuit should fail fast, not wait for timeout")
                .isLessThan(1000L);
    }

    @Test
    @DisplayName("circuit metrics — failure rate recorded correctly")
    void circuitBreaker_failureRateRecordedCorrectly() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tempo");
        cb.transitionToClosedState();

        // Force 3 failures to fill the sliding window
        for (int i = 1; i <= 3; i++) {
            int attempt = i;
            try { tempoTraceAdapter.findByTraceId("fail-" + attempt); } catch (Exception ignored) {}
        }

        assertThat(cb.getMetrics().getNumberOfFailedCalls())
                .as("All 3 calls should be recorded as failures")
                .isEqualTo(3);
    }
}

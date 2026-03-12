package com.rcaagent.infrastructure.resilience;

import com.rcaagent.adapters.out.tempo.TempoTraceAdapter;
import com.rcaagent.domain.exception.TraceNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Circuit Breaker behavior on TempoTraceAdapter.
 *
 * What to study here:
 *   - @SpringBootTest loads the full Spring context — needed for Resilience4j AOP
 *   - CircuitBreakerRegistry lets us inspect circuit state programmatically
 *   - After threshold failures the circuit opens — subsequent calls fail fast
 */
@SpringBootTest
@TestPropertySource(properties = {
    "resilience4j.circuitbreaker.instances.tempo.sliding-window-size=3",
    "resilience4j.circuitbreaker.instances.tempo.failure-rate-threshold=100",
    "resilience4j.circuitbreaker.instances.tempo.minimum-number-of-calls=3"
})
class CircuitBreakerTest {

    @Autowired
    private TempoTraceAdapter tempoTraceAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    @DisplayName("circuit breaker transitions to OPEN after threshold failures")
    void circuitBreaker_afterThresholdFailures_transitionsToOpen() {
        // Force 3 failures — Tempo not running in test context
        assertThatThrownBy(() -> tempoTraceAdapter.findByTraceId("nonexistent-1"))
                .isInstanceOf(TraceNotFoundException.class);
        assertThatThrownBy(() -> tempoTraceAdapter.findByTraceId("nonexistent-2"))
                .isInstanceOf(TraceNotFoundException.class);
        assertThatThrownBy(() -> tempoTraceAdapter.findByTraceId("nonexistent-3"))
                .isInstanceOf(TraceNotFoundException.class);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tempo");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}

package com.rcaagent.adapters.out.tempo;

import com.rcaagent.domain.SpanTree;
import com.rcaagent.domain.exception.TraceNotFoundException;
import com.rcaagent.ports.out.TraceRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Adapter — implements TraceRepository using Grafana Tempo HTTP API.
 *
 * Resilience patterns applied:
 *   @CircuitBreaker — opens after 50% failures in 10 calls, stays open 30s
 *   Fallback        — returns degraded response instead of propagating exception
 *
 * What to study here:
 *   - @CircuitBreaker is declarative — no try/catch boilerplate
 *   - fallbackMethod receives the exception — can log and return degraded response
 *   - The domain never knows about circuit breakers — resilience lives in the adapter
 */
@Component
public class TempoTraceAdapter implements TraceRepository {

    private static final Logger log = LoggerFactory.getLogger(TempoTraceAdapter.class);

    private final WebClient webClient;

    public TempoTraceAdapter(@Value("${tempo.base-url:http://localhost:3200}") String tempoBaseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(tempoBaseUrl)
                .build();
    }

    @Override
    @CircuitBreaker(name = "tempo", fallbackMethod = "fallback")
    public SpanTree findByTraceId(String traceId) {
        try {
            TempoSpanResponse response = webClient.get()
                    .uri("/api/traces/{traceId}", traceId)
                    .retrieve()
                    .bodyToMono(TempoSpanResponse.class)
                    .block();

            if (response == null || response.resourceSpans() == null || response.resourceSpans().isEmpty()) {
                throw new TraceNotFoundException(traceId);
            }

            return SpanTreeMapper.from(traceId, response);

        } catch (WebClientResponseException.NotFound e) {
            throw new TraceNotFoundException(traceId);
        }
    }

    /**
     * Fallback — called when circuit is open or Tempo is unreachable.
     * Fails fast with a clear exception instead of blocking threads.
     */
    public SpanTree fallback(String traceId, Exception e) {
        log.warn("Circuit breaker open for Tempo — traceId: {} — reason: {}", traceId, e.getMessage());
        throw new TraceNotFoundException("Tempo unavailable — circuit open for traceId: " + traceId);
    }
}

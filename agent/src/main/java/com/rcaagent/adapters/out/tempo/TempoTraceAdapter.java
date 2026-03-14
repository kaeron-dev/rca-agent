package com.rcaagent.adapters.out.tempo;

import com.rcaagent.domain.SpanTree;
import com.rcaagent.domain.exception.TraceNotFoundException;
import com.rcaagent.ports.out.TraceRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Adapter — implements TraceRepository using Grafana Tempo HTTP API.
 * Enhanced with smart polling to handle eventual consistency.
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
        int attempts = 0;
        while (attempts < 5) {
            try {
                TempoSpanResponse response = webClient.get()
                        .uri("/api/v2/traces/{traceId}", traceId)
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(TempoSpanResponse.class)
                        .block();

                if (response != null && response.trace() != null && response.trace().resourceSpans() != null) {
                    SpanTree tree = SpanTreeMapper.from(traceId, response);

                    // Integrity check: the demo always injects 3000ms. 
                    // We wait until we see at least one span > 500ms.
                    boolean hasAnomalousSpans = tree.spans().stream().anyMatch(s -> s.durationMs() > 500);

                    if (hasAnomalousSpans || attempts == 4) {
                        return tree;
                    }
                }
            } catch (Exception e) {
                log.debug("Tempo poll attempt {} failed for traceId: {}", attempts, traceId);
            }

            attempts++;
            log.info("Trace {} incomplete or anomaly not yet indexed, retrying in 3s... (attempt {})", traceId, attempts);
            try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        throw new TraceNotFoundException(traceId);
    }

    public SpanTree fallback(String traceId, Exception e) {
        log.warn("Circuit breaker open or trace not ready for Tempo — traceId: {} — reason: {}", traceId, e.getMessage());
        throw new TraceNotFoundException("Tempo data not ready or unavailable for traceId: " + traceId);
    }
}

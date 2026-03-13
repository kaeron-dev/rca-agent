package com.rcaagent.adapters.out.prometheus;

import com.rcaagent.ports.out.MetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Adapter — implements MetricsRepository using Prometheus HTTP API.
 *
 * Queries P99 latency baseline for a given service using PromQL.
 * If Prometheus is unavailable, returns a safe default baseline.
 *
 * What to study here:
 *   - PromQL query construction from Java
 *   - Defensive fallback: Prometheus is AP — may return stale or no data
 *   - Why the domain never sees Prometheus types
 */
@Component
public class PrometheusMetricsAdapter implements MetricsRepository {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsAdapter.class);
    private static final long DEFAULT_BASELINE_MS = 200L;

    private final WebClient webClient;

    public PrometheusMetricsAdapter(
            @Value("${prometheus.base-url:http://localhost:9090}") String prometheusBaseUrl
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(prometheusBaseUrl)
                .build();
    }

    /**
     * Queries Prometheus for the P99 latency of the given service.
     * PromQL: histogram_quantile(0.99, rate(http_server_duration_milliseconds_bucket{service=~"serviceName"}[5m]))
     *
     * Returns DEFAULT_BASELINE_MS if Prometheus is unavailable or has no data.
     * The RcaReport will include this baseline — the LLM uses it to contextualize the anomaly.
     */
    @Override
    public long getBaseline(String serviceName) {
        try {
            String query = String.format(
                "histogram_quantile(0.99, rate(http_server_duration_milliseconds_bucket{service=~\"%s\"}[5m]))",
                serviceName
            );

            PrometheusQueryResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/query")
                            .queryParam("query", query)
                            .build())
                    .retrieve()
                    .bodyToMono(PrometheusQueryResponse.class)
                    .block();

            return extractBaseline(response, serviceName);

        } catch (Exception e) {
            log.warn("Could not fetch baseline from Prometheus for service '{}': {}. Using default {}ms.",
                    serviceName, e.getMessage(), DEFAULT_BASELINE_MS);
            return DEFAULT_BASELINE_MS;
        }
    }

    private long extractBaseline(PrometheusQueryResponse response, String serviceName) {
        if (response == null || !"success".equals(response.status())) return DEFAULT_BASELINE_MS;
        if (response.data() == null || response.data().result().isEmpty()) {
            log.debug("No Prometheus data found for service '{}'. Using default baseline.", serviceName);
            return DEFAULT_BASELINE_MS;
        }

        var result = response.data().result().get(0);
        if (result.value() == null || result.value().size() < 2) return DEFAULT_BASELINE_MS;

        try {
            double valueSeconds = Double.parseDouble(result.value().get(1).toString());
            return Math.max(1L, Math.round(valueSeconds * 1000));
        } catch (NumberFormatException e) {
            return DEFAULT_BASELINE_MS;
        }
    }
}

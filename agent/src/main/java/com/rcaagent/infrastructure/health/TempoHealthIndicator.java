package com.rcaagent.infrastructure.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Health indicator for Grafana Tempo.
 * Exposed at /actuator/health — shows Tempo status alongside app health.
 *
 * What to study here:
 *   - HealthIndicator is Spring's extension point for custom health checks
 *   - Each dependency gets its own indicator — granular visibility
 *   - In production this feeds alerting systems
 */
@Component
public class TempoHealthIndicator implements HealthIndicator {

    private final WebClient webClient;

    public TempoHealthIndicator(
            @Value("${tempo.base-url:http://localhost:3200}") String tempoBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(tempoBaseUrl).build();
    }

    @Override
    public Health health() {
        try {
            webClient.get()
                    .uri("/ready")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return Health.up()
                    .withDetail("url", webClient.toString())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("reason", e.getMessage())
                    .build();
        }
    }
}

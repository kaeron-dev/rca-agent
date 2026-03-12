package com.rcaagent.infrastructure.health;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the LLM model.
 * Sends a minimal probe request to verify the model is reachable.
 *
 * What to study here:
 *   - We inject the fastModel bean — same model the adapter uses
 *   - A failed probe means the LLM is down — fallback chain will activate
 */
@Component
public class LlmHealthIndicator implements HealthIndicator {

    private final ChatLanguageModel fastModel;

    public LlmHealthIndicator(ChatLanguageModel fastModel) {
        this.fastModel = fastModel;
    }

    @Override
    public Health health() {
        try {
            fastModel.generate("ping");
            return Health.up()
                    .withDetail("model", fastModel.getClass().getSimpleName())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("model", fastModel.getClass().getSimpleName())
                    .withDetail("reason", e.getMessage())
                    .build();
        }
    }
}

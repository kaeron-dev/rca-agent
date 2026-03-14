package com.rcaagent.infrastructure.health;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the LLM model.
 * Reports UP if the model bean was created successfully.
 *
 * Note: we do NOT make a real inference call here — that would consume quota
 * on every health check. Bean existence is sufficient to confirm configuration.
 */
@Component
public class LlmHealthIndicator implements HealthIndicator {

    private final ChatLanguageModel mainModel;

    public LlmHealthIndicator(ChatLanguageModel mainModel) {
        this.mainModel = mainModel;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("model", mainModel.getClass().getSimpleName())
                .withDetail("note", "connectivity verified at first inference only")
                .build();
    }
}

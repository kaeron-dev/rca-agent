package com.rcaagent.infrastructure.config;

import com.rcaagent.adapters.out.llm.ComplexTraceStrategy;
import com.rcaagent.adapters.out.llm.ModelSelectionStrategy;
import com.rcaagent.adapters.out.llm.SimpleTraceStrategy;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Factory configuration for LLM models and strategy.
 *
 * What to study here:
 *   - Factory pattern: @Bean methods centralize object creation — no new Model() scattered in code
 *   - @Value: reads from application.properties or .env
 *   - Two modes controlled by llm.mode property:
 *       ollama → local Llama 3.2, no internet, 16GB RAM
 *       gemini → Gemini API, free tier, 8GB RAM
 *
 * Adding a new provider = add a new @Bean, zero changes to LangChain4jRcaAdapter.
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Value("${llm.mode:ollama}")
    private String llmMode;

    @Value("${llm.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${llm.ollama.model:llama3.2}")
    private String ollamaModel;

    @Value("${llm.openai.api-key:}")
    private String openAiApiKey;

    @Value("${llm.openai.model:gpt-4o-mini}")
    private String openAiModel;

    /**
     * Fast model — used for simple traces.
     * Ollama in local mode, OpenAI in cloud mode.
     */
    @Bean
    public ChatLanguageModel fastModel() {
        if ("ollama".equalsIgnoreCase(llmMode)) {
            log.info("LLM fast model: Ollama ({}) at {}", ollamaModel, ollamaBaseUrl);
            return OllamaChatModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(ollamaModel)
                    .timeout(Duration.ofSeconds(60))
                    .build();
        }
        log.info("LLM fast model: OpenAI ({})", openAiModel);
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(openAiModel)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Premium model — used for complex traces (anomalyFactor >= 10x).
     * Falls back to fast model if no premium key is configured.
     */
    @Bean
    public ChatLanguageModel premiumModel() {
        if ("ollama".equalsIgnoreCase(llmMode)) {
            log.info("LLM premium model: Ollama ({}) — same as fast in local mode", ollamaModel);
            return fastModel();
        }
        log.info("LLM premium model: OpenAI (gpt-4o)");
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-4o")
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * Strategy bean — selects which model to use per trace.
     * ComplexTraceStrategy routes severe anomalies to the premium model.
     */
    @Bean
    public ModelSelectionStrategy modelSelectionStrategy(
            ChatLanguageModel fastModel,
            ChatLanguageModel premiumModel
    ) {
        return new ComplexTraceStrategy(fastModel, premiumModel);
    }
}

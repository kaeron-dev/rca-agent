package com.rcaagent.infrastructure.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Factory configuration for LLM models with hybrid fallback (Gemini Flash -> Ollama).
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Value("${llm.mode:ollama}")
    private String llmMode;

    @Value("${llm.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${llm.ollama.model:llama3.2:1b}")
    private String ollamaModel;

    @Value("${llm.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${llm.gemini.model:gemini-3.1-flash-lite}")
    private String geminiModel;

    @Bean
    public ChatLanguageModel mainModel() {
        boolean isGeminiMode = "gemini".equalsIgnoreCase(llmMode);
        // Una API Key de Google suele tener ~39 caracteres. Validamos presencia y longitud mínima.
        boolean hasValidKey = geminiApiKey != null && geminiApiKey.trim().length() > 20;

        if (isGeminiMode && hasValidKey) {
            log.info("Configuring Gemini Flash ({}) as primary model", geminiModel);
            try {
                return GoogleAiGeminiChatModel.builder()
                        .apiKey(geminiApiKey)
                        .modelName(geminiModel)
                        .timeout(Duration.ofSeconds(15)) // Timeout agresivo para fallar rápido
                        .build();
            } catch (Exception e) {
                log.error("Failed to initialize Gemini, falling back to local model: {}", e.getMessage());
            }
        }

        log.warn("Gemini not configured or API Key invalid. Directing traffic to Ollama.");
        return backupModel();
    }

    @Bean
    public ChatLanguageModel backupModel() {
        log.info("Using Ollama ({}) as backup/local model", ollamaModel);
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .format("json") // Fuerza modo JSON en Ollama
                .timeout(Duration.ofSeconds(180))
                .logRequests(true)
                .logResponses(true)
                .maxRetries(0)
                .build();
    }
}

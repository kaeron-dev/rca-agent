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

    @Value("${llm.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Bean
    public ChatLanguageModel mainModel() {
        if ("gemini".equalsIgnoreCase(llmMode) && geminiApiKey != null && !geminiApiKey.isBlank()) {
            log.info("Using Gemini Flash ({}) as main model", geminiModel);
            return GoogleAiGeminiChatModel.builder()
                    .apiKey(geminiApiKey)
                    .modelName(geminiModel)
                    .timeout(Duration.ofSeconds(30))
                    .build();
        }
        return backupModel();
    }

    @Bean
    public ChatLanguageModel backupModel() {
        log.info("Using Ollama ({}) as backup/local model", ollamaModel);
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .maxRetries(0)
                .build();
    }
}

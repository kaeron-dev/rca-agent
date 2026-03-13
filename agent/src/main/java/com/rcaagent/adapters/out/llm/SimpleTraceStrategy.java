package com.rcaagent.adapters.out.llm;

import com.rcaagent.domain.TraceContext;
import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * Strategy for simple traces (anomalyFactor < 10x).
 * Uses the fast local model — Ollama with llama3.2.
 * Low cost, low latency, good enough for straightforward anomalies.
 */
public class SimpleTraceStrategy implements ModelSelectionStrategy {

    private final ChatLanguageModel fastModel;

    public SimpleTraceStrategy(ChatLanguageModel fastModel) {
        this.fastModel = fastModel;
    }

    @Override
    public ChatLanguageModel select(TraceContext context) {
        return fastModel;
    }
}

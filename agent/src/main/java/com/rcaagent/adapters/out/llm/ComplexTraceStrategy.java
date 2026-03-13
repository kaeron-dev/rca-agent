package com.rcaagent.adapters.out.llm;

import com.rcaagent.domain.TraceContext;
import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * Strategy for complex traces (anomalyFactor >= 10x or cascade failures).
 * Uses the premium model — Gemini Pro or GPT-4.
 * Higher cost, higher quality reasoning for severe anomalies.
 */
public class ComplexTraceStrategy implements ModelSelectionStrategy {

    private static final double COMPLEXITY_THRESHOLD = 10.0;

    private final ChatLanguageModel fastModel;
    private final ChatLanguageModel premiumModel;

    public ComplexTraceStrategy(ChatLanguageModel fastModel, ChatLanguageModel premiumModel) {
        this.fastModel    = fastModel;
        this.premiumModel = premiumModel;
    }

    @Override
    public ChatLanguageModel select(TraceContext context) {
        return context.anomalyFactor() >= COMPLEXITY_THRESHOLD ? premiumModel : fastModel;
    }
}

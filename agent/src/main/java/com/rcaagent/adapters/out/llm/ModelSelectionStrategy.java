package com.rcaagent.adapters.out.llm;

import com.rcaagent.domain.TraceContext;
import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * Strategy — decides which LLM model to use based on trace complexity.
 *
 * What to study here:
 *   - Strategy pattern: the algorithm (model selection) is encapsulated behind an interface
 *   - LangChain4jRcaAdapter never knows which model runs — it only calls select()
 *   - Adding a new strategy (e.g. ClaudeStrategy) = new class, zero changes to existing code (OCP)
 */
public interface ModelSelectionStrategy {
    ChatLanguageModel select(TraceContext context);
}

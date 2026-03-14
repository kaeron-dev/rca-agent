package com.rcaagent.adapters.out.llm;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.TraceContext;
import com.rcaagent.ports.out.RcaAnalyzer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapter — implements RcaAnalyzer using LangChain4j with automatic hybrid fallback.
 */
@Component
public class LangChain4jRcaAdapter implements RcaAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jRcaAdapter.class);

    private final ChatLanguageModel mainModel;
    private final ChatLanguageModel backupModel;

    // Memoria para evitar llamar a Gemini si sabemos que no hay cuota (1 minuto)
    private final AtomicLong lastQuotaErrorTime = new AtomicLong(0);
    private static final long QUOTA_RECOVERY_TIME_MS = 60_000;

    public LangChain4jRcaAdapter(ChatLanguageModel mainModel, ChatLanguageModel backupModel) {
        this.mainModel   = mainModel;
        this.backupModel = backupModel;
    }

    @Override
    @Bulkhead(name = "llm", fallbackMethod = "fallback")
    public synchronized RcaReport analyze(TraceContext context) {
        var prompt = RcaPromptBuilder.build(context);

        // Si falló por cuota hace poco, saltamos directo a local
        if (System.currentTimeMillis() - lastQuotaErrorTime.get() < QUOTA_RECOVERY_TIME_MS) {
            log.info("Skipping Gemini (quota recently exceeded) for trace {}. Using local model...", context.traceId());
            return useBackup(prompt, context);
        }

        try {
            log.info("Analyzing trace {} using standard model...", context.traceId());
            var response = mainModel.generate(prompt);
            return RcaReportParser.parse(response, context);
        } catch (Exception e) {
            if (isQuotaError(e)) {
                log.warn("Gemini quota exceeded. Remembering for 60s...");
                lastQuotaErrorTime.set(System.currentTimeMillis());
            }
            
            // SI EL BACKUP ES EL MISMO MODELO QUE EL MAIN, NO REINTENTAMOS (evita bucles si no hay key de Gemini)
            if (mainModel.equals(backupModel)) {
                log.error("Primary model (Local) failed and no alternate model is configured. Trace: {}", context.traceId());
                return fallback(context, e);
            }

            log.warn("Main model failed for trace {} (reason: {}). Switching to backup/local model...", 
                    context.traceId(), e.getMessage());
            return useBackup(prompt, context);
        }
    }

    private RcaReport useBackup(String unusedPrompt, TraceContext context) {
        try {
             log.info("Calling backup model (Ollama) with LITE prompt for trace {}...", context.traceId());
             // Generamos un nuevo prompt LITE específicamente para Ollama
             var litePrompt = RcaPromptBuilder.build(context, true);
             var response = backupModel.generate(litePrompt);
             return RcaReportParser.parse(response, context);
        } catch (Exception e) {
             log.error("Backup model also failed for trace {}: {}", context.traceId(), e.getMessage());
             return fallback(context, e);
        }
    }

    private boolean isQuotaError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("429") || msg.contains("quota") || msg.contains("resource_exhausted");
    }

    /**
     * Fallback — llamado solo si AMBOS modelos fallan o el sistema está saturado.
     */
    public RcaReport fallback(TraceContext context, Exception e) {
        log.error("All LLM models failed or bulkhead full for trace {}. Reason: {}", context.traceId(), e.getMessage());

        return new RcaReport(
                context.traceId(),
                "LLM unavailable — manual investigation required for span: "
                        + context.anomalySpan().operationName()
                        + " in " + context.anomalySpan().serviceName(),
                context.anomalySpan().operationName(),
                context.anomalySpan().durationMs(),
                context.baselineMs(),
                context.anomalyFactor(),
                "LLM service unavailable. Check " + context.anomalySpan().serviceName()
                        + " — span took " + context.anomalyFactor() + "x longer than baseline.",
                0.0
        );
    }
}

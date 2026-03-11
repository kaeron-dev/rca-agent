package com.rcaagent.ports.in;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.exception.TraceNotFoundException;

public interface AnalyzeTraceUseCase {

    /**
     * @throws TraceNotFoundException if traceId does not exist in Tempo
     */
    RcaReport analyze(String traceId);
}

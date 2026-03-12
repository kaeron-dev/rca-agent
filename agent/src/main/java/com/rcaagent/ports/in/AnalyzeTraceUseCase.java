package com.rcaagent.ports.in;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.exception.TraceNotFoundException;

public interface AnalyzeTraceUseCase {

    RcaReport analyze(String traceId);
}

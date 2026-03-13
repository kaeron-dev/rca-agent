package com.rcaagent.ports.out;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.domain.TraceContext;

public interface RcaAnalyzer {

    RcaReport analyze(TraceContext context);
}

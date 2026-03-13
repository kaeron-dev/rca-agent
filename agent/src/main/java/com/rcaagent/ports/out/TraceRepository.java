package com.rcaagent.ports.out;

import com.rcaagent.domain.SpanTree;
import com.rcaagent.domain.exception.TraceNotFoundException;

public interface TraceRepository {

    SpanTree findByTraceId(String traceId);
}

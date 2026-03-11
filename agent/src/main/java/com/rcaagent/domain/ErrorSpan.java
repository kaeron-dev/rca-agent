package com.rcaagent.domain;

public record ErrorSpan(
        String spanId,
        String operationName,
        String serviceName,
        int statusCode,
        String errorMessage
) implements AnomalyType {}

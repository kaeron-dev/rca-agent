package com.rcaagent.domain;

public sealed interface AnomalyType
        permits SlowSpan, ErrorSpan, HighLatency {}

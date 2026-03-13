package com.rcaagent.ports.out;

public interface MetricsRepository {

    long getBaseline(String serviceName);
}

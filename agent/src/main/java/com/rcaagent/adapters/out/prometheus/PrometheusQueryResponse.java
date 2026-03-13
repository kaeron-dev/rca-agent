package com.rcaagent.adapters.out.prometheus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Raw Prometheus HTTP API response — never exposed outside this package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusQueryResponse(
        String status,
        PrometheusData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrometheusData(
            String resultType,
            List<PrometheusResult> result
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrometheusResult(
            List<Object> value
    ) {}
}

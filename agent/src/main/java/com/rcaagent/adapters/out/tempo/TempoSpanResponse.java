package com.rcaagent.adapters.out.tempo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Raw Tempo API response — deserialized from JSON, never exposed outside this package.
 * The adapter maps this to domain objects via SpanTreeMapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TempoSpanResponse(
        String traceID,
        List<TempoResourceSpan> resourceSpans
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempoResourceSpan(
            List<TempoScopeSpan> scopeSpans,
            Map<String, Object> resource
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempoScopeSpan(
            List<TempoSpan> spans
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempoSpan(
            String spanId,
            String parentSpanId,
            String name,
            String kind,
            String startTimeUnixNano,
            String endTimeUnixNano,
            List<Map<String, Object>> attributes,
            TempoStatus status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempoStatus(
            Integer code
    ) {}
}

package com.rcaagent.adapters.out.tempo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Raw Tempo API response — deserialized from JSON, never exposed outside this package.
 * The adapter maps this to domain objects via SpanTreeMapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TempoSpanResponse(
        @JsonProperty("trace")
        TempoTrace trace
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempoTrace(
            @JsonProperty("resourceSpans")
            List<TempoResourceSpan> resourceSpans
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempoResourceSpan(
            @JsonProperty("scopeSpans")
            List<TempoScopeSpan> scopeSpans,
            @JsonProperty("resource")
            Map<String, Object> resource
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempoScopeSpan(
            @JsonProperty("spans")
            List<TempoSpan> spans
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempoSpan(
            @JsonProperty("spanId")
            String spanId,
            @JsonProperty("parentSpanId")
            String parentSpanId,
            @JsonProperty("name")
            String name,
            @JsonProperty("kind")
            String kind,
            @JsonProperty("startTimeUnixNano")
            String startTimeUnixNano,
            @JsonProperty("endTimeUnixNano")
            String endTimeUnixNano,
            @JsonProperty("attributes")
            List<Map<String, Object>> attributes,
            @JsonProperty("status")
            TempoStatus status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TempoStatus(
            @JsonProperty("code")
            Integer code
    ) {}
}

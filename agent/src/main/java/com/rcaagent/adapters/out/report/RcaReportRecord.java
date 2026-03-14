package com.rcaagent.adapters.out.report;

import com.rcaagent.domain.RcaReport;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persistence Entity for RCA Reports.
 * Maps the domain RcaReport to the 'rca_reports' H2 table.
 *
 * Design Note: Fields are expanded into columns rather than serialized as JSON
 * to allow future SQL-based analytics on root causes and confidence levels.
 */
@Entity
@Table(name = "rca_reports")
public class RcaReportRecord {

    @Id
    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "root_cause", length = 1000)
    private String rootCause;

    @Column(name = "anomaly_span")
    private String anomalySpan;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "baseline_ms")
    private long baselineMs;

    @Column(name = "anomaly_factor")
    private double anomalyFactor;

    @Column(name = "recommendation", length = 1000)
    private String recommendation;

    @Column(name = "confidence")
    private double confidence;

    @Column(name = "anomaly_type")
    private String anomalyType;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    public RcaReportRecord() {}

    /**
     * Converts a domain object to a database record.
     */
    public RcaReportRecord(RcaReport report) {
        this.traceId = report.traceId();
        this.rootCause = report.rootCause();
        this.anomalySpan = report.anomalySpan();
        this.durationMs = report.durationMs();
        this.baselineMs = report.baselineMs();
        this.anomalyFactor = report.anomalyFactor();
        this.recommendation = report.recommendation();
        this.confidence = report.confidence();
        this.anomalyType = report.anomalyType();
        this.analyzedAt = LocalDateTime.now();
    }

    /**
     * Converts this database record back to a domain object.
     */
    public RcaReport toDomain() {
        return new RcaReport(
                traceId,
                rootCause,
                anomalySpan,
                durationMs,
                baselineMs,
                anomalyFactor,
                recommendation,
                confidence,
                anomalyType
        );
    }
}

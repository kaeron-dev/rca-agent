package com.rcaagent.adapters.out.baseline;

import jakarta.persistence.*;

/**
 * JPA entity — persists baseline history in H2.
 * Stays in the adapter layer — domain never sees JPA annotations.
 */
@Entity
@Table(name = "baseline_history")
public class BaselineRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private long baselineMs;

    @Column(nullable = false)
    private java.time.Instant recordedAt;

    protected BaselineRecord() {}

    public BaselineRecord(String serviceName, long baselineMs) {
        this.serviceName = serviceName;
        this.baselineMs  = baselineMs;
        this.recordedAt  = java.time.Instant.now();
    }

    public String getServiceName() { return serviceName; }
    public long getBaselineMs()    { return baselineMs; }
    public java.time.Instant getRecordedAt() { return recordedAt; }
}

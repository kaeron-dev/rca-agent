package com.rcaagent.adapters.out.evaluation;

import com.rcaagent.domain.EvaluationEntry;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity — persists evaluation run results in H2.
 * Stays in the adapter layer — domain never sees JPA annotations.
 */
@Entity
@Table(name = "evaluation_history")
public class EvaluationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String traceId;

    @Column(nullable = false)
    private String expectedRootCauseSpan;

    @Column(nullable = false)
    private String actualRootCauseSpan;

    @Column(nullable = false)
    private String expectedAnomalyType;

    @Column(nullable = false)
    private String actualAnomalyType;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private Instant evaluatedAt;

    protected EvaluationRecord() {}

    public static EvaluationRecord from(EvaluationEntry entry) {
        var record = new EvaluationRecord();
        record.traceId               = entry.traceId();
        record.expectedRootCauseSpan = entry.expectedRootCauseSpan();
        record.actualRootCauseSpan   = entry.actualRootCauseSpan();
        record.expectedAnomalyType   = entry.expectedAnomalyType();
        record.actualAnomalyType     = entry.actualAnomalyType();
        record.confidence            = entry.confidence();
        record.correct               = entry.correct();
        record.evaluatedAt           = entry.evaluatedAt();
        return record;
    }

    public String getTraceId()             { return traceId; }
    public String getExpectedAnomalyType() { return expectedAnomalyType; }
    public String getActualAnomalyType()   { return actualAnomalyType; }
    public double getConfidence()          { return confidence; }
    public boolean isCorrect()             { return correct; }
    public Instant getEvaluatedAt()        { return evaluatedAt; }
}

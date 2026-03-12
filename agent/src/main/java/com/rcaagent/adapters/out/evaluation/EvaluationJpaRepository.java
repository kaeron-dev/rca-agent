package com.rcaagent.adapters.out.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EvaluationJpaRepository extends JpaRepository<EvaluationRecord, Long> {

    @Query("SELECT e FROM EvaluationRecord e ORDER BY e.evaluatedAt DESC")
    List<EvaluationRecord> findLatestRun();

    @Query("SELECT AVG(CASE WHEN e.correct = true THEN 1.0 ELSE 0.0 END) FROM EvaluationRecord e WHERE e.expectedAnomalyType = :type")
    Double findAccuracyByType(String type);
}

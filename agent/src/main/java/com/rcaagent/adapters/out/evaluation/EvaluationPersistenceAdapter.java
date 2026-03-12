package com.rcaagent.adapters.out.evaluation;

import com.rcaagent.domain.EvaluationEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EvaluationPersistenceAdapter {

    private static final Logger log = LoggerFactory.getLogger(EvaluationPersistenceAdapter.class);

    private final EvaluationJpaRepository repository;

    public EvaluationPersistenceAdapter(EvaluationJpaRepository repository) {
        this.repository = repository;
    }

    public void saveAll(List<EvaluationEntry> entries) {
        var records = entries.stream()
                .map(EvaluationRecord::from)
                .toList();
        repository.saveAll(records);
        log.info("Persisted {} evaluation entries to H2", records.size());
    }
}

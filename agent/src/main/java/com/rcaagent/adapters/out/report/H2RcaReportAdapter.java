package com.rcaagent.adapters.out.report;

import com.rcaagent.domain.RcaReport;
import com.rcaagent.ports.out.RcaReportRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * H2 Implementation of the RcaReportRepository port.
 * Provides immutable storage for trace analyses.
 *
 * Design Decision: Reports are cached locally to minimize expensive LLM inference
 * and ensure consistent diagnostics for the same traceId.
 */
@Component
public class H2RcaReportAdapter implements RcaReportRepository {

    private final RcaReportJpaRepository repository;

    public H2RcaReportAdapter(RcaReportJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(RcaReport report) {
        repository.save(new RcaReportRecord(report));
    }

    @Override
    public Optional<RcaReport> findByTraceId(String traceId) {
        return repository.findById(traceId).map(RcaReportRecord::toDomain);
    }
}

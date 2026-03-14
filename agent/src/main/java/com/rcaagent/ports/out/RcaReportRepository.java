package com.rcaagent.ports.out;

import com.rcaagent.domain.RcaReport;
import java.util.Optional;

/**
 * Port for persisting and retrieving RCA reports.
 * Part of the Hexagonal Architecture — decouples domain from persistence.
 */
public interface RcaReportRepository {
    void save(RcaReport report);
    Optional<RcaReport> findByTraceId(String traceId);
}

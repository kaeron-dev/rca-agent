package com.rcaagent.adapters.out.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RcaReportJpaRepository extends JpaRepository<RcaReportRecord, String> {
}

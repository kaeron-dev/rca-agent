package com.rcaagent.adapters.out.baseline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for BaselineRecord.
 * Stays in the adapter layer — domain never imports this.
 */
public interface BaselineJpaRepository extends JpaRepository<BaselineRecord, Long> {

    @Query("SELECT AVG(b.baselineMs) FROM BaselineRecord b WHERE b.serviceName = :serviceName")
    Optional<Double> findAverageBaselineByServiceName(@Param("serviceName") String serviceName);
}

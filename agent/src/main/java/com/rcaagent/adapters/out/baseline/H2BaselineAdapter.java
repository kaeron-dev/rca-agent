package com.rcaagent.adapters.out.baseline;

import com.rcaagent.ports.out.MetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class H2BaselineAdapter implements MetricsRepository {

    private static final Logger log = LoggerFactory.getLogger(H2BaselineAdapter.class);
    private static final long DEFAULT_BASELINE_MS = 200L;

    private final BaselineJpaRepository repository;

    public H2BaselineAdapter(BaselineJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public long getBaseline(String serviceName) {
        return repository.findAverageBaselineByServiceName(serviceName)
                .map(avg -> {
                    long baseline = Math.max(1L, Math.round(avg));
                    log.debug("Baseline for '{}': {}ms (from H2 history)", serviceName, baseline);
                    return baseline;
                })
                .orElseGet(() -> {
                    log.debug("No baseline history for '{}'. Using default {}ms.", serviceName, DEFAULT_BASELINE_MS);
                    return DEFAULT_BASELINE_MS;
                });
    }

    public void recordBaseline(String serviceName, long baselineMs) {
        repository.save(new BaselineRecord(serviceName, baselineMs));
    }
}

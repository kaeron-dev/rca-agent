package com.rcaagent.payment.application

/**
 * Shared demo state for anomaly injection.
 *
 * LIMITATION (Phase 1-5):
 * @Volatile guarantees visibility within a single JVM process.
 * Does NOT work across multiple pods — each pod has its own JVM.
 *
 * PRODUCTION SOLUTION (Phase 5 — Kubernetes):
 * Replace with Redis shared state:
 *   - LatencyController writes to Redis key="demo:latency:payment-service"
 *   - ErrorController writes to Redis key="demo:error-rate:payment-service"
 *   - PaymentService reads from Redis before processing
 *   - All pods share the same Redis instance
 */
object DemoConfig {
    @Volatile
    var latencyMs: Long = 0

    /**
     * Error injection rate — percentage of payments that fail artificially.
     * Range: 0 (no errors) to 100 (all payments fail).
     * Used to simulate downstream failures and cascade scenarios.
     */
    @Volatile
    var errorRatePct: Int = 0
}

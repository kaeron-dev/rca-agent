package com.rcaagent.inventory.application

/**
 * Shared demo state for anomaly injection in inventory-service.
 *
 * LIMITATION (Phase 1-5):
 * @Volatile guarantees visibility within a single JVM process.
 * Does NOT work across multiple pods — each pod has its own JVM.
 *
 * PRODUCTION SOLUTION (Phase 5 — Kubernetes):
 * Replace with Redis shared state:
 *   - ErrorController writes to Redis key="demo:error-rate:inventory-service"
 *   - InventoryService reads from Redis before processing
 */
object DemoConfig {
    /**
     * Error injection rate — percentage of reservations that fail artificially.
     * Range: 0 (no errors) to 100 (all reservations fail).
     * Used to simulate downstream failures and cascade scenarios.
     */
    @Volatile
    var errorRatePct: Int = 0

    /**
     * Latency injection — ms of artificial delay per reservation.
     * Used to simulate slow inventory checks in cascade scenarios.
     */
    @Volatile
    var latencyMs: Long = 0
}

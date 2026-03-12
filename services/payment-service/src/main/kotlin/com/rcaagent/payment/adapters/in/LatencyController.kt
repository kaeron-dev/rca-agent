package com.rcaagent.payment.adapters.`in`

import com.rcaagent.payment.application.DemoConfig
import org.springframework.web.bind.annotation.*

/**
 * Demo controller for anomaly injection.
 *
 * Endpoints:
 *   POST /demo/latency?ms=N      — inject N ms of artificial latency
 *   POST /demo/latency/reset     — reset latency to 0
 *   POST /demo/errors?rate=N     — inject N% error rate (0-100)
 *   POST /demo/errors/reset      — reset error rate to 0
 *   POST /demo/reset             — reset all anomaly injections
 *   GET  /demo/status            — current injection state
 *
 * What to study here:
 *   - SRP: demo concerns are isolated from business logic
 *   - @Volatile guarantees JVM-level visibility — documented limitation for multi-pod
 */
@RestController
@RequestMapping("/demo")
class LatencyController {

    @PostMapping("/latency")
    fun setLatency(@RequestParam ms: Long): Map<String, Any> {
        require(ms >= 0) { "latencyMs must be >= 0" }
        DemoConfig.latencyMs = ms
        return mapOf("latencyMs" to ms, "status" to "injected")
    }

    @PostMapping("/latency/reset")
    fun resetLatency(): Map<String, Any> {
        DemoConfig.latencyMs = 0
        return mapOf("latencyMs" to 0, "status" to "reset")
    }

    @PostMapping("/errors")
    fun setErrorRate(@RequestParam rate: Int): Map<String, Any> {
        require(rate in 0..100) { "errorRatePct must be between 0 and 100" }
        DemoConfig.errorRatePct = rate
        return mapOf("errorRatePct" to rate, "status" to "injected")
    }

    @PostMapping("/errors/reset")
    fun resetErrors(): Map<String, Any> {
        DemoConfig.errorRatePct = 0
        return mapOf("errorRatePct" to 0, "status" to "reset")
    }

    @PostMapping("/reset")
    fun resetAll(): Map<String, Any> {
        DemoConfig.latencyMs = 0
        DemoConfig.errorRatePct = 0
        return mapOf("latencyMs" to 0, "errorRatePct" to 0, "status" to "reset")
    }

    @GetMapping("/status")
    fun status(): Map<String, Any> =
        mapOf(
            "latencyMs"    to DemoConfig.latencyMs,
            "errorRatePct" to DemoConfig.errorRatePct
        )
}

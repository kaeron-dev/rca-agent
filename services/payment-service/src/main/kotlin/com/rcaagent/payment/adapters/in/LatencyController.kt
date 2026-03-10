package com.rcaagent.payment.adapters.`in`

import com.rcaagent.payment.application.DemoConfig
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/demo")
class LatencyController {

    @PostMapping("/latency")
    fun setLatency(@RequestParam ms: Long): String {
        DemoConfig.latencyMs = ms
        return "Latency set to ${ms}ms"
    }

    @PostMapping("/latency/reset")
    fun resetLatency(): String {
        DemoConfig.latencyMs = 0
        return "Latency reset"
    }
}

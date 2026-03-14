package com.rcaagent.order

import io.opentelemetry.api.trace.Span
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.WebFilter

@SpringBootApplication
class OrderServiceApplication {
    @Bean
    fun traceResponseFilter(): WebFilter = WebFilter { exchange, chain ->
        exchange.response.headers.add("X-Trace-Id", Span.current().spanContext.traceId)
        chain.filter(exchange)
    }
}

fun main(args: Array<String>) {
    runApplication<OrderServiceApplication>(*args)
}

@Configuration
class WebClientConfig {
    @Bean
    fun webClient(): WebClient = WebClient.builder().build()
}

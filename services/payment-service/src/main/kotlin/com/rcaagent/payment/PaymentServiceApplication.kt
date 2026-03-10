package com.rcaagent.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@SpringBootApplication
class PaymentServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentServiceApplication>(*args)
}

@Configuration
class WebClientConfig {
    @Bean
    fun webClient(): WebClient = WebClient.builder().build()
}

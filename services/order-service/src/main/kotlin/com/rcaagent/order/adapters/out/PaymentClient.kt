package com.rcaagent.order.adapters.out

import com.rcaagent.order.ports.out.PaymentPort
import com.rcaagent.order.ports.out.PaymentResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

data class PaymentRequest(val orderId: String, val amount: Double)

@Component
class PaymentClient(private val webClient: WebClient) : PaymentPort {
    override suspend fun confirmPayment(orderId: String, amount: Double): PaymentResponse {
        return webClient.post()
            .uri("http://payment-service:8082/payments")
            .bodyValue(PaymentRequest(orderId, amount))
            .retrieve()
            .awaitBody()
    }
}

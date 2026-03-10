package com.rcaagent.payment.adapters.`in`

import com.rcaagent.payment.ports.`in`.ProcessPaymentUseCase
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val processPaymentUseCase: ProcessPaymentUseCase
) {
    @PostMapping
    suspend fun processPayment(@RequestBody request: PaymentRequest): PaymentResponse {
        val payment = processPaymentUseCase.processPayment(request.orderId, request.amount)
        return PaymentResponse(
            paymentId = payment.id,
            orderId   = payment.orderId,
            status    = payment.status.name
        )
    }
}

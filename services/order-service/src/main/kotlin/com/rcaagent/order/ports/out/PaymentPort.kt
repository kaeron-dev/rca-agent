package com.rcaagent.order.ports.out

data class PaymentResponse(val confirmed: Boolean)

interface PaymentPort {
    suspend fun confirmPayment(orderId: String, amount: Double): PaymentResponse
}

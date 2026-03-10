package com.rcaagent.payment.ports.`in`

import com.rcaagent.payment.domain.Payment

interface ProcessPaymentUseCase {
    suspend fun processPayment(orderId: String, amount: Double): Payment
}

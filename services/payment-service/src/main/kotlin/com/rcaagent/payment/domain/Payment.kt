package com.rcaagent.payment.domain

import java.util.UUID

data class Payment(
    val id: String = UUID.randomUUID().toString(),
    val orderId: String,
    val amount: Double,
    val status: PaymentStatus = PaymentStatus.PENDING
)

enum class PaymentStatus {
    PENDING, CONFIRMED, FAILED
}

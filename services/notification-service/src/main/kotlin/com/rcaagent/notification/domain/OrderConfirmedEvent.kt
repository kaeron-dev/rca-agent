package com.rcaagent.notification.domain

data class OrderConfirmedEvent(
    val orderId: String,
    val productId: String,
    val quantity: Int,
    val amount: Double,
    val status: String
)

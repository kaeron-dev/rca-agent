package com.rcaagent.order.domain

import java.util.UUID

data class Order(
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val quantity: Int,
    val status: OrderStatus = OrderStatus.PENDING
)

enum class OrderStatus {
    PENDING, CONFIRMED, FAILED
}

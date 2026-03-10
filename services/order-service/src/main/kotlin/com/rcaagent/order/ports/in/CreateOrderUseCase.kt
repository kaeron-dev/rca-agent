package com.rcaagent.order.ports.`in`

import com.rcaagent.order.domain.Order

interface CreateOrderUseCase {
    suspend fun createOrder(productId: String, quantity: Int): Order
}

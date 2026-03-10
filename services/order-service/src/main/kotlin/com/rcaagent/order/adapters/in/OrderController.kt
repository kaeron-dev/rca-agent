package com.rcaagent.order.adapters.`in`

import com.rcaagent.order.ports.`in`.CreateOrderUseCase
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/orders")
class OrderController(
    private val createOrderUseCase: CreateOrderUseCase
) {
    @PostMapping
    suspend fun createOrder(@RequestBody request: OrderRequest): OrderResponse {
        val order = createOrderUseCase.createOrder(request.productId, request.quantity)
        return OrderResponse(
            orderId = order.id,
            status  = order.status.name
        )
    }
}

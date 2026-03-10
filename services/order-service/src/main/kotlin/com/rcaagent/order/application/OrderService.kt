package com.rcaagent.order.application

import com.rcaagent.order.domain.Order
import com.rcaagent.order.domain.OrderStatus
import com.rcaagent.order.ports.`in`.CreateOrderUseCase
import com.rcaagent.order.ports.out.InventoryPort
import com.rcaagent.order.ports.out.PaymentPort
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderService(
    private val paymentPort: PaymentPort,
    private val inventoryPort: InventoryPort
) : CreateOrderUseCase {

    override suspend fun createOrder(productId: String, quantity: Int): Order = coroutineScope {
        val order = Order(
            id        = UUID.randomUUID().toString(),
            productId = productId,
            quantity  = quantity,
            status    = OrderStatus.PENDING
        )

        // TODO Fase 4 — agregar Circuit Breaker con Resilience4j
        // Si payment-service cae, el Circuit Breaker abre y devuelve FAILED
        // sin esperar el timeout

        val paymentDeferred   = async { paymentPort.confirmPayment(order.id, 100.0) }
        val inventoryDeferred = async { inventoryPort.reserveInventory(productId, quantity) }

        val payment   = paymentDeferred.await()
        val inventory = inventoryDeferred.await()

        if (payment.confirmed && inventory.reserved)
            order.copy(status = OrderStatus.CONFIRMED)
        else
            order.copy(status = OrderStatus.FAILED)
    }
}

package com.rcaagent.payment.application

import com.rcaagent.payment.domain.Payment
import com.rcaagent.payment.domain.PaymentStatus
import com.rcaagent.payment.ports.`in`.ProcessPaymentUseCase
import com.rcaagent.payment.ports.out.InventoryPort
import org.springframework.stereotype.Service
import kotlinx.coroutines.delay
import java.util.UUID

@Service
class PaymentService(
    private val inventoryPort: InventoryPort
) : ProcessPaymentUseCase {

    override suspend fun processPayment(orderId: String, amount: Double): Payment {
        val payment = Payment(
            id      = UUID.randomUUID().toString(),
            orderId = orderId,
            amount  = amount,
            status  = PaymentStatus.PENDING
        )

        // Aplica latencia artificial si esta configurada (solo para demo)
        if (DemoConfig.latencyMs > 0) {
            delay(DemoConfig.latencyMs)
        }

        // TODO Fase 4 — agregar Circuit Breaker con Resilience4j
        val inventory = inventoryPort.reserveInventory("default-product", 1)

        return if (inventory.reserved)
            payment.copy(status = PaymentStatus.CONFIRMED)
        else
            payment.copy(status = PaymentStatus.FAILED)
    }
}

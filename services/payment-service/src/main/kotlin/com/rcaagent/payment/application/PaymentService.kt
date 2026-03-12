package com.rcaagent.payment.application

import com.rcaagent.payment.domain.Payment
import com.rcaagent.payment.domain.PaymentStatus
import com.rcaagent.payment.ports.`in`.ProcessPaymentUseCase
import com.rcaagent.payment.ports.out.InventoryPort
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
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

        // Inject artificial latency if configured (demo only)
        if (DemoConfig.latencyMs > 0) {
            delay(DemoConfig.latencyMs)
        }

        // Inject artificial errors if configured (demo only)
        // Uses thread-safe random to avoid bias in concurrent requests
        if (DemoConfig.errorRatePct > 0 && (1..100).random() <= DemoConfig.errorRatePct) {
            return payment.copy(status = PaymentStatus.FAILED)
        }

        val inventory = inventoryPort.reserveInventory("default-product", 1)
        return if (inventory.reserved)
            payment.copy(status = PaymentStatus.CONFIRMED)
        else
            payment.copy(status = PaymentStatus.FAILED)
    }
}

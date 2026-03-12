package com.rcaagent.inventory.application

import com.rcaagent.inventory.domain.Reservation
import com.rcaagent.inventory.domain.ReservationStatus
import com.rcaagent.inventory.ports.`in`.ReserveInventoryUseCase
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class InventoryService : ReserveInventoryUseCase {

    // TODO Phase 5 — replace with real stock in DB
    override suspend fun reserveInventory(productId: String, quantity: Int): Reservation {
        val reservation = Reservation(
            id       = UUID.randomUUID().toString(),
            orderId  = productId,
            quantity = quantity,
            status   = ReservationStatus.PENDING
        )

        // Inject artificial latency if configured (demo only)
        if (DemoConfig.latencyMs > 0) {
            delay(DemoConfig.latencyMs)
        }

        // Inject artificial errors if configured (demo only)
        if (DemoConfig.errorRatePct > 0 && (1..100).random() <= DemoConfig.errorRatePct) {
            return reservation.copy(status = ReservationStatus.FAILED)
        }

        return reservation.copy(status = ReservationStatus.RESERVED)
    }
}

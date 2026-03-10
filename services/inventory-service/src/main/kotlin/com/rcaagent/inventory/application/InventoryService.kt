package com.rcaagent.inventory.application

import com.rcaagent.inventory.domain.Reservation
import com.rcaagent.inventory.domain.ReservationStatus
import com.rcaagent.inventory.ports.`in`.ReserveInventoryUseCase
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class InventoryService : ReserveInventoryUseCase {

    // TODO Fase 5 — reemplazar por stock real en DB
    // Por ahora siempre hay stock disponible
    override suspend fun reserveInventory(productId: String, quantity: Int): Reservation {
        val reservation = Reservation(
            id       = UUID.randomUUID().toString(),
            orderId  = productId,
            quantity = quantity,
            status   = ReservationStatus.PENDING
        )

        // TODO Fase 4 — agregar Circuit Breaker con Resilience4j
        return reservation.copy(status = ReservationStatus.RESERVED)
    }
}

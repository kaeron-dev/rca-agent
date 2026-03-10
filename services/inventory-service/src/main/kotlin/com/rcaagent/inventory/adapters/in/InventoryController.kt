package com.rcaagent.inventory.adapters.`in`

import com.rcaagent.inventory.ports.`in`.ReserveInventoryUseCase
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/inventory")
class InventoryController(
    private val reserveInventoryUseCase: ReserveInventoryUseCase
) {
    @PostMapping("/reserve")
    suspend fun reserveInventory(@RequestBody request: ReservationRequest): ReservationResponse {
        val reservation = reserveInventoryUseCase.reserveInventory(request.productId, request.quantity)
        return ReservationResponse(
            reservationId = reservation.id,
            productId     = request.productId,
            quantity      = reservation.quantity,
            status        = reservation.status.name
        )
    }
}

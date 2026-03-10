package com.rcaagent.inventory.ports.`in`

import com.rcaagent.inventory.domain.Reservation

interface ReserveInventoryUseCase {
    suspend fun reserveInventory(productId: String, quantity: Int): Reservation
}

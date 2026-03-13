package com.rcaagent.payment.ports.out

data class InventoryResponse(val reserved: Boolean)

interface InventoryPort {
    suspend fun reserveInventory(productId: String, quantity: Int): InventoryResponse
}

package com.rcaagent.inventory.adapters.`in`

data class ReservationResponse(
    val reservationId: String,
    val productId: String,
    val quantity: Int,
    val status: String
)

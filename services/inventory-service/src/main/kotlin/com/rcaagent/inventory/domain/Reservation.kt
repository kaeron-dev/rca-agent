package com.rcaagent.inventory.domain

import java.util.UUID

data class Reservation(
    val id: String = UUID.randomUUID().toString(),
    val orderId: String,
    val quantity: Int,
    val status: ReservationStatus = ReservationStatus.PENDING
)

enum class ReservationStatus {
    PENDING, RESERVED, FAILED
}

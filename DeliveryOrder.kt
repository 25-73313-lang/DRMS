package com.example.deliveryroutesystem

enum class OrderStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED
}

data class DeliveryOrder(
    val id: String,
    val customerName: String,
    val barangay: String,
    val latitude: Double,
    val longitude: Double,
    var status: OrderStatus = OrderStatus.PENDING,
    var sequenceNumber: Int = 0
)

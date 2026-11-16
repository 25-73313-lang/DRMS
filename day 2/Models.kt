package com.example.deliveryroutesystem

import org.maplibre.android.geometry.LatLng

data class Hub(val name: String, val latLng: LatLng)

data class BarangayPoint(val name: String, val latLng: LatLng)

data class RouteResult(
    val points: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val legDurationsSeconds: List<Double>,
    val legDistancesMeters: List<Double>
)


class CostMatrix(
    val durations: Array<DoubleArray>,
    val distances: Array<DoubleArray>
)


data class RiderPlan(
    val riderId: Int,
    val name: String,
    val colorHex: String,
    val stops: List<DeliveryOrder>,
    val routePoints: List<LatLng>,
    val distanceKm: Double,
    val driveMinutes: Double,
    val serviceMinutes: Double,
    val departureMillis: Long,
    val isEstimated: Boolean,
    val legMeters: List<Double> = emptyList()
) {
    val totalMinutes: Double get() = driveMinutes + serviceMinutes
    val codTotal: Double get() = stops.sumOf { it.cashOnDelivery }
}
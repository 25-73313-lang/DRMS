package com.example.deliveryroutesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object DeliveryPlanner {

    val RIDER_COLORS = listOf("#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA")

    private const val FALLBACK_SPEED_KMH = 25.0
    private const val DETOUR_FACTOR = 1.3

    suspend fun plan(
        hub: Hub,
        riderCount: Int,
        serviceMinutesPerStop: Int,
        orders: List<DeliveryOrder>,
        departureMillis: Long = System.currentTimeMillis()
    ): List<RiderPlan> {
        val active = orders.filter { it.status != OrderStatus.DELIVERED }
        val groups = withContext(Dispatchers.Default) {
            RiderAssigner.assign(hub.latLng, active, riderCount)
        }
        val plans = mutableListOf<RiderPlan>()

        for ((index, group) in groups.withIndex()) {
            if (group.isNotEmpty() && plans.any { it.stops.isNotEmpty() }) {
                delay(300)
            }
            plans.add(
                planGroup(
                    riderId = index + 1,
                    start = hub.latLng,
                    startsAtHub = true,
                    hub = hub.latLng,
                    group = group,
                    serviceMinutesPerStop = serviceMinutesPerStop,
                    departureMillis = departureMillis
                )
            )
        }
        return plans
    }

    suspend fun replanRider(
        hub: Hub,
        old: RiderPlan,
        start: LatLng,
        remaining: List<DeliveryOrder>,
        serviceMinutesPerStop: Int
    ): RiderPlan = planGroup(
        riderId = old.riderId,
        start = start,
        startsAtHub = false,
        hub = hub.latLng,
        group = remaining,
        serviceMinutesPerStop = serviceMinutesPerStop,
        departureMillis = System.currentTimeMillis()
    )

    private suspend fun planGroup(
        riderId: Int,
        start: LatLng,
        startsAtHub: Boolean,
        hub: LatLng,
        group: List<DeliveryOrder>,
        serviceMinutesPerStop: Int,
        departureMillis: Long
    ): RiderPlan {
        val color = RIDER_COLORS[(riderId - 1) % RIDER_COLORS.size]

        if (group.isEmpty()) {
            return RiderPlan(
                riderId, "Rider $riderId", color, emptyList(), emptyList(),
                0.0, 0.0, 0.0, departureMillis, false
            )
        }

        val stopPoints = group.map { LatLng(it.latitude, it.longitude) }
        val points = if (startsAtHub) listOf(hub) + stopPoints
        else listOf(start) + stopPoints + listOf(hub)
        val endIndex = if (startsAtHub) 0 else points.size - 1

        val matrix = RouteFetcher.fetchMatrix(points)
        var estimated = matrix == null
        val cost = matrix ?: fallbackMatrix(points)

        val visitOrder = RouteSequencer.nearestFirst(cost.distances, 0, endIndex)
        val orderedOrders = visitOrder.map { group[it - 1] }
        val tour = listOf(0) + visitOrder + listOf(endIndex)
        val waypoints = tour.map { points[it] }

        val route = RouteFetcher.fetchShortestPath(waypoints) ?: RouteFetcher.fetchRoute(waypoints)
        val legSeconds: List<Double>
        val legMeters: List<Double>
        val line: List<LatLng>

        if (route != null && route.legDurationsSeconds.size == waypoints.size - 1) {
            legSeconds = route.legDurationsSeconds
            legMeters = route.legDistancesMeters
            line = route.points
        } else {
            estimated = true
            legSeconds = (0 until tour.size - 1).map { cost.durations[tour[it]][tour[it + 1]] }
            legMeters = (0 until tour.size - 1).map { cost.distances[tour[it]][tour[it + 1]] }
            line = waypoints
        }

        var clock = 0.0
        val plannedStops = orderedOrders.mapIndexed { i, order ->
            clock += legSeconds[i] / 60.0
            val arrival = clock
            clock += serviceMinutesPerStop
            order.copy(
                riderId = riderId,
                sequenceNumber = i + 1,
                etaMinutes = arrival,
                status = OrderStatus.IN_TRANSIT
            )
        }

        return RiderPlan(
            riderId = riderId,
            name = "Rider $riderId",
            colorHex = color,
            stops = plannedStops,
            routePoints = line,
            distanceKm = legMeters.sum() / 1000.0,
            driveMinutes = legSeconds.sum() / 60.0,
            serviceMinutes = serviceMinutesPerStop * plannedStops.size.toDouble(),
            departureMillis = departureMillis,
            isEstimated = estimated,
            legMeters = legMeters
        )
    }

    private fun fallbackMatrix(points: List<LatLng>): CostMatrix {
        val n = points.size
        val dist = Array(n) { DoubleArray(n) }
        val time = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j) continue
                val meters = RouteSequencer.calculateDistance(points[i], points[j]) * 1000.0 * DETOUR_FACTOR
                dist[i][j] = meters
                time[i][j] = meters / 1000.0 / FALLBACK_SPEED_KMH * 3600.0
            }
        }
        return CostMatrix(time, dist)
    }

    fun clockTime(departureMillis: Long, minutesAfterDeparture: Double): String =
        SimpleDateFormat("h:mm a", Locale.getDefault())
            .format(Date(departureMillis + (minutesAfterDeparture * 60_000).toLong()))

    fun formatDuration(minutes: Double): String {
        val m = minutes.roundToInt()
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }
}
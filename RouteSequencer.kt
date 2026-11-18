package com.example.deliveryroutesystem

import org.maplibre.android.geometry.LatLng
import kotlin.math.*

object RouteSequencer {

    private const val EXACT_LIMIT = 10

    fun calculateDistance(start: LatLng, end: LatLng): Double {
        val r = 6371.0
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(start.latitude)) * cos(Math.toRadians(end.latitude)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun nearestFirst(cost: Array<DoubleArray>, startIndex: Int = 0, endIndex: Int = 0): List<Int> {
        val remaining = (0 until cost.size).filter { it != startIndex && it != endIndex }.toMutableList()
        val order = mutableListOf<Int>()
        var current = startIndex
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { cost[current][it] }!!
            order.add(next)
            remaining.remove(next)
            current = next
        }
        return order
    }

    fun nearestFirstUrgentPriority(cost: Array<DoubleArray>, isUrgent: List<Boolean>, startIndex: Int = 0, endIndex: Int = 0): List<Int> {
        val allIndices = (0 until cost.size).filter { it != startIndex && it != endIndex }
        val urgentIndices = allIndices.filter { isUrgent.getOrElse(it) { false } }.toMutableList()
        val normalIndices = allIndices.filter { !isUrgent.getOrElse(it) { false } }.toMutableList()

        val result = mutableListOf<Int>()
        var current = startIndex

        while (urgentIndices.isNotEmpty()) {
            val next = urgentIndices.minByOrNull { cost[current][it] }!!
            result.add(next)
            urgentIndices.remove(next)
            current = next
        }

        while (normalIndices.isNotEmpty()) {
            val next = normalIndices.minByOrNull { cost[current][it] }!!
            result.add(next)
            normalIndices.remove(next)
            current = next
        }

        return result
    }

    fun sequenceRoute(startLocation: LatLng, orders: List<DeliveryOrder>): List<DeliveryOrder> {
        val pendingOrders = orders.filter { it.status != OrderStatus.DELIVERED }.toMutableList()
        val urgentOrders = pendingOrders.filter { it.isUrgent }.toMutableList()
        val normalOrders = pendingOrders.filter { !it.isUrgent }.toMutableList()

        val sequencedOrders = mutableListOf<DeliveryOrder>()
        var currentLocation = startLocation
        var stepCounter = 1

        while (urgentOrders.isNotEmpty()) {
            val nextOrder = urgentOrders.minByOrNull { order ->
                calculateDistance(currentLocation, LatLng(order.latitude, order.longitude))
            }
            nextOrder?.let { order ->
                val sequenced = order.copy(sequenceNumber = stepCounter++)
                sequencedOrders.add(sequenced)
                urgentOrders.remove(order)
                currentLocation = LatLng(order.latitude, order.longitude)
            }
        }

        while (normalOrders.isNotEmpty()) {
            val nextOrder = normalOrders.minByOrNull { order ->
                calculateDistance(currentLocation, LatLng(order.latitude, order.longitude))
            }
            nextOrder?.let { order ->
                val sequenced = order.copy(sequenceNumber = stepCounter++)
                sequencedOrders.add(sequenced)
                normalOrders.remove(order)
                currentLocation = LatLng(order.latitude, order.longitude)
            }
        }

        val deliveredOrders = orders.filter { it.status == OrderStatus.DELIVERED }
        return sequencedOrders + deliveredOrders
    }

    fun optimizeTour(cost: Array<DoubleArray>, startIndex: Int = 0, endIndex: Int = 0): List<Int> {
        val customers = (0 until cost.size).filter { it != startIndex && it != endIndex }
        if (customers.isEmpty()) return emptyList()
        return if (customers.size <= EXACT_LIMIT) exactOrder(cost, startIndex, endIndex, customers)
        else heuristicOrder(cost, startIndex, endIndex, customers)
    }

    private fun exactOrder(
        cost: Array<DoubleArray>,
        start: Int,
        end: Int,
        c: List<Int>
    ): List<Int> {
        val m = c.size
        val full = (1 shl m) - 1
        val inf = Double.MAX_VALUE / 4

        val dp = Array(1 shl m) { DoubleArray(m) { inf } }
        val parent = Array(1 shl m) { IntArray(m) { -1 } }

        for (j in 0 until m) dp[1 shl j][j] = cost[start][c[j]]

        for (mask in 1..full) {
            for (j in 0 until m) {
                if ((mask and (1 shl j)) == 0) continue
                val cur = dp[mask][j]
                if (cur >= inf) continue
                for (k in 0 until m) {
                    if ((mask and (1 shl k)) != 0) continue
                    val nextMask = mask or (1 shl k)
                    val value = cur + cost[c[j]][c[k]]
                    if (value < dp[nextMask][k]) {
                        dp[nextMask][k] = value
                        parent[nextMask][k] = j
                    }
                }
            }
        }

        var bestJ = 0
        var best = inf
        for (j in 0 until m) {
            val value = dp[full][j] + cost[c[j]][end]
            if (value < best) {
                best = value
                bestJ = j
            }
        }

        val order = ArrayList<Int>()
        var mask = full
        var j = bestJ
        while (j != -1) {
            order.add(c[j])
            val previous = parent[mask][j]
            mask = mask xor (1 shl j)
            j = previous
        }
        order.reverse()
        return order
    }

    private fun heuristicOrder(
        cost: Array<DoubleArray>,
        start: Int,
        end: Int,
        customers: List<Int>
    ): List<Int> {
        var bestTour: List<Int> = emptyList()
        var bestCost = Double.MAX_VALUE

        for (first in customers) {
            val tour = mutableListOf(start, first)
            val remaining = customers.filter { it != first }.toMutableList()
            var current = first
            while (remaining.isNotEmpty()) {
                val next = remaining.minByOrNull { cost[current][it] }!!
                tour.add(next)
                remaining.remove(next)
                current = next
            }
            tour.add(end)

            improve(cost, tour)
            val total = tourCost(cost, tour)
            if (total < bestCost) {
                bestCost = total
                bestTour = tour.toList()
            }
        }
        return bestTour.subList(1, bestTour.size - 1).toList()
    }

    private fun tourCost(cost: Array<DoubleArray>, t: List<Int>): Double {
        var sum = 0.0
        for (k in 0 until t.size - 1) sum += cost[t[k]][t[k + 1]]
        return sum
    }

    private fun improve(cost: Array<DoubleArray>, tour: MutableList<Int>) {
        var best = tourCost(cost, tour)
        var improved = true
        var rounds = 0
        while (improved && rounds < 50) {
            improved = false
            rounds++

            for (i in 1 until tour.size - 2) {
                for (j in i + 1 until tour.size - 1) {
                    val candidate = tour.toMutableList()
                    candidate.subList(i, j + 1).reverse()
                    val c = tourCost(cost, candidate)
                    if (c < best - 1e-6) {
                        tour.clear()
                        tour.addAll(candidate)
                        best = c
                        improved = true
                    }
                }
            }

            for (i in 1 until tour.size - 1) {
                for (j in 1 until tour.size - 1) {
                    if (i == j) continue
                    val candidate = tour.toMutableList()
                    val node = candidate.removeAt(i)
                    candidate.add(j, node)
                    val c = tourCost(cost, candidate)
                    if (c < best - 1e-6) {
                        tour.clear()
                        tour.addAll(candidate)
                        best = c
                        improved = true
                    }
                }
            }
        }
    }
}

package com.example.applemaps.map

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Renderer-neutral WGS84 coordinate shared by routing, map selection, and navigation. */
data class MapCoordinate(val latitude: Double, val longitude: Double) {
    fun distanceTo(other: MapCoordinate): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 12_742_000.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

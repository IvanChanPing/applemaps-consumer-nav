package com.example.applemaps.map

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficRouteTest {
    @Test
    fun segmentLevelsCompressAndClampToGeometry() {
        val intervals = compressTrafficSegments(
            listOf(TrafficLevel.NORMAL, TrafficLevel.NORMAL, TrafficLevel.SLOW, TrafficLevel.HEAVY, TrafficLevel.SEVERE),
            pointCount = 5,
        )
        assertEquals(
            listOf(
                TrafficInterval(0, 2, TrafficLevel.NORMAL),
                TrafficInterval(2, 3, TrafficLevel.SLOW),
                TrafficInterval(3, 4, TrafficLevel.HEAVY),
            ),
            intervals,
        )
    }

    @Test
    fun pointIndexesBecomeDistanceFractionsRatherThanIndexFractions() {
        val points = listOf(MapCoordinate(0.0, 0.0), MapCoordinate(0.0, 0.01), MapCoordinate(0.0, 0.04))
        val stops = trafficColorStops(
            points,
            listOf(TrafficInterval(0, 1, TrafficLevel.NORMAL), TrafficInterval(1, 2, TrafficLevel.HEAVY)),
        )
        assertEquals(2, stops.size)
        assertEquals(0.0, stops[0].progress, 1e-9)
        assertEquals(0.25, stops[1].progress, 0.002)
        assertEquals(TrafficLevel.HEAVY, stops[1].level)
    }
}

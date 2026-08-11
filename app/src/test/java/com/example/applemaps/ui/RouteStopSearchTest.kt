package com.example.applemaps.ui

import com.example.applemaps.map.MapCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteStopSearchTest {
    private val first = MapCoordinate(40.70, -73.99)
    private val second = MapCoordinate(40.71, -73.98)
    private val replacement = MapCoordinate(40.72, -73.97)

    @Test fun listEndIndexAppendsAddStopSearchResult() {
        assertEquals(listOf(first, second, replacement), updatedRouteStops(listOf(first, second), 2, replacement))
    }

    @Test fun existingIndexReplacesEditedStop() {
        assertEquals(listOf(first, replacement), updatedRouteStops(listOf(first, second), 1, replacement))
    }

    @Test fun staleIndexIsRejected() {
        assertNull(updatedRouteStops(listOf(first), 3, replacement))
    }

    @Test fun reopeningSentinelCannotBlockTheWholeMap() {
        assertEquals(0f, validMapSheetHeight(Float.MAX_VALUE, 1280f))
        assertEquals(420f, validMapSheetHeight(420f, 1280f))
    }
}

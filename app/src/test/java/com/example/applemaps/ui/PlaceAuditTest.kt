package com.example.applemaps.ui

import com.example.applemaps.map.Place
import com.example.applemaps.map.PlaceRepository
import com.example.applemaps.map.AirportDetails
import com.example.applemaps.map.MapCoordinate
import com.example.applemaps.map.PlaceAccessPoint
import com.example.applemaps.map.routeCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM checks for the audit's camera coordinate gate and source-truth hours row. */
class PlaceAuditTest {
    @Test
    fun mapCoordinateGateRejectsNonFiniteAndOutOfMercatorBounds() {
        assertTrue(PlaceRepository.isValidMapCoordinate(40.6895, -73.9857))
        assertFalse(PlaceRepository.isValidMapCoordinate(Double.NaN, -73.9857))
        assertFalse(PlaceRepository.isValidMapCoordinate(89.0, -73.9857))
        assertFalse(PlaceRepository.isValidMapCoordinate(40.6895, 181.0))
    }

    @Test
    fun hoursStatusIncludesTheBackendTransition() {
        val place = Place("Clinic", "Dermatologist", "Brooklyn", "", 40.0, -73.0,
            open = true, nextTransition = "Closes 7:00 PM")
        assertEquals("Open · Closes 7:00 PM", placeHoursStatus(place))
        assertEquals("Closed · Opens tomorrow 9:00 AM", placeHoursStatus(place.copy(open = false, nextTransition = "Opens tomorrow 9:00 AM")))
    }

    @Test
    fun airportDirectionsUseNearestModeCompatibleAccessPoint() {
        val airport = Place("Airport", "Airport", "", "", 40.0, -73.0,
            airportDetails = AirportDetails(accessPoints = listOf(
                PlaceAccessPoint(40.001, -73.001, walking = true, driving = false),
                PlaceAccessPoint(40.010, -73.010, walking = false, driving = true),
                PlaceAccessPoint(40.020, -73.020, walking = true, driving = true),
            )))
        val origin = MapCoordinate(40.0, -73.0)
        assertEquals(MapCoordinate(40.001, -73.001), airport.routeCoordinate("Walk", origin))
        assertEquals(MapCoordinate(40.010, -73.010), airport.routeCoordinate("Drive", origin))
        assertEquals(MapCoordinate(40.001, -73.001), airport.routeCoordinate("Cycle", origin))
    }
}

package com.example.applemaps.ui

import com.example.applemaps.map.Place
import com.example.applemaps.map.PlaceRepository
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
}

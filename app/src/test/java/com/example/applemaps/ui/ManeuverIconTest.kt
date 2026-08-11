package com.example.applemaps.ui

import com.example.applemaps.R
import com.example.applemaps.map.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverIconTest {
    @Test
    fun structuredModifierControlsRouteStepIcon() {
        assertEquals(
            R.drawable.ic_arrow_triangle_turn_up_right,
            routeStepIcon(RouteStep("Turn right onto Left Street", "Left Street", 80, "turn", "right")),
        )
    }

    @Test
    fun uturnDoesNotUseDownArrow() {
        assertEquals(
            R.drawable.ic_arrow_left_and_right,
            maneuverIconFor("turn", "uturn", "Make a U-turn"),
        )
    }

    @Test
    fun instructionFallbackUsesActionPrefixOnly() {
        assertEquals(
            R.drawable.ic_arrow_up,
            maneuverIconFor("", "", "Continue onto Right Avenue"),
        )
    }
}

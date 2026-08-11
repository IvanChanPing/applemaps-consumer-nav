package com.example.applemaps.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavFollowTimingTest {
    @Test
    fun oneSixtiethSecondPreservesOriginalFraction() {
        assertEquals(0.16, navFollowFraction(1.0 / 60.0), 1e-12)
    }

    @Test
    fun two120HzFramesMatchOne60HzFrame() {
        val halfFrame = navFollowFraction(1.0 / 120.0)
        val remainder = (1.0 - halfFrame) * (1.0 - halfFrame)
        assertEquals(0.84, remainder, 1e-12)
    }

    @Test
    fun longFrameGapIsBounded() {
        assertEquals(navFollowFraction(0.1), navFollowFraction(1.0), 0.0)
    }
}

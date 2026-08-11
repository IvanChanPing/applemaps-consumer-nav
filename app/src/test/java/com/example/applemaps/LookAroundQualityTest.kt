package com.example.applemaps

import com.example.applemaps.ui.flatLookAroundHdUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM contracts for flat Look Around's SD-first, distinct-HD request gate. */
class LookAroundQualityTest {
    @Test
    fun flatViewerRequestsHdOnlyForADistinctNonBlankUrl() {
        assertEquals(null, flatLookAroundHdUrl("https://example/sd.jpg", ""))
        assertEquals(null, flatLookAroundHdUrl("https://example/sd.jpg", "https://example/sd.jpg"))
        assertEquals("https://example/hd.jpg", flatLookAroundHdUrl("https://example/sd.jpg", "https://example/hd.jpg"))
    }
}

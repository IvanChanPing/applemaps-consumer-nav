package com.example.applemaps.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoGalleryGestureTest {
    @Test
    fun panBoundsAreZeroAtOneXAndHalfTheExpandedPixelsAtFourX() {
        val oneX = galleryImagePanBounds(1f, 300f, 300f)
        val fourX = galleryImagePanBounds(4f, 300f, 300f)
        assertEquals(0f, oneX.x, 0f); assertEquals(0f, oneX.y, 0f)
        assertEquals(450f, fourX.x, 0f); assertEquals(450f, fourX.y, 0f)
    }

    @Test
    fun panClampKeepsImageWithinItsZoomedBounds() {
        val clamped = clampGalleryImagePan(Offset(999f, -999f), 2f, 300f, 300f)
        val atOneX = clampGalleryImagePan(Offset(8f, -8f), 1f, 300f, 300f)
        assertEquals(150f, clamped.x, 0f); assertEquals(-150f, clamped.y, 0f)
        assertEquals(0f, atOneX.x, 0f); assertEquals(0f, atOneX.y, 0f)
    }
}

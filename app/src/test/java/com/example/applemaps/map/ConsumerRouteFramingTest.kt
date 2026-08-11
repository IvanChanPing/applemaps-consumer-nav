package com.example.applemaps.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsumerRouteFramingTest {
    @Test fun physicalSheetPaddingIsConvertedToWebCssPixels() {
        assertEquals(360, consumerCssPixels(720, 2f))
        assertEquals(240, consumerCssPixels(720, 3f))
    }

    @Test fun invalidDensityPreservesNonNegativePhysicalPadding() {
        assertEquals(720, consumerCssPixels(720, 0f))
        assertEquals(0, consumerCssPixels(-20, Float.NaN))
    }
}

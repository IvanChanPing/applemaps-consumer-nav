package com.example.applemaps.map

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the production map-data boundary: providers are contacted by the APK, not box relay routes. */
class BoxlessMapDataTest {
    private val mainSource = File("src/main").walkTopDown()
        .filter { it.isFile && (it.extension == "kt" || it.extension == "json") }
        .joinToString("\n") { it.readText() }
    private val buildScript = File("build.gradle.kts").readText()

    @Test fun productionSourceContainsNoMapDataRelayRoutes() {
        assertFalse(mainSource.contains("/appleplace"))
        assertFalse(mainSource.contains("/applelookaround"))
    }

    @Test fun directProvidersRemainWired() {
        assertTrue(mainSource.contains("nominatim.openstreetmap.org"))
        assertTrue(mainSource.contains("panoramax.openstreetmap.fr"))
        assertTrue(mainSource.contains("maps.apple.com/data/search-autocomplete"))
        assertTrue(mainSource.contains("maps.apple.com/place?place-id="))
        assertTrue(mainSource.contains("gspe76-ssl.ls.apple.com/api/tile"))
        assertTrue(mainSource.contains("gspe72-ssl.ls.apple.com/mnn_us"))
        assertTrue(mainSource.contains("COMPONENT_TYPE_BUSINESS_HOURS"))
        assertTrue(mainSource.contains("COMPONENT_TYPE_REVIEW"))
        assertTrue(mainSource.contains("mzstatic") && mainSource.contains("otstatic"))
        assertTrue(buildScript.contains("com.google.android.libraries.places:places:5.3.0"))
    }

    @Test fun selectedPlaceStartsOneSharedLookAroundPreparation() {
        assertTrue(mainSource.contains("placeLookPreparation = scope.async"))
        assertTrue(mainSource.contains("prepareLookAround(context, found)"))
        assertTrue(mainSource.contains("preparation = lookViewerPreparation"))
        assertTrue(mainSource.contains("event=preloadUsed"))
    }

    @Test fun mapUsesPlatformAndroidViewInputInsteadOfManualSiblingRelay() {
        assertTrue(mainSource.contains("AndroidView("))
        assertTrue(mainSource.contains("FrameLayout(context).also(controller::attachTo)"))
        assertFalse(mainSource.contains("pointerInteropFilter(onTouchEvent = controller::dispatchTouchEvent)"))
        assertFalse(mainSource.contains("setInputBottomInsetPx"))
    }
}

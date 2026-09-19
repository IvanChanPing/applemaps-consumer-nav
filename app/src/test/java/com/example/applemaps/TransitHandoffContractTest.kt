package com.example.applemaps

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Purpose: source-level regression contract for the Apple planner → Vela Transit ownership boundary.
 * Invocation: standard host JVM test discovery.
 * Contract: Transit retains schedules/legs in Vela, preserves departure time, and never auto-starts an itinerary.
 * Verification: reads the production owners and fails when their explicit handoff or settle gate disappears.
 */
class TransitHandoffContractTest {
    @Test fun plannerOffersTransitWithoutFlatteningItIntoRoadRoutes() {
        val sheet = projectFile("app/src/main/java/com/example/applemaps/ui/DirectionsSheet.kt").readText()
        val screen = projectFile("app/src/main/java/com/example/applemaps/ui/AppleMapsScreen.kt").readText()
        val recompute = screen.substringAfter("fun recomputeDirections()").substringBefore("fun handleConsumerSelection")
        val planner = screen.substringAfter("if (dr != null) DirectionsBody").substringBefore("onSelect = selectRoute")

        assertTrue(sheet.contains("Mode(\"Transit\", R.drawable.ic_tram_fill, \"TRANSIT\")"))
        assertTrue(sheet.contains("transitReady -> TransitHandoffCard(onGo)"))
        assertTrue(recompute.contains("if (dirMode == \"Transit\")"))
        assertTrue(recompute.contains("directionsRoutes = emptyList()"))
        assertTrue(recompute.contains("transitPreviewReady = true"))
        assertTrue(planner.contains("if (it == \"Transit\") { stops.value = emptyList(); stopLabels.value = emptyList() }"))
    }

    @Test fun transitHandoffPreservesTimeAndWaitsForItinerarySelection() {
        val activity = projectFile("app/src/main/java/com/example/applemaps/VelaNavigationActivity.kt").readText()
        val setup = activity.substringAfter("lifecycleScope.launch {").substringBefore("lifecycleScope.launch {", "")

        assertTrue(activity.contains("EXTRA_DEPART_OFFSET_MIN"))
        assertTrue(activity.contains("vm.setDirectionsTime(1"))
        assertTrue(setup.contains("if (mode != TravelMode.TRANSIT)"))
        assertFalse(setup.substringBefore("if (mode != TravelMode.TRANSIT)").contains("vm.startNav()"))
        assertTrue(activity.contains("GUIDANCE_FINISH_CONFIRM_MS"))
        assertTrue(activity.contains("val settled = vm.state.value"))
        assertTrue(activity.contains("generation == completionGeneration && confirmedTerminal"))
    }

    private fun projectFile(path: String): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory).canonicalFile) { it.parentFile }
            .map { File(it, path) }
            .first(File::isFile)
    }
}

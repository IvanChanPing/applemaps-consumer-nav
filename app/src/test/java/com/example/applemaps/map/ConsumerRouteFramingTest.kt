package com.example.applemaps.map

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerRouteCameraContractTest {
    @Test fun routeAdapterDoesNotTakeCameraOwnership() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val source = generateSequence(File(workingDirectory).canonicalFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/example/applemaps/map/ConsumerMapController.kt") }
            .first(File::isFile)
            .readText()
        val setRoutes = source.substringAfter("function setRoutes(payload)").substringBefore("function setRouteProgress")
        assertFalse(setRoutes.contains("showItems"))
        assertFalse(setRoutes.contains("setCamera"))
    }

    @Test fun nativeHomeBrowseDoesNotNavigateTheRenderer() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val source = generateSequence(File(workingDirectory).canonicalFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/example/applemaps/map/ConsumerMapController.kt") }
            .first(File::isFile)
            .readText()
        val category = source.substringAfter("fun showHomeCategory").substringBefore("fun showGuide")
        val guide = source.substringAfter("fun showGuide").substringBefore("fun setBrowsePlaces")
        assertFalse(category.contains("loadUrl"))
        assertFalse(guide.contains("loadUrl"))
        assertFalse(source.contains("fun resetBrowsePage"))
    }

    @Test fun nativeBrowseResultsInstallBoundedMapKitMarkers() {
        val source = controllerSource()
        val markerMethod = source.substringAfter("fun setBrowsePlaces").substringBefore("fun clearBrowsePlaces")
        val adapterMethod = source.substringAfter("function setBrowsePlaces(items)").substringBefore("function setUserLocation")
        assertTrue(markerMethod.contains("places.take(20)"))
        assertTrue(adapterMethod.contains("new mapkit.MarkerAnnotation"))
        assertTrue(adapterMethod.contains("state.map.addAnnotation(a)"))
    }

    @Test fun directionsPreviewLoadsAppleConsumerRoutePage() {
        val controller = controllerSource()
        val screen = projectFile("app/src/main/java/com/example/applemaps/ui/AppleMapsScreen.kt").readText()
        val recompute = screen.substringAfter("fun recomputeDirections()").substringBefore("fun handleConsumerSelection")
        val selection = screen.substringAfter("fun handleConsumerSelection").substringBefore("fun recenterOnPin")
        assertTrue(controller.substringAfter("fun showConsumerDirections").substringBefore("fun showConsumerPlace")
            .contains("appendPath(\"directions\")"))
        assertTrue(recompute.contains("showConsumerDirections"))
        assertFalse(recompute.contains("RouteLayer.drawRoutes"))
        assertTrue(selection.contains("navMode || directionsRoutes != null"))
    }

    private fun controllerSource(): String =
        projectFile("app/src/main/java/com/example/applemaps/map/ConsumerMapController.kt").readText()

    private fun projectFile(path: String): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory).canonicalFile) { it.parentFile }
            .map { File(it, path) }
            .first(File::isFile)
    }
}

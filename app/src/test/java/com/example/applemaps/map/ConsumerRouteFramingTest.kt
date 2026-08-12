package com.example.applemaps.map

import java.io.File
import org.junit.Assert.assertFalse
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
        val guide = source.substringAfter("fun showGuide").substringBefore("fun center()")
        assertFalse(category.contains("loadUrl"))
        assertFalse(guide.contains("loadUrl"))
        assertFalse(source.contains("fun resetBrowsePage"))
    }
}

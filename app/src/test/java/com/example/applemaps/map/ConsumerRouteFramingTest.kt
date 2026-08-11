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
}

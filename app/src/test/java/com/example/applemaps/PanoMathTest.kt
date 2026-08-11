package com.example.applemaps

import com.example.applemaps.ui.PanoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** JVM unit test for [PanoMath], the pure-Kotlin mirror of PanoRenderer.kt's fragment shader.
 *  Assertions and tolerance are pinned by docs/STREET_VIEW_ROADMAP.md Step 2 "Verify" section. */
class PanoMathTest {
    private fun rad(deg: Double) = Math.toRadians(deg).toFloat()
    private val tol = 1e-3f

    private data class FaceCalibration(
        val fovS: Float,
        val fovH: Float,
        val cy: Float,
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
    )

    /** A real six-face calibration returned for the Empire State Building panorama. */
    private val appleFaces = listOf(
        FaceCalibration(2.159844949f, 1.832595715f, 0.305432619f, 0f, 0f, 0f),
        FaceCalibration(1.178097245f, 1.832595715f, 0.305432619f, rad(90.0), 0f, 0f),
        FaceCalibration(2.159844949f, 1.832595715f, 0.305432619f, rad(-180.0), 0f, 0f),
        FaceCalibration(1.178097245f, 1.832595715f, 0.305432619f, rad(-90.0), 0f, 0f),
        FaceCalibration(1.047197551f, 1.047197551f, 0f, 0f, rad(90.0), rad(180.0)),
        FaceCalibration(2.129301687f, 2.268928028f, 0f, rad(180.0), rad(-90.0), 0f),
    )

    private fun faceAt(yawDegrees: Double, pitchDegrees: Double): Int {
        val yaw = rad(yawDegrees)
        val pitch = rad(pitchDegrees)
        val cosPitch = cos(pitch)
        val worldX = cosPitch * sin(yaw)
        val worldY = sin(pitch)
        val worldZ = -cosPitch * cos(yaw)
        return appleFaces.indexOfFirst { face ->
            PanoMath.rawFaceUv(
                appleFaces.indexOf(face), worldX, worldY, worldZ,
                face.fovS, face.fovH, face.cy, face.yaw, face.pitch, face.roll,
            ) != null
        }
    }

    @Test
    fun centerLooksAtImageCenter() {
        val (u, v) = PanoMath.ndcToUv(0f, 0f, 2f, rad(90.0), 0f, 0f)
        assertEquals(0.5f, u, tol)
        assertEquals(0.5f, v, tol)
    }

    @Test
    fun yawRight90DegreesShiftsUByQuarter() {
        val (u, _) = PanoMath.ndcToUv(0f, 0f, 2f, rad(90.0), (Math.PI / 2).toFloat(), 0f)
        assertEquals(0.75f, u, tol)
    }

    @Test
    fun pitchUp90DegreesLooksAtZenith() {
        val (_, v) = PanoMath.ndcToUv(0f, 0f, 2f, rad(90.0), 0f, (Math.PI / 2).toFloat())
        assertEquals(0.0f, v, tol)
    }

    @Test
    fun rightEdgeAtHalfFovxOffsetsUByFovxOver360() {
        val (u, _) = PanoMath.ndcToUv(1f, 0f, 1f, rad(90.0), 0f, 0f)
        assertEquals(0.625f, u, tol)
    }

    @Test
    fun progressiveHdRequiresDistinctUrlAnd4096TextureSupport() {
        assertEquals(null, PanoMath.progressiveHdWidth(2048, true))
        assertEquals(null, PanoMath.progressiveHdWidth(4096, false))
        assertEquals(4096, PanoMath.progressiveHdWidth(4096, true))
        assertEquals(4096, PanoMath.progressiveHdWidth(8192, true))
    }

    @Test
    fun rawFaceTexturesStayWithinTheBoundedSixTextureBudget() {
        assertEquals(1024, PanoMath.PRELOAD_RAW_FACE_SIZE)
        assertEquals(0, PanoMath.rawFaceTargetSize(1024))
        assertEquals(1024, PanoMath.rawFaceTargetSize(2048))
        assertEquals(1536, PanoMath.rawFaceTargetSize(4096))
        assertEquals(1536, PanoMath.rawFaceTargetSize(8192))
        assertTrue(PanoMath.PRELOAD_RAW_FACE_SIZE <= PanoMath.rawFaceTargetSize(2048))
    }

    @Test
    fun rearSeamUsesTheCenterOfFaceTwo() {
        val face = appleFaces[2]
        val uv = PanoMath.rawFaceUv(
            2, 0f, 0f, 1f,
            face.fovS, face.fovH, face.cy, face.yaw, face.pitch, face.roll,
        )
        assertNotNull(uv)
        assertEquals(0.5f, uv!!.first, tol)
        assertEquals(2, faceAt(180.0, 0.0))
    }

    @Test
    fun polarFacesOwnStraightUpAndStraightDown() {
        assertEquals(4, faceAt(0.0, 90.0))
        assertEquals(5, faceAt(0.0, -90.0))
    }

    @Test
    fun realAppleCalibrationCoversAFullSphereTwoDegreeGrid() {
        for (pitch in -90..90 step 2) {
            for (yaw in -180..180 step 2) {
                assertTrue("No Apple face at yaw=$yaw pitch=$pitch", faceAt(yaw.toDouble(), pitch.toDouble()) >= 0)
            }
        }
    }
}

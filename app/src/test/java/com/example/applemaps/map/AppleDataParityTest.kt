package com.example.applemaps.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import streetlevel.GroundMetadataTileOuterClass

/** Pure JVM contracts for the former box's Apple photo, coverage-protobuf, and face-signing behavior. */
class AppleDataParityTest {
    @Test fun photoParserNormalizesEscapedAppleUrlsAndUpsizesThem() {
        val blob = """{\"url\":\"https:\\/\\/is1-ssl.mzstatic.com\\/image\\/thumb\\/abc\\/240x240bb.jpg\\u0026x=1\"}"""
        assertEquals(
            listOf("https://is1-ssl.mzstatic.com/image/thumb/abc/1200x1200bb.jpg&x=1"),
            ApplePlaceClient.parsePhotos(blob),
        )
    }

    @Test fun coverageParserPreservesUnsignedIdsCoordinatesAndSixCalibrations() {
        val lens = GroundMetadataTileOuterClass.CameraMetadata.LensProjection.newBuilder()
            .setFovS(2.1).setFovH(1.8).setCy(0.3).build()
        val cameras = (0..5).map { index ->
            GroundMetadataTileOuterClass.CameraMetadata.newBuilder()
                .setCameraNumber(index).setLensProjection(lens)
                .setPosition(GroundMetadataTileOuterClass.CameraMetadata.OrientedPosition.newBuilder().setYaw(index.toDouble()))
                .build()
        }
        val tile = GroundMetadataTileOuterClass.GroundMetadataTile.newBuilder()
            .setTileCoordinate(GroundMetadataTileOuterClass.TileCoordinate.newBuilder().setX(38598).setY(49263).setZ(17))
            .addBuildTable(GroundMetadataTileOuterClass.GroundMetadataTile.GroundDataBuild.newBuilder().setBuildId(2_147_486_047L))
            .addAllCameraMetadata(cameras)
            .addPano(
                GroundMetadataTileOuterClass.GroundMetadataTile.PhotoPosition.newBuilder()
                    .setPanoid(java.lang.Long.parseUnsignedLong("15122779243262790715"))
                    .setBuildTableIdx(0).addAllCameraMetadataIdx((0..5).toList())
                    .setTilePosition(
                        GroundMetadataTileOuterClass.GroundMetadataTile.PhotoPosition.OrientedTilePosition.newBuilder()
                            .setX(0).setY(0),
                    ),
            ).build()
        val parsed = AppleLookAroundClient.parseCoverageTile(tile.toByteArray()).single()
        assertEquals("15122779243262790715", parsed.panoId)
        assertEquals("2147486047", parsed.buildId)
        assertEquals(6, parsed.cameras.size)
        assertTrue(parsed.lat.isFinite() && parsed.lon.isFinite())
    }

    @Test fun faceSignerMatchesTheReferenceAlgorithmByteForByte() {
        val raw = "https://gspe72-ssl.ls.apple.com/mnn_us/1512/2779/2432/6279/0715/2147486047/t/0/7"
        val sid = "1".repeat(40)
        val expected = raw + "?sid=" + sid +
            "&accessKey=1700000000_AAAAAAAAAAAAAAAA_TO6Q60bYc%2BW1TWEI9pO1tP4iSAHfZw39dnUcaLfT7ED%2F3k0mS8D1m%2BkiwSwzGHk2mfyuskwjovfT1%2FS7emHUQT6ODUJZyzfVzERuZkyIPmqkkB1sksUa6ggt4Yiwk%2BlCN81ysMsjblbQo7xF9rbPqCrIpBI6BeHrspt3vEZGnM8%3D"
        assertEquals(expected, AppleLookAroundClient.authenticateUrl(raw, sid, "A".repeat(16), 1_700_000_000L))
    }
}

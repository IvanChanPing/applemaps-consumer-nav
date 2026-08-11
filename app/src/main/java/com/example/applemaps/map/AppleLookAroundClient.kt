package com.example.applemaps.map

import com.example.applemaps.diag.DiagLog
import streetlevel.GroundMetadataTileOuterClass
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.LinkedHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Device-local translation of the former `applelookaround.py` + `streetlevel.lookaround` metadata path.
 * It queries Apple's z17 coverage tiles, selects the nearest panorama within 150 metres, signs direct
 * six-face HEIC URLs, and feeds the existing calibrated GL renderer without a box data relay.
 * [PlaceRepository.lookAround] invokes it whenever a place is selected and falls back to Panoramax only
 * when Apple has no usable result. The protobuf and signer contracts are JVM-tested; the direct endpoint
 * and HEIC response were live-probed, while the complete physical-phone Look Around UI remains unverified.
 */
internal object AppleLookAroundClient {
    private const val COVERAGE_ENDPOINT = "https://gspe76-ssl.ls.apple.com/api/tile?"
    private const val FACE_ENDPOINT = "https://gspe72-ssl.ls.apple.com/mnn_us/"
    private const val COVERAGE_AUTH_TOKEN = "w31CPGRO/n7BsFPh8X7kZnFG0LDj9pAuR8nTtH3xhH8="
    private const val TOKEN_P1 = "4cjLaD4jGRwlQ9U"
    private const val TOKEN_P2 = "72xIzEBe0vHBmf9"
    private const val ZOOM = 17
    private const val MAX_DISTANCE_METERS = 150.0
    private const val MAX_COVERAGE_BYTES = 8 * 1024 * 1024
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val CACHE_ENTRIES = 256
    private const val ALPHANUMERIC = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private val random = SecureRandom()
    private val sessionId = randomString(40, "0123456789")
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<CacheKey, CacheEntry>(CACHE_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, CacheEntry>?): Boolean =
            size > CACHE_ENTRIES
    }

    private data class CacheKey(val latE5: Long, val lonE5: Long)
    private data class CacheEntry(val storedAtMs: Long, val value: LookAround?)

    internal data class Camera(
        val fovS: Float,
        val fovH: Float,
        val cx: Float,
        val cy: Float,
        val k2: Float,
        val k3: Float,
        val k4: Float,
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
    )

    internal data class Candidate(
        val panoId: String,
        val buildId: String,
        val lat: Double,
        val lon: Double,
        val cameras: List<Camera>,
    )

    fun lookup(lat: Double, lon: Double): LookAround? {
        val key = CacheKey(round(lat * 100_000).toLong(), round(lon * 100_000).toLong())
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            cache[key]?.takeIf { now - it.storedAtMs <= CACHE_TTL_MS }?.let {
                DiagLog.log("APPLELOOK", "event=cacheHit", "found=${if (it.value == null) 0 else 1}")
                return it.value
            }
            cache.remove(key)
        }

        val (centerX, centerY) = wgs84ToTile(lat, lon)
        var successfulTiles = 0
        var failedTiles = 0
        val candidates = LinkedHashMap<String, Candidate>()
        for (dy in -1..1) for (dx in -1..1) {
            try {
                val body = fetchCoverageTile(centerX + dx, centerY + dy)
                successfulTiles++
                for (candidate in parseCoverageTile(body)) {
                    candidates.putIfAbsent("${candidate.panoId}/${candidate.buildId}", candidate)
                }
            } catch (_: Exception) {
                failedTiles++
            }
        }
        if (successfulTiles == 0) {
            DiagLog.log("APPLELOOK", "event=coverageFailed", "tilesFailed=$failedTiles")
            throw IOException("All Apple Look Around coverage tile requests failed")
        }

        val nearest = candidates.values
            .map { candidate -> haversineMeters(lat, lon, candidate.lat, candidate.lon) to candidate }
            .minByOrNull { it.first }
            ?.takeIf { it.first <= MAX_DISTANCE_METERS }
        val result = nearest?.let { (distance, candidate) -> normalize(candidate, distance) }
        synchronized(cacheLock) { cache[key] = CacheEntry(now, result) }
        DiagLog.log(
            "APPLELOOK", "event=coverageComplete", "tilesOk=$successfulTiles", "tilesFailed=$failedTiles",
            "candidates=${candidates.size}", "found=${if (result == null) 0 else 1}",
            "distance=${nearest?.first?.toInt() ?: -1}", "faces=${result?.rawFaces?.size ?: 0}",
        )
        return result
    }

    private fun normalize(candidate: Candidate, distance: Double): LookAround {
        val rawFaces = if (candidate.cameras.size == 6) candidate.cameras.mapIndexed { index, camera ->
            LookAroundRawFace(
                index = index,
                url = panoramaFaceUrl(candidate.panoId, candidate.buildId, index, 0),
                fovS = camera.fovS,
                fovH = camera.fovH,
                cx = camera.cx,
                cy = camera.cy,
                k2 = camera.k2,
                k3 = camera.k3,
                k4 = camera.k4,
                yaw = camera.yaw,
                pitch = camera.pitch,
                roll = camera.roll,
            )
        } else emptyList()
        val preview = panoramaFaceUrl(candidate.panoId, candidate.buildId, 2, 7)
        return LookAround(
            provider = LookAroundProvider.APPLE,
            id = candidate.panoId,
            collectionId = candidate.buildId,
            instanceHost = "https://gspe72-ssl.ls.apple.com",
            thumbUrl = preview,
            sdUrl = preview,
            hdUrl = panoramaFaceUrl(candidate.panoId, candidate.buildId, 2, 6),
            isPano = true,
            azimuth = null,
            producer = "Apple",
            lat = candidate.lat,
            lon = candidate.lon,
            distanceMeters = distance.toFloat(),
            panoWidth = null,
            panoHeight = null,
            tileCols = null,
            tileRows = null,
            tileSize = null,
            prev = null,
            next = null,
            related = emptyList(),
            rawFaces = rawFaces,
        )
    }

    private fun fetchCoverageTile(tileX: Int, tileY: Int): ByteArray {
        val connection = URL(COVERAGE_ENDPOINT).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("maps-tile-style", "style=57&size=2&scale=0&v=0&preflight=2")
            connection.setRequestProperty("maps-tile-x", tileX.toString())
            connection.setRequestProperty("maps-tile-y", tileY.toString())
            connection.setRequestProperty("maps-tile-z", ZOOM.toString())
            connection.setRequestProperty("maps-auth-token", COVERAGE_AUTH_TOKEN)
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.errorStream?.close()
                throw IOException("Apple coverage HTTP $code")
            }
            connection.inputStream.use { readBounded(it, MAX_COVERAGE_BYTES) }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseCoverageTile(body: ByteArray): List<Candidate> {
        val tile = GroundMetadataTileOuterClass.GroundMetadataTile.parseFrom(body)
        val cameras = tile.cameraMetadataList.map { camera ->
            val lens = camera.lensProjection
            val position = camera.position
            Camera(
                lens.fovS.toFloat(), lens.fovH.toFloat(), lens.cx.toFloat(), lens.cy.toFloat(),
                lens.k2.toFloat(), lens.k3.toFloat(), lens.k4.toFloat(),
                position.yaw.toFloat(), position.pitch.toFloat(), position.roll.toFloat(),
            )
        }
        val tileCoordinate = tile.tileCoordinate
        return tile.panoList.mapNotNull { pano ->
            if (pano.buildTableIdx !in tile.buildTableList.indices) return@mapNotNull null
            val selectedCameras = pano.cameraMetadataIdxList.mapNotNull { index -> cameras.getOrNull(index) }
            val (panoLat, panoLon) = tileOffsetToWgs84(
                pano.tilePosition.x, pano.tilePosition.y, tileCoordinate.x, tileCoordinate.y,
            )
            Candidate(
                panoId = java.lang.Long.toUnsignedString(pano.panoid),
                buildId = java.lang.Long.toUnsignedString(tile.buildTableList[pano.buildTableIdx].buildId),
                lat = panoLat,
                lon = panoLon,
                cameras = selectedCameras,
            )
        }
    }

    internal fun panoramaFaceUrl(panoId: String, buildId: String, face: Int, zoom: Int): String {
        require(panoId.length <= 20 && panoId.all(Char::isDigit))
        require(buildId.length <= 10 && buildId.all(Char::isDigit))
        require(face in 0..5)
        val padded = panoId.padStart(20, '0').chunked(4).joinToString("/")
        val raw = "$FACE_ENDPOINT$padded/${buildId.padStart(10, '0')}/t/$face/${zoom.coerceIn(0, 7)}"
        return authenticateUrl(
            raw,
            sessionId,
            randomString(16, ALPHANUMERIC),
            System.currentTimeMillis() / 1000L + 4_200L,
        )
    }

    internal fun authenticateUrl(url: String, sid: String, tokenP3: String, timestampSeconds: Long): String {
        val uri = URI(url)
        val separator = if (uri.rawQuery.isNullOrEmpty()) "?" else "&"
        val pathAndQuery = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
        val plaintext = "$pathAndQuery${separator}sid=$sid$timestampSeconds$tokenP3"
        val key = MessageDigest.getInstance("SHA-256")
            .digest((TOKEN_P1 + TOKEN_P2 + tokenP3).toByteArray(StandardCharsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val encoded = URLEncoder.encode(Base64.getEncoder().encodeToString(encrypted), StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "$url${separator}sid=$sid&accessKey=${timestampSeconds}_${tokenP3}_$encoded"
    }

    private fun wgs84ToTile(lat: Double, lon: Double): Pair<Int, Int> {
        val scale = 1 shl ZOOM
        val latRad = Math.toRadians(lat)
        val x = (lon + 180.0) / 360.0 * scale
        val y = (1.0 - asinh(kotlin.math.tan(latRad)) / PI) / 2.0 * scale
        return floor(x).toInt() to floor(y).toInt()
    }

    private fun tileOffsetToWgs84(xOffset: Int, yOffset: Int, tileX: Int, tileY: Int): Pair<Double, Double> {
        val panoX = tileX + (xOffset / 64.0) / 255.0
        val panoY = tileY + (255.0 - yOffset / 64.0) / 255.0
        val scale = (1 shl ZOOM).toDouble()
        val panoLon = panoX / scale * 360.0 - 180.0
        val panoLat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * panoY / scale))))
        return panoLat to panoLon
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 6_371_008.8 * 2 * kotlin.math.asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun randomString(length: Int, alphabet: String): String =
        buildString(length) { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 32 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("Response exceeded $limit bytes")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

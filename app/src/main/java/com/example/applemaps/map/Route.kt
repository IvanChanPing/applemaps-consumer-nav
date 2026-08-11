package com.example.applemaps.map

import com.example.applemaps.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal const val MAPBOX_TOKEN = "[removed]"

enum class TrafficLevel {
    NORMAL, SLOW, HEAVY, SEVERE;

    companion object {
        internal fun fromProvider(value: String): TrafficLevel = when (value.uppercase()) {
            "MODERATE", "SLOW" -> SLOW
            "HEAVY", "TRAFFIC_JAM" -> HEAVY
            "SEVERE" -> SEVERE
            else -> NORMAL
        }
    }
}

/** Traffic classification for polyline segments from startPointIndex (inclusive) to endPointIndex (exclusive). */
data class TrafficInterval(val startPointIndex: Int, val endPointIndex: Int, val level: TrafficLevel)

/** One computed route: decoded geometry + summary + the Nav-SDK route token (for guaranteed-same guidance). */
data class Route(
    val points: List<MapCoordinate>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val routeToken: String? = null,
    val steps: List<RouteStep> = emptyList(),   // per-maneuver directions for the route (i) info page
    val climbFeet: Int? = null,                 // total ascent (walk/bike) — null = not fetched/unavailable
    val descentFeet: Int? = null,               // total descent (walk/bike)
    val elevation: List<Double> = emptyList(),  // sampled elevation (metres) along the route → the profile chart
    val typicalDurationSeconds: Int? = null,     // non-live comparison for traffic-aware driving routes
    val trafficIntervals: List<TrafficInterval> = emptyList(),
)

/** One turn-by-turn maneuver in a route, for the route-details (i) page: an instruction + the road + length. */
data class RouteStep(
    val instruction: String,   // e.g. "Turn left onto Market St"
    val road: String,          // the road name for this step (may be blank)
    val distanceMeters: Int,
    val maneuverType: String = "",
    val maneuverModifier: String = "",
)

/**
 * Route planning = ON-DEVICE Google Routes API (`computeRoutes`), per docs/ROUTE_PREVIEW_PLAN.md. The BOX is
 * only for scraped Apple place data; routing is a plain on-device Google call (a `routeToken` later feeds the
 * Nav SDK so turn-by-turn follows the previewed route exactly). The API key is a user precondition (enable the
 * Routes API + billing), supplied as the gradle property `ROUTES_API_KEY` -> `BuildConfig.ROUTES_API_KEY`.
 * With no key, [computeRoutes] returns null and the caller falls back to [demoRoute] so the reveal is visible.
 */
object RouteRepository {
    // Mapbox token (reused from tracker-app, in-house). Used for Directions API voice/banner/lane
    // instructions that keyless FOSSGIS OSRM cannot provide. TODO move to BuildConfig/runtime before any public push.
    suspend fun computeRoutes(origin: MapCoordinate, dest: MapCoordinate, travelMode: String = "DRIVE", vias: List<MapCoordinate> = emptyList()): List<Route>? =
        withContext(Dispatchers.IO) {
            val key = BuildConfig.ROUTES_API_KEY
            if (key.isBlank()) return@withContext null
            runCatching {
                val body = JSONObject().apply {
                    put("origin", waypoint(origin)); put("destination", waypoint(dest))
                    if (vias.isNotEmpty()) put("intermediates", org.json.JSONArray().apply { vias.forEach { put(waypoint(it)) } })
                    put("travelMode", travelMode); put("computeAlternativeRoutes", true)
                    if (travelMode == "DRIVE" || travelMode == "TWO_WHEELER") put("routingPreference", "TRAFFIC_AWARE")
                }.toString()
                val conn = (URL("https://routes.googleapis.com/directions/v2:computeRoutes").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 12000; readTimeout = 12000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Goog-Api-Key", key)
                    setRequestProperty("X-Goog-FieldMask",
                        "routes.polyline.encodedPolyline,routes.distanceMeters,routes.duration,routes.staticDuration," +
                            "routes.routeToken,routes.travelAdvisory.speedReadingIntervals")
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode !in 200..299) return@runCatching null
                val txt = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(txt).optJSONArray("routes") ?: return@runCatching null
                (0 until arr.length()).mapNotNull { i ->
                    val r = arr.getJSONObject(i)
                    val enc = r.optJSONObject("polyline")?.optString("encodedPolyline")?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val points = decodePolyline(enc)
                    val dur = r.optString("duration", "0s").removeSuffix("s").toIntOrNull() ?: 0
                    val typical = r.optString("staticDuration", "").removeSuffix("s").toIntOrNull()
                    Route(points, r.optInt("distanceMeters"), dur,
                        routeToken = r.optString("routeToken").ifBlank { null },
                        typicalDurationSeconds = typical,
                        trafficIntervals = googleTrafficIntervals(r, points.size))
                }.ifEmpty { null }
            }.getOrNull()
        }

    /**
     * Live/historic traffic routing for Drive previews. `overview=full` makes the concatenated per-leg
     * congestion array align one-to-one with consecutive route geometry segments, including added stops.
     * Unknown/missing traffic remains NORMAL (blue); any HTTP/shape failure returns null for the existing
     * Google → OSRM → demo fallback chain.
     */
    suspend fun mapboxTrafficRoutes(origin: MapCoordinate, dest: MapCoordinate, vias: List<MapCoordinate> = emptyList()): List<Route>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val coords = (listOf(origin) + vias + dest).joinToString(";") { "${it.longitude},${it.latitude}" }
                val url = URL("https://api.mapbox.com/directions/v5/mapbox/driving-traffic/$coords" +
                    "?geometries=geojson&overview=full&annotations=congestion&alternatives=true&steps=true" +
                    "&access_token=$MAPBOX_TOKEN")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000; readTimeout = 12000; setRequestProperty("User-Agent", "AppleMapsClone/0.1")
                }
                if (conn.responseCode !in 200..299) return@runCatching null
                val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (root.optString("code") != "Ok") return@runCatching null
                val routes = root.optJSONArray("routes") ?: return@runCatching null
                (0 until routes.length()).mapNotNull { i ->
                    val route = routes.getJSONObject(i)
                    val coordsJson = route.optJSONObject("geometry")?.optJSONArray("coordinates")
                        ?: return@mapNotNull null
                    val points = (0 until coordsJson.length()).mapNotNull { pi ->
                        val pair = coordsJson.optJSONArray(pi) ?: return@mapNotNull null
                        if (pair.length() < 2) null else MapCoordinate(pair.optDouble(1), pair.optDouble(0))
                    }
                    if (points.size < 2) return@mapNotNull null
                    val levels = ArrayList<TrafficLevel>()
                    val legs = route.optJSONArray("legs")
                    if (legs != null) for (li in 0 until legs.length()) {
                        val congestion = legs.getJSONObject(li).optJSONObject("annotation")?.optJSONArray("congestion") ?: continue
                        for (ci in 0 until congestion.length()) levels.add(TrafficLevel.fromProvider(congestion.optString(ci)))
                    }
                    val typical = if (route.has("duration_typical") && !route.isNull("duration_typical"))
                        route.optDouble("duration_typical").toInt() else null
                    Route(
                        points = points,
                        distanceMeters = route.optDouble("distance").toInt(),
                        durationSeconds = route.optDouble("duration").toInt(),
                        steps = osrmSteps(route),
                        typicalDurationSeconds = typical,
                        trafficIntervals = compressTrafficSegments(levels, points.size),
                    )
                }.ifEmpty { null }
            }.getOrNull()
        }

    private fun googleTrafficIntervals(route: JSONObject, pointCount: Int): List<TrafficInterval> {
        val readings = route.optJSONObject("travelAdvisory")?.optJSONArray("speedReadingIntervals") ?: return emptyList()
        val out = ArrayList<TrafficInterval>(); var priorEnd = 0
        for (i in 0 until readings.length()) {
            val reading = readings.getJSONObject(i)
            val start = if (reading.has("startPolylinePointIndex")) reading.optInt("startPolylinePointIndex") else priorEnd
            val end = reading.optInt("endPolylinePointIndex", start)
            val safeStart = start.coerceIn(0, (pointCount - 1).coerceAtLeast(0))
            val safeEnd = end.coerceIn(safeStart, (pointCount - 1).coerceAtLeast(0))
            if (safeEnd > safeStart) out.add(TrafficInterval(safeStart, safeEnd, TrafficLevel.fromProvider(reading.optString("speed"))))
            priorEnd = end
        }
        return out
    }

    /** Google encoded-polyline decoder (precision 5). */
    /** Free, KEYLESS road routing via the public OSRM server — returns a real road-following polyline
     *  (fixes the straight-line demo route when no Google ROUTES_API_KEY is set). */
    suspend fun osrmRoute(origin: MapCoordinate, dest: MapCoordinate, profile: String = "car", vias: List<MapCoordinate> = emptyList()): List<Route>? = withContext(Dispatchers.IO) {
        try {
            // FOSSGIS keyless OSRM instances (what the OSM website uses): routed-car / routed-bike / routed-foot
            val coords = (listOf(origin) + vias + dest).joinToString(";") { "${it.longitude},${it.latitude}" }   // origin;stop(s);dest
            val url = URL("https://routing.openstreetmap.de/routed-$profile/route/v1/driving/$coords?overview=full&geometries=polyline&alternatives=true&steps=true")
            val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 12000; readTimeout = 12000; setRequestProperty("User-Agent", "AppleMapsClone/0.1") }
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (j.optString("code") != "Ok") return@withContext null
            val rs = j.optJSONArray("routes") ?: return@withContext null
            val out = (0 until rs.length()).mapNotNull { i ->   // [0]=best, [1..]=alternatives
                val r = rs.getJSONObject(i); val pts = decodePolyline(r.optString("geometry"))
                if (pts.size < 2) null else Route(pts, r.optDouble("distance").toInt(), r.optDouble("duration").toInt(), null, osrmSteps(r))
            }
            // Walk/bike routes get an elevation profile (climb + samples) so the cards can show the graph.
            val withElev = if (profile == "foot" || profile == "bike") elevateRoutes(out) else out
            withElev.ifEmpty { null }
        } catch (e: Exception) { null }
    }

    /** Raw OSRM response (with steps + polyline6) for feeding Ferrostar's createRouteFromOsrm (turn maneuvers). */
    suspend fun osrmRawJson(origin: MapCoordinate, dest: MapCoordinate, profile: String = "car", vias: List<MapCoordinate> = emptyList()): String? = withContext(Dispatchers.IO) {
        val coords = (listOf(origin) + vias + dest).joinToString(";") { "${it.longitude},${it.latitude}" }
        val mbProfile = when (profile) { "bike" -> "cycling"; "foot" -> "walking"; else -> "driving" }
        // PRIMARY: Mapbox Directions — OSRM-format WITH voiceInstructions + bannerInstructions (Ferrostar speaks
        // these + gets lane guidance). Keyless FOSSGIS OSRM omits them, which is why there was no voice.
        try {
            val url = URL("https://api.mapbox.com/directions/v5/mapbox/$mbProfile/$coords?overview=full&geometries=polyline6&steps=true&alternatives=true&voice_instructions=true&banner_instructions=true&voice_units=imperial&access_token=$MAPBOX_TOKEN")
            val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 12000; readTimeout = 12000; setRequestProperty("User-Agent", "AppleMapsClone/0.1") }
            if (conn.responseCode in 200..299) return@withContext conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) { /* fall through to keyless */ }
        // FALLBACK: keyless FOSSGIS OSRM (no spoken instructions).
        try {
            val url = URL("https://routing.openstreetmap.de/routed-$profile/route/v1/driving/$coords?overview=full&geometries=polyline6&steps=true")
            val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 12000; readTimeout = 12000; setRequestProperty("User-Agent", "AppleMapsClone/0.1") }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) { null }
    }

    /**
     * Populate walk/bike routes with a climb figure + elevation samples, in ONE keyless call to
     * **opentopodata** (aster30m — fast ~0.5s + accurate; open-elevation was flaky: 502s, multi-second
     * latency, and returned bogus 0.0). Samples up to ~32 points per route (≤100 total = one batched GET,
     * inside the public rate limit). Routes with missing/void elevation are returned unchanged (not faked).
     */
    private suspend fun elevateRoutes(routes: List<Route>): List<Route> = withContext(Dispatchers.IO) {
        try {
            val usable = routes.filter { it.points.size >= 2 }
            if (usable.isEmpty()) return@withContext routes
            val perRoute = (100 / usable.size).coerceIn(8, 32)
            val counts = ArrayList<Int>(); val allPts = ArrayList<MapCoordinate>()
            usable.forEach { r ->
                val pts = if (r.points.size <= perRoute) r.points else (0 until perRoute).map { r.points[it * (r.points.size - 1) / (perRoute - 1)] }
                counts.add(pts.size); allPts.addAll(pts)
            }
            val locs = allPts.joinToString("|") { "${it.latitude},${it.longitude}" }
            val conn = (URL("https://api.opentopodata.org/v1/aster30m?locations=$locs").openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000; readTimeout = 12000; setRequestProperty("User-Agent", "AppleMapsClone/0.1")
            }
            if (conn.responseCode !in 200..299) return@withContext routes
            val arr = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return@withContext routes
            val allElev = (0 until arr.length()).map { arr.getJSONObject(it).optDouble("elevation", Double.NaN) }
            var idx = 0
            val repl = HashMap<Route, Route>()
            usable.forEachIndexed { i, r ->
                val c = counts[i]; if (idx + c > allElev.size) return@forEachIndexed
                val elev = allElev.subList(idx, idx + c).toList(); idx += c
                if (elev.size >= 2 && elev.none { it.isNaN() }) {
                    var climb = 0.0; var descent = 0.0
                    for (k in 1 until elev.size) { val d = elev[k] - elev[k - 1]; if (d > 0) climb += d else descent -= d }
                    repl[r] = r.copy(climbFeet = (climb * 3.28084).toInt(), descentFeet = (descent * 3.28084).toInt(), elevation = elev)
                }
            }
            routes.map { repl[it] ?: it }
        } catch (e: Exception) { routes }
    }

    fun decodePolyline(enc: String, factor: Double = 1e5): List<MapCoordinate> {
        val poly = ArrayList<MapCoordinate>(); var i = 0; var lat = 0; var lng = 0
        while (i < enc.length) {
            var shift = 0; var result = 0; var b: Int
            do { b = enc[i++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20 && i < enc.length)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0; result = 0
            do { b = enc[i++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20 && i < enc.length)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            poly.add(MapCoordinate(lat / factor, lng / factor))
        }
        return poly
    }

    /** Build the per-step directions list from one OSRM route object's legs[].steps[]. */
    private fun osrmSteps(route: JSONObject): List<RouteStep> {
        val out = ArrayList<RouteStep>()
        val legs = route.optJSONArray("legs") ?: return out
        for (li in 0 until legs.length()) {
            val steps = legs.getJSONObject(li).optJSONArray("steps") ?: continue
            for (si in 0 until steps.length()) {
                val s = steps.getJSONObject(si)
                val road = s.optString("name")
                val m = s.optJSONObject("maneuver")
                val type = m?.optString("type") ?: ""
                val mod = m?.optString("modifier") ?: ""
                if (type == "depart" && si == 0 && li == 0) out.add(RouteStep(if (road.isNotBlank()) "Head out on $road" else "Head out", road, s.optDouble("distance").toInt(), type, mod))
                else if (type == "arrive") { /* final arrival appended once below */ }
                else out.add(RouteStep(osrmInstruction(type, mod, road), road, s.optDouble("distance").toInt(), type, mod))
            }
        }
        out.add(RouteStep("Arrive at your destination", "", 0, "arrive", ""))
        return out
    }

    /** Human instruction from an OSRM maneuver type+modifier (+ road). Covers the common driving maneuvers. */
    private fun osrmInstruction(type: String, mod: String, road: String): String {
        val onto = if (road.isNotBlank()) " onto $road" else ""
        val dir = when (mod) {
            "left" -> "left"; "right" -> "right"; "slight left" -> "slight left"; "slight right" -> "slight right"
            "sharp left" -> "sharp left"; "sharp right" -> "sharp right"; "straight" -> "straight"; "uturn" -> "around"
            else -> mod
        }
        return when (type) {
            "turn" -> "Turn $dir$onto"
            "new name" -> if (road.isNotBlank()) "Continue onto $road" else "Continue straight"
            "merge" -> "Merge $dir$onto".trimEnd()
            "on ramp" -> "Take the ramp$onto"
            "off ramp" -> "Take the exit$onto"
            "fork" -> "Keep $dir$onto"
            "end of road" -> "Turn $dir$onto"
            "roundabout", "rotary" -> "Enter the roundabout$onto"
            "continue" -> if (road.isNotBlank()) "Continue$onto" else "Continue $dir".trimEnd()
            else -> (if (road.isNotBlank()) "Continue onto $road" else "Continue").trim()
        }
    }

    /**
     * KEYLESS avoid-routing via FOSSGIS Valhalla (the OSRM `exclude` flag is unsupported on that server).
     * Valhalla `auto` costing honors `use_tolls`/`use_highways` (0 = avoid), so this genuinely re-routes to
     * skip tolls/highways rather than faking a toggle. Returns ONE route (Valhalla gives no alternatives here)
     * with decoded polyline6 geometry + maneuvers for the (i) page. Falls back to null on any failure.
     */
    suspend fun valhallaRoute(origin: MapCoordinate, dest: MapCoordinate, profile: String, avoidTolls: Boolean, avoidHighways: Boolean, vias: List<MapCoordinate> = emptyList()): List<Route>? = withContext(Dispatchers.IO) {
        try {
            val costing = when (profile) { "foot" -> "pedestrian"; "bike" -> "bicycle"; else -> "auto" }
            val locs = (listOf(origin) + vias + dest).joinToString(",") { "{\"lat\":${it.latitude},\"lon\":${it.longitude}}" }
            val opts = if (costing == "auto") ",\"costing_options\":{\"auto\":{\"use_tolls\":${if (avoidTolls) 0 else 1},\"use_highways\":${if (avoidHighways) 0 else 1}}}" else ""
            val body = "{\"locations\":[$locs],\"costing\":\"$costing\"$opts,\"directions_options\":{\"units\":\"miles\"}}"
            val conn = (URL("https://valhalla1.openstreetmap.de/route").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 12000; readTimeout = 12000
                setRequestProperty("Content-Type", "application/json"); setRequestProperty("User-Agent", "AppleMapsClone/0.1")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) return@withContext null
            val trip = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONObject("trip") ?: return@withContext null
            if (trip.optJSONObject("summary") == null) return@withContext null
            val legs = trip.optJSONArray("legs") ?: return@withContext null
            val pts = ArrayList<MapCoordinate>(); val steps = ArrayList<RouteStep>()
            for (li in 0 until legs.length()) {
                val leg = legs.getJSONObject(li)
                pts.addAll(decodePolyline(leg.optString("shape"), 1e6))   // Valhalla shape = polyline6
                val mans = leg.optJSONArray("maneuvers") ?: continue
                for (mi in 0 until mans.length()) {
                    val mn = mans.getJSONObject(mi)
                    val instr = mn.optString("instruction")
                    if (instr.isNotBlank()) steps.add(RouteStep(instr, mn.optJSONArray("street_names")?.optString(0) ?: "", (mn.optDouble("length") * 1609.34).toInt()))
                }
            }
            if (pts.size < 2) return@withContext null
            val sum = trip.getJSONObject("summary")
            listOf(Route(pts, (sum.optDouble("length") * 1609.34).toInt(), sum.optDouble("time").toInt(), null, steps))
        } catch (e: Exception) { null }
    }

    /** No-key fallback: a gently bowed multi-point line from origin to dest so the reveal animation is visible
     *  end-to-end. Replaced by real street geometry the moment ROUTES_API_KEY is set. */
    fun demoRoute(origin: MapCoordinate, dest: MapCoordinate): List<Route> {
        val n = 40
        val pts = ArrayList<MapCoordinate>(n + 1)
        val perpLat = dest.longitude - origin.longitude          // perpendicular offset for a slight arc
        val perpLng = -(dest.latitude - origin.latitude)
        for (k in 0..n) {
            val t = k / n.toFloat()
            val bow = Math.sin(t * Math.PI) * 0.12
            pts.add(MapCoordinate(
                origin.latitude + (dest.latitude - origin.latitude) * t + perpLat * bow,
                origin.longitude + (dest.longitude - origin.longitude) * t + perpLng * bow))
        }
        val meters = origin.distanceTo(dest).toInt()
        return listOf(Route(pts, meters, meters / 12, null))
    }

    private fun waypoint(p: MapCoordinate) = JSONObject().put("location",
        JSONObject().put("latLng", JSONObject().put("latitude", p.latitude).put("longitude", p.longitude)))
}

/** Compress one traffic value per geometry segment into contiguous point-index intervals. */
internal fun compressTrafficSegments(levels: List<TrafficLevel>, pointCount: Int): List<TrafficInterval> {
    val segmentCount = levels.size.coerceAtMost((pointCount - 1).coerceAtLeast(0))
    if (segmentCount == 0) return emptyList()
    val out = ArrayList<TrafficInterval>(); var start = 0; var level = levels[0]
    for (i in 1 until segmentCount) {
        if (levels[i] != level) {
            out.add(TrafficInterval(start, i, level)); start = i; level = levels[i]
        }
    }
    out.add(TrafficInterval(start, segmentCount, level))
    return out
}

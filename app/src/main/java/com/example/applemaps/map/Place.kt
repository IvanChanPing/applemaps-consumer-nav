package com.example.applemaps.map

import android.content.Context
import android.text.Html
import android.text.style.URLSpan
import com.example.applemaps.BuildConfig
import com.example.applemaps.diag.DiagLog
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place as GooglePlace
import com.google.android.libraries.places.api.net.FetchResolvedPhotoUriRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

data class Review(
    val author: String,
    val text: String,
    val rating: Int? = null,
    val authorUri: String? = null,
    val relativePublishTime: String? = null,
)

data class PlaceAttribution(val text: String, val uri: String? = null)

data class PlacePhoto(
    val url: String,
    val caption: String? = null,
    val author: String? = null,
    val provider: String? = null,
    val actionUri: String? = null,
)

data class PlacePhotoAlbum(val title: String, val photos: List<PlacePhoto>)
data class PlaceAmenity(val name: String, val symbolName: String? = null)

data class RelatedPlace(
    val id: String,
    val name: String,
    val rating: Double? = null,
    val ratingMaximum: Double? = null,
    val ratingCount: Int? = null,
    val ratingSource: String? = null,
)

data class PlaceBounds(
    val southLat: Double,
    val westLon: Double,
    val northLat: Double,
    val eastLon: Double,
)

data class VenueTerminal(val name: String, val levels: List<String>)
data class AirportBrowseCategory(val label: String, val query: String, val subcategories: List<String> = emptyList())
data class PlaceAccessPoint(val lat: Double, val lon: Double, val walking: Boolean, val driving: Boolean)
data class AirportDetails(
    val code: String? = null,
    val terminals: List<VenueTerminal> = emptyList(),
    val airlines: List<String> = emptyList(),
    val browseCategories: List<AirportBrowseCategory> = emptyList(),
    val bounds: PlaceBounds? = null,
    val accessPoints: List<PlaceAccessPoint> = emptyList(),
    val elevationMeters: Double? = null,
)

/** A place shown in the sheet. Fields mirror the measured Apple card (title/category·locality/details). */
data class Place(
    val name: String,
    val category: String,     // "Train Station", "Restaurant", … (measured subtitle prefix)
    val locality: String,     // "Chelsea, Manhattan" (measured accent link)
    val address: String,
    val lat: Double,
    val lon: Double,
    // rich card fields — populated directly by the on-device Apple place client or configured Places SDK client
    val phone: String? = null,
    val website: String? = null,
    val hours: String? = null,
    val open: Boolean? = null,
    val nextTransition: String? = null, // source-truth "Closes 7:00 PM" / "Opens tomorrow 9:00 AM"
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val ratingCountFormatted: String? = null,
    val ratingSource: String? = null,
    val priceLevel: Int? = null,   // 1–4 → COST ribbon ($ .. $$$$); null = unknown (Google price_level 0 → null)
    val description: String? = null,
    val aboutAttribution: PlaceAttribution? = null,
    val amenities: List<String> = emptyList(),
    val amenityDetails: List<PlaceAmenity> = emptyList(),
    val photoLabels: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),   // resolved provider image URIs shown by Coil
    val photoAttributionUrls: List<String?> = emptyList(),
    val photoAlbums: List<PlacePhotoAlbum> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val alsoHere: List<String> = emptyList(),
    val relatedPlaces: List<RelatedPlace> = emptyList(),
    val airportDetails: AirportDetails? = null,
    val dataAttributions: List<PlaceAttribution> = emptyList(),
) {
    val coords: String
        get() = "%.5f° %s, %.5f° %s".format(abs(lat), if (lat >= 0) "N" else "S", abs(lon), if (lon >= 0) "E" else "W")
}

/** Returns the nearest mode-compatible venue entrance, falling back to the place center. */
internal fun Place.routeCoordinate(mode: String, origin: MapCoordinate): MapCoordinate {
    val points = airportDetails?.accessPoints.orEmpty().filter {
        if (mode.equals("Drive", ignoreCase = true)) it.driving else it.walking
    }
    val nearest = points.minByOrNull { point ->
        val latScale = 111_320.0
        val lonScale = latScale * kotlin.math.cos(Math.toRadians((origin.latitude + point.lat) / 2.0))
        val y = (point.lat - origin.latitude) * latScale
        val x = (point.lon - origin.longitude) * lonScale
        x * x + y * y
    }
    return nearest?.let { MapCoordinate(it.lat, it.lon) } ?: MapCoordinate(lat, lon)
}

/** One picture reference inside a sequence link (id + where it is). */
data class LookAroundLink(val itemId: String, val collectionId: String, val lat: Double, val lon: Double)

enum class LookAroundProvider { APPLE, PANORAMAX }

/** One original Apple HEIC camera face plus the calibration needed to project it on-device. */
data class LookAroundRawFace(
    val index: Int,
    val url: String,
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

/** One street-level image normalized for the shared equirectangular/flat Look Around viewers. */
data class LookAround(
    val provider: LookAroundProvider,
    val id: String,
    val collectionId: String,      // STAC "collection" — needed for the item GET
    val instanceHost: String,      // scheme+host of assets.hd.href, e.g. "https://panoramax.openstreetmap.fr"
    val thumbUrl: String,
    val sdUrl: String,             // 2048-wide derivate — first paint
    val hdUrl: String,             // full-res original — sharp swap-in
    val isPano: Boolean,           // pers:interior_orientation.field_of_view == 360
    val azimuth: Double?,          // view:azimuth, deg clockwise from true north; null if absent
    val producer: String?,         // geovisio:producer — attribution line
    val lat: Double, val lon: Double,
    val distanceMeters: Float,
    val panoWidth: Int?, val panoHeight: Int?,                   // sensor_array_dimensions or null
    val tileCols: Int?, val tileRows: Int?, val tileSize: Int?,  // tiles:tile_matrix_sets or nulls
    val prev: LookAroundLink?, val next: LookAroundLink?,
    val related: List<LookAroundLink>,
    val rawFaces: List<LookAroundRawFace> = emptyList(), // Apple-only source bytes; renderer chooses when supported
)

/**
 * Place data behind one interface. Search/reverse geocoding use direct keyless Nominatim; selected places are
 * enriched by an explicitly configured Places SDK client or the keyless on-device Apple place-page client. When a
 * rendered address number is tapped, [reverseGeocode] deliberately returns an Address place titled from the
 * structured road field rather than substituting a nearby building or POI name.
 */
object PlaceRepository {
    @Volatile private var placesClient: PlacesClient? = null

    /**
     * Initializes the boxless Google Places SDK (New) client used for rich place cards. Called once from
     * [com.example.applemaps.MainActivity]. An absent key deliberately leaves the client null so the same search
     * and POI UI continues with direct Nominatim data; no backend or embedded web-service key is substituted.
     * Runtime Google data requires Places API (New), billing, and an Android-restricted `PLACES_API_KEY`.
     */
    fun initialize(context: Context) {
        val key = BuildConfig.PLACES_API_KEY.trim()
        if (key.isEmpty() || placesClient != null) return
        synchronized(this) {
            if (placesClient != null) return
            runCatching {
                if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext, key)
                placesClient = Places.createClient(context.applicationContext)
            }
        }
    }

    /** Web-Mercator-safe coordinate gate shared by search and camera-selection entry points. */
    internal fun isValidMapCoordinate(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -85.05112878..85.05112878 && lon in -180.0..180.0

    suspend fun reverseGeocode(lat: Double, lon: Double, addressNumber: String? = null): Place = withContext(Dispatchers.IO) {
        val tappedNumber = addressNumber?.trim()?.takeIf(String::isNotEmpty)
        try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=18&addressdetails=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "AppleMapsClone/0.1 (dev)")
                connectTimeout = 8000; readTimeout = 8000
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val addr = json.optJSONObject("address")
            val road = listOf("road", "pedestrian", "residential", "footway", "path")
                .firstNotNullOfOrNull { addr?.optString(it)?.takeIf(String::isNotEmpty) }
            val name = if (tappedNumber != null) {
                listOfNotNull(tappedNumber, road).joinToString(" ")
            } else {
                listOf("amenity", "shop", "tourism", "building", "railway", "road")
                    .firstNotNullOfOrNull { addr?.optString(it)?.takeIf(String::isNotEmpty) }
                    ?: json.optString("name").takeIf(String::isNotEmpty) ?: "Dropped Pin"
            }
            val type = json.optString("type").ifEmpty { json.optString("class") }
            val category = if (tappedNumber != null) "Address" else type.replace('_', ' ').split(' ')
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.ifEmpty { "Location" }
            val locality = listOf("neighbourhood", "suburb", "city_district", "quarter", "city", "town", "village")
                .firstNotNullOfOrNull { addr?.optString(it)?.takeIf(String::isNotEmpty) }
                ?.let { hood -> addr?.optString("city")?.takeIf(String::isNotEmpty)?.let { "$hood, $it" } ?: hood }
                ?: (addr?.optString("state") ?: "")
            Place(name, category, locality, json.optString("display_name", ""), lat, lon)
        } catch (e: Exception) {
            Place(tappedNumber ?: "Dropped Pin", if (tappedNumber != null) "Address" else "Location", "", "", lat, lon)
        }
    }

    /** Forward search (type a place → candidate results) via keyless OSM Nominatim, biased near the map center. */
    suspend fun searchPlaces(query: String, nearLat: Double? = null, nearLon: Double? = null): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            val bias = if (nearLat != null && nearLon != null)
                "&viewbox=${nearLon - 0.6},${nearLat + 0.6},${nearLon + 0.6},${nearLat - 0.6}&bounded=0" else ""
            val url = URL("https://nominatim.openstreetmap.org/search?q=$q&format=jsonv2&limit=8&addressdetails=1$bias")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "AppleMapsClone/0.1 (dev)"); connectTimeout = 8000; readTimeout = 8000 }
            val arr = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val lat = o.getString("lat").toDouble(); val lon = o.getString("lon").toDouble()
                if (!isValidMapCoordinate(lat, lon)) return@mapNotNull null
                val disp = o.optString("display_name")
                val name = o.optString("name").ifBlank { disp.substringBefore(",") }
                val cat = o.optString("type").replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Place" }
                val ad = o.optJSONObject("address")
                val locality = ad?.let { it.optString("suburb").ifBlank { it.optString("neighbourhood").ifBlank { it.optString("city").ifBlank { it.optString("town") } } } } ?: ""
                Place(name, cat, locality, disp, lat, lon)
            }
        } catch (e: Exception) { emptyList() }
    }

    private val richPlaceFields = listOf(
        GooglePlace.Field.ID,
        GooglePlace.Field.DISPLAY_NAME,
        GooglePlace.Field.FORMATTED_ADDRESS,
        GooglePlace.Field.SHORT_FORMATTED_ADDRESS,
        GooglePlace.Field.ADDRESS_COMPONENTS,
        GooglePlace.Field.LOCATION,
        GooglePlace.Field.PRIMARY_TYPE,
        GooglePlace.Field.PRIMARY_TYPE_DISPLAY_NAME,
        GooglePlace.Field.NATIONAL_PHONE_NUMBER,
        GooglePlace.Field.INTERNATIONAL_PHONE_NUMBER,
        GooglePlace.Field.WEBSITE_URI,
        GooglePlace.Field.RATING,
        GooglePlace.Field.USER_RATING_COUNT,
        GooglePlace.Field.CURRENT_OPENING_HOURS,
        GooglePlace.Field.OPENING_HOURS,
        GooglePlace.Field.EDITORIAL_SUMMARY,
        GooglePlace.Field.REVIEWS,
        GooglePlace.Field.PHOTO_METADATAS,
        GooglePlace.Field.PRICE_LEVEL,
    )

    private data class ResolvedPhoto(val uri: String, val attribution: String?, val attributionUri: String?)

    private fun parseAttribution(raw: String): PlaceAttribution? {
        if (raw.isBlank()) return null
        val styled = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
        val text = styled.toString().trim().ifBlank { return null }
        val uri = styled.getSpans(0, styled.length, URLSpan::class.java).firstOrNull()?.url
        return PlaceAttribution(text, uri)
    }

    private suspend fun resolvePhotos(client: PlacesClient, metadata: List<PhotoMetadata>): List<ResolvedPhoto> =
        withTimeoutOrNull(12_000) {
            coroutineScope {
                metadata.take(10).map { photo ->
                    async {
                        try {
                            val response = client.fetchResolvedPhotoUri(
                                FetchResolvedPhotoUriRequest.builder(photo).setMaxWidth(1000).setMaxHeight(1000).build(),
                            ).await()
                            val resolvedUri = response.uri?.toString() ?: return@async null
                            val authors = photo.authorAttributions?.asList().orEmpty()
                            val authorNames = authors.mapNotNull { it.name.trim().takeIf(String::isNotEmpty) }
                            val fallback = parseAttribution(photo.attributions.orEmpty())
                            ResolvedPhoto(
                                resolvedUri,
                                authorNames.takeIf { it.isNotEmpty() }?.joinToString(", ") { "Photo: $it" }
                                    ?: fallback?.text,
                                authors.firstNotNullOfOrNull { it.uri?.trim()?.takeIf(String::isNotEmpty) }
                                    ?: fallback?.uri,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } ?: emptyList()

    /**
     * Fetches the closest rich place match directly through Places SDK for Android (New), never through the box.
     * Search, review, and resolved-photo tasks are bounded and run from [Dispatchers.IO]. Provider attribution is
     * carried into [Place] for the existing card/gallery UI. Returns null when unconfigured, timed out, rejected,
     * or unmatched so callers retain the selected Apple/Nominatim place rather than showing fabricated data.
     */
    suspend fun fetchGooglePlace(q: String, lat: Double, lon: Double): Place? = withContext(Dispatchers.IO) {
        val client = placesClient ?: return@withContext null
        try {
            val request = SearchByTextRequest.builder(q, richPlaceFields)
                .setMaxResultCount(1)
                .setLocationBias(CircularBounds.newInstance(com.google.android.gms.maps.model.LatLng(lat, lon), 5_000.0))
                .build()
            val d = withTimeoutOrNull(12_000) { client.searchByText(request).await() }
                ?.places?.firstOrNull() ?: return@withContext null
            val photos = resolvePhotos(client, d.photoMetadatas.orEmpty())
            val hours = d.currentOpeningHours ?: d.openingHours
            val today = hours?.weekdayText?.takeIf { it.size >= 7 }?.let {
                val gi = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                it[gi].substringAfter(": ", it[gi])
            }
            val cat = d.primaryTypeDisplayName?.takeIf(String::isNotBlank)
                ?: d.primaryType?.replace('_', ' ')?.split(' ')?.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                ?: "Place"
            val locality = d.addressComponents?.asList()?.firstOrNull { component ->
                component.types.any { it == "sublocality_level_1" || it == "locality" || it == "postal_town" }
            }?.name.orEmpty()
            val reviews = d.reviews.orEmpty().mapNotNull { review ->
                val text = review.text?.trim().orEmpty()
                if (text.isEmpty()) null else Review(
                    author = review.authorAttribution.name.trim().ifBlank { "Google Maps user" },
                    text = text,
                    rating = review.rating.toInt().takeIf { it in 1..5 },
                    authorUri = review.authorAttribution.uri?.trim()?.takeIf(String::isNotEmpty),
                    relativePublishTime = review.relativePublishTimeDescription?.trim()?.takeIf(String::isNotEmpty),
                )
            }
            val actual = d.location
            Place(
                d.displayName?.ifBlank { q } ?: q, cat, locality, d.formattedAddress.orEmpty(),
                actual?.latitude ?: lat, actual?.longitude ?: lon,
                phone = d.nationalPhoneNumber?.ifBlank { null } ?: d.internationalPhoneNumber?.ifBlank { null },
                website = d.websiteUri?.toString(),
                hours = today,
                rating = d.rating?.takeIf { it > 0 },
                ratingCount = d.userRatingCount?.takeIf { it > 0 },
                ratingSource = "Google",
                priceLevel = d.priceLevel?.takeIf { it in 1..4 },
                description = d.editorialSummary?.ifBlank { null },
                reviews = reviews,
                photoUrls = photos.map(ResolvedPhoto::uri),
                photoLabels = photos.map { it.attribution.orEmpty() },
                photoAttributionUrls = photos.map(ResolvedPhoto::attributionUri),
                dataAttributions = d.attributions.orEmpty().mapNotNull(::parseAttribution),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Runs the former box-side Apple Maps Web autocomplete/place-page parser directly on-device. The request
     * contract and returned rich fields match `appleplace.py`; no box URL, Apple account, or billable key is used.
     */
    suspend fun fetchApplePlace(q: String, lat: Double, lon: Double, selectedPlaceId: String? = null): Place? = withContext(Dispatchers.IO) {
        try {
            ApplePlaceClient.lookup(q, lat, lon, selectedPlaceId).also { result ->
                DiagLog.log(
                    "APPLEPLACE", "event=complete", "found=${if (result == null) 0 else 1}",
                    "photos=${result?.photoUrls?.size ?: 0}", "reviews=${result?.reviews?.size ?: 0}",
                    "hasHours=${if (result?.hours == null) 0 else 1}",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLog.log("APPLEPLACE", "event=failed", "error=${e.javaClass.simpleName}")
            null
        }
    }

    /** Parses one STAC/GeoVisio search feature into a [LookAround], per STREET_VIEW_ROADMAP.md A.3/1b.
     *  Returns null when required fields (coordinates, a thumb asset) are missing. */
    private fun parseLookFeature(f: JSONObject, refLat: Double, refLon: Double): LookAround? {
        val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val flon = coords.optDouble(0, Double.NaN); val flat = coords.optDouble(1, Double.NaN)
        if (flon.isNaN() || flat.isNaN()) return null
        val assets = f.optJSONObject("assets") ?: return null
        val thumb = assets.optJSONObject("thumb")?.optString("href")?.ifBlank { null } ?: return null
        val sd = assets.optJSONObject("sd")?.optString("href")?.ifBlank { null } ?: thumb
        val hd = assets.optJSONObject("hd")?.optString("href")?.ifBlank { null } ?: sd
        val instanceHost = try { URL(hd).let { "${it.protocol}://${it.host}" } } catch (e: Exception) { "" }
        val props = f.optJSONObject("properties")
        val io = props?.optJSONObject("pers:interior_orientation")
        val isPano = (io?.optDouble("field_of_view", 0.0) ?: 0.0) == 360.0
        val azV = props?.optDouble("view:azimuth", Double.NaN) ?: Double.NaN
        val producer = props?.optString("geovisio:producer")?.ifBlank { null }
        val dims = io?.optJSONArray("sensor_array_dimensions")
        val panoW = dims?.let { if (it.length() >= 2) it.optInt(0) else null }
        val panoH = dims?.let { if (it.length() >= 2) it.optInt(1) else null }
        val tileMatrix = props?.optJSONObject("tiles:tile_matrix_sets")?.optJSONObject("geovisio")
            ?.optJSONArray("tileMatrix")?.optJSONObject(0)
        val tileCols = tileMatrix?.let { if (it.has("matrixWidth")) it.optInt("matrixWidth") else null }
        val tileRows = tileMatrix?.let { if (it.has("matrixHeight")) it.optInt("matrixHeight") else null }
        val tileSize = tileMatrix?.let { if (it.has("tileWidth")) it.optDouble("tileWidth").toInt() else null }
        var prev: LookAroundLink? = null; var next: LookAroundLink? = null
        val related = mutableListOf<LookAroundLink>()
        val links = f.optJSONArray("links")
        if (links != null) for (i in 0 until links.length()) {
            val l = links.optJSONObject(i) ?: continue
            val rel = l.optString("rel")
            if (rel != "prev" && rel != "next" && rel != "related") continue
            val id = l.optString("id").ifBlank { null } ?: continue
            val g = l.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val lLon = g.optDouble(0, Double.NaN); val lLat = g.optDouble(1, Double.NaN)
            if (lLon.isNaN() || lLat.isNaN()) continue
            val href = l.optString("href")
            val collId = href.substringAfter("/collections/", "").substringBefore("/")
            if (collId.isBlank()) continue
            val link = LookAroundLink(id, collId, lLat, lLon)
            when (rel) { "prev" -> prev = link; "next" -> next = link; else -> related.add(link) }
        }
        val out = FloatArray(1); android.location.Location.distanceBetween(refLat, refLon, flat, flon, out)
        return LookAround(
            provider = LookAroundProvider.PANORAMAX,
            id = f.optString("id"), collectionId = f.optString("collection"), instanceHost = instanceHost,
            thumbUrl = thumb, sdUrl = sd, hdUrl = hd, isPano = isPano,
            azimuth = if (azV.isNaN()) null else azV, producer = producer,
            lat = flat, lon = flon, distanceMeters = out[0],
            panoWidth = panoW, panoHeight = panoH,
            tileCols = tileCols, tileRows = tileRows, tileSize = tileSize,
            prev = prev, next = next, related = related,
        )
    }

    private fun httpGetJson(urlStr: String): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "AppleMapsClone/0.1 (dev)")
            connectTimeout = 8000; readTimeout = 8000; instanceFollowRedirects = true
        }
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    }

    /** Finds Apple Look Around directly on-device, then uses the existing direct Panoramax cascade as fallback. */
    suspend fun lookAround(lat: Double, lon: Double): LookAround? = withContext(Dispatchers.IO) {
        try {
            AppleLookAroundClient.lookup(lat, lon)?.let { return@withContext it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLog.log("APPLELOOK", "event=lookupFailed", "error=${e.javaClass.simpleName}")
        }
        fun nearest(feats: org.json.JSONArray): LookAround? {
            var best: LookAround? = null; var bestD = Float.MAX_VALUE
            for (i in 0 until feats.length()) {
                val la = parseLookFeature(feats.getJSONObject(i), lat, lon) ?: continue
                if (la.distanceMeters < bestD) { bestD = la.distanceMeters; best = la }
            }
            return best
        }
        val lonF = "%.6f".format(java.util.Locale.US, lon); val latF = "%.6f".format(java.util.Locale.US, lat)
        try {
            val placeUrl = "https://api.panoramax.xyz/api/search?place_position=$lonF,$latF&place_distance=3-100&limit=30&filter=field_of_view%3D360"
            nearest(httpGetJson(placeUrl).optJSONArray("features") ?: org.json.JSONArray())?.let { return@withContext it }
        } catch (e: Exception) { /* fall through to bbox */ }
        try {
            val d = 0.005
            val bbox = "${lon - d},${lat - d},${lon + d},${lat + d}"
            val bboxUrl = "https://api.panoramax.xyz/api/search?bbox=$bbox&limit=100&filter=field_of_view%3D360"
            nearest(httpGetJson(bboxUrl).optJSONArray("features") ?: org.json.JSONArray())?.let { return@withContext it }
        } catch (e: Exception) { /* fall through to flat-photo bbox */ }
        try {
            val d = 0.0025
            val bbox = "${lon - d},${lat - d},${lon + d},${lat + d}"
            val bboxUrl = "https://api.panoramax.xyz/api/search?bbox=$bbox&limit=100"
            nearest(httpGetJson(bboxUrl).optJSONArray("features") ?: org.json.JSONArray())
        } catch (e: Exception) { null }
    }

    /** Fetches a single Panoramax item by (collectionId, itemId) — used to navigate prev/next/related
     *  links from an already-open Look Around viewer. STAC: GET /collections/{c}/items/{i} (A.4). */
    suspend fun lookAroundItem(collectionId: String, itemId: String, refLat: Double, refLon: Double): LookAround? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.panoramax.xyz/api/collections/$collectionId/items/$itemId"
                parseLookFeature(httpGetJson(url), refLat, refLon)
            } catch (e: Exception) { null }
        }
}

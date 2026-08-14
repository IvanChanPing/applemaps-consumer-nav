package com.example.applemaps.map

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Device-local translation of the former `appleplace.py` bridge. It performs the same Apple Maps Web
 * autocomplete request, opens the selected place page, and converts its server-rendered `shell-props`
 * place components into the app's existing [Place] model. No box endpoint or user-owned API key is used.
 */
internal object ApplePlaceClient {
    private const val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"

    private val shellPropsPattern = Regex(
        """<script id="shell-props" type="application/json"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val placeIdPattern = Regex(""""placeId"\s*:\s*"([^"]+)"""")

    private val amenityNames = mapOf(
        "ACCEPTS_APPLE_PAY" to "Apple Pay",
        "ACCEPTS_CREDIT_CARDS" to "Credit Cards",
        "HAS_FREE_WIFI" to "Free Wi-Fi",
        "WHEELCHAIR_ACCESSIBLE" to "Wheelchair Accessible",
        "HAS_DELIVERY" to "Delivery",
        "HAS_VEGETARIAN_OPTIONS" to "Vegetarian Options",
        "ACCEPTS_CONTACTLESS_PAYMENTS" to "Contactless Payments",
        "HAS_OUTDOOR_SEATING" to "Outdoor Seating",
        "GOOD_FOR_KIDS" to "Good for Kids",
        "HAS_STREET_PARKING" to "Street Parking",
        "GENDER_NEUTRAL_RESTROOM" to "Gender-Neutral Restrooms",
        "GOOD_FOR_GROUPS" to "Good for Groups",
        "TAKES_RESERVATIONS" to "Reservations",
    )

    /**
     * Resolves the selected consumer-map entity. A bridge-provided [selectedPlaceId] is authoritative:
     * autocomplete is only for searches that did not originate from Apple's selected annotation.
     */
    fun lookup(query: String, lat: Double, lon: Double, selectedPlaceId: String? = null): Place? {
        val placeId = selectedPlaceId?.takeIf { it.isNotBlank() && !it.startsWith("coordinate:") }
            ?: autocomplete(query, lat, lon) ?: return null
        val encodedId = URLEncoder.encode(placeId, Charsets.UTF_8.name())
        val html = get("https://maps.apple.com/place?place-id=$encodedId")
        return parsePlace(query, lat, lon, html)
    }

    private fun autocomplete(query: String, lat: Double, lon: Double): String? {
        val body = JSONObject()
            .put("q", query)
            .put("latlong", JSONObject().put("lat", lat).put("lng", lon))
            .put("span", JSONObject().put("latitudeDelta", 0.05).put("longitudeDelta", 0.05))
            .put(
                "analyticMetadata",
                JSONObject()
                    .put("appIdentifier", "com.apple.MapsWeb")
                    .put("appMajorVersion", "1")
                    .put("appMinorVersion", "1.7.372")
                    .put("isInternalInstall", false)
                    .put("isFromAPI", false)
                    .put("serviceTag", JSONObject().put("tag", "00000000-0000-0000-0000-000000000000"))
                    .put("sessionId", JSONObject().put("high", 1).put("low", 1)),
            )
            .put("dcc", "US")
        val response = post("https://maps.apple.com/data/search-autocomplete", body)
        return placeIdPattern.find(response)?.groupValues?.get(1)
    }

    private fun connection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Origin", "https://maps.apple.com")
        setRequestProperty("Referer", "https://maps.apple.com/")
        connectTimeout = 15_000
        readTimeout = 15_000
        instanceFollowRedirects = true
    }

    private fun post(url: String, body: JSONObject): String {
        val conn = connection(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String): String {
        val conn = connection(url).apply { setRequestProperty("Accept-Encoding", "gzip") }
        return try {
            val raw = conn.inputStream
            val stream = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
            stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    internal fun parsePlace(query: String, fallbackLat: Double, fallbackLon: Double, html: String): Place? {
        val encoded = shellPropsPattern.find(html)?.groupValues?.get(1) ?: return null
        val root = JSONObject(encoded)
        val cache = root.optJSONObject("initialState")?.optJSONObject("placeCache") ?: return null
        val cacheKey = cache.keys().asSequence().firstOrNull() ?: return null
        val rawPlace = cache.optJSONObject(cacheKey) ?: return null
        val components = linkedMapOf<String, JSONObject>()
        rawPlace.optJSONArray("component")?.let { array ->
            for (index in 0 until array.length()) {
                val component = array.optJSONObject(index) ?: continue
                component.optString("type").takeIf(String::isNotBlank)?.let { components.putIfAbsent(it, component) }
            }
        }

        fun componentValue(type: String): JSONObject? = components[type]
            ?.optJSONArray("value")?.optJSONObject(0)
        val entity = componentValue("COMPONENT_TYPE_ENTITY")?.optJSONObject("entity") ?: JSONObject()
        val categories = entity.optJSONArray("localizedCategory")
        var category: String? = null
        var broadCategory: String? = null
        if (categories != null) for (index in 0 until categories.length()) {
            val item = categories.optJSONObject(index) ?: continue
            val localized = firstString(item.opt("localizedName")) ?: continue
            if (broadCategory == null) broadCategory = localized
            if (item.optInt("level", 0) > 1 && category == null) category = localized
        }

        val containmentLine = componentValue("COMPONENT_TYPE_CONTAINMENT_PLACE")
            ?.optJSONObject("containmentPlace")?.optJSONObject("containmentLine")
            ?.optJSONArray("formatString")?.optString(0)
        val locality = containmentLine?.substringAfter("·", "")
            ?.replace(Regex("""\{/?s:s\}"""), "")?.trim()?.takeIf(String::isNotEmpty)

        val ratingComponent = components["COMPONENT_TYPE_RATING"]
        val rating = ratingComponent?.optJSONArray("value")?.optJSONObject(0)?.optJSONObject("rating")
        val ratingScore = rating?.optDouble("score", Double.NaN) ?: Double.NaN
        val ratingMaximum = rating?.optDouble("maxScore", Double.NaN) ?: Double.NaN

        val placeInfo = componentValue("COMPONENT_TYPE_PLACE_INFO")?.optJSONObject("placeInfo")
        val center = placeInfo?.optJSONObject("center")
        val actualLat = center?.optDouble("lat", Double.NaN)?.takeIf(Double::isFinite) ?: fallbackLat
        val actualLon = center?.optDouble("lng", Double.NaN)?.takeIf(Double::isFinite) ?: fallbackLon
        val timezone = placeInfo?.optJSONObject("timezone")?.optString("identifier")
            ?.takeIf(String::isNotBlank) ?: "UTC"
        val hours = parseHours(components["COMPONENT_TYPE_BUSINESS_HOURS"], timezone)

        val amenityDetails = parseAmenities(components["COMPONENT_TYPE_AMENITIES"])
        val reviews = parseReviews(components["COMPONENT_TYPE_REVIEW"])
        val categorizedPhotos = parseCategorizedPhotos(components["COMPONENT_TYPE_CATEGORIZED_PHOTOS"])
        val photos = categorizedPhotos?.albums?.mapNotNull { it.photos.firstOrNull()?.url }
            ?: parsePhotos(rawPlace.toString())
        val addressObject = componentValue("COMPONENT_TYPE_ADDRESS_OBJECT")?.optJSONObject("addressObject")
        val addressLines = addressObject?.optJSONArray("formattedAddressLines")
        val address = if (addressLines != null) (0 until addressLines.length())
            .mapNotNull { addressLines.optString(it).takeIf(String::isNotBlank) }.joinToString(", ")
        else ""
        val about = parseAbout(components, ::componentValue)
        val relatedPlaces = parseRelatedPlaces(components["COMPONENT_TYPE_TEMPLATE_PLACE"])
        val airportDetails = parseAirportDetails(components)
        val menuUrl = parseMenuUrl(components["COMPONENT_TYPE_QUICK_LINK"])

        return Place(
            name = firstString(entity.opt("name")) ?: query,
            category = category ?: broadCategory ?: "Place",
            locality = locality ?: addressObject?.optString("getDisplayLocality").orEmpty(),
            address = address.ifBlank { addressObject?.optString("shortAddress").orEmpty() },
            lat = actualLat,
            lon = actualLon,
            phone = entity.optString("phoneNumberFormatted").ifBlank {
                entity.optString("telephone").ifBlank { null }
            },
            website = entity.optString("url").ifBlank { null },
            hours = hours.today,
            open = hours.open,
            nextTransition = hours.nextTransition,
            rating = if (ratingScore.isFinite() && ratingMaximum > 0) ratingScore * 5.0 / ratingMaximum else null,
            ratingCount = rating?.optInt("numRatingsUsedForScore", 0)?.takeIf { it > 0 },
            ratingCountFormatted = rating?.optString("ratingsFormatted")?.ifBlank { null },
            ratingSource = ratingComponent?.optJSONObject("attribution")?.optString("displayName")?.ifBlank { null },
            description = about.first,
            aboutAttribution = about.second,
            amenities = amenityDetails.map(PlaceAmenity::name),
            amenityDetails = amenityDetails,
            photoLabels = categorizedPhotos?.albums?.map(PlacePhotoAlbum::title).orEmpty(),
            photoUrls = photos,
            photoAttributionUrls = categorizedPhotos?.albums?.map { it.photos.firstOrNull()?.actionUri }.orEmpty(),
            photoAlbums = categorizedPhotos?.albums.orEmpty(),
            reviews = reviews,
            alsoHere = relatedPlaces.map(RelatedPlace::name),
            relatedPlaces = relatedPlaces,
            airportDetails = airportDetails,
            menuUrl = menuUrl,
        )
    }

    /** Returns only Apple's explicit HTTPS “Menu” quick link; order/delivery links are intentionally excluded. */
    private fun parseMenuUrl(component: JSONObject?): String? {
        val values = component?.optJSONArray("value") ?: return null
        for (valueIndex in 0 until values.length()) {
            val links = values.optJSONObject(valueIndex)?.optJSONObject("quickLink")
                ?.optJSONArray("quickLinkItem") ?: continue
            for (linkIndex in 0 until links.length()) {
                val link = links.optJSONObject(linkIndex) ?: continue
                if (!firstString(link.opt("title")).equals("Menu", ignoreCase = true)) continue
                return link.optString("url").trim().takeIf { it.startsWith("https://") }
            }
        }
        return null
    }

    private data class CategorizedPhotos(val albums: List<PlacePhotoAlbum>)

    /** Maps Apple's airport/venue photo categories to the native tray's aligned cover-card model. */
    private fun parseCategorizedPhotos(component: JSONObject?): CategorizedPhotos? {
        val values = component?.optJSONArray("value") ?: return null
        val albums = mutableListOf<PlacePhotoAlbum>()
        for (valueIndex in 0 until values.length()) {
            val category = values.optJSONObject(valueIndex)?.optJSONObject("categorizedPhotos") ?: continue
            val label = firstString(category.opt("categoryName")) ?: continue
            val photos = category.optJSONArray("photo") ?: continue
            val parsed = (0 until minOf(10, photos.length())).mapNotNull { index ->
                val raw = photos.optJSONObject(index) ?: return@mapNotNull null
                val url = photoUrl(raw) ?: return@mapNotNull null
                val attribution = raw.optJSONObject("attribution")
                PlacePhoto(
                    url = url,
                    caption = raw.optString("caption").ifBlank { null },
                    author = raw.optString("author").ifBlank { null },
                    provider = attribution?.optString("displayName")?.ifBlank { null },
                    actionUri = raw.optString("viewPhotoActionUrl").ifBlank {
                        attribution?.optString("baseActionUrl").orEmpty()
                    }.ifBlank { null },
                )
            }
            if (parsed.isNotEmpty()) albums += PlacePhotoAlbum(label, parsed)
        }
        return CategorizedPhotos(albums).takeIf { albums.isNotEmpty() }
    }

    private fun photoUrl(value: JSONObject?): String? {
        val photo = value?.optJSONObject("photo") ?: value ?: return null
        val versions = photo.optJSONArray("photoVersion") ?: photo.optJSONArray("photoVersions") ?: return null
        val candidates = (0 until versions.length()).mapNotNull { index ->
            val version = versions.optJSONObject(index) ?: return@mapNotNull null
            version.optString("url").takeIf(String::isNotBlank)?.let { version.optString("urlType") to it }
        }
        val selected = candidates.firstOrNull { (type, url) ->
            type == "URL_TYPE_AMP_TEMPLATE" || "{w}" in url || "{h}" in url
        }?.second ?: candidates.firstOrNull()?.second ?: return null
        return selected.replace("{w}", "1200").replace("{h}", "1200").replace("{f}", "jpg")
    }

    private fun parseRelatedPlaces(component: JSONObject?): List<RelatedPlace> {
        val values = component?.optJSONArray("value") ?: return emptyList()
        val places = linkedMapOf<String, RelatedPlace>()
        for (valueIndex in 0 until values.length()) {
            val templates = values.optJSONObject(valueIndex)?.optJSONObject("templatePlace")
                ?.optJSONArray("templateData") ?: continue
            for (templateIndex in 0 until templates.length()) {
                val template = templates.optJSONObject(templateIndex) ?: continue
                val name = firstString(template.opt("title")) ?: continue
                val muid = template.optJSONObject("mapsId")?.optJSONObject("shardedId")
                    ?.optString("muid")?.takeIf { it.matches(Regex("[0-9]{1,20}")) } ?: continue
                val id = muid.toULongOrNull()?.toString(16)?.uppercase(Locale.US)?.let { "I$it" } ?: continue
                val ratingData = template.optJSONObject("footer")?.optJSONObject("ratingData")
                val rating = ratingData?.optJSONArray("rating")?.optJSONObject(0)
                places.putIfAbsent(
                    id,
                    RelatedPlace(
                        id = id,
                        name = name,
                        rating = rating?.optDouble("score", Double.NaN)?.takeIf(Double::isFinite),
                        ratingMaximum = rating?.optDouble("maxScore", Double.NaN)?.takeIf(Double::isFinite),
                        ratingCount = rating?.optInt("numRatingsUsedForScore", 0)?.takeIf { it > 0 },
                        ratingSource = ratingData?.optString("vendorName")?.ifBlank { null },
                    ),
                )
            }
        }
        return places.values.toList()
    }

    private fun parseAbout(
        components: Map<String, JSONObject>,
        componentValue: (String) -> JSONObject?,
    ): Pair<String?, PlaceAttribution?> {
        val block = componentValue("COMPONENT_TYPE_TEXT_BLOCK")?.optJSONObject("textBlock")
        val text = firstString(components["COMPONENT_TYPE_ABOUT"]?.opt("value"))
            ?: block?.let { firstString(it.opt("text")) }
            ?: firstString(components["COMPONENT_TYPE_RESULT_SNIPPET"]?.opt("value"))
        val title = block?.let { firstString(it.opt("title")) }
        val uri = block?.optString("attributionUrl")?.ifBlank { null }
        return text to title?.let { PlaceAttribution(it, uri) }
    }

    private fun parseAirportDetails(components: Map<String, JSONObject>): AirportDetails? {
        fun first(type: String, key: String): JSONObject? = components[type]
            ?.optJSONArray("value")?.optJSONObject(0)?.optJSONObject(key)

        val boundsJson = first("COMPONENT_TYPE_BOUNDS", "bounds")?.optJSONObject("mapRegion")
        val bounds = boundsJson?.let {
            PlaceBounds(
                it.optDouble("southLat"), it.optDouble("westLng"),
                it.optDouble("northLat"), it.optDouble("eastLng"),
            ).takeIf { b -> listOf(b.southLat, b.westLon, b.northLat, b.eastLon).all(Double::isFinite) }
        }

        val venue = first("COMPONENT_TYPE_VENUE_INFO", "venueInfo")
        val featureVenue = venue?.optJSONObject("featureValue")?.optJSONObject("featureVenue")
        val levelNames = linkedMapOf<String, String>()
        featureVenue?.optJSONArray("level")?.let { levels ->
            for (index in 0 until levels.length()) {
                val level = levels.optJSONObject(index) ?: continue
                val id = level.optString("levelId")
                if (id.isBlank()) continue
                firstString(level.optJSONObject("label")?.opt("name"))?.let { levelNames[id] = it }
            }
        }
        val terminals = mutableListOf<VenueTerminal>()
        featureVenue?.optJSONArray("building")?.let { buildings ->
            for (index in 0 until buildings.length()) {
                val building = buildings.optJSONObject(index) ?: continue
                val name = firstString(building.optJSONObject("label")?.opt("name")) ?: continue
                val ids = building.optJSONArray("levelId")
                val levels = if (ids == null) emptyList() else (0 until ids.length())
                    .mapNotNull { levelNames[ids.optString(it)] }.distinct()
                terminals += VenueTerminal(name, levels)
            }
        }
        val airlines = venue?.optJSONObject("itemList")?.optJSONArray("item")?.let { items ->
            (0 until items.length()).mapNotNull { items.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        val code = featureVenue?.optJSONObject("venueContainer")?.optJSONObject("label")
            ?.optString("nameShort")?.ifBlank { null }

        val browse = first("COMPONENT_TYPE_BROWSE_CATEGORIES", "browseCategories")
            ?.optJSONArray("browseCategory")
        val categories = if (browse == null) emptyList() else (0 until browse.length()).mapNotNull { index ->
            val item = browse.optJSONObject(index) ?: return@mapNotNull null
            val label = item.optString("displayString").ifBlank { return@mapNotNull null }
            val query = item.optString("popularDisplayToken").ifBlank { label }
            val sub = item.optJSONArray("subCategory")?.let { array ->
                (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("displayString")?.takeIf(String::isNotBlank) }
            }.orEmpty()
            AirportBrowseCategory(label, query, sub)
        }

        val access = first("COMPONENT_TYPE_ROAD_ACCESS_INFO", "accessInfo")
            ?.optJSONArray("roadAccessPoint")
        val accessPoints = if (access == null) emptyList() else (0 until access.length()).mapNotNull { index ->
            val point = access.optJSONObject(index) ?: return@mapNotNull null
            val location = point.optJSONObject("location") ?: return@mapNotNull null
            val lat = location.optDouble("lat", Double.NaN)
            val lon = location.optDouble("lng", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            PlaceAccessPoint(
                lat = lat,
                lon = lon,
                walking = point.optString("walkingDirection").isNotBlank(),
                driving = point.optString("drivingDirection").isNotBlank(),
            )
        }
        val elevation = first("COMPONENT_TYPE_FACTOID", "factoid")
            ?.takeIf { it.optString("entryType") == "ELEVATION" }
            ?.optDouble("number", Double.NaN)?.takeIf(Double::isFinite)

        return AirportDetails(code, terminals, airlines, categories, bounds, accessPoints, elevation)
            .takeIf { code != null || terminals.isNotEmpty() || categories.isNotEmpty() || accessPoints.isNotEmpty() }
    }

    private data class ParsedHours(val today: String?, val open: Boolean?, val nextTransition: String?)

    private fun parseHours(component: JSONObject?, timezone: String): ParsedHours {
        val weekly = component?.optJSONArray("value")?.optJSONObject(0)
            ?.optJSONObject("businessHours")?.optJSONArray("weeklyHours")
            ?: return ParsedHours(null, null, null)
        val byDay = linkedMapOf<String, MutableList<Pair<Int, Int>>>()
        for (index in 0 until weekly.length()) {
            val entry = weekly.optJSONObject(index) ?: continue
            val ranges = mutableListOf<Pair<Int, Int>>()
            entry.optJSONArray("timeRange")?.let { array ->
                for (rangeIndex in 0 until array.length()) {
                    val range = array.optJSONObject(rangeIndex) ?: continue
                    if (range.has("from") && range.has("to")) ranges += range.optInt("from") to range.optInt("to")
                }
            }
            entry.optJSONArray("day")?.let { days ->
                for (dayIndex in 0 until days.length()) byDay.getOrPut(days.optString(dayIndex)) { mutableListOf() }
                    .addAll(ranges)
            }
        }
        val now = runCatching { ZonedDateTime.now(ZoneId.of(timezone)) }.getOrElse { ZonedDateTime.now(ZoneId.of("UTC")) }
        val dayNames = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        val todayRanges = byDay[dayNames[now.dayOfWeek.value - 1]].orEmpty().sortedBy(Pair<Int, Int>::first)
        val todayText = todayRanges.takeIf(List<Pair<Int, Int>>::isNotEmpty)
            ?.joinToString(", ") { (from, to) -> "${formatTime(from)} – ${formatTime(to)}" }
        val nowSeconds = now.hour * 3600 + now.minute * 60 + now.second
        val active = todayRanges.firstOrNull { (from, to) -> nowSeconds in from until to }
        if (active != null) return ParsedHours(todayText, true, "Closes ${formatTime(active.second)}")
        val laterToday = todayRanges.firstOrNull { it.first > nowSeconds }
        if (laterToday != null) return ParsedHours(todayText, false, "Opens ${formatTime(laterToday.first)}")
        for (delta in 1..7) {
            val nextDay = dayNames[(now.dayOfWeek.value - 1 + delta) % 7]
            val nextRange = byDay[nextDay].orEmpty().minByOrNull(Pair<Int, Int>::first) ?: continue
            val prefix = if (delta == 1) "tomorrow " else "${nextDay.lowercase().replaceFirstChar(Char::uppercase)} "
            return ParsedHours(todayText, false, "Opens $prefix${formatTime(nextRange.first)}")
        }
        return ParsedHours(todayText, false, null)
    }

    private fun formatTime(seconds: Int): String {
        val normalized = Math.floorMod(seconds, 86_400)
        val hour = normalized / 3600
        val minute = normalized % 3600 / 60
        val suffix = if (hour < 12) "AM" else "PM"
        val hour12 = (hour % 12).takeIf { it != 0 } ?: 12
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, suffix)
    }

    private fun parseAmenities(component: JSONObject?): List<PlaceAmenity> {
        val container = component?.optJSONArray("value")?.optJSONObject(0)
            ?.optJSONObject("amenities") ?: return emptyList()
        val result = linkedMapOf<String, PlaceAmenity>()
        container.optJSONArray("amenityV2")?.let { amenities ->
            for (index in 0 until amenities.length()) {
                val amenity = amenities.optJSONObject(index) ?: continue
                if (!amenity.optBoolean("amenityPresent")) continue
                val name = firstString(amenity.opt("name")) ?: continue
                result[name] = PlaceAmenity(name, amenity.optString("symbolImageName").ifBlank { null })
            }
        }
        container.optJSONArray("amenity")?.let { amenities ->
            for (index in 0 until amenities.length()) {
                val amenity = amenities.optJSONObject(index) ?: continue
                if (!amenity.optBoolean("amenityPresent")) continue
                val type = amenity.optString("amenityType").takeIf(String::isNotBlank) ?: continue
                val name = amenityNames[type] ?: type.lowercase().split('_')
                    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                result.putIfAbsent(name, PlaceAmenity(name))
            }
        }
        return result.values.toList()
    }

    private fun parseReviews(component: JSONObject?): List<Review> {
        val values = component?.optJSONArray("value") ?: return emptyList()
        val result = mutableListOf<Review>()
        for (index in 0 until minOf(3, values.length())) {
            val review = values.optJSONObject(index)?.optJSONObject("review") ?: continue
            val text = firstString(review.opt("snippet"))?.trim().orEmpty()
            if (text.isEmpty()) continue
            result += Review(
                author = review.optJSONObject("reviewer")?.optString("name").orEmpty(),
                text = text,
            )
        }
        return result
    }

    internal fun parsePhotos(blob: String): List<String> {
        val normalizedBlob = blob
            .replace(Regex("""\\+/"""), "/")
            .replace(Regex("""\\+u0026""", RegexOption.IGNORE_CASE), "&")
        val photoPattern = Regex(
            """https://[^"\\ ]*(?:mzstatic\.com|otstatic\.com)[^"\\ ]*?\.(?:jpg|jpeg|png|webp)[^"\\ ]*""",
            RegexOption.IGNORE_CASE,
        )
        val sizePattern = Regex("""/\d+x\d+([a-z]{0,2})\.(jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)
        return photoPattern.findAll(normalizedBlob).map { match ->
            sizePattern.replace(match.value) { size -> "/1200x1200${size.groupValues[1]}.${size.groupValues[2]}" }
        }.distinct().take(12).toList()
    }

    private fun firstString(value: Any?): String? = when (value) {
        is String -> value.takeIf(String::isNotBlank)
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { firstString(value.opt(it)) }.firstOrNull()
        is JSONObject -> value.optString("stringValue").takeIf(String::isNotBlank)
            ?: listOf("localizedName", "name", "displayName", "text", "title").asSequence()
                .mapNotNull { key -> firstString(value.opt(key)) }.firstOrNull()
        else -> null
    }
}

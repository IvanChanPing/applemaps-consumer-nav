package com.example.applemaps.map

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.GZIPInputStream

data class AppleBrowsePlace(val id: String, val place: Place, val note: String? = null)
data class AppleCategoryResults(val title: String, val places: List<AppleBrowsePlace>)
data class AppleHomeCategory(val label: String, val query: String)
data class AppleHomeContent(
    val categoryTitle: String,
    val categories: List<AppleHomeCategory>,
    val guideTitle: String,
    val guides: List<AppleGuide>,
    val refreshToken: Long,
)
data class AppleGuide(
    val id: String,
    val title: String,
    val publisher: String,
    val description: String?,
    val heroUrl: String?,
    val places: List<AppleBrowsePlace>,
)

/**
 * Device-local Apple Maps Web retrieval for the native Find Nearby and Guide trays. It mirrors
 * [ApplePlaceClient]: bounded HTTPS requests, no account/API key/box relay, strict parsing into app
 * models, and no WebView DOM scraping. Search responses may mix places and Guides; only valid place
 * results are retained. Guide pages come from their server-rendered shell cache.
 */
internal object AppleBrowseClient {
    private const val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"
    private const val APPLE_EPOCH_UNIX_SECONDS = 978_307_200L
    private const val RESULT_LIMIT = 20
    private const val HOME_GUIDE_LIMIT = 6
    private val shellPropsPattern = Regex(
        """<script id="shell-props" type="application/json"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    fun category(query: String, lat: Double, lon: Double): AppleCategoryResults? {
        if (query.isBlank() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val now = ZonedDateTime.now()
        val appleTime = System.currentTimeMillis() / 1000L - APPLE_EPOCH_UNIX_SECONDS
        val timezoneHours = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000
        val serviceTag = UUID.randomUUID().toString()
        val body = JSONObject()
            .put("ull", JSONObject.NULL)
            .put("timeSinceMapViewportChanged", 1)
            .put("sll", JSONObject().put("lat", lat).put("lng", lon))
            .put("span", JSONObject().put("latitudeDelta", 0.12).put("longitudeDelta", 0.12))
            .put("dcc", Locale.getDefault().country.uppercase().ifBlank { "US" })
            .put("q", query)
            .put(
                "clientTimeInfo",
                JSONObject()
                    .put("clientRequestTime", appleTime)
                    .put("clientTimezoneOffset", timezoneHours)
                    .put("clientHourOfDay", now.hour)
                    .put("clientDayOfWeek", now.dayOfWeek.value),
            )
            .put(
                "analyticMetadata",
                JSONObject()
                    .put("appIdentifier", "com.apple.MapsWeb")
                    .put("appMajorVersion", "1")
                    .put("appMinorVersion", "1.7.378")
                    .put("isInternalInstall", false)
                    .put("isFromAPI", false)
                    .put(
                        "requestTime",
                        JSONObject()
                            .put("timeRoundedToHour", appleTime)
                            .put("timezoneOffsetFromGmtInHours", timezoneHours),
                    )
                    .put("serviceTag", JSONObject().put("tag", serviceTag))
                    .put("hardwareModel", "Android")
                    .put("osVersion", "Android")
                    .put("productName", "Android")
                    .put(
                        "sessionId",
                        JSONObject()
                            .put("high", appleTime)
                            .put("low", (appleTime xor query.hashCode().toLong()) and Long.MAX_VALUE),
                    )
                    .put("relativeTimestamp", 0)
                    .put("sequenceNumber", 1),
            )
        return parseCategoryResponse(query, post("https://maps.apple.com/data/search", body))
    }

    /** Retrieves the current region-scoped Apple home feed and resolves its ordered Guide records concurrently. */
    suspend fun home(lat: Double, lon: Double): AppleHomeContent? = coroutineScope {
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@coroutineScope null
        val seed = withContext(Dispatchers.IO) {
            parseHomeResponse(post("https://maps.apple.com/data/search-home", homeRequestBody(lat, lon)))
        } ?: return@coroutineScope null
        val guides = seed.guideIds.take(HOME_GUIDE_LIMIT).map { curatedId ->
            async(Dispatchers.IO) {
                try {
                    guide(curatedId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
        if (guides.isEmpty()) return@coroutineScope null
        AppleHomeContent(
            categoryTitle = seed.categoryTitle,
            categories = seed.categories,
            guideTitle = seed.guideTitle,
            guides = guides,
            refreshToken = System.nanoTime(),
        )
    }

    fun guide(curatedId: String): AppleGuide? {
        if (!curatedId.matches(Regex("[0-9]{1,20}"))) return null
        val encodedId = URLEncoder.encode(curatedId, Charsets.UTF_8.name())
        return parseGuideHtml(
            curatedId,
            get("https://maps.apple.com/guides?curated=$encodedId&_provider=9902"),
        )
    }

    internal fun parseCategoryResponse(query: String, raw: String): AppleCategoryResults? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optString("status") != "STATUS_SUCCESS") return null
        val results = root.optJSONArray("mapsResult") ?: return null
        val places = mutableListOf<AppleBrowsePlace>()
        for (index in 0 until results.length()) {
            if (places.size >= RESULT_LIMIT) break
            val result = results.optJSONObject(index) ?: continue
            if (result.optString("resultType") != "MAPS_RESULT_TYPE_PLACE") continue
            parsePlace(result.optJSONObject("place") ?: continue)?.let(places::add)
        }
        return AppleCategoryResults(query, places).takeIf { it.places.isNotEmpty() }
    }

    internal data class AppleHomeSeed(
        val categoryTitle: String,
        val categories: List<AppleHomeCategory>,
        val guideTitle: String,
        val guideIds: List<String>,
    )

    internal fun parseHomeResponse(raw: String): AppleHomeSeed? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optString("status") != "STATUS_SUCCESS") return null
        val sections = root.optJSONObject("globalResult")
            ?.optJSONObject("mapsSearchHomeResult")
            ?.optJSONArray("mapsSearchHomeSection") ?: return null
        var categoryTitle = "Find Nearby"
        var guideTitle = "Guides We Love"
        val categories = mutableListOf<AppleHomeCategory>()
        val guideIds = mutableListOf<String>()
        for (index in 0 until sections.length()) {
            val section = sections.optJSONObject(index) ?: continue
            section.optJSONObject("searchBrowseCategorySuggestionResult")?.optJSONArray("category")?.let { items ->
                categoryTitle = section.optString("name").ifBlank { categoryTitle }
                for (itemIndex in 0 until items.length()) {
                    val item = items.optJSONObject(itemIndex) ?: continue
                    val label = item.optString("shortDisplayString").ifBlank { item.optString("displayString") }
                    val query = item.optString("popularDisplayToken").ifBlank { item.optString("displayString") }
                    if (label.isNotBlank() && query.isNotBlank()) categories += AppleHomeCategory(label, query)
                }
            }
            section.optJSONObject("collectionSuggestionResult")?.optJSONArray("collectionId")?.let { items ->
                guideTitle = section.optString("name").ifBlank { guideTitle }
                for (itemIndex in 0 until items.length()) {
                    items.optJSONObject(itemIndex)?.optJSONObject("shardedId")?.optString("muid")
                        ?.takeIf { it.matches(Regex("[0-9]{1,20}")) }
                        ?.let(guideIds::add)
                }
            }
        }
        return AppleHomeSeed(categoryTitle, categories, guideTitle, guideIds)
            .takeIf { it.categories.isNotEmpty() && it.guideIds.isNotEmpty() }
    }

    internal fun parseGuideHtml(curatedId: String, html: String): AppleGuide? {
        val encoded = shellPropsPattern.find(html)?.groupValues?.get(1) ?: return null
        val root = runCatching { JSONObject(encoded) }.getOrNull() ?: return null
        val guide = root.optJSONObject("initialState")?.optJSONObject("placeCache")
            ?.optJSONObject(curatedId) ?: return null
        val places = mutableListOf<AppleBrowsePlace>()
        guide.optJSONArray("items")?.let { items ->
            for (index in 0 until minOf(items.length(), RESULT_LIMIT)) {
                val item = items.optJSONObject(index) ?: continue
                val parsed = parsePlace(item.optJSONObject("placeData") ?: continue) ?: continue
                places += parsed.copy(note = firstString(item.opt("descriptionLines")))
            }
        }
        if (places.isEmpty()) return null
        return AppleGuide(
            id = curatedId,
            title = firstString(guide.opt("longTitleLines"))
                ?: firstString(guide.opt("titleLines")) ?: return null,
            publisher = guide.optJSONObject("publisher")?.optString("name").orEmpty(),
            description = firstString(guide.opt("descriptionLines")),
            heroUrl = guidePhoto(guide.optJSONArray("photos")),
            places = places,
        )
    }

    private fun parsePlace(raw: JSONObject): AppleBrowsePlace? {
        val components = linkedMapOf<String, JSONObject>()
        raw.optJSONArray("component")?.let { array ->
            for (index in 0 until array.length()) {
                val component = array.optJSONObject(index) ?: continue
                component.optString("type").takeIf(String::isNotBlank)
                    ?.let { components.putIfAbsent(it, component) }
            }
        }
        fun value(type: String): JSONObject? =
            components[type]?.optJSONArray("value")?.optJSONObject(0)
        val entity = value("COMPONENT_TYPE_ENTITY")?.optJSONObject("entity") ?: return null
        val mapsId = raw.optJSONObject("mapsId")?.optJSONObject("shardedId")
        val center = mapsId?.optJSONObject("center")
        val lat = center?.optDouble("lat", Double.NaN) ?: Double.NaN
        val lon = center?.optDouble("lng", Double.NaN) ?: Double.NaN
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val snippet = value("COMPONENT_TYPE_RESULT_SNIPPET")?.optJSONObject("resultSnippet")
        val address = value("COMPONENT_TYPE_ADDRESS_OBJECT")?.optJSONObject("addressObject")
        val name = firstString(entity.opt("name")) ?: snippet?.optString("name")?.ifBlank { null } ?: return null
        val categories = entity.optJSONArray("localizedCategory")
        var broadCategory: String? = null
        var specificCategory: String? = null
        if (categories != null) for (index in 0 until categories.length()) {
            val category = categories.optJSONObject(index) ?: continue
            val text = firstString(category.opt("localizedName")) ?: continue
            if (broadCategory == null) broadCategory = text
            if (category.optInt("level") > 1 && specificCategory == null) specificCategory = text
        }
        val formatted = address?.optJSONArray("formattedAddressLines")
        val addressText = if (formatted == null) "" else (0 until formatted.length())
            .mapNotNull { formatted.optString(it).takeIf(String::isNotBlank) }.joinToString(", ")
        val id = raw.optString("muid").ifBlank {
            mapsId?.optString("muid").orEmpty()
        }.ifBlank { "coordinate:$lat,$lon" }
        return AppleBrowsePlace(
            id = id,
            place = Place(
                name = name,
                category = snippet?.optString("category")?.ifBlank { null }
                    ?: specificCategory ?: broadCategory ?: "Place",
                locality = snippet?.optString("locationString")?.ifBlank { null }
                    ?: address?.optString("getDisplayLocality").orEmpty(),
                address = addressText.ifBlank { address?.optString("shortAddress").orEmpty() },
                lat = lat,
                lon = lon,
            ),
        )
    }

    private fun guidePhoto(photos: JSONArray?): String? {
        val versions = photos?.optJSONObject(0)?.optJSONObject("photo")
            ?.optJSONArray("photoVersions") ?: return null
        var template: String? = null
        for (index in 0 until versions.length()) {
            val version = versions.optJSONObject(index) ?: continue
            val url = version.optString("url").ifBlank { null } ?: continue
            if (version.optString("urlType") == "URL_TYPE_REGULAR") return url
            if (template == null) template = url
        }
        return template?.replace("{w}", "1200")?.replace("{h}", "800")
            ?.replace("{c}", "cc")?.replace("{f}", "jpg")
    }

    private fun homeRequestBody(lat: Double, lon: Double): JSONObject {
        val now = ZonedDateTime.now()
        val appleTime = System.currentTimeMillis() / 1000L - APPLE_EPOCH_UNIX_SECONDS
        val timezoneHours = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000
        return JSONObject()
            .put("ull", JSONObject.NULL)
            .put("timeSinceMapViewportChanged", 1)
            .put("latlong", JSONObject().put("lat", lat).put("lng", lon))
            .put("span", JSONObject().put("latitudeDelta", 0.08).put("longitudeDelta", 0.12))
            .put("dcc", Locale.getDefault().country.uppercase().ifBlank { "US" })
            .put(
                "clientTimeInfo",
                JSONObject()
                    .put("clientRequestTime", appleTime)
                    .put("clientTimezoneOffset", timezoneHours)
                    .put("clientHourOfDay", now.hour)
                    .put("clientDayOfWeek", now.dayOfWeek.value),
            )
            .put(
                "analyticMetadata",
                JSONObject()
                    .put("appIdentifier", "com.apple.MapsWeb")
                    .put("appMajorVersion", "1")
                    .put("appMinorVersion", "1.7.378")
                    .put("isInternalInstall", false)
                    .put("isFromAPI", false)
                    .put(
                        "requestTime",
                        JSONObject()
                            .put("timeRoundedToHour", appleTime)
                            .put("timezoneOffsetFromGmtInHours", timezoneHours),
                    )
                    .put("serviceTag", JSONObject().put("tag", UUID.randomUUID().toString()))
                    .put("hardwareModel", "Android")
                    .put("osVersion", "Android")
                    .put("productName", "Android")
                    .put(
                        "sessionId",
                        JSONObject()
                            .put("high", appleTime)
                            .put("low", (appleTime xor lat.hashCode().toLong() xor lon.hashCode().toLong()) and Long.MAX_VALUE),
                    )
                    .put("relativeTimestamp", 0)
                    .put("sequenceNumber", 1),
            )
    }

    private fun firstString(value: Any?): String? = when (value) {
        is String -> value.takeIf(String::isNotBlank)
        is JSONArray -> (0 until value.length()).asSequence()
            .mapNotNull { firstString(value.opt(it)) }.firstOrNull()
        is JSONObject -> value.optString("stringValue").takeIf(String::isNotBlank)
            ?: listOf("localizedName", "name", "displayName", "text", "title").asSequence()
                .mapNotNull { firstString(value.opt(it)) }.firstOrNull()
        else -> null
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
        val connection = connection(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun get(url: String): String {
        val connection = connection(url).apply { setRequestProperty("Accept-Encoding", "gzip") }
        return try {
            val raw = connection.inputStream
            val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
            stream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

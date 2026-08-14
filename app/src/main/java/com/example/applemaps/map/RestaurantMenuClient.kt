package com.example.applemaps.map

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Currency
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Restaurant Menu data client for the place card's user-facing Menu over-sheet.
 *
 * Apple place pages identify a restaurant's actual menu URL but do not embed the dishes. This client opens that
 * HTTPS URL off the main thread through [PlaceRepository.fetchRestaurantMenu], reads its standard schema.org `Menu`
 * JSON-LD, and associates optional row photos from the same document by item URL. Missing/changed source markup
 * returns null; [com.example.applemaps.ui.RestaurantMenuPage] then keeps the source link visible instead of fabricating
 * menu content. Exercise it by selecting a restaurant with an Apple “Menu” quick link and tapping Menu in the card.
 * Parser behavior is unit-tested. Real emulator taps verify the Menu action, independent sheet, and unavailable-source state; a route that
 * can reach the source is still required to exercise loaded rows and filters in the physical UI.
 */
internal object RestaurantMenuClient {
    private const val MAX_HTML_CHARS = 2_000_000
    private const val MAX_SECTIONS = 30
    private const val MAX_ITEMS_PER_SECTION = 200
    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0 Mobile Safari/537.36"

    private val jsonLdPattern = Regex(
        """<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val mobilePhotoPattern = Regex(
        """data-url\s*=\s*["']([^"']+)["'][^>]*>.*?<img[^>]*src\s*=\s*["']([^"']+)["']""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun fetch(sourceUrl: String): RestaurantMenu? {
        val source = runCatching { URL(sourceUrl) }.getOrNull()
            ?.takeIf { it.protocol.equals("https", ignoreCase = true) } ?: return null
        val connection = (source.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Accept-Encoding", "gzip")
        }
        return try {
            if (connection.responseCode !in 200..299 || !connection.url.protocol.equals("https", ignoreCase = true)) {
                null
            } else {
                val raw = connection.inputStream
                val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
                val html = InputStreamReader(stream, Charsets.UTF_8).use(::readBounded) ?: return null
                parse(connection.url.toString(), html)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(reader: InputStreamReader): String? {
        val result = StringBuilder(minOf(MAX_HTML_CHARS, 64 * 1024))
        val buffer = CharArray(8 * 1024)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) return result.toString()
            if (result.length + count > MAX_HTML_CHARS) return null
            result.append(buffer, 0, count)
        }
    }

    internal fun parse(sourceUrl: String, html: String): RestaurantMenu? {
        val menuNode = jsonLdPattern.findAll(html).mapNotNull { match ->
            runCatching { JSONObject(match.groupValues[1].trim()) }.getOrNull()?.let(::findMenuNode)
                ?: runCatching { JSONArray(match.groupValues[1].trim()) }.getOrNull()?.let(::findMenuNode)
        }.firstOrNull() ?: return null
        val photoUrlsByItemPath = mobilePhotoPattern.findAll(html).mapNotNull { match ->
            val path = normalizedPath(match.groupValues[1]) ?: return@mapNotNull null
            val image = decodeHtml(match.groupValues[2]).takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            path to image.replace(Regex("/(?:60s|ms)\\.(?=[A-Za-z]+(?:[?#]|$))"), "/300s.")
        }.toMap()

        val sections = mutableListOf<RestaurantMenuSection>()
        val rawSections = jsonObjects(menuNode.opt("hasMenuSection"))
        val sectionNodes = if (rawSections.isEmpty()) listOf(menuNode) else rawSections
        for ((index, section) in sectionNodes.take(MAX_SECTIONS).withIndex()) {
            val items = jsonObjects(section.opt("hasMenuItem")).take(MAX_ITEMS_PER_SECTION).mapNotNull { item ->
                val name = item.optString("name").trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
                val itemUrl = item.optString("url").trim().takeIf(String::isNotEmpty)
                    ?.let { runCatching { URL(URL(sourceUrl), it).toString() }.getOrNull() }
                RestaurantMenuItem(
                    name = name,
                    description = item.optString("description").trim().takeIf(String::isNotEmpty),
                    price = jsonObjects(item.opt("offers")).firstNotNullOfOrNull(::formatPrice),
                    imageUrl = itemUrl?.let(::normalizedPath)?.let(photoUrlsByItemPath::get),
                    sourceUrl = itemUrl,
                )
            }
            if (items.isNotEmpty()) {
                sections += RestaurantMenuSection(
                    name = section.optString("name").trim().takeIf(String::isNotEmpty)
                        ?: if (sectionNodes.size == 1) "Menu" else "Section ${index + 1}",
                    items = items,
                )
            }
        }
        if (sections.isEmpty()) return null
        val host = runCatching { URL(sourceUrl).host.removePrefix("www.") }.getOrDefault("")
        val sourceName = if (host.equals("yelp.com", ignoreCase = true)) "Yelp" else host.ifBlank { "Menu source" }
        return RestaurantMenu(
            name = menuNode.optString("name").trim().takeIf(String::isNotEmpty),
            sourceUrl = sourceUrl,
            sourceName = sourceName,
            sections = sections,
        )
    }

    private fun findMenuNode(value: Any?): JSONObject? = when (value) {
        is JSONObject -> when {
            value.optString("@type").equals("Menu", ignoreCase = true) -> value
            else -> value.keys().asSequence().mapNotNull { findMenuNode(value.opt(it)) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findMenuNode(value.opt(it)) }.firstOrNull()
        else -> null
    }

    private fun jsonObjects(value: Any?): List<JSONObject> = when (value) {
        is JSONObject -> listOf(value)
        is JSONArray -> (0 until value.length()).mapNotNull(value::optJSONObject)
        else -> emptyList()
    }

    private fun formatPrice(offer: JSONObject): String? {
        val raw = offer.opt("price")?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val number = raw.toBigDecimalOrNull() ?: return raw
        val currency = offer.optString("priceCurrency").trim().takeIf(String::isNotEmpty) ?: return raw
        val symbol = runCatching { Currency.getInstance(currency).getSymbol(Locale.US) }.getOrElse { "$currency " }
        return symbol + number.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    }

    private fun normalizedPath(url: String): String? = runCatching { URL(URL("https://menu.invalid"), decodeHtml(url)).path }
        .getOrNull()?.trimEnd('/')?.takeIf(String::isNotEmpty)

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}

package com.example.applemaps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.applemaps.R
import com.example.applemaps.diag.DiagLog
import com.example.applemaps.map.Place
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.applemaps.ui.theme.LocalAppleColors
import kotlin.math.roundToInt

/**
 * Place card — split into the always-visible HEADER (title/subtitle/actions + close) and the scrollable
 * DETAILS body, matching the measured maps.apple.com card (spec/layout/measure_all.json):
 * title 28sp/700 · subtitle 14sp ("Category · " + accent locality) · Directions 52dp with a white circular
 * badge/blue turn glyph · Share/Close 30dp
 * circles (bg rgba(199,199,199,0.36)) · Hours/Ratings/Accepts before media · "Details" 20sp/600 ·
 * platter cells (14sp title / 17sp content). Final ordering and clipping remain phone-UI unverified.
 */
@Composable
fun PlaceCardHeader(place: Place, onDirections: () -> Unit = {}, onClose: () -> Unit = {}) {
    val c = LocalAppleColors.current
    val context = LocalContext.current
    // P1 3.3: transition state (not a plain Boolean) so the More popover can play its 150ms exit before unmounting
    val moreState = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    fun fire(i: android.content.Intent) { runCatching { context.startActivity(i) } }
    fun webUrl() = place.website?.let { if (it.startsWith("http")) it else "https://$it" }
    fun share() = fire(android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, listOfNotNull(place.name, place.address.ifBlank { null }, webUrl()).joinToString("\n")), "Share"))
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(place.name, color = c.glyphDefault, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                Spacer(Modifier.height(4.dp))
                // placeCategoryLocalityLine — muted category + blue locality; always one line so it cannot
                // increase the resting card height. Long localities truncate at the trailing edge.
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.glyphMuted)) { append(place.category) }
                        if (place.locality.isNotEmpty()) {
                            withStyle(SpanStyle(color = c.glyphMuted)) { append(" · ") }
                            withStyle(SpanStyle(color = Color(0xFF0A7BFF))) { append(place.locality) }
                        }
                    },
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CircleIconButton(R.drawable.ic_square_and_arrow_up, "Share") { share() }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(R.drawable.ic_xmark, "Close", iconSize = 12.dp, onClick = onClose)
        }
        Spacer(Modifier.height(16.dp))
        // Action row — Directions (primary accent) + Call / Website / More (measured: r10, 11sp/600 label).
        // Only the AVAILABLE actions are shown, and each now fires the real intent (dial / browser / share).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                R.drawable.ic_action_turn,
                "Directions",
                Modifier.weight(1f),
                primary = true,
                iconSize = 18.dp,
                iconBadge = true,
                onClick = onDirections,
            )
            if (!place.phone.isNullOrEmpty()) ActionButton(R.drawable.ic_phone_fill, "Call", Modifier.weight(1f)) {
                fire(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${place.phone}")))
            }
            if (!place.website.isNullOrEmpty()) ActionButton(R.drawable.ic_safari_fill, "Website", Modifier.weight(1f)) {
                webUrl()?.let { fire(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it))) }
            }
            Box(Modifier.weight(1f)) {
                ActionButton(R.drawable.ic_action_ellipsis, "More", Modifier.fillMaxWidth(), iconSize = 18.dp) {
                    moreState.targetState = true
                }
                // P1 3.3: the More menu pops with the extracted iOS overshoot (PopoverPop) instead of Material's
                // DropdownMenu motion; the Popup stays mounted until the exit finishes (currentState catches up).
                if (moreState.currentState || moreState.targetState) {
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.TopEnd,
                        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                        onDismissRequest = { moreState.targetState = false },
                    ) {
                        com.example.applemaps.ui.components.PopoverPop(moreState) {
                            // white rounded menu card with soft shadow — same rows the DropdownMenu had
                            Column(Modifier.shadow(8.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
                                DropdownMenuItem(text = { Text("Share…") }, onClick = { moreState.targetState = false; share() })
                                place.phone?.let { p -> DropdownMenuItem(text = { Text("Call $p") }, onClick = { moreState.targetState = false; fire(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$p"))) }) }
                                webUrl()?.let { u -> DropdownMenuItem(text = { Text("Open Website") }, onClick = { moreState.targetState = false; fire(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))) }) }
                                if (place.address.isNotEmpty()) DropdownMenuItem(text = { Text("Copy Address") }, onClick = {
                                    moreState.targetState = false
                                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                                        ?.setPrimaryClip(android.content.ClipData.newPlainText("address", place.address))
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    iconBadge: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalAppleColors.current
    val bg = if (primary) c.fillActionBrandAccentDefault else Color(0x0F000000)   // primary=brand fill; rest=rgba(0,0,0,0.06)
    val fg = if (primary) Color.White else Color(0xFF0088FA)                       // traced sc-unified-action-row-item color
    Column(
        modifier.height(52.dp).clip(RoundedCornerShape(10.dp)).background(bg).applePressScale(onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (iconBadge) {
            Box(Modifier.size(iconSize).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Image(
                    painterResource(icon),
                    label,
                    Modifier.size(12.dp),
                    colorFilter = ColorFilter.tint(Color(0xFF007AFF)),
                )
            }
        } else {
            Image(painterResource(icon), label, Modifier.size(iconSize), colorFilter = ColorFilter.tint(fg))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Full place-card body — traced from maps.apple.com: white r16 platters (sc-platter-container, pad 20) on
// the #F2F2F2 card, each detail section under a 20sp/600 header. Photos scroll edge-to-edge. Each section
// renders only when its data is present, so a data source just needs to populate the Place fields.
private val CardGreen = Color(0xFF65C366)   // "Open" — traced sc-opening-state rgb(101,195,102)
private val CardRed = Color(0xFFE8534E)     // "Closed"
private val CardOrange = Color(0xFFFF9500)  // "Closing Soon"

// Ribbon columns are content-sized (the strip scrolls); a min width keeps them evenly spaced like Apple's.
private val RibbonColWidth = Modifier.widthIn(min = 88.dp)

/**
 * True when the place is open AND now is within ~30 min of today's closing time, parsed from the hours
 * string (e.g. "10:30 AM – 8:00 PM" → closes 20:00). Only refines an already-open place; never invents
 * open/closed. Returns false if hours can't be parsed ("Open 24 hours", empty) so we fall back to "Open".
 */
private fun closingSoon(place: Place): Boolean {
    if (place.open != true) return false
    val h = place.hours ?: return false
    val matches = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM)", RegexOption.IGNORE_CASE).findAll(h).toList()
    val m = matches.lastOrNull() ?: return false   // last clock time = today's closing time
    var hour = m.groupValues[1].toIntOrNull() ?: return false
    val min = m.groupValues[2].toIntOrNull() ?: 0
    when (m.groupValues[3].uppercase()) {
        "PM" -> if (hour != 12) hour += 12
        "AM" -> if (hour == 12) hour = 0
    }
    var closeMin = hour * 60 + min
    val cal = java.util.Calendar.getInstance()
    val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    if (closeMin <= nowMin) closeMin += 24 * 60   // closes after midnight
    return (closeMin - nowMin) in 0..30
}

// COST value — four "$" where the first `level` are dark (glyphDefault) and the rest gray (glyphMuted).
@Composable private fun PriceLevel(level: Int) {
    val c = LocalAppleColors.current
    Text(
        buildAnnotatedString {
            repeat(4) { i -> withStyle(SpanStyle(color = if (i < level) c.glyphDefault else c.glyphMuted)) { append("$") } }
        },
        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

@Composable
fun PlaceCardBody(place: Place, onPhotoClick: (Int, Rect?) -> Unit = { _, _ -> }, onDirections: () -> Unit = {}, loading: Boolean = false, distanceMiles: Double? = null) {
    val c = LocalAppleColors.current
    // P1 3.1: spinner → content CROSSFADES (150ms CSS ease-out, the traced .mw-card fade) instead of an instant swap
    androidx.compose.animation.Crossfade(loading,
        animationSpec = androidx.compose.animation.core.tween(150, easing = com.example.applemaps.ui.anim.AppleEasing.EaseOutStd),
        label = "cardLoad") { stillLoading ->
    if (stillLoading) {   // full data still fetching → spinner instead of blanks that fill in one by one
        Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = c.glyphMuted, strokeWidth = 3.dp)
        }
    } else {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        // placeSummaryRibbon — HOURS | RATINGS | ACCEPTS directly below actions, before visual media.
        RibbonStrip(place, distanceMiles)
        // Photos — edge-to-edge horizontal scroller (traced sc-photo-item 173x217 r16, 20dp start inset)
        if (place.photoUrls.isNotEmpty() || place.photoLabels.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            PhotosRow(
                place.photoUrls,
                place.photoLabels,
                providerAttribution = "Google Maps".takeIf { place.ratingSource == "Google" },
                onClick = onPhotoClick,
            )
        }
        // Look Around stays on the map above the sheet; AppleMapsScreen owns its thumbnail/control.
        if (!place.description.isNullOrEmpty()) Section("About") {
            Text(place.description, color = c.glyphDefault, fontSize = 17.sp, lineHeight = 22.sp)
        }
        if ((place.rating != null && (place.ratingCount ?: 0) > 0) || place.reviews.isNotEmpty()) RatingsReviews(place)
        if (place.amenities.isNotEmpty()) Section("Good to Know") {
            place.amenities.forEachIndexed { i, a -> if (i > 0) Spacer(Modifier.height(4.dp)); AmenityRow(a) }
        }
        if (place.alsoHere.isNotEmpty()) Section("Also at This Location") {
            place.alsoHere.forEach { Text(it, color = c.glyphDefault, fontSize = 17.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        }
        // Details — Hours in its own platter, then a Website/Phone(blue links)/Address(+directions) platter
        SectionHeader("Details")
        place.hours?.let { Platter { HoursDetail(place) } }
        Platter {
            val rows = buildList<@Composable () -> Unit> {
                place.website?.let { w -> add { ContactRow("Website", hostOf(w), link = true) } }
                place.phone?.let { p -> add { ContactRow("Phone", p, link = true) } }
                if (place.address.isNotEmpty()) add { AddressRow(place, onDirections) }
            }
            rows.forEachIndexed { i, row -> if (i > 0) HorizontalDivider(color = c.borderMuted, thickness = 1.dp); row() }
        }
        if (place.ratingSource == "Google" || place.dataAttributions.isNotEmpty()) {
            val context = LocalContext.current
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                if (place.ratingSource == "Google") {
                    Text("Google Maps", color = c.glyphMuted, fontSize = 12.sp)
                }
                place.dataAttributions.forEachIndexed { index, attribution ->
                    if (index > 0 || place.ratingSource == "Google") Text(" · ", color = c.glyphMuted, fontSize = 12.sp)
                    Text(
                        attribution.text,
                        color = if (attribution.uri == null) c.glyphMuted else Color(0xFF007AFF),
                        fontSize = 12.sp,
                        modifier = attribution.uri?.let { uri ->
                            Modifier.clickable {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
                                }
                            }
                        } ?: Modifier,
                    )
                }
            }
        }
    }
    }
    }
}

// White r16 platter (traced sc-platter-container: bg #fff, r16, pad 20, 20dp side margin on the card).
@Composable private fun Platter(content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(20.dp), content = content)
    }
}

// Section header — 24sp bold on the gray card (iOS place-card size).
@Composable private fun SectionHeader(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, color = LocalAppleColors.current.glyphDefault, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
    Spacer(Modifier.height(8.dp))
}

// Section = header + one white platter holding the content.
@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionHeader(title)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(20.dp), content = content)
    }
}

// Apple-style cards show some sources as a thumbs-up percentage; Google supplies a 0-5 star score.
private fun ratingPct(r: Double): Int = if (r <= 1.0) (r * 100).roundToInt() else (r / 5.0 * 100).roundToInt()

/** Compact source-truth status shown in the first fact column (for example, “Open · Closes 7:00 PM”). */
internal fun placeHoursStatus(place: Place): String = when {
    place.open == true -> listOfNotNull("Open", place.nextTransition).joinToString(" · ")
    place.open == false -> listOfNotNull("Closed", place.nextTransition).joinToString(" · ")
    else -> place.hours.orEmpty()
}

private fun usesStarRating(place: Place): Boolean =
    place.ratingSource?.lowercase()?.let { "yelp" in it || "booking" in it || "google" in it } == true

/**
 * "Ratings & Reviews" — a horizontally SCROLLABLE row of cards (Apple Maps style): a rating-summary card
 * (source + score + stars + count) followed by one card per review (text + author + stars). Replaces the old
 * stacked Ratings/Reviews platters.
 */
@Composable private fun RatingsReviews(place: Place) {
    val c = LocalAppleColors.current
    val context = LocalContext.current
    SectionHeader("Ratings & Reviews")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        if (place.rating != null && (place.ratingCount ?: 0) > 0) {
            val stars5 = if (place.rating <= 1.0) place.rating * 5 else place.rating
            Column(Modifier.width(150.dp).height(158.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
                Text(place.ratingSource ?: "Ratings", color = c.glyphMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("%.1f".format(stars5), color = c.glyphDefault, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                StarRow(stars5)
                Spacer(Modifier.height(4.dp))
                Text("${place.ratingCount} ratings", color = c.glyphMuted, fontSize = 13.sp)
            }
        }
        place.reviews.forEach { rv ->
            Column(Modifier.width(240.dp).height(158.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
                Text(rv.text, color = c.glyphDefault, fontSize = 15.sp, lineHeight = 20.sp,
                    maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(rv.author, rv.relativePublishTime).joinToString(" · "),
                        color = if (rv.authorUri == null) c.glyphDefault else Color(0xFF007AFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).then(rv.authorUri?.let { uri ->
                            Modifier.clickable {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
                                }
                            }
                        } ?: Modifier),
                    )
                    rv.rating?.let { StarRow(it.toDouble(), 12.sp) }
                }
            }
        }
    }
}

/** A 5-star row with nearest-half rounding (filled ★ when value ≥ position+0.5, else ☆). */
@Composable private fun StarRow(value: Double, size: androidx.compose.ui.unit.TextUnit = 14.sp) {
    Row { repeat(5) { i -> Text(if (value >= i + 0.5) "★" else "☆", color = Color(0xFFF5A623), fontSize = size) } }
}

// Ribbon strip — the 3-column HOURS | N RATINGS | ACCEPTS band on the gray card (uppercase labels, dividers).
@Composable private fun RibbonStrip(place: Place, distanceMiles: Double? = null) {
    val c = LocalAppleColors.current
    val payments = place.amenities.filter { val n = it.lowercase(); "apple pay" in n || "contactless" in n || "cash" in n || "credit" in n }
    val hasHours = place.hours != null || place.open != null
    val hasRating = place.rating != null && (place.ratingCount ?: 0) > 0   // Apple hides the ratings column when 0
    val hasAccepts = payments.isNotEmpty()
    val hasCost = place.priceLevel != null   // COST column ($$$$) only when a price level is known
    if (!hasHours && !hasRating && !hasAccepts && !hasCost && distanceMiles == null) return
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = Color(0xFFCED0D4), thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))
    // Horizontally scrollable strip — Apple's ribbon scrolls when the columns overflow the screen width.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
        var placed = 0
        if (hasHours) {
            val soon = place.nextTransition == null && closingSoon(place)
            val primaryStatus = when {
                soon -> "Closing Soon"
                place.open == true -> "Open"
                place.open == false -> "Closed"
                else -> place.hours.orEmpty()
            }
            val transition = place.nextTransition?.takeUnless { soon }
            // hoursFactColumn — fixed width prevents a long transition from resizing the ribbon. Only the
            // primary state is emphasized; the opening/closing time stays small, muted, and on the same line.
            RibbonCol(Modifier.width(128.dp), "HOURS") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        primaryStatus,
                        color = when { soon -> CardOrange; place.open == true -> CardGreen; place.open == false -> CardRed; else -> c.glyphDefault },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (!transition.isNullOrBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "· $transition",
                            modifier = Modifier.weight(1f),
                            color = c.glyphMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }; placed++
        }
        if (hasRating) {
            if (placed > 0) RibbonDivider()
            val count = place.ratingCountFormatted ?: (place.ratingCount ?: 0).toString()
            val sourceLabel = place.ratingSource?.uppercase()?.takeIf(String::isNotBlank)
            RibbonCol(RibbonColWidth, sourceLabel?.let { "$it ($count)" } ?: "$count RATINGS") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (usesStarRating(place)) {
                        Text("★", color = Color(0xFFFF9500), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp)); Text("%.1f".format(place.rating), color = Color(0xFFFF9500), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Image(painterResource(R.drawable.ic_hand_thumbsup_fill), null, Modifier.size(15.dp), colorFilter = ColorFilter.tint(c.glyphMuted))
                        Spacer(Modifier.width(5.dp)); Text("${ratingPct(place.rating!!)}%", color = c.glyphDefault, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }; placed++
        }
        if (hasAccepts) {
            if (placed > 0) RibbonDivider()
            RibbonCol(RibbonColWidth, "ACCEPTS") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    payments.take(2).forEachIndexed { i, a -> if (i > 0) Spacer(Modifier.width(6.dp)); Image(painterResource(amenityIcon(a)), null, Modifier.height(18.dp), colorFilter = ColorFilter.tint(c.glyphDefault)) }
                }
            }; placed++
        }
        if (hasCost) {   // COST — $$$$ with the first `priceLevel` dark (finally rendered; the helper was unused)
            if (placed > 0) RibbonDivider()
            RibbonCol(RibbonColWidth, "COST") { PriceLevel(place.priceLevel!!) }; placed++
        }
        if (distanceMiles != null) {   // DISTANCE — miles from the user, with Apple's route-squiggle glyph
            if (placed > 0) RibbonDivider()
            RibbonCol(RibbonColWidth, "DISTANCE") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_location_bottomleft_forward_to_point_topright_scurvepath_dashed), null, Modifier.size(15.dp), colorFilter = ColorFilter.tint(c.glyphMuted))
                    Spacer(Modifier.width(5.dp))
                    Text(if (distanceMiles < 0.1) "${(distanceMiles * 5280).roundToInt()} ft" else "%.1f mi".format(distanceMiles),
                        color = c.glyphDefault, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
    HorizontalDivider(color = Color(0xFFCED0D4), thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable private fun RowScope.RibbonCol(modifier: Modifier, label: String, value: @Composable () -> Unit) {
    val c = LocalAppleColors.current
    Column(modifier) {
        Text(label, color = c.glyphMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(3.dp)); value()
    }
}

@Composable private fun RowScope.RibbonDivider() {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.width(1.dp).height(38.dp).background(Color(0xFFCED0D4)).align(Alignment.CenterVertically))
    Spacer(Modifier.width(16.dp))
}

@Composable private fun RatingRow(title: String, rating: Double, count: Int?) {
    val c = LocalAppleColors.current
    Column {
        Text(title, color = c.glyphDefault, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_hand_thumbsup_fill), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(c.glyphMuted))
            Spacer(Modifier.width(6.dp))
            Text("${ratingPct(rating)}%", color = c.glyphDefault, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            count?.let { Text(" · $it ratings", color = c.glyphMuted, fontSize = 17.sp) }
        }
    }
}

@Composable private fun PhotosRow(
    urls: List<String>,
    labels: List<String>,
    providerAttribution: String? = null,
    onClick: (Int, Rect?) -> Unit = { _, _ -> },
) {
    val n = (if (urls.isNotEmpty()) urls.size else labels.size).coerceAtMost(10)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until n).forEach { i ->
            val label = labels.getOrNull(i)
            var bounds by remember(i) { mutableStateOf<Rect?>(null) }
            Box(Modifier.size(width = 173.dp, height = 217.dp)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFFF2F2F6)).clickable { onClick(i, bounds) }) {
                urls.getOrNull(i)?.let { url ->
                    // crossfade → the photo FADES IN as it loads (over the gray placeholder)
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current).data(url).crossfade(300)
                            .listener(
                                onSuccess = { _, _ -> DiagLog.log("APPLEPHOTO", "event=imageReady", "index=$i") },
                                onError = { _, result -> DiagLog.log("APPLEPHOTO", "event=imageFailed", "index=$i", "error=${result.throwable.javaClass.simpleName}") },
                            ).build(),
                        contentDescription = label,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
                // bottom gradient + white title (traced sc-photo-gradient-overlay 80dp, sc-photo-item-title 17px/600 white)
                if (label != null || providerAttribution != null) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        label?.let { Text(it, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                        providerAttribution?.let { Text(it, color = Color.White, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

// Map an amenity label to its real glyph (traced from the "Good to Know" rows).
private fun amenityIcon(name: String): Int {
    val n = name.lowercase()
    return when {
        "apple pay" in n -> R.drawable.ic_applepay
        "contactless" in n -> R.drawable.ic_nfccontactlesspayment
        "wheelchair" in n || "accessible" in n -> R.drawable.ic_accessiblewheelchair
        "curbside" in n -> R.drawable.ic_car_fill
        "cash" in n -> R.drawable.ic_cashonly
        "credit" in n || "card" in n -> R.drawable.ic_creditcard_fill
        "wi-fi" in n || "wifi" in n -> R.drawable.ic_wifi
        else -> R.drawable.ic_checkmark
    }
}

@Composable private fun AmenityRow(name: String) {
    val c = LocalAppleColors.current
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(amenityIcon(name)), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(c.glyphDefault))
        Spacer(Modifier.width(14.dp))
        Text(name, color = c.glyphDefault, fontSize = 17.sp)
    }
}

private fun hostOf(url: String) = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/")

// "10:30 AM – 8:00 PM" with AM/PM rendered smaller, like the iOS card.
private fun styleAmPm(s: String) = buildAnnotatedString {
    val re = Regex("\\b(AM|PM)\\b")
    var last = 0
    for (m in re.findAll(s)) {
        append(s.substring(last, m.range.first)); withStyle(SpanStyle(fontSize = 13.sp)) { append(m.value) }; last = m.range.last + 1
    }
    append(s.substring(last))
}

@Composable private fun HoursDetail(place: Place) {
    val c = LocalAppleColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text("Hours", color = c.glyphMuted, fontSize = 14.sp)
            Spacer(Modifier.height(3.dp))
            place.hours?.let { Text(styleAmPm(it), color = c.glyphDefault, fontSize = 17.sp) }
            place.open?.let { Text(if (it) "Open" else "Closed", color = if (it) CardGreen else Color(0xFFE8534E), fontSize = 17.sp) }
        }
        Image(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(c.glyphMuted))
    }
}

@Composable private fun ContactRow(label: String, value: String, link: Boolean) {
    val c = LocalAppleColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, color = c.glyphMuted, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = if (link) Color(0xFF007AFF) else c.glyphDefault, fontSize = 17.sp)
    }
}

@Composable private fun AddressRow(place: Place, onDirections: () -> Unit) {
    val c = LocalAppleColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Address", color = c.glyphMuted, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(place.address, color = c.glyphDefault, fontSize = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFE5E5EA)).clickable { onDirections() }, contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_arrow_triangle_turn_up_right_semibold), "Directions", Modifier.size(14.dp), colorFilter = ColorFilter.tint(Color(0xFF007AFF)))
        }
    }
}

@Composable
private fun CircleIconButton(icon: Int, cd: String, iconSize: androidx.compose.ui.unit.Dp = 14.dp, onClick: () -> Unit) {
    val c = LocalAppleColors.current
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Color(0x5CC7C7C7)).applePressScale(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), cd, Modifier.size(iconSize), colorFilter = ColorFilter.tint(c.glyphMuted))
    }
}

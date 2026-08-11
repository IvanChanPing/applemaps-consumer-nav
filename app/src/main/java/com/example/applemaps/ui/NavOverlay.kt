package com.example.applemaps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.applemaps.R
import com.example.applemaps.ui.anim.AppleEasing
import uniffi.ferrostar.LaneInfo
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Turn-by-turn navigation overlay — built from scratch to match the Google Immersive Navigation look
 * (the reference screenshot): a dark-teal instruction banner at the top (maneuver icon + distance + the
 * street to turn onto), a floating right-side FAB column (recenter · mute · report), and a floating white
 * bottom bar (ETA · remaining distance · arrival time + a red Exit button). It fully REPLACES the normal
 * bottom sheet + map chrome while navigating (driven from FerrostarCore.state via NavEngine accessors).
 *
 * All values are live from the trip: [instruction] = primary maneuver text, [distanceToNext] = metres to the
 * next maneuver, [durationRemaining]/[distanceRemaining] = whole-trip remaining. No posted-speed-limit sign
 * (keyless OSM has no reliable speed-limit data — omitted rather than faked).
 */
private val TEAL = Color(0xFF1C3D3A)          // Google immersive banner green-teal
private val TEAL_LANE = Color(0xFF264D49)     // lane strip band under the banner
private val EXIT_RED = Color(0xFFEA4335)

@Composable
fun NavOverlay(
    instruction: String?,
    lanes: List<LaneInfo> = emptyList(),
    distanceToNext: Double?,
    durationRemaining: Double?,
    distanceRemaining: Double?,
    speedMps: Double? = null,
    muted: Boolean,
    onMute: () -> Unit,
    onRecenter: () -> Unit,
    onExit: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // ── TOP: FLOATING instruction banner + lane strip (Google-style rounded card w/ shadow + margins) ──
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(TEAL).padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // P1 6.1: banner content transitions per maneuver — old slides/fades up-out, new slides in.
                // Keyed on the instruction; the live distance text recomposes inside without re-triggering.
                AnimatedContent(targetState = instruction, transitionSpec = {
                    (slideInVertically(tween(300, easing = AppleEasing.Standard)) { it / 2 } + fadeIn(tween(300))) togetherWith
                        (slideOutVertically(tween(300, easing = AppleEasing.EaseIn)) { -it / 2 } + fadeOut(tween(200)))
                }, label = "maneuver") { instr ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(maneuverIcon(instr)), null, Modifier.size(40.dp),
                        colorFilter = ColorFilter.tint(Color.White))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(distToNext(distanceToNext), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(streetOf(instr), color = Color(0xFFCFE0DE), fontSize = 17.sp, maxLines = 1)
                    }
                }
                }
            }
            // Google/Apple-style lane strip under the banner: per-lane arrows, active lanes bright, others dimmed
            // P1 6.2: the teal lane band expands/collapses under the banner instead of popping; lanesShown caches
            // the last non-empty set so the shrink-out still has content to draw.
            val lanesShown = remember { mutableStateOf(lanes) }
            if (lanes.isNotEmpty()) lanesShown.value = lanes
            AnimatedVisibility(visible = lanes.isNotEmpty(),
                enter = expandVertically(tween(250, easing = AppleEasing.ExpoOut)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200, easing = AppleEasing.EaseIn)) + fadeOut(tween(150))) {
                LaneStrip(lanesShown.value)
            }
        }

        // ── RIGHT FAB column ───────────────────────────────────────────────────
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NavFab(R.drawable.ic_centerlocation, onRecenter)
            NavFab(R.drawable.ic_speaker_wave_2, onMute, dim = muted)
            NavFab(R.drawable.ic_exclamationmark_triangle_fill, {}, tint = Color(0xFFF2A73B))
        }

        // ── CURRENT SPEED (bottom-left), real GPS speed like Google's speed pill ─
        // P1 6.3: the white speed pill fades/pops in with the first GPS fix (overshoot bezier) instead of appearing
        // in one frame; lastMps caches the value so the fade-out frame still shows a number.
        val lastMps = remember { mutableStateOf(0.0) }
        speedMps?.let { lastMps.value = it }
        AnimatedVisibility(visible = speedMps != null, modifier = Modifier.align(Alignment.BottomStart),
            enter = fadeIn(tween(200, easing = AppleEasing.Standard)) + scaleIn(tween(200, easing = AppleEasing.Overshoot), initialScale = 0.8f),
            exit = fadeOut(tween(150, easing = AppleEasing.EaseIn))) {
            val mph = (lastMps.value * 2.23694).roundToInt().coerceAtLeast(0)
            Column(
                Modifier.navigationBarsPadding().padding(start = 16.dp, bottom = 96.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$mph", color = Color(0xFF1C1C1E), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("mph", color = Color(0xFF6E6E73), fontSize = 11.sp)
            }
        }

        // ── BOTTOM floating bar: ETA · distance · arrival  +  Exit ─────────────
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
                .fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(
                targetState = minLabel(durationRemaining) to "${distRemaining(distanceRemaining)} · ${arrivalOf(durationRemaining)}",
                animationSpec = tween(150, easing = AppleEasing.Standard), label = "navEta",
                modifier = Modifier.weight(1f),
            ) { (eta, detail) -> Column { Text(eta, color = Color(0xFF137333), fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(detail, color = Color(0xFF6E6E73), fontSize = 15.sp) } }
            Box(
                Modifier.clip(RoundedCornerShape(24.dp)).background(EXIT_RED.copy(alpha = 0.14f))
                    .applePressScale(onExit).padding(horizontal = 22.dp, vertical = 12.dp),
            ) { Text("Exit", color = EXIT_RED, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun NavFab(icon: Int, onClick: () -> Unit, tint: Color = Color(0xFF1C1C1E), dim: Boolean = false) {
    val iconTint by animateColorAsState(if (dim) tint.copy(alpha = 0.35f) else tint,
        tween(150, easing = AppleEasing.Standard), label = "navFabTint")
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color.White).applePressScale(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), null, Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(iconTint))
    }
}

/** Lane-guidance strip (the teal band under the banner): one arrow per lane, active lanes white, others dimmed. */
@Composable
private fun LaneStrip(lanes: List<LaneInfo>) {
    Row(
        Modifier.fillMaxWidth().background(TEAL_LANE).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lanes.forEach { lane ->
            // P1 6.2: active/dim lane tint crossfades (150ms) instead of snapping between maneuvers
            val laneTint by animateColorAsState(if (lane.active) Color.White else Color(0x59FFFFFF),
                tween(150, easing = AppleEasing.Standard), label = "laneTint")
            Image(
                painterResource(laneArrowIcon(lane)), null,
                Modifier.padding(horizontal = 5.dp).size(26.dp),
                colorFilter = ColorFilter.tint(laneTint),
            )
        }
    }
}

/** Arrow for one lane: the active direction when the lane serves this maneuver, else its first direction. */
private fun laneArrowIcon(lane: LaneInfo): Int {
    val dir = (lane.activeDirection?.takeIf { lane.active && it.isNotBlank() } ?: lane.directions.firstOrNull() ?: "").lowercase()
    return when {
        "uturn" in dir || "u-turn" in dir -> R.drawable.ic_arrow_left_and_right
        "slight left" in dir -> R.drawable.ic_arrow_up_left
        "slight right" in dir -> R.drawable.ic_arrow_up_right
        "left" in dir -> R.drawable.ic_arrow_up_left
        "right" in dir -> R.drawable.ic_arrow_triangle_turn_up_right
        else -> R.drawable.ic_arrow_up
    }
}

/** Pick a maneuver arrow from instruction prefixes only; road names later in the text must not affect icons. */
private fun maneuverIcon(instruction: String?): Int {
    val t = (instruction ?: "").lowercase()
    return when {
        t.startsWith("arrive") || t.contains("destination") -> R.drawable.ic_location_fill
        t.startsWith("enter the roundabout") || t.startsWith("enter the rotary") -> R.drawable.ic_arrow_clockwise
        t.startsWith("make a u-turn") || t.startsWith("u-turn") || t.startsWith("turn around") -> R.drawable.ic_arrow_left_and_right
        t.startsWith("turn slight left") || t.startsWith("keep slight left") || t.startsWith("merge slight left") -> R.drawable.ic_arrow_up_left
        t.startsWith("turn slight right") || t.startsWith("keep slight right") || t.startsWith("merge slight right") -> R.drawable.ic_arrow_up_right
        t.startsWith("turn left") || t.startsWith("keep left") || t.startsWith("merge left") -> R.drawable.ic_arrow_up_left
        t.startsWith("turn right") || t.startsWith("keep right") || t.startsWith("merge right") -> R.drawable.ic_arrow_triangle_turn_up_right
        else -> R.drawable.ic_arrow_up
    }
}

/** The road you're turning ONTO (strip the leading "Turn left onto " etc.), or the whole instruction. */
private fun streetOf(instruction: String?): String {
    val t = instruction ?: return "Continue"
    val onto = t.indexOf(" onto ")
    return if (onto >= 0) t.substring(onto + 6) else t
}

private fun distToNext(meters: Double?): String {
    val m = meters ?: return ""
    val mi = m / 1609.34
    return if (mi < 0.1) "${(m * 3.28084).roundToInt()} ft" else "%.1f mi".format(mi)
}

private fun distRemaining(meters: Double?): String {
    val m = meters ?: return ""
    val mi = m / 1609.34
    return if (mi < 0.1) "${(m * 3.28084).roundToInt()} ft" else "%.1f mi".format(mi)
}

private fun minLabel(seconds: Double?): String {
    val s = seconds ?: return "—"
    val m = (s / 60.0).roundToInt().coerceAtLeast(1)
    return if (m >= 60) "${m / 60} hr ${m % 60} min" else "$m min"
}

private fun arrivalOf(seconds: Double?): String {
    val s = seconds ?: return ""
    val c = Calendar.getInstance(); c.add(Calendar.SECOND, s.toInt())
    var h = c.get(Calendar.HOUR); if (h == 0) h = 12
    val ampm = if (c.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(h, c.get(Calendar.MINUTE), ampm)
}

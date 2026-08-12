package com.example.applemaps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.applemaps.R
import com.example.applemaps.map.Place
import com.example.applemaps.map.Route
import com.example.applemaps.map.RouteStep
import com.example.applemaps.ui.anim.AppleEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Directions preview sheet, shown after tapping Directions on a place. All planner + route-card values traced
 * from maps.apple.com's directions sheet (2026-07-14): 3 mode pills (r12, selected solid blue #007AFF/white
 * icon, else gray track), white From/To card with reorder handles + dotted connector, Now/Avoid pills, and the
 * route-option cards — the SELECTED route is a full blue card, alternatives are white, each "N min" (large
 * bold) + "H:MM PM ETA · X.X mi" + descriptor + info (i). The green GO/navigate button is the iOS-screenshot
 * addition (the web has no navigate action), placed on the selected route.
 */

private data class Mode(val key: String, val icon: Int, val api: String)
private val MODES = listOf(
    Mode("Drive", R.drawable.ic_mode_car_fill, "DRIVE"),
    Mode("Walk", R.drawable.ic_mode_walk, "WALK"),
    Mode("Cycle", R.drawable.ic_mode_bicycle, "BICYCLE"),
)
fun modeApi(key: String): String = MODES.firstOrNull { it.key == key }?.api ?: "DRIVE"
fun osrmProfile(key: String): String = when (key) { "Walk" -> "foot"; "Cycle" -> "bike"; else -> "car" }   // FOSSGIS routed-* profile

private val TRACK = Color(0x1F767680)     // iOS gray track (rgba(118,118,128,0.12))
private val GRAY = Color(0xFF8E8E93)
private val LABEL = Color(0xFF1C1C1E)
private val SUBGRAY = Color(0xFF6E6E73)
private val GO_GREEN = Color(0xFF34C759)
private val ACCENT = Color(0xFF007AFF)

/** Header: big "Directions" title + iOS gray-circle Share and Close actions. */
@Composable
fun DirectionsHeader(onShare: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Directions", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = LABEL, modifier = Modifier.weight(1f))
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x5CC7C7C7)).clickable(onClick = onShare),
            contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_square_and_arrow_up), "Share directions", Modifier.size(15.dp), colorFilter = ColorFilter.tint(Color(0xFF7C7C80)))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x5CC7C7C7)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_xmark), null, Modifier.size(13.dp), colorFilter = ColorFilter.tint(Color(0xFF7C7C80)))
        }
    }
}

/** Body: mode toggle · From/To card · Now/Avoid · the route-option cards (selected = blue + GO). */
@Composable
fun DirectionsBody(
    place: Place, routes: List<Route>, selected: Int, mode: String,
    onMode: (String) -> Unit, onSelect: (Int) -> Unit, onGo: () -> Unit,
    stopCount: Int = 0, stopLabels: List<String> = emptyList(), onAddStop: () -> Unit = {}, onRemoveStop: (Int) -> Unit = {}, onEditStop: (Int) -> Unit = {},
    onReorderStop: (Int, Int) -> Unit = { _, _ -> },
    departOffsetMin: Int = 0, onDepart: (Int) -> Unit = {},
    avoidTolls: Boolean = false, avoidHighways: Boolean = false, onAvoid: (Boolean, Boolean) -> Unit = { _, _ -> },
    onInfo: (Int) -> Unit = {},
    routeError: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        ModeToggle(mode, onMode)
        Spacer(Modifier.height(14.dp))
        FromToCard(place, stopCount, stopLabels, onAddStop, onRemoveStop, onEditStop, onReorderStop, showAddStop = mode == "Drive")
        Spacer(Modifier.height(14.dp))
        Row {   // Apple shows the "Now" departure-time pill ONLY for driving; walking/cycling get Avoid only
            if (mode == "Drive") { NowPill(departOffsetMin, onDepart); Spacer(Modifier.width(10.dp)) }
            AvoidPill(avoidTolls, avoidHighways, onAvoid)
        }
        Spacer(Modifier.height(16.dp))
        // P1 2.6: new route options crossfade in on mode change / reroute instead of flashing in one frame
        Crossfade(routes to routeError, animationSpec = tween(150, easing = AppleEasing.Standard), label = "routeList") { (rs, error) ->
            when {
                error != null -> Text(error, fontSize = 15.sp, color = SUBGRAY, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp))
                rs.isEmpty() -> Text("Finding routes…", fontSize = 15.sp, color = SUBGRAY, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp))
                else -> Column {
                    rs.forEachIndexed { i, r ->
                        RouteCard(r, i == selected, i, departOffsetMin, mode, onGo = onGo, onSelect = { onSelect(i) }, onInfo = { onInfo(i) })
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/** "Now" pill → departure-time menu. Keyless routing has no live traffic, so this shifts the DEPART base
 *  (Leave Now / Depart in N) which moves the shown ETA accordingly — a real, honest effect on the label. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPill(offsetMin: Int, onDepart: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val dialogVisibility = remember { MutableTransitionState(false) }
    dialogVisibility.targetState = open
    val label = if (offsetMin == 0) "Now" else {
        val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, offsetMin) }
        "Depart " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(c.time)
    }
    MenuPill(label) { open = true }
    if (dialogVisibility.currentState || dialogVisibility.targetState) {
        val now = java.util.Calendar.getInstance()
        val state = rememberTimePickerState(now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), false)
        Dialog(onDismissRequest = { open = false }) {
            AnimatedVisibility(visibleState = dialogVisibility,
                enter = scaleIn(tween(200, easing = AppleEasing.Overshoot), initialScale = 0.92f),
                exit = scaleOut(tween(150, easing = AppleEasing.Standard), targetScale = 0.92f)) {
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Depart at", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LABEL)
                Spacer(Modifier.height(14.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { onDepart(0); open = false }) { Text("Leave Now") }
                    TextButton(onClick = {
                        val picked = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, state.hour)
                            set(java.util.Calendar.MINUTE, state.minute); set(java.util.Calendar.SECOND, 0)
                        }
                        if (picked.timeInMillis <= System.currentTimeMillis()) picked.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        onDepart(((picked.timeInMillis - System.currentTimeMillis()) / 60000L).toInt().coerceAtLeast(0))
                        open = false
                    }) { Text("Set", fontWeight = FontWeight.SemiBold) }
                }
            }
            }
        }
    }
}

/** "Avoid" pill → the modal (traced `avoid-modal`: title + rows of "Avoid X" label + iOS switch + Done).
 *  The switches genuinely re-route via Valhalla (use_tolls/use_highways). */
@Composable
private fun AvoidPill(tolls: Boolean, highways: Boolean, onAvoid: (Boolean, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val dialogVisibility = remember { MutableTransitionState(false) }
    dialogVisibility.targetState = open
    val label = when { tolls && highways -> "Avoid: Tolls, Hwys"; tolls -> "Avoid: Tolls"; highways -> "Avoid: Hwys"; else -> "Avoid" }
    MenuPill(label) { open = true }
    if (dialogVisibility.currentState || dialogVisibility.targetState) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { open = false }) {
            AnimatedVisibility(visibleState = dialogVisibility,
                enter = scaleIn(tween(200, easing = AppleEasing.Overshoot), initialScale = 0.92f),
                exit = scaleOut(tween(150, easing = AppleEasing.Standard), targetScale = 0.92f)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF2F2F2)).padding(20.dp)) {
                Text("Avoid", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LABEL)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)) {
                    AvoidRow("Avoid Tolls", tolls) { onAvoid(it, highways) }
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(Color(0x1A3C3C43)))
                    AvoidRow("Avoid Highways", highways) { onAvoid(tolls, it) }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Done", color = ACCENT, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = false }.padding(horizontal = 20.dp, vertical = 6.dp))
                }
            }
            }
        }
    }
}

/** One "Avoid X" row in the modal: label + an iOS-style toggle switch. */
@Composable
private fun AvoidRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 17.sp, color = LABEL, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ModeToggle(selected: String, onMode: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp)) {
        MODES.forEachIndexed { i, m ->
            if (i > 0) Spacer(Modifier.width(10.dp))
            val sel = m.key == selected
            // P1 2.1: selection crossfades the pill fill + icon tint (iOS segmented feel) instead of snapping
            val pillBg by animateColorAsState(if (sel) ACCENT else TRACK, tween(200, easing = AppleEasing.Standard), label = "modeBg")
            val pillFg by animateColorAsState(if (sel) Color.White else GRAY, tween(200, easing = AppleEasing.Standard), label = "modeFg")
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(pillBg).clickable { onMode(m.key) },
                contentAlignment = Alignment.Center,
            ) {
                val iconModifier = when (m.key) {
                    "Drive" -> Modifier.width(22.dp).height(17.dp)
                    "Walk" -> Modifier.size(22.dp)
                    else -> Modifier.width(28.dp).height(18.dp)
                }
                Image(painterResource(m.icon), m.key, iconModifier,
                    colorFilter = ColorFilter.tint(pillFg))
            }
        }
    }
}

/** Keeps selected stop names beside their route coordinates so the planner never replaces them with generic labels. */
@Composable
private fun FromToCard(place: Place, stopCount: Int, stopLabels: List<String>, onAddStop: () -> Unit, onRemoveStop: (Int) -> Unit = {}, onEditStop: (Int) -> Unit = {}, onReorderStop: (Int, Int) -> Unit = { _, _ -> }, showAddStop: Boolean = true) {
    val strideP = with(LocalDensity.current) { 49.dp.toPx() }   // one row's height → how many rows a drag has crossed
    var dragIdx by remember { mutableStateOf<Int?>(null) }
    var dragDy by remember { mutableStateOf(0f) }
    // P1 2.5: the white From/To card grows/shrinks smoothly as stops are added/removed (no height jump)
    Box(Modifier.fillMaxWidth().animateContentSize(tween(250, easing = AppleEasing.ExpoOut)).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        val density = LocalDensity.current
        Canvas(Modifier.matchParentSize()) {
            val x = with(density) { 23.dp.toPx() }
            val firstCenter = with(density) { 24.5.dp.toPx() }
            drawLine(
                color = Color(0x407C7C80),
                start = Offset(x, firstCenter),
                end = Offset(x, firstCenter + stopCount * strideP + strideP),
                strokeWidth = with(density) { 2.dp.toPx() },
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(with(density) { 2.dp.toPx() }, with(density) { 4.dp.toPx() }),
                ),
            )
        }
        Column(Modifier.fillMaxWidth()) {
        FieldRow(R.drawable.ic_location_fill, ACCENT, "My Location", handle = true)
        Divider()
        repeat(stopCount) { i ->
            // Drag the ≡ handle VERTICALLY to reorder; commit on release. Coexists with the horizontal swipe-remove.
            val handleMod = Modifier.draggable(
                state = rememberDraggableState { d -> dragDy += d },
                orientation = Orientation.Vertical,
                onDragStarted = { dragIdx = i; dragDy = 0f },
                onDragStopped = {
                    val target = (i + (dragDy / strideP).roundToInt()).coerceIn(0, (stopCount - 1).coerceAtLeast(0))
                    // P1 2.4: glide the dragged row into its destination slot before committing (no teleport)
                    animate(dragDy, (target - i) * strideP, animationSpec = tween(250, easing = AppleEasing.ExpoOut)) { v, _ -> dragDy = v }
                    dragIdx = null; dragDy = 0f
                    if (target != i) onReorderStop(i, target)
                })
            // P1 2.4: rows the drag has crossed slide out of the way (±1 row height) instead of never moving
            val di = dragIdx
            val crossedTarget = if (di == null) null else (di + (dragDy / strideP).roundToInt()).coerceIn(0, (stopCount - 1).coerceAtLeast(0))
            val shiftTarget = when {
                di == null || crossedTarget == null || di == i -> 0f
                i in (di + 1)..crossedTarget -> -strideP
                i in crossedTarget until di -> strideP
                else -> 0f
            }
            val shift by animateFloatAsState(shiftTarget, tween(200, easing = AppleEasing.Standard), label = "stopShift")
            val rowMod = if (di == i) Modifier.offset { IntOffset(0, dragDy.roundToInt()) } else Modifier.offset { IntOffset(0, shift.roundToInt()) }
            // P1 2.5: a newly added stop row fades + expands in (removal height change is covered by animateContentSize)
            AnimatedVisibility(
                visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                enter = fadeIn(tween(200, easing = AppleEasing.Standard)) + expandVertically(tween(250, easing = AppleEasing.ExpoOut)),
            ) {
                Column { StopRow(stopLabels.getOrElse(i) { "Stop ${i + 1}" }, { onRemoveStop(i) }, { onEditStop(i) }, handleMod, rowMod); Divider() }
            }
        }
        FieldRow(R.drawable.ic_location_fill, Color(0xFFFF3B30), place.name, handle = true)
        if (showAddStop) {   // Apple offers Add Stop only for driving
            Divider()
            FieldRow(R.drawable.ic_plus_circle_fill, ACCENT, "Add Stop", handle = false, accentText = true, onClick = onAddStop)
        }
        }
    }
}

/** A stop row you SWIPE sideways (left) to reveal a red minus button, which you tap to remove the stop. */
@Composable
private fun StopRow(label: String, onRemove: () -> Unit, onEdit: () -> Unit = {}, handleMod: Modifier = Modifier, rowMod: Modifier = Modifier) {
    val revealPx = with(LocalDensity.current) { 56.dp.toPx() }
    // P1 2.3: Animatable so the release GLIDES to the revealed/closed rest position (interruptible), no snap
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    Box(Modifier.fillMaxWidth().height(49.dp).then(rowMod)) {
        // red minus button revealed on the right as the row slides left
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.padding(end = 16.dp).size(28.dp).clip(CircleShape).background(Color(0xFFFF3B30)).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center) {
                Box(Modifier.width(12.dp).height(2.5.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
            }
        }
        Box(
            Modifier.matchParentSize().offset { IntOffset(offset.value.roundToInt(), 0) }.background(Color.White)
                .draggable(
                    state = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(-revealPx, 0f)) } },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { offset.animateTo(if (offset.value < -revealPx / 2) -revealPx else 0f, tween(300, easing = AppleEasing.ExpoOut)) }),
        ) { FieldRow(R.drawable.ic_location_fill, GRAY, label, handle = true, onClick = onEdit, handleModifier = handleMod) }
    }
}

@Composable
private fun FieldRow(icon: Int, tint: Color, text: String, handle: Boolean, accentText: Boolean = false, onClick: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, handleModifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth().height(49.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onRemove != null) {   // red "−" delete (Apple edit-mode style) to remove an added stop
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFFF3B30)).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Box(Modifier.width(10.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
            }
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (accentText) Color.Transparent else tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center) {
            Image(painterResource(icon), null, Modifier.size(if (accentText) 22.dp else 15.dp), colorFilter = ColorFilter.tint(tint))
        }
        Text(text, fontSize = 16.sp, color = if (accentText) ACCENT else LABEL,
            modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
        if (handle) Box(Modifier.then(handleModifier).padding(4.dp), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_line_3_horizontal), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(GRAY))
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(Color(0x1A3C3C43)))
}

/** Tappable filter pill (Now / Avoid) that opens a dropdown menu. */
@Composable
private fun MenuPill(text: String, onClick: () -> Unit) {
    Row(Modifier.animateContentSize(tween(200, easing = AppleEasing.Standard)).clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0x1F3C3C43), RoundedCornerShape(16.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 14.sp, color = LABEL, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Image(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(11.dp), colorFilter = ColorFilter.tint(GRAY))
    }
}

/**
 * A route option. Selected = solid blue card (web look); alternatives = white, tappable to select. "N min"
 * large bold + "H:MM PM ETA · X.X mi" + descriptor. The selected route shows the green GO/navigate button
 * (iOS addition); alternatives show the (i) info button.
 */
@Composable
private fun RouteCard(route: Route, selected: Boolean, index: Int, departOffsetMin: Int, mode: String, onGo: () -> Unit, onSelect: () -> Unit, onInfo: () -> Unit) {
    // P1 2.2: selection crossfades the card colors (250ms) instead of swapping blue/white in one frame
    val bg by animateColorAsState(if (selected) Color(0xFF0071E3) else Color.White, tween(250, easing = AppleEasing.Standard), label = "routeBg")   // traced --apl-color-fill-action-brand-accent-selected
    val primary by animateColorAsState(if (selected) Color.White else LABEL, tween(250, easing = AppleEasing.Standard), label = "routePrimary")
    val sub by animateColorAsState(if (selected) Color(0xE6FFFFFF) else SUBGRAY, tween(250, easing = AppleEasing.Standard), label = "routeSub")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg)
            .clickable(enabled = !selected, onClick = onSelect).padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(etaLabel(route.durationSeconds), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = primary)
                Text("${arrivalLabel(route.durationSeconds, departOffsetMin)} ETA · ${distLabel(route.distanceMeters)}",
                    fontSize = 16.sp, color = sub, modifier = Modifier.padding(top = 3.dp))
                // Per-mode extra lines (captured traces): drive = Fastest/Fewer turns; walk/cycle = "N ft climb";
                // cycle also = bike-lane advisory.
                if (mode == "Drive") {
                    Text(if (index == 0) "Fastest" else "Fewer turns", fontSize = 16.sp, color = sub, modifier = Modifier.padding(top = 1.dp))
                } else {
                    route.climbFeet?.let { Text("$it ft climb", fontSize = 16.sp, color = sub, modifier = Modifier.padding(top = 1.dp)) }
                    if (mode == "Cycle") Text("Protected lanes and bike lanes", fontSize = 16.sp, color = sub, modifier = Modifier.padding(top = 1.dp))
                }
            }
            // (i) route-details button — shown on every card (Apple shows details for the tapped route)
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (selected) Color(0x33FFFFFF) else GRAY.copy(alpha = 0.5f)).clickable(onClick = onInfo),
                contentAlignment = Alignment.Center) {
                Text("i", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            // P1 2.2: GO pops in with the extracted overshoot (y>1 bezier) + fade instead of blinking into existence
            AnimatedVisibility(visible = selected,
                enter = fadeIn(tween(200, easing = AppleEasing.Standard)) + scaleIn(tween(200, easing = AppleEasing.Overshoot), initialScale = 0.85f),
                exit = fadeOut(tween(150, easing = AppleEasing.EaseIn))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(width = 62.dp, height = 50.dp).clip(RoundedCornerShape(12.dp)).background(GO_GREEN).clickable(onClick = onGo),
                        contentAlignment = Alignment.Center) {
                        Text("GO", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        // Inline elevation profile (walk/cycle), full-width below the summary — matches `.mw-elevation-chart`.
        if (mode != "Drive" && route.elevation.size >= 2) {
            Spacer(Modifier.height(10.dp))
            ElevationChart(route.elevation, selected, Modifier.fillMaxWidth().height(40.dp))
        }
    }
}

/** Inline elevation profile: a filled line chart of the sampled elevation (white on the selected blue card,
 *  gray on white cards) — the port of Apple's `.mw-elevation-chart`. */
@Composable
private fun ElevationChart(elev: List<Double>, selected: Boolean, modifier: Modifier) {
    val line = if (selected) Color.White else Color(0xFF8E8E93)
    val fill = if (selected) Color(0x33FFFFFF) else Color(0x148E8E93)
    androidx.compose.foundation.Canvas(modifier) {
        val mn = elev.min(); val mx = elev.max(); val range = (mx - mn).coerceAtLeast(1.0)
        val w = size.width; val h = size.height
        val pts = elev.mapIndexed { i, e -> androidx.compose.ui.geometry.Offset(i.toFloat() / (elev.size - 1) * w, (h - (e - mn) / range * h).toFloat()) }
        val stroke = androidx.compose.ui.graphics.Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        val area = androidx.compose.ui.graphics.Path().apply { addPath(stroke); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(area, fill)
        drawPath(stroke, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
}

/**
 * Route (i) details page — the maneuver-by-maneuver breakdown of a route (Apple Maps' route details).
 * Summary header (time · ETA · distance) + a scrolling list of each step's instruction, road, and length.
 */
@Composable
fun RouteInfoSheet(route: Route, index: Int, departOffsetMin: Int, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(etaLabel(route.durationSeconds), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = LABEL)
                Text("${arrivalLabel(route.durationSeconds, departOffsetMin)} ETA · ${distLabel(route.distanceMeters)} · ${if (index == 0) "Fastest" else "Alternative"}",
                    fontSize = 15.sp, color = SUBGRAY, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x5CC7C7C7)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_xmark), null, Modifier.size(13.dp), colorFilter = ColorFilter.tint(Color(0xFF7C7C80)))
            }
        }
        Spacer(Modifier.height(8.dp))
        // Elevation summary (walk/bike) — ascent/descent icons plus Total Elevation and full-width profile chart.
        if (route.elevation.size >= 2 && route.climbFeet != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_arrow_up_right), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(LABEL))
                    Spacer(Modifier.width(4.dp))
                    Text("${route.climbFeet} ft", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LABEL)
                    Spacer(Modifier.width(18.dp))
                    Image(painterResource(R.drawable.ic_arrow_down_right), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(LABEL))
                    Spacer(Modifier.width(4.dp))
                    Text("${route.descentFeet ?: 0} ft", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LABEL)
                }
                Text("Total Elevation", fontSize = 14.sp, color = SUBGRAY, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))
                ElevationChart(route.elevation, selected = false, Modifier.fillMaxWidth().height(80.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (route.steps.isEmpty()) {
                Text("Turn-by-turn details are not available for this route.", fontSize = 15.sp, color = SUBGRAY, modifier = Modifier.padding(16.dp))
            } else route.steps.forEachIndexed { i, s ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(Color(0x1A3C3C43)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(routeStepIcon(s)), null, Modifier.size(22.dp).padding(end = 8.dp), colorFilter = ColorFilter.tint(SUBGRAY))
                    Text(s.instruction, fontSize = 16.sp, color = LABEL, modifier = Modifier.weight(1f))
                    if (s.distanceMeters > 0) Text(distLabel(s.distanceMeters), fontSize = 14.sp, color = SUBGRAY, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

internal fun routeStepIcon(step: RouteStep): Int = maneuverIconFor(step.maneuverType, step.maneuverModifier, step.instruction)

internal fun maneuverIconFor(type: String, modifier: String, instruction: String = ""): Int {
    val kind = type.lowercase()
    val mod = modifier.lowercase()
    return when {
        kind == "arrive" || instruction.startsWith("Arrive", ignoreCase = true) -> R.drawable.ic_location_fill
        "roundabout" in kind || kind == "rotary" -> R.drawable.ic_arrow_clockwise
        "uturn" in mod || "u-turn" in mod -> R.drawable.ic_arrow_left_and_right
        "sharp left" in mod || "slight left" in mod || mod == "left" -> R.drawable.ic_arrow_up_left
        "sharp right" in mod || "slight right" in mod || mod == "right" -> R.drawable.ic_arrow_triangle_turn_up_right
        mod == "straight" || kind == "depart" || kind == "continue" || kind == "new name" -> R.drawable.ic_arrow_up
        instruction.startsWith("Turn left", ignoreCase = true) ||
            instruction.startsWith("Keep left", ignoreCase = true) ||
            instruction.startsWith("Merge left", ignoreCase = true) -> R.drawable.ic_arrow_up_left
        instruction.startsWith("Turn right", ignoreCase = true) ||
            instruction.startsWith("Keep right", ignoreCase = true) ||
            instruction.startsWith("Merge right", ignoreCase = true) -> R.drawable.ic_arrow_triangle_turn_up_right
        else -> R.drawable.ic_arrow_up
    }
}

private fun etaLabel(seconds: Int): String {
    val m = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    return if (m >= 60) "${m / 60} hr ${m % 60} min" else "$m min"
}

private fun arrivalLabel(seconds: Int, offsetMin: Int = 0): String {
    val c = Calendar.getInstance(); c.add(Calendar.SECOND, seconds + offsetMin * 60)
    var h = c.get(Calendar.HOUR); if (h == 0) h = 12
    val ampm = if (c.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(h, c.get(Calendar.MINUTE), ampm)
}

private fun distLabel(meters: Int): String {
    val mi = meters / 1609.34
    return if (mi < 0.1) "${(meters * 3.28084).roundToInt()} ft" else "%.1f mi".format(mi)
}

package com.example.applemaps.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.applemaps.R
import com.example.applemaps.ui.anim.AppleDuration
import com.example.applemaps.ui.anim.AppleEasing
import com.example.applemaps.ui.theme.LocalAppleColors
import kotlinx.coroutines.CancellationException

/** Translucent-white map control (map-type / location). Press = scale 1→.95 + dim (extracted motion). */
@Composable
fun AppleControlButton(
    iconRes: Int,
    cdRes: Int,
    size: Dp,
    shape: Shape = CircleShape,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(AppleDuration.ControlWipe, easing = AppleEasing.Standard),
        label = "press",
    )
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .shadow(4.dp, shape, clip = false)   // floating map control — soft lift over the map
            .clip(shape)
            .background(Color.White.copy(alpha = 0.82f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = stringResource(cdRes),
            colorFilter = ColorFilter.tint(LocalAppleColors.current.glyphDefault.copy(alpha = if (pressed) 0.85f else 1f)),
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/**
 * Bottom sheet (tray). Reproduces the maps.apple.com sheet feel, copied from shell.js:
 *  - drag tracks the finger 1:1;
 *  - on release, the fling velocity is PROJECTED (iOS deceleration-rate model, rate≈0.998) to a
 *    predicted resting height, then it SNAPS to the nearest detent and settles with the ExpoOut curve
     *    (`cubic-bezier(.19,1,.22,1)`) — no physics bounce;
 *  - [navBarHeight] caps how far DOWN the sheet can go: when the navigation buttons are enabled it
 *    rests ON TOP of them (the sheet is lifted by [navBarHeight] and never overlaps that bar).
 * Detents = peek, a medium (~half), and full. iOS-style rounded top corners via [topRadius].
 */
@Composable
fun AppleBottomSheet(
    peekHeight: Dp,
    topRadius: Dp,
    modifier: Modifier = Modifier,
    navBarHeight: Dp = 0.dp,
    controller: AppleSheetController? = null,     // external detent control (e.g. search → expand)
    ceilingProvider: (() -> Float)? = null,       // max height this sheet may occupy — stays under the front sheet
    initialDetent: Int? = null,                   // detent this sheet first appears at (default peek) — for stacking
    header: @Composable ColumnScope.() -> Unit,   // grabber+brand+search — ALWAYS drags the sheet
    body: @Composable ColumnScope.() -> Unit,     // scrolls, but ONLY once the sheet is fully expanded
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val colors = LocalAppleColors.current
        val sysNavPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val statusPx = WindowInsets.statusBars.getTop(density).toFloat()

        // Measured detents: peek (search visible) · medium 320 (--open-card-height) · FULL = top of the
        // screen (just below the status bar). Sheet rests above the system nav bar + any in-app nav bar.
        val openCardHeight = 320.dp
        val peekPx = with(density) { peekHeight.toPx() }
        val fullPx = with(density) { (maxHeight - navBarHeight).toPx() } - sysNavPx - statusPx
        val mediumPx = with(density) { openCardHeight.toPx() }.coerceIn(peekPx, fullPx)
        val detents = listOf(peekPx, mediumPx, fullPx).distinct().sorted()

        val initIdx = (initialDetent ?: 0).coerceIn(0, detents.lastIndex)
        val offset = remember { mutableFloatStateOf(detents[initIdx]) }   // first appears AT this detent (stacking)
        val restingIndex = remember { mutableIntStateOf(initIdx) }
        val motionGeneration = remember { mutableIntStateOf(0) }
        val scroll = rememberScrollState()

        // Publish this sheet's live height + settled detent so a STACKED front sheet can couple to it.
        if (controller != null) {
            androidx.compose.runtime.SideEffect {
                controller.offsetPx = offset.floatValue
                controller.restingIndex = restingIndex.intValue
            }
        }
        // Ceiling coupling (sheet-over-sheet): this sheet may never rise ABOVE the sheet in front of it. When the
        // front sheet is dragged DOWN past this one, this one follows it down (pulled down); otherwise it is free.
        if (ceilingProvider != null) {
            LaunchedEffect(Unit) {
                androidx.compose.runtime.snapshotFlow { ceilingProvider() }.collect { ceil ->
                    if (ceil > 0f && offset.floatValue > ceil) offset.floatValue = ceil
                }
            }
        }

        val decelerationRate = 0.998f
        val flingScale = 0.30f
        val projectionFactor = decelerationRate / (1f - decelerationRate) / 1000f * flingScale

        // Release settle: project the fling, advance/retreat by DETENT (35% commit), glide (ExpoOut).
        val settle: suspend (Float) -> Unit = { velocity ->
            val motionId = ++motionGeneration.intValue
            val cur = offset.floatValue
            val from = detents[restingIndex.intValue]
            val projected = cur + (-velocity) * projectionFactor
            val commit = 0.35f
            var idx = restingIndex.intValue
            if (projected >= from) {
                while (idx < detents.lastIndex && projected >= detents[idx] + (detents[idx + 1] - detents[idx]) * commit) idx++
            } else {
                while (idx > 0 && projected <= detents[idx] - (detents[idx] - detents[idx - 1]) * commit) idx--
            }
            restingIndex.intValue = idx
            val target = detents[idx]
            val dist = kotlin.math.abs(target - cur)
            // Keep the (good) distance-scaled base; a HARDER fling only makes it FASTER (shorter time).
            // Distance-scaled glide, NO velocity term. The velocity divide (added in the pin commit)
            // crushed EVERY real fling to the 200ms floor → snap, not ease (proven by the on-device
            // SETTLE log: durMs=200 for dist 494 and 1488 alike). This is the version that felt right:
            // ~250ms short hop … ~900ms full-travel, ExpoOut tail — matching the web sheet's ~900ms glide.
            val durMs = (250f + (dist / (fullPx - peekPx).coerceAtLeast(1f)) * 650f).toInt().coerceIn(250, 900)
            // DIAG: log the settle params + capture the ACTUAL offset trajectory (extreme-diagnostics)
            val startNs = System.nanoTime()
            val traj = StringBuilder(768)
            com.example.applemaps.diag.DiagLog.log(
                "SETTLE",
                "from=${from.toInt()}", "cur=${cur.toInt()}", "target=${target.toInt()}",
                "dist=${dist.toInt()}", "vel=${velocity.toInt()}", "proj=${projected.toInt()}",
                "idx=$idx", "durMs=$durMs", "easing=ExpoOut",
            )
            animate(cur, target, animationSpec = tween(durMs, easing = AppleEasing.ExpoOut)) { v, _ ->
                if (motionId != motionGeneration.intValue) throw CancellationException("sheet motion superseded")
                offset.floatValue = v
                traj.append(((System.nanoTime() - startNs) / 1_000_000).toInt()).append(':').append(v.toInt()).append(',')
            }
            if (motionId == motionGeneration.intValue) com.example.applemaps.diag.DiagLog.log("TRAJ", traj.toString())
        }

        // External control: search tap → expand to a detent (consumed once).
        if (controller != null) {
            LaunchedEffect(controller.request) {
                val req = controller.request
                if (req != null) {
                    val idx = req.coerceIn(0, detents.lastIndex)
                    val motionId = ++motionGeneration.intValue
                    restingIndex.intValue = idx
                    animate(offset.floatValue, detents[idx], animationSpec = tween(400, easing = AppleEasing.ExpoOut)) { v, _ ->
                        if (motionId != motionGeneration.intValue) throw CancellationException("sheet motion superseded")
                        offset.floatValue = v
                    }
                    controller.request = null
                }
            }
        }

        // Nested-scroll coordination: drag UP expands the sheet to full BEFORE the body scrolls; drag
        // DOWN scrolls the body to the top BEFORE the sheet collapses (the standard iOS sheet behavior).
        val connection = remember(fullPx, peekPx, mediumPx) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    val dy = available.y
                    if (dy < 0f && offset.floatValue < fullPx) {
                        motionGeneration.intValue++
                        val take = (-dy).coerceAtMost(fullPx - offset.floatValue)
                        offset.floatValue += take
                        return androidx.compose.ui.geometry.Offset(0f, -take)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    val dy = available.y
                    if (dy > 0f && offset.floatValue > peekPx) {
                        motionGeneration.intValue++
                        val take = dy.coerceAtMost(offset.floatValue - peekPx)
                        offset.floatValue -= take
                        return androidx.compose.ui.geometry.Offset(0f, take)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    val v = available.y
                    if ((v < 0f && offset.floatValue < fullPx) || (v > 0f && offset.floatValue > peekPx && scroll.value == 0)) {
                        settle(v)
                        return available
                    }
                    return androidx.compose.ui.unit.Velocity.Zero
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = navBarHeight)
                .height(with(density) { offset.floatValue.toDp() })
                .shadow(12.dp, RoundedCornerShape(topStart = topRadius, topEnd = topRadius), clip = false)  // sheet lift over the map
                .clip(RoundedCornerShape(topStart = topRadius, topEnd = topRadius))
                .background(Color(0xFFF2F2F2)),   // traced maps.apple.com card bg = rgb(242,242,242)
        ) {
            // header (grabber + brand + search) — always drags the sheet directly, at any point
            Column(
                Modifier.fillMaxWidth().draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        motionGeneration.intValue++
                        offset.floatValue = (offset.floatValue - delta).coerceIn(peekPx, fullPx)
                    },
                    onDragStopped = { velocity -> settle(velocity) },
                ),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(3.dp))
                        .background(colors.glyphMuted.copy(alpha = 0.45f)))
                }
                header()
            }
            // body — scrolls within the sheet, coordinated by the nested-scroll connection above
            Column(
                Modifier.fillMaxWidth().weight(1f).nestedScroll(connection).verticalScroll(scroll),
            ) { body() }
        }
    }
}

/** Brand row: "Maps" wordmark + BETA badge (measured at the top of the sheet). */
@Composable
fun SheetBrandRow(modifier: Modifier = Modifier) {
    val colors = LocalAppleColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Maps", color = colors.glyphDefault, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(colors.glyphMuted.copy(alpha = 0.20f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text("BETA", color = colors.glyphMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The rounded "Apple Maps" search field that lives in the sheet. */
@Composable
fun AppleSearchField(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val colors = LocalAppleColors.current
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.fillSecondary)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_magnifyingglass_semibold),
            contentDescription = stringResource(R.string.cd_search),
            colorFilter = ColorFilter.tint(colors.glyphMuted),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Apple Maps", color = colors.glyphMuted, fontSize = 17.sp)
    }
}

/** Privacy · Terms · Legal · Imagery (measured just above the sheet, ~11dp text). */
@Composable
fun LegalLinks(modifier: Modifier = Modifier) {
    val colors = LocalAppleColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(R.string.legal_privacy, R.string.legal_terms, R.string.legal_legal, R.string.legal_imagery)
            .forEach { Text(stringResource(it), color = colors.glyphMuted, fontSize = 11.sp) }
    }
}

/** Staggered entrance: fade + slide-up (the extracted `moveIn`+`fadeIn`), curve AppleEasing.CurveB
 *  (--apl-curve-default = cubic-bezier(.12,.55,.19,1)), ~500ms, delay index*40ms. Plays once on show. */
@Composable
fun AppleAppearOnce(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(500, delayMillis = index * 40, easing = AppleEasing.CurveB),
        label = "appear",
    )
    Box(Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 24f }) { content() }
}

/**
 * Place-card entrance. The real `.mw-card` transitions `opacity .15s` and slides up from
 * `translate3d(0, --open-card-height, 0)` with `transform .15s ease-out`; the sheet itself rises
 * (ExpoOut) so here we reproduce the card's own fade + a short settle rise, 150ms CSS ease-out.
 * Re-plays whenever [key] changes (a NEW place selected).
 */
@Composable
fun AppleCardEnter(key: Any?, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { shown = true }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(150, easing = AppleEasing.EaseOutStd),
        label = "cardEnter",
    )
    val rise = with(density) { 16.dp.toPx() }
    Box(Modifier.fillMaxWidth().graphicsLayer { alpha = p; translationY = (1f - p) * rise }) { content() }
}

/** Controller to drive the sheet detent externally (e.g. search tap → expand to medium). */
@androidx.compose.runtime.Stable
class AppleSheetController {
    var request by mutableStateOf<Int?>(null)     // 0=peek,1=medium,2=full; consumed by the sheet
    var offsetPx by mutableFloatStateOf(0f)        // current sheet HEIGHT in px, published live by the sheet
    var restingIndex by mutableIntStateOf(0)       // current settled detent index, published live by the sheet
    fun goTo(detent: Int) { request = detent }
}

/** Popover "pop" — scale .5→1 + fade, extracted overshoot easing cubic-bezier(.25,.1,.25,1.3). */
@Composable
fun PopoverPop(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.scaleIn(tween(AppleDuration.Popover, easing = AppleEasing.Overshoot), initialScale = 0.5f) +
            androidx.compose.animation.fadeIn(tween(AppleDuration.Popover)),
        exit = androidx.compose.animation.scaleOut(tween(150), targetScale = 0.85f) +
            androidx.compose.animation.fadeOut(tween(150)),
        modifier = modifier,
    ) { content() }
}

/** [PopoverPop] variant driven by a MutableTransitionState, so a caller hosting the popover in a
 *  Popup window can keep the window mounted through the 150ms exit (P1 audit 3.3). Same extracted
 *  specs as [PopoverPop]: scale .5→1 overshoot bezier + fade in 200ms; scale→.85 + fade out 150ms. */
@Composable
fun PopoverPop(visibleState: androidx.compose.animation.core.MutableTransitionState<Boolean>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visibleState = visibleState,
        enter = androidx.compose.animation.scaleIn(tween(AppleDuration.Popover, easing = AppleEasing.Overshoot), initialScale = 0.5f) +
            androidx.compose.animation.fadeIn(tween(AppleDuration.Popover)),
        exit = androidx.compose.animation.scaleOut(tween(150), targetScale = 0.85f) +
            androidx.compose.animation.fadeOut(tween(150)),
        modifier = modifier,
    ) { content() }
}

/** The "Choose Map" modal (measured maps.apple.com): title 22/700 + close 30dp circle; Standard full-width
 *  preview card (r8) + Hybrid/Satellite cards below; selected card = 2dp accent border. Traffic, Transit,
 *  and 3D are not shown here because the consumer renderer path does not expose those mutable layers. */
@Composable
fun MapTypePicker(selected: String, buildings3D: Boolean = true, trafficEnabled: Boolean = false,
                  onToggle3D: (Boolean) -> Unit = {}, onToggleTraffic: (Boolean) -> Unit = {},
                  onClose: () -> Unit, onSelect: (String) -> Unit) {
    val c = LocalAppleColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Color(0xFFF2F2F2))
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),   // traced mw-inner bg #F2F2F2
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Choose Map", color = c.glyphDefault, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x24000000)).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_xmark), "Close", Modifier.size(12.dp), colorFilter = ColorFilter.tint(c.glyphMuted))
            }   // traced mw-close on Choose Map = rgba(0,0,0,0.14)
        }
        Spacer(Modifier.height(20.dp))
        MapTypeCard("Standard", selected, Modifier.fillMaxWidth(), Color(0xFFDDE8C2)) { onSelect("Standard") }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MapTypeCard("Hybrid", selected, Modifier.weight(1f), Color(0xFF3F5A4C)) { onSelect("Hybrid") }
            MapTypeCard("Satellite", selected, Modifier.weight(1f), Color(0xFF4B5340)) { onSelect("Satellite") }
        }
    }
}

// Traced mw-map-type-card-button: 121px tall, r8, label = frosted white bar overlaid at the bottom
// (span bg rgba(255,255,255,0.6), 15px/400); selected = 2px #007CFF ring (box-shadow 0 0 0 2px).
@Composable
private fun MapTypeCard(label: String, selected: String, modifier: Modifier, preview: Color, onClick: () -> Unit) {
    val sel = label == selected
    Box(
        modifier.height(121.dp).clip(RoundedCornerShape(8.dp)).background(preview)
            .then(if (sel) Modifier.border(2.dp, Color(0xFF007CFF), RoundedCornerShape(8.dp)) else Modifier)
            .clickable { onClick() },
    ) {
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x99FFFFFF)).padding(horizontal = 12.dp, vertical = 8.dp),
        ) { Text(label, color = Color(0xFF1D1D1F), fontSize = 15.sp) }
    }
}

package com.example.applemaps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.imageLoader
import com.example.applemaps.R
import com.example.applemaps.diag.DiagLog
import com.example.applemaps.map.LookAround
import com.example.applemaps.map.LookAroundProvider
import com.example.applemaps.map.LookAroundRawFace
import com.example.applemaps.ui.anim.AppleEasing
import com.example.applemaps.ui.components.AppleControlButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.min

/**
 * Look Around — direct on-device Apple imagery with keyless Panoramax fallback.
 * [LookAroundTile] is the place-card thumbnail with the binoculars affordance; [LookAroundViewer] is the
 * fullscreen pannable image (drag to look around, pinch to zoom). Attribution follows the selected provider.
 *
 * [LookAroundTile] accepts `look: LookAround? = null` so `AppleMapsScreen`/`PlaceCard` can render the
 * tile when no direct Apple/Panoramax panorama coverage is available: when
 * `look == null` a neutral placeholder card (binoculars badge only, no thumbnail) is shown instead of
 * gating the tile away — Apple's own Look Around may still have coverage where Panoramax doesn't.
 */
@Composable
fun LookAroundTile(look: LookAround?, onClick: () -> Unit, onBoundsChanged: (Rect) -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp))
                .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
                .background(Color(0x14000000)).clickable { onClick() },
        ) {
            // placeholderFill — plain dark tint shown in place of the Panoramax thumbnail when there's
            // no Panoramax coverage but the Apple backend may still have imagery (look == null).
            if (look != null) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(look.thumbUrl).crossfade(300).build(), contentDescription = "Look Around",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
            // Frosted binoculars badge + label, bottom-left (Apple's Look Around affordance)
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp)
                    .clip(RoundedCornerShape(9.dp)).background(Color(0x8C000000))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(R.drawable.ic_binoculars_fill), null,
                    Modifier.size(16.dp), colorFilter = ColorFilter.tint(Color.White),
                )
                Spacer(Modifier.width(6.dp))
                Text("Look Around", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Floating map entry for Look Around, matching the measured mobile Maps control: a 44dp translucent
 * square with 12dp corners. The root screen owns its live sheet-relative position and opens the
 * large preview card from this control.
 */
@Composable
fun LookAroundFloatingButton(
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier.onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }) {
        AppleControlButton(
            iconRes = R.drawable.ic_binoculars_fill,
            cdRes = R.string.cd_look_around,
            size = 44.dp,
            shape = RoundedCornerShape(12.dp),
            onClick = onClick,
        )
    }
}

/**
 * lookAroundMapThumbnail — 150×96dp street-photo card at the map's lower-left, wholly above the place sheet.
 * A small dark binocular badge identifies it without placing any Look Around control inside the sheet. Tapping
 * the image opens the existing large preview; only that preview's expand action opens fullscreen.
 */
@Composable
fun LookAroundMapThumbnail(
    look: LookAround,
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(width = 150.dp, height = 96.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
            .clip(RoundedCornerShape(14.dp)).background(Color(0x14000000)).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(look.thumbUrl).crossfade(300).build(),
            contentDescription = "Open Look Around",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.align(Alignment.BottomStart).padding(8.dp).size(28.dp)
                .clip(CircleShape).background(Color(0xA6000000)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_binoculars_fill), null,
                Modifier.size(15.dp), colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}

/**
 * mapLookAroundPreview — the large rounded street-level card shown after tapping the floating
 * binoculars control. It mirrors the supplied iOS/web sequence: preview first, then a separate
 * expand action into the fullscreen viewer. A centered white spinner remains visible while the box
 * builds a first-use panorama; metadata absence and image failure retain the stable gray empty state.
 */
@Composable
fun LookAroundPreviewCard(
    look: LookAround?,
    loading: Boolean,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().height(314.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
            // No self-clip: LookAroundEntryPreviewTransform clips this card to the animated rounded rect, so a
            // second clip here would cut its own (scaled, smaller-radius) corners inside that one mid-collapse.
            .background(Color(0xFF929296)),
    ) {
        look?.let {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(it.sdUrl).build(),
                contentDescription = "Look Around preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clickable { onExpand() },
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.78f), strokeWidth = 3.dp)
                    }
                },
                success = { SubcomposeAsyncImageContent() },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Look Around unavailable here", color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp)
                    }
                },
            )
        }
        if (look == null) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.78f), strokeWidth = 3.dp)
                } else {
                    Text("Look Around unavailable here", color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp)
                }
            }
        }

        Row(
            Modifier.align(Alignment.TopEnd).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC242426))
                    .clickable { onExpand() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.ic_arrow_down_left_and_arrow_up_right),
                    stringResource(R.string.cd_enter_fullscreen),
                    Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(width = 72.dp, height = 48.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC242426)).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) { Text("Done", color = Color.White, fontSize = 16.sp) }
        }

        Image(
            painterResource(R.drawable.ic_binoculars_fill), null,
            Modifier.align(Alignment.BottomStart).padding(20.dp).size(20.dp),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

/** Explicit GL-provider empty state; keeps Done and the debug provider selector reachable. */
@Composable
fun LookAroundUnavailableViewer(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.ic_binoculars_fill), null,
                Modifier.size(36.dp), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.72f)),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No Look Around imagery near this location.",
                color = Color.White.copy(alpha = 0.82f), fontSize = 16.sp,
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(20.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000))
                .clickable { onClose() }.padding(horizontal = 16.dp, vertical = 9.dp),
        ) { Text("Done", color = Color.White, fontSize = 16.sp) }
    }
}

/**
 * lookAroundEntryPreviewTransform — the floating binocular/photo entry and 314dp preview are one visual container.
 * It maps the measured entry bounds onto the measured preview bounds on an explicit geometry tween — 320ms
 * ExpoOut opening, 300ms Exit closing — and reverses the same path before the map entry is remounted. The
 * travelling rounded rect is a real-pixel CLIP (radius 14dp→16dp), while the card inside carries a single
 * uniform scale, so the imagery crops instead of stretching and the collapse lands on the thumbnail's exact
 * outline. `onIdle(expanded)` fires when the transition truly settles, which is what retires the caller's
 * transitioning flag. `settleAtTarget` is used only when fullscreen Done
 * returns to this preview, so the fullscreen's own reverse transform lands on a stable destination.
 * Source/build verified; physical-phone motion remains unverified.
 */
@Composable
fun LookAroundEntryPreviewTransform(
    visible: Boolean,
    sourceBounds: Rect?,
    settleAtTarget: Boolean,
    modifier: Modifier = Modifier,
    onIdle: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    val clipShape = remember { Path() }
    SideEffect { visibility.targetState = visible }
    // transitionSettled — reports the REAL end of the geometry transition (expanded=true / collapsed=false)
    // so the caller retires its transitioning flag on the animation clock instead of a fixed timer.
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle) onIdle(visibility.currentState)
    }
    if (visibility.currentState || visibility.targetState) {
        Box(Modifier.fillMaxSize()) {
            val transition = rememberTransition(visibility, label = "lookAroundEntryPreviewTransform")
            // Expand eases out (ExpoOut); collapse uses the Exit curve so it accelerates away instead of
            // crawling through its last frames on a reversed ease-out and then cutting at unmount.
            val animatedProgress = transition.animateFloat(
                transitionSpec = {
                    if (targetState) tween(320, easing = AppleEasing.ExpoOut)
                    else tween(300, easing = AppleEasing.Exit)
                },
                label = "lookAroundEntryPreviewGeometry",
            ) { if (it) 1f else 0f }
            // fullscreen-return preview — hold the destination geometry while the existing fullscreen
            // reverse transform reaches it; its own transition clock still completes underneath.
            fun progress(): Float = if (settleAtTarget && visible) 1f else animatedProgress.value
            Box(
                modifier.onGloballyPositioned { targetBounds = it.boundsInWindow() }
                    // animatedClipWindow — the card is CLIPPED to a real-pixel rounded rect that travels and
                    // resizes between the map thumbnail's rect and the preview's own rect. The clip carries the
                    // shape (radius 14dp→16dp interpolated in device pixels, so corners stay circular and the
                    // collapse lands exactly on the thumbnail's 150×96 outline); the content layer below only
                    // scales, so geometry and rounding can never disagree.
                    .drawWithContent {
                        val target = targetBounds
                        val source = sourceBounds
                        val p = progress()
                        val w: Float
                        val h: Float
                        val cx: Float
                        val cy: Float
                        if (target == null || source == null) {
                            w = size.width; h = size.height; cx = 0f; cy = 0f
                        } else {
                            w = source.width + (size.width - source.width) * p
                            h = source.height + (size.height - source.height) * p
                            cx = (source.center.x - target.center.x) * (1f - p)
                            cy = (source.center.y - target.center.y) * (1f - p)
                        }
                        val left = (size.width - w) / 2f + cx
                        val top = (size.height - h) / 2f + cy
                        val radius = 14.dp.toPx() + (16.dp.toPx() - 14.dp.toPx()) * p
                        clipShape.rewind()
                        clipShape.addRoundRect(
                            RoundRect(
                                Rect(left, top, left + w, top + h),
                                CornerRadius(radius, radius),
                            ),
                        )
                        clipPath(clipShape) { this@drawWithContent.drawContent() }
                    },
            ) {
                // croppedContentLayer — ONE uniform scale (max of the two axis ratios) so the street image is
                // CROPPED by the clip above, never squashed; independent scaleX/scaleY distorted the card and
                // every control inside it whenever the thumbnail and the preview had different aspect ratios.
                Box(
                    Modifier.graphicsLayer {
                        val target = targetBounds
                        val source = sourceBounds
                        if (target != null && source != null) {
                            val p = progress()
                            val w = source.width + (size.width - source.width) * p
                            val h = source.height + (size.height - source.height) * p
                            val scale = maxOf(w / size.width, h / size.height).coerceAtLeast(0.001f)
                            scaleX = scale
                            scaleY = scale
                            translationX = (source.center.x - target.center.x) * (1f - p)
                            translationY = (source.center.y - target.center.y) * (1f - p)
                        }
                    },
                ) { content() }
            }
        }
    }
}

/** Geometry-only preview-tile to fullscreen transform. Alpha remains 1 throughout. */
@Composable
fun LookAroundTileTransform(
    visible: Boolean,
    sourceBounds: Rect?,
    sourceCornerRadius: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    SideEffect { visibility.targetState = visible }
    if (visibility.currentState || visibility.targetState) {
        BoxWithConstraints(
            Modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInWindow() },
        ) {
            val fullW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val fullH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val root = rootBounds
            val transition = rememberTransition(visibility, label = "lookAroundTileTransform")
            val progress by transition.animateFloat(
                transitionSpec = { tween(if (targetState) 380 else 320, easing = AppleEasing.ExpoOut) },
                label = "lookAroundGeometry",
            ) { if (it) 1f else 0f }
            val startScaleX = (sourceBounds?.width ?: root?.width ?: fullW) /
                (root?.width ?: fullW).coerceAtLeast(1f)
            val startScaleY = (sourceBounds?.height ?: root?.height ?: fullH) /
                (root?.height ?: fullH).coerceAtLeast(1f)
            val startX = if (sourceBounds != null && root != null) {
                sourceBounds.center.x - root.center.x
            } else 0f
            val startY = if (sourceBounds != null && root != null) {
                sourceBounds.center.y - root.center.y
            } else 0f
            val radius = sourceCornerRadius * (1f - progress)
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = startScaleX + (1f - startScaleX) * progress
                scaleY = startScaleY + (1f - startScaleY) * progress
                translationX = startX * (1f - progress)
                translationY = startY * (1f - progress)
                shape = RoundedCornerShape(radius)
                clip = true
            }) { content() }
        }
    }
}

/**
 * Returns the distinct high-resolution URL for flat fullscreen imagery. SD remains the first paint; a blank,
 * duplicate, or same URL must never start a second decode. Kept pure so the full-resolution request contract is
 * covered without a device renderer.
 */
internal fun flatLookAroundHdUrl(sdUrl: String, hdUrl: String): String? =
    hdUrl.takeIf { it.isNotBlank() && it != sdUrl }

/**
 * A single selected-place preparation retained by [AppleMapsScreen]. Raw Apple faces are decoded at the
 * bounded first-frame size; Panoramax uses one software SD bitmap. The same deferred result is handed to the
 * viewer, preventing duplicate work when fullscreen opens before preparation completes.
 */
internal data class PreparedLookAround(
    val lookId: String,
    val rawFaces: List<RawPanoFace>? = null,
    val rawFaceSize: Int = 0,
    val panoBitmap: android.graphics.Bitmap? = null,
)

/** Performs selected-place panorama download/decode off the main thread before fullscreen is requested. */
internal suspend fun prepareLookAround(
    context: android.content.Context,
    look: LookAround,
): PreparedLookAround? {
    try {
        if (look.rawFaces.size == 6) {
            loadRawPanoFaces(look.rawFaces, PanoMath.PRELOAD_RAW_FACE_SIZE)?.let { faces ->
                DiagLog.log(
                    "LOOKRAW", "event=preloadReady", "kind=raw",
                    "target=${PanoMath.PRELOAD_RAW_FACE_SIZE}", "provider=${look.provider}",
                )
                return PreparedLookAround(look.id, rawFaces = faces, rawFaceSize = PanoMath.PRELOAD_RAW_FACE_SIZE)
            }
        }
        return loadPanoBitmap(context, look.sdUrl, potW = 2048)?.let { bitmap ->
            DiagLog.log("LOOKRAW", "event=preloadReady", "kind=pano", "target=2048", "provider=${look.provider}")
            PreparedLookAround(look.id, panoBitmap = bitmap)
        }.also {
            if (it == null) DiagLog.log("LOOKRAW", "event=preloadFailed", "provider=${look.provider}")
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DiagLog.log("LOOKRAW", "event=preloadFailed", "provider=${look.provider}")
        return null
    }
}

/** Fullscreen Look Around viewer — real GL 360 pano for [LookAround.isPano], or flat pan/zoom imagery that
 * upgrades from SD to a successful 4096×2048 HD decode. Entered by tapping [LookAroundTile]. */
@Composable
internal fun LookAroundViewer(
    look: LookAround,
    preparation: Deferred<PreparedLookAround?>? = null,
    onClose: () -> Unit,
) {
    if (look.isPano) { PanoViewer(look, preparation, onClose); return }
    val context = androidx.compose.ui.platform.LocalContext.current
    val hdUrl = remember(look.id, look.sdUrl, look.hdUrl) { flatLookAroundHdUrl(look.sdUrl, look.hdUrl) }
    var sdReady by remember(look.id, look.sdUrl) { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    var panVx by remember { mutableFloatStateOf(0f) }
    var panVy by remember { mutableFloatStateOf(0f) }
    var lastPanNs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val maxX = with(LocalDensity.current) { maxWidth.toPx() }
        val maxY = with(LocalDensity.current) { maxHeight.toPx() }
        val transformState = rememberTransformableState { zoom, pan, _ ->
            val now = System.nanoTime()
            val dt = ((now - lastPanNs).coerceAtLeast(1L) / 1_000_000_000f).coerceAtMost(0.1f)
            if (lastPanNs != 0L) { panVx = pan.x / dt; panVy = pan.y / dt }
            lastPanNs = now
            scale = (scale * zoom).coerceIn(1f, 4f)
            offX = (offX + pan.x).coerceIn(-maxX * scale * 1.12f, maxX * scale * 1.12f)
            offY = (offY + pan.y).coerceIn(-maxY * (scale - 1f) * 1.12f, maxY * (scale - 1f) * 1.12f)
        }
        LaunchedEffect(transformState, maxX, maxY) {
            snapshotFlow { transformState.isTransformInProgress }.collectLatest { active ->
                if (!active) {
                    val targetX = (offX + panVx * 0.18f).coerceIn(-maxX * scale, maxX * scale)
                    val targetY = (offY + panVy * 0.18f).coerceIn(-maxY * (scale - 1f), maxY * (scale - 1f))
                    launch { androidx.compose.animation.core.animate(offX, targetX, animationSpec = tween(350, easing = AppleEasing.ExpoOut)) { value, _ -> offX = value } }
                    launch { androidx.compose.animation.core.animate(offY, targetY, animationSpec = tween(350, easing = AppleEasing.ExpoOut)) { value, _ -> offY = value } }
                    panVx = 0f; panVy = 0f; lastPanNs = 0L
                }
            }
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offX; translationY = offY }
                .transformable(transformState)
                .pointerInput(look.id) { detectTapGestures(onDoubleTap = {
                    val target = if (scale < 1.5f) 2f else 1f
                    scope.launch { androidx.compose.animation.core.animate(scale, target,
                        animationSpec = tween(300, easing = AppleEasing.ExpoOut)) { value, _ -> scale = value } }
                }) },
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(look.sdUrl).build(),
                contentDescription = "Look Around", contentScale = ContentScale.FillHeight,
                modifier = Modifier.fillMaxSize(),
                success = {
                    LaunchedEffect(look.id, look.sdUrl) { sdReady = true }
                    SubcomposeAsyncImageContent()
                },
            )
            if (sdReady && hdUrl != null) {
                // The transparent loading/error states preserve the SD frame until a distinct 4K decode succeeds.
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(hdUrl).size(4096, 2048).build(),
                    contentDescription = "Look Around HD", contentScale = ContentScale.FillHeight,
                    modifier = Modifier.fillMaxSize(),
                    loading = {},
                    success = {
                        LaunchedEffect(look.id, hdUrl) {
                            DiagLog.log("LOOKHD", "event=flatHdReady", "provider=${look.provider}")
                        }
                        SubcomposeAsyncImageContent()
                    },
                    error = {
                        LaunchedEffect(look.id, hdUrl) {
                            DiagLog.log("LOOKHD", "event=flatHdFailed", "provider=${look.provider}")
                        }
                    },
                )
            }
        }
        // Done pill — top-right (matches the photo viewer)
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(20.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000))
                .clickable { onClose() }.padding(horizontal = 16.dp, vertical = 9.dp),
        ) { Text("Done", color = Color.White, fontSize = 16.sp) }
        // Provider attribution + distance from the selected place.
        Box(
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0x66000000))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                if (look.provider == LookAroundProvider.APPLE) "Apple Maps · ${look.distanceMeters.toInt()} m away"
                else "Panoramax · ${look.distanceMeters.toInt()} m away",
                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp,
            )
        }
    }
}

/** panoGlSurface — real immersive 360 viewer for an Apple/Panoramax equirectangular pano: fullscreen black GL
 *  surface (PanoGlView/PanoGlRenderer, PanoRenderer.kt), one-finger drag to look around (content follows
 *  finger), simultaneous pinch-and-pan zoom, and tweened inertia on one-finger release. A single gesture
 *  owner is required because Compose's transform detector also consumes one-finger pan events. After GL publishes
 *  its actual texture limit, loads the SD 2048-wide derivative first and then swaps in a distinct HD 4096-wide
 *  derivative only on capable devices. An unavailable HD upgrade leaves the already-rendered SD texture intact.
 *  Entered from [LookAroundTile]
 *  via [LookAroundViewer] when `look.isPano == true`. Math = docs/STREET_VIEW_ROADMAP.md PART B, gesture
 *  mapping = B.6. Test by opening the GL viewer, dragging horizontally/vertically, pinching, and releasing;
 *  LOOKWEB `glGestureStart`/`glGestureEnd` events auto-upload. Status: source-corrected; phone UI UNVERIFIED. */
@Composable
private fun PanoViewer(
    look: LookAround,
    preparation: Deferred<PreparedLookAround?>?,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var bearingDeg by remember(look.id) { mutableFloatStateOf((look.azimuth ?: 0.0).toFloat()) }
    var pitchDeg by remember(look.id) { mutableFloatStateOf(0f) }
    var fovYDeg by remember(look.id) { mutableFloatStateOf(65f) }
    var glView by remember { mutableStateOf<android.opengl.GLSurfaceView?>(null) }
    var renderer by remember { mutableStateOf<PanoGlRenderer?>(null) }
    var maxTextureSize by remember { mutableStateOf(0) }
    val flingBearing = remember(look.id) { androidx.compose.animation.core.Animatable(bearingDeg) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PanoGlView(
            modifier = Modifier.fillMaxSize(),
            onTextureLimitKnown = { maxTextureSize = it },
        ) { v, r -> glView = v; renderer = r }
        // glPanZoomSurface — transparent fullscreen touch layer above the panorama; one finger rotates,
        // two fingers may pan and pinch together, and only a one-finger release carries horizontal inertia.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(look.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, Offset.Zero)
                        var trackedPan = Offset.Zero
                        var accumulatedPan = Offset.Zero
                        var accumulatedZoom = 1f
                        var pastTouchSlop = false
                        var hadMultiplePointers = false
                        var canceled = false
                        val touchSlop = viewConfiguration.touchSlop
                        scope.launch { flingBearing.stop() }

                        do {
                            val event = awaitPointerEvent()
                            canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                val panChange = event.calculatePan()
                                val zoomChange = event.calculateZoom()
                                hadMultiplePointers = hadMultiplePointers || event.changes.count { it.pressed } > 1
                                trackedPan += panChange
                                event.changes.firstOrNull { it.pressed || it.previousPressed }?.let {
                                    velocityTracker.addPosition(it.uptimeMillis, trackedPan)
                                }

                                if (!pastTouchSlop) {
                                    accumulatedPan += panChange
                                    accumulatedZoom *= zoomChange
                                    val zoomMotion = abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
                                    if (accumulatedPan.getDistance() > touchSlop || zoomMotion > touchSlop) {
                                        pastTouchSlop = true
                                        DiagLog.log("LOOKWEB", "event=glGestureStart", "provider=${look.provider}")
                                    }
                                }

                                if (pastTouchSlop && (panChange != Offset.Zero || zoomChange != 1f)) {
                                    val widthPx = size.width.coerceAtLeast(1)
                                    val heightPx = size.height.coerceAtLeast(1)
                                    val fovX = PanoMath.fovXdeg(fovYDeg, widthPx.toFloat() / heightPx)
                                    bearingDeg -= panChange.x / widthPx * fovX
                                    pitchDeg = (pitchDeg + panChange.y / heightPx * fovYDeg).coerceIn(-85f, 85f)
                                    fovYDeg = (fovYDeg / zoomChange).coerceIn(30f, 100f)
                                    pushCamera(renderer, glView, look, bearingDeg, pitchDeg, fovYDeg)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })

                        if (pastTouchSlop) {
                            val velocityX = if (canceled || hadMultiplePointers) 0f else velocityTracker.calculateVelocity().x
                            DiagLog.log(
                                "LOOKWEB", "event=glGestureEnd", "canceled=$canceled",
                                "multi=$hadMultiplePointers", "vx=${velocityX.toInt()}", "fov=${fovYDeg.toInt()}",
                            )
                            if (!canceled && !hadMultiplePointers) {
                                val widthPx = size.width.coerceAtLeast(1)
                                val heightPx = size.height.coerceAtLeast(1)
                                val fovX = PanoMath.fovXdeg(fovYDeg, widthPx.toFloat() / heightPx)
                                val target = bearingDeg - velocityX / widthPx * fovX * 0.35f
                                scope.launch {
                                    flingBearing.snapTo(bearingDeg)
                                    flingBearing.animateTo(target, tween(500, easing = AppleEasing.ExpoOut)) {
                                        bearingDeg = value
                                        pushCamera(renderer, glView, look, bearingDeg, pitchDeg, fovYDeg)
                                    }
                                }
                            }
                        }
                    }
                },
        )
        // Done pill — top-right, same styling as the flat viewer
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(20.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000))
                .clickable { onClose() }.padding(horizontal = 16.dp, vertical = 9.dp),
        ) { Text("Done", color = Color.White, fontSize = 16.sp) }
        // Panoramax keeps its required license link; Apple uses provider text without that link.
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        val attributionModifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)).let { base ->
                if (look.provider == LookAroundProvider.PANORAMAX) {
                    base.clickable { uriHandler.openUri("https://creativecommons.org/licenses/by-sa/4.0/") }
                } else base
            }.padding(horizontal = 10.dp, vertical = 5.dp)
        Box(
            attributionModifier,
        ) {
            Text(
                if (look.provider == LookAroundProvider.APPLE) {
                    "Apple Maps · ${look.distanceMeters.toInt()} m"
                } else {
                    "© ${look.producer ?: "contributor"} · Panoramax · CC-BY-SA · ${look.distanceMeters.toInt()} m"
                },
                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp,
            )
        }
    }

    LaunchedEffect(look.id, renderer, maxTextureSize, look.rawFaces, preparation) {
        val r = renderer ?: return@LaunchedEffect
        if (maxTextureSize <= 0) return@LaunchedEffect
        val rawTarget = PanoMath.rawFaceTargetSize(maxTextureSize)
        val prepared = preparation?.await()?.takeIf { it.lookId == look.id }
        val preparedRawSize = prepared?.rawFaceSize ?: 0
        var hasRawFrame = false
        if (rawTarget > 0 && look.rawFaces.size == 6) {
            prepared?.rawFaces?.takeIf { preparedRawSize <= rawTarget }?.let { faces ->
                r.setRawFaces(faces); glView?.requestRender(); hasRawFrame = true
                DiagLog.log(
                    "LOOKRAW", "event=preloadUsed", "kind=raw",
                    "target=$preparedRawSize", "provider=${look.provider}",
                )
            }
            val needsRawLoad = (hasRawFrame && rawTarget > preparedRawSize) ||
                (!hasRawFrame && prepared?.panoBitmap == null)
            if (needsRawLoad) {
                loadRawPanoFaces(look.rawFaces, rawTarget)?.let { faces ->
                    r.setRawFaces(faces); glView?.requestRender(); hasRawFrame = true
                    DiagLog.log("LOOKRAW", "event=rawFacesReady", "target=$rawTarget", "provider=${look.provider}")
                } ?: DiagLog.log("LOOKRAW", "event=rawFacesFailed", "target=$rawTarget", "provider=${look.provider}")
            }
            if (hasRawFrame) return@LaunchedEffect
        }
        prepared?.panoBitmap?.let { bitmap ->
            r.setBitmap(bitmap); glView?.requestRender()
            DiagLog.log("LOOKRAW", "event=preloadUsed", "kind=pano", "target=2048", "provider=${look.provider}")
        } ?: run {
            loadPanoBitmap(context, look.sdUrl, potW = 2048)?.let { r.setBitmap(it); glView?.requestRender() }
        }
        PanoMath.progressiveHdWidth(maxTextureSize, look.hdUrl != look.sdUrl)?.let { width ->
            loadPanoBitmap(context, look.hdUrl, potW = width)?.let { r.setBitmap(it); glView?.requestRender() }
        }
    }
}

/** Decodes direct Apple HEIC faces at a bounded device-local texture size on every supported API level. */
private suspend fun loadRawPanoFaces(rawFaces: List<LookAroundRawFace>, targetSize: Int): List<RawPanoFace>? =
    withContext(Dispatchers.IO) {
        val decoded = mutableListOf<RawPanoFace>()
        try {
            rawFaces.sortedBy(LookAroundRawFace::index).forEach { face ->
                currentCoroutineContext().ensureActive()
                val conn = (URL(face.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000; readTimeout = 20000; instanceFollowRedirects = true
                }
                val body = try {
                    conn.inputStream.use(::readBoundedFace)
                } finally {
                    conn.disconnect()
                }
                if (body.isEmpty()) throw java.io.IOException("Apple HEIC face was empty")
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(ByteBuffer.wrap(body))) { decoder, info, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        val scale = min(1f, targetSize.toFloat() / maxOf(info.size.width, info.size.height).toFloat())
                        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    }
                } else {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(body, 0, body.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw java.io.IOException("Apple HEIC bounds were invalid")
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSize) sample *= 2
                    val decoded = android.graphics.BitmapFactory.decodeByteArray(
                        body, 0, body.size,
                        android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                        },
                    ) ?: throw java.io.IOException("Apple HEIC decode returned null")
                    val scale = min(1f, targetSize.toFloat() / maxOf(decoded.width, decoded.height).toFloat())
                    if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                        decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1), true,
                    ).also { if (it !== decoded) decoded.recycle() } else decoded
                }
                decoded += RawPanoFace(face, bitmap)
            }
            decoded.takeIf { it.map { face -> face.calibration.index } == (0..5).toList() }
                ?: throw java.io.IOException("Apple HEIC face set was incomplete")
        } catch (cancelled: CancellationException) {
            decoded.forEach { it.bitmap.recycle() }
            throw cancelled
        } catch (_: Exception) {
            decoded.forEach { it.bitmap.recycle() }
            null
        }
    }

private fun readBoundedFace(input: java.io.InputStream): ByteArray {
    val limit = 8 * 1024 * 1024
    val output = ByteArrayOutputStream(32 * 1024)
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw java.io.IOException("Apple HEIC face exceeded $limit bytes")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

/** Pushes the current camera (bearing/pitch/FOV, relative to the pano's capture azimuth per B.1) into
 *  the renderer's @Volatile fields and requests a redraw. Called from every gesture handler. */
private fun pushCamera(
    r: PanoGlRenderer?, v: android.opengl.GLSurfaceView?, look: LookAround,
    bearingDeg: Float, pitchDeg: Float, fovYDeg: Float,
) {
    r ?: return
    r.yawRad = Math.toRadians((bearingDeg - (look.azimuth ?: 0.0)).toDouble()).toFloat()
    r.pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
    r.fovYRad = Math.toRadians(fovYDeg.toDouble()).toFloat()
    v?.requestRender()
}

/** Decodes a normalized Apple/Panoramax image URL into a POT-sized software bitmap ready for GL upload (B.4):
 *  `allowHardware(false)` (HARDWARE bitmaps can't be read for glTexImage2D), requested at `potW x potW/2`,
 *  rescaled with [Bitmap.createScaledBitmap] if Coil returned different dims. */
private suspend fun loadPanoBitmap(context: android.content.Context, url: String, potW: Int): android.graphics.Bitmap? {
    val potH = potW / 2
    val request = coil.request.ImageRequest.Builder(context).data(url).allowHardware(false).size(potW, potH).build()
    val result = context.imageLoader.execute(request)
    val drawable = (result as? coil.request.SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable ?: return null
    val bmp = drawable.bitmap
    return if (bmp.width == potW && bmp.height == potH) bmp
    else android.graphics.Bitmap.createScaledBitmap(bmp, potW, potH, true)
}

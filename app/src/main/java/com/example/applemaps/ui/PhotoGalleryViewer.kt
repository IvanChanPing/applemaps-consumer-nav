package com.example.applemaps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.example.applemaps.R
import com.example.applemaps.ui.anim.AppleEasing

/**
 * Fullscreen photo gallery viewer — traced from maps.apple.com (docs/trace/photo-gallery-viewer): a dark
 * #2B2B2B (rgb 43,43,43) backdrop, one square r10 photo at a time paged with compact chevrons, an
 * attribution pill (r10, bg rgba(0,0,0,0.1), 11sp white) overlaid on the photo and linked when the
 * provider supplies an author URL, an "N of N" caption
 * (11sp/600 white@0.8) below it, and a dark "Done" pill (72×48 r16, bg rgba(0,0,0,0.8), 16sp white).
 * Index-based (no pager dependency). Swipes track the finger and both swipe/chevron releases settle through
 * the same explicit tween. Compile-verified; gesture feel and interruption remain on-device unverified.
 */
data class GalleryPhoto(
    val url: String,
    val attribution: String? = null,
    val attributionUrl: String? = null,
    val providerAttribution: String? = null,
)

/**
 * galleryImagePanBounds — bounds a zoomed photo around its center without changing the gallery page frame.
 * The fullscreen place-photo viewer uses this for the two-finger pinch/one-finger-at-zoom gesture while its
 * existing one-finger 1× drag remains a page swipe. Pure helpers keep the gesture contract unit-testable.
 */
internal fun galleryImagePanBounds(scale: Float, width: Float, height: Float): Offset {
    val normalizedScale = scale.coerceIn(1f, 4f)
    return Offset(
        x = (width.coerceAtLeast(0f) * (normalizedScale - 1f)) / 2f,
        y = (height.coerceAtLeast(0f) * (normalizedScale - 1f)) / 2f,
    )
}

/** Clamps image-space pan so there is never exposed backdrop outside a zoomed gallery photo. */
internal fun clampGalleryImagePan(pan: Offset, scale: Float, width: Float, height: Float): Offset {
    val bounds = galleryImagePanBounds(scale, width, height)
    return Offset(pan.x.coerceIn(-bounds.x, bounds.x), pan.y.coerceIn(-bounds.y, bounds.y))
}

@Composable
fun PhotoGalleryViewer(photos: List<GalleryPhoto>, startIndex: Int = 0, onClose: () -> Unit) {
    if (photos.isEmpty()) return
    var index by remember { mutableStateOf(startIndex.coerceIn(0, photos.lastIndex)) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var pageWidth by remember { mutableFloatStateOf(1f) }
    var imageScale by remember { mutableFloatStateOf(1f) }
    var imagePan by remember { mutableStateOf(Offset.Zero) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val settlePage: (Int) -> Unit = { direction ->
        val allowed = (direction > 0 && index < photos.lastIndex) || (direction < 0 && index > 0) || direction == 0
        if (allowed) {
            settleJob?.cancel()
            settleJob = scope.launch {
                androidx.compose.animation.core.animate(dragOffset, -direction * pageWidth,
                    animationSpec = tween(260, easing = AppleEasing.ExpoOut)) { value, _ -> dragOffset = value }
                index += direction
                dragOffset = 0f
            }
        }
    }
    LaunchedEffect(index) {
        imageScale = 1f
        imagePan = Offset.Zero
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF2B2B2B))) {
        // "Done" pill — top-right (traced mw-close 72×48 r16 bg rgba(0,0,0,0.8), 16sp white, margin 20)
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(20.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000))
                .clickable { onClose() }.padding(horizontal = 16.dp, vertical = 9.dp),
        ) { Text("Done", color = Color.White, fontSize = 16.sp) }

        // Centered photo + caption
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // zoomablePhotoPager — 1× one-finger motion preserves paging; pinch (and a one-finger drag while
            // zoomed) moves only the image pixels inside the selected page. All bounds are local to this square.
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                .pointerInput(index, photos.size, imageScale) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        settleJob?.cancel()
                        var photoGesture = false
                        var cancelled = false
                        do {
                            val event = awaitPointerEvent()
                            cancelled = event.changes.any { it.isConsumed }
                            if (!cancelled) {
                                val multiTouch = event.changes.count { it.pressed } > 1
                                photoGesture = photoGesture || multiTouch || imageScale > 1f
                                if (photoGesture) {
                                    val nextScale = (imageScale * event.calculateZoom()).coerceIn(1f, 4f)
                                    imageScale = nextScale
                                    imagePan = clampGalleryImagePan(
                                        imagePan + event.calculatePan(), nextScale,
                                        size.width.toFloat(), size.height.toFloat(),
                                    )
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else {
                                    val deltaX = event.changes.firstOrNull { it.pressed || it.previousPressed }
                                        ?.let { it.position.x - it.previousPosition.x } ?: 0f
                                    if (deltaX != 0f) {
                                        dragOffset = (dragOffset + deltaX).coerceIn(
                                            if (index < photos.lastIndex) -size.width.toFloat() else 0f,
                                            if (index > 0) size.width.toFloat() else 0f,
                                        )
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            }
                        } while (!cancelled && event.changes.any { it.pressed })

                        if (!photoGesture) {
                            val width = size.width.toFloat()
                            val direction = when {
                                dragOffset < -width * 0.22f && index < photos.lastIndex -> 1
                                dragOffset > width * 0.22f && index > 0 -> -1
                                else -> 0
                            }
                            settlePage(direction)
                        }
                    }
                }) {
                val width = constraints.maxWidth.toFloat()
                LaunchedEffect(width) { pageWidth = width }
                ((index - 1).coerceAtLeast(0)..(index + 1).coerceAtMost(photos.lastIndex)).forEach { page ->
                    GalleryPage(photos[page], Modifier.fillMaxSize().offset {
                        IntOffset(((page - index) * width + dragOffset).roundToInt(), 0)
                    }, imageScale = if (page == index) imageScale else 1f,
                        imagePan = if (page == index) imagePan else Offset.Zero)
                }
            }
            Spacer(Modifier.height(17.dp))
            Text("${index + 1} of ${photos.size}", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        // Prev / next compact chevrons (traced chevron.compact.left/right, white, centered vertically)
        // P1 5.2: chevrons FADE in/out (150ms) at the first/last photo instead of popping
        AnimatedVisibility(visible = index > 0, modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(tween(150, easing = AppleEasing.Standard)), exit = fadeOut(tween(150, easing = AppleEasing.Standard))) {
            Box { ChevronButton(R.drawable.ic_chevron_compact_left, Alignment.CenterStart) { settlePage(-1) } }
        }
        AnimatedVisibility(visible = index < photos.lastIndex, modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(tween(150, easing = AppleEasing.Standard)), exit = fadeOut(tween(150, easing = AppleEasing.Standard))) {
            Box { ChevronButton(R.drawable.ic_chevron_compact_right, Alignment.CenterEnd) { settlePage(1) } }
        }
    }
}

@Composable
private fun GalleryPage(
    photo: GalleryPhoto,
    modifier: Modifier = Modifier,
    imageScale: Float = 1f,
    imagePan: Offset = Offset.Zero,
) {
    val context = LocalContext.current
    Box(modifier.background(Color(0x14FFFFFF))) {
        AsyncImage(
            model = photo.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = imageScale
                scaleY = imageScale
                translationX = imagePan.x
                translationY = imagePan.y
            },
        )
        if (photo.providerAttribution != null || photo.attribution != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x1A000000))
                .padding(horizontal = 16.dp, vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    photo.providerAttribution?.let { provider ->
                        Text(provider, color = Color.White, fontSize = 12.sp)
                    }
                    photo.attribution?.let { attr ->
                        if (photo.providerAttribution != null) Text(" · ", color = Color.White, fontSize = 11.sp)
                        Text(
                            attr,
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = photo.attributionUrl?.let { uri ->
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

@Composable
private fun BoxScope.ChevronButton(icon: Int, align: Alignment, onClick: () -> Unit) {
    Image(
        painterResource(icon), null,
        Modifier.align(align).padding(horizontal = 12.dp).size(28.dp).clickable { onClick() },
        colorFilter = ColorFilter.tint(Color.White),
    )
}

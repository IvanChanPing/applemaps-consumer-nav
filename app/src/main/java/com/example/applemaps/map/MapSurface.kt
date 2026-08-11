package com.example.applemaps.map

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose binding for the persistent consumer Apple renderer.
 *
 * The renderer is hosted as a real Android View inside Compose so WebView receives the platform's complete gesture
 * stream directly. Later Compose siblings such as controls and sheets remain above it and keep their own hits.
 */
@Composable
fun MapSurface(
    controller: ConsumerMapController,
    modifier: Modifier = Modifier,
    pin: MapCoordinate? = null,
    showBalloon: Boolean = true,
    pinTint: Color = Color(0xFFFF3B30),
    pinFace: ImageBitmap? = null,
    pinLabel: String = "Marked Location",
    onMapLongClick: (MapCoordinate) -> Unit = {},
    onPlaceSelected: (ConsumerSelectedPlace) -> Unit = {},
    onMapGesture: () -> Unit = {},
    locationEnabled: Boolean = false,
    buildings3DEnabled: Boolean = true,
) {
    val latestLongClick by rememberUpdatedState(onMapLongClick)
    val latestSelected by rememberUpdatedState(onPlaceSelected)
    val latestGesture by rememberUpdatedState(onMapGesture)
    DisposableEffect(controller) {
        controller.bind(
            onSelected = { latestSelected(it) },
            onLongPress = { latestLongClick(it) },
            onGesture = { latestGesture() },
        )
        onDispose { controller.unbind() }
    }
    LaunchedEffect(pin, showBalloon, pinTint, pinFace, pinLabel) {
        controller.setPin(pin, showBalloon, pinTint, pinFace, pinLabel)
    }
    LaunchedEffect(locationEnabled) { controller.setLocationEnabled(locationEnabled) }
    LaunchedEffect(buildings3DEnabled) { controller.setBuildings3DEnabled(buildings3DEnabled) }

    val state = controller.state.value
    Box(modifier) {
        AndroidView(
            factory = { context -> FrameLayout(context).also(controller::attachTo) },
            modifier = Modifier.fillMaxSize(),
        )
        when (state) {
            ConsumerRendererState.Ready -> Unit
            ConsumerRendererState.Loading -> StatusCard("Loading Apple map…", "Connecting to maps.apple.com")
            is ConsumerRendererState.Error -> StatusCard(state.title, state.detail, controller::reload)
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String, reload: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.background(Color(0xEEFFFFFF), RoundedCornerShape(18.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title)
            Text(detail, color = Color(0xFF636366))
            reload?.let { Button(onClick = it) { Text("Reload map") } }
        }
    }
}

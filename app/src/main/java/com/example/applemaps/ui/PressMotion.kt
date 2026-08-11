package com.example.applemaps.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.applemaps.ui.anim.AppleEasing

/**
 * applePressScale — ripple-free iOS-style touch feedback used by place, home, and navigation controls.
 * Touch-down scales to 0.95 and release returns to 1 on a 120ms explicit tween; opacity is unchanged.
 * Compile-verified only; test by pressing each wired control on device and checking cancellation on drag-away.
 */
@Composable
fun Modifier.applePressScale(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        tween(120, easing = AppleEasing.Standard),
        label = "applePressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

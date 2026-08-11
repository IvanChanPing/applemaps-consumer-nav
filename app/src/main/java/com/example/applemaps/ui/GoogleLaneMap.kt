package com.example.applemaps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * A full-screen GOOGLE MAPS surface (Maps SDK for Android via maps-compose). Google renders "Road Level Details"
 * automatically at zoom 17+ in covered cities (NYC etc.) — the painted lane markings, crosswalks and medians on
 * the road surface that the consumer Apple surface does not expose. This is Google's own rendered surface; it
 * remains a separate screen. Needs a Maps-SDK-enabled API key in the manifest,
 * allowlisted for this package + signing SHA-1 (else the map renders blank/grey).
 */
@Composable
fun GoogleLaneMap(onClose: () -> Unit) {
    val cam = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(40.6895, -73.9210), 18.5f)   // Gates Ave, Brooklyn (test area)
    }
    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cam,
            properties = MapProperties(mapType = MapType.NORMAL, isBuildingEnabled = true),
        )
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000))
                .clickable { onClose() }.padding(horizontal = 16.dp, vertical = 9.dp),
        ) { Text("Close", color = Color.White, fontSize = 16.sp) }
    }
}

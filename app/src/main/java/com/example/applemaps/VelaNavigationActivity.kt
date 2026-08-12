package com.example.applemaps

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.vela.core.model.LatLng
import app.vela.core.model.Place
import app.vela.core.model.TravelMode
import app.vela.ui.AppVisibility
import app.vela.ui.map.MapScreen
import app.vela.ui.map.MapViewModel
import app.vela.ui.settings.SettingsScreen
import app.vela.ui.theme.VelaTheme
import app.vela.ui.theme.isAppInDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Task-stack handoff from Apple planning to Vela guidance. This Activity contains no navigation
 * implementation: it renders Vela's unchanged screens and calls only Vela's public ViewModel API.
 * Finishing it restores the still-existing Apple Activity and its WebView state underneath.
 */
@AndroidEntryPoint
class VelaNavigationActivity : ComponentActivity() {
    private val vm: MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
        val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
        if (!latitude.isFinite() || !longitude.isFinite()) {
            finish()
            return
        }
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "Destination" }
        val mode = intent.getStringExtra(EXTRA_MODE).toTravelMode()

        setContent {
            VelaTheme(darkTheme = isAppInDarkTheme()) {
                var settingsOpen by remember { mutableStateOf(false) }
                var voiceLibraryOpen by remember { mutableStateOf(false) }
                Box {
                    MapScreen(
                        vm = vm,
                        onOpenSettings = { settingsOpen = true },
                        onOpenVoiceSettings = { voiceLibraryOpen = true; settingsOpen = true },
                    )
                    if (settingsOpen) {
                        SettingsScreen(
                            vm = vm,
                            onBack = { settingsOpen = false; voiceLibraryOpen = false },
                            openVoiceLibrary = voiceLibraryOpen,
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            vm.startLocation()
            vm.state.map { it.myLocation }.filterNotNull().first()
            vm.setTravelMode(mode)
            vm.setDirectionsDestination(
                Place(
                    id = "apple-handoff:$latitude,$longitude",
                    name = label,
                    location = LatLng(latitude, longitude),
                ),
            )
            vm.state.map { it.activeRoute }.filterNotNull().first()
            vm.startNav()
            vm.state.map { it.navigating }.filter { it }.first()
        }

        lifecycleScope.launch {
            var started = false
            vm.state.collect { state ->
                if (state.navigating) started = true
                if (started && (state.arrived || !state.navigating)) finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground.value = true
    }

    override fun onStop() {
        AppVisibility.foreground.value = false
        super.onStop()
    }

    companion object {
        private const val EXTRA_LATITUDE = "vela.destination.latitude"
        private const val EXTRA_LONGITUDE = "vela.destination.longitude"
        private const val EXTRA_LABEL = "vela.destination.label"
        private const val EXTRA_MODE = "vela.destination.mode"

        fun intent(context: Context, destination: com.example.applemaps.map.MapCoordinate, label: String, mode: String) =
            Intent(context, VelaNavigationActivity::class.java).apply {
                putExtra(EXTRA_LATITUDE, destination.latitude)
                putExtra(EXTRA_LONGITUDE, destination.longitude)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_MODE, mode)
            }
    }
}

private fun String?.toTravelMode(): TravelMode = when (this) {
    "Walk" -> TravelMode.WALK
    "Bike" -> TravelMode.BICYCLE
    "Transit" -> TravelMode.TRANSIT
    else -> TravelMode.DRIVE
}

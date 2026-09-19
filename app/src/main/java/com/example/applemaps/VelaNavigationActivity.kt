package com.example.applemaps

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Purpose: task-stack handoff from Apple planning to Vela guidance.
 * Invocation: receives one destination, travel mode, and optional departure offset from the Apple planner.
 * Contract: road modes auto-start after Vela resolves a route; Transit opens Vela's itinerary chooser and
 * starts only after the user chooses an itinerary. Ending guidance restores the unchanged Apple planner.
 * Verification: [TransitHandoffContractTest] guards mode branching, time handoff, and the completion gate.
 * Visual ownership: Vela renders route selection, the navigation map, voice controls, and guidance sheets.
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
        val departOffsetMin = intent.getIntExtra(EXTRA_DEPART_OFFSET_MIN, 0).coerceAtLeast(0)

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
            if (mode == TravelMode.TRANSIT && departOffsetMin > 0) {
                vm.setDirectionsTime(1, System.currentTimeMillis() / 1000L + departOffsetMin * 60L)
            }
            vm.setDirectionsDestination(
                Place(
                    id = "apple-handoff:$latitude,$longitude",
                    name = label,
                    location = LatLng(latitude, longitude),
                ),
            )
            if (mode != TravelMode.TRANSIT) {
                vm.state.map { it.activeRoute }.filterNotNull().first()
                vm.startNav()
                vm.state.map { it.navigating }.filter { it }.first()
            }
        }

        lifecycleScope.launch {
            var roadStarted = false
            var transitStarted = false
            var startedAtMs: Long? = null
            var completionGeneration = 0L
            var completionJob: Job? = null
            vm.state.collect { state ->
                if (state.navigating && !roadStarted) {
                    roadStarted = true
                    startedAtMs = SystemClock.elapsedRealtime()
                }
                if (state.transitNav != null && !transitStarted) {
                    transitStarted = true
                    startedAtMs = SystemClock.elapsedRealtime()
                }

                val terminalCandidate = roadStarted && (state.arrived || !state.navigating) ||
                    transitStarted && state.transitNav == null
                if (!terminalCandidate) {
                    completionGeneration++
                    completionJob?.cancel()
                    completionJob = null
                    return@collect
                }
                if (completionJob?.isActive == true) return@collect

                val generation = ++completionGeneration
                val activeForMs = SystemClock.elapsedRealtime() - (startedAtMs ?: SystemClock.elapsedRealtime())
                val settleMs = maxOf(GUIDANCE_FINISH_CONFIRM_MS, GUIDANCE_MIN_ACTIVE_MS - activeForMs)
                completionJob = lifecycleScope.launch {
                    delay(settleMs)
                    val settled = vm.state.value
                    val confirmedTerminal = roadStarted && (settled.arrived || !settled.navigating) ||
                        transitStarted && settled.transitNav == null
                    if (generation == completionGeneration && confirmedTerminal) finish()
                }
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
        private const val EXTRA_DEPART_OFFSET_MIN = "vela.destination.depart_offset_min"
        /** Minimum visible guidance lifetime before any completion can close this task. */
        private const val GUIDANCE_MIN_ACTIVE_MS = 1_000L
        /** Separation between the initial terminal observation and the authoritative second read. */
        private const val GUIDANCE_FINISH_CONFIRM_MS = 500L

        fun intent(
            context: Context,
            destination: com.example.applemaps.map.MapCoordinate,
            label: String,
            mode: String,
            departOffsetMin: Int = 0,
        ) =
            Intent(context, VelaNavigationActivity::class.java).apply {
                putExtra(EXTRA_LATITUDE, destination.latitude)
                putExtra(EXTRA_LONGITUDE, destination.longitude)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_DEPART_OFFSET_MIN, departOffsetMin.coerceAtLeast(0))
            }
    }
}

internal fun String?.toTravelMode(): TravelMode = when (this) {
    "Walk" -> TravelMode.WALK
    "Bike" -> TravelMode.BICYCLE
    "Transit" -> TravelMode.TRANSIT
    else -> TravelMode.DRIVE
}

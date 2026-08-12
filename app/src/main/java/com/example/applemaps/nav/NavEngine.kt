package com.example.applemaps.nav

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.applemaps.map.ConsumerMapController
import com.example.applemaps.map.MapCoordinate
import com.example.applemaps.map.RouteRepository
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import com.stadiamaps.ferrostar.core.location.toUserLocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import org.json.JSONObject
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.TripState
import uniffi.ferrostar.LaneInfo
import uniffi.ferrostar.VisualInstruction
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.createRouteFromOsrm
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

/**
 * Self-contained Ferrostar 0.53.0 turn-by-turn core engine. The copied native navigation UI consumes
 * [FerrostarCore.state], while [ConsumerMapController] presents the route and navigation arrow.
 *
 * WHAT: wraps a [FerrostarCore] that (a) fetches routes via a [CustomRouteProvider] which re-uses the
 *   existing keyless OSRM endpoint ([RouteRepository.osrmRawJson], polyline6 + steps) and parses it with
 *   uniffi's [createRouteFromOsrm]; (b) tracks the freshest available fused/GPS/network fix; (c) speaks
 *   instructions via [AndroidTtsObserver]. Exposes [state] and small accessors so the UI can follow the
 *   trip (snapped location, bearing, progress, current instruction, route geometry).
 * HOW USED: caller does `remember { NavEngine(context) }`, `onStart()`/`onDestroy()` for TTS lifecycle,
 *   `start(dest, profile, vias, routeIndex)` on GO, collects [state] to drive the consumer camera and arrow,
 *   `stop()` on exit.
 */
class NavEngine(private val context: Context) {

    private val locationProvider = ResilientNavigationLocationProvider(context)
    private val tts = AndroidTtsObserver(context)

    // Which OSRM profile the CustomRouteProvider should fetch (car/bike/foot); set by start() before getRoutes.
    @Volatile private var pendingProfile: String = "car"
    @Volatile private var pendingVias: List<MapCoordinate> = emptyList()
    @Volatile private var pendingRouteIndex: Int = 0

    private val config = NavigationControllerConfig(
        WaypointAdvanceMode.WaypointWithinRange(100.0),
        stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),        // normal step advance
        stepAdvanceDistanceToEndOfStep(30u, 32u),             // arrival step advance
        RouteDeviationTracking.StaticThreshold(15u, 50.0),
        CourseFiltering.SNAP_TO_ROUTE,
    )

    // CustomRouteProvider: raw OSRM (polyline6 + steps) -> split routes[0] + waypoints[] -> createRouteFromOsrm.
    private val routeProvider = CustomRouteProvider { userLocation, waypoints ->
        val origin = MapCoordinate(userLocation.coordinates.lat, userLocation.coordinates.lng)
        val destWp = waypoints.last()
        val dest = MapCoordinate(destWp.coordinate.lat, destWp.coordinate.lng)
        val raw = RouteRepository.osrmRawJson(origin, dest, pendingProfile, pendingVias)
            ?: throw RuntimeException("OSRM route fetch failed")
        val json = JSONObject(raw)
        val routes = json.getJSONArray("routes")
        val routeObj = routes.getJSONObject(pendingRouteIndex.coerceIn(0, routes.length() - 1))
        val waypointsArr = json.getJSONArray("waypoints")             // OSRM waypoints array
        val route = createRouteFromOsrm(
            routeObj.toString().toByteArray(),
            waypointsArr.toString().toByteArray(),
            6u,                                                       // polyline6 precision
        )
        listOf(route)
    }

    // Verified 0.53.0 secondary ctor: (CustomRouteProvider, HttpClientProvider, NavigationLocationProviding,
    // NavigationControllerConfig, ForegroundServiceManager? = null). We pass null for the FGS manager.
    val core = FerrostarCore(
        routeProvider,
        OkHttpClientProvider(OkHttpClient()),
        locationProvider,
        config,
        null,
    )

    val state: StateFlow<NavigationState> get() = core.state

    init {
        core.spokenInstructionObserver = tts
    }

    /** Start TTS (call from a lifecycle onStart / DisposableEffect). */
    fun onStart() = tts.start()

    /** Release TTS (call from onDestroy / DisposableEffect onDispose). */
    fun onDestroy() = tts.shutdown()

    /** GO: fetch a route from current GPS through [vias] to [dest] and begin turn-by-turn. */
    suspend fun start(dest: MapCoordinate, profile: String, vias: List<MapCoordinate> = emptyList(), routeIndex: Int = 0): Boolean {
        pendingProfile = profile
        pendingVias = vias
        pendingRouteIndex = routeIndex.coerceAtLeast(0)
        val loc = locationProvider.lastLocation() ?: return false
        val userLocation = loc.toUserLocation()
        val waypoints = (vias + dest).map { Waypoint(GeographicCoordinate(it.latitude, it.longitude), WaypointKind.BREAK) }
        val routes = core.getRoutes(userLocation, waypoints)
        val route = routes.firstOrNull() ?: return false
        core.startNavigation(route)
        return true
    }

    fun stop() = core.stopNavigation()

    /** Mute / unmute spoken instructions (the nav-overlay speaker toggle). */
    fun setMuted(m: Boolean) = tts.setMuted(m)

    // ---- small helper accessors reading from state.value.tripState ----
    private val navigating: TripState.Navigating?
        get() = state.value.tripState as? TripState.Navigating

    val currentInstruction: VisualInstruction? get() = navigating?.visualInstruction

    /** Lane guidance for the upcoming maneuver (Mapbox banner sub-components, parsed by Ferrostar into
     *  VisualInstructionContent.laneInfo). Sub content carries the lanes; fall back to primary. Empty when
     *  the segment has no lane data (e.g. FOSSGIS OSRM fallback, or a plain segment). */
    val currentLanes: List<LaneInfo> get() {
        val vi = currentInstruction ?: return emptyList()
        val sub = vi.subContent?.laneInfo ?: emptyList()
        return sub.ifEmpty { vi.primaryContent?.laneInfo ?: emptyList() }
    }
    val distanceToNextManeuver: Double? get() = navigating?.progress?.distanceToNextManeuver
    val distanceRemaining: Double? get() = navigating?.progress?.distanceRemaining
    val durationRemaining: Double? get() = navigating?.progress?.durationRemaining

    val snappedLocation: MapCoordinate?
        get() = navigating?.snappedUserLocation?.coordinates?.let { MapCoordinate(it.lat, it.lng) }

    /** Bearing (compass degrees) from the snapped course-over-ground, or null if unknown. */
    val bearing: Double?
        get() = navigating?.snappedUserLocation?.courseOverGround?.degrees?.toDouble()

    /** Current ground speed in metres/second (real GPS via the snapped location), or null if unknown. */
    val currentSpeedMps: Double?
        get() = navigating?.snappedUserLocation?.speed?.value

    val routeGeometry: List<MapCoordinate>
        get() = state.value.routeGeometry.map { MapCoordinate(it.lat, it.lng) }
}

/**
 * Ferrostar's stock provider binds to one preferred Android provider. This implementation observes every enabled
 * provider and chooses the freshest fix, so an enabled fused provider with no sample cannot mask a live GPS fix.
 */
private class ResilientNavigationLocationProvider(context: Context) : NavigationLocationProviding {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val appContext = context.applicationContext

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enabledProviders(): List<String> {
        val available = locationManager.allProviders.toSet()
        return buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.distinct().filter { provider ->
            provider in available && runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastLocation(): Location? {
        if (!hasPermission()) return null
        return enabledProviders().mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { location ->
            if (Build.VERSION.SDK_INT >= 17) location.elapsedRealtimeNanos else location.time * 1_000_000L
        }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMillis: Long): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        lastLocation()?.let { trySend(it) }
        val listener = LocationListener { location -> trySend(location) }
        val registered = enabledProviders().count { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    intervalMillis.coerceAtLeast(0L),
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }.isSuccess
        }
        if (registered == 0) {
            close()
            return@callbackFlow
        }
        awaitClose { runCatching { locationManager.removeUpdates(listener) } }
    }
}

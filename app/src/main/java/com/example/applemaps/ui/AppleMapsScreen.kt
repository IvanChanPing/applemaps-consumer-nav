package com.example.applemaps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.applemaps.diag.DiagLog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import com.example.applemaps.map.LookAround
import com.example.applemaps.map.ConsumerMapController
import com.example.applemaps.map.ConsumerSelectedPlace
import com.example.applemaps.map.MapCoordinate
import com.example.applemaps.map.Place
import com.example.applemaps.map.PlaceRepository
import com.example.applemaps.map.AppleBrowseClient
import com.example.applemaps.map.RouteLayer
import com.example.applemaps.map.RouteRepository
import com.example.applemaps.ui.anim.AppleEasing
import com.example.applemaps.ui.components.AppleBottomSheet
import com.example.applemaps.ui.components.AppleSheetController
import com.example.applemaps.ui.components.MapTypePicker
import com.example.applemaps.ui.components.PopoverPop
import com.example.applemaps.ui.components.AppleCardEnter
import com.example.applemaps.ui.theme.LocalAppleColors
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Root screen. Layout is machine-generated 1:1 from measured maps.apple.com geometry. Wires the map:
 * long-press drops a pin → reverse-geocode → the place card shows in the sheet (auto-expanded); the
 * map-type button opens the picker popover (Standard/Transit Apple style ↔ ESRI satellite); location recenters; search
 * expands the sheet. Close-zoom address-number taps select the nearest real address point and open an Address
 * place sheet. Every displayed-pin selection path recenters the map: long-pressed locations, address labels, and
 * tapped POI/transit icons preserve zoom, while search selection retains its established zoom level.
 * Look Around imagery appears as a lower-left map thumbnail above the sheet; its selected-place panorama prepares
 * in the background and the fullscreen viewer shares that in-flight result. Without imagery, the same position holds
 * a standalone 44dp entry control whose preview explains availability before fullscreen.
 * Place-photo taps expand from the measured thumbnail bounds into the fullscreen gallery.
 * Route stops are intentionally excluded because they use route-bound camera framing instead of the pin flow.
 * Directions and active guidance stay on that same consumer WebView: the blue route trims behind the navigation
 * arrow, heading follow pauses on a real map gesture, and the searchable "+ Add Stop" row appends a via waypoint.
 * Find Nearby and editorial Guide taps retrieve Apple Web data in the background and render it in a native tray;
 * their corresponding hidden consumer page keeps Apple's native result markers on the map.
 * Phone rendering for this consumer-guidance revision remains device-unverified until its APK is exercised.
 */
/** Preserves the former 0.16-per-60Hz-frame follow curve at every display refresh rate. */
internal fun navFollowFraction(deltaSeconds: Double): Double =
    1.0 - (1.0 - 0.16).pow(deltaSeconds.coerceIn(0.0, 0.1) * 60.0)

/** Explicit Choose Map routing keeps Transit on the Standard basemap instead of the satellite fallback. */
internal fun consumerMapType(mapType: String): String = when (mapType) {
    "Hybrid" -> "hybrid"
    "Satellite" -> "satellite"
    else -> "standard"
}

internal fun transitEnabledForMapType(mapType: String): Boolean = mapType == "Transit"

/** Returns the Add Stop search result applied at an existing row or appended at the list-end sentinel. */
internal fun updatedRouteStops(
    stops: List<MapCoordinate>,
    index: Int,
    coordinate: MapCoordinate,
): List<MapCoordinate>? = when {
    index in stops.indices -> stops.toMutableList().also { it[index] = coordinate }
    index == stops.size -> stops + coordinate
    else -> null
}

// ETA bubble text for each route (time + descriptor), in the order the routes are drawn (index 0 = selected).
private fun routeLabels(routes: List<com.example.applemaps.map.Route>): List<Pair<String, String>> = routes.mapIndexed { i, r ->
    val m = (r.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val t = if (m >= 60) "${m / 60} hr ${m % 60} min" else "$m min"
    t to (if (i == 0) "Fastest" else "Alternative")
}

@Composable
fun AppleMapsScreen(mapController: ConsumerMapController) {
    val sheetController = remember { AppleSheetController() }
    val placeSheet = remember { AppleSheetController() }
    val directionsSheet = remember { AppleSheetController() }   // directions slide up OVER the place card (sheet-over-sheet)
    var showPicker by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf("Standard") }
    var pin by remember { mutableStateOf<MapCoordinate?>(null) }
    var pinIsDropped by remember { mutableStateOf(false) } // only a deliberate long-press survives card dismissal
    var selectionGeneration by remember { mutableStateOf(0) } // rejects rich data from an older selection
    var place by remember { mutableStateOf<Place?>(null) }
    var minimized by remember { mutableStateOf(false) }        // after dismiss: balloon → dot (pin stays)
    var lastPlace by remember { mutableStateOf<Place?>(null) }
    var isStation by remember { mutableStateOf(false) }                          // transit POI -> station departures card
    var stationInfo by remember { mutableStateOf<StationInfo?>(null) }
    var gallery by remember { mutableStateOf<Pair<List<GalleryPhoto>, Int>?>(null) }   // fullscreen photo viewer
    var gallerySourceBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var placeLook by remember { mutableStateOf<LookAround?>(null) }   // Apple-first/Panoramax-fallback imagery for the current place
    var placeLookPreparation by remember { mutableStateOf<Deferred<PreparedLookAround?>?>(null) }
    var lookViewer by remember { mutableStateOf<LookAround?>(null) }  // normalized GL-branch content; may be null in APPLE mode
    var lookViewerPreparation by remember { mutableStateOf<Deferred<PreparedLookAround?>?>(null) }
    var lookPreviewOpen by remember { mutableStateOf(false) }         // floating binoculars → large map preview
    var lookPreviewTransitioning by remember { mutableStateOf(false) } // prevents remounting the map entry beneath its reverse transform
    var lookOpen by remember { mutableStateOf(false) }                 // true → fullscreen Look Around viewer open (either backend)
    var lookReturnToPreview by remember { mutableStateOf(false) }
    var lookPreviewBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var lookEntryBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var lookSourceBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var placeLookLoading by remember { mutableStateOf(false) }
    var placeLoading by remember { mutableStateOf(false) }   // show a spinner in the card body until the full data loads
    var directionsRoutes by remember { mutableStateOf<List<com.example.applemaps.map.Route>?>(null) }   // route options while in directions mode
    var directionsError by remember { mutableStateOf<String?>(null) }
    var routeRequestId by remember { mutableStateOf(0) }
    var lastDir by remember { mutableStateOf<List<com.example.applemaps.map.Route>?>(null) }   // retained so the directions sheet keeps its content while sliding out
    var selectedRoute by remember { mutableStateOf(0) }
    var dirMode by remember { mutableStateOf("Drive") }
    var navMode by remember { mutableStateOf(false) }   // turn-by-turn: consumer WebView route + native overlay
    val stops = remember { mutableStateOf<List<MapCoordinate>>(emptyList()) }   // via-waypoints added via "Add Stop"
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navEngine = remember { com.example.applemaps.nav.NavEngine(context) }   // Ferrostar TBT core (Option A)
    var navInstruction by remember { mutableStateOf<String?>(null) }             // debug: current maneuver text
    var navLanes by remember { mutableStateOf<List<uniffi.ferrostar.LaneInfo>>(emptyList()) }   // lane guidance for the upcoming maneuver
    val density = androidx.compose.ui.platform.LocalDensity.current
    val deviceScreenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var buildings3D by remember { mutableStateOf(true) }        // Choose-Map toggle; forced ON in navMode
    var trafficEnabled by rememberSaveable { mutableStateOf(false) } // Choose-Map traffic overlay; survives recreation/style changes
    var avoidTolls by remember { mutableStateOf(false) }        // Avoid pill → Valhalla use_tolls=0
    var avoidHighways by remember { mutableStateOf(false) }     // Avoid pill → Valhalla use_highways=0
    var departOffsetMin by remember { mutableStateOf(0) }       // Now pill → shifts the shown ETA base
    var infoRoute by remember { mutableStateOf<Int?>(null) }    // route (i) details page: which route index
    var navMuted by remember { mutableStateOf(false) }          // nav overlay: mute spoken instructions
    var navDistToNext by remember { mutableStateOf<Double?>(null) }   // metres to next maneuver (live)
    var navDurRemaining by remember { mutableStateOf<Double?>(null) } // whole-trip seconds remaining (live)
    var navDistRemaining by remember { mutableStateOf<Double?>(null) }// whole-trip metres remaining (live)
    var navSpeed by remember { mutableStateOf<Double?>(null) }        // current GPS speed m/s (nav speed pill)
    var navTgt by remember { mutableStateOf<MapCoordinate?>(null) }          // latest snapped follow target (from GPS fixes)
    var navFollowing by remember { mutableStateOf(true) }             // nav camera follows the puck; a user pan pauses it, recenter resumes
    var navTgtBrg by remember { mutableStateOf(0.0) }                 // latest heading target
    var navTotalDist by remember { mutableStateOf<Double?>(null) }    // route total (m) captured at start -> progress
    var navProgress by remember { mutableStateOf(0f) }
    var searchActive by remember { mutableStateOf(false) }            // full-screen search overlay open
    var browseState by remember { mutableStateOf<AppleBrowseState?>(null) }
    var browseRequestId by remember { mutableStateOf(0) }
    val browseSheet = remember { AppleSheetController() }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Place>>(emptyList()) }
    var editingStopIndex by remember { mutableStateOf<Int?>(null) }   // existing index replaces; stops.size appends
    LaunchedEffect(searchQuery) {                                     // debounced keyless forward-geocode (Nominatim)
        if (searchQuery.isBlank()) { searchResults = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(280)
        val c = mapController.center()
        searchResults = PlaceRepository.searchPlaces(searchQuery, c.latitude, c.longitude)
    }
    DisposableEffect(Unit) { navEngine.onStart(); onDispose { navEngine.onDestroy() } }   // TTS lifecycle
    var locationGranted by remember {
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { locationGranted = it }
    LaunchedEffect(Unit) { if (!locationGranted) permLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) }
    // per-category balloon: class -> icon PNG, icon -> its color (so a POI balloon uses that icon + its color)
    val poiClassIcon = remember { runCatching { org.json.JSONObject(context.assets.open("poi_class_icon.json").bufferedReader().use { it.readText() }) }.getOrNull() }
    val poiColors = remember { runCatching { org.json.JSONObject(context.assets.open("poi_colors.json").bufferedReader().use { it.readText() }) }.getOrNull() }
    var pinTint by remember { mutableStateOf(Color(0xFFFF3B30)) }
    var pinFace by remember { mutableStateOf<ImageBitmap?>(null) }
    var pinLabel by remember { mutableStateOf("Marked Location") }
    fun applyCategoryPin(category: String) {
        val n = category.lowercase()
        val cls = when {
            "dermat" in n || "doctor" in n || "clinic" in n -> "doctors"
            "pharmacy" in n -> "pharmacy"
            "hospital" in n || "health" in n -> "hospital"
            "fast food" in n -> "fast_food"
            "convenience" in n -> "convenience"
            "supermarket" in n -> "supermarket"
            "grocery" in n -> "grocery"
            "restaurant" in n || "dining" in n || "food" in n -> "restaurant"
            "hotel" in n || "lodging" in n -> "hotel"
            "museum" in n -> "museum"
            "store" in n || "shop" in n -> "shop"
            else -> n.replace(' ', '_')
        }
        val icon = (poiClassIcon?.optString(cls) ?: "").ifBlank { "poi_177" }
        val hex = poiColors?.optString(icon, "#9aa0a6") ?: "#9aa0a6"
        pinTint = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF9AA0A6))
        pinFace = runCatching { context.assets.open("poi/$icon.png").use { android.graphics.BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
    }
    /**
     * displayedPinRecenter — camera motion invoked by every action that produces the visible map balloon.
     * Test with a long-press, POI/transit icon tap, and normal search result. Source/compile verified only;
     * real on-device framing remains unverified because this project is barred from the shared emulator.
     */
    fun recenterOnPin(location: MapCoordinate, zoom: Double? = null) {
        if (!PlaceRepository.isValidMapCoordinate(location.latitude, location.longitude)) {
            DiagLog.log("MAPCAM", "rejectInvalid", "lat=${location.latitude}", "lon=${location.longitude}")
            return
        }
        val sheetPad = maxOf(placeSheet.offsetPx, with(density) { 320.dp.toPx() }).coerceAtMost(deviceScreenHeightPx * 0.68f)
        mapController.centerOn(location, zoom = zoom, bottomPaddingPx = sheetPad.roundToInt())
        DiagLog.log("MAPCAM", "select", "lat=${location.latitude}", "lon=${location.longitude}", "zoom=${zoom ?: -1.0}", "bottomPad=${sheetPad.roundToInt()}")
    }
    fun openLookAround(
        target: Place,
        sourceBounds: androidx.compose.ui.geometry.Rect?,
        returnToPreview: Boolean = false,
    ) {
        lookViewer = placeLook
        lookViewerPreparation = placeLookPreparation
        lookSourceBounds = sourceBounds
        lookReturnToPreview = returnToPreview
        lookPreviewOpen = false
        lookOpen = true
        DiagLog.log(
            "LOOKWEB", "viewerTrigger", "backend=direct",
            "coverage=${if (placeLook != null) 1 else 0}", "provider=${placeLook?.provider ?: "none"}",
            "lat=${target.lat}", "lon=${target.lon}",
        )
    }
    if (place != null) lastPlace = place
    if (directionsRoutes != null) lastDir = directionsRoutes

    // X or Back: slide the card down. TWO collapse behaviors (per the pin-restore bug):
    //  · Marked Location (long-press, no face) → the balloon shrinks to the small DOT, which stays.
    //  · POI icon (tapped category marker, has a face) → REMOVE our overlay entirely so the tile's own POI
    //    icon shows again ("back to the icon") — collapsing a POI to a circular dot was the wrong behavior.
    fun dismissCard() {
        selectionGeneration++
        place = null; isStation = false; placeLoading = false; directionsRoutes = null; directionsError = null; stops.value = emptyList()
        if (!pinIsDropped) { pin = null; pinFace = null; pinLabel = "Marked Location"; minimized = false } else { minimized = true }
        RouteLayer.clear(mapController)
    }

    // Compute routes origin(=user loc)→[stops]→dest and draw them; used by Directions, the mode toggle, and Add Stop.
    fun recomputeDirections() {
        val dest = (place ?: lastPlace) ?: return
        val requestId = ++routeRequestId
        directionsRoutes = emptyList()
        directionsError = null
        RouteLayer.clear(mapController)
        scope.launch {
            runCatching {
                val d = MapCoordinate(dest.lat, dest.lon)
                val o = mapController.lastKnownLocation()
                if (o == null) {
                    if (requestId == routeRequestId) directionsError = "Current location is required to calculate directions."
                    DiagLog.log("DIRECTIONS", "event=origin_missing")
                    return@launch
                }
                // Avoid set → route via Valhalla (honors use_tolls/use_highways keyless); else the normal chain.
                val routes = if (avoidTolls || avoidHighways)
                    RouteRepository.valhallaRoute(o, d, osrmProfile(dirMode), avoidTolls, avoidHighways, stops.value)
                        ?: RouteRepository.osrmRoute(o, d, osrmProfile(dirMode), stops.value)
                else if (dirMode == "Drive")
                    RouteRepository.mapboxTrafficRoutes(o, d, stops.value)
                        ?: RouteRepository.computeRoutes(o, d, modeApi(dirMode), stops.value)
                        ?: RouteRepository.osrmRoute(o, d, osrmProfile(dirMode), stops.value)
                else
                    RouteRepository.computeRoutes(o, d, modeApi(dirMode), stops.value) ?: RouteRepository.osrmRoute(o, d, osrmProfile(dirMode), stops.value)
                if (requestId != routeRequestId) {
                    DiagLog.log("DIRECTIONS", "event=stale_result", "request=$requestId", "current=$routeRequestId")
                    return@launch
                }
                if (!routes.isNullOrEmpty()) {
                    directionsRoutes = routes; selectedRoute = 0
                    RouteLayer.drawRoutes(mapController, routes, labels = routeLabels(routes))
                    DiagLog.log("DIRECTIONS", "event=routes_ready", "request=$requestId", "count=${routes.size}", "stops=${stops.value.size}")
                } else {
                    directionsError = "No route was found for the selected options."
                    DiagLog.log("DIRECTIONS", "event=no_route", "request=$requestId")
                }
            }.onFailure {
                if (requestId == routeRequestId) directionsError = "Directions failed: ${it.javaClass.simpleName}"
                DiagLog.log("DIRECTIONS", "event=error", "request=$requestId", "type=${it.javaClass.simpleName}")
            }
        }
    }

    fun handleConsumerSelection(selection: ConsumerSelectedPlace) {
        if (navMode) return
        browseState = null
        browseRequestId++
        if (selection.source == "app-marker") {
            val retained = place ?: lastPlace
            if (retained != null) {
                pin = selection.coordinate
                minimized = false
                place = retained
                placeLoading = false
                recenterOnPin(selection.coordinate)
                return
            }
        }
        selectionGeneration++
        val requestGeneration = selectionGeneration
        val location = selection.coordinate
        val title = selection.title.ifBlank { "Marked Location" }
        val category = selection.category.ifBlank { "Apple place" }
        pinIsDropped = false
        pin = location
        minimized = false
        applyCategoryPin(category)
        pinLabel = title
        recenterOnPin(location)
        val transit = listOf("transit", "station", "railway", "subway", "bus", "tram", "ferry", "rail")
            .any { category.contains(it, ignoreCase = true) }
        isStation = transit
        if (transit) stationInfo = StationInfo(title, category, listOf(
            Departure("F Train", "Downtown to Coney Island", "1, 8"),
            Departure("F Train", "Uptown to Jamaica–179 St", "3, 11"),
            Departure("L Train", "To Canarsie–Rockaway Pkwy", "2, 9"),
            Departure("Q Train", "To 96 St–2 Av", "5, 14"),
        ))
        place = Place(title, category, "", "", location.latitude, location.longitude)
        placeLoading = true
        scope.launch {
            val real = PlaceRepository.fetchApplePlace(title, location.latitude, location.longitude)
                ?: PlaceRepository.fetchGooglePlace(title, location.latitude, location.longitude)
            if (selectionGeneration == requestGeneration && place?.lat == location.latitude && place?.lon == location.longitude) {
                if (real != null) place = real
                else {
                    val resolved = PlaceRepository.reverseGeocode(location.latitude, location.longitude)
                    place = place?.copy(locality = resolved.locality, address = resolved.address)
                }
                placeLoading = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GeneratedAppleLayout(
            mapController = mapController,
            sheetController = sheetController,
            pin = pin,
            showBalloon = pin != null && !minimized,
            pinTint = pinTint,
            pinFace = pinFace,
            pinLabel = pinLabel,
            baseSheetVisible = place == null && !navMode && browseState == null,
            chromeVisible = !navMode,
            buildings3DEnabled = buildings3D || navMode,    // user preference; navigation intentionally forces 3D on
            locationEnabled = locationGranted && !navMode,  // hide the blue location dot in nav (the arrow puck replaces it)
            onPlaceSelected = ::handleConsumerSelection,
            onMapGesture = { if (navMode) navFollowing = false },

            onMapLongClick = onLong@{ ll ->
                if (navMode) return@onLong   // no dropping pins while navigating
                selectionGeneration++
                val requestGeneration = selectionGeneration
                pinIsDropped = true
                pin = ll; minimized = false; isStation = false
                pinTint = Color(0xFFFF3B30); pinFace = null; pinLabel = "Marked Location"   // long-press = red Marked Location
                place = Place("Marked Location", "Location", "", "", ll.latitude, ll.longitude)
                placeLoading = true
                recenterOnPin(ll)
                scope.launch {
                    val resolved = PlaceRepository.reverseGeocode(ll.latitude, ll.longitude)
                    if (selectionGeneration == requestGeneration) { place = resolved; placeLoading = false }
                }
            },
            onMapType = { if (showPicker) { showPicker = false; sheetController.goTo(0) } else { showPicker = true; sheetController.goTo(1) } },
            onLocate = {
                val location = mapController.lastKnownLocation()
                mapController.centerOn(location ?: MapCoordinate(37.7749, -122.4194), zoom = if (location != null) 15.0 else 14.0)
            },
            onSearch = { searchActive = true },
            onCategory = { label ->
                if (mapController.showHomeCategory(label)) {
                    val requestId = ++browseRequestId
                    browseState = AppleBrowseState.Loading(label)
                    scope.launch {
                        val center = mapController.center()
                        val result = try {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                AppleBrowseClient.category(label, center.latitude, center.longitude)
                            }
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            DiagLog.log("APPLEBROWSE", "event=category_failed", "type=${error.javaClass.simpleName}")
                            null
                        }
                        if (requestId == browseRequestId) browseState = result?.let(AppleBrowseState::Category)
                            ?: AppleBrowseState.Error(label, "Apple Maps did not return nearby places.")
                    }
                }
            },
            onGuide = { curatedId ->
                if (mapController.showGuide(curatedId)) {
                    val requestId = ++browseRequestId
                    browseState = AppleBrowseState.Loading("Guide")
                    scope.launch {
                        val result = try {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                AppleBrowseClient.guide(curatedId)
                            }
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            DiagLog.log("APPLEBROWSE", "event=guide_failed", "type=${error.javaClass.simpleName}")
                            null
                        }
                        if (requestId == browseRequestId) browseState = result?.let(AppleBrowseState::Guide)
                            ?: AppleBrowseState.Error("Guide", "Apple Maps did not return this Guide.")
                    }
                }
            },
        )

        // lookAroundMapEntry — imagery uses Apple's lower-left photo thumbnail; absent coverage uses the
        // standalone 44dp binocular control. Both stay wholly on the map, 10dp above the active sheet.
        if (place != null && directionsRoutes == null && !navMode && !lookOpen && !lookPreviewOpen && !lookPreviewTransitioning && gallery == null) {
            val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
            val reportedHeight = if (directionsRoutes != null) directionsSheet.offsetPx else placeSheet.offsetPx
            val activeSheetHeight = reportedHeight.takeIf { it.isFinite() && it in 1f..screenHeightPx }
                ?: with(density) { 320.dp.toPx() }
            val navigationInset = WindowInsets.navigationBars.getBottom(density)
            val entryModifier = Modifier.align(Alignment.BottomStart).zIndex(3f).offset {
                IntOffset(
                    x = with(density) { 10.dp.roundToPx() },
                    y = -navigationInset - activeSheetHeight.roundToInt() - with(density) { 10.dp.roundToPx() },
                )
            }
            val imagery = placeLook
            if (imagery != null) {
                LookAroundMapThumbnail(
                    look = imagery,
                    onBoundsChanged = { lookEntryBounds = it },
                    onClick = {
                        place?.let {
                            lookReturnToPreview = false
                            lookPreviewOpen = true
                            DiagLog.log("LOOKWEB", "previewOpen", "coverage=1", "loading=${if (placeLookLoading) 1 else 0}")
                        }
                    },
                    modifier = entryModifier,
                )
            } else {
                LookAroundFloatingButton(
                    onBoundsChanged = { lookEntryBounds = it },
                    onClick = {
                        place?.let {
                            lookReturnToPreview = false
                            lookPreviewOpen = true
                            DiagLog.log("LOOKWEB", "previewOpen", "coverage=0", "loading=${if (placeLookLoading) 1 else 0}")
                        }
                    },
                    modifier = entryModifier,
                )
            }
        }

        val closeLookPreview = {
            lookReturnToPreview = false
            lookPreviewTransitioning = true
            lookPreviewOpen = false
            DiagLog.log("LOOKWEB", "previewClose")
        }
        LookAroundEntryPreviewTransform(
            visible = lookPreviewOpen && !lookOpen,
            sourceBounds = lookEntryBounds,
            settleAtTarget = lookReturnToPreview,
            onIdle = { expanded -> if (!expanded) lookPreviewTransitioning = false },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(if (lookReturnToPreview) 0f else 4f)
                .statusBarsPadding().padding(horizontal = 10.dp, vertical = 20.dp),
        ) {
            LookAroundPreviewCard(
                look = placeLook,
                loading = placeLookLoading,
                onExpand = { place?.let { openLookAround(it, lookPreviewBounds, returnToPreview = true) } },
                onClose = closeLookPreview,
                onBoundsChanged = { lookPreviewBounds = it },
            )
        }
        // lookPreviewTransitioning is retired by the transform's onIdle above — the transition's own clock,
        // not a fixed delay, so the map thumbnail can never remount a frame early or late under the card.

        // Two INDEPENDENT sibling sheets, each a copy of the same AppleBottomSheet with its own controller: the
        // place CARD, and the DIRECTIONS planner that slides up OVER it. Both are direct children of the root Box
        // (not nested), so dismissing the top one leaves the other EXACTLY where it was — neither sheet drives the
        // other's position or size. This is the "separate sheet for every thing, stacked" model.
        val cardPlace = place ?: lastPlace
        val startDirections: () -> Unit = { stops.value = emptyList(); recomputeDirections() }
        val selectRoute: (Int) -> Unit = { idx ->
            selectedRoute = idx
            val rs = directionsRoutes
            if (rs != null) {
                val ordered = listOf(rs[idx]) + rs.filterIndexed { i, _ -> i != idx }
                RouteLayer.drawRoutes(mapController, ordered, fitAndReveal = false, labels = routeLabels(ordered))
            }
        }
        val exitDirections: () -> Unit = { directionsRoutes = null; directionsError = null; stops.value = emptyList(); RouteLayer.clear(mapController) }
        // GO keeps the consumer Apple route alive. The native sheets hide while the same WebView receives progress,
        // navigation-arrow, heading-follow, gesture-pause, recenter, and compass updates beneath NavOverlay.
        val startNav: () -> Unit = {
            val rs = directionsRoutes
            if (rs != null && rs.isNotEmpty()) {
                val selectedIndex = selectedRoute.coerceIn(0, rs.lastIndex)
                val route = rs[selectedIndex].points
                if (route.size >= 2) {
                    scope.launch {
                        directionsError = null
                        val started = runCatching { navEngine.start(route.last(), osrmProfile(dirMode), stops.value, selectedIndex) }
                            .onFailure { DiagLog.log("DIRECTIONS", "event=nav_start_error", "type=${it.javaClass.simpleName}") }
                            .getOrDefault(false)
                        if (started) {
                            val ordered = listOf(rs[selectedIndex]) + rs.filterIndexed { i, _ -> i != selectedIndex }
                            RouteLayer.drawRoutes(mapController, ordered, fitAndReveal = false, labels = routeLabels(ordered))
                            navMode = true
                            navFollowing = true   // each nav session starts following the puck
                            navProgress = 0f
                            DiagLog.log("DIRECTIONS", "event=nav_started", "route=$selectedIndex", "stops=${stops.value.size}")
                        } else {
                            directionsError = "Navigation needs a current GPS fix before GO can start."
                            DiagLog.log("DIRECTIONS", "event=nav_start_failed", "route=$selectedIndex")
                        }
                    }
                }
            }
        }
        // PLACE CARD sheet — the base of the stack; Back/X pops it (does NOT exit the app).
        AnimatedVisibility(
            visible = place != null && !navMode,
            enter = slideInVertically(tween(420, easing = AppleEasing.ExpoOut)) { it },
            exit = slideOutVertically(tween(520, easing = AppleEasing.ExpoOut)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (cardPlace != null) {
                val st = stationInfo   // local capture so the null-check smart-casts (no !! on the delegated state)
                AppleBottomSheet(
                    peekHeight = 82.dp,
                    topRadius = 14.dp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    controller = placeSheet,
                    ceilingProvider = { if (directionsRoutes != null) directionsSheet.offsetPx else 0f },   // never rise above the directions sheet
                    header = {
                        AppleCardEnter(cardPlace.name) { when {   // P1 3.2: re-play the card enter when a NEW place is selected
                            isStation && st != null -> StationCardHeader(st, onClose = { dismissCard() })
                            else -> PlaceCardHeader(cardPlace, onDirections = startDirections, onClose = { dismissCard() })
                        } }
                    },
                    body = {
                        AppleCardEnter(cardPlace.name) { when {   // P1 3.2: body fades/rises on a NEW place too
                            isStation && st != null -> StationCardBody(st)
                            else -> PlaceCardBody(cardPlace, onDirections = startDirections, loading = placeLoading,
                                distanceMiles = run {   // miles from the user's real location to the place → DISTANCE ribbon column
                                    mapController.lastKnownLocation()?.let { loc ->
                                        val d = FloatArray(1)
                                        android.location.Location.distanceBetween(loc.latitude, loc.longitude, cardPlace.lat, cardPlace.lon, d)
                                        (d[0] / 1609.34).toDouble()
                                    }
                                },
                                onPhotoClick = { i, bounds ->
                                gallerySourceBounds = bounds
                                gallery = (if (cardPlace.photoUrls.isNotEmpty()) cardPlace.photoUrls.mapIndexed { k, u -> GalleryPhoto(u, cardPlace.photoLabels.getOrNull(k), cardPlace.photoAttributionUrls.getOrNull(k), "Google Maps".takeIf { cardPlace.ratingSource == "Google" }) }
                                           else cardPlace.photoLabels.map { GalleryPhoto("", it) }) to i
                                DiagLog.log("LOOKWEB", "photoExpand", "index=$i", "hasBounds=${if (bounds != null) 1 else 0}")
                            })
                        } }
                    },
                )
            }
        }
        // DIRECTIONS over-sheet — an INDEPENDENT sibling (NOT nested in the card): same peek/detents, its own
        // controller, slides up OVER the card and back down on dismiss; the card behind stays put. dr falls back to
        // lastDir so the content holds while it slides away.
        AnimatedVisibility(
            visible = directionsRoutes != null && !navMode,
            enter = slideInVertically(tween(420, easing = AppleEasing.ExpoOut)) { it },
            exit = slideOutVertically(tween(520, easing = AppleEasing.ExpoOut)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (cardPlace != null) {
                AppleBottomSheet(
                    peekHeight = 82.dp,
                    topRadius = 14.dp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    controller = directionsSheet,
                    initialDetent = maxOf(1, placeSheet.restingIndex),   // open AT the card's current height (never below it)
                    header = {
                        DirectionsHeader(
                            onShare = {
                                val routePlace = cardPlace
                                val text = buildString {
                                    append("Directions to ").append(routePlace.name)
                                    if (routePlace.address.isNotBlank()) append("\n").append(routePlace.address)
                                }
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                                runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share directions")) }
                            },
                            onClose = exitDirections,
                        )
                    },
                    body = {
                        val dr = directionsRoutes ?: lastDir
                        if (dr != null) DirectionsBody(cardPlace, dr, selectedRoute, dirMode,
                            onMode = { dirMode = it; recomputeDirections() }, onSelect = selectRoute, onGo = startNav,
                            stopCount = stops.value.size, routeError = directionsError,
                            // addStopRow — blue "+ Add Stop" row in the iOS-style From/To card; opens search and appends.
                            onAddStop = { editingStopIndex = stops.value.size; searchActive = true },
                            onRemoveStop = { i -> stops.value = stops.value.toMutableList().also { if (i in it.indices) it.removeAt(i) }; recomputeDirections() },
                            onEditStop = { i -> editingStopIndex = i; searchActive = true },
                            onReorderStop = { from, to -> stops.value = stops.value.toMutableList().also { if (from in it.indices) { val s = it.removeAt(from); it.add(to.coerceIn(0, it.size), s) } }; recomputeDirections() },
                            departOffsetMin = departOffsetMin, onDepart = { departOffsetMin = it },
                            avoidTolls = avoidTolls, avoidHighways = avoidHighways,
                            onAvoid = { t, h -> avoidTolls = t; avoidHighways = h; recomputeDirections() },
                            onInfo = { infoRoute = it })
                    },
                )
            }
        }
        val closeBrowse = {
            browseRequestId++
            browseState = null
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = browseState != null && place == null && !navMode,
            enter = slideInVertically(tween(420, easing = AppleEasing.ExpoOut)) { it },
            exit = slideOutVertically(tween(420, easing = AppleEasing.ExpoOut)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            browseState?.let { state ->
                AppleBottomSheet(
                    peekHeight = 82.dp,
                    topRadius = 14.dp,
                    controller = browseSheet,
                    initialDetent = 1,
                    header = { AppleBrowseHeader(state, closeBrowse) },
                    body = {
                        AppleBrowseBody(state) { result ->
                            handleConsumerSelection(
                                ConsumerSelectedPlace(
                                    id = result.id,
                                    title = result.place.name,
                                    category = result.place.category,
                                    coordinate = MapCoordinate(result.place.lat, result.place.lon),
                                    source = "apple-browse",
                                ),
                            )
                        }
                    },
                )
            }
        }
        LaunchedEffect(browseState != null) { if (browseState != null) browseSheet.goTo(1) }

        BackHandler(enabled = browseState != null || place != null || directionsRoutes != null || navMode) {
            when {
                browseState != null -> closeBrowse()
                navMode -> {   // leave turn-by-turn: flatten the map back to the overview, restore the planner
                    navMode = false
                    mapController.clearNavigation()
                    mapController.centerOn(mapController.center(), rotation = 0.0)
                    placeSheet.goTo(1)
                }
                directionsRoutes != null -> { directionsRoutes = null; directionsError = null; RouteLayer.clear(mapController) }
                else -> dismissCard()
            }
        }
        LaunchedEffect(place) { if (place != null) placeSheet.goTo(1) }
        LaunchedEffect(place?.lat, place?.lon) {   // direct Apple lookup with Panoramax fallback
            placeLookPreparation?.cancel()
            placeLookPreparation = null
            lookPreviewOpen = false
            lookPreviewTransitioning = false
            lookPreviewBounds = null
            lookEntryBounds = null
            placeLook = null
            val p = place
            placeLookLoading = p != null
            if (p != null) {
                val found = PlaceRepository.lookAround(p.lat, p.lon)
                placeLook = found
                placeLookLoading = false
                if (found != null) {
                    placeLookPreparation = scope.async { prepareLookAround(context, found) }
                }
                DiagLog.log(
                    "LOOKWEB", "coverage", "found=${if (found != null) 1 else 0}",
                    "provider=${found?.provider ?: "none"}", "distance=${found?.distanceMeters ?: -1}",
                    "lat=${p.lat}", "lon=${p.lon}",
                )
            }
        }
        LaunchedEffect(directionsRoutes != null) { if (directionsRoutes != null) directionsSheet.offsetPx = Float.MAX_VALUE }   // neutralize any stale ceiling from a prior session so the card isn't yanked down on reopen (height comes from initialDetent)
        BackHandler(enabled = gallery != null) { gallery = null }
        BackHandler(enabled = lookPreviewOpen && !lookOpen) { closeLookPreview() }
        BackHandler(enabled = infoRoute != null) { infoRoute = null }
        // "Choose Map" modal. Transition (traced feel): opening moved the base card to its MID detent (above);
        // the picker then rises up from the bottom edge to cover the map; closing reverses both. Scrim fades.
        val closePicker = { showPicker = false; sheetController.goTo(0) }
        AnimatedVisibility(visible = showPicker,
            enter = androidx.compose.animation.fadeIn(tween(280)), exit = androidx.compose.animation.fadeOut(tween(240)),
            modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x22000000)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { closePicker() })
        }
        AnimatedVisibility(visible = showPicker,
            enter = slideInVertically(tween(420, easing = AppleEasing.ExpoOut)) { it },
            exit = slideOutVertically(tween(300, easing = AppleEasing.ExpoOut)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.navigationBarsPadding()) {
                MapTypePicker(selected = mapType, buildings3D = buildings3D, trafficEnabled = trafficEnabled,
                    onToggle3D = { buildings3D = it }, onToggleTraffic = { trafficEnabled = it }, onClose = { closePicker() }) { type ->
                    mapType = type
                    mapController.setMapType(consumerMapType(type))
                    closePicker()
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(LocalAppleColors.current.fillTertiary),
        )

        // Fullscreen photo gallery — geometry-only thumbnail→viewer transform; galShown caches the exit frame.
        val galShown = remember { mutableStateOf<Pair<List<GalleryPhoto>, Int>?>(null) }
        gallery?.let { galShown.value = it }
        LookAroundTileTransform(
            visible = gallery != null,
            sourceBounds = gallerySourceBounds,
            sourceCornerRadius = 16.dp,
        ) {
            galShown.value?.let { (photos, idx) -> PhotoGalleryViewer(photos, idx, onClose = { gallery = null }) }
        }

        // Fullscreen Look Around viewer (tap the card tile) — geometry-only tile→fullscreen transform.
        val lookShown = remember { mutableStateOf<LookAround?>(null) }
        lookViewer?.let { lookShown.value = it }
        val closeLook = {
            lookOpen = false
            if (lookReturnToPreview) lookPreviewOpen = true
        }
        LaunchedEffect(lookOpen, lookPreviewOpen, lookReturnToPreview) {
            if (!lookOpen && lookPreviewOpen && lookReturnToPreview) {
                kotlinx.coroutines.delay(320)
                lookReturnToPreview = false
            }
        }
        LookAroundTileTransform(visible = lookOpen, sourceBounds = lookSourceBounds, sourceCornerRadius = 16.dp) {
            Box(Modifier.fillMaxSize()) {
                lookShown.value?.let {
                    LookAroundViewer(it, preparation = lookViewerPreparation, onClose = closeLook)
                } ?: LookAroundUnavailableViewer(onClose = closeLook)
            }
        }
        BackHandler(enabled = lookOpen) { closeLook() }

        LaunchedEffect(trafficEnabled) { mapController.setTrafficEnabled(trafficEnabled) }
        LaunchedEffect(mapType) { mapController.setMapType(consumerMapType(mapType)) }

        // Route (i) details page — a SLIDE-UP bottom sheet (scrim fades, panel rises) listing the maneuvers.
        // infoShown caches the last route so the panel still renders during the slide-DOWN exit (infoRoute=null).
        val infoShown = remember { mutableStateOf<Pair<com.example.applemaps.map.Route, Int>?>(null) }
        run { val ir = infoRoute; val dr = directionsRoutes; if (ir != null && dr != null && ir in dr.indices) infoShown.value = dr[ir] to ir }
        AnimatedVisibility(visible = infoRoute != null, enter = androidx.compose.animation.fadeIn(tween(220)),
            exit = androidx.compose.animation.fadeOut(tween(200)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x33000000)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { infoRoute = null })
        }
        AnimatedVisibility(visible = infoRoute != null,
            enter = slideInVertically(tween(380, easing = AppleEasing.ExpoOut)) { it },
            exit = slideOutVertically(tween(300, easing = AppleEasing.ExpoOut)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)) {
            infoShown.value?.let { (route, idx) ->
                Box(Modifier.navigationBarsPadding().fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(Color(0xFFF2F2F2)).padding(top = 14.dp)) {
                    RouteInfoSheet(route, idx, departOffsetMin, onClose = { infoRoute = null })
                }
            }
        }

        // TURN-BY-TURN state collector: per FerrostarCore.state, set the follow TARGET + banner + route trim.
        LaunchedEffect(navMode) {
            if (!navMode) {
                navInstruction = null
                mapController.clearNavigation()
                navEngine.stop()
                navTgt = null; navTotalDist = null; navProgress = 0f
                return@LaunchedEffect
            }
            navEngine.state.collect {
                val snapped = navEngine.snappedLocation ?: return@collect
                navTgt = snapped
                navEngine.bearing?.let { navTgtBrg = it }
                navInstruction = navEngine.currentInstruction?.primaryContent?.text
                navLanes = navEngine.currentLanes
                navDistToNext = navEngine.distanceToNextManeuver
                navDurRemaining = navEngine.durationRemaining
                navDistRemaining = navEngine.distanceRemaining
                navSpeed = navEngine.currentSpeedMps
                // vanishing route line: trim the traveled part behind the puck (progress = 1 − remaining/total).
                navEngine.distanceRemaining?.let { remain ->
                    if (navTotalDist == null || remain > navTotalDist!!) navTotalDist = remain
                    val t = (1.0 - remain / (navTotalDist ?: remain)).coerceIn(0.0, 1.0)
                    navProgress = t.toFloat()
                    RouteLayer.setProgress(mapController, navProgress)
                }
            }
        }

        // TURN-BY-TURN SMOOTH follow: ease the camera + arrow toward each new target at display rate. The elapsed-
        // time fraction preserves the old 60Hz curve on high-refresh screens. Once the pose converges, duplicate
        // consumer-renderer route/puck/camera mutations pause until the target or follow mode changes.
        LaunchedEffect(navMode) {
            if (!navMode) return@LaunchedEffect
            var initialized = false
            var dispLat = 0.0
            var dispLon = 0.0
            var dispBrg = navTgtBrg
            var lastFrameNanos = 0L
            var renderedLat = Double.NaN
            var renderedLon = Double.NaN
            var renderedBrg = Double.NaN
            var wasFollowing = navFollowing
            while (true) {
                val frameNanos = withFrameNanos { it }
                val deltaSeconds = if (lastFrameNanos == 0L) 1.0 / 60.0
                    else (frameNanos - lastFrameNanos).toDouble() / 1_000_000_000.0
                lastFrameNanos = frameNanos
                val tgt = navTgt ?: continue
                val f = navFollowFraction(deltaSeconds)
                if (!initialized) {
                    dispLat = tgt.latitude
                    dispLon = tgt.longitude
                    dispBrg = navTgtBrg
                    initialized = true
                } else {
                    val latDelta = tgt.latitude - dispLat
                    val lonDelta = tgt.longitude - dispLon
                    dispLat = if (abs(latDelta) <= 1e-7) tgt.latitude else dispLat + latDelta * f
                    dispLon = if (abs(lonDelta) <= 1e-7) tgt.longitude else dispLon + lonDelta * f
                }
                var d = navTgtBrg - dispBrg
                while (d > 180) d -= 360; while (d < -180) d += 360
                dispBrg = if (abs(d) <= 0.05) navTgtBrg else dispBrg + d * f

                val poseChanged = dispLat != renderedLat || dispLon != renderedLon ||
                    dispBrg != renderedBrg
                val followChanged = navFollowing != wasFollowing
                if (poseChanged || (navFollowing && followChanged)) {
                    val coordinate = MapCoordinate(dispLat, dispLon)
                    mapController.setNavigationPose(coordinate, dispBrg, navFollowing)
                    renderedLat = dispLat
                    renderedLon = dispLon
                    renderedBrg = dispBrg
                }
                wasFollowing = navFollowing
            }
        }

        // Full turn-by-turn overlay (built from the Google Immersive reference): banner + FABs + Exit bar.
        // Replaces the normal sheet + map chrome while navigating.
        // While navigating, a USER map gesture (pan/zoom/rotate) pauses auto-follow so you can look around; the
        // recenter FAB resumes it. The consumer renderer reports only real user map gestures.

        AnimatedVisibility(visible = navMode, modifier = Modifier.fillMaxSize(),
            enter = androidx.compose.animation.fadeIn(tween(320)), exit = androidx.compose.animation.fadeOut(tween(260))) {
            NavOverlay(
                instruction = navInstruction,
                lanes = navLanes,
                distanceToNext = navDistToNext,
                durationRemaining = navDurRemaining,
                distanceRemaining = navDistRemaining,
                speedMps = navSpeed,
                muted = navMuted,
                onMute = { navMuted = !navMuted; navEngine.setMuted(navMuted) },
                onRecenter = {
                    navFollowing = true   // resume auto-follow after the user panned away
                    navEngine.snappedLocation?.let { location ->
                        mapController.setNavigationPose(location, navEngine.bearing ?: navTgtBrg, follow = true)
                    }
                },
                onExit = {
                    navMode = false
                    mapController.clearNavigation()
                    mapController.centerOn(mapController.center(), rotation = 0.0)
                    placeSheet.goTo(1)
                },
            )
        }

        // P1 1.1: search overlay fades/slides in (250ms = extracted SearchField duration) instead of popping
        AnimatedVisibility(visible = searchActive, modifier = Modifier.fillMaxSize(),
            enter = androidx.compose.animation.fadeIn(tween(250, easing = AppleEasing.Standard)) +
                slideInVertically(tween(250, easing = AppleEasing.ExpoOut)) { it / 8 },
            exit = androidx.compose.animation.fadeOut(tween(200, easing = AppleEasing.EaseIn))) {
            SearchOverlay(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                results = searchResults,
                onSelect = onSelect@{ p ->
                    if (!PlaceRepository.isValidMapCoordinate(p.lat, p.lon)) {
                        DiagLog.log("MAPCAM", "rejectSearch", "name=${p.name}", "lat=${p.lat}", "lon=${p.lon}")
                        return@onSelect
                    }
                    searchActive = false; searchQuery = ""
                    val idx = editingStopIndex
                    if (idx != null) {                              // existing index replaces; list size appends
                        editingStopIndex = null
                        val updated = updatedRouteStops(stops.value, idx, MapCoordinate(p.lat, p.lon))
                        if (updated != null) {
                            stops.value = updated
                            directionsSheet.goTo(1)
                            recomputeDirections()
                        } else DiagLog.log("DIRECTIONS", "event=invalid_stop_index", "index=$idx", "size=${stops.value.size}")
                    } else {                                        // normal: drop pin + open its place card
                        selectionGeneration++
                        val requestGeneration = selectionGeneration
                        val ll = MapCoordinate(p.lat, p.lon)
                        pinIsDropped = false
                        pin = ll; minimized = false; isStation = false
                        applyCategoryPin(p.category); pinLabel = p.name
                        place = p; placeLoading = true
                        recenterOnPin(ll, 16.0)
                        scope.launch {
                            val real = PlaceRepository.fetchApplePlace(p.name, p.lat, p.lon)
                                ?: PlaceRepository.fetchGooglePlace(p.name, p.lat, p.lon)
                            if (selectionGeneration == requestGeneration) {
                                // Preserve the marker chosen from the search result; richer provider
                                // category text belongs to the card and is not a replacement icon class.
                                if (real != null) place = real
                                placeLoading = false
                            }
                        }
                        placeSheet.goTo(1)
                    }
                },
                onClose = { searchActive = false; searchQuery = ""; editingStopIndex = null },
            )
        }
    }
}

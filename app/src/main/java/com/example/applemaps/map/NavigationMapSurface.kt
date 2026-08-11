package com.example.applemaps.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.applemaps.R
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineGradient
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

/**
 * Navigation-only renderer restored from the preserved custom map path and invoked by the green GO action.
 * Browsing and Directions preview stay on the consumer Apple WebView underneath; GO crossfades this MapView over
 * it, and Exit reveals the unchanged Apple session again. The 48dp circular native compass sits at top-right below
 * the maneuver banner, remains visible at north, and its SDK-owned tap action animates bearing to zero. The map uses
 * the local Apple-colored navigation style restricted to OpenFreeMap sources. Compiler/JVM/lint coverage is local;
 * GO, live follow, compass tap, and Exit require the emulator/physical-device UI pass recorded in the task journal.
 */
@Composable
fun NavigationMapSurface(
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap?) -> Unit,
    onMapGesture: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val latestReady = rememberUpdatedState(onMapReady)
    val latestGesture = rememberUpdatedState(onMapGesture)
    val compassTopPx = WindowInsets.statusBars.getTop(density) + with(density) { 116.dp.roundToPx() }
    val compassRightPx = with(density) { 10.dp.roundToPx() }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.setTileCacheEnabled(true)
                map.uiSettings.apply {
                    setCompassEnabled(true)
                    setCompassFadeFacingNorth(false)
                    setCompassGravity(Gravity.TOP or Gravity.END)
                    setCompassMargins(0, compassTopPx, compassRightPx, 0)
                }
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) latestGesture.value()
                }
                map.setStyle(Style.Builder().fromUri("asset://navigation_style.json")) {
                    latestReady.value(map)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var destroyed = false
        var started = false
        var resumed = false
        fun destroyMap() {
            if (destroyed) return
            destroyed = true
            latestReady.value(null)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            resumed = false
            started = false
            mapView.onDestroy()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { if (resumed) mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { if (started) mapView.onStop(); started = false }
                Lifecycle.Event.ON_DESTROY -> destroyMap()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            destroyMap()
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    }
}

/** Native navigation route; only this renderer mutates these source/layer IDs. */
object NavigationRouteLayer {
    private const val SOURCE = "navigation-route-source"
    private const val CASING = "navigation-route-casing"
    private const val MAIN = "navigation-route-main"
    private const val DESTINATION_SOURCE = "navigation-destination-source"
    private const val DESTINATION = "navigation-destination"

    fun draw(map: MapLibreMap, route: Route) {
        val style = map.style ?: return
        if (route.points.size < 2) return
        clear(map)
        val geometry = LineString.fromLngLats(route.points.map { Point.fromLngLat(it.longitude, it.latitude) })
        style.addSource(GeoJsonSource(SOURCE, geometry, GeoJsonOptions().withLineMetrics(true)))
        style.addLayer(LineLayer(CASING, SOURCE).withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            lineColor("#004DE9"), lineWidth(8.5f),
        ))
        style.addLayer(LineLayer(MAIN, SOURCE).withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            lineColor("#00A1FE"), lineWidth(5.8f),
        ))
        val destination = route.points.last()
        style.addSource(GeoJsonSource(DESTINATION_SOURCE,
            Feature.fromGeometry(Point.fromLngLat(destination.longitude, destination.latitude))))
        style.addLayer(CircleLayer(DESTINATION, DESTINATION_SOURCE).withProperties(
            circleRadius(7f), circleColor("#004DE9"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2.5f),
        ))
    }

    fun setProgress(map: MapLibreMap, progress: Float) {
        val p = progress.coerceIn(0f, 1f).toDouble()
        val style = map.style ?: return
        style.getLayerAs<LineLayer>(MAIN)?.setProperties(lineGradient(Expression.step(
            Expression.lineProgress(), Expression.color(android.graphics.Color.TRANSPARENT),
            Expression.literal(p), Expression.color(android.graphics.Color.parseColor("#00A1FE")),
        )))
        style.getLayerAs<LineLayer>(CASING)?.setProperties(lineGradient(Expression.step(
            Expression.lineProgress(), Expression.color(android.graphics.Color.TRANSPARENT),
            Expression.literal(p), Expression.color(android.graphics.Color.parseColor("#004DE9")),
        )))
    }

    fun clear(map: MapLibreMap) {
        val style = map.style ?: return
        listOf(DESTINATION, MAIN, CASING).forEach { if (style.getLayer(it) != null) style.removeLayer(it) }
        listOf(DESTINATION_SOURCE, SOURCE).forEach { if (style.getSource(it) != null) style.removeSource(it) }
    }
}

/** Frame-locked navigation arrow copied from the preserved custom renderer and adapted to provider-neutral points. */
object NavigationArrowLayer {
    private const val SOURCE = "navigation-arrow-source"
    private const val LAYER = "navigation-arrow-layer"
    private const val IMAGE = "navigation-arrow-image"

    fun update(map: MapLibreMap, context: android.content.Context, coordinate: MapCoordinate, bearing: Double) {
        val style = map.style ?: return
        if (style.getImage(IMAGE) == null) {
            drawableToBitmap(context, R.drawable.ic_nav_arrow)?.let { style.addImage(IMAGE, it) }
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(coordinate.longitude, coordinate.latitude))
        val source = style.getSourceAs<GeoJsonSource>(SOURCE)
        if (source == null) {
            style.addSource(GeoJsonSource(SOURCE, feature))
            style.addLayer(SymbolLayer(LAYER, SOURCE).withProperties(
                iconImage(IMAGE), iconRotate(bearing.toFloat()),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconAnchor(Property.ICON_ANCHOR_CENTER), iconAllowOverlap(true),
                iconIgnorePlacement(true), iconSize(1f),
            ))
        } else {
            source.setGeoJson(feature)
            style.getLayerAs<SymbolLayer>(LAYER)?.setProperties(iconRotate(bearing.toFloat()))
        }
    }

    private fun drawableToBitmap(context: android.content.Context, resource: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, resource) ?: return null
        val side = (48 * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, side, side)
            drawable.draw(Canvas(bitmap))
        }
    }
}

package com.example.applemaps.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.example.applemaps.BuildConfig
import com.example.applemaps.R
import com.example.applemaps.diag.DiagLog
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

private const val CONSUMER_URL = "https://maps.apple.com/"

sealed interface ConsumerRendererState {
    data object Loading : ConsumerRendererState
    data object Ready : ConsumerRendererState
    data class Error(val title: String, val detail: String) : ConsumerRendererState
}

data class ConsumerSelectedPlace(
    val id: String,
    val title: String,
    val category: String,
    val coordinate: MapCoordinate,
    val source: String,
)

/**
 * Persistent controller for the consumer `maps.apple.com` renderer.
 *
 * The WebView is hosted directly by Compose's AndroidView interop. This keeps Apple tile/WebGL state mounted while
 * copied sheets change and gives the WebView the platform touch stream without a sibling-event relay. The injected
 * adapter retains Apple's selected annotation, creates app-origin selections
 * with MapKit's native marker, keeps scroll/zoom/rotation enabled, and forces the north compass visible. Directions
 * overlays never fit or otherwise take over the consumer camera; turn-by-turn route/arrow/camera state stays on this
 * one renderer instead of switching map engines after GO.
 * The page creates its own consumer session; this class never accepts or logs a developer token.
 */
class ConsumerMapController(private val context: Context) : LocationListener {
    private val main = Handler(Looper.getMainLooper())
    private val rendererState = mutableStateOf<ConsumerRendererState>(ConsumerRendererState.Loading)
    val state: State<ConsumerRendererState> get() = rendererState
    private val cameraCenterState = mutableStateOf<MapCoordinate?>(null)
    val centerState: State<MapCoordinate?> get() = cameraCenterState
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private var container: FrameLayout? = null
    private var webView: WebView? = null
    private var ready = false
    private var resumed = false
    private var locationEnabled = false
    private var mapType = "standard"
    private var currentCenter = MapCoordinate(37.7749, -122.4194)
    private var selectedCallback: (ConsumerSelectedPlace) -> Unit = {}
    private var longPressCallback: (MapCoordinate) -> Unit = {}
    private var gestureCallback: () -> Unit = {}
    private var desiredPin: JSONObject? = null
    private var desiredRoutes: JSONObject? = null
    private var desiredProgress = 0f
    private var desiredNav: JSONObject? = null
    private var desiredUserLocation: MapCoordinate? = null
    private val navArrowDataUrl by lazy { drawableDataUrl(R.drawable.ic_nav_arrow) }

    fun attachTo(target: FrameLayout) {
        container = target
        if (webView == null) recreateRenderer()
    }

    fun bind(
        onSelected: (ConsumerSelectedPlace) -> Unit,
        onLongPress: (MapCoordinate) -> Unit,
        onGesture: () -> Unit,
    ) {
        selectedCallback = onSelected
        longPressCallback = onLongPress
        gestureCallback = onGesture
    }

    fun unbind() {
        selectedCallback = {}
        longPressCallback = {}
        gestureCallback = {}
    }

    fun onResume() {
        resumed = true
        if (webView == null && container != null) recreateRenderer() else webView?.onResume()
        updateLocationSubscription()
    }

    fun onPause() {
        resumed = false
        locationManager.removeUpdates(this)
        webView?.onPause()
    }

    fun destroy() {
        locationManager.removeUpdates(this)
        ready = false
        webView?.let(::destroyWebView)
        webView = null
        container = null
    }

    fun reload() = recreateRenderer()

    /** Validates that a native category tray can open without navigating or rebuilding the live map page. */
    fun showHomeCategory(label: String): Boolean {
        if (!ready || label.isBlank()) {
            DiagLog.log("CONSUMERMAP", "event=home_action_unavailable", "kind=category")
            return false
        }
        DiagLog.log("CONSUMERMAP", "event=home_action", "kind=category", "mode=native-tray-stable-map")
        return true
    }

    /** Validates that a native Guide tray can open without navigating or rebuilding the live map page. */
    fun showGuide(curatedId: String): Boolean {
        if (!ready || !curatedId.matches(Regex("[0-9]{1,20}"))) {
            DiagLog.log("CONSUMERMAP", "event=home_action_unavailable", "kind=guide")
            return false
        }
        DiagLog.log("CONSUMERMAP", "event=home_action", "kind=guide", "mode=native-tray-stable-map")
        return true
    }

    fun center(): MapCoordinate = currentCenter

    fun lastKnownLocation(): MapCoordinate? {
        if (!hasLocationPermission()) return null
        return runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull(locationManager::getLastKnownLocation)
                .maxByOrNull(Location::getTime)
                ?.let { MapCoordinate(it.latitude, it.longitude) }
        }.getOrNull()
    }

    fun setMapType(value: String) {
        mapType = when (value.lowercase()) {
            "satellite" -> "satellite"
            "hybrid" -> "hybrid"
            else -> "standard"
        }
        call("setMapType", JSONObject.quote(mapType))
        DiagLog.log("CONSUMERMAP", "event=map_type", "value=$mapType")
    }

    fun setTrafficEnabled(enabled: Boolean) {
        DiagLog.log("CONSUMERMAP", "event=traffic_request", "enabled=${if (enabled) 1 else 0}", "supported=0")
    }

    fun setBuildings3DEnabled(enabled: Boolean) {
        DiagLog.log("CONSUMERMAP", "event=buildings_request", "enabled=${if (enabled) 1 else 0}", "supported=0")
    }

    fun setPin(
        coordinate: MapCoordinate?,
        showBalloon: Boolean,
        tint: Color,
        @Suppress("UNUSED_PARAMETER") face: ImageBitmap?,
        label: String,
    ) {
        desiredPin = coordinate?.let {
            JSONObject()
                .put("id", "app-pin:${it.latitude},${it.longitude}")
                .put("title", label.take(160))
                .put("category", if (face == null) "Marked Location" else "Selected place")
                .put("latitude", it.latitude)
                .put("longitude", it.longitude)
                .put("color", String.format("#%06X", 0xFFFFFF and tint.toArgb()))
                .put("expanded", showBalloon)
        }
        call("setPin", desiredPin?.toString() ?: "null")
    }

    fun drawRoutes(
        routes: List<Route>,
        labels: List<Pair<String, String>>?,
        fitAndReveal: Boolean,
    ) {
        desiredRoutes = JSONObject()
            .put("reveal", fitAndReveal)
            .put("routes", JSONArray().apply {
                routes.forEachIndexed { routeIndex, route ->
                    put(JSONObject()
                        .put("points", JSONArray().apply {
                            route.points.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
                        })
                        .put("traffic", JSONArray().apply {
                            route.trafficIntervals.forEach { interval ->
                                put(JSONObject().put("start", interval.startPointIndex).put("end", interval.endPointIndex)
                                    .put("level", interval.level.name.lowercase()))
                            }
                        })
                        .put("time", labels?.getOrNull(routeIndex)?.first ?: "")
                        .put("subtitle", labels?.getOrNull(routeIndex)?.second ?: ""))
                }
            })
        desiredProgress = 0f
        call("setRoutes", desiredRoutes.toString())
        DiagLog.log("CONSUMERMAP", "event=routes", "count=${routes.size}", "reveal=${if (fitAndReveal) 1 else 0}", "camera=consumer")
    }

    fun clearRoutes() {
        desiredRoutes = null
        desiredProgress = 0f
        call("clearRoutes")
        DiagLog.log("CONSUMERMAP", "event=routes_clear")
    }

    fun setRouteProgress(progress: Float) {
        desiredProgress = progress.coerceIn(0f, 1f)
        call("setRouteProgress", desiredProgress.toString())
    }

    fun centerOn(
        coordinate: MapCoordinate,
        zoom: Double? = null,
        rotation: Double? = null,
        bottomPaddingPx: Int = 0,
        animated: Boolean = true,
    ) {
        val payload = JSONObject()
            .put("latitude", coordinate.latitude)
            .put("longitude", coordinate.longitude)
            .put("distance", zoom?.let { zoomToCameraDistance(it, coordinate.latitude) })
            .put("rotation", rotation)
            .put("bottomPadding", bottomPaddingPx.coerceAtLeast(0))
            .put("animated", animated)
        call("setCamera", payload.toString())
        DiagLog.log("CONSUMERMAP", "event=camera", "zoom=${zoom ?: -1.0}", "rotation=${rotation ?: -1.0}")
    }

    /** Frames a selected large venue from Apple's own bounds without invoking MapKit route/item fitting. */
    fun centerOnBounds(bounds: PlaceBounds) {
        if (bounds.southLat !in -85.05112878..85.05112878 || bounds.northLat !in -85.05112878..85.05112878 ||
            bounds.westLon !in -180.0..180.0 || bounds.eastLon !in -180.0..180.0 ||
            bounds.southLat >= bounds.northLat || bounds.westLon >= bounds.eastLon
        ) return
        val latitude = (bounds.southLat + bounds.northLat) / 2.0
        val longitude = (bounds.westLon + bounds.eastLon) / 2.0
        val latSpan = bounds.northLat - bounds.southLat
        val lonSpan = (bounds.eastLon - bounds.westLon) * cos(Math.toRadians(latitude)).coerceAtLeast(0.2)
        val zoom = (log2(360.0 / maxOf(latSpan, lonSpan).coerceAtLeast(1e-6)) - 0.75).coerceIn(5.0, 18.0)
        centerOn(MapCoordinate(latitude, longitude), zoom = zoom)
    }

    fun setNavigationPose(coordinate: MapCoordinate, bearing: Double, follow: Boolean) {
        desiredNav = JSONObject()
            .put("latitude", coordinate.latitude)
            .put("longitude", coordinate.longitude)
            .put("bearing", bearing)
            .put("image", navArrowDataUrl)
        call("setNavigationPose", desiredNav.toString())
        if (follow) centerOn(coordinate, zoom = 18.0, rotation = bearing, animated = false)
    }

    fun clearNavigation() {
        desiredNav = null
        call("clearNavigation")
    }

    fun setLocationEnabled(enabled: Boolean) {
        if (locationEnabled == enabled) return
        locationEnabled = enabled
        if (!enabled) {
            locationManager.removeUpdates(this)
            desiredUserLocation = null
            call("setUserLocation", "null")
        } else {
            lastKnownLocation()?.let(::publishLocation)
            updateLocationSubscription()
        }
    }

    override fun onLocationChanged(location: Location) = publishLocation(MapCoordinate(location.latitude, location.longitude))
    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    @SuppressLint("MissingPermission")
    private fun updateLocationSubscription() {
        locationManager.removeUpdates(this)
        if (!resumed || !locationEnabled || !hasLocationPermission()) return
        runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                if (locationManager.isProviderEnabled(provider)) locationManager.requestLocationUpdates(provider, 1_000L, 1f, this)
            }
        }.onFailure { DiagLog.log("CONSUMERMAP", "event=location_error", "type=${it.javaClass.simpleName}") }
    }

    private fun publishLocation(coordinate: MapCoordinate) {
        desiredUserLocation = coordinate
        call("setUserLocation", JSONObject().put("latitude", coordinate.latitude).put("longitude", coordinate.longitude).toString())
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun zoomToCameraDistance(zoom: Double, latitude: Double): Double =
        (40_000_000.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.2) / 2.0.pow(zoom)).coerceAtLeast(40.0)

    private fun call(name: String, argument: String? = null) {
        if (!ready) return
        val js = if (argument == null) "window.__consumerNavAdapter&&window.__consumerNavAdapter.$name()"
            else "window.__consumerNavAdapter&&window.__consumerNavAdapter.$name($argument)"
        main.post { webView?.evaluateJavascript(js, null) }
    }

    private fun syncDesiredState() {
        setMapType(mapType)
        call("setPin", desiredPin?.toString() ?: "null")
        desiredRoutes?.let { call("setRoutes", it.toString()); call("setRouteProgress", desiredProgress.toString()) }
        desiredNav?.let { call("setNavigationPose", it.toString()) }
        desiredUserLocation?.let { call("setUserLocation", JSONObject().put("latitude", it.latitude).put("longitude", it.longitude).toString()) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun recreateRenderer() {
        main.post {
            ready = false
            rendererState.value = ConsumerRendererState.Loading
            webView?.let(::destroyWebView)
            val target = container ?: return@post
            val view = WebView(context).apply {
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                setBackgroundColor(android.graphics.Color.rgb(246, 243, 234))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.setGeolocationEnabled(false)
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                addJavascriptInterface(Bridge(), "AndroidConsumerNav")
                webViewClient = client
                webChromeClient = chrome
                loadUrl(CONSUMER_URL)
            }
            webView = view
            target.addView(view, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            if (resumed) view.onResume()
            DiagLog.log("CONSUMERMAP", "event=load", "sdk=${Build.VERSION.SDK_INT}")
        }
    }

    private fun destroyWebView(view: WebView) {
        (view.parent as? FrameLayout)?.removeView(view)
        view.removeJavascriptInterface("AndroidConsumerNav")
        view.stopLoading()
        view.webChromeClient = null
        view.webViewClient = WebViewClient()
        view.destroy()
    }

    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            return !request.url.isAllowedConsumerPage()
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            ready = false
            rendererState.value = ConsumerRendererState.Loading
            DiagLog.log("CONSUMERMAP", "event=page_started")
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (url?.let(Uri::parse)?.isAllowedConsumerPage() == true) view.evaluateJavascript(CONSUMER_NAV_SCRIPT, null)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) rendererState.value = ConsumerRendererState.Error("maps.apple.com", error.description.toString().take(160))
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (request.isForMainFrame) rendererState.value = ConsumerRendererState.Error("maps.apple.com", "HTTP ${errorResponse.statusCode}")
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            DiagLog.log("CONSUMERMAP", "event=renderer_gone", "crashed=${if (detail.didCrash()) 1 else 0}")
            if (webView === view) webView = null
            (view.parent as? FrameLayout)?.removeView(view)
            view.destroy()
            rendererState.value = ConsumerRendererState.Error("Map renderer stopped", "Reload the Apple map renderer")
            return true
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            if (message.messageLevel() != ConsoleMessage.MessageLevel.LOG) {
                DiagLog.log(
                    "CONSUMERMAP",
                    "event=console",
                    "level=${message.messageLevel()}",
                    "line=${message.lineNumber()}",
                    "source=${message.sourceId().takeLast(80)}",
                    "message=${message.message().take(180)}",
                )
            }
            return true
        }
    }

    private inner class Bridge {
        @JavascriptInterface fun onReady() = main.post {
            ready = true
            rendererState.value = ConsumerRendererState.Ready
            syncDesiredState()
            DiagLog.log("CONSUMERMAP", "event=ready")
        }

        @JavascriptInterface fun onSelected(raw: String) = main.post {
            val p = runCatching { JSONObject(raw.take(2_048)) }.getOrNull() ?: return@post
            val lat = p.optDouble("latitude", Double.NaN)
            val lon = p.optDouble("longitude", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@post
            selectedCallback(ConsumerSelectedPlace(
                id = p.optString("id").take(180),
                title = p.optString("title", "Marked Location").take(160),
                category = p.optString("category", "Apple place").take(120),
                coordinate = MapCoordinate(lat, lon),
                source = p.optString("source", "apple-place").take(40),
            ))
            DiagLog.log("CONSUMERMAP", "event=selection", "source=${p.optString("source", "unknown").take(40)}")
        }

        @JavascriptInterface fun onLongPress(raw: String) = main.post {
            val p = runCatching { JSONObject(raw.take(256)) }.getOrNull() ?: return@post
            val lat = p.optDouble("latitude", Double.NaN)
            val lon = p.optDouble("longitude", Double.NaN)
            if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                longPressCallback(MapCoordinate(lat, lon))
                DiagLog.log("CONSUMERMAP", "event=long_press")
            }
        }

        @JavascriptInterface fun onGesture() = main.post(gestureCallback)

        @JavascriptInterface fun onCamera(raw: String) = main.post {
            val p = runCatching { JSONObject(raw.take(512)) }.getOrNull() ?: return@post
            if (p.optString("event") == "controls") {
                DiagLog.log(
                    "CONSUMERMAP", "event=controls",
                    "scroll=${p.optBoolean("scroll")}", "zoom=${p.optBoolean("zoom")}",
                    "rotation=${p.optBoolean("rotation")}", "compass=${p.optString("compass").take(40)}",
                )
                return@post
            }
            val lat = p.optDouble("latitude", Double.NaN)
            val lon = p.optDouble("longitude", Double.NaN)
            if (lat.isFinite() && lon.isFinite()) {
                val center = MapCoordinate(lat, lon)
                currentCenter = center
                cameraCenterState.value = center
            }
        }

        @JavascriptInterface fun onError(title: String, detail: String) = main.post {
            rendererState.value = ConsumerRendererState.Error(title.take(80), detail.take(160))
            DiagLog.log("CONSUMERMAP", "event=adapter_error", "title=${title.take(80)}")
        }
    }

    private fun drawableDataUrl(@DrawableRes id: Int): String {
        val drawable = ContextCompat.getDrawable(context, id) ?: return ""
        val side = (48 * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, side, side)
        drawable.draw(Canvas(bitmap))
        return java.io.ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            "data:image/png;base64," + android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
        }
    }
}

private fun Uri.isAllowedConsumerPage(): Boolean = scheme == "https" && host == "maps.apple.com"

private val CONSUMER_NAV_SCRIPT = """
(() => {
  'use strict';
  const bridge = () => window.AndroidConsumerNav;
  const text = v => typeof v === 'string' ? v : '';
  if (window.__consumerNavAdapter) { window.__consumerNavAdapter.reportReady(); return; }
  const state = {map:null,pin:null,appPin:null,pinOwned:false,pinData:null,user:null,nav:null,routeOverlays:[],routePrimary:[],routeLabels:[],attempts:0,raf:false,tracking:false,trackRaf:0,pointer:null,gestureSent:false};
  const style = document.createElement('style');
  style.id = 'consumer-nav-style';
  style.textContent = `
    #shell-navigation,#shell-tray{display:none!important}
    #shell-map-controls{display:block!important}
    #shell-map-controls .mw-controls-container,
    #shell-map-controls #ttr-control-container,
    #shell-map-controls .mw-zoom-controls,
    #shell-map-controls .mw-legal-links,
    #shell-map-controls .mw-look-around{display:none!important}
    #shell-map-controls .mw-top-right-controls-container{pointer-events:none}
    #shell-map-controls .mw-compass{display:block!important;pointer-events:auto}
    .consumer-map-node{position:fixed;z-index:2147483000;pointer-events:auto;transform:translate(-50%,-50%)}
    #consumer-user{width:18px;height:18px;border:3px solid white;border-radius:50%;background:#0a84ff;box-shadow:0 1px 4px rgba(0,0,0,.35);pointer-events:none}
    #consumer-nav-arrow{width:48px;height:48px;pointer-events:none;transform-origin:50% 50%}
    .consumer-route-label{padding:6px 10px;border-radius:8px;background:white;box-shadow:0 2px 7px rgba(0,0,0,.2);font:14px -apple-system,sans-serif;white-space:nowrap;color:#8e8e93;pointer-events:none}
    .consumer-route-label strong{display:block;color:#007aff;font-size:15px;text-align:center}
    .consumer-route-label.selected{background:#007aff;color:rgba(255,255,255,.9)}.consumer-route-label.selected strong{color:white}
    #consumer-destination{width:14px;height:14px;border:3px solid white;border-radius:50%;background:#007aff;box-sizing:border-box;pointer-events:none}
  `;
  document.head.appendChild(style);

  function viewport(){
    const h=innerHeight+'px';
    [document.documentElement,document.body,document.querySelector('#shell-wrapper'),document.querySelector('#shell-map')].filter(Boolean).forEach(e=>{e.style.setProperty('height',h,'important');e.style.setProperty('min-height',h,'important')});
    bridge()?.onCamera(JSON.stringify({event:'viewport',height:innerHeight}));
  }
  function node(id,cls){let e=document.getElementById(id);if(!e){e=document.createElement('div');e.id=id;e.className='consumer-map-node '+(cls||'');document.body.appendChild(e)}return e}
  function point(c){return state.map?.convertCoordinateToPointOnPage(new mapkit.Coordinate(c.latitude,c.longitude))}
  function place(e,c){const p=point(c);if(!p||!Number.isFinite(p.x)||!Number.isFinite(p.y)){e.style.display='none';return}e.style.display='block';e.style.left=p.x+'px';e.style.top=p.y+'px'}
  function updateNodes(){state.raf=false;if(state.user&&state.userData)place(state.user,state.userData);if(state.nav&&state.navData)place(state.nav,state.navData);state.routeLabels.forEach(x=>place(x.node,x.coordinate));if(state.destination)place(state.destination,state.destinationData)}
  function queue(){if(!state.raf){state.raf=true;requestAnimationFrame(updateNodes)}}
  function trackNodes(){if(!state.tracking)return;updateNodes();state.trackRaf=requestAnimationFrame(trackNodes)}
  function startTracking(){if(state.tracking)return;state.tracking=true;state.trackRaf=requestAnimationFrame(trackNodes)}
  function stopTracking(){state.tracking=false;if(state.trackRaf)cancelAnimationFrame(state.trackRaf);state.trackRaf=0;queue()}
  function publishCamera(){const c=state.map?.center;if(c)bridge()?.onCamera(JSON.stringify({latitude:c.latitude,longitude:c.longitude,distance:state.map.cameraDistance,rotation:state.map.rotation}))}
  function urlPlaceId(){try{return new URL(location.href).searchParams.get('place-id')||''}catch(_){return''}}
  function emitSelection(a,initial,frames,source){const c=a?.coordinate;if(!c)return;const now=urlPlaceId();if(source==='app-marker'||(now&&now!==initial)||frames<=0){bridge()?.onSelected(JSON.stringify({id:a.placeId||now||('coordinate:'+c.latitude+','+c.longitude),title:text(a.title)||'Marked Location',category:text(a.pointOfInterestCategory)||text(a.subtitle)||'Apple place',latitude:c.latitude,longitude:c.longitude,source:source||'apple-place'}));return}requestAnimationFrame(()=>emitSelection(a,initial,frames-1,source))}
  function sameCoordinate(a,p){const c=a?.coordinate;return !!c&&Math.abs(c.latitude-p.latitude)<1e-7&&Math.abs(c.longitude-p.longitude)<1e-7}
  function releasePin(){if(state.pinOwned&&state.pin&&state.map)state.map.removeAnnotation(state.pin);if(state.map?.selectedAnnotation===state.pin)state.map.selectedAnnotation=null;state.pin=null;state.appPin=null;state.pinOwned=false;state.pinData=null}
  function setPin(p){if(!state.map)return;state.pinData=p;if(!p){releasePin();return}if(!sameCoordinate(state.pin,p)){if(typeof mapkit.MarkerAnnotation!=='function'){bridge()?.onError('Apple marker unavailable','Consumer map did not expose MarkerAnnotation');return}releasePin();state.pin=new mapkit.MarkerAnnotation(new mapkit.Coordinate(p.latitude,p.longitude),{title:p.title||'',color:p.color||'#ff3b30'});state.appPin=state.pin;state.pinOwned=true;state.map.addAnnotation(state.pin)}if(p.expanded)state.map.selectedAnnotation=state.pin;else if(state.map.selectedAnnotation===state.pin)state.map.selectedAnnotation=null}
  function setUserLocation(p){state.userData=p;if(!p){state.user?.remove();state.user=null;return}state.user=node('consumer-user');queue()}
  function setNavigationPose(p){state.navData=p;state.nav=node('consumer-nav-arrow');state.nav.innerHTML='<img alt="" width="48" height="48" src="'+(p.image||'')+'">';state.nav.style.transform='translate(-50%,-50%) rotate('+p.bearing+'deg)';queue()}
  function clearNavigation(){state.navData=null;state.nav?.remove();state.nav=null}
  function clearRoutes(){if(state.map&&state.routeOverlays.length)state.map.removeOverlays(state.routeOverlays);state.routeOverlays=[];state.routePrimary=[];state.routeLabels.forEach(x=>x.node.remove());state.routeLabels=[];state.destination?.remove();state.destination=null}
  function coords(raw){return raw.map(p=>new mapkit.Coordinate(p[0],p[1]))}
  function line(points,options,primary){const o=new mapkit.PolylineOverlay(points,{style:new mapkit.Style(options)});state.map.addOverlay(o);state.routeOverlays.push(o);if(primary)state.routePrimary.push(o);return o}
  function trafficColor(level){return level==='slow'?'#ff9f0a':level==='heavy'?'#ff3b30':level==='severe'?'#a80000':'#007aff'}
  function setRoutes(payload){clearRoutes();if(!state.map||!payload?.routes?.length)return;payload.routes.forEach((r,i)=>{const c=coords(r.points);if(c.length<2)return;if(i>0){line(c,{strokeColor:'#8e8e93',strokeOpacity:.62,lineWidth:4.5,lineCap:'round',lineJoin:'round'},false)}else{line(c,{strokeColor:'#ffffff',lineWidth:10,lineCap:'round',lineJoin:'round',strokeEnd:payload.reveal?0:1},true);if(r.traffic?.length){r.traffic.forEach(t=>{const seg=c.slice(Math.max(0,t.start),Math.min(c.length,t.end+1));if(seg.length>1)line(seg,{strokeColor:trafficColor(t.level),lineWidth:6,lineCap:'round',lineJoin:'round',strokeEnd:payload.reveal?0:1},true)})}else line(c,{strokeColor:'#007aff',lineWidth:6,lineCap:'round',lineJoin:'round',strokeEnd:payload.reveal?0:1},true)}if(r.time){const n=document.createElement('div');n.className='consumer-map-node consumer-route-label '+(i===0?'selected':'');n.innerHTML='<strong></strong><span></span>';n.querySelector('strong').textContent=r.time;n.querySelector('span').textContent=r.subtitle||'';document.body.appendChild(n);state.routeLabels.push({node:n,coordinate:r.points[Math.floor(r.points.length/2)]&&{latitude:r.points[Math.floor(r.points.length/2)][0],longitude:r.points[Math.floor(r.points.length/2)][1]}})}});const last=payload.routes[0].points[payload.routes[0].points.length-1];if(last){state.destination=node('consumer-destination');state.destinationData={latitude:last[0],longitude:last[1]}}queue();if(payload.reveal){const start=performance.now();const tick=now=>{const p=Math.max(0,Math.min(1,(now-start)/1400));state.routePrimary.forEach(o=>o.style.strokeEnd=p);if(p<1)requestAnimationFrame(tick)};requestAnimationFrame(tick)}}
  function setRouteProgress(t){const v=Math.max(0,Math.min(1,Number(t)||0));state.routePrimary.forEach(o=>o.style.strokeStart=v)}
  function setMapType(type){if(state.map)state.map.mapType=['standard','satellite','hybrid'].includes(type)?type:'standard'}
  function setCamera(p){if(!state.map||!p)return;state.map.setPadding(new mapkit.Padding(0,0,Number(p.bottomPadding)||0,0),!!p.animated);state.map.setCenterAnimated(new mapkit.Coordinate(p.latitude,p.longitude),!!p.animated);if(Number.isFinite(p.distance))state.map.setCameraDistanceAnimated(p.distance,!!p.animated);if(Number.isFinite(p.rotation))state.map.setRotationAnimated(p.rotation,!!p.animated)}
  function reportReady(){if(state.map)bridge()?.onReady()}
  function install(){viewport();const m=window.mapkit?.maps?.[0];if(!m){if(++state.attempts<900)requestAnimationFrame(install);else bridge()?.onError('Consumer map','Timed out waiting for maps.apple.com renderer');return}state.map=m;m.isScrollEnabled=true;m.isZoomEnabled=true;m.isRotationEnabled=true;m.showsCompass=mapkit.FeatureVisibility.Visible;m.addEventListener('select',e=>{const a=e.annotation||m.selectedAnnotation;if(!a)return;if(a!==state.pin){if(state.pinOwned)releasePin();state.pin=a;state.pinOwned=a===state.appPin}emitSelection(a,urlPlaceId(),30,state.pinOwned?'app-marker':'apple-place')});m.addEventListener('region-change-start',startTracking);m.addEventListener('region-change-end',()=>{stopTracking();publishCamera()});const target=document.querySelector('#shell-map')||document.body;target.addEventListener('pointerdown',e=>{state.pointer={x:e.clientX,y:e.clientY,time:performance.now()};state.gestureSent=false},true);target.addEventListener('pointermove',e=>{if(!state.pointer)return;const d=Math.hypot(e.clientX-state.pointer.x,e.clientY-state.pointer.y);if(d>6&&!state.gestureSent){state.gestureSent=true;bridge()?.onGesture()}},true);target.addEventListener('pointerup',e=>{const p=state.pointer;state.pointer=null;if(!p)return;const d=Math.hypot(e.clientX-p.x,e.clientY-p.y);if(performance.now()-p.time>=500&&d<12){const c=m.convertPointOnPageToCoordinate(new DOMPoint(e.clientX,e.clientY));if(c)bridge()?.onLongPress(JSON.stringify({latitude:c.latitude,longitude:c.longitude}))}},true);bridge()?.onCamera(JSON.stringify({event:'controls',scroll:m.isScrollEnabled,zoom:m.isZoomEnabled,rotation:m.isRotationEnabled,compass:String(m.showsCompass)}));bridge()?.onReady()}
  addEventListener('resize',viewport);visualViewport?.addEventListener('resize',viewport);
  window.__consumerNavAdapter={setPin,setUserLocation,setNavigationPose,clearNavigation,setRoutes,clearRoutes,setRouteProgress,setMapType,setCamera,reportReady};
  install();
})()
""".trimIndent()

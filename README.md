# Apple Maps Consumer Navigation

Separate Android application copied from the completed Apple Maps-style UI and installed as
`com.example.applemaps.consumernav`.

The native Compose controls, three-detent sheets, place cards, search, directions planner, Look Around viewer,
photo gallery, and Ferrostar navigation UI are retained. Browsing and Directions preview use one persistent WebView,
hosted directly inside the Compose map surface, that loads consumer `https://maps.apple.com/`. The page owns its normal
Apple session, vector/WebGL rendering, and browser cache; this project does not accept a MapKit developer token.

The Android bridge controls only the live consumer map instance exposed by that page:

- Standard, Satellite, and Hybrid map types
- Apple place selection with Apple's native selected annotation and native sheet handoff
- native Apple markers for app-origin search and long-press selections
- current-location dot
- route alternatives, traffic-colored route segments, and reveal without an app-forced camera fit
- turn-by-turn route progress, heading follow, pan/zoom pause, and always-visible compass after GO

The green GO action keeps that same Apple consumer renderer mounted beneath the Ferrostar navigation overlay. Route
progress trims the traveled line, the existing navigation arrow follows the current snapped location and heading,
a real map gesture pauses camera following, Recenter resumes it, and Exit restores the route planner. The app does
not switch to a MapLibre/Mapbox basemap during navigation.

The WebView receives pan, pinch, rotate, tap, and long-press input through Compose's native Android View interop.
Opening Directions or selecting a route adds route overlays without calling a bridge-owned bounds/camera fit, so the
consumer map retains its current camera and receives map-region gestures directly around the visible native sheets.

The Directions card's blue **+ Add Stop** row opens the existing place search. Selecting a result appends a via stop;
selecting an existing stop row uses the same search to replace it.

The native search overlay remains unchanged. The home tray refreshes from Apple's region-scoped `/data/search-home`
response after the current map camera settles, so Find Nearby labels/order and Guides—including current publisher,
title, and hero image—follow Apple's newest response rather than a bundled snapshot. Opening a category or Guide uses
the same bounded device-local client pattern as rich place photos and Look Around. The hidden consumer page enters
the same category/Guide state so Apple's native map markers remain synchronized.

Directions/place providers remain the copied app's existing providers. Google's Road Level Details map remains a
separate optional screen and is not the basemap renderer.

## Build

```bash
ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest lintDebug assembleDebug
```

The canonical debug deliverable is copied to the repository root as `applemaps-consumer-nav-debug.apk`.

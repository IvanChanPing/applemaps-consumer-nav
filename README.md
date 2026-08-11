# Apple Maps Consumer Navigation

Separate Android application copied from the completed Apple Maps-style UI and installed as
`com.example.applemaps.consumernav`.

The native Compose controls, three-detent sheets, place cards, search, directions planner, Look Around viewer,
photo gallery, and Ferrostar navigation UI are retained. Browsing and Directions preview use one persistent
Activity-owned WebView that loads the consumer `https://maps.apple.com/` page directly. The page owns its normal
Apple session, vector/WebGL rendering, and browser cache; this project does not accept a MapKit developer token.

The Android bridge controls only the live consumer map instance exposed by that page:

- Standard, Satellite, and Hybrid map types
- Apple place selection and native sheet handoff
- app-owned animated selected marker
- current-location dot
- route alternatives, traffic-colored route segments, reveal, and iOS-style route-bounds framing

The green GO action crossfades out of the Apple route-preview presentation and into the preserved custom navigation
renderer. That navigation-only MapLibre surface uses a local Apple-colored style restricted to OpenFreeMap sources
(no Mapbox source or token), the existing arrow artwork and Ferrostar state, the vanishing traveled-route line,
heading-up 58-degree follow camera, pause-on-user-gesture behavior, and MapLibre's native always-visible compass.
Tapping the compass uses the SDK's built-in north reset. Exit crossfades back to the still-mounted Apple session.

Provider categories are normalized before selecting the custom marker artwork, including Convenience Store,
Grocery, Supermarket, and generic Store/Shop categories; only genuinely unknown classes use the gray fallback.

Directions/place providers remain the copied app's existing providers. Google's Road Level Details map remains a
separate optional screen and is not the basemap renderer.

## Build

```bash
ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest lintDebug assembleDebug
```

The canonical debug deliverable is copied to the repository root as `applemaps-consumer-nav-debug.apk`.

# Changelog

## 2026-08-14 — 0.15 stacked restaurant menu sheet

- Moved Menu from an in-place Overview/Menu transformation into the restaurant card's top action row, matching the five-button reference layout when Directions, Call, Website, Menu, and More are available.
- Menu now opens in its own independently controlled sheet above the unchanged restaurant card, with its own close/back behavior, loading/error states, section filters, item rows, prices, descriptions, and available photos.
- Menu retrieval starts when the separate sheet opens and still exposes the original source when that source blocks the current network route; no blocked-route bypass or fabricated content is implied.
- Real emulator taps verified the five-button row, separate-sheet opening, X dismissal, Android Back dismissal, unchanged-card restoration, and blocked-source fallback without a crash/ANR signature. Loaded rows remain physical-network UI-unverified because Yelp blocked that emulator route. Canonical APK: 237,670,600 bytes; SHA-256 `2d9265dce227add1b8116b71ebe4a37a57f2bd27564028a023ed2f46cfcf9f6f`.

## 2026-08-14 — 0.14 restaurant menu page

- Restaurant place cards now expose a native Overview/Menu tab when Apple supplies an explicit Menu quick link.
- Menu data is loaded independently from the place card through the source page's semantic schema.org Menu data, preserving real sections, dish names, descriptions, currency prices, item links, and optional path-matched photos without hardcoded samples.
- Source loading is selection-scoped and bounded; changing restaurants cancels stale work, while unavailable or changed source pages show an explicit error with an Open menu action instead of delaying or breaking Overview.
- Added parser coverage for array/object schema variants, missing optional fields, image association, price formatting, and documents without real items. Files: `RestaurantMenuClient.kt`, `Place.kt`, `ApplePlaceClient.kt`, `PlaceCard.kt`, `AppleMapsScreen.kt`, tests, and version metadata. Unit tests, Android lint, and debug assembly pass. Real emulator taps verified search → Sisters → Menu and the visible unavailable-source state; Yelp blocked that emulator route, so loaded rows/filtering remain parser-verified but physical-network UI-unverified. Canonical APK: 237,670,588 bytes; SHA-256 `31d7411eb236dedda5d3a69c0dcf39880cfe7a3eaec5137d88e0e698ec10fdb4`.

## 2026-08-12 — 0.13 Vela navigation screen

- GO now backgrounds the Apple WebView Activity and opens a dedicated Vela navigation Activity.
- Vela's pinned core, map UI, routing, rerouting, voice, downloadable voices, service, and Exit behavior are compiled from an immutable upstream snapshot without source edits.
- Vela's legacy embedded protobuf runtime is build-time relocated with its OsmAnd callers so Apple Look Around can retain protobuf-javalite 3.22.3 without duplicate or incompatible classes.
- Vela's navigation and data-sync foreground services, TTS engine visibility, notification reopen path, and large-heap voice runtime requirements are declared by the host manifest.
- Ending guidance or arriving finishes the Vela Activity and resumes the preserved Apple planner/map underneath.
- The former Ferrostar navigation implementation remains in source but has no reachable production caller.
- Combined verification passed 42 unit tests, host Android lint, and debug assembly. Canonical local APK: 237,605,056 bytes; SHA-256 `33e752ae7aa735ef376aa92850943b0fca4cccfa789e2de4072853e13ac680dd`.

- 2026-08-12 — Main-page Find Nearby and Guide trays now install up to 20 matching blue MapKit markers on the
  already-mounted Apple map and clear them on close, selection, error, or replacement without refreshing the page.
  **Directions** now opens Apple's official unified consumer route URL with the app's origin, destination, mode,
  waypoints, and avoid options, allowing the web app's own alternatives, labels, and overview camera to render under
  the native planner; preview no longer draws the app's separate route overlay. VersionCode 11 / versionName
  `0.12-consumer-browse-and-directions`. Files: `ConsumerMapController.kt`, `AppleMapsScreen.kt`, focused contract
  tests, and version metadata. Route-page map taps are ignored by the native place-selection bridge while Directions
  is open, preventing a panned-over POI from silently replacing the planned destination. The final 42-test run,
  Android lint, and debug assembly pass. Real UI taps showed the matching result dots, Apple's route overview, and
  horizontal and vertical route panning. Canonical APK: 27,949,462 bytes; SHA-256
  `e40a8bf854afa1804ff926ff1f5d145a358b1e7b7707102d408f989125ff6d58`.

- 2026-08-12 — Airport and large-venue place cards now retain Apple's complete categorized photo albums with
  captions/provider actions, expandable attributed About copy, Amenity V2 labels/icons, and rich clickable “Also at
  This Location” cards with ratings and a full list. Airport Directory exposes terminals, levels, airlines, and the
  venue's Gates/Bag Claims/Food/Drinks/Shops/Restrooms browse categories in native trays. Apple venue bounds frame the
  selected airport without route-camera takeover, while walking/cycling/driving directions use the nearest compatible
  Apple road-access point. VersionCode 10 / versionName `0.11-airport-place-parity`; canonical APK is 27,374,542 bytes
  with SHA-256 `24b51845c41d5db87f5e237469db99d268d9160058dfc5015ea6751fe639aa83`.

- 2026-08-12 — Added the canonical v0.10 debug APK and the complete v0.9 UI-verification evidence set to Git:
  16 screenshots, the served and independently downloaded APK copies, and the captured HTTP response headers. This
  tracking-only release changes no application source or runtime behavior.

- 2026-08-12 — Main-page category and Guide buttons now open their native Apple-data trays without navigating the
  mounted consumer map WebView. Opening or closing Restaurants, Parking, and the other home browse actions therefore
  preserves the existing map session, camera, tiles, and annotations instead of reloading `maps.apple.com`.
  Added a source-contract test that rejects renderer navigation from these actions. VersionCode 9 / versionName
  `0.10-stable-home-browse`. Files: `ConsumerMapController.kt`, `AppleMapsScreen.kt`, and
  `ConsumerRouteFramingTest.kt`. JVM tests, Android lint, and debug assembly pass. The canonical APK is 27,325,406
  bytes with SHA-256 `34ff5ef454a4a8818eebd7f41e1b952fbd7b82126f422faf313a8afa29f155ea`.

- 2026-08-11 — Repaired the complete reproduced consumer-map defect set for versionCode 8 / versionName
  `0.9-consumer-map-repairs`. Apple-selected places now use the bridge's exact place ID instead of replacing a selected
  result through text autocomplete; category-only current home responses replace stale home content; Guide chrome is
  hidden when Apple supplies no Guides. Place recentering no longer gives native sheet pixels to MapKit's WebView
  viewport padding, Directions Exit/Back no longer issues a camera reset, selected Add Stop names remain aligned with
  their waypoints, and the More popover is focusable so its outside dismissal is modal. Browse dismissal resets the
  hidden Apple page to its neutral state, sample station departures are not shown as real data, and a renderer missing
  after resume is recreated. Airport and other large-venue cards now map Apple's categorized photo covers, text block,
  and co-located place templates into the native tray. Navigation observes all enabled Android location providers so a
  missing fused sample cannot mask a current GPS fix, and selective Apple control filtering retains the native compass.
  Files: `ApplePlaceClient.kt`, `AppleBrowseClient.kt`, `Place.kt`, `NavEngine.kt`,
  `ConsumerMapController.kt`, `AppleMapsScreen.kt`, `DirectionsSheet.kt`, `HomeFrontPage.kt`, and `PlaceCard.kt`.

- 2026-08-11 — Replaced the static home categories, Guide titles, and solid-color Guide placeholders with Apple's
  latest region-scoped `/data/search-home` feed for versionCode 7 / versionName `0.8-live-home-feed`. The app waits
  for Apple's real camera center, debounces region changes, invalidates older requests immediately, resolves the
  newest ordered Guide IDs concurrently, and renders their current publisher/title/hero image. Each completed feed
  refresh bypasses prior Coil memory/disk image entries, while request generations prevent an older response from
  replacing the newest region. Search is unchanged. Files: `AppleBrowseClient.kt`, `ConsumerMapController.kt`,
  `AppleMapsScreen.kt`, `GeneratedLayout.kt`, `HomeFrontPage.kt`, parser tests, version metadata, and README. All 37
  JVM tests, Android lint, and debug assembly pass. The canonical, served, and HTTPS-downloaded APKs are byte-identical
  with SHA-256 `1fcbdcc53bf1800496347a3a13bb41f1a207f1f4af1192e8ed466ea55e22c59c`; the APK remains untracked so the
  security-audited GitHub history stays free of committed binaries.

- 2026-08-11 — Mapped the home sheet's non-search content to the consumer Apple Maps web app for versionCode 6 /
  versionName `0.7-web-home-actions`. Find Nearby uses Apple's live `/data/search` contract and the static
  editorial samples were replaced with three verified Apple curated Guides parsed from server-rendered Guide caches.
  Category places and Guide title/publisher/description/hero/place rows render in the app's own native tray using the
  same bounded device-local client pattern as rich Apple place photos and Look Around. The hidden consumer page enters
  the corresponding category/Guide state so its native markers remain synchronized; no Apple web tray is exposed.
  Back restores the native home and selecting a row opens the existing native place card. The user-approved native
  search overlay is unchanged. Files: `AppleBrowseClient.kt`, `AppleBrowseSheet.kt`, `HomeFrontPage.kt`,
  `GeneratedLayout.kt`, `AppleMapsScreen.kt`, `ConsumerMapController.kt`, version metadata, README, and tests. All
  35 JVM tests, Android lint, and debug assembly pass. The canonical, served, and HTTPS-downloaded APKs are
  byte-identical at 27,309,014 bytes with SHA-256
  `c1c21b4da82acf80b316cca66555911ab315e9d99d874f298143906e94e75da2`. The APK remains an untracked
  delivery artifact so the security-audited GitHub history stays free of committed binaries.

- 2026-08-11 — Replaced the full-screen Compose-to-sibling touch relay with a persistent WebView hosted directly in
  Compose `AndroidView` interop for versionCode 5 / versionName `0.6-direct-webview-touch`. Map drags, pinch, rotation,
  taps, and long-presses now enter the WebView through the platform-owned interop path while later native controls and
  sheets retain their hit targets. Directions route creation, route selection, and GO no longer call MapKit
  `showItems` or carry route-fit padding, so adding overlays does not force a coast-wide camera fit; auto-uploaded
  route telemetry reports `camera=consumer`. The former forwarding inset and its sentinel workaround were removed
  with the obsolete framing helper, and a source-contract test rejects route-camera takeover. Files:
  `MainActivity.kt`, `MapSurface.kt`, `ConsumerMapController.kt`, `RouteLayer.kt`, `AppleMapsScreen.kt`, focused tests,
  version metadata, and README. JavaScript parsing, all 32 JVM tests, Android lint, and debug assembly pass. The
  canonical and served APK are 27,259,862 bytes with SHA-256
  `a26de148eed015ff203425655056175b03c5f78be5662352925895dbba2f089e`; a complete public HTTPS GET is byte-identical.
  No Android device was attached, so physical-phone scrolling and Directions camera behavior remain UI-unverified.

- 2026-08-11 — Repaired the screenshot-confirmed consumer-map defects for versionCode 4 / versionName
  `0.5-webview-navigation-fixes`. Apple selections now retain the consumer page's native annotation, while search and
  long-press selections use native `MarkerAnnotation` instead of the custom gray/brown DOM balloon. Directions uses
  a real `mapkit.Padding`, reduced sheet clearance, clamped route reveal progress, and a real `DOMPoint`; scrolling,
  zooming, rotation, and the north compass are explicitly enabled. Invalid sheet-reopening sentinels are rejected
  before calculating the WebView touch boundary, so they cannot block the entire map. The blue **+ Add Stop** row now opens place search
  and appends the selected result instead of collapsing the sheet and arming a map tap. GO keeps route progress,
  arrow, camera follow/pause, and the navigation overlay on the same consumer Apple WebView, removing the separate
  blank MapLibre renderer and its dependency/style asset. Files: `ConsumerMapController.kt`, `AppleMapsScreen.kt`,
  `RouteStopSearchTest.kt`, Gradle metadata, README, and removed navigation-renderer files. The full 34-test JVM
  suite, Android lint, and debug assembly pass; the APK contains no MapLibre library/style payload. Canonical artifact:
  `applemaps-consumer-nav-debug.apk` (27,771,426 bytes; SHA-256
  `3fe523af738ee1026de69bfe0e7407ce3140a4bbd3a8aadff90317f358af0b47`). No Android device was attached, so the
  physical-phone Directions → Add Stop → GO path remains UI-unverified in this build pass.

- 2026-08-11 — Prepared the complete project for its initial private GitHub publication. Local copy-verification
  manifests are excluded because they contain machine-specific absolute paths and are not application inputs; source,
  tests, Gradle wrapper, documentation, and the canonical debug APK remain included.

- 2026-08-11 — Fixed the audited consumer-navigation defects in the `com.example.applemaps.consumernav` build.
  Directions now require a real current-location origin instead of silently routing from the map center; provider
  failures surface in the Directions sheet instead of falling through to a fabricated demo route; rapid reroutes are
  generation-guarded so stale responses cannot overwrite newer options; Google Routes receives via stops; and GO
  starts turn-by-turn only after the navigation engine accepts the selected route request with the same via stops and
  selected alternative index. Bottom-sheet motion now invalidates older in-flight height animations before they can
  keep writing stale offsets. Route-details maneuvers use structured type/modifier metadata, U-turn icons no longer
  render as down arrows, instruction text fallback only checks action prefixes, search/result glyphs use bundled
  vector resources, and unsupported Transit/Traffic/3D controls are no longer visible in Choose Map. WebView console
  diagnostics now auto-upload message/source text. The manifest sets an explicit launcher icon and disables backup.
  With `ANDROID_HOME=/opt/android-sdk`, `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass. Package metadata
  is versionCode 3 / versionName `0.4-navigation-fixes`. Artifact: `applemaps-consumer-nav-debug.apk`
  (53,392,793 bytes; SHA-256 `939d7cfd34d794c4a82dfc107576af037115c43caef3b306c4e9abd3c28e7217`).
  Physical-phone GO/sheet gesture behavior
  remains UI-unverified from this box.

- 2026-08-11 — Refreshed the canonical debug APK after a read-only navigation/Directions audit. No application
  source changed. With `ANDROID_HOME=/opt/android-sdk`, all 27 JVM tests passed, Android lint completed without a
  blocking error, and debug assembly succeeded. Artifact: `applemaps-consumer-nav-debug.apk` (53,391,058 bytes;
  SHA-256 `8d8998a40e38b33b091aba004a785fe674d9c2efa70e18e1c77b86beaf34b61e`). Runtime behavior was not re-tested in
  this audit build.

- 2026-08-10 — Split browsing/preview and active-navigation renderer ownership. Consumer `maps.apple.com` remains
  mounted for browsing and Directions route preview; the green GO action now crossfades into the preserved custom
  MapLibre navigation renderer, which owns the existing route, arrow puck, 58-degree heading-follow camera, gesture
  pause/recenter behavior, and native top-right compass. The compass no longer fades at north, and its SDK-owned tap
  action resets bearing to zero. The navigation style retains the existing Apple-colored presentation but is
  restricted to OpenFreeMap raster/vector sources, with all Mapbox sources/layers and tokens removed. Directions
  route fitting now converts Android physical sheet padding to WebView CSS pixels before Apple's route-bounds
  framing. Convenience Store, Grocery, Supermarket, and Store/Shop selections now reach their existing colored icon
  families instead of the generic gray fallback. Files: `NavigationMapSurface.kt`, `AppleMapsScreen.kt`,
  `RouteLayer.kt`, `navigation_style.json`, Gradle dependency/version metadata, README, and focused framing tests.
  The full debug JVM suite, Android lint, and assembly pass. The canonical root APK is version
  `0.3-navigation-renderer`. Android 15 cold-launch, consumer Apple tiles, native search entry, and a live Apple
  place suggestion were exercised without an app fatal exception or ANR; the emulator could not activate the
  non-focusable suggestion, so GO/follow/compass/Exit remain explicitly UI-unverified.

- 2026-08-10 — Corrected the consumer-map interaction boundary without changing the copied UI. A touch beginning
  inside the active base/place/Directions sheet or system navigation area no longer reaches the underlying WebView,
  while a gesture beginning on the visible map retains its complete Android event stream. App-owned pins, the user
  location dot, navigation arrow, route labels, and destination now update each display frame only between the
  consumer renderer's camera-start/camera-end events, eliminating their drag-then-jump behavior without an idle loop.
  Apple place selection is programmatically deselected after its data is bridged so the custom animated marker owns
  the selected-pin appearance and the underlying Apple POI remains on the map. The full JVM suite, Android lint, and
  debug assembly pass. On the Android 15 emulator, the real Compose focus/action path opened Directions and rendered
  its route sheet/fastest-route label; a live renderer drag moved the custom pin on every sampled intermediate frame.
  Physical touchscreen verification of sheet-to-map swiping and the location dot remains pending because this
  emulator exposes no functioning touchscreen injection path.

- 2026-08-10 — Created this independent `applemaps-consumer-nav` application with package
  `com.example.applemaps.consumernav`, leaving the source `applemaps-nav-lean` project unchanged. The existing
  Compose interface, sheets, search/place flows, custom marker artwork, directions providers, route presentation,
  and navigation UI were copied intact; only the basemap boundary was replaced. A persistent WebView now displays
  the consumer `https://maps.apple.com/` page and bridges its own WebGL map to the copied controls, custom marker,
  route overlays, progress line, location dot, and navigation arrow. The app supplies no MapKit developer token and
  includes no MapLibre dependency or bundled MapLibre/Mapbox style. Consumer-page Standard, Satellite, and Hybrid
  map types are bridged; Transit falls back to Standard, while the consumer renderer exposes no equivalent pitch,
  traffic toggle, or 3D-building toggle. `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass. The Android 15
  x86_64 emulator displayed the live Apple basemap; the preserved map picker opened; and a real click on the
  collapsed custom marker reopened the same `Mannerheimintie` place card, corroborated by automatic diagnostics.
  Artifact: `applemaps-consumer-nav-debug.apk`.

- 2026-08-10 — Routed empty-map Android touch streams through the transparent Compose map surface to the persistent
  consumer WebView. Existing Compose controls, sheets, cards, and modal scrims remain later siblings with their own
  hit targets; loading/error states do not forward input. This restores native WebView pan, pinch, rotate, tap, and
  long-press handling without synthesizing gestures or remounting the Apple renderer.

- 2026-08-10 — Made the **3D Buildings** preference authoritative across camera movement and Standard-style reloads.
  The zoom-threshold controller is now the sole writer for the `building-3d` layer: it receives the effective
  `buildings3D || navMode` state, parks the extrusion at every zoom while disabled, cancels an in-flight height
  animation when the preference changes, and performs no repeated style write while the zoom phase is unchanged.
  This removes the former race where the settings sheet hid buildings once and the next zoom event made them visible
  again. The focused state/ownership regression test, debug and release Kotlin compilation, and ARM64 debug assembly
  succeed. The complete JVM run remains 61/62 because the separate, pre-existing
  `MapPaletteStyleTest.greenFillsUseTimedThresholdTargetsInsteadOfZoomPositionOpacity` green-style workstream is
  currently failing; the physical-phone off→zoom gesture remains unverified. Artifact:
  `applemaps-nav-lean-arm64-debug.apk` (42,929,485 bytes; SHA-256
  `f6a3b4f6a9f366ba074136ea98abbf11f25b4cf6d998445184cc95fe82ba2ab0`). A complete public GET is
  byte-identical and returns HTTP 200 with APK MIME at
  `[removed]`.

- 2026-08-10 — Look Around preview dismissal now collapses as a true container transform. The travelling card is
  clipped to a real-pixel rounded rect that interpolates between the map thumbnail's 150x96 / 14dp outline and the
  preview's own 16dp outline, and the card inside it carries a single uniform scale, so the street imagery crops
  instead of stretching and the collapse lands on the thumbnail's exact outline. Opening keeps its 320ms ExpoOut
  easing; closing runs 300ms on the Exit curve so it accelerates away rather than reversing an ease-out through its
  final frames. The floating map entry's remount is gated on the transition's own completion (`onIdle`) instead of a
  fixed 320ms delay, so the thumbnail cannot reappear under a card that is still moving. Files: `ui/LookAround.kt`
  (LookAroundEntryPreviewTransform rewritten; LookAroundPreviewCard's own clip removed so the transform owns the
  rounding), `ui/AppleMapsScreen.kt` (onIdle wiring; delay-based effect removed). `compileDebugKotlin` succeeds.
  Physical-phone motion is the final check.

- 2026-08-10 — Added **Transit** to the Choose Map selector without increasing the modal height. Transit keeps the
  Standard basemap and enables four independent native vector sources for tram, subway/light rail, commuter rail,
  and long-distance rail. Line casing, route strokes, station fills, and station outlines consume each OSM-derived
  feature's supplied color and width; no fixed route palette or recolored physical-rail substitute is used. The four
  ordinary gray rail layers are hidden only while Transit is active and restored on exit. Empty or missing tiles for
  one network do not block the others, all sources are removed outside Transit, and attribution names OpenStreetMap
  contributors and the University of Freiburg. Existing auto-upload diagnostics report install/remove state without
  locations or tile URLs. All 60 JVM tests pass; ARM64 debug assembly and release Kotlin compilation succeed.
  Artifact: `applemaps-nav-lean-arm64-debug.apk` (42,387,854 bytes; SHA-256
  `f1c71034ac0527ae913e0d8e4d212b04093cba19d7e577979837983940a7e4b4`). A real public HTTP GET returned the same
  bytes with APK MIME and attachment filename. Physical-phone rendering remains the final UI check; no emulator was used.

- 2026-08-10 — Look Around now starts bounded panorama preparation as soon as selected-place coverage resolves,
  while the normal map and place card remain usable. The fullscreen GL viewer shares that exact in-flight result,
  so opening early cannot duplicate the six-face request and opening after completion can upload the prepared frame
  immediately. Selection changes cancel stale work and recycle partial face decodes; Apple first-frame faces are
  bounded to 1024 pixels, with the existing 1536 quality upgrade retained for capable GL hardware. Panoramax SD
  panoramas use the same background path. Existing auto-upload diagnostics report preparation readiness/use/failure
  without place names, coordinates, signed URLs, or search queries. Physical-phone first-open latency remains the
  final UI check; no emulator was used. All 57 JVM tests pass; ARM64 debug assembly and release Kotlin compilation
  succeed. Artifact: `applemaps-nav-lean-arm64-debug.apk` (42,371,470 bytes; SHA-256
  `fc6fcdfa89891edcc83d4dff3cac7c2d4f5500293af13880f65705d9815fe4e7`). A real HTTP GET returned the same
  bytes with APK MIME and attachment filename.

- 2026-08-09 — Replaced the Tier-1 hard zoom cuts that made parks, buildings, addresses, POIs, shields,
  airports, one-way arrows, and crosswalks pop on or off. A table-driven `ZoomThresholdFadeController`
  now parks distant layers, pre-arms their geometry invisibly, and fires one complete 300 ms transition
  only when its threshold is crossed; camera zoom never leaves a layer partially faded. Zoom-stepped
  green, address, and POI feature groups are split into stable, mutually exclusive bands, while 3D
  buildings remain opaque and animate height/base so roads and labels cannot show through them. Address
  and POI taps plus render diagnostics cover every generated band, and `ZOOM_FADE` events use the existing
  automatic upload channel. All 55 JVM tests pass, release Kotlin compilation and debug APK assembly
  succeed. Android lint reports four pre-existing project errors outside this change and no finding in
  the new controller/band implementation. Artifact: `applemaps-nav-lean-debug.apk` (42,884,899 bytes;
  SHA-256 `e9e7f95db938e5c1a74c0ba2c4a078c038bdfa4bf9bc467b48f1c1042c92a696`). Physical-phone
  appearance and timing remain the final UI check.

- 2026-08-09 — Replaced the single shared neighborhood-label zoom ramp with four mutually exclusive priority cohorts.
  Higher-priority neighborhood names now fade in first at zoom 10, followed by three smaller groups through zoom 11.05;
  zooming out removes the same groups in reverse. The cohorts use stable Mapbox `symbolrank` ranges, retain the existing
  `filterrank <= 4` density cap and all typography/collision styling, and use the existing 300 ms threshold transition.
  Map data sources, searches, place details, satellite mode, and Look Around are unchanged. All 56 JVM tests pass;
  ARM64 debug assembly and release Kotlin compilation succeed. Artifact: `applemaps-nav-lean-arm64-debug.apk`
  (42,371,470 bytes; SHA-256 `a38818a1a85eb84462e0df8cacaa593da37140fd9be714dfc3370e6b02fc100c`).
  A real HTTP GET returned the same bytes with APK MIME and attachment filename. No emulator was used; physical-phone
  cadence remains the final visual check.

- 2026-08-09 — Corrected native Look Around rendering behind the viewer and at both poles. The four side-camera
  faces now wrap across the ±180° seam, and the top/bottom faces use their full Apple yaw/pitch/roll calibration
  instead of being treated as side bands. A real six-face calibration now has regression coverage over a full-sphere
  2° grid, including explicit rear, straight-up, and straight-down assertions. Face downloads, HEIC decode,
  texture selection priority, gestures, and Panoramax fallback are unchanged. All 51 JVM tests pass; debug assembly
  and release Kotlin compilation succeed. Artifact: `applemaps-nav-lean-arm64-debug.apk` (42,355,130 bytes;
  SHA-256 `72ed90e5eee2fe9f14ee239b9a718ce570141a102962b6e99888936380096289`). Physical-phone GL output remains
  the final visual check; no emulator was used.

- 2026-08-09 — Added Standard-map neighborhood labels at city-scale zooms from the already-loaded Mapbox Streets
  `place_label` data. Borough-ranked subdivisions are excluded to avoid duplicating the existing city/borough layer;
  candidate ranks are bounded before collision placement, and OpenMapTiles quarter/neighborhood records are excluded
  at higher zooms so each neighborhood has one label owner. Satellite and map-data routing are unchanged. All 48 JVM
  tests pass; debug assembly and release Kotlin compilation succeed. Artifact: `applemaps-nav-lean-arm64-debug.apk`
  (42,355,130 bytes; SHA-256 `9ef2bd2060046f3402802b89109d5ba67fd5bb87fbe5677f6f8d89c094b0cf52`).

- 2026-08-09 — Completed the missing boxless Apple-data parity. Rich selection now prefers the direct
  Apple place-page client so its hours, reviews, and photos cannot be bypassed by a partial optional provider
  response; escaped Apple photo URLs are normalized and photo decode outcomes auto-upload without logging URLs
  or queries. Native Look Around now performs the former box's 3×3 z17 Apple coverage lookup, nearest-within-150 m
  selection, protobuf parsing, and authenticated six-face HEIC retrieval inside the APK, with the existing calibrated
  GL renderer and API 26+ decoding; direct Panoramax remains the fallback. No `/appleplace` or `/applelookaround`
  relay route was restored. All 47 JVM tests pass and release Kotlin compiles. Artifact:
  `applemaps-nav-lean-arm64-debug.apk` (42,354,926 bytes; SHA-256
  `a97297ef77bcb07c9969a23e593f856c14d2cd0f2f8a7195fcc21396656d8cdd`). Physical-phone photo and
  panorama rendering remain runtime verification.

- 2026-08-09 — Produced an ARM64-only physical-device diagnostic build. Debug packaging now filters every native
  dependency to `arm64-v8a`, removing the emulator/legacy ABI payloads contributed by MapLibre, Ferrostar,
  AndroidX Graphics, and JNA; release dependency behavior remains unchanged. The custom MapLibre diagnostic AAR
  was rebuilt specifically for ARM64, and its packaged `libmaplibre.so` is byte-identical to the APK copy. All 44
  JVM tests were freshly executed with zero failures or errors. Artifact: `applemaps-nav-lean-arm64-debug.apk`
  (42,004,426 bytes; SHA-256 `349dcf13c3f267f76ecf870f035473f60c4fe7a0899119f6684c1c869fddf9fa`).
  The byte-identical public copy returns HTTP 200 with Android APK MIME and an attachment filename at
  `[removed]`;
  a real Chromium Download-button click saved and hash-matched the APK. Physical-phone runtime remains user verification.

- 2026-08-09 — Added a debug-only, renderer-native causal zoom diagnostic using the exact MapLibre Native 13.3.1
  source and a pinned Perfetto SDK. Each user camera gesture records correlated renderer cycles/frames, tile
  generation and ownership, held/pending tile state, per-layer paint evaluation and transition state, symbol
  placement/opacity, and Vulkan draw submission. Capture is armed before movement, ends after the idle tail, writes
  through a bounded in-process ring buffer, finalizes off the renderer/UI threads, and auto-uploads through Android's
  validated network to a separate bounded binary collector. Failed uploads remain in a bounded private spool; release
  builds retain the stock MapLibre dependency and no-op trace controller. Redundant source callbacks and in-motion
  rendered-feature scans were removed so diagnostics do not create the workload being measured. Debug tests, APK
  assembly, and release Kotlin compilation pass. The packaged ARM64 library is byte-identical to the corrected native
  AAR and exports trace start/stop. Physical-phone pop attribution awaits the user's drive. Diagnostic artifact:
  `applemaps-render-trace-debug.apk` (97,892,533 bytes; SHA-256
  `99bca54351114c7870a9f400abaefa13c8cbc676ea3d227f84414a5143708fa5`). The byte-identical public copy returns
  HTTP 200 with Android APK MIME at `[removed]`.

- 2026-08-09 — Corrected the boxless place-data implementation to preserve the original Apple provider and rich
  card behavior. The former box-side Apple Maps Web autocomplete → place page → `shell-props` parser now runs
  directly inside the Android app, restoring keyless hours, open state, ratings, review snippets, photos,
  amenities, phone, website, locality, and address without `/appleplace` or any other map-data relay. Both tapped
  POIs and selected search results use the on-device Apple fallback after an explicitly configured Places SDK
  client. The separate diagnostics upload path is unchanged.

- 2026-08-09 — Added exact rendered-frame evidence for the remaining zoom-pop diagnosis without changing map style
  or animation behavior. Debug builds arm immediately before the measured integer/custom zoom handoffs, enforce the
  MapLibre 13.3.1 single-snapshot-callback contract, and capture bounded before/cross/+120/+300/+600 ms PNG bursts.
  Snapshot work is one-at-a-time; lossless compression, hashing, bounded app-private spooling, and validated-network
  upload run off the UI/renderer callback. Every request, callback latency, queue/drop, spool, and upload shares the
  existing text trace's session/frame IDs. A separate HTTPS collector at `[removed]` validates PNG type,
  size, dimensions, and SHA-256; writes atomically; and prunes at 1,000 files or 256 MiB without touching the existing
  text collector. Pillow tooling produces contact sheets and enhanced raw differences while warning that camera
  motion contributes to metrics. The entire map-render trace is constructed only in debug builds, and the controller
  also rejects direct non-debug entry, so release builds do not attach its camera/tile/render listeners. All 44 JVM
  tests and two collector/analyzer tests pass; debug assembly and release Kotlin compilation succeed,
  and an emulator runtime-only smoke crossed zoom 10, uploaded five genuine 720×1280 frames, drained the app spool,
  and left Maps resumed without a fatal or ANR. Animation quality remains physical-phone unverified. Debug artifact:
  `applemaps-nav-lean-debug.apk` (81,213,397 bytes; SHA-256
  `ede1e884dda37b55d6fefae4fb3911e78fa4a1e37f368c273729ccbcf90faf96`). A byte-identical public copy is served
  as `applemaps-frame-diagnostics-debug.apk`; Caddy returns HTTP 200, Android APK MIME, and the same content length.

- 2026-08-09 — Removed the box relay from production place and street-imagery data. Basic search/reverse geocoding
  now stays direct to Nominatim; native Look Around discovery and image delivery stay direct to Panoramax; and
  optional rich details, reviews, and resolved photo URIs use Places SDK for Android (New) with Android-restricted
  key support. Google place, review-author, and photo-author attributions are preserved as clickable card/gallery
  credits. The separate `[removed]` diagnostics upload is unchanged. All 41 JVM tests pass and debug assembly
  succeeds. Packaged-Dex inspection found the direct Nominatim/Panoramax URLs and neither removed relay route. On
  redroid, the exact APK launched, returned live Rue de Rivoli search results, opened the selected basic place card,
  and loaded its Panoramax street-image thumbnail with no app fatal or ANR found in logcat. The keyed rich Google
  branch is compile-verified but runtime-unverified because this checkout has no Places key; the fullscreen street
  viewer was not opened because this redroid instance's pointer injection is stuck. Debug artifact:
  `applemaps-nav-lean-debug.apk` (81,213,397 bytes; SHA-256
  `3e39a3e330fe1e5def69931b57436fba1b8383a712bbcf73865aa5ff8a9b10b1`).

- 2026-08-09 — Added phone-driven map-render diagnostics with automatic box upload. Camera thresholds, aggregated
  tile/source activity, frame timing, style ownership, glyph/sprite/image events, and visible counts for street
  names, parks, icons, and addresses now share a sequenced process session. Events enter a bounded drop-oldest
  queue, are batch-spooled in app-private storage, and upload only through Android's validated default network;
  failed uploads replay after connectivity or process recovery. Tile callbacks only increment counters, and
  visible-feature queries run only after a relevant completed render or idle boundary. Added a reproducible static
  animation-coverage audit: 34 files scanned, 56 explicit specifications, 2 definite omissions, and 31 heuristic
  candidates. All 39 JVM tests pass; debug assembly succeeds; redroid launch and two touch gestures produced
  collector batches while the private spool returned to zero, with no app fatal or ANR. Debug artifact:
  `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `09d44e70cf7c0845f83c00ce9fc13dfdbfac5fbd13f9ec3872d1ccc58433b886`).

- 2026-08-09 — Replaced the rejected zoom-position vegetation opacity ramps with threshold-triggered timed fades.
  Mapbox Terrain is capped at z8 and retained as overscaled outgoing geometry; crossing zoom 9 now starts one
  300 ms native MapLibre crossfade to true Streets/OpenFreeMap z9 detail, while wood and cemetery use the same
  complete time fade at zoom 10. Stopping at 9.01 cannot leave a half-visible state, and reversing across the
  threshold retargets the running transition. The camera callback compares target states only and performs no
  rendered-feature query, network work, bitmap work, or repeated layer mutation while remaining on one side.
  Frida proved Terrain makes zero z9 requests and renders z8/overscaled-z9 while the detailed sources parse z9.
  A fixed-camera screen recording captured multiple intermediate green frames after the 9.01 crossing before the
  final state. All 39 JVM tests passed, assembly succeeded, the packaged/source styles are byte-identical, and the
  installed Maps process remained resumed without an app fatal or ANR. The separate two-pointer helper could not
  complete because Android retained stale pointers from an earlier interrupted injector; physical-phone pinch
  acceptance remains UI-unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `c035baf4e3a0086b82fffe2073b8b09d7e75ad4d9b41591e6c36c1b239f405db`).

- 2026-08-08 — Corrected the extreme zoom-out framing and rebuilt green ownership from the actual vector schemas.
  The map now enforces a continental minimum zoom of 2.25, raised only when a larger viewport needs more world
  coverage; normal regional panning and zooming remain unrestricted. A low/mid-zoom Mapbox Terrain v2 vector fill
  supplies real wood, scrub, grass, and crop coverage, while Streets v8 national-park and green-landuse polygons
  hand off to detailed OpenFreeMap park/grass/cemetery owners. Semantic green layers now render above residential
  land but below water, and their detailed entrances span 4→8, 8→10, and 10→12 instead of popping across short
  bands. No raster continent tint, custom mask, per-frame feature query, or camera-move listener was added. All 39
  JVM tests passed; debug assembly completed; and source/APK styles are byte-identical. In redroid, the exact APK
  remained resumed after six genuine two-pointer zoom-out gestures and stopped at a filled continental frame with
  generalized vegetation visible; no app fatal or ANR occurred. Physical-phone park-transition smoothness remains
  UI-unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `112e8aefdc4d132c790626c22faeac9ba72727a9061dae0528be7db90c137fe0`).

- 2026-08-08 — Lengthened the two park-green camera fades after the prior 0.45-zoom bands were not perceptible on
  the physical phone. Dedicated `park` now fades from zoom 4→5 and schema-wide `landcover_grass` from zoom 8→9,
  beginning at their measured data-availability boundaries. Cemetery, woods, colors, filters, sources, layer order,
  water coverage, runtime code, and every non-park style property are unchanged. An exact normalized comparison
  reports only the two opacity endpoint changes. All 38 JVM tests passed and debug assembly completed; source and
  packaged styles match. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `bfb647d96733546e42053c03a106fbe9c7af2a738cbb3e809108b0f4e9a3c616`). Perceived fade remains phone-UI
  unverified for this new build.

- 2026-08-08 — Reverted the rejected railway thickening while preserving the earlier single-source cleanup.
  Mapbox ground and bridge continuous rail centerlines are restored from 4→8px to their prior 0.5→1px width,
  and their existing 4→8px short-dash detail layers are visible again. All six legacy OpenMapTiles rail layers
  remain hidden. An exact comparison with the previous delivered style reports only those four rail leaves; all
  non-rail style content, including the new 3D ownership ordering, is unchanged. All 38 JVM tests passed and debug
  assembly completed; source and packaged styles match. Debug artifact: `applemaps-nav-lean-debug.apk`
  (73,193,255 bytes; SHA-256 `e75681ff7a984f103a1404de4c22aed34c020926515eb42881d0749582e37d37`).
  Physical-phone railway appearance remains UI-unverified.

- 2026-08-08 — Corrected 3D building occlusion through MapLibre layer ownership and ordering. Existing building
  separators, crosswalks, one-way arrows, road names, and house numbers now render after the final base road but
  before `building-3d`; the extrusion is fully opaque. Water labels, shields, place labels, POIs, pins, routes, and
  navigation overlays remain above it. Runtime traffic now explicitly anchors below `building-3d`, retaining the
  prior first-label/end fallback for styles without the extrusion. No mask, per-frame query, camera listener,
  duplicate source, or other rendering workaround was added. All 38 JVM tests passed, debug assembly completed,
  and source and packaged styles match. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `88d51b99d0725f4e6eae331b6f395bf058bf541ab1bd7bf007179277e2a5aa11`). Physical-phone 3D/flat appearance
  remains UI-unverified.

- 2026-08-08 — Removed the rejected world-scale Natural Earth raster tint and corrected missing green coverage
  through the vector schema instead. The former park-only landcover rule now paints every
  `landcover.class=grass` polygon, covering ordinary grass, gardens, meadows, recreation grounds, and parks without
  duplicate park overdraw. A separate `landuse.class=cemetery` fill uses Mount Zion Cemetery's measured
  zoom-10 source boundary and a 10→10.45 renderer-native opacity entrance. Existing dedicated parks retain their
  4→4.45 entrance, schema-wide grass retains 8→8.45, and vector water now renders above all land/vegetation fills
  while remaining below ice-shelf and glacier overlays.
  The exact cream base, green color, residential tint, wood fill, sources, roads, labels, POIs, and Android runtime
  code are unchanged. All 35 JVM tests passed, debug assembly completed, and the source and packaged styles match.
  Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `ffd4b6a1475ca8816cc5ff21654a00850b91d12f5fb96c726b2a3a5ac31a0acf`). Physical-phone appearance remains
  UI-unverified.

- 2026-08-08 — Added the missing building-detail fade seen between the supplied Long Island City zoom frames. The
  live OpenFreeMap contract begins building geometry at zoom 13, while the style previously declared zoom 12 with
  no fill opacity; the ineffective early boundary allowed zoom-13 building tiles to enter fully opaque. The
  `building` layer now truthfully begins at zoom 13 and fades renderer-side from opacity 0→1 across zoom 13→14.
  Its source, source-layer, order, colors, outline, antialiasing, and every other layer/runtime path are unchanged.
  The exact previous-APK comparison reports only the minzoom and fill-opacity changes. All 35 JVM tests passed and
  debug assembly completed; source and packaged styles match. Debug artifact: `applemaps-nav-lean-debug.apk`
  (73,193,255 bytes; SHA-256 `370db9ca16baf6a875e2c2e2cdb7d3835c74fee8a2a63295d3118d9bd6982786`).
  Perceived transition smoothness remains physical-phone UI-unverified.

- 2026-08-08 — Replaced the still-three-track Broadway railway treatment with one solid generalized Mapbox
  corridor per structure. The continuous ground and bridge centerlines now use the former 4→8px detail width;
  both short-dash track-detail layers and all six legacy OpenMapTiles rail layers are hidden. Sources, filters,
  colors, opacity, order, station/transit symbols, roads, labels, and runtime code remain unchanged. A comparison
  with the previously served APK reports exactly six intended style leaves. All 34 JVM tests passed and debug
  assembly completed; the packaged style matches source. Debug artifact: `applemaps-nav-lean-debug.apk`
  (73,193,255 bytes; SHA-256 `95d4ded76521834d6eada77b5f076b529de5d954cb2e78a69e987987d72eb005`).
  The same physical-phone Broadway view remains required before accepting the optical result.

- 2026-08-08 — Restored the approved progressive address-number staging and reduced bus-stop symbol cost without
  changing store or other location eligibility. Address candidates now progress by numeric multiples of 8/4/2/all
  at zooms 16.5/17/18/19 with matching renderer-native opacity ramps. Bus POIs are absent below zoom 17, then capped
  at ranks 2/4/7/11/16/24 through zoom 22; their icon scales only 0.44→0.60, their label is 9sp, and their collision
  priority is lower. Every non-bus POI retains the exact 8/16/28/42/58 rank ceilings, and the existing requested-
  image listener plus 384 KiB bitmap cache are unchanged. All 34 JVM tests passed and debug assembly completed;
  source and packaged styles match. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `522eb387d363a2b3c29f5ec95bc5344a67b37dd5c99d7fda8c3c7dbfa8dc3392`). Physical-phone icon balance,
  address staging, and frame time remain UI-unverified.

- 2026-08-08 — Added the missing continent-scale green land/terrain coverage used at world zoom. The Standard
  style now renders its already-declared Natural Earth shaded raster beneath park and vector-water fills through
  zoom 5, then fades it natively to zero by zoom 7 so `#F6F3EA` remains the street-level land base. The existing
  park, water, road, label, POI, and cache contracts are unchanged. All 31 JVM tests passed and debug assembly
  completed; the packaged style matches the source. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255
  bytes; SHA-256 `21abd32384be4b96e8ce77ef45d5fee5eefde89e7c77cd31a648f7801dd1617b`). Physical-phone
  appearance and the separately observed partial-tile rectangles remain UI-unverified.

- 2026-08-08 — Added native zoom-out opacity ramps to both Standard-map park polygon owners. The dedicated `park`
  source fades across zoom 4.0→4.45, matching its declared source boundary; `landcover_park` fades across
  8.0→8.45, matching the live Central Park polygon's measured first-containing tile at zoom 8. Colors, filters,
  source ownership, water ordering, every non-park style property, and the prior Mapbox-only rail change remain
  unchanged. All 30 JVM tests passed and debug assembly completed. Debug artifact: `applemaps-nav-lean-debug.apk`
  (73,193,255 bytes; SHA-256 `a42349cd144a3e42acddc97c9ae3f7aa9e64d0a5df40aa7417f1b2fe8ab90192`).
  Physical-phone fade perception remains UI-unverified.

- 2026-08-08 — Reduced Standard-map railway rendering to one vector-tile source. All six OpenMapTiles rail,
  transit, service, and companion dash layers are now non-rendering; the existing Mapbox Streets ground and bridge
  rail layers remain byte-identical, as do station/transit POIs and every non-rail style property. A regression test
  guards both sides of the single-source contract. All 29 JVM tests passed and debug assembly completed. Debug
  artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `aa20b94b0382d2c9838c31562f165edc491059a8a70354a32974b1597fd6a6a8`). The resulting corridor appearance
  remains physical-phone UI-unverified.

- 2026-08-08 — Continued all 16 Standard-style road and bridge interior widths from zoom 18 through zoom 22 so
  close-zoom roads enlarge with the existing official crosswalk size curve instead of freezing while crosswalks
  grow. Existing zoom-18 widths and hierarchy are preserved; casing, low-zoom layers, crosswalk assets/expressions,
  routes, traffic, POIs, and labels are unchanged. A regression test enumerates every target layer. All 27 JVM
  tests passed and debug assembly completed. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes;
  SHA-256 `1d70c74294b8f9685808ac77fd6303c28c908745100a23abff91088c5cace904`). Physical-phone appearance remains
  UI-unverified.

- 2026-08-08 — Added the missing standard Gradle wrapper and pinned it to the project's AGP-compatible Gradle
  9.4.1 binary distribution with the official SHA-256 checksum. Both platform scripts, the wrapper JAR, and its
  properties are checked in together; `./gradlew --version`, all 26 JVM tests, and debug assembly completed through
  the wrapper. Android plugins, repositories, dependencies, application code, and the APK were not changed by this
  build-infrastructure addition.

- 2026-08-08 — Replaced eager registration of all 63 bundled POI bitmaps with MapLibre Native's requested-image
  lifecycle. The single `MapView` listener is installed before the first style, synchronously supplies only known
  POI assets requested by the renderer, uses a 384 KiB allocation-byte LRU for recent pan-back reuse, and is removed
  before teardown. MapLibre's native viewport tile cover remains responsible for off-screen feature culling; no
  camera listener, feature scan, density filter, POI mapping, or Standard style change was added. All 26 JVM tests
  passed and debug assembly completed. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `1d2a2d5458913178606ac50beb364f4090317dde57c833166fc20cfb17bc1e04`). Physical-phone icon visibility and
  frame-time remain UI-unverified.

- 2026-08-08 — Reverted the POI/address density and eligibility work to the last pre-density rendering contract
  from `1bccb0b`, as requested, without attempting another icon repair. The original address/POI eligibility,
  sizing, and runtime opacity logic are restored, and the later density-only test was removed. The separately
  approved close name placement, raw Look Around forwarding, and corrected 3D-building layer order remain in place.
  All 24 remaining JVM tests passed and debug assembly completed. Debug artifact: `applemaps-nav-lean-debug.apk`
  (73,193,255 bytes; SHA-256 `95d79b41cf12ea9cef5468cd4c2eed47e2a38c7a231d5045b5e8b9d7b580094e`).
  Physical-phone icon rendering remains UI-unverified.

- 2026-08-08 — Corrected pitched 3D building occlusion without changing geometry or visual styling. The extrusion
  and building-separator layers previously sat beneath every tunnel, road, rail, and bridge layer, allowing later
  base roads to paint over building faces that were spatially behind them. Those two byte-identical layers now sit
  immediately above the final base-road layer and below crosswalks, one-way arrows, labels, traffic, routes, POIs,
  and pins. A source-contract test locks the new order; all 28 JVM tests passed and debug assembly completed.
  Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `02f64f9e5b8620582e5f80c470c1d7af81f35d85dbd82fd04ace88e4e81af864`). Pitched phone rendering remains
  UI-unverified until the new APK is installed and viewed.

- 2026-08-08 — Corrected the phone-observed disappearance of every ordinary location icon. The store/bus tuning
  had placed three independent zoom curves inside the single `poi-dot` filter, which violates MapLibre Native
  13.3.1's one-zoom-curve expression contract and caused the renderer to reject the layer. The filter and matching
  opacity now use one outer zoom curve with data-only store/general/bus branches, preserving the same pre-layout
  candidate budgets, smaller bus treatment, icon/name fading, `poi-dot` tap ID, and no-listener performance design.
  All 27 JVM tests passed and debug assembly completed. Debug artifact: `applemaps-nav-lean-debug.apk` (SHA-256
  `799c0d05043fcc5f714d0c818b1af66ac07b59ec41e2f0a0dd5778bb15a399d2`); final icon rendering remains
  physical-phone UI-unverified.

- 2026-08-08 — Replaced the primary Apple Look Around transport with a raw six-face HEIC manifest. The box bridge
  now authenticates each fixed Apple face request and relays its original bytes unchanged; it supplies the matching
  per-face calibration for an Android 28+ six-texture GL path, which decodes at a bounded device-local target size.
  The old generated 2K/4K JPEG endpoints remain only as the API 26–27 or raw-decode fallback. The raw relay was
  verified against an authenticated upstream face byte-for-byte (1,235,538 bytes; SHA-256
  `9af7f51676977ceb8cbf6ece4e50ed115806d3711d3ab80f07d6b2fce7abf2e0`). Phone rendering and HEIC decode behavior
  remain UI-unverified pending a physical-device Look Around session.

- 2026-08-08 — Fixed the flat fullscreen Look Around path, which previously requested only `sdUrl` while the
  360° GL path alone upgraded to HD. Flat fullscreen now keeps SD as its first paint, requests a distinct HD URL at
  4096×2048 only after SD succeeds, and swaps the pixels only after Coil decodes HD successfully; an HD failure keeps
  SD visible. Both image layers share the existing pan/zoom transform. One bounded auto-uploaded diagnostic records
  each HD success/failure, so the app’s real result can be read from the box without user log relay. All 26 JVM tests
  passed and debug assembly completed. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `f67b565d41804fcd64f7e1cd56d069ddb56bcf6901edb131ee61e3ac09eb4220`); physical-phone sharpness and memory
  behavior remain UI-unverified.

- 2026-08-08 — Increased close-zoom store coverage without broadening the general POI budget: named retail classes
  now expand from rank 12 at zoom 17 through rank 96 at zoom 21, while every non-store category retains its existing
  2→58 ceiling. Bus POIs now begin at zoom 16, use their own lower 2→36 budget, a smaller 0.48→0.68 icon scale,
  and a 10sp label instead of the shared 12sp label. This is entirely pre-layout style eligibility: no source,
  listener, global POI scan, sprite registration, tap behavior, or tile-cache policy changed. All 25 JVM tests
  passed and debug assembly completed. Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `d364fed50480a8d30c77639307ec599b47073db66f39a8d95fa2f4263bd803ff`); physical-phone density, icon balance,
  and frame-time remain UI-unverified.

- 2026-08-08 — Made place-gallery images zoomable with bounded 1×–4× pinch zoom and one-finger pan while
  zoomed; the existing 1× horizontal photo paging remains intact. Look Around now uses a single measured
  lower-left-entry↔preview geometry transform in both directions, defers re-showing the entry until its reverse
  finishes, stays hidden while Directions is open, and returns fullscreen Done to a stable preview. The primary
  Directions glyph is 18dp inside its unchanged action button. All 23 JVM tests passed and debug assembly completed.
  Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `8c90134c4ff57913e23d175395e8990c87a9675e4b99234c0116e49eab73b84f`); physical-phone gesture and animation
  appearance remain UI-unverified.

- 2026-08-08 — Moved ordinary map place names closer to their icons without changing label placement policy. The
  `poi-dot` icon/name gap is now 1.0em (from 1.45em), while airport labels use 1.15em (from 1.6em); both retain
  automatic left/right collision placement, optional text, fonts, wrapping, candidate budgets, tap layers, and
  map behavior. All 21 JVM tests passed and debug assembly completed. Debug artifact:
  `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `95b3970149a290ae2bd4e9530f6d526450149f9aef7f174da214dd3bf2302b26`); phone visual spacing remains UI-unverified.

- 2026-08-08 — Added progressive pre-layout density limits for both close-zoom house numbers and every eligible
  Standard-map POI icon category. Addresses now reveal deterministic numeric subsets at zooms 16–18 and the full
  set—including ranges/alphanumeric numbers—at zoom 19; a measured Brooklyn z16 tile progresses through
  41/77/157/334 eligible labels instead of submitting all 334 at first appearance. POI rank ceilings now expand
  from 2 to 58 across zooms 14–21; the measured downtown z14 tile admits 484 candidates at zoom 18 instead of
  1,258 (61.5% fewer before collision/layout). Matching native opacity bands fade newly admitted labels/icons over
  0.45 zoom levels. Existing layer IDs, rendered-feature taps, sources, collision rules, map tile cache, selected
  pins, traffic, routes, and controls remain unchanged. All 20 JVM tests passed and debug assembly completed.
  Debug artifact: `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `efe1ef8a51ccd1b4517ea3c640bad2edd82ece6086999288e0a4387c432f38c2`); physical-phone frame-time and visual
  density remain UI-unverified.

- 2026-08-08 — Corrected the explicitly requested place-action and Directions travel-mode symbols without changing
  button containers, labels, callbacks, or sheet geometry. The blue Directions action now uses a 22dp white circular
  badge containing a fitted 12dp blue turn glyph; More uses a dedicated normalized ellipsis at 18dp. Drive, Walk,
  and Cycle now use caller-specific normalized vectors in 22×17dp, 22dp-square, and 28×18dp image boxes,
  eliminating the clipped car fragments and hairline bicycle. Shared amenity, address-row, and navigation drawables remain
  untouched. All 18 JVM tests passed and debug assembly completed. Debug artifact:
  `applemaps-nav-lean-debug.apk` (73,193,255 bytes; SHA-256
  `159320af993e2d36e77be44f54e665dd5f333c7d8281e077cb511d397ba1ed7c`); physical-phone optical alignment
  remains UI-unverified.

- 2026-08-08 — Changed the Standard map's main land canvas from the stronger yellow-beige `#F8F0E0` to the exact
  requested neutral `#F6F3EA`. Residential land (`#F1EEE6`), 2D buildings (`#ECE9E1` with `#DAD7D0` outline), and
  3D buildings (`#E9E6DE`) remain subtly distinct; building separators now reveal the new base. The dormant
  compatible-Mapbox palette helper is synchronized so it cannot later restore the prior colors. Roads, parks,
  water, labels, POIs, crosswalks, routes, traffic, opacity, geometry, and Satellite remain unchanged. All 18 JVM
  tests and debug assembly passed. Debug artifact: `applemaps-nav-lean-debug.apk` (72,744,781 bytes;
  SHA-256 `f9d6fe5fa05c465121a8d9fe95fa07b665cdaef13e130ac780555eef07b1d4fb`); physical-phone color balance
  remains UI-unverified.

- 2026-08-08 — Replaced the phone-rejected repeated pedestrian-path crosswalk with Mapbox Streets' native
  directional crosswalk contract. Crosswalks now come from one `structure/type=crosswalk` point, require point
  geometry, rotate from the tile's `direction` field, and use the exact three-band/five-band Mapbox sprites at
  their original pixel ratio. This removes the unbounded line-repeat placement that drew dotted boxes beyond the
  road. Both sprites register once per style load; no source, request, listener, camera callback, or per-frame work
  was added. All 16 JVM tests and debug assembly passed. Debug artifact: `applemaps-nav-lean-debug.apk` (72,744,781
  bytes; SHA-256 `68c4d994264072e5cf5d4ad4a499dd49fa4bb03b4041a253863bc1ffb9327ea1`); physical-phone
  roadway placement and orientation remain UI-unverified.

- 2026-08-08 — Corrected the phone-observed place-card and Look Around regressions without changing map style,
  routes, sheet detents, or the fixed sheet timing. Restored the prior semibold turn glyph for Directions at a
  fitted 18dp action size (14dp in the address row), constrained category/locality to one styled ellipsized line,
  and split Hours into an emphasized primary state plus a 12sp normal-weight muted transition inside a fixed-width
  column. Tapping either the imagery thumbnail or binocular fallback now opens the existing intermediate preview;
  only its expand control enters fullscreen. Rich place metadata still refreshes the card but no longer replaces
  the category symbol selected from the original map/search result. All 14 JVM tests and debug assembly passed.
  Debug artifact: `applemaps-nav-lean-debug.apk` (72,744,781 bytes; SHA-256
  `2f545661c151e45bcb438ad38eb82658bbe221953f0c6d4c1ee52a0744c07f68`); physical-phone appearance and touch
  sequence remain UI-unverified.

- 2026-08-08 — Extended smooth zoom-out removal from symbols to the complete road/linear-detail hierarchy. All 59
  scalar-opacity line layers with hard zoom boundaries now use the existing native 0.45-level opacity ramp; the two
  rail-track layers that already contain explicit zoom fades remain unchanged. All 12 icon-bearing symbol layers
  remain covered, including crosswalks, one-way arrows, shields, airports, place dots, and POIs. The pass runs once
  per style load, adds no camera/frame listener or new layer/source, and leaves selected pins, routes, traffic,
  location puck, fills, and UI controls untouched. All 14 JVM tests and debug assembly passed. Debug artifact:
  `applemaps-nav-lean-debug.apk` (72,748,012 bytes; SHA-256
  `9a55a368e18580aeafbb3fdeef3a2a716007b6e4ad2162aacb9b92bb30094b37`); phone appearance remains UI-unverified.

- 2026-08-08 — Implemented the consolidated iOS UI audit without changing route presentation, sheet detents, or
  the user-lowered home rest. Selected places now use sheet-aware padded camera framing, reject invalid coordinates,
  and generation-guard rich data so an older request cannot replace a newer selection; only true long-press pins
  survive dismissal. Look Around imagery is now a tappable map thumbnail above the sheet, with a standalone map
  button only when imagery is absent. Directions gained Share, a visible dotted stop connector, unclipped 40dp mode
  controls, compact Now/Avoid pills, and the corrected circular Directions glyph. Place facts retain Hours first,
  show the real next opening/closing transition, and render source rating/count semantics. Close-zoom rendering now
  disables the secondary POI dot band, limits full POIs to named supported categories, and uses Mapbox's smaller
  collision-managed address-label source. The Apple place backend now parses structured name/category/locality,
  exact rating/count, timezone-aware current hours, and the real business website. All 14 JVM tests and debug
  assembly passed. Lint retains four pre-existing errors in untouched location/manifest/MapKit-JS paths and reports
  no new error in an audit-changed file. Debug artifact: `applemaps-nav-lean-debug.apk` (72,748,012 bytes; SHA-256
  `b452bfada68b56d30f988ca61adba4bdcbecf7f249a88092f0d2cd6165274335`); phone rendering remains UI-unverified.

- 2026-08-08 — Corrected Central Park and equivalent OpenFreeMap park landcover from cream to the existing Apple
  green. Live z12–z14 tile decoding proved the main Central Park polygon is `landcover class=grass,
  subclass=park`, which the style did not render; only its separate woodland patches were green. Added one static
  polygon-only `landcover_park` fill immediately below water, preserving blue lakes/reservoirs and every existing
  source, layer, color, road, building, label, POI, and runtime path. Measured tiles add only 12–24 matching park
  polygons. All 12 unit tests and debug assembly passed; final phone-rendered extent remains UI-unverified. Debug
  artifact: `applemaps-nav-lean-debug.apk` (72,733,455 bytes; SHA-256
  `29f4ef94c1c31f2edf4e45b480a3124ae300be5a2e78db9a24706dc89a423741`).

- 2026-08-08 — Upgraded Apple Look Around to progressive high-resolution imagery without slowing the initial
  preview. The original zoom-4 2048×1024 JPEG remains the thumbnail/SD and backward-compatible URL; fullscreen
  now waits for the real GL texture limit and, on 4096-capable devices, replaces SD with a distinct zoom-3
  4096×2048 JPEG quality-92 texture. SD remains displayed if HD is unavailable, lower-limit devices skip HD,
  conversion stays single-flight, both tiers share the bounded 256 MiB cache, and no gesture/per-frame path was
  added. The live service memory ceiling is now a bounded 3 GiB; public SD/HD generation passed at 2048×1024 and
  4096×2048 with zero service restarts. All 12 unit tests and debug assembly passed; final phone-rendered sharpness
  remains UI-unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,733,455 bytes; SHA-256
  `456e87b8827d84aafe1ad633618e6d303df9e12aab41bb833bdb18f6ec1d6412`).

- 2026-08-08 — Corrected ordinary map-place name placement that the earlier selected-pin-only change missed.
  Business, medical, school, park, lodging, and transit names in `poi-dot`, plus airport names, now try left then
  right beside their icons with automatic justification and icon-sized radial clearance instead of using a fixed
  below-icon anchor. Existing icon sizes, text size/font/colors/halos/wrapping, filters, sorting, collision density,
  and zoom fades remain unchanged; no source, listener, network, camera, or frame work was added. All 11 unit tests
  and debug assembly passed; final left/right collision choices remain phone-UI unverified. Debug artifact:
  `applemaps-nav-lean-debug.apk` (72,731,043 bytes; SHA-256
  `dbeea91c63f53e7a2fbc9335b9ebdf8e36b46e3321078e4127ba15d5b6ff6489`).

- 2026-08-08 — Replaced the malformed dark dashed pedestrian-crossing paths with Google-style zebra crossings.
  Mapbox's verified roadway-spanning `path/crossing` segments now carry repeated 5×22px white transverse bars,
  map-aligned and zoom-scaled from level 18, while ignoring symbol placement so crossings neither hide nor displace
  labels. The stripe bitmap is registered once per style load (440 bytes); no source, network request, listener,
  animation clock, camera callback, or per-frame Kotlin work was added. All 11 unit tests and debug assembly passed;
  final stripe sizing/contrast remains physical-phone UI unverified. Debug artifact: `applemaps-nav-lean-debug.apk`
  (72,731,043 bytes; SHA-256 `7dc6913ba1affddbc8f3f922e7a315944ff19473faa549a448fa0c53f57123dd`).

- 2026-08-08 — Smoothed Standard-map detail removal while zooming. All 22 existing hard-boundary symbol layers
  now use native zoom-linked 0.45-level opacity ramps for their applicable labels/icons, covering place and road
  names, shields, POIs, address numbers, one-way arrows, airports, and country/city labels. Place-marker dots now
  fade before their internal empty-icon switch, and full-icon/label/small-dot POI rank bands fade across the same
  zoom thresholds as their existing filters; the established label density and 0.5 one-way-arrow opacity remain.
  Roads, buildings, routes, selected pins, layer/source counts, and app-side frame paths are unchanged. All 11
  tests and debug assembly passed; perceived fade timing remains phone-UI unverified. Debug artifact:
  `applemaps-nav-lean-debug.apk` (72,731,043 bytes; SHA-256
  `e93246be52187a00b5f2fdfcd0d47b64432d5da4cedbdad7d4adc3b598ad99c2`).

- 2026-08-08 — Moved MapLibre's circular native compass from above the top-right map controls to 8dp below
  the existing 44dp map-type/location pill. Placement uses the live status-bar inset, preserving edge-to-edge
  alignment across devices, while the pill, compass interaction, map layers, camera behavior, and render paths
  remain unchanged. The unit-test task and debug assembly passed; final physical alignment remains phone-UI
  unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,731,043 bytes; SHA-256
  `59132d61d2430fb607e78bf1f9dee91adc95491725984f46e653642a09629535`).

- 2026-08-08 — Added traffic to directions and the regular map. Drive previews now request Mapbox's
  `driving-traffic` routes, so route choice and displayed ETA include current/historic traffic; provider-classified
  slow, heavy, and severe geometry portions are styled amber, red, and dark red over the selected blue route, while
  unknown/normal flow stays blue and Google/OSRM/Valhalla/demo fallbacks remain intact. The Choose Map sheet now
  has an independent Traffic switch that adds/removes one native Mapbox Traffic v1 vector source/layer on
  Standard, Hybrid, and Satellite, preserves its state across recreation/style changes, offsets travel directions,
  leaves labels above the overlay, uses the existing 250 MiB native tile cache, and carries Mapbox attribution.
  Traffic interval compression and distance-based gradient placement are unit-tested; all 11 tests and debug
  assembly passed. Full lint has four pre-existing non-traffic errors; no traffic file has a lint error. Phone UI
  rendering remains unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,731,043 bytes; SHA-256
  `8fa2908142a2d3b1fb61f8d60204949a1bef52eba5eb17b31263869fdec88a9f`).

- 2026-08-08 — Moved the floating 44dp Look Around binoculars control completely off the place/directions sheet,
  matching the supplied iOS relationship. Its bottom edge now follows 10dp above the active sheet's outer top,
  accounting for the navigation-bar inset rather than deliberately overlapping the tray. Leading position, size,
  corners, visibility, click behavior, preview expansion, imagery, sheet detents, and performance paths are
  unchanged. Nine unit tests and debug assembly passed; final physical separation remains phone-UI unverified.
  Debug artifact: `applemaps-nav-lean-debug.apk` (72,729,671 bytes; SHA-256
  `4ec421b750f4ab931681e0c37c0f61c766997c9494f72e1036267f5303f87060`).

- 2026-08-08 — Raised MapLibre's persistent ambient map-resource cache from its 50 MiB default to a bounded
  250 MiB before the first style loads, covering recently viewed Standard vector resources and Satellite raster
  tiles across launches. Explicitly retained MapLibre's zoom-level tile cache for smoother zooming. Setup is
  process-guarded, asynchronous, preserves existing cached data, retries after a reported setup error, and sends
  success/error state through the existing bounded auto-upload diagnostics. No map source, style, navigation,
  camera, lifecycle, or per-frame path changed. Nine unit tests and debug assembly passed; process-restart cache
  reuse remains phone-runtime unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,729,683 bytes;
  SHA-256 `4091d6ff454d953b6b60442d017aea2392030d91dd803e8370e2b48cc9cdccd2`).

- 2026-08-08 — Added close-zoom Standard-map building separators and address numbers. Address labels use the
  live OpenFreeMap `housenumber` layer; tapping one selects the nearest coordinate even when the tile groups
  repeated numbers into a MultiPoint, recenters the map, drops the existing red pin, and opens an Address sheet.
  Structured reverse geocoding preserves the tapped number and recognizes road, pedestrian, residential,
  footway, and path names; network failure still leaves a usable Address sheet. No camera/frame listener or
  per-frame work was added. Nine unit tests and debug assembly passed; rendered spacing, label readability, and
  phone tap behavior remain UI-unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,729,683 bytes;
  SHA-256 `8555b6dbe20e885801f96b7c6bc645e61a9ea65bcc0e9ea7f4e4aca7b9fb34d2`).

- 2026-08-08 — Refined Standard map roads into the requested three-tier hierarchy: local street/service/track
  interiors are white; primary/secondary/tertiary roads and their links are light neutral gray; motorway/trunk
  roads and their links/ramps are darker gray with the existing darker casing retained. Mixed Mapbox layers now
  use the actual road `class`, preventing primary/secondary/tertiary links from inheriting the white of street or
  service features stored in the same layer. Existing layers, filters, widths, zoom rules, arrows, routes, labels,
  and frame paths are unchanged. Seven unit tests and debug assembly passed; rendered colors remain phone-UI
  unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,727,953 bytes;
  SHA-256 `053b79d2d5719c9d1441bdad1a93c284a495416e75ba7acbd4459fe503e6b34f`).

- 2026-08-08 — Corrected the non-draggable Apple/Panoramax GL Look Around viewer. Replaced competing drag and
  transform recognizers—where the transform recognizer could consume one-finger movement while its pan value was
  discarded—with one touch-slop-aware pan/pinch owner. One-finger drag now drives bearing/pitch, pinch retains the
  existing 30–100° FOV bounds, and one-finger release retains the explicit 500ms `ExpoOut` tween while multi-touch
  release does not add inertia. Start/end-only LOOKWEB diagnostics use the existing bounded auto-uploader, with no
  per-frame logging or network work. Seven unit tests and debug assembly passed; phone GL interaction remains UI-
  unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,727,953 bytes;
  SHA-256 `658d43bda1d65ba14a0ef26e4d4252135aac8419c3380a0340f28ad8c488ac37`).

- 2026-08-07 — Lowered the default home sheet rest from 124dp to 116dp so the collapsed screen does not expose
  a clipped fragment of `Find Nearby`; legal links follow the same 8dp shift. Place cards now put the existing
  Hours/Ratings/Accepts ribbon immediately below their action buttons and before photos/Look Around, matching the
  supplied Apple references. Traced action-button geometry remains unchanged at 52dp height with 20dp glyphs and
  11sp labels. Medium/full/place-card detents and all sheet timing are unchanged. Seven unit tests and debug
  assembly passed; final clipping/order remain phone-UI unverified. Debug artifact:
  `applemaps-nav-lean-debug.apk` (72,728,136 bytes;
  SHA-256 `24ceacdc6f0ca3a74c857d46ec793e2f84b78fd01cd2d2cbd2c1b5e8304de90f`).

- 2026-08-07 — Corrected the visible Mapbox one-way road arrows, which were oriented sideways because the
  upward-pointing sprite was used with line placement that aligns its horizontal axis to the road. Added the
  required 90-degree clockwise icon rotation while preserving road filters, arrow spacing, zoom scaling, opacity,
  collision behavior, and all map performance work. Seven unit tests and debug assembly passed; final direction
  display remains phone-UI unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,728,100 bytes;
  SHA-256 `05e6af05b1497522749e52eefd9a204262f2e45960f19e4f6c719c202a4271f6`).

- 2026-08-07 — Corrected the Standard map's road hierarchy and selected-pin label placement in the isolated
  sandbox. Ordinary road and bridge interiors are now white; motorway/trunk roads and ramps use the existing
  neutral-gray palette while retaining every existing width, casing, filter, and zoom rule. Selected location
  names now try the marker's right side and then its left with clearance derived from the existing marker/text
  geometry, rather than appearing below the pin. Palette work runs only when a style loads, so no camera,
  navigation, or per-frame work was added. Seven unit tests and debug assembly passed; final color/spacing remain
  phone-UI unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,728,100 bytes;
  SHA-256 `f94baa9ff136308fd9b1e22031cfa6bdb6f4291101410fc96406ac22422a0c8b`).

- 2026-08-07 — Added the private/debug Apple Look Around bridge in the isolated sandbox: Apple-first 3×3
  coverage lookup, dynamically signed six-face HEIC retrieval, one-shot 2048×1024 reprojection, single-flight
  conversion, and a 256MiB data-volume cache. The Android app reuses its existing GL viewer and preview motion,
  falls back to Panoramax when Apple coverage/metadata is unavailable, avoids a duplicate Apple texture load, and
  shows provider-correct attribution. The rounded preview shows a centered spinner during first-use panorama
  generation and an explicit image-error state without adding a fade. LOOKWEB diagnostics now include the selected
  provider. Apple place-card service remains isolated. Tests and debug assembly passed; phone UI remains unverified.
  Debug artifact: `applemaps-nav-lean-debug.apk` (72,728,100 bytes;
  SHA-256 `9ba193ad35efd241ebcd1d117c80b81edfce474ff705bcddba179d824c51d73f`).

- 2026-08-07 — Behavior-preserving map performance pass in the isolated sandbox. Removed the unused Google Maps
  renderer initialization from app startup; navigation follow now preserves the former 60Hz interpolation curve
  at 60/90/120Hz, updates at display rate while moving, and stops rebuilding puck GeoJSON/camera state after the
  pose converges; POI bitmaps are decoded once and safely reused across MapLibre style reloads. Map layers,
  vector sources, 3D buildings, route presentation, and all animation durations remain unchanged. Seven unit tests
  and debug assembly passed; phone frame pacing remains device-unverified. Debug artifact:
  `applemaps-nav-lean-debug.apk` (72,725,253 bytes;
  SHA-256 `9095049f8b1f55f080f3d039812e14004f3c70a21f35504f378357c609d60b55`).

- 2026-08-07 — Reworked the sandbox Look Around entry to match the supplied iOS/web sequence: the floating
  binoculars control now grows into a 314dp rounded map preview with expand and Done controls, then the preview
  expands into the existing fullscreen viewer and Done returns to the preview. Loading and no-coverage states
  remain visible instead of hiding the entry. Place-photo taps now open the fullscreen gallery with a
  geometry-only transform from the exact measured thumbnail bounds. Reverted the trial animated map-type ring
  to the original static selected ring because choosing a map type already closes the picker. Preview and photo
  events auto-upload through LOOKWEB diagnostics. Unit tests and debug assembly passed; phone UI remains
  unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,724,278 bytes;
  SHA-256 `a7104803c7ac77879a9e45e281607edd4c58759bcfb7b15ccadf9acd7ae2c5c5`).

- 2026-08-07 — Restored the missing floating Look Around entry in the isolated sandbox build. The traced
  44dp binoculars control follows the active sheet, opens with a geometry-only expansion from its own bounds,
  and remains reachable when Panoramax has no nearby imagery. Null coverage now presents an explicit empty
  state with Done and the debug provider selector; per-place viewer state prevents stale imagery from a prior
  pin. Coverage and entry events auto-upload through the existing LOOKWEB diagnostics. Route presentation and
  all deferred sheet behavior remain unchanged. Gradle tests and debug assembly passed; on-device UI remains
  unverified. Debug artifact: `applemaps-nav-lean-debug.apk` (72,711,154 bytes;
  SHA-256 `499825f7e52fb00448cba2449a15dfdeafa2cdaa5261849017f9cb1d881281bd`).

- 2026-08-07 — Approved sandbox animation trial set (compile-verified, on-device UI unverified): Look Around
  now expands from the exact measured preview-tile rectangle without fading and reverses into it; preview/flat
  images crossfade only while loading; flat-view pan release, edge return, pinch, and double-tap zoom ease with
  explicit tweens; gallery swipes track the finger and share one settle path with chevrons; Now/Avoid dialogs
  scale without fading; place/home/navigation controls use reversible ripple-free press scaling; Choose Map's
  selection ring animates; pin labels appear after balloon motion settles; navigation ETA values crossfade; and
  Now/Avoid pills ease width changes. Route presentation, sheet timing, map reload, balloon-to-dot collapse,
  place-section simultaneity, and station-live behavior are unchanged.
  Debug artifact: `applemaps-nav-lean-debug.apk` (72,700,394 bytes;
  SHA-256 `abf58c4e7b881fe768ed8636255fea43d623ad18bf15fa41f90ab343dd6d7243`).

- 2026-08-07 — Displayed-pin recentering (sandbox source, device-unverified): long-pressed locations,
  tapped POI/transit icons, and normal search-result selections now animate the map center onto the pin.
  Direct map selections preserve zoom; search retains its existing zoom-to-16 behavior. Route stops and route
  presentation remain unchanged. File: `AppleMapsScreen.kt`.

- 2026-08-07 — Navigation-boundary map controls (sandbox source, device-unverified): the white top-right
  map-mode/location pill now expands from and contracts into its anchored corner without an opacity transition.
  The bottom legal links fade independently, as requested. Updated both `GeneratedLayout.kt` and the relevant
  output in `pipeline/2_geometry_to_compose.py`; compile verified with Gradle 9.4.1.

- Turn-by-turn engine (Ferrostar core, keyless, Option A). GO now starts FerrostarCore navigation on our OWN MapLibre map: a CustomRouteProvider feeds our keyless OSRM route (createRouteFromOsrm) and we follow core.state — animating the camera (tilt 55 / zoom 17 / bearing = course) and moving the ic_nav_arrow puck (rotated to heading) at the snapped location, with VOICE guidance via AndroidTtsObserver (spoken maneuvers). Builds + launches clean. (It fires only with real GPS movement; the custom nav UI overlay — banner/lanes/speed/ETA — and the gray nav road style are the next build.)

- 3D buildings: added a fill-extrusion buildings layer to the map style (from the tiles' render_height with height fallbacks), rendered when the camera is tilted — so the immersive/turn-by-turn view has extruded buildings like Google's Immersive Navigation / Apple Maps. Captured the turn-by-turn UI spec (nav banner + lane guidance, arrow puck, side FABs, speed-limit sign, road pill, ETA bar) from the Google reference screenshots into docs/NAV_TBT_PLAN.md.

- Add Stop: the "Add Stop" row in the Directions planner now works — tap it, then long-press the map to drop a via-waypoint; the route reroutes origin → stop(s) → destination (OSRM multi-waypoint via routing.openstreetmap.de) and each stop shows as a row in the From/To card. Switching Drive/Walk/Cycle keeps the stops. Also added the turn-by-turn nav-arrow puck drawable (Google-style 3D two-facet blue chevron, points to heading) for the upcoming navigation view.

- Walking + biking directions (keyless). The Drive/Walk/Cycle toggle now routes on the correct profile via the FOSSGIS OSRM instances (routed-car / routed-bike / routed-foot — the same ones the OSM website uses), with alternatives. Fixed the Walk mode-toggle icon (a mis-exported vector that rendered as a sliver) with a clean walking-figure path.

- Ferrostar turn-by-turn — dependency integration (keyless, BSD). Added Ferrostar 0.53.0 (core + ui-compose, skipping ui-maplibre so it doesn't force its own map) + core-library desugaring. Integrating via Option A: keep our MapLibre MapView and drive it from FerrostarCore.state, overlaying the map-agnostic InstructionsView banner — so the preview→drive transition stays on one map. Builds clean; the guidance/voice/rerouting wiring is next.

- Directions sheet wired in (the traced route planner). Tapping Directions now opens the Directions sheet — Drive/Walk/Cycle mode toggle, "My Location" → destination with Add Stop, Now/Avoid pills, and a selectable route card per OSRM alternative (N min, H:MM ETA · distance, Fastest, GO). Selecting an alternate card redraws it bold on the map (others faded) instantly with no camera jump; Back exits directions. GO is stubbed for the upcoming Ferrostar turn-by-turn.

- Alternative routes + destination dot: OSRM now requests alternatives, and the map draws the SELECTED route bold blue (with the 0→1 reveal) over any ALTERNATES in faded gray (static), plus a destination dot (route-color circle with a white ring) at the route's end. The camera fits ALL routes before the reveal plays.

- Place-card polish + bug fixes: About no longer shows the literal "null" (Android's optString returns "null" for JSON null — now null-guarded across the box fields); the subtitle drops the dangling "·" when there's no locality; the ratings column and Ratings section now hide when there are 0 ratings (like Apple); the ribbon/section dividers are darker (#CED0D4) so they're actually visible on the gray card; the mis-exported Website (compass) and thumbs-up icons were replaced with clean paths; and the card body shows a loading spinner until the full data arrives instead of blanks that fill in one at a time.

- Real location + blue dot: the app requests location permission at launch and activates MapLibre's LocationComponent, so your actual position shows as the blue dot. The location button now recenters the camera on your real location (falls back to San Francisco until a fix arrives). Permissions were already declared in the manifest; this adds the runtime request + the dot + recenter.

- Route now follows roads — no Nav SDK, no key needed. The straight line was the demo fallback (the Google Routes API had no ROUTES_API_KEY). Added keyless OSRM road routing (router.project-osrm.org → a real road-following polyline), chained between Google Routes and the straight-line demo, so tapping Directions now draws the actual road path.

- Details section rebuilt to match the iOS card: Hours sits in its own white platter (small AM/PM, green "Open", chevron), followed by a Website/Phone/Address platter — Website and Phone are blue links (Website shows just the host, e.g. macys.com), and the Address row has a blue directions button wired to the same route flow as the header's Directions button.

- Photos now populate on the place card and in the gallery. Fixed the keyless box /appleplace server (its photo regex only matched otstatic/mapkit and missed Apple's mzstatic photo URLs — which also leaked into the website field): broadened it to grab mzstatic images (upsized to 1200×1200) and excluded mzstatic from the website pick. The app maps the box `photos` array into photoUrls (real image URLs) so the card thumbnails and fullscreen viewer show them.

- Place-card data populates again without a Google key: the POI tap now falls back to the keyless box /appleplace (real Apple hours / website / phone / rating / reviews) when Google Places returns nothing — previously everything was blank because Google needs a key. Ratings normalize both scales (box 0–1 fraction, Google 0–5) to the thumbs-up %.

- Place-card layout matched to the iOS card: replaced the old hours/rating platter with the HOURS | N RATINGS 👍% | ACCEPTS ribbon strip (adaptive columns, uppercase labels, vertical dividers); added a "Ratings" platter with an Overall 👍% · N ratings row; gave the "Good to Know" rows real per-amenity icons (Apple Pay / contactless / wheelchair / curbside / cash / credit / wi-fi); bumped section headers to 24sp bold.

- Fix the messed-up action-row buttons: (1) the earlier blank-icon fillColor pass had also filled a transparent bounding-rect path in 25 multi-path icons (globe, map-type control, etc.), making them render as solid squares — un-filled those rectangle paths so the real shape shows; (2) the Call icon was a mis-exported vector drawn above its own viewport (only a top sliver visible) — replaced it with a clean handset path; (3) Website now uses the Safari/compass icon like Apple instead of a globe; (4) the action row shows only the available actions — Call appears only when there's a phone, Website only when there's a website.

- Directions transition: tapping Directions now slides the place card DOWN and the map auto-zooms so the ENTIRE route is centered before the blue line draws in. The route is added hidden (reveal at 0), the camera fits the full route bounds, and only then — in the camera's onFinish — does the 0→1 line reveal play (previously the reveal and the camera fit fired at once, with the card still up).

- Fix blank icons across the UI: 257 icon vector drawables had a path with no fillColor and no strokeColor, so it drew nothing (and a Compose tint can't fill an empty path). Added a solid fillColor to every such path — the action-row, amenity, and Find Nearby category glyphs now render and tint correctly.

- Real place data + photos via Google Places: tapping a POI fetches details (rating, hours, reviews, website, phone, category) and real photo URLs from Google Places (Find Place → Details → Photo), shown in the place card and the fullscreen gallery. Uses PLACES_API_KEY (falls back to ROUTES_API_KEY so one Google key with both APIs enabled works).

- Restore the pin balloon's bounce + sway on the native SymbolLayer. The pin stays a map-locked SymbolLayer (a Compose overlay can't frame-lock to the map), but the earlier migration had replaced the real entrance with a plain 380ms overshoot. Reapplied the original diagnosed entrance verbatim: the marker holds ~230ms, then over ~1.5s grows with the subtle damped bounce (dampedSolve w0=17 ζ=0.75, ~2.9% overshoot) and sways side-to-side (±3–6°, ~1.54 Hz, decaying e^−2.4t), pivoting at the point tip via icon-rotate on the bottom-anchored icon — both driven off one clock.

- Crash hardening (from a parallel bug sweep + on-device verification): guard the Directions coroutine with runCatching so MapLibre style/bounds mutations can't crash the app when Directions is tapped; bound-check the polyline decoder against truncated input; replace `stationInfo!!` with a smart-cast local; drop dead `verticalScroll` imports (the nested-scroll footgun). Launch + home page render verified on-device with no FATAL.

- Fix a launch crash (IllegalStateException: infinite-height scroll measure): the home front page nested a `Column(Modifier.verticalScroll())` inside the bottom sheet's own scroll container. Removed the redundant scroll — the sheet handles scrolling, matching the place-card body.

- Wire the new screens into the app: the **home front page** is now the default bottom-sheet content shown before searching (under the search field); tapping a photo on a place card opens the **fullscreen photo gallery viewer** (Back/Done closes it); and tapping a transit POI (station/rail/subway/bus/tram/ferry) opens the **station departures card** instead of the regular place card. The Choose Map picker was already wired to the map-type button.

- Four more screens built from their traces: (1) **Photo gallery viewer** — fullscreen #2B2B2B backdrop, one square r10 photo at a time paged with compact chevrons, an attribution pill (r10, rgba(0,0,0,0.1)) over the photo, an "N of N" caption (11sp/600 white@0.8), and a dark 72×48 r16 "Done" pill. (2) **Home front page** — "Find Nearby" 20sp/600 header over a 2-column grid of white r16 category pills (30dp icon + 17sp label), a horizontal row of editorial bricks (154×196 r16 with a bottom gradient + publisher + 16sp/800 title), a recently-searched list, and the legal footer. (3) **Transit station card** — #F2F2F2 card with a 28sp/700 title + #0A7BFF locality, a "Departures" section whose white r16 platter lists line/direction rows with a green dot.radiowaves live badge (#34C759) and an "N min" countdown, plus a transit-provider attribution. (4) **Choose Map** picker refined to the trace — #F2F2F2 modal, rgba(0,0,0,0.14) close circle, 121dp r8 cards with a frosted-white label bar and a 2px #007CFF selected ring.

- Place card: native place detail card built from the script-traced maps.apple.com spec — a #F2F2F2 card (r12) whose detail sections (About / Hours / Ratings & Reviews / Good to Know / Also at This Location / Details) each sit in a white r16 platter at 20dp padding under a 20sp/600 header. Header is a 28sp/700 title with a subtitle that links the locality in #0A7BFF and a 30dp gray close circle; the action row runs a primary Directions button beside Call/Website/More pills (rgba(0,0,0,0.06) with #0088FA glyphs). Hours shows "Open" in green #65C366, and photos scroll edge-to-edge as 173×217 r16 cards with a bottom-gradient white title overlay. Sheet background set to the traced #F2F2F2. Built with Gradle 9.4.1 (AGP 9.2.0 minimum).

- Route preview: draw the directions route as native MapLibre line layers (#00A1FE main over #004DE9 casing, traced from Apple's route scene) that reveal by "drawing"/elongating origin→destination via a line-gradient `step(line-progress)` cut point animated 0→1 over 300ms linear (reproducing Apple's shader-trim reveal, live-traced 2026-07-14). On-device Google Routes API (`computeRoutes` → decoded polyline + routeToken; key via `ROUTES_API_KEY` BuildConfig), with a no-key demo-route fallback so the reveal is visible. Directions button on the place card triggers it; camera fits the route; Back/X clears it.

- Add Apple Maps UI extraction scripts, assets, and project scaffold

- Add extraction pipeline outputs and initial Jetpack Compose app (MainActivity, MapSurface, generated layout, theme) with MapLibre OSM map surface

- Upgrade Apple Maps bottom sheet with iOS velocity-projection fling, real snap-to detents, nav-bar floor, and 14dp corners

- Replace fixed 300ms bottom-sheet snap with distance-scaled 250–900ms ExpoOut settle derived from measured web-sheet physics

- Fix bottom sheet failing to snap to detents on release and make the system navigation bar read as opaque behind the sheet

- Expose MapLibre controller through onMapReady, tune bottom-sheet fling damping, and add the port roadmap

- Render measured Find Nearby grid, business card, and legal footer in a scrollable expanded bottom sheet

- Replace SF-symbol category glyphs with real Apple colored icons, switch basemap to OpenFreeMap Positron, and add pipeline/7_fetch_category_icons.py

- Rework AppleBottomSheet with header/body slots and nested-scroll coordination so drag expands the sheet before scrolling content, and restyle category chips (white fill, 16dp radius, 17sp labels)

- Add staggered bottom-sheet content entrance animation and a drag-trajectory-fitted sheet settle easing

- Add apple_style.json MapLibre basemap style asset

- Wire the map-type picker popover and search-triggered sheet expansion to the MapLibre controller

- Add long-press pin drop with Nominatim reverse-geocoding and a place card in the bottom sheet

- Fix dropped pin vanishing on map pan, add overshoot drop-in animation, velocity-scaled sheet fling duration, and measured place-card close button

- Split PlaceCard into pinned header + scrollable details body and add category/locality fields to Place

- Restore ExpoOut bottom-sheet easing, add red pin balloon with overshoot drop-in, and fade in the place card with an 82dp name-only collapsed peek

- Log bottom-sheet settle/trajectory diagnostics and lower detent commit threshold to 0.35

- Retune dropped-pin entrance to a 200ms tip-anchored scale/fade and remove the velocity term that snapped bottom-sheet settles to the floor

- Dropped pin now pops in with an overshoot-and-sway matching mapkit's spring appearance and shrinks out on removal

- Retune dropped pin appearance to extracted expand-and-wobble curve and rename the damped-motion helper

- Retune dropped pin to a visible decaying wobble with squash-stretch morph and correct the exit-shrink anchor to the pin's last on-screen position

- Redraw the dropped pin as a Canvas circle-on-point with white border and pushpin glyph, and increase the settle sway amplitude

- Split the dropped pin into a persistent ground dot plus a minimizable balloon (shrinks into the dot on card dismiss), retuned to a single expand bounce with a gentle decaying wobble and a ~54dp circle.

- Update apple_style.json to use OpenFreeMap sources with Apple-style basemap colors

- Repoint apple_style.json basemap to OpenFreeMap tiles, sprites, glyphs, and Apple-style layer colors

- Retune dropped-pin balloon drop/sway animation to match values captured from the real Apple Maps animation.

- Retune dropped-pin balloon expansion to the subtle ~2.9% overshoot diagnosed from the live animation

- Sequence the dropped-pin dot to pop ~150ms before the balloon rises (~340ms delay), enlarge the ground dot to 9dp, and add pop-order timeline output to capture_diagnose.py

- Dismissing a map card now collapses its balloon into a plain circle dot that persists until the pin is cleared

- Change pin balloon exit to shrink away into the marker as a reversed entrance animation

- Pin balloon now shrinks and sinks onto the marker dot on dismiss rather than disappearing

- Retune pin balloon dismiss shrink to overshoot past the marker then settle back (360ms overshoot easing)

- Add "Marked Location" label beneath the map pin and make the pin balloon grow open from the marker on entrance

- Pin balloon appears instantly at marker size, holds briefly, then grows; dismiss shrink no longer overshoots

- Reformat apple_style.json map style asset from minified to multi-line

- Replace plain POI dots with Apple MapKit category glyph icons on the map

- Expand apple_style.json poi-dot category-to-icon mapping with transit, lodging, education, culture, and civic categories and corrected glyph IDs

- Add colored POI category dots and merge icon/label rendering into a rank-filtered poi-dot layer

- Change POI dot and label opacity to stepped zoom thresholds with 250ms fade-in transitions

- Add tap-to-select on POI icons that opens the place card from map tile name/class data

- Render POI place-card balloons with the category icon and color on icon tap, keeping the red pin for long-press

- Add full Apple place-card scaffolding — rich Place fields, sectioned card body, and Directions/Call/Website/More action row

- Retune POI balloon/label zoom interpolation stops for smaller, more gradual scaling

- Wire POI place card to real Apple place data (fetchApplePlace + Coil AsyncImage photos), dropping placeholder fields

- Keep pin overlays anchored to their map location while panning and correct square POI balloon rendering

- Replace the Compose pin/balloon/label overlay with a frame-locked native SymbolLayer driven by a GeoJSON source, animated open/collapse via ValueAnimator, and fix the POI face asset path

- Replace the map-type popover with a bottom-anchored "Choose Map" modal using preview cards matching maps.apple.com

- Draw the directions route as native MapLibre line layers with an animated line-progress reveal, sourced from the Google Routes API with a demo-route fallback

- Add DirectionsSheet UI, 3D/TBT/route-preview plan docs, icon-mapping and spec capture tooling, and a more precise scaleAndRotate trace logpoint

- Add Apple-Maps directions preview sheet with traced planner/route-card layout and supporting plan docs and capture scripts

- Extend station-place-card trace JSON with captured design tokens and a POI-icon rendering note

- Add Apple Maps UI extraction capture scripts, icon-mapping pipeline, DirectionsSheet UI, and 3D/TBT/route-preview plan docs

- Enrich station-place-card component trace with margin, transition, and animation-state annotations

- Link new plan docs from APPLE_MAPS_TODO and extend the scaleAndRotate logpoint to record node width, resetting `window.__sr` per run

- Add plan-doc links to APPLE_MAPS_TODO and record node size in the scaleAndRotate CDP trace capture

- Adds Apple Maps directions capture scripts, a DirectionsSheet component, POI/icon mappings, and route-preview/3D-map/nav planning docs

- Add superseded/overview-only banner to DIRECTIONS_UI_TRACE_SPEC.md pointing to docs/trace/ dumps

- Add Apple Maps POI/directions capture scripts, POI icon assets and mapping pipeline, DirectionsSheet UI, and route/3D/nav plan docs

- Add DirectionsSheet directions-preview UI, the directions-routes-walk trace, POI icon assets, route/3D/nav plan docs, and directions capture tooling

- Add Apple Maps directions/POI/icon capture tooling, DirectionsSheet UI, directions-routes trace, and 3D/nav/route-preview plan docs

- Link 3D/nav/route-preview TODO items to their plan docs and capture pin node size in the scaleAndRotate trace logpoint

- Add Apple Maps directions/POI capture tooling, DirectionsSheet UI, route/3D/nav plan docs, and pin-size scale tracing

- Add DirectionsSheet UI, directions/POI capture tooling, route-preview/3D/nav plan docs, and pin-size capture in the scale trace

- Link 3D/nav/route-preview TODO items to plan docs and capture pin node size in the scaleAndRotate trace

- Add directions/POI capture tooling, DirectionsSheet UI, route/3D/nav plan docs, and pin-size scale tracing

- Expand APPLE_MAPS_TODO with plan-doc links and a precise POI balloon grow/shrink behavior spec

- Extend spec/trace_capture.py to log pin width and reset trace state per run

- Add POI icons, icon-mapping pipeline, capture/trace tooling, plan docs, and DirectionsSheet UI; capture pin node width in the scaleAndRotate trace

- Extend the scaleAndRotate trace logpoint to record pin node width and reset capture state before each run

- Extend scaleAndRotate trace logpoint to capture pin node width, reset prior-run rows, and retarget the drag to map center

- Extend the scaleAndRotate trace logpoint to capture pin node width, clear prior-run rows, and drag from map center

- Rebuild the place detail card into traced white platters with photos, reviews, and detail sections on a #F2F2F2 background

- Wire the home front page, photo gallery viewer, and transit station departures card into the app's place flow

- Fix launch crash by removing the home front page's nested verticalScroll that conflicted with the bottom sheet's scroll container

- Harden Directions coroutine, polyline decoder, and station-card null handling against crashes; remove dead verticalScroll imports

- Reapply the original damped-bounce-and-sway pin entrance on the native SymbolLayer, driven off one clock with bottom-anchor icon-rotate pivoting at the point tip

- Wire Google Places into the place card and gallery for real details and photos, and add a fillColor to 257 icon vectors that previously drew nothing

- Directions transition slides the card down, fits the camera to the whole route, then reveals the blue line

- Fix action-row icons (un-fill stray bounding rects, replace Call handset and Website Safari glyphs) and show Call/Website only when the place has a phone/website

- Populate place cards without a Google key via the /appleplace fallback and rework the card into the iOS ribbon/ratings layout with per-amenity icons

- Populate place-card and gallery photos from the keyless Apple box's photos array

- Rebuild place-card Details section into iOS-style Hours and Website/Phone/Address platters, with blue links and a working Address directions button

- Add keyless OSRM road routing between Google Routes and the straight-line demo so Directions follows actual roads

- Show the user's real location as a blue dot and recenter the camera on it when the location button is tapped (falls back to San Francisco until a fix arrives)

- Fix place-card "null" text, hide empty ratings, add loading spinner, and darken dividers

- Add alternative-route rendering (selected bold, alternates faded) and an end-of-route destination dot

- Route directions now start from the user's real GPS location (blue dot) when available, falling back to the map center

- Add Directions sheet with mode toggle and selectable per-alternative route cards that redraw instantly on the map

- Wire the Directions "Go" button to a seamless tilted turn-by-turn driving view with Back-to-overview handling

- Add Ferrostar 0.53.0 core + ui-compose dependencies and core-library desugaring for keyless turn-by-turn navigation

- Add keyless walking and biking directions via FOSSGIS OSRM profiles, plus a fixed Walk mode-toggle icon

- Add multi-stop ("Add Stop") via-waypoint routing to the Directions planner, persisting stops across Drive/Walk/Cycle and rendering each as a From/To row

- Add a fill-extrusion buildings layer to apple_style.json, rendered when the camera is tilted, plus the turn-by-turn UI plan in docs/NAV_TBT_PLAN.md

- Add Ferrostar TBT engine wired to GO with camera-follow, arrow-puck heading, TTS voice guidance, and a debug instruction readout

- Remove annotations=true from the OSRM raw JSON route URL

- Add turn-by-turn route steps, Valhalla avoid-toll/highway routing, and fix stuck pin animation on mid-grow dismiss

- Add route ETA label bubbles, style-reload pin/POI reinstallation, nav mute toggle, and a longer constant-duration route reveal

- Reveal alternate routes and ETA-label fade-in in sync with the main route, hide the blue dot and disable pin/POI taps during navigation, tighten the nav follow camera, and support removing waypoint stops

- Add place price levels, walk/bike elevation profiles, and mode-aware directions controls with traced ETA bubble styling

- Add a GPS speed pill to navigation, switch elevation profiles to a batched opentopodata call, and fade the gallery, add-stop hint, and nav overlay in/out

- Remove bundled apple_style.json map style from app assets

- Add a lane-guidance strip under the nav banner showing per-lane direction arrows with active lanes highlighted

- Navigation camera pauses auto-follow when the user pans the map and resumes it via the recenter button

- Add ferry and navigation road casing layers to apple_style.json

- Switch MapSurface to render Mapbox's Standard style via the Mapbox tile server and access token

- Load Mapbox Streets v12 style instead of Standard in MapSurface

- Add applyApplePalette to recolor base map polygons (land/water/buildings/parks) to the Apple palette

- Inline PropertyFactory references in applyApplePalette, dropping the local `p` alias

- Color major roads (gold highways, cream primaries) and replace Mapbox maki POI icons with the Apple poi_* icon set via a maki-field match expression

- Add a "Lanes (Google)" pill that opens a Google Maps SDK screen showing Road Level Details lane markings

- Switch base map to bundled apple_style.json, remove the Google lanes pill/overlay, and prefer real 360 panoramas in Look Around

- Animates place card enter, search overlay show/hide, directions mode toggle and route list, stop-row reorder/add, and map pin removal with tween-based transitions

- Standard map now uses white ordinary roads, gray highways/ramps, and side-positioned selected-pin labels without per-frame work.

- Google-style zebra crossings and side-positioned ordinary place labels.

- Correct phone-observed place-card and Look Around regressions.

## 2026-07-13 — sheet easing restored + pin balloon + card entrance (real asset values)
- Reverted bottom-sheet settle easing to ExpoOut cubic-bezier(0.19,1,0.22,1) — the pin-era commit had
  swapped in a fitted, non-monotonic curve (0.4,0.6,0,1) that broke the fling. Value taken verbatim
  from the real Apple shell.js/CSS assets.
- Fling detent commit threshold set to 0.5 (A/B vs 0.35).
- Dropped pin is now a red balloon (white glyph) attached above a red dot, drops in with overshoot.
- Place card fades in (real .mw-card opacity .15s ease-out) and collapses to an 82dp peek showing just
  the name/subtitle (Directions hidden until expanded).
- Added skill web-motion-extract: pull exact motion/timing/easing/token values from a site's real CSS+JS.

## 2026-07-15
- Colorize POI labels to category tint + SF Pro map fonts (bundled SDF glyphs), one-way arrows, zoom-reveal road colors, tuned transit POI weighting (major stations only). Consolidated browse work onto master.

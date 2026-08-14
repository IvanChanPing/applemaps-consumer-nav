## CURRENT STATE / NEXT STEP   (updated 2026-08-14 UTC)
- GOAL: Present source-backed restaurant menus in a separate Google-style sheet opened from a Menu action in the place card's top action row.
- DONE (verified): The live target is the clean `applemaps-consumer-nav` repository at `bee694d`; the older `applemaps-nav-lean` tree has extensive concurrent changes and is out of scope.
- DONE (verified): `Place` has no menu model, `ApplePlaceClient.parsePlace` does not parse menu components, and the configured Google Places fields stop at place details/reviews/photos/price level.
- SUPERSEDED: v0.14's in-place Overview/Menu implementation was built and tested, then replaced by the user-requested v0.15 stacked-sheet interaction below.
- TEST LIMIT: Yelp blocked the emulator's direct network route, so loaded rows, section filters, and menu scrolling are parser/build verified but physical-network UI-unverified.
- DONE (verified): The canonical APK is published at `https://204-168-163-118.sslip.io/trackers/static/applemaps-consumer-nav-restaurant-menu-debug.apk`; a fresh HTTPS download returned HTTP 200, 237,670,588 bytes, and the exact source SHA-256 `31d7411eb236dedda5d3a69c0dcf39880cfe7a3eaec5137d88e0e698ec10fdb4`.
- DONE (verified): Codex-owned scoped finalization created on-box commit `f39b1c3523e7a1ba0659bd2739efc0cb4a662bdc` (`Add source-backed restaurant menu page`) containing exactly the 11 feature, test, documentation, metadata, and canonical APK paths; no push was requested or performed.
- DONE (verified): The in-place tab was removed; Menu is now a conditional top-row action and opens an independently controlled sibling sheet above the unchanged place card.
- DONE (verified): Unit tests, Android lint, and debug assembly passed for versionCode 14 / `0.15-stacked-restaurant-menu`; the canonical root APK is 237,670,600 bytes with SHA-256 `2d9265dce227add1b8116b71ebe4a37a57f2bd27564028a023ed2f46cfcf9f6f`.
- DONE (verified): The exact v0.15 root APK was installed on Redroid; real UI input showed Directions/Call/Website/Menu/More, opened Menu as an independent sheet, and restored the unchanged place card through both X and Android Back without a crash/ANR signature.
- TEST LIMIT: Yelp blocked this emulator route, so the separate sheet's unavailable/source fallback is UI-verified; loaded item rows, category filtering, photos, and scrolling remain parser/build verified but physical-network UI-unverified.
- DONE (verified): Final assembly succeeded; the exact v0.15 APK is published at `https://204-168-163-118.sslip.io/trackers/static/applemaps-consumer-nav-stacked-menu-debug.apk`, and a fresh HTTPS download returned HTTP 200 with matching 237,670,600-byte size and SHA-256 `2d9265dce227add1b8116b71ebe4a37a57f2bd27564028a023ed2f46cfcf9f6f`.
- DONE (verified): Codex-owned scoped finalization created on-box commit `0b6b050` (`Open restaurant menus in a stacked sheet`) containing exactly the seven corrected source, documentation, metadata, and canonical APK paths; no push was requested or performed.
- NEXT STEP: User installs the published v0.15 APK and tests a restaurant menu on their normal phone network; source-reachable loaded rows remain the only explicit UI verification gap.
- KEY PATHS: `app/src/main/java/com/example/applemaps/map/Place.kt`, `app/src/main/java/com/example/applemaps/map/ApplePlaceClient.kt`, `app/src/main/java/com/example/applemaps/ui/PlaceCard.kt`.

### 2026-08-14 UTC — Target and data gap established
- VERIFIED: Git status in `applemaps-consumer-nav` is clean on branch `hk/fix-consumer-map-directions-and-navigati` at `bee694d`.
- VERIFIED: The alternate `applemaps-nav-lean` worktree has many modified and untracked paths, so it is not a safe target for this request.
- VERIFIED: The current `Place` data class contains rich place metadata but no menu section/item fields.
- VERIFIED: `ApplePlaceClient.parsePlace` handles entity, rating, hours, amenities, reviews, categorized photos, related places, About text, and airport details; it has no menu parser.
- VERIFIED: `app/build.gradle.kts` uses Google Places SDK 5.3.0, and the source field list contains no structured restaurant-menu field.
- DECISION: Do not hardcode the photographed Sisters menu. A provider contract that returns actual restaurant-specific menu data must be proven before implementation.

### 2026-08-14 UTC — Real Sisters menu contract verified
- VERIFIED: The official Google Places SDK field reference exposes restaurant attributes but no structured menu sections/items; `MENU_FOR_CHILDREN` is only a Boolean.
- VERIFIED: The real Apple Maps Sisters payload (`place-id=I83FC67094BF4BE15`) returned HTTP 200 and includes a `COMPONENT_TYPE_QUICK_LINK` titled `Menu` whose URL is the restaurant-specific Yelp menu page.
- VERIFIED: The Apple payload itself does not contain structured dishes; it delegates to the menu URL.
- VERIFIED: The linked Sisters page, fetched through a U.S. residential route after direct datacenter HTTP 403, contains a valid `schema.org/Menu` JSON-LD object with 2 sections and 10 real dishes, including descriptions, currency, prices, and item paths.
- VERIFIED: The same mobile response associates dish paths with Yelp CDN photos. The Fried Chicken Sandwich image variants `60s`, `300s`, `o`, and `l` all returned HTTP 200; `300s` is a verified 300×300 JPEG suitable for the row design.
- VERIFIED: A WARP-routed mobile request returned the complete page with HTTP 200, proving the failure is route-dependent rather than a universally inaccessible page.
- DECISION: Use the semantic JSON-LD for menu text/prices and the same page's path-keyed image URLs for optional item photos. Never synthesize dishes or prices.

### 2026-08-14 UTC — PRE-BUILD RISK PASS
- ASSUMPTION — VERIFIED: Existing selection enrichment runs off the main thread through `PlaceRepository.fetchApplePlace` on `Dispatchers.IO`.
- ASSUMPTION — VERIFIED: Both reachable place-selection entry points converge on the same `place` state and `PlaceCardBody`; a `LaunchedEffect` keyed by menu URL can cover both without duplicating fetch logic.
- ASSUMPTION — VERIFIED: The manifest already declares `INTERNET` and `ACCESS_NETWORK_STATE`; no new permission is required.
- UNKNOWN / FEASIBILITY RISK: Yelp may return 403, omit JSON-LD, alter HTML image markup, or provide object-vs-array schema variants. The client will use bounded timeouts, normalize object/array forms, tolerate missing offers/images, return no invented data, and expose failure in the menu page with an Open Original action.
- PRECONDITION: A restaurant must expose an Apple quick link titled `Menu`. Without one, the Overview remains unchanged and no empty Menu tab is shown.
- ALL ENTRY POINTS: Apple annotation selection (`AppleMapsScreen.kt` around the consumer bridge callback) and native search-result selection both assign `place`; one selection-scoped effect will fetch menus for either path and cancellation will prevent stale data from attaching to a newer place.
- CROSS-CUTTING: Network work stays on `Dispatchers.IO`; request timeouts are bounded; selection changes cancel the coroutine; stale URL checks prevent cross-place updates; Compose state resets per menu URL; API 29 compatibility is retained; no physics animation is introduced.
- OBSERVABILITY: The Menu page visibly distinguishes loading, unavailable, and loaded states. The unavailable state offers the source page rather than silently hiding a known menu link.
- VERIFICATION REACHABILITY: Unit tests will cover the exact Sisters contract plus missing/mixed shapes and image matching. Build/lint will verify integration. Redroid real taps will verify the Overview→Menu click path and scrolling if its network route can load Yelp; otherwise the real UI will verify the visible failure/open-original path and the physical-network success path will remain explicitly unverified.
- DECISION: Do not make menu retrieval part of initial place enrichment, because a blocked third-party menu must not hold the entire restaurant card spinner.

### 2026-08-14 UTC — First verification attempt stopped before compilation
- VERIFIED: The combined test/lint/assembly invocation exited 1 before resolving `:app:testDebugUnitTest` because this process had empty `ANDROID_HOME`/`ANDROID_SDK_ROOT` and the repository has no `local.properties`.
- VERIFIED: `/opt/android-sdk` is a mounted-volume symlink with Android platform 36 and build-tools 36.0.0 present.
- DECISION: Keep the repository unchanged for this environment-only condition and rerun with process-local `ANDROID_HOME=/opt/android-sdk` and `ANDROID_SDK_ROOT=/opt/android-sdk`.

### 2026-08-14 UTC — Source/build verification passed
- VERIFIED: With the process-local SDK variables, one combined `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` invocation returned `BUILD SUCCESSFUL in 4m 34s`.
- VERIFIED: App compilation, unit tests (including the exact menu/quick-link contracts), Android lint, and debug assembly completed successfully.
- VERIFIED: The Gradle APK and top-level `applemaps-consumer-nav-debug.apk` are byte-identical at 237,670,588 bytes, SHA-256 `31d7411eb236dedda5d3a69c0dcf39880cfe7a3eaec5137d88e0e698ec10fdb4`.
- VERIFIED: `aapt` reports package `com.example.applemaps.consumernav`, versionCode 13, versionName `0.14-restaurant-menus`, minSdk 29, targetSdk 36.
- VERIFIED: The compiler emitted only three pre-existing warnings in untouched code regions; no new warning points to the menu implementation.
- TEST LIMIT: No real UI click path has been exercised yet, so visual layout, menu-source loading on Redroid, section filters, and scrolling remain UI-unverified.

### 2026-08-14 UTC — Real UI click path exercised
- VERIFIED: Installed `applemaps-consumer-nav-debug.apk` returned `Success`; Android package state reports versionCode 13 and versionName `0.14-restaurant-menus`.
- VERIFIED: Real launcher/search UI input opened `com.example.applemaps.consumernav`, searched `Sisters 900 Fulton Street Brooklyn`, selected the Sisters restaurant result, and rendered the place card with Overview and Menu tabs.
- VERIFIED: Tapping Menu selected its blue underline and rendered `Menu couldn't load` plus the source fallback action. A screenshot and UI hierarchy were captured under `/root/agent-work/research/consumer-applemaps-menu/`.
- VERIFIED: Post-click-path logcat contained no matching fatal-exception, process-crash, or ANR signature for the app, and the app process remained present.
- TEST LIMIT: This Redroid network route cannot load Yelp, matching the previously observed direct-route block. Loaded menu rows, filter pills, dish-photo rendering, and vertical scrolling were therefore not exercised through a physical-network UI; the semantic parsing path is covered by the passing exact-contract unit tests.

### 2026-08-14 UTC — HTTPS deliverable verified
- VERIFIED: The canonical APK was copied to the existing Caddy static directory as `applemaps-consumer-nav-restaurant-menu-debug.apk`; source and served copy are both 237,670,588 bytes with SHA-256 `31d7411eb236dedda5d3a69c0dcf39880cfe7a3eaec5137d88e0e698ec10fdb4`.
- VERIFIED: Downloading `https://204-168-163-118.sslip.io/trackers/static/applemaps-consumer-nav-restaurant-menu-debug.apk` returned HTTP 200 and 237,670,588 bytes; the downloaded SHA-256 matches the canonical root APK exactly.

### 2026-08-14 UTC — Scoped finalization verified
- VERIFIED: `/root/.codex/tools/hk/hk-finalize.sh` completed successfully and its log records commit `f39b1c3` on branch `hk/fix-consumer-map-directions-and-navigati` for the 11 explicitly scoped paths.
- VERIFIED: `git show` reports the source client, models, Apple link parser, UI, lifecycle wiring, two test files, changelog/version metadata, journal, and canonical root APK in that commit. The APK is Git-tracked despite the repository's local exclude rule.
- VERIFIED: No GitHub push was requested or performed.

### 2026-08-14 UTC — User-corrected interaction model and PRE-BUILD RISK PASS
- VERIFIED: The current implementation transforms `PlaceCardBody` between Overview and Menu using an internal tab bar; this conflicts with the requested interaction shown in the new reference image.
- VERIFIED: `AppleMapsScreen` already renders the place card and Directions as independent sibling `AppleBottomSheet` instances with separate controllers, stacked draw order, independent dismissal, and a preserved underlying sheet. The same contract can host Menu.
- VERIFIED: The place-card action row conditionally composes Directions, Call, Website, and More; `ic_menucard.xml` and `ic_menucard_fill.xml` already exist, so no new artwork is required.
- VERIFIED: Yelp blocked the tested emulator/datacenter route. On any route Yelp blocks, direct source parsing cannot populate the native menu; the current explicit unavailable state/source-page action is the only verified behavior there.
- ASSUMPTION — VERIFIED: Menu availability is already represented by `place.menuUrl`, and every place selection converges on the same `place` state.
- UNKNOWN / FEASIBILITY RISK: A normal user's phone route may or may not be accepted by Yelp. This UI correction does not solve that transport dependency and must not be described as doing so.
- PRECONDITION: The selected restaurant must expose Apple's explicit HTTPS Menu quick link; otherwise the top-row Menu action remains absent.
- ALL ENTRY POINTS: `PlaceCardHeader` is called once for restaurant cards; `PlaceCardBody` has one live call site; `AppleMapsScreen` owns place, Directions, and Back state. All three must change together.
- CROSS-CUTTING: Menu fetch remains off the main thread; it should start only when the Menu sheet opens, reset on place change, reject stale URL completion, and preserve API 29 support. Back/X must dismiss Menu before Directions/place, and no physics animation may be introduced.
- OBSERVABILITY: The separate sheet retains visible loading, unavailable, loaded, and Open menu states.
- VERIFICATION REACHABILITY: Unit/lint/build cover wiring and parsing. Real Redroid taps can verify action-row placement, independent over-sheet stacking, Back/X dismissal, and the unavailable state. Populated rows remain physical-network UI-unverified unless the route reaches Yelp.
- DECISION: Implement only the requested interaction correction now; do not add an unrequested credentialed scraping backend or claim that the Yelp route block is solved.

### 2026-08-14 UTC — Stacked-sheet implementation built
- VERIFIED: `PlaceCardHeader` now conditionally inserts `ic_menucard_fill` / Menu before More; `PlaceCardBody` no longer contains Overview/Menu page state or tabs.
- VERIFIED: `AppleMapsScreen` owns a separate `menuSheet` controller and `menuOpen` state, renders Menu as a sibling `AppleBottomSheet`, begins source loading only while that sheet is open, resets it on place change, and gives Menu its own close/back path.
- VERIFIED: The combined `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` invocation returned `BUILD SUCCESSFUL in 2m 36s`; compilation, unit tests, lint, and assembly passed.
- VERIFIED: The canonical root APK is byte-identical to Gradle output at 237,670,600 bytes, SHA-256 `2d9265dce227add1b8116b71ebe4a37a57f2bd27564028a023ed2f46cfcf9f6f`; `aapt` reports versionCode 14, versionName `0.15-stacked-restaurant-menu`, minSdk 29, targetSdk 36.
- VERIFIED: The compiler emitted the same three warnings in pre-existing untouched expressions; no warning points to the new Menu action or sheet.

### 2026-08-14 UTC — Stacked Menu real-UI verification
- VERIFIED: Installed root APK reported versionCode 14 / versionName `0.15-stacked-restaurant-menu`, and launcher taps opened the intended Maps activity.
- VERIFIED: Searching for `Sisters 900 Fulton Street Brooklyn` and opening the result rendered five evenly weighted actions in this order: Directions, Call, Website, Menu, More. No in-body Overview/Menu tabs remained.
- VERIFIED: Tapping Menu rendered a second `AppleBottomSheet` with its own Sisters/Menu header, close control, and blocked-source fallback. The UI hierarchy retained the underlying Sisters place-card nodes while the second sheet was open.
- VERIFIED: Tapping the menu sheet's X restored the original card with all five actions. Reopening Menu and pressing Android Back also removed only the Menu sheet and restored the same action row.
- VERIFIED: The app process remained present and scoped post-test logcat contained no fatal-exception, app-process-crash, or ANR signature.
- TEST LIMIT: Yelp still blocks this emulator network route. This proves the separate-sheet interaction and fallback, not loaded native menu rows on a source-reachable phone route.
- SELF-REVIEW: All changed callers/usages were enumerated; network I/O remains on `Dispatchers.IO`; place changes and card dismissal close Menu; stale URL completion is guarded; absent menu URLs omit the action; no permission/API-level change was introduced. One stale Menu-tab KDoc was corrected.

### 2026-08-14 UTC — Final v0.15 artifact published
- VERIFIED: Final `:app:assembleDebug` after documentation self-review returned `BUILD SUCCESSFUL in 31s`; root and Gradle APKs are byte-identical at 237,670,600 bytes with SHA-256 `2d9265dce227add1b8116b71ebe4a37a57f2bd27564028a023ed2f46cfcf9f6f`.
- VERIFIED: Caddy serves the artifact at `https://204-168-163-118.sslip.io/trackers/static/applemaps-consumer-nav-stacked-menu-debug.apk`; a fresh download returned HTTP 200 and matched the root APK's exact byte count and SHA-256.

### 2026-08-14 UTC — v0.15 scoped finalization verified
- VERIFIED: Codex-owned finalization completed as commit `0b6b050` (`Open restaurant menus in a stacked sheet`) on `hk/fix-consumer-map-directions-and-navigati`, scoped to the seven intended v0.15 paths.
- VERIFIED: No GitHub push was requested or performed.

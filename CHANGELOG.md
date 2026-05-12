# Changelog
All notable changes to Caelum (formerly the Allsky Companion App) will be
documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.5.0] - 2026-05-12
### Added
- **Live sky overlay (v1).** The home-screen live image now paints the Moon
  and every above-horizon naked-eye planet on top of the actual fisheye
  frame, in their correct alt/az. Off by default until you calibrate.
- **One-tap calibration.** Settings → Sky Overlay → Calibrate freezes the
  current live frame and asks you to tap a single bright body — the Sun
  by day, the Moon at night, or the brightest naked-eye planet as a
  fallback. The app already knows where that body *should* be from your
  location and the time, so a single tap is enough to solve the camera
  rotation. Calibration survives camera-resolution changes (coordinates
  are stored as image fractions, not pixels).
- **Equidistant fisheye projection module.** New `FisheyeProjection`
  handles forward (`altAz → pixel`), inverse (`pixel → altAz`) and both
  a 1-tap rotation-only solver and a 4-parameter Levenberg–Marquardt
  fit for the (not-yet-exposed) 3-tap precise calibration coming next.

### Coming next
- 3-tap precise calibration (Sun + due-north horizon + due-east horizon),
  for installs where the lens isn't perfectly centred. Solver is already
  in `FisheyeProjection.preciseCalibrate`; only the UI is pending.
- Satellite-pass arcs and meteor-shower radiants on the live overlay.

## [3.4.0] - 2026-05-11
### Added
- **Tonight card: zenith-centred sky map.** A small always-visible diagram
  now sits at the top of the Tonight card showing where everything is
  right now. Centre = zenith, outer ring = horizon, N/E/S/W labelled. The
  Moon, every above-horizon naked-eye planet, the next visible satellite
  pass (as a quadratic-Bézier arc through start / max-altitude / end),
  and the active meteor shower's radiant all plot in their correct alt/az.
  Three concentric guide rings mark horizon, 30° altitude, and 60°
  altitude. Equidistant projection — radial distance is proportional to
  zenith angle, matching the planar fisheye projection we're planning to
  target for a future overlay on the live allsky frame.
- **Meteor shower radiant coordinates.** Each entry in the offline shower
  table now carries its J2000 radiant RA/Dec, projected to observer-local
  alt/az on demand by the ViewModel. Powers the new sky-map marker and
  sets up the future "live overlay" work without further data plumbing.

### Changed
- **Tonight card: row header layout.** The all-caps row label (METEORS,
  MOON, PLANETS, AURORA, PASSES) used to sit beside the title in a fixed
  64dp slot, where long titles like "Southern δ Aquariids" or
  "2 visible: Jupiter, Saturn" would butt right up against it. The label
  now sits *above* the title in its own line — title text gets the full
  row width, labels stop colliding, and rows look unified across content
  lengths.
- **Moon phase: single source of truth.** The home-screen moon card and
  the Tonight card moon row used to call into two different calculators
  — one based on a "seconds since the 2000 new moon, mod 29.53 days"
  cycle, one based on Meeus illumination percentage. They disagreed near
  the phase boundaries (one could say "Full Moon" while the other said
  "Gibbous"). Both now route through a new `MoonAlmanac.phaseAt(now)`
  that derives the named phase from Meeus illumination + a 1-hour-later
  sample to decide waxing vs waning. The legacy `MoonPhaseCalculator` is
  preserved as a thin facade so all callers continue to work unchanged.

## [3.3.0] - 2026-05-11
### Added
- **Tonight card: four new location-aware rows.** The card now reads the
  user's saved latitude/longitude (no new permission prompt — same coords
  the weather module already uses) and surfaces:
  - **MOON** — rise / transit / set today at the user's site, illumination
    %, and a friendly phase label. Pure offline compute (Meeus chapter 47,
    very-low-precision form, ~0.3° position accuracy).
  - **PLANETS** — naked-eye planets currently above the horizon at the
    user's location, sorted brightest first. Each shows altitude/azimuth
    cardinal, V-band magnitude, set time, and zodiac constellation.
    Driven by JPL "Approximate Positions of the Planets" Keplerian
    elements (valid 1800–2050) plus the standard phase-corrected
    magnitude formulas from the _Astronomical Almanac_.
  - **AURORA** — NOAA SWPC 3-day planetary-Kp forecast, peak Kp over the
    next 24 hours. Row is gated by **geomagnetic latitude**: below
    |45°| geomag (most of mid-US, central Europe) the row hides entirely
    rather than dangle a permanent "no aurora at your latitude" string.
    1-hour in-memory cache.
  - **PASSES** — bright satellite passes for the next 24 hours from the
    CelesTrak `visual` group (~150 curated naked-eye targets), propagated
    via SGP4 (predict4java) against the user's lat/lon and filtered to
    max-elevation ≥ 20°, with the observer in nautical-to-civil twilight
    at TCA so the satellite is sunlit but the sky is dark. 24-hour TLE
    cache.
- **Per-row expansion.** Each row is independently tappable to expand for
  more detail (full per-planet rundown, descriptive aurora text, all
  passes rather than just the next, etc.). The card-level expand chevron
  from 3.2.0 is gone — rows own their own state now.

### Changed
- **Tonight card: from one row to five.** The single shower row + "More
  coming in future releases" footer is replaced with a stacked layout
  where each data source is its own row. Rows hide themselves when there
  is nothing useful to surface (no active shower, no aurora at this
  latitude, no bright passes in the next 24h), so the card adapts to the
  actual sky rather than reserving holes.
- **Internal: new `data/astro/` package.** Self-contained astronomy
  primitives (Julian date, sidereal time, eq→horizontal, Bennett
  refraction, twilight gating, sun position) shared by every row. Pure
  Kotlin, no Android deps, unit-testable. Three almanacs (Moon, Planets,
  Meteor showers — the last still lives in `ui/modules/TonightModule.kt`
  for now) build on top.

### Dependencies
- Added `uk.me.g4dpz:predict4java:1.1.3` (~80 KB, MIT). Faithful Java
  port of Vallado's reference SGP4 implementation. Used solely by the
  satellite-passes row.

### Notes
- Why we use saved lat/lon and not live GPS: the user already set
  coordinates during onboarding (geocoded from their station name) and
  most home observers don't move. Skipping a runtime location prompt
  keeps the permission surface tight. Travellers can update Settings →
  Location to reflect their current site.
- Why low-precision Meeus rather than VSOP87 or DE440: a phone screen
  cannot tell the difference between a planet at 32.0° altitude and one
  at 32.04°. The truncated formulas are ~0.5° accurate in longitude,
  well below display granularity.
- predict4java is unmaintained since ~2014. Accepted because the SGP4
  model itself doesn't change, the API is stable Java, and there are no
  transitive dependencies. If it ever falls off Maven Central we have
  the option of vendoring it.

## [3.2.0] - 2026-05-11
### Added
- **Tonight card: tap to learn more.** Tap the Tonight card and it
  expands inline to show a curated 2-3 sentence description of the
  active shower — parent body (asteroid 3200 Phaethon for the Geminids,
  Halley's Comet for both Eta Aquariids and Orionids, the 33-year storm
  cycle of comet 55P/Tempel–Tuttle behind the Leonids, and so on),
  radiant constellation, and what makes the shower notable. A "Learn
  more on Wikipedia ↗" button hands the canonical en.wikipedia.org URL
  to the user's default browser via Intent.ACTION_VIEW — no in-app
  webview, no extra dependency. A subtle chevron in the card header
  rotates 180° when expanded to telegraph the tap target. On quiet
  nights (no active shower) the card stays non-tappable to avoid
  dangling a useless target.

### Notes
- Why curated text rather than an AI call: the headline annual showers
  are a fixed set of ten, so a hand-written blurb per row is cheaper
  than any API integration and immune to hallucinated radiants or peak
  rates. Same shape will accommodate planet / moon / aurora /
  satellite-pass rows when those data sources land.

## [3.1.0] - 2026-05-11
### Added
- **Focus module on the home screen.** Once the focus motor feature is
  enabled AND the rig is reachable on screen open, a compact jog card
  appears in the layout editor's AVAILABLE section. Add it to your home
  screen and you get four step-size presets (64 / 256 / 1024 / 4096), the
  BACK / FORWARD jog buttons, and a one-line move-result pill — without
  navigating to the standalone Focus screen. OPEN in the top-right jumps
  there for credential edits and the wider preset list. Conditional
  visibility: when the rig is unreachable, the card collapses to zero
  height rather than reserving a hole.
- **Tonight card.** New home-screen module surfacing the strongest active
  annual meteor shower with days-from-peak context (ZHR pill scales its
  green tint with shower strength — Geminids at 150 reads brighter than
  Ursids at 10). Backed by a built-in IMO 2024 working-list table, so
  zero network calls. Designed as a durable replacement for the
  originally-planned ISS-pass alert idea — a generic "what's worth
  looking at tonight" board that gracefully outlives any single space
  station's lifetime. Visible planets, moon rise/set, and bright
  satellite passes are queued for future slices in the same card.

### Changed
- **Layout editor catalogue.** TONIGHT joins the BASE module list; FOCUS
  is appended dynamically only when the focus feature is enabled, so
  users who never opt in never see a dead row.

## [3.0.1] - 2026-05-11
### Fixed
- **Layout editor: saved removals stick.** Unchecking a module and hitting
  SAVE used to round-trip the layout through `resolveLayout()`, which
  re-appended every DEFAULT_LAYOUT module missing from the saved list and
  silently undid your change. The auto-append block was duplicative of the
  layout-version-bump mechanism and is gone; the saved list is now
  honoured verbatim.

### Changed
- **Layout editor: real drag-to-reorder.** Long-press the grip on the left
  of any row to drag it into a new position; haptic tick fires on grab and
  on each swap. The editor now splits into two sections — "ON HOME SCREEN"
  (draggable, reorderable) and "AVAILABLE" (tap + to add) — so the drag
  affordance never lies about what's interactive. Up/down arrow buttons
  are removed; drag is the primary mechanism. Built on
  `sh.calvin.reorderable:2.4.3` (~70 KB MIT lib, zero transitive deps).

## [3.0.0] - 2026-05-11
### Changed
- **Renamed to Caelum**: Latin for "the heavens" — and a southern
  constellation. The app keeps the same internal `de.astronarren.allsky`
  applicationId so existing installs update in place without losing
  settings; only the display name, launcher icon, and About copy change.
  References to "Allsky" remain wherever they describe the upstream
  camera/server product (the URL field, the camera-credit copy, the
  upstream-project link).
- **New Launcher Icon**: Four-star Caelum-constellation glyph drawn as a
  vector adaptive icon on the existing deep-navy background. Renders
  crisply at every density and survives all OEM mask shapes (circle,
  squircle, teardrop). The notification status-bar drawable (crescent
  moon + sparkle, shipped in 2.3.0) is unchanged.

### Fixed
- **compileSdk 35 Build Errors**: `MainActivity.onNewIntent` now overrides
  the non-null `Intent` parameter (Android's SDK 35 platform stubs tightened
  the nullability), and `MainScreen.kt` adds the missing
  `androidx.compose.ui.layout.positionInParent` import. Both errors were
  blocking `assembleDebug` and `assembleRelease` in CI; this is the actual
  build of 2.3.0 reaching users.

## [2.3.0] - 2026-05-11
### Added
- **Sky-Condition Push Alerts**: New "Sky alerts" toggle in the drawer (off by
  default). When on, you get a single push the moment tonight's viewing
  rating jumps from POOR/FAIR up to GOOD/EXCELLENT — no spam on slightly
  improved nights, no quiet hours to configure. Tapping the notification
  opens the app and animates straight to the Best Viewing card with a brief
  highlight. Long-press the toggle to fire a sample notification for testing.
  First-time enable also kicks off a one-off forecast refresh so you're not
  waiting up to three hours for the next periodic worker.
- **Sunset-Anchored Night Rating**: New 4-level rating (POOR / FAIR / GOOD /
  EXCELLENT) computed over the 10 hours after local sunset using OWM's own
  sunset timestamp, so high-latitude users with 23:00 sunsets aren't
  penalised by a fixed 21:00–05:00 window. Rain, snow and thunder hard-cap
  the rating to POOR.
- **Notification-Specific App Icon**: New status-bar drawable (crescent moon
  + sparkle) replaces the generic compass that Android was tinting white.

### Changed
- **Focus Screen Polish**: The "First time here?" help card now appears
  immediately under the Enable toggle so new users see the v3AF guide link
  before scrolling. SAVE became SAVE & TEST: on green, the credential
  fields fold away into a one-line "CONNECTED — pi@allsky-pi" chip with a
  small EDIT button; on red, the editor stays open with the failure reason.
  Re-entering the screen auto-probes silently so the chip always reflects
  the live state, not a stale form. Jog buttons are now 72 dp arrow-only
  with the caption beneath, so the label can never wrap onto a second line.
  Move-result feedback gained matching green/red status pills with check /
  error icons.
- **Notification Channel Description**: Now reads "Heads-up when tonight's
  viewing conditions improve" rather than the old "Alerts for clear sky
  conditions" — accurate to the new improvement-only trigger.

### Fixed
- **Notification Deep-Link Lost on Warm Start**: The old "Clear Skies
  Tonight" notification used `FLAG_ACTIVITY_NEW_TASK | CLEAR_TASK`, which
  tore down the running activity and forced a cold start — losing scroll
  position and any open dialogs. The new improvement notification uses
  `SINGLE_TOP | CLEAR_TOP` against a `launchMode="singleTop"` activity, so
  taps land on the existing instance and reach `onNewIntent` cleanly.
- **Notification Spam on Every Worker Run**: The previous worker fired a
  "Clear Skies Tonight!" notification on **every** 3-hour run whenever the
  next forecast point happened to be below 20% clouds, regardless of
  whether anything had actually changed. Removed; the improvement-only
  detector is now the single notification path.

## [2.2.0] - 2026-05-11
### Added
- **Optional Focus-Motor Feature**: New "Focus Motor" screen for users who
  follow the [v3AF focus-capable Allsky guide](https://www.printables.com/article/allsky-v3af-focus-capable-allsky-VNLB02d).
  Choose SSH or HTTP transport, set host / credentials / step size (64–1024 +
  custom), and jog the focuser forward or backward without leaving the app.
  Help card explains how to use Tailscale tailnet hostnames for remote access
  with zero in-app SDK integration.
- **City-Search Onboarding**: Setup step 4 replaces blind lat/long entry with
  a free-form place search backed by the Open-Meteo geocoder (no API key, no
  Maps SDK bloat). GPS button + manual lat/long are still available as
  fallbacks; the selected place is shown as a confirmation chip.
- **Status Overlay in Viewer**: Full-screen image and video viewers now show
  a labelled status pill — LOADING / READY / DOWNLOADING / SAVED / ERROR —
  so the user has feedback while a large timelapse buffers or a download
  completes.
- **Stable Release Signing**: Release APKs are now signed with a stable
  developer-owned keystore via GitHub Secrets. Future updates install in
  place — no more uninstall/reinstall every release.
- **Coil Disk + Memory Caches**: Thumbnails are cached on disk (100 MB) and
  in memory (25% of heap), making repeat scrolls and second launches feel
  noticeably snappier.

### Changed
- **Home Layout**: Moon phase moved to the bottom and rebuilt as a compact
  horizontal card (96 dp disc + stats on the right) — Weather and Best
  Viewing Night are now the first things you see after the live tile.
  Existing layouts are reset to the new canonical order on first launch (via
  a layout-version migration).
- **Full-Screen Viewer Chrome**: Top bar (station name / URL pill) and burger
  menu are now hidden whenever a viewer is open, so the X is no longer cut
  off by the menu and the location nickname doesn't bleed behind the
  filename. Close / Download replaced with consistent floating round buttons
  on both image and video viewers.

### Fixed
- **5-Day Forecast Collapsed to 1 Day**: `WeatherDisplay` was slicing the
  already-daily-deduped forecast list by `index % 8 == 0`, which kept only
  index 0 — so the strip rendered a single column instead of five days.
  Removed the stale slicing.
- **Weather Errors Rendered Blank**: When the API call failed (bad key,
  missing coords, network drop) the weather card rendered an empty Box with
  no feedback. There's now a dedicated error card with human-readable text
  for each known failure mode.
- **Timelapses / Meteors / Star Trails Missing**: The portal-listing parser
  rejected any media URL containing a query string (cache busters, signed
  links) and required strict filename tokens for keograms/startrails. The
  parser is now permissive — accepts query strings on real media URLs,
  unions the portal listing with the direct directory listing per category,
  and falls back to directory-path detection when the filename token is
  missing. Per-section error states surface the cause instead of a silent
  empty list.

## [2.1.0] - 2026-05-10
### Added
- **Unified Material 3 Design System**: New `Color.kt` with proper M3 surface
  tiers (`surfaceContainer*` scale), a dedicated `Shape.kt`, and refined
  typography in `Type.kt`. Every screen now pulls from these tokens instead
  of hand-tuning alpha + corner radius at the call site.
- **Shared Surfaces**: `AppBackground` (the navy nebula gradient) and
  `GlassCard` (translucent card with consistent border) — used across
  Settings, Media, Layout Editor, About, and the drawer for a single visual
  language.
- **Splash Theme Fix**: `themes.xml` now extends Material 3 with a navy
  window background, so cold start no longer flashes white before Compose
  draws the first frame.

### Changed
- **Settings Screen**: Re-grouped into Station / Credentials / Location /
  Appearance sections with section headers and icon glyphs. Inputs use a
  custom glass-styled `OutlinedTextField` palette.
- **Media Screen**: Tighter grid, lower-profile filter chips, modern empty
  and error states inside a `GlassCard`. Plays in the same dark aesthetic as
  the home screen.
- **Layout Editor**: Each module is now a card with an at-a-glance enabled
  state, drag handle glyph, and quieter reorder buttons.
- **About Screen**: Rebuilt as a single dark-themed card stack with brand
  display type, author links inside rounded pills, and a feature glyph list.
- **Drawer (`SettingsPanel`)**: Glass list items grouped by section
  (Navigation / Media / System); ditched the legacy `NavigationDrawerItem`
  for a more compact custom row.
- **MainScreen**: Live image card uses 32 dp radius and 8 dp elevation;
  Best Viewing and Weather empty-state cards use the new `GlassCard`.
- **Material You Off By Default**: `dynamicColor = false` so the curated
  cosmic identity isn't replaced by the user's wallpaper colours.

## [2.0.1] - 2026-05-10
### Fixed
- **Videos Behind Basic Auth**: ExoPlayer now sends `Authorization: Basic …` on every request. Previously the `user:pass@host` form was stripped before the connection was made, so protected installs returned HTTP 401 and timelapses, meteor recordings, and HLS streams refused to play.
- **Thumbnails Behind Basic Auth**: Coil now shares a single OkHttp client with a dedicated auth interceptor that promotes URL userinfo (or saved credentials when the host matches the configured Allsky URL) to a Basic Auth header. Keograms, startrails, and the live image now load correctly on password-protected portals.
- **Empty Live Tile**: The home screen no longer opens a broken viewer when tapped before the first frame loads; a loading spinner is shown instead and the tile is non-clickable until the stream resolves.
- **Live Image Flicker**: The 30-second refresh no longer triggers a full fade-in/out crossfade on the live card. The crossfade now only runs when the underlying stream URL itself changes.
- **Wasted HEAD Probes**: The live-image endpoint is now cached after the first successful probe, eliminating redundant HEAD requests on every 30-second refresh.
- **Bottom Gesture Inset**: Main screen content now respects the system navigation/gesture inset so the bottom of the scroll isn't hidden.
- **Image Gallery Noise**: Day-list navigation links (`page=list_images`, trailing-slash directory entries) no longer leak into the raw-image carousel; they're isolated for the nested-day fetch only.
- **Layout Editor Order**: Re-enabling a hidden module now inserts it at its canonical position rather than appending to the end of the layout.
- **Downloads Behind Basic Auth**: `DownloadManager` requests now strip any userinfo from the URL and add a proper `Authorization` header.

## [2.0.0] - 2026-04-14
### Added
- **Station Name Personalization**: You can now set a custom name for your Allsky station in the settings, which will be displayed in the top bar.
- **Improved Weather Display**: Overhauled the weather section with condition icons from OpenWeatherMap, improved text alignment, and a more intuitive 5-day forecast layout.

### Fixed
- **Robust Media Loading**: Significant improvements to the media parsing engine. Fixed issues where star trails, time lapses, and other media were not showing due to URL normalization errors on non-root Allsky installations.
- **Enhanced Parsing**: Relaxed parsing rules to ensure media is detected across a wider variety of Allsky portal configurations.

## [1.7.9] - 2026-04-14
### Fixed
- **Image Viewer Layout**: Added a semi-transparent title bar to the full-screen image viewer to prevent the close and download buttons from overlapping with the image title/filename.
- **Layout Editor Persistence**: Fixed a bug in the Layout Editor where removing an item wouldn't persist correctly. The editor now correctly manages the visibility state of all modules.

## [1.7.8] - 2026-04-14
### Added
- **New Widget Worker**: Completely overhauled the Allsky Widget with `WorkManager` and `Coil` for robust background updates.
- **Image Downsampling**: Implemented automatic image downsampling for the widget to prevent "TransactionTooLargeException" and ensure the widget always shows an image.
- **Visual Placeholders**: Added dedicated background and placeholder graphics for a more polished widget look.

### Changed
- **UI/UX Refinement**: Modernized typography with high-contrast weights (Black/ExtraBold) for better nighttime and high-contrast environments.
- **Visual Hierarchy**: Improved spacing and card contrast (`0.05f` alpha cards with border strokes) for the Weather and Media sections.
- **Dynamic Backgrounds**: Updated the dynamic background logic for better contrast between text and various weather-based gradients.

### Fixed
- **Widget Transparency**: Resolved the issue where the widget would appear transparent instead of showing the live allsky image.
- **ViewModel Robustness**: Refined coroutine usage in `WeatherViewModel` and `LiveImageViewModel` to prevent redundant launches and handle network drops gracefully.
- **Stream Discovery**: Improved the adaptive path discovery for live allsky camera streams.

## [1.7.7] - 2026-04-14
### Fixed
- **Media URL Normalization**: Overhauled the URL resolution logic to correctly handle absolute and relative paths from the Allsky server, resolving the "Invalid URL" warnings and ensuring content like Startrails and Timelapses appears correctly on the Home Screen.
- **Improved Performance**: Switched to asynchronous parallel fetching for all media categories, significantly reducing the initial load time on the Home Screen.

## [1.7.6] - 2026-04-14
### Fixed
- **Compilation Error**: Removed lingering import statement for the removed database module that was preventing successful compilation of the previous release.

## [1.7.5] - 2026-04-14
### Changed
- **Removed Smart Caching**: Temporarily reverted the Room Database caching system to ensure media screens reliably load without displaying blank thumbnails on the initial launch.
- **Removed System Monitoring**: Temporarily disabled the System Monitoring module as the Raspberry Pi system page HTML structures vary too wildly across different Allsky versions to be reliably parsed at this time.

## [1.7.4] - 2026-04-14
### Fixed
- **Compilation Error**: Fixed an errant syntax error in the repository layer that caused the previous deployment build to fail.

## [1.7.3] - 2026-04-13
### Fixed
- **Smart Caching UX**: Resolved the issue where the app would briefly display "No content available" before content popped in. The app now correctly displays a loading spinner if the local database is empty while seamlessly fetching fresh network data in the background.
- **Fallback Thumbnails**: Fixed a bug where video files lacking a native thumbnail would display as a blank square instead of the designated fallback thumbnail in the Media screens.
- **System Monitoring**: Completed the implementation of the System Status module. The app now correctly parses and displays your Allsky Raspberry Pi's CPU Load, CPU Temperature, Disk Usage, Memory Used, and Uptime directly on the Main Screen.

## [1.7.2] - 2026-04-13
### Fixed
- **Build Stabilization**: Fixed a critical compiler error in the ViewModel layer related to the newly introduced System Info data mapping.

## [1.7.1] - 2026-04-13
### Fixed
- **Build Stabilization**: Addressed minor compiler errors related to the Room database integration to ensure successful release packaging.

## [1.7.0] - 2026-04-12
### Added
- **Smart Caching (Room Database)**: The app now uses a local SQLite database (Room) to cache your timelapses, startrails, and other media. This makes the Media Screens load instantly while it checks for updates in the background.
- **Dynamic Theming (Material You / Palette)**: The app's background and UI elements now intelligently extract colors from the live sky image and adapt their theme to match your current sky conditions in real-time.
- **System Monitoring Hub**: Added a new "System Monitoring" module to the Main Screen that scrapes your Allsky Raspberry Pi's health metrics (CPU Temp, Disk Usage, etc.) directly from the web interface.
- **Interactive Forecast Widget**: Expanded the Android home screen widget to display a sleek 3-day weather forecast directly beneath the live sky image.
- **Advanced Video Streaming**: Integrated Media3 ExoPlayer with HLS (`.m3u8`) support and optimized buffering parameters to ensure smooth, adaptive streaming of your high-resolution timelapses.

## [1.6.0] - 2026-04-12
### Added
- **Live View Home Screen Widget**: Re-engineered the Android home screen widget to accurately resolve the live image stream path and handle Basic Authentication credentials securely. Tapping the widget image now launches the app directly for a seamless experience.
- **Fully Integrated Weather API**: The OpenWeather API key is now completely integrated into the app. The manual API key input fields have been permanently removed from both the Setup and Settings screens, simplifying the onboarding process for new users.

## [1.5.5] - 2026-04-12
### Fixed
- **Fatal Rendering Crash on Startup**: Resolved a critical `ResourceResolutionException` caused by unsupported placeholder image formats (WebP/HTML masquerading as WebP). All custom thumbnails (timelapses, raw images, moon phase) have been rigorously verified and converted to clean `.jpg` formats to ensure guaranteed decoding via Jetpack Compose's `painterResource`, preventing the app from crashing after setup.

## [1.5.4] - 2026-04-12
### Fixed
- **Critical Startup Crash on Android 13+**: Resolved a hard crash that occurred immediately after entering logon details and latitude/longitude. This was caused by the app's background weather worker attempting to display a push notification without declaring or requesting the `POST_NOTIFICATIONS` permission required in newer Android versions. The permission is now correctly declared, and safety checks are in place to prevent the app from forcefully closing if permission hasn't been granted.
- **Autofill Tuning**: Further stabilized the keyboard input settings to maximize the chance of password managers like 1Password successfully detecting the login fields during setup.

## [1.5.3] - 2026-04-12
### Fixed
- **NavHost Crash**: Resolved a critical issue where the app would crash immediately after setup because the navigation start destination changed dynamically.
- **1Password Autofill**: Enhanced accessibility labels and semantic properties on username and password fields to ensure proper triggering of password managers like 1Password.

## [1.5.1] - 2026-04-11
### Added
- **Pull-to-Refresh**: Easily refresh content natively by swiping down on the Main and Media screens.
- **Smooth Animations**: Added beautiful crossfade and fade-in animations for loading states and live views to enhance the overall feel and align with Android design standards.

### Fixed
- **Critical Crash Fix**: Completely refactored data storage and synchronization logic to strictly utilize asynchronous Flow and Suspend architecture, permanently resolving the "crash on load" issue after entering credentials.
- **Performance**: Optimized Main Screen state observation.

## [1.5.0] - 2026-04-11
### Added
- **GPS Integration**: Capture station's Latitude and Longitude during setup using phone's GPS.
- **Enhanced Setup Experience**: Modern UI redesign for the initial setup screens.
- **High-Res Moon Phase**: High-resolution moon image with dynamic shadow mask.
- **Custom Placeholders**: New custom high-quality placeholders for Raw Images and Timelapses.
- **Built-in Weather API Key**: The default OpenWeather API key is now baked into the app.

### Fixed
- **Startup Stability**: Reverted to a proven synchronous preference loading system to eliminate "crash on load" issues.
- **Media Viewer Controls**: Fixed visibility and responsiveness of viewer buttons.

## [1.4.8] - 2026-04-11
### Fixed
- **App Stability**: Replaced blocking data calls with asynchronous operations to prevent deadlocks and crashes during app initialization.
- **Startup Crash**: Fixed a critical issue that caused the app to crash on load for some users.
- **Setup UX**: Refined autofill support for better compatibility across devices.

## [1.4.7] - 2026-04-11
### Added
- **New Timelapse Placeholder**: Replaced the generic play icon with the custom high-quality placeholder for all timelapses.

## [1.4.6] - 2026-04-11
### Added
- **Built-in Weather API Key**: The default OpenWeather API key is now baked into the app, so you don't have to enter it manually during setup.

### Fixed
- **Timelapse Placeholders**: Fixed an issue where timelapses were incorrectly using the raw image placeholder.

## [1.4.5] - 2026-04-11
### Added
- **GPS Integration**: Added ability to capture station's Latitude and Longitude during setup using the phone's GPS.
- **Enhanced Setup Experience**: Modern UI redesign for the initial setup screens with vertical gradients and improved clarity.
- **High-Res Moon Phase**: Replaced blurry moon emojis with a high-resolution moon image and a dynamic shadow mask for accurate phase visualization.
- **Custom Raw Image Placeholder**: Added a high-quality placeholder image for the Daily Raw Images section.

### Fixed
- **Media Viewer Controls**: Improved responsiveness and visibility of Download and Close (X) buttons in the image and video players.
- **Autofill Support**: Enhanced login fields with proper keyboard types and hints for password managers.
- **Moon Phase Calculation**: Improved the accuracy of the moon phase algorithm using Instant-based UTC time.

## [1.4.4] - 2026-04-09
### Added
- **Native Date Picker**: Replaced legacy text entry with a modern Material3 DatePickerDialog.
- **Themed Placeholders**: Added a dynamic vertical gradient for missing or loading media thumbnails.
- **Station-Centric Weather**: Forecasts now accurately use the Allsky station's coordinates instead of the device GPS.

### Fixed
- **Refined Media Filtering**: Tightened logic to accurately exclude system/generic files without breaking URL query strings or causing false positives.
- **Image Viewer Usability**: Enlarged the 'X' (Close) button tap area and improved dismissal gesture consistency.
- **Download Authentication**: Fixed the download functionality to ensure Basic Auth headers are correctly passed to Android's DownloadManager.
- **About Page Update**: Refreshed content, updated authorship, and added proper credit to the original concept creators.

## [1.4.3] - 2026-04-07
### Fixed
- Fixed a build failure caused by missing adaptive icon background resources.

## [1.4.2] - 2026-04-07
### Fixed
- **App Icon Overhaul**: Removed legacy vector assets that were preventing the new custom icon from appearing on modern Android devices.
- **Layout Persistence**: Improved the layout logic to ensure new modules like "Best Viewing Night" appear for all users, even if they had a previous layout saved.
- **Restore Defaults**: Added a "Restore Defaults" button to the Layout Editor.
- **Robust Media Fetching**: Added a realistic User-Agent and fixed nested URL authentication issues to ensure galleries populate correctly.
- **Full Screen Media**: Fixed a bug where clicking media in the full-screen gallery view wouldn't open the image/video player.

## [1.4.1] - 2026-04-07
### Fixed
- Fixed critical compilation errors in MainScreen, AboutScreen, SettingsScreen, and AllskyRepository.
- Improved Date handling and units in UI.

## [1.4.0] - 2026-04-07
### Added
- **New App Icon**: Completely updated the application identity with a fresh new icon.
- **Best Viewing Night Detection**: Added a smart logic to scan your weather forecast and pinpoint the upcoming night with the best astronomical viewing conditions (lowest clouds and no precipitation).
- **Station-Specific Weather**: Removed phone location dependency. You can now set the exact Latitude and Longitude of your camera station in Settings for pinpoint accurate forecasts.
- **Download Media**: You can now download images and videos directly to your device from the full-screen viewers.
- **Calendar Date Picker**: Replaced manual date typing with an intuitive Android calendar picker in the media galleries.

### Fixed
- **Media Cleanup**: Automatically hiding system files like `allsky-logo.jpg` and `image.jpg` from the galleries.
- **Media Placeholders**: Added dark background placeholders for video and image thumbnails to prevent layout flickering.
- **About Page Redesign**: Updated author information and credits with a fresh look.

## [1.3.5] - 2026-04-06
### Fixed
- **URL Auto-Correct Bug**: Removed an overzealous auto-correct feature that was silently appending `/allsky` to users' base URLs and saving it to preferences. This caused the app to look in the wrong directory, completely breaking media discovery for many setups.

## [1.3.4] - 2026-04-06
### Fixed
- **Authentication Error Visibility**: Fixed an issue where "No content available" would be displayed if the Allsky Portal was protected by Basic Authentication but credentials were not entered in the app settings. The app now explicitly throws a 401 Unauthorized error and displays a clear message to the user prompting them to enter their Username and Password.

## [1.3.3] - 2026-04-06
### Fixed
- Fixed an issue where media would not display if the web server incorrectly returned a "200 OK" placeholder page for missing subdirectories (e.g., `/videos/`) instead of a 404 error. The app now explicitly requests the portal page first and checks for valid media tags before falling back.
- When selecting a specific date in the "Images" tab, the app now correctly queries the portal directly for that date instead of just listing available days.

## [1.3.2] - 2026-04-06
### Added
- **Customizable Main Screen Layout**: Added a new Layout Editor accessible from the side menu, allowing users to dynamically reorder and toggle modules (Live View, Weather, Timelapses, etc.).
- **Media Date Picker**: Media modules (Timelapses, Keograms, Startrails, Meteors, Images) now have dedicated screens accessible via the Side Menu. Each includes a Date Picker to fetch historical data for a specific day.
- **Dynamic Weather Background**: The Main Screen background now dynamically changes color based on the current weather conditions (e.g., Dark Blue/Purple for Clear, Slate Blue for Rain, Dark Gray for Clouds).
- **Navigation Drawer Menu**: Upgraded the app's navigation system. The settings panel has been replaced by a proper side menu, and all URL/API configurations have been moved to a clean, dedicated "Settings" screen.

## [1.2.2] - 2026-04-06
### Fixed
- Fixed "No Content Available" error by adding the required `&day=All` parameter when fetching portal media.
- Fixed historic image discovery by parsing thumbnail `<img>` tags (since the portal does not use standard links for raw images).
- Cleaned up `/thumbnails` paths when retrieving full-resolution image links.

## [1.2.1] - 2026-04-06
### Fixed
- Robust media discovery for Portal-style URLs (`index.php?page=list_...`).
- Improved support for daily image listings by parsing date-specific portal pages.
- Enhanced path normalization to handle various Allsky URL configurations.

## [1.2.0] - 2026-04-06
### Added
- New "Meteor Recordings" section to view captured meteor events.
- Support for "Historic" daily image archives by correctly parsing daily subdirectories.

### Fixed
- Major overhaul of media discovery to be more robust across different Allsky Portal versions.
- Fixed path resolution issues using absolute URL logic.
- Improved date extraction from filenames and directory names.
- Better handling of relative and root-relative links in directory listings.

## [1.1.9] - 2026-04-06
### Fixed
- Further improved media parsing for various Allsky directory listing styles.
- Fixed issue with relative paths and parent directory links in file lists.
- Support for `.mov` and `.mkv` timelapse formats.
- More robust date extraction from filenames.

## [1.1.8] - 2026-04-06
### Added
- Bold typographic redesign for a more modern "heroic" aesthetic.
- Enhanced Moon Phase display with improved layout and illumination details.
- Uppercase headers and increased letter spacing for a premium feel.

### Fixed
- Robust media parsing for Allsky installations, ensuring timelapses, keograms, and startrails are correctly identified even with non-standard directory listings.
- Improved date extraction from filenames when metadata is missing.

## [1.1.7] - 2026-04-05
### Fixed
- Added detection for live images located at `/current/tmp/image.jpg` (common in local Allsky installations).

## [1.1.6] - 2026-04-05
### Fixed
- Fixed issue where the app failed to load media or live view if the base URL did not contain the `/allsky` path.

## [1.1.5] - 2026-04-05
### Added
- Basic Authentication support for Allsky installations behind proxies.
- Night viewing conditions forecast in the app and Push Notifications.

### Fixed
- Live image loading errors caused by trailing slashes in the Allsky URL.

## [1.1.4] - 2024-11-18
### Fixed
- Update dialog "Later" button now properly closes dialog
- Update status in settings panel now correctly shows available updates
- Added ability to reopen update dialog by clicking status in settings
- Improved update state handling and dialog visibility
- Better user experience for update notifications

## [1.1.3] - 2024-11-17
### Added
- Home screen widget for live Allsky image
  - Manual refresh functionality
  - Last update timestamp display
  - Error state handling
  - Loading state indication
  - Configurable size

### Fixed
- Update dialog "Later" button now properly closes dialog
- Settings panel now correctly shows update status
- Added version number to update available message
- Improved update state handling in settings

## [1.1.2] - 2024-11-16
### Fixed
- Fixed moon phase calculation accuracy
- Improved moon phase illumination calculation
- Updated moon phase boundaries for better precision
- Added illumination percentage display to moon phase card

### Changed
- Upgraded to Media3 for video playback
- Replaced deprecated ExoPlayer components
- Enhanced video player stability

## [1.1.1] - 2024-11-15
### Improved
- Enhanced error handling in weather data fetching
- More robust URL validation for Allsky server connection
- Better error messages for users when API key is missing
- Improved stability of live image updates
- Added debug logging for better troubleshooting
- Enhanced error handling in media gallery parsing

### Fixed
- Proper error handling for invalid Allsky URLs
- Better handling of missing weather API keys
- Improved error state management in LiveImageViewModel
- More robust parsing of media gallery items

## [1.1.0] - 2024-11-15
### Added
- Multi-language support
  - German (de) translation
  - Spanish (es) translation
  - French (fr) translation
  - Italian (it) translation
- Automatic language selection based on system settings
- RTL support with AutoMirrored icons
- Modernized About screen with interactive components
- Component links with license information

### Changed
- Moved all hardcoded strings to resource files
- Updated UI components to use string resources
- Improved accessibility with proper content descriptions
- Enhanced About screen layout with Material 3 cards
- Restructured string resources with clear categorization

### Fixed
- Language-specific date and time formats
- Proper handling of system language changes
- RTL layout issues in navigation icons

## [1.0.0] - 2024-11-14
### Added
- Initial release
- Live image view with 30-second refresh
- Moon phase display with illumination percentage
- Weather forecast integration with OpenWeather API
- Keogram gallery with full-screen viewer
- Startrail gallery with full-screen viewer
- Timelapse video gallery
- Setup wizard for first launch
- Settings panel for URL and API key configuration
- About page with library attributions
- Dark/Light theme support
- Dynamic color support for Android 12+
- Location-based weather data
- Automatic content refresh on URL change
- Support for Android 10 and above

### Technical Features
- MVVM architecture with unidirectional data flow
- Coroutines for asynchronous operations
- StateFlow for state management
- Jetpack Compose UI
- Material 3 design system
- Repository pattern for data access
- HTML parsing for media galleries
- Lifecycle-aware components
- DataStore for preferences
- GPS location services
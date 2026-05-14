# AI Refactor Summary

## Verification

- `git diff --check` passes.
- `./gradlew assembleDebug` passes.
- `./gradlew testDebugUnitTest` passes.
- `./gradlew lintDebug` passes.
- `./gradlew assembleRelease --no-daemon --stacktrace` passes.
- No new dependencies were added.

## Manual Steps

- None required for this workspace. Release publishing still depends on the existing GitHub Actions signing secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

## Modified Files

### `app/build.gradle.kts`

- Before: Version remained at `3.6.0`, which already has an existing Git tag.
- After: Bumped to `versionCode = 63` and `versionName = "3.6.1"` so the release workflow can publish a fresh patch release.

### `CHANGELOG.md`

- Before: Release notes ended at `3.6.0`.
- After: Added `3.6.1` notes covering concurrency, image memory, auth, worker, and lifecycle changes.

### `app/src/main/AndroidManifest.xml`

- Before: Declared `android.permission.BIND_APPWIDGET`, a system-only permission normal apps cannot receive.
- After: Removed the invalid permission declaration.

### `app/src/main/java/de/astronarren/allsky/MainActivity.kt`

- Before: Coil auth interception had no Activity-owned lifecycle for background preference collection.
- After: Added an Activity-owned IO scope for the image pipeline, passes it into the auth interceptor, and cancels it in `onDestroy`; `LanguageManager` is also closed.

### `app/src/main/java/de/astronarren/allsky/data/AllskyRepository.kt`

- Before: Best-effort media fallbacks could swallow coroutine cancellation.
- After: Cancellation is rethrown while ordinary per-source portal/directory fetch failures remain best-effort.

### `app/src/main/java/de/astronarren/allsky/data/FocusController.kt`

- Before: SSH polling loops used `Thread.sleep` from coroutine code.
- After: Replaced blocking sleeps with cancellable `delay` and preserved cancellation in SSH/HTTP paths.

### `app/src/main/java/de/astronarren/allsky/data/UpdateRepository.kt`

- Before: Built its own Retrofit client and parsed version segments with throwing `toInt`.
- After: Uses the shared network provider, handles non-numeric version segments with `toIntOrNull`, and preserves cancellation.

### `app/src/main/java/de/astronarren/allsky/data/WeatherRepository.kt`

- Before: Forecast calls depended on caller dispatcher context.
- After: Runs preference reads and weather network calls on `Dispatchers.IO`, preserving cancellation.

### `app/src/main/java/de/astronarren/allsky/data/astro/AuroraRepository.kt`

- Before: NOAA fetch/parsing ran on caller context and `runCatching` could hide cancellation.
- After: Fetch/parsing runs on `Dispatchers.IO`; cancellation propagates while network failure can still fall back to cache.

### `app/src/main/java/de/astronarren/allsky/data/astro/SatelliteRepository.kt`

- Before: TLE fetch/parsing and pass prediction could run on caller context; cache fallback could hide cancellation.
- After: TLE work runs on IO, pass prediction runs on Default, and cancellation propagates through cache fallback.

### `app/src/main/java/de/astronarren/allsky/data/network/WeatherApiProvider.kt`

- Before: Created new Retrofit instances per provider call.
- After: Provides lazy singleton weather, geocoding, and update services backed by one configured OkHttp client.

### `app/src/main/java/de/astronarren/allsky/network/AllskyAuth.kt`

- Before: The Coil interceptor used `runBlocking` to read DataStore credentials on request threads.
- After: Keeps a lifecycle-owned in-memory auth snapshot; request interception is non-blocking and stored credentials only apply to the configured Allsky host.

### `app/src/main/java/de/astronarren/allsky/ui/MainScreen.kt`

- Before: Live-image palette extraction forced full software bitmap decoding on every refresh.
- After: Normal refreshes can use hardware bitmaps; palette extraction samples a downscaled software bitmap at most every five minutes and adds explicit auth headers.

### `app/src/main/java/de/astronarren/allsky/ui/components/CalibrationScreen.kt`

- Before: Forced software bitmap decoding just to read image dimensions.
- After: Uses drawable intrinsic dimensions and explicit auth headers without forcing bitmap allocation.

### `app/src/main/java/de/astronarren/allsky/ui/components/FullScreenImageViewer.kt`

- Before: Relied on global interception for authenticated full-screen image loads.
- After: Resolves URL userinfo or stored credentials asynchronously and passes direct auth headers for the configured Allsky host.

### `app/src/main/java/de/astronarren/allsky/ui/components/VideoPlayer.kt`

- Before: Used `runBlocking` during Compose composition to read video credentials.
- After: Resolves auth asynchronously, strips URL userinfo, configures ExoPlayer with direct headers, and annotates Media3 unstable APIs for lint.

### `app/src/main/java/de/astronarren/allsky/ui/layout/LayoutEditorScreen.kt`

- Before: Used a `produceState` collector pattern reported by Compose lint.
- After: Uses remembered state plus `LaunchedEffect` collection.

### `app/src/main/java/de/astronarren/allsky/utils/DownloadHelper.kt`

- Before: Added stored Basic Auth to downloads whenever URL userinfo was absent, regardless of target host.
- After: Reuses shared Allsky auth resolution and preserves coroutine cancellation.

### `app/src/main/java/de/astronarren/allsky/utils/LanguageManager.kt`

- Before: Created unmanaged main-thread coroutine scopes tied to Activity context.
- After: Uses one supervised scope with explicit `close()` lifecycle cleanup.

### `app/src/main/java/de/astronarren/allsky/viewmodel/AllskyViewModel.kt`

- Before: Multiple media refreshes could overlap and stale results could win.
- After: Cancels the previous load job before starting another and does not convert cancellation into stale UI errors.

### `app/src/main/java/de/astronarren/allsky/viewmodel/LiveImageViewModel.kt`

- Before: Separate polling and URL-observer jobs could race while mutating cached stream state.
- After: Uses one Flow-driven polling pipeline keyed by URL/credentials, invalidates cache on config change, and handles cancellation safely.

### `app/src/main/java/de/astronarren/allsky/viewmodel/SetupViewModel.kt`

- Before: Geocoding requests depended on caller context.
- After: Runs geocoding on `Dispatchers.IO` and preserves search-job cancellation.

### `app/src/main/java/de/astronarren/allsky/viewmodel/TonightViewModel.kt`

- Before: Aurora and satellite child jobs used `runCatching`, which could swallow cancellation.
- After: Keeps row-level failure isolation while rethrowing coroutine cancellation.

### `app/src/main/java/de/astronarren/allsky/viewmodel/WeatherViewModel.kt`

- Before: Preference-triggered and manual refreshes could overlap.
- After: Distinct preference changes schedule one cancellable weather refresh job.

### `app/src/main/java/de/astronarren/allsky/workers/WeatherWorker.kt`

- Before: Worker network/DataStore work used the default worker coroutine context and cancellation could become retry.
- After: Runs the worker body on `Dispatchers.IO` and rethrows cancellation.

### `app/src/main/java/de/astronarren/allsky/workers/WidgetUpdateWorker.kt`

- Before: Created a fresh Coil `ImageLoader`, decoded a larger widget bitmap, did not always disconnect HEAD probes, and could turn cancellation into retry.
- After: Reuses Coil's global loader, requests smaller RemoteViews bitmaps, disconnects probes in `finally`, and preserves cancellation.

### `AI_REFACTOR_SUMMARY.md`

- Before: No committed refactor summary existed for this patch.
- After: Documents the modified files, before/after rationale, verification, and manual release prerequisites.

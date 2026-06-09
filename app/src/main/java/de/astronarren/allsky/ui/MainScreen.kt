package de.astronarren.allsky.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import de.astronarren.allsky.data.SkyRating
import de.astronarren.allsky.ui.components.*
import de.astronarren.allsky.utils.NotificationHelper
import de.astronarren.allsky.workers.WeatherWorker
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.data.WeatherData
import de.astronarren.allsky.viewmodel.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import de.astronarren.allsky.R
import de.astronarren.allsky.utils.LanguageManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.astronarren.allsky.ui.theme.*
import androidx.navigation.NavController
import de.astronarren.allsky.ui.modules.FocusModule
import de.astronarren.allsky.ui.modules.TonightModule
import de.astronarren.allsky.viewmodel.FocusViewModel
import de.astronarren.allsky.viewmodel.FocusViewModelFactory
import de.astronarren.allsky.network.AllskyAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    userPreferences: UserPreferences,
    weatherViewModel: WeatherViewModel,
    allskyViewModel: AllskyViewModel,
    imageViewerViewModel: ImageViewerViewModel,
    liveImageViewModel: LiveImageViewModel,
    languageManager: LanguageManager,
    /**
     * When non-null, MainScreen scrolls the matching layout module into view
     * and briefly highlights it. Set by notification deep links (see
     * [de.astronarren.allsky.utils.NotificationHelper.EXTRA_SCROLL_TO]).
     * The owner clears it via [onScrollTargetConsumed] once we've acted.
     */
    scrollTarget: String? = null,
    onScrollTargetConsumed: () -> Unit = {},
) {
    var isSettingsOpen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var isRefreshing by remember { mutableStateOf(false) }

    // Y-offset (in scroll-content pixels) of each layout module as it lays
    // out. Populated by Modifier.onPlaced on each module's outer container;
    // consumed by the deep-link scroll-to effect below.
    val moduleOffsets = remember { mutableStateMapOf<String, Int>() }
    // Name of the module currently being pulsed after a deep-link. Cleared
    // automatically after a short hold.
    var highlightedModule by remember { mutableStateOf<String?>(null) }

    // Deep-link reaction. We re-attempt scrolling on offset changes so the
    // initial composition (which fires before the cards have measured) can
    // still land on the right card once layout settles.
    LaunchedEffect(scrollTarget, moduleOffsets[scrollTarget]) {
        val target = scrollTarget ?: return@LaunchedEffect
        val y = moduleOffsets[target] ?: return@LaunchedEffect
        scrollState.animateScrollTo(y)
        highlightedModule = target
        // Consume *after* the scroll so the caller doesn't re-trigger on
        // recomposition. The highlight teardown runs in the separate effect
        // below so it survives this effect being cancelled.
        onScrollTargetConsumed()
    }

    // Hold the highlight ~1.6 s after a deep-link hit, then fade back. Lives
    // in its own effect keyed on highlightedModule so it isn't cancelled
    // when scrollTarget is cleared above.
    LaunchedEffect(highlightedModule) {
        if (highlightedModule != null) {
            delay(1600)
            highlightedModule = null
        }
    }
    
    val weatherUiState by weatherViewModel.uiState.collectAsStateWithLifecycle()
    val allskyUiState by allskyViewModel.uiState.collectAsStateWithLifecycle()
    val imageViewerState by imageViewerViewModel.uiState.collectAsStateWithLifecycle()
    val liveImageState by liveImageViewModel.uiState.collectAsStateWithLifecycle()

    val mainLayout by userPreferences.getMainLayoutFlow().collectAsStateWithLifecycle(
        initialValue = listOf("LIVE_VIEW", "BEST_VIEWING", "WEATHER", "TIMELAPSES", "METEORS", "IMAGES", "KEOGRAMS", "STARTRAILS", "MOON")
    )

    // FocusViewModel lives at MainScreen scope so the home-screen FOCUS module
    // and the standalone Focus screen don't each re-probe the rig on every
    // navigation. The init block runs one auto-probe; subsequent moves use
    // the cached settings until the user edits credentials.
    val focusViewModel: FocusViewModel = viewModel(
        factory = FocusViewModelFactory(userPreferences)
    )

    // TonightViewModel orchestrates the meteor / moon / planets / aurora /
    // satellites rows. One viewmodel kicks off all five data sources at
    // MainScreen scope so we don't refetch on every recomposition of the
    // TonightModule card.
    val tonightViewModel: de.astronarren.allsky.viewmodel.TonightViewModel = viewModel(
        factory = de.astronarren.allsky.viewmodel.TonightViewModelFactory(userPreferences)
    )
    
    val allskyUrl by userPreferences.getAllskyUrlFlow().collectAsStateWithLifecycle(initialValue = "")
    val stationName by userPreferences.getStationNameFlow().collectAsStateWithLifecycle(initialValue = "")
    val apiKey by userPreferences.getApiKeyFlow().collectAsStateWithLifecycle(initialValue = "")
    val allskyUsername by userPreferences.getUsernameFlow().collectAsStateWithLifecycle(initialValue = "")
    val allskyPassword by userPreferences.getPasswordFlow().collectAsStateWithLifecycle(initialValue = "")
    val allskyAuthHeader = remember(allskyUsername, allskyPassword) {
        AllskyAuth.basicAuthHeader(allskyUsername, allskyPassword)
    }
    val skyAlertsEnabled by userPreferences.getSkyAlertsEnabledFlow().collectAsStateWithLifecycle(initialValue = false)
    val redLightEnabled by userPreferences.getRedLightModeFlow().collectAsStateWithLifecycle(initialValue = false)

    // Sky-overlay state. Both flows always emit (the calibration flow falls
    // back to the inscribed-circle default), so the overlay Composable can
    // unconditionally read them and decide whether to paint itself.
    val skyOverlayEnabled by userPreferences.getSkyOverlayEnabledFlow()
        .collectAsStateWithLifecycle(initialValue = false)
    val fisheyeCalibration by userPreferences.getFisheyeCalibrationFlow()
        .collectAsStateWithLifecycle(
            initialValue = de.astronarren.allsky.data.astro.FisheyeCalibration.DEFAULT_INSCRIBED
        )
    val tonightState by tonightViewModel.state.collectAsStateWithLifecycle()

    // Runtime POST_NOTIFICATIONS permission. We launch the system prompt
    // the first time the user flips the alerts toggle ON. If denied, the
    // toggle stays on but NotificationHelper.canPost() silently no-ops —
    // we don't pester the user. Re-enabling won't re-prompt either; the
    // system shows our request only when it would actually pop the dialog.
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* result intentionally ignored — see comment above */ }

    var currentVideo by remember { mutableStateOf<String?>(null) }
    var paletteColors by remember { mutableStateOf<List<Color>?>(null) }
    var lastPaletteSampleAt by remember { mutableLongStateOf(0L) }

    // When a viewer (image or video) is open we hide the entire app chrome so
    // the user only sees the media + viewer-owned controls. Previously the
    // top bar (station name + URL) and burger menu both bled through behind
    // the dim layer, and the burger's hit target overlapped the viewer's X.
    val isViewerOpen = (imageViewerState.isFullScreen && imageViewerState.currentImageUrl != null) || currentVideo != null

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isViewerOpen,
        drawerContent = {
            SettingsPanel(
                isOpen = isSettingsOpen,
                redLightEnabled = redLightEnabled,
                onRedLightToggle = { enabled ->
                    scope.launch { userPreferences.setRedLightMode(enabled) }
                },
                skyAlertsEnabled = skyAlertsEnabled,
                onSkyAlertsToggle = { enabled ->
                    scope.launch { userPreferences.setSkyAlertsEnabled(enabled) }
                    // Prompt for POST_NOTIFICATIONS the first time alerts
                    // are switched ON (no-op on API < 33 — there's no
                    // runtime permission to request).
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    // Kick off an immediate one-off WeatherWorker run when
                    // the user enables alerts. Without this they'd wait up
                    // to the periodic worker's full interval (3h) before
                    // anything could fire — confusing first-run experience.
                    // The periodic schedule from MainActivity is unchanged.
                    if (enabled) {
                        val req = OneTimeWorkRequestBuilder<WeatherWorker>().build()
                        WorkManager.getInstance(context).enqueue(req)
                    }
                },
                onSkyAlertsTestFire = {
                    // Hand-rolled sample improvement so users can validate
                    // the deep-link + Best Viewing highlight without the
                    // weather actually changing.
                    NotificationHelper(context).showSkyRatingImprovedNotification(
                        previousLabel = SkyRating.FAIR.label,
                        newLabel = SkyRating.EXCELLENT.label,
                        cloudCoverPct = 12,
                    )
                },
                onDismiss = {
                    scope.launch {
                        drawerState.close()
                        isSettingsOpen = false
                    }
                },
                onNavigate = { route ->
                    scope.launch {
                        drawerState.close()
                        isSettingsOpen = false
                    }
                    if (route == "home") {
                        navController.navigate(route) {
                            popUpTo("home") { inclusive = true }
                        }
                    } else {
                        navController.navigate(route)
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                // While a viewer is open we draw no top bar at all — the
                // station name, URL pill and burger menu would otherwise
                // bleed through behind the media and overlap the viewer's
                // close button.
                if (!isViewerOpen) {
                    CaelumHomeHeader(
                        stationName = stationName,
                        hostUrl = allskyUrl,
                        live = liveImageState.error == null && !liveImageState.imageUrl.isNullOrEmpty(),
                        redLightEnabled = redLightEnabled,
                        onMenu = {
                            scope.launch {
                                isSettingsOpen = true
                                drawerState.open()
                            }
                        },
                        onToggleRedLight = {
                            scope.launch { userPreferences.setRedLightMode(!redLightEnabled) }
                        },
                    )
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            
            // Dynamic Background based on weather and live image palette.
            // Weather-driven gradients are intentionally close to the default
            // navy so the app keeps a single visual identity instead of
            // looking like a different app in every weather condition.
            val caelum = LocalCaelum.current
            val weatherCondition = weatherUiState.weatherData?.second?.firstOrNull()?.weather?.firstOrNull()?.main ?: "Clear"
            val backgroundColors = remember(weatherCondition, paletteColors, redLightEnabled, caelum) {
                when {
                    // Red-Light: a flat deep-red field — never the navy/palette wash.
                    redLightEnabled -> listOf(caelum.field, caelum.field2, caelum.field)
                    paletteColors != null && paletteColors!!.size >= 2 -> paletteColors!!
                    else -> when (weatherCondition) {
                        "Clear" -> listOf(DeepNavy, NightPurple, ClearNight)
                        "Clouds" -> listOf(Color(0xFF0B1224), Color(0xFF1A2440), Color(0xFF2B3656))
                        "Rain", "Drizzle", "Thunderstorm" -> listOf(Color(0xFF0A1626), Color(0xFF152A40), Color(0xFF1F3A58))
                        "Snow" -> listOf(Color(0xFF101A38), Color(0xFF1C2A5A), Color(0xFF2F4380))
                        else -> listOf(DeepNavy, NightPurple, ClearNight)
                    }
                }
            }

            de.astronarren.allsky.ui.components.AppBackground(
                colors = backgroundColors
            ) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            if (apiKey.isNotEmpty()) weatherViewModel.updateWeather()
                            if (allskyUrl.isNotEmpty()) allskyViewModel.fetchContentForDate()
                            delay(1200)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding()
                            )
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        
                        mainLayout.forEach { moduleName ->
                            when (moduleName) {
                                "LIVE_VIEW" -> {
                                    if (allskyUrl.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(440.dp)
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable(
                                                        enabled = !liveImageState.imageUrl.isNullOrEmpty()
                                                    ) {
                                                        liveImageState.imageUrl?.takeIf { it.isNotEmpty() }
                                                            ?.let { imageViewerViewModel.showImage(it) }
                                                    },
                                                shape = RoundedCornerShape(32.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp, Color.White.copy(alpha = 0.12f)
                                                )
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    // Crossfade on `streamKey` (the resolved endpoint without the
                                                    // cache buster) so the 30-second `?t=` refresh doesn't fade
                                                    // the whole card in and out every cycle.
                                                    AnimatedContent(
                                                        targetState = liveImageState.streamKey,
                                                        transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
                                                        label = "LiveImageCrossfade",
                                                        modifier = Modifier.fillMaxSize()
                                                    ) { targetKey ->
                                                        val targetUrl = if (targetKey != null) liveImageState.imageUrl else null
                                                        if (targetUrl != null) {
                                                            val samplePaletteOnThisLoad =
                                                                System.currentTimeMillis() - lastPaletteSampleAt >= 5 * 60_000L
                                                            AsyncImage(
                                                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                                    .data(targetUrl)
                                                                    .allowHardware(!samplePaletteOnThisLoad)
                                                                    .listener(
                                                                        onSuccess = { _, result ->
                                                                            val drawable = result.drawable
                                                                            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                                                                // Push intrinsic image size into the VM so the
                                                                                // SkyOverlay can apply the ContentScale.Crop
                                                                                // transform to fractional fisheye coords.
                                                                                liveImageViewModel.setImageSize(
                                                                                    drawable.intrinsicWidth,
                                                                                    drawable.intrinsicHeight
                                                                                )
                                                                            }
                                                                            val bmp = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                                                            if (samplePaletteOnThisLoad && bmp != null) {
                                                                                lastPaletteSampleAt = System.currentTimeMillis()
                                                                                androidx.palette.graphics.Palette.from(bmp)
                                                                                    .resizeBitmapArea(64 * 64)
                                                                                    .maximumColorCount(8)
                                                                                    .generate { p ->
                                                                                        val dom = p?.dominantSwatch?.rgb
                                                                                        val darkMuted = p?.darkMutedSwatch?.rgb
                                                                                        val darkVibrant = p?.darkVibrantSwatch?.rgb
                                                                                        if (dom != null) {
                                                                                            paletteColors = listOf(
                                                                                                Color(dom),
                                                                                                Color(darkVibrant ?: darkMuted ?: dom).copy(alpha = 0.8f),
                                                                                                Color(darkMuted ?: dom).copy(alpha = 0.6f)
                                                                                            )
                                                                                        }
                                                                                    }
                                                                            }
                                                                        }
                                                                    )
                                                                    .apply {
                                                                        allskyAuthHeader?.let {
                                                                            setHeader("Authorization", it)
                                                                        }
                                                                    }
                                                                    .build(),
                                                                contentDescription = stringResource(R.string.live_allsky_image),
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop,
                                                                colorFilter = caelumImageColorFilter(),
                                                            )
                                                        } else {
                                                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                                                CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
                                                            }
                                                        }
                                                    }

                                                    // Sky overlay — moon and naked-eye planets, projected through the
                                                    // saved fisheye calibration. No-ops when disabled or before the
                                                    // first image arrives, so it's safe to always include in the tree.
                                                    SkyOverlay(
                                                        enabled = skyOverlayEnabled,
                                                        calibration = fisheyeCalibration,
                                                        imageWidthPx = liveImageState.imageWidthPx,
                                                        imageHeightPx = liveImageState.imageHeightPx,
                                                        moon = tonightState.moonHorizontal,
                                                        planets = tonightState.visiblePlanets,
                                                    )

                                                    // Stream Error Overlay
                                                    if (liveImageState.error != null) {
                                                        Surface(
                                                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                                                            color = Color.Red.copy(alpha = 0.7f)
                                                        ) {
                                                            Text(
                                                                liveImageState.error!!,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(8.dp),
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }

                                                    LivePill(
                                                        modifier = Modifier
                                                            .align(Alignment.TopStart)
                                                            .padding(20.dp)
                                                    )

                                                    if (formatTime(liveImageState.lastUpdate).isNotEmpty()) {
                                                        MonoChip(
                                                            text = formatTime(liveImageState.lastUpdate),
                                                            modifier = Modifier
                                                                .align(Alignment.BottomEnd)
                                                                .padding(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Verdict band — the verdict-first IA's
                                        // headline, directly under the live image.
                                        Spacer(modifier = Modifier.height(2.dp))
                                        TonightVerdictBand(
                                            forecasts = weatherUiState.weatherData?.second ?: emptyList(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .padding(bottom = 6.dp),
                                        )
                                    }
                                }
                                "BEST_VIEWING" -> {
                                    val bestNight = weatherViewModel.getBestViewingNight()
                                    if (bestNight != null) {
                                        val highlightTarget = highlightedModule == "BEST_VIEWING"
                                        val highlightColor by animateColorAsState(
                                            targetValue = if (highlightTarget)
                                                Color(0xFF69F0AE) // soft mint, matches the connection chip
                                            else Color.Transparent,
                                            animationSpec = tween(durationMillis = 500),
                                            label = "bestViewingHighlight"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                .onPlaced { coords ->
                                                    moduleOffsets["BEST_VIEWING"] =
                                                        coords.positionInParent().y.toInt()
                                                }
                                                .border(2.dp, highlightColor, RoundedCornerShape(28.dp))
                                        ) {
                                            de.astronarren.allsky.ui.components.GlassCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                cornerRadius = 28.dp,
                                                elevated = true
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                Text(
                                                    text = "BEST VIEWING NIGHT",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 3.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(bestNight.dt * 1000L)).uppercase(),
                                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "${bestNight.weather.firstOrNull()?.description?.uppercase() ?: ""} • ${bestNight.clouds.all}% CLOUDS",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp
                                                    ),
                                                    color = Color.White.copy(alpha = 0.55f)
                                                )
                                                }
                                            }
                                        }
                                    }
                                }
                                "WEATHER" -> {
                                    if (apiKey.isEmpty()) {
                                        de.astronarren.allsky.ui.components.GlassCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            cornerRadius = 28.dp
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "WEATHER FORECAST",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Black, letterSpacing = 2.sp
                                                    ),
                                                    color = Color.White.copy(alpha = 0.7f)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "Add your OpenWeather API key to see local forecasts.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White.copy(alpha = 0.55f),
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                val uriHandler = LocalUriHandler.current
                                                Button(
                                                    onClick = { uriHandler.openUri("https://home.openweathermap.org/api_keys") },
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color.White,
                                                        contentColor = MaterialTheme.colorScheme.background
                                                    )
                                                ) {
                                                    Text(
                                                        "GET API KEY",
                                                        style = MaterialTheme.typography.labelLarge.copy(
                                                            fontWeight = FontWeight.Black,
                                                            letterSpacing = 2.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        WeatherDisplay(uiState = weatherUiState)
                                    }
                                }
                                "MOON" -> {
                                    MoonPhaseDisplay()
                                }
                                "TIMELAPSES" -> {
                                    AllskyMediaSection(
                                        title = "RECENT TIMELAPSES",
                                        media = allskyUiState.timelapses,
                                        onMediaClick = { media -> currentVideo = media.url },
                                        isLoading = allskyUiState.isLoading,
                                        error = allskyUiState.error
                                    )
                                }
                                "METEORS" -> {
                                    AllskyMediaSection(
                                        title = "METEOR RECORDINGS",
                                        media = allskyUiState.meteors,
                                        onMediaClick = { media ->
                                            if (media.url.lowercase().contains(".mp4") ||
                                                media.url.lowercase().contains(".webm")) {
                                                currentVideo = media.url
                                            } else {
                                                imageViewerViewModel.showImage(media.url)
                                            }
                                        },
                                        isLoading = allskyUiState.isLoading,
                                        error = allskyUiState.error
                                    )
                                }
                                "IMAGES" -> {
                                    AllskyMediaSection(
                                        title = "DAILY RAW IMAGES",
                                        media = allskyUiState.images,
                                        onMediaClick = { media -> imageViewerViewModel.showImage(media.url) },
                                        isLoading = allskyUiState.isLoading,
                                        error = allskyUiState.error
                                    )
                                }
                                "KEOGRAMS" -> {
                                    AllskyMediaSection(
                                        title = "KEOGRAMS",
                                        media = allskyUiState.keograms,
                                        onMediaClick = { media -> imageViewerViewModel.showImage(media.url) },
                                        isLoading = allskyUiState.isLoading,
                                        error = allskyUiState.error
                                    )
                                }
                                "STARTRAILS" -> {
                                    AllskyMediaSection(
                                        title = "STARTRAILS",
                                        media = allskyUiState.startrails,
                                        onMediaClick = { media -> imageViewerViewModel.showImage(media.url) },
                                        isLoading = allskyUiState.isLoading,
                                        error = allskyUiState.error
                                    )
                                }
                                "TONIGHT" -> {
                                    TonightModule(viewModel = tonightViewModel)
                                }
                                "FOCUS" -> {
                                    // FocusModule self-collapses when the rig
                                    // isn't reachable, so the user sees a hole
                                    // only on intentional config issues —
                                    // never on routine network blips.
                                    FocusModule(
                                        viewModel = focusViewModel,
                                        onOpenFocusScreen = { navController.navigate("focus") }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                // Overlay components
                if (imageViewerState.isFullScreen && imageViewerState.currentImageUrl != null) {
                    FullScreenImageViewer(
                        imageUrl = imageViewerState.currentImageUrl!!,
                        userPreferences = userPreferences,
                        onDismiss = { imageViewerViewModel.dismissImage() }
                    )
                }

                if (currentVideo != null) {
                    VideoPlayer(
                        videoUrl = currentVideo!!,
                        userPreferences = userPreferences,
                        onDismiss = { currentVideo = null }
                    )
                }
            }
        }
    }
}

/**
 * Verdict band — the headline of Direction A's verdict-first IA. Computes
 * tonight's viewing rating from the forecast and shows it as an eyebrow +
 * one-line reason + a role-coloured badge.
 */
@Composable
private fun TonightVerdictBand(
    forecasts: List<WeatherData>,
    modifier: Modifier = Modifier,
) {
    val c = LocalCaelum.current
    val rating = remember(forecasts) {
        de.astronarren.allsky.data.SkyRater.rateNight(forecasts)
    }
    val (role, reason) = when (rating) {
        SkyRating.EXCELLENT -> c.good to "Clear skies — excellent conditions tonight."
        SkyRating.GOOD -> c.good to "Mostly clear — good viewing tonight."
        SkyRating.FAIR -> c.fair to "Partly cloudy — fair viewing tonight."
        SkyRating.POOR -> c.poor to "Overcast or wet — poor viewing tonight."
        null -> c.inkFaint to "Not enough forecast data yet."
    }
    CaelumCard(
        modifier = modifier,
        tonal = true,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow("Tonight's Viewing", accent = c.inkFaint)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkDim,
                )
            }
            if (rating != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = rating.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = role,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(role.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/**
 * Caelum home header — burger on the left, a centred station block (live LED +
 * station name + mono host URL), and the Red-Light night-vision toggle on the
 * right. Draws its own status-bar inset since it replaces the M3 app bar.
 */
@Composable
private fun CaelumHomeHeader(
    stationName: String,
    hostUrl: String,
    live: Boolean,
    redLightEnabled: Boolean,
    onMenu: () -> Unit,
    onToggleRedLight: () -> Unit,
) {
    val c = LocalCaelum.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaelumIconButton(
            icon = Icons.Default.Menu,
            contentDescription = "Menu",
            onClick = onMenu,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (live) c.good else c.poor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (stationName.isNotEmpty()) stationName else "CAELUM",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.ink,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
            if (hostUrl.isNotEmpty()) {
                Text(
                    text = hostUrl.substringAfter("://").substringBefore("/"),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.signal,
                    maxLines = 1,
                )
            }
        }
        CaelumIconButton(
            icon = Icons.Default.DarkMode,
            contentDescription = "Toggle Red-Light mode",
            onClick = onToggleRedLight,
            tint = if (redLightEnabled) c.signal else c.inkDim,
        )
    }
}

private fun formatTime(timestamp: Long): String {
    return if (timestamp == 0L) {
        ""
    } else {
        // Assume timestamp is in millis if it's large, otherwise seconds
        val millis = if (timestamp < 1000000000000L) timestamp * 1000L else timestamp
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }
}

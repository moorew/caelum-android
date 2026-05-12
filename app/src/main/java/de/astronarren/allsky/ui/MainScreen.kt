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
    val skyAlertsEnabled by userPreferences.getSkyAlertsEnabledFlow().collectAsStateWithLifecycle(initialValue = false)

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
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (stationName.isNotEmpty()) stationName.uppercase() else "ALLSKY",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = if (stationName.isNotEmpty()) 2.sp else 8.sp,
                                        fontSize = if (stationName.isNotEmpty()) 18.sp else 20.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                                if (allskyUrl.isNotEmpty()) {
                                    Text(
                                        allskyUrl.substringAfter("://").substringBefore("/").uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isSettingsOpen = true
                                        drawerState.open()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color.White
                                )
                            }
                        }
                    )
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            
            // Dynamic Background based on weather and live image palette.
            // Weather-driven gradients are intentionally close to the default
            // navy so the app keeps a single visual identity instead of
            // looking like a different app in every weather condition.
            val weatherCondition = weatherUiState.weatherData?.second?.firstOrNull()?.weather?.firstOrNull()?.main ?: "Clear"
            val backgroundColors = remember(weatherCondition, paletteColors) {
                if (paletteColors != null && paletteColors!!.size >= 2) {
                    paletteColors!!
                } else {
                    when (weatherCondition) {
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
                                                            AsyncImage(
                                                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                                    .data(targetUrl)
                                                                    .allowHardware(false)
                                                                    .listener(
                                                                        onSuccess = { _, result ->
                                                                            val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                                                            if (bmp != null) {
                                                                                androidx.palette.graphics.Palette.from(bmp).generate { p ->
                                                                                    val dom = p?.dominantSwatch?.rgb
                                                                                    val darkMuted = p?.darkMutedSwatch?.rgb
                                                                                    val darkVibrant = p?.darkVibrantSwatch?.rgb
                                                                                    if (dom != null) {
                                                                                        paletteColors = listOf(Color(dom), Color(darkVibrant ?: darkMuted ?: dom).copy(alpha = 0.8f), Color(darkMuted ?: dom).copy(alpha = 0.6f))
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    )
                                                                    .build(),
                                                                contentDescription = stringResource(R.string.live_allsky_image),
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                                                CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
                                                            }
                                                        }
                                                    }
                                                    
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

                                                    Surface(
                                                        modifier = Modifier
                                                            .align(Alignment.TopStart)
                                                            .padding(24.dp),
                                                        color = Color.Black.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(12.dp),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .background(if (liveImageState.error == null) Color.Green else Color.Red, RoundedCornerShape(4.dp))
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Text(
                                                                text = "LIVE",
                                                                style = MaterialTheme.typography.labelMedium.copy(
                                                                    fontWeight = FontWeight.Black,
                                                                    letterSpacing = 2.sp
                                                                ),
                                                                color = Color.White
                                                            )
                                                        }
                                                    }

                                                    Surface(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(24.dp),
                                                        color = Color.Black.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(12.dp),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                                    ) {
                                                        Text(
                                                            text = formatTime(liveImageState.lastUpdate),
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = Color.White.copy(alpha = 0.9f),
                                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
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

private fun formatTime(timestamp: Long): String {
    return if (timestamp == 0L) {
        ""
    } else {
        // Assume timestamp is in millis if it's large, otherwise seconds
        val millis = if (timestamp < 1000000000000L) timestamp * 1000L else timestamp
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }
}

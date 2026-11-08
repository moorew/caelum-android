package de.astronarren.allsky

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.data.network.WeatherApiProvider
import de.astronarren.allsky.ui.MainScreen
import de.astronarren.allsky.ui.about.AboutScreen
import de.astronarren.allsky.ui.setup.SetupScreen
import de.astronarren.allsky.ui.theme.AllskyTheme
import de.astronarren.allsky.viewmodel.*
import de.astronarren.allsky.data.WeatherRepository
import de.astronarren.allsky.utils.LanguageManager
import de.astronarren.allsky.utils.NotificationHelper
import de.astronarren.allsky.workers.WeatherWorker
import java.util.concurrent.TimeUnit

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.astronarren.allsky.ui.focus.FocusScreen
import de.astronarren.allsky.ui.layout.LayoutEditorScreen
import de.astronarren.allsky.ui.media.MediaScreen
import de.astronarren.allsky.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    private lateinit var weatherViewModel: WeatherViewModel
    private lateinit var allskyViewModel: AllskyViewModel
    private lateinit var imageViewerViewModel: ImageViewerViewModel
    private lateinit var setupViewModel: SetupViewModel
    private lateinit var liveImageViewModel: LiveImageViewModel
    private lateinit var languageViewModel: LanguageViewModel
    private lateinit var languageManager: LanguageManager

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified VM : androidx.lifecycle.ViewModel> vm(crossinline create: () -> VM): VM =
        androidx.lifecycle.ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = create() as T
        })[VM::class.java]

    /**
     * Set by notification PendingIntents (see [NotificationHelper.EXTRA_SCROLL_TO]).
     * MainScreen consumes the value on next composition and clears it. We
     * keep this as a top-level mutable state so cold-start (intent in
     * onCreate) and warm-start (intent in onNewIntent) share one path.
     */
    private val scrollTarget = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize LanguageManager and ViewModel
        languageManager = LanguageManager(this) {
            // Recreate activity when language changes
            recreate()
        }
        languageViewModel = LanguageViewModel(languageManager)
        
        val userPreferences = CaelumApp.from(this).userPreferences
        // Get WeatherService from provider
        val weatherService = WeatherApiProvider.provideWeatherService()
        
        val weatherRepository = WeatherRepository(
            weatherService = weatherService,
            userPreferences = userPreferences
        )
        
        val app = CaelumApp.from(this)

        // ViewModels go through ViewModelProvider so they survive rotation /
        // recreate() and are cleared (cancelling their refresh loops) when
        // the Activity finishes, instead of leaking a new set each time.
        weatherViewModel = vm {
            WeatherViewModel(
                weatherRepository = weatherRepository,
                userPreferences = userPreferences
            )
        }
        allskyViewModel = vm { AllskyViewModel(app.allskyRepository, app.allskyClient, userPreferences) }
        imageViewerViewModel = vm { ImageViewerViewModel() }
        setupViewModel = vm { SetupViewModel(userPreferences) }
        liveImageViewModel = vm { LiveImageViewModel(userPreferences) }

        scheduleWeatherWorker()

        // Capture any deep-link extra delivered on cold start. Warm starts
        // go through onNewIntent below.
        scrollTarget.value = intent?.getStringExtra(NotificationHelper.EXTRA_SCROLL_TO)

        enableEdgeToEdge()
        setContent {
            val redLight by userPreferences.getRedLightModeFlow()
                .collectAsStateWithLifecycle(initialValue = false)
            val themeMode = if (redLight) {
                de.astronarren.allsky.ui.theme.CaelumThemeMode.RedLight
            } else {
                de.astronarren.allsky.ui.theme.CaelumThemeMode.DeepObservatory
            }
            AllskyTheme(mode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var initialRoute by remember { mutableStateOf<String?>(null) }
                    
                    LaunchedEffect(Unit) {
                        initialRoute = if (userPreferences.isSetupComplete()) "home" else "setup"
                    }
                    
                    if (initialRoute != null) {
                        NavHost(
                            navController = navController,
                            startDestination = initialRoute!!
                        ) {
                            composable("setup") {
                                SetupScreen(
                                    viewModel = setupViewModel,
                                    onSetupComplete = { 
                                        navController.navigate("home") {
                                            popUpTo("setup") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("home") {
                                MainScreen(
                                    navController = navController,
                                    userPreferences = userPreferences,
                                    weatherViewModel = weatherViewModel,
                                    allskyViewModel = allskyViewModel,
                                    imageViewerViewModel = imageViewerViewModel,
                                    liveImageViewModel = liveImageViewModel,
                                    languageManager = languageManager,
                                    scrollTarget = scrollTarget.value,
                                    onScrollTargetConsumed = { scrollTarget.value = null },
                                )
                            }
                            composable("layout_editor") {
                                LayoutEditorScreen(
                                    userPreferences = userPreferences,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("media/{type}") { backStackEntry ->
                                val type = backStackEntry.arguments?.getString("type") ?: "timelapses"
                                val title = type.replaceFirstChar { it.uppercase() }
                                MediaScreen(
                                    title = title,
                                    mediaType = type,
                                    viewModel = allskyViewModel,
                                    userPreferences = userPreferences,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("settings") {
                                val updateViewModel: de.astronarren.allsky.viewmodel.UpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = de.astronarren.allsky.viewmodel.UpdateViewModelFactory(applicationContext as android.app.Application)
                                )
                                SettingsScreen(
                                    userPreferences = userPreferences,
                                    languageManager = languageManager,
                                    updateViewModel = updateViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToCalibration = { navController.navigate("calibration") },
                                )
                            }
                            composable("calibration") {
                                // The calibration screen reuses Coil's memory cache by
                                // requesting the same URL the home-screen live tile is
                                // already showing — that's the `streamKey` (no cache
                                // buster). Empty if the user has never loaded a frame.
                                val liveImageState by liveImageViewModel.uiState.collectAsStateWithLifecycle()
                                de.astronarren.allsky.ui.components.CalibrationScreen(
                                    userPreferences = userPreferences,
                                    liveImageUrl = liveImageState.streamKey ?: "",
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }
                            composable("about") {
                                AboutScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("focus") {
                                val focusViewModel: de.astronarren.allsky.viewmodel.FocusViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = de.astronarren.allsky.viewmodel.FocusViewModelFactory(userPreferences)
                                )
                                FocusScreen(
                                    viewModel = focusViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Warm-start path for notification deep links. Without this, tapping
     * the notification while the activity is already running would just
     * bring it to the front with no extras delivered to MainScreen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(NotificationHelper.EXTRA_SCROLL_TO)?.let {
            scrollTarget.value = it
        }
    }

    override fun onDestroy() {
        if (::languageManager.isInitialized) {
            languageManager.close()
        }
        super.onDestroy()
    }

    private fun scheduleWeatherWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val weatherWorkRequest = PeriodicWorkRequestBuilder<WeatherWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "WeatherAlerts",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            weatherWorkRequest
        )
    }
}

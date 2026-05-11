package de.astronarren.allsky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import de.astronarren.allsky.data.AllskyRepository
import de.astronarren.allsky.utils.LanguageManager
import de.astronarren.allsky.workers.WeatherWorker
import java.util.concurrent.TimeUnit

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import de.astronarren.allsky.network.AllskyAuthInterceptor
import de.astronarren.allsky.ui.layout.LayoutEditorScreen
import de.astronarren.allsky.ui.media.MediaScreen
import de.astronarren.allsky.ui.settings.SettingsScreen
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    private lateinit var weatherViewModel: WeatherViewModel
    private lateinit var allskyViewModel: AllskyViewModel
    private lateinit var imageViewerViewModel: ImageViewerViewModel
    private lateinit var setupViewModel: SetupViewModel
    private lateinit var liveImageViewModel: LiveImageViewModel
    private lateinit var languageViewModel: LanguageViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize LanguageManager and ViewModel
        val languageManager = LanguageManager(this) {
            // Recreate activity when language changes
            recreate()
        }
        languageViewModel = LanguageViewModel(languageManager)
        
        val userPreferences = UserPreferences(applicationContext)
        // Get WeatherService from provider
        val weatherService = WeatherApiProvider.provideWeatherService()
        
        val weatherRepository = WeatherRepository(
            weatherService = weatherService,
            userPreferences = userPreferences
        )
        
        val allskyRepository = AllskyRepository(userPreferences)
        
        weatherViewModel = WeatherViewModel(
            weatherRepository = weatherRepository,
            userPreferences = userPreferences
        )
        allskyViewModel = AllskyViewModel(allskyRepository, userPreferences)
        imageViewerViewModel = ImageViewerViewModel()
        setupViewModel = SetupViewModel(userPreferences)
        liveImageViewModel = LiveImageViewModel(userPreferences)
        
        // Shared OkHttp client carrying the Basic Auth interceptor so every
        // Coil request to the configured Allsky host is authenticated, with or
        // without `user:pass@` embedded in the URL.
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AllskyAuthInterceptor(userPreferences))
            .build()

        val imageLoader = ImageLoader.Builder(applicationContext)
            .okHttpClient(okHttpClient)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
        Coil.setImageLoader(imageLoader)

        scheduleWeatherWorker()
        
        enableEdgeToEdge()
        setContent {
            AllskyTheme {
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
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("about") {
                                AboutScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
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

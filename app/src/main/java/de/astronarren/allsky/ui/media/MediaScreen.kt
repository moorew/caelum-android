package de.astronarren.allsky.ui.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.astronarren.allsky.ui.components.AppBackground
import de.astronarren.allsky.ui.components.GlassCard
import de.astronarren.allsky.ui.theme.DeepNavy
import de.astronarren.allsky.ui.theme.NightPurple
import de.astronarren.allsky.viewmodel.AllskyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    title: String,
    mediaType: String,
    viewModel: AllskyViewModel,
    userPreferences: de.astronarren.allsky.data.UserPreferences,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dateInput by remember { mutableStateOf("All") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    var currentVideo by remember { mutableStateOf<String?>(null) }
    var currentImage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val mediaItems = when (mediaType) {
        "timelapses" -> uiState.timelapses
        "keograms" -> uiState.keograms
        "startrails" -> uiState.startrails
        "meteors" -> uiState.meteors
        "images" -> uiState.images
        else -> emptyList()
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                        dateInput = sdf.format(Date(millis))
                        viewModel.fetchContentForDate(dateInput)
                    }
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Select Date",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            viewModel.fetchContentForDate(dateInput)
                            delay(1000)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // Date filter chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilterChip(
                                selected = dateInput != "All",
                                onClick = { showDatePicker = true },
                                label = {
                                    Text(
                                        if (dateInput == "All") "ALL DATES"
                                        else try {
                                            val inputSdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                                            val displaySdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                            displaySdf.format(inputSdf.parse(dateInput)!!).uppercase()
                                        } catch (e: Exception) {
                                            dateInput
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.White.copy(alpha = 0.06f),
                                    labelColor = Color.White.copy(alpha = 0.85f),
                                    iconColor = Color.White.copy(alpha = 0.7f),
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            if (dateInput != "All") {
                                AssistChip(
                                    onClick = {
                                        dateInput = "All"
                                        viewModel.fetchContentForDate("All")
                                    },
                                    label = {
                                        Text(
                                            "CLEAR",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.04f),
                                        labelColor = Color.White.copy(alpha = 0.8f),
                                        leadingIconContentColor = Color.White.copy(alpha = 0.7f),
                                        trailingIconContentColor = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }

                        AnimatedContent(
                            targetState = uiState.isLoading to mediaItems.isEmpty(),
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                            label = "MediaContentAnimation"
                        ) { (isLoading, isEmpty) ->
                            when {
                                isLoading && mediaItems.isEmpty() -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                uiState.error != null && mediaItems.isEmpty() -> {
                                    EmptyState(
                                        icon = Icons.Default.WarningAmber,
                                        title = "Couldn't load",
                                        subtitle = uiState.error ?: "Unknown error"
                                    )
                                }
                                isEmpty -> {
                                    EmptyState(
                                        icon = Icons.Default.Image,
                                        title = "Nothing here yet",
                                        subtitle = "No content available for this date."
                                    )
                                }
                                else -> {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 160.dp),
                                        contentPadding = PaddingValues(
                                            start = 16.dp, end = 16.dp,
                                            top = 8.dp, bottom = 32.dp
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(mediaItems) { item ->
                                            val isVideo = item.url.lowercase().run {
                                                contains(".mp4") || contains(".webm") ||
                                                contains(".mov") || contains(".mkv")
                                            }
                                            MediaTile(
                                                url = item.url,
                                                date = item.date,
                                                isVideo = isVideo,
                                                mediaType = mediaType,
                                                onClick = {
                                                    if (isVideo) currentVideo = item.url
                                                    else currentImage = item.url
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (currentImage != null) {
                    de.astronarren.allsky.ui.components.FullScreenImageViewer(
                        imageUrl = currentImage!!,
                        userPreferences = userPreferences,
                        onDismiss = { currentImage = null }
                    )
                }

                if (currentVideo != null) {
                    de.astronarren.allsky.ui.components.VideoPlayer(
                        videoUrl = currentVideo!!,
                        userPreferences = userPreferences,
                        onDismiss = { currentVideo = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    url: String,
    date: String,
    isVideo: Boolean,
    mediaType: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        val placeholderGradient = Brush.verticalGradient(colors = listOf(DeepNavy, NightPurple))
        val placeholderPainter = when (mediaType) {
            "timelapses" -> androidx.compose.ui.res.painterResource(id = de.astronarren.allsky.R.drawable.timelapses_thumbnail)
            "images" -> androidx.compose.ui.res.painterResource(id = de.astronarren.allsky.R.drawable.raw_images_thumbnail)
            "startrails" -> androidx.compose.ui.res.painterResource(id = de.astronarren.allsky.R.drawable.startrails_thumbnail)
            "meteors" -> androidx.compose.ui.res.painterResource(id = de.astronarren.allsky.R.drawable.meteors_thumbnail)
            else -> androidx.compose.ui.graphics.vector.rememberVectorPainter(
                if (isVideo) Icons.Default.PlayCircle else Icons.Default.Image
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(placeholderGradient)) {
            AsyncImage(
                model = url,
                contentDescription = date,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = placeholderPainter,
                error = placeholderPainter
            )

            // Bottom gradient for legibility — drawn once, no nested cards.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        startY = size.height * 0.45f
                    )
                )
            }

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                )
                Text(
                    text = if (isVideo) "VIDEO" else "IMAGE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}

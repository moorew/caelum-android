package de.astronarren.allsky.ui.components

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import de.astronarren.allsky.utils.DownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import de.astronarren.allsky.network.AllskyAuth

private enum class VideoStatus(val label: String, val icon: ImageVector, val tint: Color) {
    BUFFERING("BUFFERING", Icons.Default.Downloading, Color(0xFFB3E5FC)),
    READY("PLAYING", Icons.Default.PlayArrow, Color(0xFFB9F6CA)),
    DOWNLOADING("DOWNLOADING", Icons.Default.Downloading, Color(0xFFFFE082)),
    SAVED("SAVED TO GALLERY", Icons.Default.Check, Color(0xFFB9F6CA)),
    ERROR("PLAYBACK ERROR", Icons.Default.ErrorOutline, Color(0xFFFFAB91)),
}

private data class VideoAuthState(
    val isLoaded: Boolean,
    val header: String?,
)

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun VideoPlayer(
    videoUrl: String,
    userPreferences: de.astronarren.allsky.data.UserPreferences,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadHelper = remember { DownloadHelper(context, userPreferences) }
    val scope = rememberCoroutineScope()

    var playbackStatus by remember { mutableStateOf(VideoStatus.BUFFERING) }
    var overrideStatus by remember { mutableStateOf<VideoStatus?>(null) }
    val status = overrideStatus ?: playbackStatus

    LaunchedEffect(overrideStatus) {
        if (overrideStatus == VideoStatus.SAVED) {
            delay(2500)
            overrideStatus = null
        }
    }

    val (cleanUrl, urlAuth) = remember(videoUrl) { AllskyAuth.extractAuth(videoUrl) }
    var authState by remember(cleanUrl, urlAuth, userPreferences) {
        mutableStateOf(VideoAuthState(isLoaded = urlAuth != null, header = urlAuth))
    }
    LaunchedEffect(cleanUrl, urlAuth, userPreferences) {
        authState = if (urlAuth != null) {
            VideoAuthState(isLoaded = true, header = urlAuth)
        } else {
            val header = withContext(Dispatchers.IO) {
                AllskyAuth.storedAuthHeaderForUrl(cleanUrl, userPreferences)
            }
            VideoAuthState(isLoaded = true, header = header)
        }
    }

    val fileName = remember(videoUrl) {
        val lastPathSegment = videoUrl.substringAfterLast("/").substringBefore("?")
        if (lastPathSegment.endsWith(".mp4") || lastPathSegment.endsWith(".webm") ||
            lastPathSegment.endsWith(".mov") || lastPathSegment.endsWith(".mkv")) {
            lastPathSegment
        } else {
            "allsky_video_${System.currentTimeMillis()}.mp4"
        }
    }

    if (!authState.isLoaded) {
        BackHandler(enabled = true) { onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
        }
        return
    }

    val exoPlayer = remember(videoUrl, cleanUrl, authState.header) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                32000, // min buffer
                64000, // max buffer
                2000,  // buffer for playback
                5000   // buffer for playback after rebuffer
            ).build()

        // Strip any `user:pass@` embedded in the URL by AllskyRepository and
        // promote it to a request header. Falls back to stored credentials if
        // the URL was clean — most Allsky installs sit behind Basic Auth and
        // ExoPlayer otherwise drops userinfo silently, returning HTTP 401.
        val auth = authState.header

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Allsky-Companion/ExoPlayer")
        if (auth != null) {
            httpFactory.setDefaultRequestProperties(mapOf("Authorization" to auth))
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                val mediaItem = MediaItem.fromUri(cleanUrl)

                val mediaSource = if (cleanUrl.lowercase().endsWith(".m3u8")) {
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                } else {
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                }

                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        // Drive the status pill from the ExoPlayer state callbacks so the user
        // can tell at a glance whether the rebuffer dot is the network being
        // slow or the host returning 401/404.
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackStatus = when (state) {
                    Player.STATE_BUFFERING -> VideoStatus.BUFFERING
                    Player.STATE_READY -> VideoStatus.READY
                    Player.STATE_ENDED -> VideoStatus.READY
                    Player.STATE_IDLE -> VideoStatus.BUFFERING
                    else -> playbackStatus
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackStatus = VideoStatus.ERROR
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Block click-through to the (hidden) parent chrome. */ }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Same top chrome shape as the image viewer — round close on the left,
        // round download on the right, status + filename centred above the
        // ExoPlayer controls.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.weight(1f))
                CircleButton(
                    icon = Icons.Default.Download,
                    contentDescription = "Download",
                    onClick = {
                        scope.launch {
                            overrideStatus = VideoStatus.DOWNLOADING
                            downloadHelper.downloadMedia(videoUrl, fileName, isVideo = true)
                            overrideStatus = VideoStatus.SAVED
                        }
                    }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusPill(status)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: VideoStatus) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, status.tint.copy(alpha = 0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = status.icon,
                contentDescription = null,
                tint = status.tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                ),
                color = status.tint
            )
        }
    }
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

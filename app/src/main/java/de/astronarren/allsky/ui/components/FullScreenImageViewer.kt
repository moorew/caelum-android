package de.astronarren.allsky.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.astronarren.allsky.utils.DownloadHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Possible states for the small status pill shown at the top centre. The viewer
 * always shows *some* status so the user has feedback while the image streams
 * in, while a download is in progress, and so on.
 */
private enum class ViewerStatus(val label: String, val icon: ImageVector, val tint: Color) {
    LOADING("LOADING", Icons.Default.Downloading, Color(0xFFB3E5FC)),
    READY("READY", Icons.Default.Check, Color(0xFFB9F6CA)),
    DOWNLOADING("DOWNLOADING", Icons.Default.Downloading, Color(0xFFFFE082)),
    SAVED("SAVED TO GALLERY", Icons.Default.Check, Color(0xFFB9F6CA)),
    ERROR("LOAD FAILED", Icons.Default.ErrorOutline, Color(0xFFFFAB91)),
}

@Composable
fun FullScreenImageViewer(
    imageUrl: String,
    userPreferences: de.astronarren.allsky.data.UserPreferences,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadHelper = remember { DownloadHelper(context, userPreferences) }
    val scope = rememberCoroutineScope()

    val fileName = remember(imageUrl) {
        val lastPathSegment = imageUrl.substringAfterLast("/").substringBefore("?")
        if (lastPathSegment.endsWith(".jpg") || lastPathSegment.endsWith(".png") || lastPathSegment.endsWith(".jpeg")) {
            lastPathSegment
        } else {
            "allsky_img_${System.currentTimeMillis()}.jpg"
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Image load lifecycle drives the status pill; the user-initiated download
    // can temporarily override it (DOWNLOADING / SAVED), which then auto-clears
    // after a few seconds so the pill returns to READY.
    var loadStatus by remember { mutableStateOf(ViewerStatus.LOADING) }
    var overrideStatus by remember { mutableStateOf<ViewerStatus?>(null) }
    val status = overrideStatus ?: loadStatus

    LaunchedEffect(overrideStatus) {
        if (overrideStatus == ViewerStatus.SAVED) {
            delay(2500)
            overrideStatus = null
        }
    }

    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume clicks so the underlying TopAppBar (which is
                  already hidden by MainScreen, but defence in depth) can't be
                  hit through the viewer. */ }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .listener(
                    onStart = { loadStatus = ViewerStatus.LOADING },
                    onSuccess = { _, _ -> loadStatus = ViewerStatus.READY },
                    onError = { _, _ -> loadStatus = ViewerStatus.ERROR }
                )
                .build(),
            contentDescription = "Full screen image",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            val maxX = size.width * (scale - 1) / 2
                            val maxY = size.height * (scale - 1) / 2
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 3f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        onTap = {
                            if (scale <= 1.1f) onDismiss()
                        }
                    )
                },
            contentScale = ContentScale.Fit
        )

        if (loadStatus == ViewerStatus.LOADING) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = Color.White.copy(alpha = 0.6f),
                strokeWidth = 3.dp
            )
        }

        // Top chrome.
        //
        // Layout decisions:
        //   * Status bar inset + 8 dp gives clear space above status pill / X,
        //     so the close button is never tucked under a notch or the (now
        //     hidden) burger menu.
        //   * Filename is centred and width-capped — no longer collides with
        //     the X on the left or the download button on the right.
        //   * The download IconButton is replaced with a labelled pill while
        //     a download is in progress so it's obvious *what* is happening.
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
                            overrideStatus = ViewerStatus.DOWNLOADING
                            downloadHelper.downloadMedia(imageUrl, fileName, isVideo = false)
                            overrideStatus = ViewerStatus.SAVED
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
private fun StatusPill(status: ViewerStatus) {
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

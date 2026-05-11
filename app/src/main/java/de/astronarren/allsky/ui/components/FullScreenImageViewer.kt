package de.astronarren.allsky.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.astronarren.allsky.utils.DownloadHelper
import kotlinx.coroutines.launch

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
    
    // Handle back button press
    BackHandler(enabled = true) {
        onDismiss()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume clicks to prevent background interaction */ }
    ) {
        AsyncImage(
            model = imageUrl,
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
        
        // Top chrome: floating pill bar with close, filename, download.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onDismiss,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                onClick = {
                    scope.launch {
                        downloadHelper.downloadMedia(imageUrl, fileName, isVideo = false)
                    }
                },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

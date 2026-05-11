package de.astronarren.allsky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.astronarren.allsky.viewmodel.AllskyMediaUiState
import de.astronarren.allsky.R

import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import de.astronarren.allsky.ui.theme.*

@Composable
fun AllskyMediaSection(
    title: String,
    media: List<AllskyMediaUiState>,
    onMediaClick: (AllskyMediaUiState) -> Unit,
    isVideo: Boolean? = null,
    isLoading: Boolean = false,
    error: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontSize = 13.sp
                ),
                color = Color.White.copy(alpha = 0.9f)
            )

            if (media.isNotEmpty()) {
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${media.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (media.isEmpty()) {
            // Differentiate three empty states so the user knows whether the
            // app is still working on it, has hit an error, or has confirmed
            // there's genuinely nothing for this category yet.
            val label: String
            val accent: Color
            when {
                isLoading -> {
                    label = "LOADING…"
                    accent = Color.White.copy(alpha = 0.55f)
                }
                error != null -> {
                    val short = error.takeIf { it.length < 60 }?.uppercase() ?: "COULDN'T LOAD"
                    label = "$short — PULL TO REFRESH"
                    accent = Color(0xFFFFAB91)
                }
                else -> {
                    label = stringResource(R.string.no_content_available).uppercase()
                    accent = Color.White.copy(alpha = 0.4f)
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.04f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, Color.White.copy(alpha = 0.08f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = accent
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                items(media) { item ->
                    val isItemVideo = isVideo ?: (item.url.lowercase().contains(".mp4") || 
                                              item.url.lowercase().contains(".webm") || 
                                              item.url.lowercase().contains(".mov") || 
                                              item.url.lowercase().contains(".mkv"))
                                              
                    val isMeteor = title.contains("Meteor", ignoreCase = true)
                    val isStartrail = title.contains("Startrail", ignoreCase = true)
                    val isRaw = title.contains("Raw Images", ignoreCase = true)
                    val isTimelapse = title.contains("Timelapse", ignoreCase = true)

                    val fallbackResId = when {
                        isMeteor -> de.astronarren.allsky.R.drawable.meteors_thumbnail
                        isStartrail -> de.astronarren.allsky.R.drawable.startrails_thumbnail
                        isTimelapse -> de.astronarren.allsky.R.drawable.timelapses_thumbnail
                        isRaw -> de.astronarren.allsky.R.drawable.raw_images_thumbnail
                        else -> de.astronarren.allsky.R.drawable.raw_images_thumbnail // Generic fallback
                    }
                    
                    MediaCard(
                        media = item,
                        onClick = { onMediaClick(item) },
                        isVideo = isItemVideo,
                        isMeteor = isMeteor,
                        fallbackResId = fallbackResId
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    media: AllskyMediaUiState,
    onClick: () -> Unit,
    isVideo: Boolean = false,
    isMeteor: Boolean = false,
    fallbackResId: Int? = null
) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .height(180.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = 0.08f)
        )
    ) {
        val placeholderGradient = Brush.verticalGradient(
            colors = listOf(DeepNavy, NightPurple)
        )
        
        Box(modifier = Modifier.fillMaxSize().background(placeholderGradient)) {
            val fallbackPainter = if (fallbackResId != null) {
                androidx.compose.ui.res.painterResource(id = fallbackResId)
            } else {
                rememberVectorPainter(if (isVideo || isMeteor) Icons.Default.PlayCircle else Icons.Default.Image)
            }
            AsyncImage(
                model = media.url,
                contentDescription = stringResource(R.string.media_from_date, media.date),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = fallbackPainter,
                error = fallbackPainter
            )
            
            // Gradient Overlay for readability
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = size.height * 0.4f
                    )
                )
            }

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = media.date,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isMeteor -> "METEOR"
                        isVideo -> "TIMELAPSE"
                        else -> "ARCHIVE"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    ),
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

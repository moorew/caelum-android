package de.astronarren.allsky.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.astronarren.allsky.BuildConfig
import de.astronarren.allsky.ui.components.AppBackground
import de.astronarren.allsky.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "ABOUT",
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
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ALLSKY",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 8.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "COMPANION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    ),
                    color = Color.White.copy(alpha = 0.55f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "V ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Author card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28.dp,
                    elevated = true
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DEVELOPED BY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Will Moore",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        LinkRow(
                            icon = Icons.Default.Code,
                            label = "github.com/moorew",
                            onClick = { uriHandler.openUri("https://github.com/moorew") }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinkRow(
                            icon = Icons.Default.Cloud,
                            label = "bsky.app/@clevercode",
                            onClick = { uriHandler.openUri("https://bsky.app/profile/clevercode.ca") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Experience your Allsky camera station like never before. A modern, high-performance interface for real-time monitoring, historical analysis, and precision weather data — built for the night sky.",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "ADVANCED CAPABILITIES",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    ),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        AboutFeatureItem(Icons.Outlined.Explore, "Intelligent Scraping", "Robust parsing of Allsky Portal galleries.")
                        AboutFeatureItem(Icons.Outlined.Brightness3, "Observation Planning", "Automated best-viewing night detection.")
                        AboutFeatureItem(Icons.Outlined.ViewQuilt, "Modular Layout", "Reorder and toggle home-screen modules.")
                        AboutFeatureItem(Icons.Outlined.History, "Media Explorer", "Calendar-driven discovery of every capture.")
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = "ACKNOWLEDGEMENTS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = Color.White.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This project honours the original concept and logic established by Thomas Jacquin and the AllskyTeam community.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.None
            ),
            color = Color.White
        )
    }
}

@Composable
private fun AboutFeatureItem(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}


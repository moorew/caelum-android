package de.astronarren.allsky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterHdr
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storm
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.astronarren.allsky.ui.theme.DeepNavy
import de.astronarren.allsky.ui.theme.NightPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    if (!isOpen) return

    ModalDrawerSheet(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxHeight(),
        drawerContainerColor = Color.Transparent,
        drawerContentColor = Color.White,
        windowInsets = WindowInsets(0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(DeepNavy, NightPurple)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "MENU",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                ),
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Text(
                text = "Allsky",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp, bottom = 24.dp)
            )

            Section("Navigation") {
                PanelItem("Home", Icons.Default.Home) { onNavigate("home") }
                PanelItem("Layout Editor", Icons.Default.ViewModule) { onNavigate("layout_editor") }
            }

            Section("Media") {
                PanelItem("Timelapses", Icons.Default.Videocam) { onNavigate("media/timelapses") }
                PanelItem("Keograms", Icons.Default.FilterHdr) { onNavigate("media/keograms") }
                PanelItem("Startrails", Icons.Default.Star) { onNavigate("media/startrails") }
                PanelItem("Meteors", Icons.Default.Storm) { onNavigate("media/meteors") }
                PanelItem("Raw Images", Icons.Default.Image) { onNavigate("media/images") }
            }

            Section("System") {
                PanelItem("Settings", Icons.Default.Settings) { onNavigate("settings") }
                PanelItem("About", Icons.Default.Info) { onNavigate("about") }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        ),
        color = Color.White.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp)
    )
    Column(content = content)
}

@Composable
private fun PanelItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

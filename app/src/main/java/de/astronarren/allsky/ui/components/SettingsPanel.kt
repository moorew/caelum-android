package de.astronarren.allsky.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterHdr
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storm
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DarkMode
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    // Sky-alert toggle — defaulted so existing call sites compile, but the
    // main screen wires this to the persisted user preference.
    skyAlertsEnabled: Boolean = false,
    onSkyAlertsToggle: (Boolean) -> Unit = {},
    // Long-press handler on the alerts row — fires a sample notification so
    // users can validate the deep-link + highlight without waiting for the
    // weather to actually change.
    onSkyAlertsTestFire: () -> Unit = {},
    // Red-Light night-vision mode — re-themes the whole app and tints imagery.
    redLightEnabled: Boolean = false,
    onRedLightToggle: (Boolean) -> Unit = {},
) {
    if (!isOpen) return

    val c = de.astronarren.allsky.ui.theme.LocalCaelum.current

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
                        colors = listOf(c.field, c.field2)
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
                text = "Caelum",
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

            Section("Extras") {
                // Optional focus-motor screen. The screen itself is the only
                // place where the feature can be enabled, so showing the
                // drawer item always (rather than gating on enabled) lets
                // first-time users discover it.
                PanelItem("Focus Motor", Icons.Default.CenterFocusStrong) { onNavigate("focus") }
            }

            Section("Alerts") {
                // Single switch — off by default. The actual transition
                // detection lives in WeatherWorker; this just flips the
                // preference flag the worker reads each 3-hour run.
                ToggleItem(
                    title = "Sky alerts",
                    subtitle = if (skyAlertsEnabled)
                        "On — you'll be notified when tonight's rating improves. Long-press to send a test."
                    else
                        "Off — no push notifications about viewing conditions.",
                    icon = Icons.Default.NotificationsActive,
                    checked = skyAlertsEnabled,
                    onToggle = onSkyAlertsToggle,
                    onLongPress = onSkyAlertsTestFire,
                )
            }

            Section("Display") {
                ToggleItem(
                    title = "Red-Light mode",
                    subtitle = if (redLightEnabled)
                        "On — night-vision palette; imagery tinted red to preserve dark adaptation."
                    else
                        "Off — Deep Observatory palette.",
                    icon = Icons.Default.DarkMode,
                    checked = redLightEnabled,
                    onToggle = onRedLightToggle,
                )
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

/**
 * Drawer row with a trailing [Switch] — used for boolean preferences that
 * the user should be able to flip without leaving the drawer. The row
 * itself is clickable as a larger tap target than the switch thumb, and
 * long-pressable for an optional secondary action (used here to fire a
 * sample sky-alert notification).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { onToggle(!checked) },
                onLongClick = onLongPress,
            )
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 2,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
        )
    }
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

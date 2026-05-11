package de.astronarren.allsky.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.ui.components.AppBackground
import de.astronarren.allsky.ui.components.GlassCard
import kotlinx.coroutines.launch

val ALL_MODULES = listOf(
    "LIVE_VIEW",
    "BEST_VIEWING",
    "WEATHER",
    "MOON",
    "TIMELAPSES",
    "METEORS",
    "IMAGES",
    "KEOGRAMS",
    "STARTRAILS"
)

private fun getModuleLabel(key: String): String = when (key) {
    "LIVE_VIEW" -> "Live View"
    "BEST_VIEWING" -> "Best Viewing Night"
    "WEATHER" -> "Weather Forecast"
    "MOON" -> "Moon Phase"
    "TIMELAPSES" -> "Timelapses"
    "METEORS" -> "Meteor Recordings"
    "IMAGES" -> "Raw Images"
    "KEOGRAMS" -> "Keograms"
    "STARTRAILS" -> "Startrails"
    else -> key
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    userPreferences: UserPreferences,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentLayout by remember { mutableStateOf<List<String>>(emptyList()) }

    val fullList = remember(currentLayout) {
        val list = currentLayout.toMutableList()
        ALL_MODULES.forEach { if (!list.contains(it)) list.add(it) }
        list
    }

    LaunchedEffect(Unit) {
        currentLayout = userPreferences.getMainLayout()
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "LAYOUT EDITOR",
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
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "TOGGLE & REORDER SECTIONS ON YOUR HOME SCREEN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(fullList) { _, module ->
                        val isVisible = currentLayout.contains(module)
                        val activeIndex = currentLayout.indexOf(module)
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            elevated = isVisible
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = if (isVisible) 0.5f else 0.2f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Checkbox(
                                    checked = isVisible,
                                    onCheckedChange = { checked ->
                                        val newLayout = currentLayout.toMutableList()
                                        if (checked) {
                                            if (!newLayout.contains(module)) {
                                                val canonicalIndex = ALL_MODULES.indexOf(module)
                                                val insertAt = (canonicalIndex - 1 downTo 0)
                                                    .firstNotNullOfOrNull { i ->
                                                        val pos = newLayout.indexOf(ALL_MODULES[i])
                                                        if (pos >= 0) pos + 1 else null
                                                    } ?: 0
                                                newLayout.add(insertAt.coerceAtMost(newLayout.size), module)
                                            }
                                        } else {
                                            newLayout.remove(module)
                                        }
                                        currentLayout = newLayout
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = Color.White.copy(alpha = 0.4f)
                                    )
                                )
                                Text(
                                    text = getModuleLabel(module),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = if (isVisible) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.weight(1f)
                                )

                                if (isVisible) {
                                    IconButton(
                                        onClick = {
                                            if (activeIndex > 0) {
                                                val newLayout = currentLayout.toMutableList()
                                                val temp = newLayout[activeIndex - 1]
                                                newLayout[activeIndex - 1] = newLayout[activeIndex]
                                                newLayout[activeIndex] = temp
                                                currentLayout = newLayout
                                            }
                                        },
                                        enabled = activeIndex > 0
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = "Up",
                                            tint = if (activeIndex > 0) Color.White.copy(alpha = 0.85f)
                                                   else Color.White.copy(alpha = 0.2f)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (activeIndex < currentLayout.size - 1) {
                                                val newLayout = currentLayout.toMutableList()
                                                val temp = newLayout[activeIndex + 1]
                                                newLayout[activeIndex + 1] = newLayout[activeIndex]
                                                newLayout[activeIndex] = temp
                                                currentLayout = newLayout
                                            }
                                        },
                                        enabled = activeIndex < currentLayout.size - 1 && activeIndex != -1
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            contentDescription = "Down",
                                            tint = if (activeIndex < currentLayout.size - 1)
                                                       Color.White.copy(alpha = 0.85f)
                                                   else Color.White.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentLayout = ALL_MODULES },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(
                            "RESET",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                userPreferences.saveMainLayout(currentLayout)
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text(
                            "SAVE",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

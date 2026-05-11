package de.astronarren.allsky.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.ui.components.AppBackground
import de.astronarren.allsky.ui.components.GlassCard
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val BASE_MODULES = listOf(
    "LIVE_VIEW",
    "BEST_VIEWING",
    "WEATHER",
    "TONIGHT",
    "MOON",
    "TIMELAPSES",
    "METEORS",
    "IMAGES",
    "KEOGRAMS",
    "STARTRAILS"
)

/**
 * Returns the modules visible in the editor for the current feature flags.
 * FOCUS only shows up once the user has enabled the focus motor feature —
 * the editor would otherwise dangle a dead row for everyone else.
 */
private fun allModules(focusEnabled: Boolean): List<String> =
    if (focusEnabled) BASE_MODULES + "FOCUS" else BASE_MODULES

private fun getModuleLabel(key: String): String = when (key) {
    "LIVE_VIEW" -> "Live View"
    "BEST_VIEWING" -> "Best Viewing Night"
    "WEATHER" -> "Weather Forecast"
    "TONIGHT" -> "Tonight"
    "MOON" -> "Moon Phase"
    "TIMELAPSES" -> "Timelapses"
    "METEORS" -> "Meteor Recordings"
    "IMAGES" -> "Raw Images"
    "KEOGRAMS" -> "Keograms"
    "STARTRAILS" -> "Startrails"
    "FOCUS" -> "Focus Control"
    else -> key
}

/**
 * The layout editor splits modules into two sections — the home-screen list
 * (drag-to-reorder, long-press the handle) and the off list (tap to add).
 * Reorder operates only on the on-list, which keeps the swap logic simple
 * and the affordance honest: you can't drag what isn't on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    userPreferences: UserPreferences,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Focus availability gates whether FOCUS appears in the available list.
    // If the user disables focus after adding it to their layout, the saved
    // FOCUS entry stays in `onList` (the editor doesn't strip it) — it just
    // won't render anything on the home screen. That's the cheap fix; a
    // future polish could auto-prune on save.
    val focusEnabled by produceState(initialValue = false, userPreferences) {
        userPreferences.getFocusSettingsFlow().collect { value = it.enabled }
    }

    var onList by remember { mutableStateOf<List<String>>(emptyList()) }
    val offList by remember(focusEnabled) {
        derivedStateOf { allModules(focusEnabled).filterNot { it in onList } }
    }

    LaunchedEffect(Unit) {
        onList = userPreferences.getMainLayout()
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // The reorderable library hands us indexes into the LazyColumn's full
        // item list. We have one header item before the on-list, so subtract
        // 1 to map back into our pure-data list. Off-list items live below
        // their own header and are not draggable, so we never see them here.
        val fromIdx = from.index - 1
        val toIdx = to.index - 1
        if (fromIdx in onList.indices && toIdx in onList.indices) {
            onList = onList.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
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
                    text = "DRAG TO REORDER · TAP TO ADD OR REMOVE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Section header: ON HOME SCREEN
                    item(key = "header_on") {
                        SectionHeader(
                            title = "ON HOME SCREEN",
                            count = onList.size
                        )
                    }

                    // The on-list — reorderable. Keyed by the module string so
                    // Compose can animate items across the swap.
                    items(items = onList, key = { it }) { module ->
                        ReorderableItem(reorderState, key = module) { _ ->
                            OnRow(
                                module = module,
                                onRemove = { onList = onList - module },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                )
                            )
                        }
                    }

                    // Section header: AVAILABLE — only shown when there's
                    // something to add. Saves a row of empty header noise
                    // once the user has everything turned on.
                    if (offList.isNotEmpty()) {
                        item(key = "header_off") {
                            SectionHeader(
                                title = "AVAILABLE",
                                count = offList.size,
                                topPadding = 12.dp
                            )
                        }
                        items(items = offList, key = { "off_$it" }) { module ->
                            OffRow(
                                module = module,
                                onAdd = { onList = onList + module }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onList = allModules(focusEnabled) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color.White.copy(alpha = 0.3f)
                        ),
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
                                userPreferences.saveMainLayout(onList)
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
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

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            ),
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White.copy(alpha = 0.35f)
        )
    }
}

/** Active home-screen row: drag handle, label, remove. */
@Composable
private fun OnRow(
    module: String,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        elevated = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag grip — long-press anywhere in this 40dp hit-target to
            // start a drag. The grip carries the only drag-start gesture,
            // so taps elsewhere on the row never accidentally initiate a
            // reorder (which would conflict with the remove button below).
            Box(
                modifier = dragHandleModifier
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = getModuleLabel(module),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRemove) {
                Text(
                    text = "REMOVE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    ),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** Available (off-list) row: dim, with a single big ADD affordance. */
@Composable
private fun OffRow(
    module: String,
    onAdd: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        elevated = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // No drag handle here — the row isn't reorderable until added.
            Spacer(modifier = Modifier.width(44.dp))
            Text(
                text = getModuleLabel(module),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to home screen",
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

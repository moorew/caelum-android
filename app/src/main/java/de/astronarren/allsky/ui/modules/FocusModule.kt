package de.astronarren.allsky.ui.modules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.astronarren.allsky.data.FocusController
import de.astronarren.allsky.ui.components.GlassCard
import de.astronarren.allsky.viewmodel.ConnectionStatus
import de.astronarren.allsky.viewmodel.FocusViewModel

/**
 * Compact home-screen counterpart to the full Focus screen's JogCard.
 *
 * Only renders when the rig is actually reachable — `settings.enabled` plus a
 * successful most-recent connection probe. The probe is run once by
 * [FocusViewModel.init] on screen open; we don't re-probe per recomposition.
 * When the rig is unreachable the whole card collapses to zero height via
 * AnimatedVisibility so the home layout doesn't reserve a hole for it.
 *
 * "OPEN" in the top-right jumps to the full Focus screen for settings or for
 * the wider preset list. Kept deliberately tight here — 4 presets, 2 jog
 * buttons, one feedback pill — because anything more starts to compete with
 * the home screen's other modules for thumb real estate.
 */
private val COMPACT_PRESET_STEPS = listOf(64, 256, 1024, 4096)

@Composable
fun FocusModule(
    viewModel: FocusViewModel,
    onOpenFocusScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible = state.settings.enabled && state.connectionStatus is ConnectionStatus.Connected

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevated = true
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header row: title chip + OPEN affordance.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "FOCUS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp
                            ),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Tiny green dot mirrors the connected pill on the
                        // Focus settings screen so the relationship is
                        // visually obvious without spelling it out.
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF66BB6A))
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = onOpenFocusScreen,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "OPEN",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp
                                ),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Step-size presets — same list as the full Focus screen
                    // intersected to the 4 most useful values. Tap selects;
                    // we mirror the selected-pill border style for continuity.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        COMPACT_PRESET_STEPS.forEach { preset ->
                            val selected = state.steps == preset
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setSteps(preset) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) Color.White.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color.White.copy(alpha = 0.55f)
                                    else Color.White.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    "$preset",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Two jog buttons — 56dp here vs 72dp on the Focus screen
                    // because the home card sits among other modules and
                    // shouldn't dominate. Captions match the Focus screen's
                    // BACK / FORWARD wording for muscle-memory consistency.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompactJogButton(
                            icon = Icons.Default.ArrowDownward,
                            caption = "BACK",
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.move(FocusController.Direction.BACKWARD) }
                        )
                        CompactJogButton(
                            icon = Icons.Default.ArrowUpward,
                            caption = "FORWARD",
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.move(FocusController.Direction.FORWARD) }
                        )
                    }

                    // Result pill: green tick on success, red error icon on
                    // failure. Auto-clears next time the user moves. Stays
                    // compact (one line) so the card height is predictable.
                    state.lastResult?.let { last ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (state.lastSuccess) Color(0xFF66BB6A).copy(alpha = 0.14f)
                                    else Color(0xFFEF5350).copy(alpha = 0.18f)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (state.lastSuccess) Icons.Default.Check
                                else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (state.lastSuccess) Color(0xFF81C784)
                                       else Color(0xFFEF9A9A),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.lastSuccess) "Move complete"
                                       else last.take(80),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactJogButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    caption: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.2f),
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            )
        ) {
            Icon(icon, contentDescription = caption, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            ),
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

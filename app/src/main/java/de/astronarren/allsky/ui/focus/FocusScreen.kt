package de.astronarren.allsky.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.astronarren.allsky.data.FocusController
import de.astronarren.allsky.data.FocusSettings
import de.astronarren.allsky.data.FocusTransport
import de.astronarren.allsky.ui.components.AppBackground
import de.astronarren.allsky.ui.components.GlassCard
import de.astronarren.allsky.viewmodel.ConnectionStatus
import de.astronarren.allsky.viewmodel.FocusViewModel

private val PRESET_STEPS = listOf(64, 128, 256, 512, 1024)

private val OkGreen = Color(0xFF00C853)
private val OkGreenSoft = Color(0xFF69F0AE)
private val WarnRedSoft = Color(0xFFFFAB91)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val uriHandler = LocalUriHandler.current

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "FOCUS MOTOR",
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
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ---------- Enable card ----------
                EnableCard(
                    enabled = s.enabled,
                    onToggle = { checked ->
                        viewModel.updateSettings { it.copy(enabled = checked) }
                        viewModel.save()
                    }
                )

                if (s.enabled) {

                    // ---------- Intro/help card (now at the top) ----------
                    IntroHelpCard(
                        onOpenPrintables = {
                            uriHandler.openUri("https://www.printables.com/article/allsky-v3af-focus-capable-allsky-VNLB02d")
                        },
                        onOpenTailscale = {
                            uriHandler.openUri("https://tailscale.com/kb/1019/install-android")
                        }
                    )

                    // ---------- Connection (collapsed chip OR full editor) ----------
                    ConnectionCard(
                        settings = s,
                        connectionStatus = state.connectionStatus,
                        editMode = state.editMode,
                        onTransportChange = { opt ->
                            viewModel.updateSettings { it.copy(transport = opt) }
                        },
                        onFieldChange = { transform ->
                            viewModel.updateSettings(transform)
                        },
                        onSaveAndTest = { viewModel.saveAndTest() },
                        onEdit = { viewModel.setEditMode(true) },
                    )

                    // ---------- Step + jog ----------
                    JogCard(
                        steps = state.steps,
                        busy = state.busy,
                        lastResult = state.lastResult,
                        lastSuccess = state.lastSuccess,
                        onSetSteps = viewModel::setSteps,
                        onMove = viewModel::move,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun EnableCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevated = enabled) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CenterFocusStrong,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ENABLE FOCUS CONTROL",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Adds a remote nudge-the-focuser screen for Allsky rigs running a focus motor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun IntroHelpCard(
    onOpenPrintables: () -> Unit,
    onOpenTailscale: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "FIRST TIME HERE?",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp
                    ),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Most Allsky rigs don't ship with a focus motor — this screen is opt-in for builds " +
                "following the v3AF guide, which covers the wiring, the focus.py script, and parts. " +
                "Once your Pi can run that script locally, fill in the connection details below.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            HelpLink(label = "OPEN PRINTABLES GUIDE", onClick = onOpenPrintables)
            Spacer(modifier = Modifier.height(8.dp))
            HelpLink(label = "TAILSCALE INSTALL (REMOTE ACCESS)", onClick = onOpenTailscale)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Tailscale runs as a system VPN — once this phone and your Pi are on the same tailnet, " +
                "put the Pi's tailnet name (e.g. allsky-pi.tail-xyz.ts.net) in the Host field.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    settings: FocusSettings,
    connectionStatus: ConnectionStatus,
    editMode: Boolean,
    onTransportChange: (FocusTransport) -> Unit,
    onFieldChange: ((FocusSettings) -> FocusSettings) -> Unit,
    onSaveAndTest: () -> Unit,
    onEdit: () -> Unit,
) {
    val connected = connectionStatus is ConnectionStatus.Connected
    // Collapse whenever we have a credible state (testing or connected) and
    // the user hasn't asked to edit. A Failed/Unknown status always expands
    // the editor so the user can fix what's broken.
    val collapsed = !editMode &&
        (connectionStatus is ConnectionStatus.Connected || connectionStatus is ConnectionStatus.Testing)

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevated = connected) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "CONNECTION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (collapsed) {
                // Single-line chip: dot/spinner + identity + EDIT button.
                // Handles both the steady Connected state and the brief
                // Testing window on screen-open.
                ConnectedChip(
                    status = connectionStatus,
                    onEdit = onEdit,
                )
            } else {
                // Transport picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FocusTransport.values().forEach { opt ->
                        val selected = settings.transport == opt
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onTransportChange(opt) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) Color.White.copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.04f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) Color.White.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.18f)
                            )
                        ) {
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (settings.transport) {
                    FocusTransport.SSH -> SshFields(settings, onFieldChange)
                    FocusTransport.HTTP -> HttpFields(settings, onFieldChange)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status pill (only when there's something to report)
                ConnectionStatusPill(connectionStatus)

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSaveAndTest,
                    enabled = connectionStatus !is ConnectionStatus.Testing,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.4f),
                        disabledContentColor = Color.Black.copy(alpha = 0.4f),
                    )
                ) {
                    Text(
                        if (connectionStatus is ConnectionStatus.Testing) "TESTING…" else "SAVE & TEST",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedChip(status: ConnectionStatus, onEdit: () -> Unit) {
    val isTesting = status is ConnectionStatus.Testing
    val tintBg = if (isTesting) Color.White.copy(alpha = 0.06f) else OkGreen.copy(alpha = 0.10f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tintBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isTesting) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.8f)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(OkGreen)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isTesting) "VERIFYING…" else "CONNECTED",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp
                ),
                color = if (isTesting) Color.White.copy(alpha = 0.7f) else OkGreenSoft
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                when (status) {
                    is ConnectionStatus.Connected -> status.detail
                    ConnectionStatus.Testing -> "Checking the rig is reachable…"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
        TextButton(onClick = onEdit, enabled = !isTesting) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = if (isTesting) Color.White.copy(alpha = 0.4f) else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "EDIT",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp
                ),
                color = if (isTesting) Color.White.copy(alpha = 0.4f) else Color.White,
            )
        }
    }
}

@Composable
private fun ConnectionStatusPill(status: ConnectionStatus) {
    when (status) {
        ConnectionStatus.Unknown -> Unit // no pill before user has tried
        ConnectionStatus.Testing -> StatusPill(
            tint = Color.White.copy(alpha = 0.15f),
            borderTint = Color.White.copy(alpha = 0.35f),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Testing connection…",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
        is ConnectionStatus.Connected -> StatusPill(
            tint = OkGreen.copy(alpha = 0.12f),
            borderTint = OkGreenSoft.copy(alpha = 0.55f),
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = OkGreenSoft,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Connected — ${status.detail}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
        is ConnectionStatus.Failed -> StatusPill(
            tint = WarnRedSoft.copy(alpha = 0.18f),
            borderTint = WarnRedSoft.copy(alpha = 0.6f),
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = WarnRedSoft,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Could not connect — ${status.reason}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun StatusPill(
    tint: Color,
    borderTint: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderTint),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun SshFields(
    s: FocusSettings,
    onFieldChange: ((FocusSettings) -> FocusSettings) -> Unit,
) {
    FocusField(
        value = s.host,
        label = "Host or Tailscale name",
        placeholder = "allsky-pi.local  or  allsky-pi.tail-xyz.ts.net",
        onChange = { v -> onFieldChange { it.copy(host = v) } }
    )
    Spacer(modifier = Modifier.height(8.dp))
    FocusField(
        value = s.port.toString(),
        label = "Port",
        keyboardType = KeyboardType.Number,
        onChange = { v -> onFieldChange { it.copy(port = v.toIntOrNull() ?: 22) } }
    )
    Spacer(modifier = Modifier.height(8.dp))
    FocusField(
        value = s.username,
        label = "SSH username",
        placeholder = "pi",
        onChange = { v -> onFieldChange { it.copy(username = v) } }
    )
    Spacer(modifier = Modifier.height(8.dp))
    FocusField(
        value = s.password,
        label = "SSH password",
        isPassword = true,
        onChange = { v -> onFieldChange { it.copy(password = v) } }
    )
    Spacer(modifier = Modifier.height(8.dp))
    FocusField(
        value = s.scriptPath,
        label = "focus.py path on the Pi",
        onChange = { v -> onFieldChange { it.copy(scriptPath = v) } }
    )
}

@Composable
private fun HttpFields(
    s: FocusSettings,
    onFieldChange: ((FocusSettings) -> FocusSettings) -> Unit,
) {
    FocusField(
        value = s.httpEndpoint,
        label = "Endpoint URL template",
        placeholder = "http://pi.local:5000/focus?dir={direction}&steps={steps}",
        onChange = { v -> onFieldChange { it.copy(httpEndpoint = v) } }
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "Placeholders {direction} (forward|backward) and {steps} are substituted before the GET request.",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.6f)
    )
}

@Composable
private fun JogCard(
    steps: Int,
    busy: Boolean,
    lastResult: String?,
    lastSuccess: Boolean,
    onSetSteps: (Int) -> Unit,
    onMove: (FocusController.Direction) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevated = true) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "STEP SIZE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PRESET_STEPS.forEach { preset ->
                    val selected = steps == preset
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSetSteps(preset) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) Color.White.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.05f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) Color.White.copy(alpha = 0.55f)
                            else Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Text(
                            "$preset",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FocusField(
                value = steps.toString(),
                label = "Custom step count",
                keyboardType = KeyboardType.Number,
                onChange = { v -> v.toIntOrNull()?.let(onSetSteps) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Arrow-only jog buttons with a tiny caption under each so the
            // arrow is unambiguous on first run. Captions live outside the
            // button so they can't wrap and bisect the label.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                JogArrowButton(
                    icon = Icons.Default.ArrowDownward,
                    caption = "BACK",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { onMove(FocusController.Direction.BACKWARD) }
                )
                JogArrowButton(
                    icon = Icons.Default.ArrowUpward,
                    caption = "FORWARD",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { onMove(FocusController.Direction.FORWARD) }
                )
            }

            if (busy) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Running $steps-step move…",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            lastResult?.let { last ->
                Spacer(modifier = Modifier.height(14.dp))
                StatusPill(
                    tint = if (lastSuccess) OkGreen.copy(alpha = 0.12f)
                           else WarnRedSoft.copy(alpha = 0.18f),
                    borderTint = if (lastSuccess) OkGreenSoft.copy(alpha = 0.55f)
                                 else WarnRedSoft.copy(alpha = 0.6f),
                ) {
                    Icon(
                        if (lastSuccess) Icons.Default.Check else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (lastSuccess) OkGreenSoft else WarnRedSoft,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lastSuccess) "Move complete — $last" else last,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Small reusable widgets
// ---------------------------------------------------------------------------

@Composable
private fun JogArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    caption: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.2f),
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            )
        ) {
            Icon(icon, contentDescription = caption, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            ),
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Color.White.copy(alpha = 0.35f)) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.04f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
            focusedBorderColor = Color.White.copy(alpha = 0.6f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Color.White.copy(alpha = 0.55f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        )
    )
}

@Composable
private fun HelpLink(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black, letterSpacing = 1.5.sp
                ),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

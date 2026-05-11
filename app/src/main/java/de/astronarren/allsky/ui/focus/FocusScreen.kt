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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.astronarren.allsky.data.FocusController
import de.astronarren.allsky.data.FocusTransport
import de.astronarren.allsky.ui.components.AppBackground
import de.astronarren.allsky.ui.components.GlassCard
import de.astronarren.allsky.viewmodel.FocusViewModel

private val PRESET_STEPS = listOf(64, 128, 256, 512, 1024)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val uriHandler = LocalUriHandler.current
    var showAdvanced by remember { mutableStateOf(s.transport == FocusTransport.HTTP) }

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
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevated = s.enabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CenterFocusStrong,
                            contentDescription = null,
                            tint = if (s.enabled) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.5f),
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
                        Switch(
                            checked = s.enabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(enabled = checked) }
                                viewModel.save()
                            }
                        )
                    }
                }

                if (s.enabled) {
                    // ---------- Transport picker ----------
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "CONNECTION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black, letterSpacing = 2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FocusTransport.values().forEach { opt ->
                                    val selected = s.transport == opt
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                viewModel.updateSettings { it.copy(transport = opt) }
                                            },
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
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            when (s.transport) {
                                FocusTransport.SSH -> {
                                    FocusField(
                                        value = s.host,
                                        label = "Host or Tailscale name",
                                        placeholder = "allsky-pi.local  or  allsky-pi.tail-xyz.ts.net",
                                        onChange = { v ->
                                            viewModel.updateSettings { it.copy(host = v) }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FocusField(
                                        value = s.port.toString(),
                                        label = "Port",
                                        keyboardType = KeyboardType.Number,
                                        onChange = { v ->
                                            viewModel.updateSettings {
                                                it.copy(port = v.toIntOrNull() ?: 22)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FocusField(
                                        value = s.username,
                                        label = "SSH username",
                                        placeholder = "pi",
                                        onChange = { v ->
                                            viewModel.updateSettings { it.copy(username = v) }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FocusField(
                                        value = s.password,
                                        label = "SSH password",
                                        isPassword = true,
                                        onChange = { v ->
                                            viewModel.updateSettings { it.copy(password = v) }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FocusField(
                                        value = s.scriptPath,
                                        label = "focus.py path on the Pi",
                                        onChange = { v ->
                                            viewModel.updateSettings { it.copy(scriptPath = v) }
                                        }
                                    )
                                }
                                FocusTransport.HTTP -> {
                                    FocusField(
                                        value = s.httpEndpoint,
                                        label = "Endpoint URL template",
                                        placeholder = "http://pi.local:5000/focus?dir={direction}&steps={steps}",
                                        onChange = { v ->
                                            viewModel.updateSettings { it.copy(httpEndpoint = v) }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Placeholders {direction} (forward|backward) and {steps} are substituted before the GET request.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.save() },
                                modifier = Modifier.align(Alignment.End),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(
                                    "SAVE",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }
                    }

                    // ---------- Step control + jog buttons ----------
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
                                    val selected = state.steps == preset
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.setSteps(preset) },
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
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            FocusField(
                                value = state.steps.toString(),
                                label = "Custom step count",
                                keyboardType = KeyboardType.Number,
                                onChange = { v ->
                                    v.toIntOrNull()?.let(viewModel::setSteps)
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                JogButton(
                                    label = "BACKWARD",
                                    icon = Icons.Default.ArrowDownward,
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.move(FocusController.Direction.BACKWARD) }
                                )
                                JogButton(
                                    label = "FORWARD",
                                    icon = Icons.Default.ArrowUpward,
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.move(FocusController.Direction.FORWARD) }
                                )
                            }

                            if (state.busy) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Running ${state.steps}-step move…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            state.lastResult?.let { last ->
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (state.lastSuccess) Color(0xFF00C853).copy(alpha = 0.12f)
                                            else Color(0xFFFFAB91).copy(alpha = 0.18f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (state.lastSuccess) Color(0xFF69F0AE).copy(alpha = 0.5f)
                                        else Color(0xFFFFAB91).copy(alpha = 0.6f)
                                    )
                                ) {
                                    Text(
                                        text = last,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ---------- Help card ----------
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
                                "Most Allsky rigs do not have a focus motor — this feature is opt-in for builds following the v3AF guide. " +
                                "The guide explains the wiring, the focus.py script and the parts list.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            HelpLink(
                                label = "OPEN PRINTABLES GUIDE",
                                onClick = { uriHandler.openUri("https://www.printables.com/article/allsky-v3af-focus-capable-allsky-VNLB02d") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HelpLink(
                                label = "TAILSCALE INSTALL (REMOTE ACCESS)",
                                onClick = { uriHandler.openUri("https://tailscale.com/kb/1019/install-android") }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Tailscale runs as a system VPN — once you've added this phone and your Pi to the same tailnet, just put the Pi's tailnet name (e.g. allsky-pi.tail-xyz.ts.net) in the Host field. No SDK or extra config in the app.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun JogButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.2f),
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
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

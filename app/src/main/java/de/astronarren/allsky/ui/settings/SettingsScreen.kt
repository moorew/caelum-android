package de.astronarren.allsky.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.astronarren.allsky.R
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.ui.components.AppBackground
import de.astronarren.allsky.ui.components.GlassCard
import de.astronarren.allsky.ui.components.LanguageSelector
import de.astronarren.allsky.utils.LanguageManager
import de.astronarren.allsky.viewmodel.UpdateUiState
import de.astronarren.allsky.viewmodel.UpdateViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    languageManager: LanguageManager,
    updateViewModel: UpdateViewModel,
    onNavigateBack: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var stationNameInput by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf(de.astronarren.allsky.utils.AppLanguage.SYSTEM) }
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        currentLanguage = languageManager.getCurrentLanguage()
        urlInput = userPreferences.getAllskyUrl()
        stationNameInput = userPreferences.getStationName()
        latInput = userPreferences.getLatitude()
        lonInput = userPreferences.getLongitude()
        usernameInput = userPreferences.getUsername()
        passwordInput = userPreferences.getPassword()
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_title).uppercase(),
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
                Spacer(modifier = Modifier.height(4.dp))

                // ---------------- Update status card ----------------
                val updatable = updateState is UpdateUiState.UpdateAvailable
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (updatable) Modifier.clickable { updateViewModel.showUpdateDialog() }
                            else Modifier
                        ),
                    cornerRadius = 24.dp,
                    elevated = updatable
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = if (updatable) MaterialTheme.colorScheme.secondary
                                   else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_status).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                ),
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (val s = updateState) {
                                    is UpdateUiState.UpdateAvailable -> stringResource(
                                        R.string.update_available_status, s.updateInfo.latestVersion
                                    )
                                    is UpdateUiState.Checking -> stringResource(R.string.checking_for_updates)
                                    is UpdateUiState.Downloading -> stringResource(R.string.downloading_update)
                                    is UpdateUiState.NoUpdate -> stringResource(R.string.up_to_date)
                                    else -> stringResource(R.string.up_to_date)
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                        if (updatable) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ) {
                                Text(
                                    text = stringResource(R.string.update_available_badge),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black)
                                )
                            }
                        }
                    }
                }

                // ---------------- Station group ----------------
                SettingsGroup(title = "Station", icon = Icons.Default.SatelliteAlt) {
                    GlassTextField(
                        value = stationNameInput,
                        onValueChange = { stationNameInput = it },
                        label = "Station Name",
                        placeholder = "e.g. Backyard Observatory"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = stringResource(R.string.allsky_url_input),
                        placeholder = "https://myallsky.local",
                        keyboardType = KeyboardType.Uri
                    )
                }

                // ---------------- Credentials group ----------------
                SettingsGroup(title = "Credentials", icon = Icons.Default.Lock) {
                    GlassTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = "Username (optional)",
                        leadingIcon = Icons.Default.Person,
                        keyboardType = KeyboardType.Email,
                        semanticsLabel = "username"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = "Password (optional)",
                        leadingIcon = Icons.Default.Lock,
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        semanticsLabel = "password"
                    )
                }

                // ---------------- Location group ----------------
                SettingsGroup(title = "Location", icon = Icons.Default.LocationOn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassTextField(
                            value = latInput,
                            onValueChange = { latInput = it },
                            label = "Latitude",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        GlassTextField(
                            value = lonInput,
                            onValueChange = { lonInput = it },
                            label = "Longitude",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ---------------- Language picker ----------------
                SettingsGroup(title = "Appearance", icon = Icons.Default.Language) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageDialog = true },
                        cornerRadius = 20.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.language_settings).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(currentLanguage.nameResId),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open language picker",
                                tint = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // ---------------- Save button ----------------
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        scope.launch {
                            userPreferences.saveStationName(stationNameInput)
                            userPreferences.saveAllskyUrl(urlInput)
                            userPreferences.saveLatitude(latInput)
                            userPreferences.saveLongitude(lonInput)
                            userPreferences.saveUsername(usernameInput)
                            userPreferences.savePassword(passwordInput)
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(
                        text = stringResource(R.string.save).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showLanguageDialog) {
        LanguageSelector(
            currentLanguage = currentLanguage,
            onLanguageSelected = { language ->
                currentLanguage = language
                languageManager.setLanguage(language)
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                ),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
        }
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp
        ) {
            Column(modifier = Modifier.padding(20.dp), content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    semanticsLabel: String? = null
) {
    val sm = if (semanticsLabel != null) {
        Modifier.semantics { contentDescription = semanticsLabel }
    } else Modifier
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(sm),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Color.White.copy(alpha = 0.35f)) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.04f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
            focusedBorderColor = Color.White.copy(alpha = 0.6f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Color.White.copy(alpha = 0.55f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLeadingIconColor = Color.White.copy(alpha = 0.85f),
            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.5f)
        )
    )
}

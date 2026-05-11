package de.astronarren.allsky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.astronarren.allsky.data.FocusController
import de.astronarren.allsky.data.FocusSettings
import de.astronarren.allsky.data.FocusTransport
import de.astronarren.allsky.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Result of the most-recent [FocusController.testConnection] call. Drives the
 * coloured pill in the UI and decides whether the credentials panel collapses
 * into a chip or stays expanded for editing.
 */
sealed class ConnectionStatus {
    /** No test has run yet (or the user changed a credential field). */
    object Unknown : ConnectionStatus()
    object Testing : ConnectionStatus()
    data class Connected(val detail: String) : ConnectionStatus()
    data class Failed(val reason: String) : ConnectionStatus()
}

data class FocusUiState(
    val settings: FocusSettings = FocusSettings(
        enabled = false,
        transport = FocusTransport.SSH,
        host = "",
        port = 22,
        username = "pi",
        password = "",
        scriptPath = "~/allsky/scripts/focus.py",
        httpEndpoint = "",
        defaultSteps = 256,
    ),
    val steps: Int = 256,
    val busy: Boolean = false,
    val lastResult: String? = null,
    val lastSuccess: Boolean = true,
    /** True when we should show the editable credential fields. */
    val editMode: Boolean = true,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
)

class FocusViewModel(
    private val userPreferences: UserPreferences,
    private val controller: FocusController = FocusController(),
) : ViewModel() {
    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = userPreferences.getFocusSettingsFlow().first()
            val canCollapse = saved.enabled && hasCredentials(saved)
            _state.update {
                it.copy(
                    settings = saved,
                    steps = saved.defaultSteps,
                    editMode = !canCollapse,
                    connectionStatus = if (canCollapse) ConnectionStatus.Testing
                                       else ConnectionStatus.Unknown,
                )
            }
            // Auto-probe on screen open so the user immediately sees whether
            // the rig is reachable today, rather than the panel reverting to
            // the full editor every time they re-enter Focus settings.
            if (canCollapse) {
                val result = controller.testConnection(saved)
                _state.update {
                    val status = if (result.success) {
                        ConnectionStatus.Connected(result.output)
                    } else {
                        ConnectionStatus.Failed(result.output)
                    }
                    it.copy(
                        connectionStatus = status,
                        editMode = !result.success,
                    )
                }
            }
        }
    }

    fun updateSettings(transform: (FocusSettings) -> FocusSettings) {
        _state.update {
            // Any credential edit invalidates the cached connection status.
            it.copy(
                settings = transform(it.settings),
                connectionStatus = ConnectionStatus.Unknown,
            )
        }
    }

    fun setSteps(value: Int) {
        _state.update { it.copy(steps = value.coerceIn(1, 8192)) }
    }

    fun setEditMode(editing: Boolean) {
        _state.update { it.copy(editMode = editing) }
    }

    /** Persist settings without running a connection test. */
    fun save(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            userPreferences.saveFocusSettings(_state.value.settings)
            onSaved()
        }
    }

    /**
     * Persist, then probe — the primary action from the settings panel.
     * On success the panel collapses into the connected chip.
     */
    fun saveAndTest() {
        viewModelScope.launch {
            userPreferences.saveFocusSettings(_state.value.settings)
            _state.update { it.copy(connectionStatus = ConnectionStatus.Testing) }
            val result = controller.testConnection(_state.value.settings)
            _state.update {
                val status = if (result.success) {
                    ConnectionStatus.Connected(result.output)
                } else {
                    ConnectionStatus.Failed(result.output)
                }
                it.copy(
                    connectionStatus = status,
                    // Auto-collapse on success, stay expanded on failure so
                    // the user can fix what they typed.
                    editMode = !result.success,
                )
            }
        }
    }

    fun move(direction: FocusController.Direction) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, lastResult = null) }
            val result = controller.move(
                settings = _state.value.settings,
                direction = direction,
                steps = _state.value.steps,
            )
            _state.update {
                it.copy(
                    busy = false,
                    lastResult = result.output,
                    lastSuccess = result.success,
                )
            }
        }
    }

    private fun hasCredentials(s: FocusSettings): Boolean = when (s.transport) {
        FocusTransport.SSH -> s.host.isNotBlank() && s.password.isNotBlank()
        FocusTransport.HTTP -> s.httpEndpoint.isNotBlank()
    }
}

class FocusViewModelFactory(
    private val userPreferences: UserPreferences,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FocusViewModel(userPreferences) as T
    }
}

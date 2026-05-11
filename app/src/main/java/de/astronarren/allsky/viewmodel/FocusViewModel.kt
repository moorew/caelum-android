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
            _state.update { it.copy(settings = saved, steps = saved.defaultSteps) }
        }
    }

    fun updateSettings(transform: (FocusSettings) -> FocusSettings) {
        _state.update { it.copy(settings = transform(it.settings)) }
    }

    fun setSteps(value: Int) {
        _state.update { it.copy(steps = value.coerceIn(1, 8192)) }
    }

    fun save(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            userPreferences.saveFocusSettings(_state.value.settings)
            onSaved()
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
}

class FocusViewModelFactory(
    private val userPreferences: UserPreferences,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FocusViewModel(userPreferences) as T
    }
}

package de.astronarren.allsky.viewmodel

import androidx.lifecycle.ViewModel
import de.astronarren.allsky.ui.state.ImageViewerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ImageViewerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ImageViewerUiState())
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    fun showImage(url: String, thumbUrl: String? = null) {
        _uiState.update { currentState ->
            currentState.copy(isFullScreen = true, currentImageUrl = url, currentThumbUrl = thumbUrl)
        }
    }

    fun dismissImage() {
        _uiState.update { currentState -> 
            currentState.copy(isFullScreen = false, currentImageUrl = null, currentThumbUrl = null)
        }
    }
} 
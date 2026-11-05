package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.BatchProgress
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class NavigationFeature(
    private val state: MutableStateFlow<ImageCompressorUiState>,
    private val markOnboardingComplete: () -> Unit,
) {
  fun completeOnboarding() {
    markOnboardingComplete()
    state.update { it.copy(screen = AppScreen.HOME) }
  }

  fun navigateTo(screen: AppScreen) {
    if (state.value.isCompressing || state.value.isSaving || state.value.isInitializing || state.value.isLoadingImages) return
    state.update { current ->
      val resultsOrigin = when {
        screen != AppScreen.RESULTS || current.screen in setOf(AppScreen.RESULTS, AppScreen.IMAGE_COMPARE) -> current.resultsReturnScreen
        current.screen == AppScreen.HOME -> AppScreen.HOME
        else -> AppScreen.COMPRESSION_SETTINGS
      }
      current.copy(screen = screen, resultsReturnScreen = resultsOrigin, message = null)
    }
  }

  fun goBack() {
    val destination =
      when (state.value.screen) {
        AppScreen.ONBOARDING,
        AppScreen.HOME -> AppScreen.HOME
        AppScreen.PREVIEW -> if (state.value.selectedImages.isEmpty()) AppScreen.HOME else AppScreen.COMPRESSION_SETTINGS
        AppScreen.COMPRESSION_SETTINGS -> AppScreen.HOME
        AppScreen.RESULTS -> state.value.resultsReturnScreen
        AppScreen.IMAGE_COMPARE -> state.value.compareReturnScreen
        AppScreen.HISTORY,
        AppScreen.SETTINGS -> AppScreen.HOME
      }
    navigateTo(destination)
  }

  fun startOver() {
    state.update {
      it.copy(
        screen = AppScreen.HOME,
        selectedImages = emptyList(),
        editorImageId = null,
        editorRetained = null,
        editorExportError = null,
        compressedImages = emptyList(),
        compareImage = null,
        resultsReturnScreen = AppScreen.COMPRESSION_SETTINGS,
        progress = BatchProgress(),
        batch = null,
        saveErrors = emptyMap(),
        message = null,
      )
    }
  }

  fun openImageCompare(image: CompressedImage) {
    state.update { it.copy(screen = AppScreen.IMAGE_COMPARE, compareImage = image,
      compareReturnScreen = if (it.screen == AppScreen.HISTORY) AppScreen.HISTORY else AppScreen.RESULTS, message = null) }
  }

  fun showMessage(message: String) {
    state.update { it.copy(message = message) }
  }

  fun consumeMessage() {
    state.update { it.copy(message = null) }
  }
}

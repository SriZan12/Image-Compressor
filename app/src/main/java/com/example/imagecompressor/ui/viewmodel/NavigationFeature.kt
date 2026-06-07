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
    state.update { it.copy(screen = screen, message = null) }
  }

  fun goBack() {
    val destination =
      when (state.value.screen) {
        AppScreen.ONBOARDING,
        AppScreen.HOME -> AppScreen.HOME
        AppScreen.PREVIEW -> AppScreen.HOME
        AppScreen.COMPRESSION_SETTINGS -> AppScreen.PREVIEW
        AppScreen.RESULTS -> AppScreen.COMPRESSION_SETTINGS
        AppScreen.IMAGE_COMPARE -> AppScreen.RESULTS
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
        compressedImages = emptyList(),
        compareImage = null,
        progress = BatchProgress(),
        message = null,
      )
    }
  }

  fun openImageCompare(image: CompressedImage) {
    state.update { it.copy(screen = AppScreen.IMAGE_COMPARE, compareImage = image, message = null) }
  }

  fun showMessage(message: String) {
    state.update { it.copy(message = message) }
  }

  fun consumeMessage() {
    state.update { it.copy(message = null) }
  }
}
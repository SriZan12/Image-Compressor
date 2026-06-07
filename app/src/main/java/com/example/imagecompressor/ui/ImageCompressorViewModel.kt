package com.example.imagecompressor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagecompressor.ImageCompressorApplication
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.ResizeMode
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
  ONBOARDING,
  HOME,
  PREVIEW,
  COMPRESSION_SETTINGS,
  RESULTS,
  IMAGE_COMPARE,
  HISTORY,
  SETTINGS,
}

data class BatchProgress(val completed: Int = 0, val total: Int = 0) {
  val fraction: Float
    get() = if (total == 0) 0f else completed.toFloat() / total
}

data class ImageCompressorUiState(
  val screen: AppScreen,
  val selectedImages: List<SelectedImage> = emptyList(),
  val compressionSettings: CompressionSettings = CompressionSettings(),
  val compressedImages: List<CompressedImage> = emptyList(),
  val compareImage: CompressedImage? = null,
  val history: List<CompressionHistoryEntity> = emptyList(),
  val themePreference: ThemePreference = ThemePreference.SYSTEM,
  val progress: BatchProgress = BatchProgress(),
  val isLoadingImages: Boolean = false,
  val isCompressing: Boolean = false,
  val isSaving: Boolean = false,
  val message: String? = null,
)

class ImageCompressorViewModel(application: Application) : AndroidViewModel(application) {
  private val container = (application as ImageCompressorApplication).container
  private val imageRepository: ImageRepository = container.imageRepository
  private val preferencesRepository: AppPreferencesRepository = container.preferencesRepository

  private val _uiState =
    MutableStateFlow(
      ImageCompressorUiState(
        screen =
          if (preferencesRepository.onboardingComplete.value) AppScreen.HOME
          else AppScreen.ONBOARDING,
        themePreference = preferencesRepository.themePreference.value,
      )
    )
  val uiState = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      imageRepository.history.collect { history -> _uiState.update { it.copy(history = history) } }
    }
    viewModelScope.launch {
      preferencesRepository.themePreference.collect { preference ->
        _uiState.update { it.copy(themePreference = preference) }
      }
    }
  }

  fun completeOnboarding() {
    preferencesRepository.setOnboardingComplete()
    _uiState.update { it.copy(screen = AppScreen.HOME) }
  }

  fun navigateTo(screen: AppScreen) {
    _uiState.update { it.copy(screen = screen, message = null) }
  }

  fun goBack() {
    val destination =
      when (_uiState.value.screen) {
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

  fun addSelectedImages(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingImages = true, message = null) }
      runCatching { imageRepository.inspectImages(uris) }
        .onSuccess { inspected ->
          _uiState.update { state ->
            val merged = (state.selectedImages + inspected).distinctBy { it.id }
            state.copy(
              selectedImages = merged,
              isLoadingImages = false,
              screen = AppScreen.PREVIEW,
              message = "${inspected.size} image${if (inspected.size == 1) "" else "s"} selected.",
            )
          }
        }
        .onFailure { throwable ->
          _uiState.update {
            it.copy(
              isLoadingImages = false,
              message = throwable.message ?: "Unable to read the selected images.",
            )
          }
        }
    }
  }

  fun removeSelectedImage(image: SelectedImage) {
    _uiState.update { state ->
      state.copy(selectedImages = state.selectedImages.filterNot { it.id == image.id })
    }
  }

  fun clearSelectedImages() {
    _uiState.update { it.copy(selectedImages = emptyList()) }
  }

  fun updateQuality(quality: Int) {
    updateSettings { copy(quality = quality.coerceIn(10, 100)) }
  }

  fun updateTargetBytes(targetBytes: Long?) {
    updateSettings { copy(targetBytes = targetBytes) }
  }

  fun updateResizeMode(resizeMode: ResizeMode) {
    updateSettings { copy(resizeMode = resizeMode) }
  }

  fun updateResizePercent(percent: Int) {
    updateSettings { copy(resizePercent = percent.coerceIn(10, 100)) }
  }

  fun updateCustomWidth(width: String) {
    updateSettings { copy(customWidth = width.filter(Char::isDigit)) }
  }

  fun updateCustomHeight(height: String) {
    updateSettings { copy(customHeight = height.filter(Char::isDigit)) }
  }

  fun updatePreserveAspectRatio(preserve: Boolean) {
    updateSettings { copy(preserveAspectRatio = preserve) }
  }

  fun updateOutputFormat(outputFormat: OutputFormat) {
    updateSettings { copy(outputFormat = outputFormat) }
  }

  private fun updateSettings(block: CompressionSettings.() -> CompressionSettings) {
    _uiState.update { it.copy(compressionSettings = it.compressionSettings.block()) }
  }

  fun compressSelectedImages() {
    val images = _uiState.value.selectedImages
    if (images.isEmpty()) {
      showMessage("Select at least one image first.")
      return
    }
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          screen = AppScreen.RESULTS,
          compressedImages = emptyList(),
          progress = BatchProgress(total = images.size),
          isCompressing = true,
          message = null,
        )
      }
      runCatching {
          imageRepository.compressImages(images, _uiState.value.compressionSettings) {
              completed,
              total ->
            _uiState.update { state -> state.copy(progress = BatchProgress(completed, total)) }
          }
        }
        .onSuccess { compressed ->
          _uiState.update {
            it.copy(
              compressedImages = compressed,
              isCompressing = false,
              message = "Compression complete.",
            )
          }
        }
        .onFailure { throwable ->
          _uiState.update {
            it.copy(
              isCompressing = false,
              message = throwable.message ?: "Unable to compress the selected images.",
            )
          }
        }
    }
  }

  fun saveToGallery(image: CompressedImage) {
    if (image.savedUri != null) {
      showMessage("This image is already saved.")
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, message = null) }
      runCatching { imageRepository.saveToGallery(image) }
        .onSuccess { saved ->
          _uiState.update { state ->
            state.copy(
              compressedImages =
                state.compressedImages.map { if (it.outputFilePath == saved.outputFilePath) saved else it },
              compareImage =
                state.compareImage?.let {
                  if (it.outputFilePath == saved.outputFilePath) saved else it
                },
              isSaving = false,
              message = "Saved to your gallery.",
            )
          }
        }
        .onFailure { throwable ->
          _uiState.update {
            it.copy(isSaving = false, message = throwable.message ?: "Unable to save the image.")
          }
        }
    }
  }

  fun saveAllToGallery() {
    val images = _uiState.value.compressedImages.filter { it.savedUri == null }
    if (images.isEmpty()) {
      showMessage("All compressed images are already saved.")
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, message = null) }
      runCatching {
          val savedByPath =
            images.associate { image ->
              val saved = imageRepository.saveToGallery(image)
              saved.outputFilePath to saved
            }
          _uiState.update { state ->
            state.copy(
              compressedImages =
                state.compressedImages.map { image -> savedByPath[image.outputFilePath] ?: image },
              compareImage =
                state.compareImage?.let { image -> savedByPath[image.outputFilePath] ?: image },
            )
          }
        }
        .onSuccess {
          _uiState.update { it.copy(isSaving = false, message = "Saved all images to your gallery.") }
        }
        .onFailure { throwable ->
          _uiState.update {
            it.copy(isSaving = false, message = throwable.message ?: "Unable to save every image.")
          }
        }
    }
  }

  fun startOver() {
    _uiState.update {
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
    _uiState.update { it.copy(screen = AppScreen.IMAGE_COMPARE, compareImage = image, message = null) }
  }

  fun deleteHistory(item: CompressionHistoryEntity) {
    viewModelScope.launch {
      runCatching { imageRepository.deleteHistory(item) }
        .onFailure { showMessage(it.message ?: "Unable to remove this history item.") }
    }
  }

  fun clearHistory() {
    viewModelScope.launch {
      runCatching { imageRepository.clearHistory() }
        .onSuccess { showMessage("History cleared.") }
        .onFailure { showMessage(it.message ?: "Unable to clear history.") }
    }
  }

  fun setThemePreference(preference: ThemePreference) {
    preferencesRepository.setThemePreference(preference)
  }

  fun showMessage(message: String) {
    _uiState.update { it.copy(message = message) }
  }

  fun consumeMessage() {
    _uiState.update { it.copy(message = null) }
  }
}

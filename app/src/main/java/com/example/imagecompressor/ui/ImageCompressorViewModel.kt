package com.example.imagecompressor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagecompressor.ImageCompressorApplication
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.ResizeMode
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.data.preferences.AppPreferencesRepository
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.example.imagecompressor.ui.viewmodel.CompressionSettingsFeature
import com.example.imagecompressor.ui.viewmodel.GallerySaveFeature
import com.example.imagecompressor.ui.viewmodel.HistoryFeature
import com.example.imagecompressor.ui.viewmodel.ImageCompressionFeature
import com.example.imagecompressor.ui.viewmodel.ImageSelectionFeature
import com.example.imagecompressor.ui.viewmodel.NavigationFeature
import com.example.imagecompressor.ui.viewmodel.SettingsFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

  private val navigationFeature =
    NavigationFeature(
      state = _uiState,
      markOnboardingComplete = preferencesRepository::setOnboardingComplete,
    )
  private val selectionFeature = ImageSelectionFeature(_uiState, imageRepository, viewModelScope)
  private val settingsFeature = CompressionSettingsFeature(_uiState)
  private val compressionFeature =
    ImageCompressionFeature(
      _uiState,
      imageRepository,
      viewModelScope,
      navigationFeature::showMessage
    )
  private val gallerySaveFeature =
    GallerySaveFeature(_uiState, imageRepository, viewModelScope, navigationFeature::showMessage)
  private val historyFeature =
    HistoryFeature(imageRepository, viewModelScope, navigationFeature::showMessage)
  private val appSettingsFeature = SettingsFeature(preferencesRepository)

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

  fun completeOnboarding() = navigationFeature.completeOnboarding()

  fun navigateTo(screen: AppScreen) = navigationFeature.navigateTo(screen)

  fun goBack() = navigationFeature.goBack()

  fun addSelectedImages(uris: List<Uri>) = selectionFeature.addSelectedImages(uris)

  fun removeSelectedImage(image: SelectedImage) = selectionFeature.removeSelectedImage(image)

  fun clearSelectedImages() = selectionFeature.clearSelectedImages()

  fun updateQuality(quality: Int) = settingsFeature.updateQuality(quality)

  fun updateTargetBytes(targetBytes: Long?) = settingsFeature.updateTargetBytes(targetBytes)

  fun updateResizeMode(resizeMode: ResizeMode) = settingsFeature.updateResizeMode(resizeMode)

  fun updateResizePercent(percent: Int) = settingsFeature.updateResizePercent(percent)

  fun updateCustomWidth(width: String) = settingsFeature.updateCustomWidth(width)

  fun updateCustomHeight(height: String) = settingsFeature.updateCustomHeight(height)

  fun updatePreserveAspectRatio(preserve: Boolean) = settingsFeature.updatePreserveAspectRatio(preserve)

  fun updateOutputFormat(outputFormat: OutputFormat) = settingsFeature.updateOutputFormat(outputFormat)

  fun compressSelectedImages() = compressionFeature.compressSelectedImages()

  fun saveToGallery(image: CompressedImage) = gallerySaveFeature.saveToGallery(image)

  fun saveAllToGallery() = gallerySaveFeature.saveAllToGallery()

  fun startOver() = navigationFeature.startOver()

  fun openImageCompare(image: CompressedImage) = navigationFeature.openImageCompare(image)

  fun deleteHistory(item: CompressionHistoryEntity) = historyFeature.deleteHistory(item)

  fun clearHistory() = historyFeature.clearHistory()

  fun setThemePreference(preference: ThemePreference) = appSettingsFeature.setThemePreference(preference)

  fun showMessage(message: String) = navigationFeature.showMessage(message)

  fun consumeMessage() = navigationFeature.consumeMessage()
}
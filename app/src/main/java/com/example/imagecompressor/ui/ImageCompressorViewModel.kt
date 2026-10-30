package com.example.imagecompressor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagecompressor.ImageCompressorApplication
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.ResizeMode
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.data.preferences.AppPreferencesRepository
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.ui.viewmodel.EditorExportFeature
import com.example.imagecompressor.ui.viewmodel.CompressionSettingsFeature
import com.example.imagecompressor.ui.viewmodel.GallerySaveFeature
import com.example.imagecompressor.ui.viewmodel.HistoryFeature
import com.example.imagecompressor.ui.viewmodel.ImageCompressionFeature
import com.example.imagecompressor.ui.viewmodel.ImageSelectionFeature
import com.example.imagecompressor.ui.viewmodel.NavigationFeature
import com.example.imagecompressor.ui.viewmodel.restoreSession
import com.example.imagecompressor.ui.viewmodel.SettingsFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import com.example.imagecompressor.data.model.SettingsCodec
import com.example.imagecompressor.data.model.BatchSnapshot
import com.example.imagecompressor.ui.state.BatchProgress
import com.example.imagecompressor.data.userMessage
import com.example.imagecompressor.ui.editor.LiveEditorSession
import com.example.imagecompressor.util.ImageCompressor
import com.example.imagecompressor.util.TemporaryImageProcessor
import com.example.imagecompressor.util.compression.CompressionPolicy
import com.example.imagecompressor.util.compression.PixelSize
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ImageCompressorViewModel internal constructor(
  application: Application,
  private val savedState: SavedStateHandle,
  private val imageRepository: ImageRepository,
  private val preferencesRepository: AppPreferencesRepository,
  previewProcessor: TemporaryImageProcessor = ImageCompressor(application).createPreviewProcessor(),
) : AndroidViewModel(application) {
  constructor(application: Application, savedState: SavedStateHandle) : this(application, savedState,
    (application as ImageCompressorApplication).container.imageRepository,
    application.container.preferencesRepository)

  private val _uiState =
    MutableStateFlow(
      ImageCompressorUiState(
        screen = AppScreen.HOME,
        themePreference = preferencesRepository.themePreference.value,
        compressionSettings = savedState.get<String>("settings")?.let(SettingsCodec::decode) ?: preferencesRepository.lastCompressionSettings,
        dynamicColor = preferencesRepository.dynamicColor.value,
      )
    )
  val uiState = _uiState.asStateFlow()
  private val editorSession = LiveEditorSession(viewModelScope, previewProcessor)
  private val editorResumed = MutableStateFlow(false)
  private val editorTargetDraftValid = MutableStateFlow(true)
  private val editorExportFeature = EditorExportFeature(_uiState, imageRepository, editorSession,
    viewModelScope, { editorResumed.value }, { editorTargetDraftValid.value })

  private val navigationFeature =
    NavigationFeature(
      state = _uiState,
      markOnboardingComplete = preferencesRepository::setOnboardingComplete,
    )
  private val selectionFeature = ImageSelectionFeature(_uiState, imageRepository, viewModelScope)
  private val settingsFeature = CompressionSettingsFeature(_uiState, preferencesRepository::rememberCompressionSettings)
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

  private var sessionLoaded = false

  init {
    if (!savedState.contains("settings")) savedState["settings"] = SettingsCodec.encode(preferencesRepository.lastCompressionSettings)
    viewModelScope.launch {
      combine(editorSession.state, uiState.map { current ->
        current.editorRetained?.takeIf { it.settings == current.compressionSettings &&
          it.image.original == current.selectedImages.singleOrNull() }
      }.distinctUntilChanged(), editorTargetDraftValid) { preview, retained, draftValid ->
        if (!draftValid) EditorPreview.NotCalculated else retained ?: preview
      }.distinctUntilChanged().collect { preview -> _uiState.update { it.copy(editorPreview = preview) } }
    }
    viewModelScope.launch {
      combine(uiState, editorResumed, editorTargetDraftValid) { current, resumed, draftValid ->
        val settings = current.compressionSettings
        val hasRetained = current.editorRetained?.let { it.settings == settings &&
          it.image.original == current.selectedImages.singleOrNull() } == true
        val active = resumed && draftValid && current.screen == AppScreen.COMPRESSION_SETTINGS &&
          !current.isInitializing && current.initializationError == null && !current.isLoadingImages &&
          !current.isCompressing && !hasRetained && current.selectedImages.all { image ->
            runCatching { CompressionPolicy.requestedSize(PixelSize(image.width, image.height), settings) }.isSuccess
          }
        (current.editorSource.takeIf { active }) to settings
      }.distinctUntilChanged().collect { (image, settings) -> editorSession.request(image, settings) }
    }
    restoreSession()
  }

  fun setEditorResumed(resumed: Boolean) {
    editorResumed.value = resumed
    if (!resumed) editorSession.request(null, _uiState.value.compressionSettings)
  }

  fun setEditorTargetDraftValid(valid: Boolean) {
    editorTargetDraftValid.value = valid
    if (!valid) editorSession.request(null, _uiState.value.compressionSettings)
  }

  fun retryEditorPreview() {
    if (_uiState.value.isSaving) return
    _uiState.update { it.copy(editorRetained = null, editorExportError = null) }
    editorSession.retry()
  }

  fun saveEditorPhoto() = editorExportFeature.save()

  fun shareEditorPhoto(onReady: (Uri) -> Unit) = editorExportFeature.share(onReady)

  override fun onCleared() {
    editorSession.close()
    super.onCleared()
  }

  private fun applyBatch(batch: BatchSnapshot?) {
    _uiState.update { current -> current.copy(batch = batch,
      compressedImages = batch?.results.orEmpty(),
      progress = BatchProgress(batch?.finished ?: 0, batch?.items?.size ?: 0),
      compareImage = current.compareImage?.let { image -> batch?.results?.find { it.historyId == image.historyId } ?: image }) }
  }

  private fun restoreSession() {
    _uiState.update { it.copy(isInitializing = true, initializationError = null) }
    viewModelScope.launch {
      try {
        imageRepository.initialize()
        val recovered = imageRepository.batch.first()
        val restored = restoreSession(savedState, recovered, imageRepository)
        val retainedSettings = savedState.get<String>("editorResultSettings")?.let(SettingsCodec::decode)
        val retained = savedState.get<Long>("editorResultId")?.let { imageRepository.getResult(it) }
          ?.takeIf { retainedSettings == restored.settings && it.original == restored.selection.singleOrNull() &&
            it.outputFilePath.isNotBlank() }
          ?.let { EditorPreview.Ready(it, restored.settings) }
        applyBatch(recovered)
        _uiState.update { it.copy(selectedImages = restored.selection, compressionSettings = restored.settings,
          screen = AppScreen.HOME, compareImage = null, message = restored.message,
          editorRetained = retained, editorImageId = savedState.get<String>("editorImageId")
            ?.takeIf { id -> restored.selection.any { image -> image.id == id } }) }
        sessionLoaded = true
        viewModelScope.launch { imageRepository.batch.catch { showMessage("Unable to read batch progress. Check storage and reopen the app.") }.collect(::applyBatch) }
        viewModelScope.launch {
          imageRepository.history.catch { showMessage("Unable to read history. Check storage and reopen the app.") }.collect { history ->
            // Room's list emission can lag the commit that just produced an export receipt.
            // Query that receipt directly before clearing it or changing its Saved status.
            val expected = _uiState.value.editorRetained
            var checked = false
            val verified = expected?.let { ready ->
              try {
                val result = imageRepository.getResult(ready.image.historyId)
                checked = true
                result?.takeIf { it.outputFilePath.isNotBlank() && it.original == ready.image.original &&
                  it.compressedSizeBytes == ready.image.compressedSizeBytes && it.width == ready.image.width &&
                  it.height == ready.image.height && it.format == ready.image.format }
                  ?.let { EditorPreview.Ready(it, ready.settings) }
              } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) { showMessage(userMessage(failure, "Unable to check this copy. Please try again.")); null }
            }
            _uiState.update { current -> current.copy(history = history,
              editorRetained = if (checked && current.editorRetained == expected) verified else current.editorRetained,
              compareImage = current.compareImage?.let { image -> history.find { it.id == image.historyId }?.let { row ->
                if (row.availability == "UNAVAILABLE") null else image.copy(savedUri = row.savedPath?.let(Uri::parse))
              } }) }
          }
        }
        viewModelScope.launch { preferencesRepository.dynamicColor.collect { enabled -> _uiState.update { it.copy(dynamicColor = enabled) } } }
        viewModelScope.launch { preferencesRepository.themePreference.collect { preference -> _uiState.update { it.copy(themePreference = preference) } } }
        viewModelScope.launch {
          uiState.collect { current ->
            savedState["selection"] = ArrayList(current.selectedImages.take(30).map { it.uri.toString() })
            savedState["editorImageId"] = current.editorSource?.id
            savedState["settings"] = SettingsCodec.encode(current.compressionSettings)
            savedState["screen"] = current.screen.name
            savedState["compareId"] = current.compareImage?.historyId
            savedState["compareFrom"] = current.compareReturnScreen.name
            savedState["editorResultId"] = current.editorRetained?.image?.historyId
            savedState["editorResultSettings"] = current.editorRetained?.let { SettingsCodec.encode(it.settings) }
          }
        }
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { _uiState.update { it.copy(initializationError =
        userMessage(failure, "Unable to restore results. Check available storage and try again.")) } }
      finally { _uiState.update { it.copy(isInitializing = false) } }
    }
  }

  fun refreshAvailability() {
    if (_uiState.value.isInitializing) return
    if (!sessionLoaded) { restoreSession(); return }
    viewModelScope.launch {
      try { imageRepository.refreshAvailability() }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { showMessage(userMessage(failure, "Unable to check saved results.")) }
    }
  }

  fun shareResults(ids: List<Long>, onReady: (List<Uri>) -> Unit) {
    if (_uiState.value.isInitializing) return
    viewModelScope.launch {
      try { onReady(ids.map { imageRepository.shareUri(it) }) }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { showMessage(userMessage(failure, "Unable to share these images.")) }
    }
  }

  fun openHistory(item: CompressionHistoryEntity) {
    viewModelScope.launch {
      try {
        val result = imageRepository.getResult(item.id)
        if (result == null) showMessage("This result is unavailable. Select the original again.")
        else navigationFeature.openImageCompare(result)
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { showMessage(userMessage(failure, "Unable to open this result.")) }
    }
  }

  fun completeOnboarding() = navigationFeature.completeOnboarding()

  fun navigateTo(screen: AppScreen) = navigationFeature.navigateTo(screen)

  fun goBack() = navigationFeature.goBack()

  fun addSelectedImages(uris: List<Uri>) = selectionFeature.addSelectedImages(uris)

  fun replaceSelectedImages(uris: List<Uri>) = selectionFeature.addSelectedImages(uris, replace = true)

  fun previewSelectedImage(image: SelectedImage) {
    val current = _uiState.value
    if (current.screen != AppScreen.COMPRESSION_SETTINGS || current.isSaving || current.isCompressing ||
      current.isLoadingImages || current.selectedImages.none { it == image } || current.editorSource == image) return
    _uiState.update { it.copy(editorImageId = image.id, editorPreview = EditorPreview.NotCalculated,
      editorRetained = null, editorExportError = null) }
  }

  fun removeSelectedImage(image: SelectedImage) = selectionFeature.removeSelectedImage(image)

  fun clearSelectedImages() = selectionFeature.clearSelectedImages()

  fun updateCompressionSettings(settings: CompressionSettings) = settingsFeature.applySettings(settings)

  fun setDynamicColor(enabled: Boolean) = preferencesRepository.setDynamicColor(enabled)

  fun updateQuality(quality: Int) = settingsFeature.updateQuality(quality)

  fun updateTargetBytes(targetBytes: Long?) = settingsFeature.updateTargetBytes(targetBytes)

  fun updateAllowTargetResize(allow: Boolean) = settingsFeature.updateAllowTargetResize(allow)

  fun updateResizeMode(resizeMode: ResizeMode) = settingsFeature.updateResizeMode(resizeMode)

  fun updateResizePercent(percent: Int) = settingsFeature.updateResizePercent(percent)

  fun updateCustomWidth(width: String) = settingsFeature.updateCustomWidth(width)

  fun updateCustomHeight(height: String) = settingsFeature.updateCustomHeight(height)

  fun updatePreserveAspectRatio(preserve: Boolean) = settingsFeature.updatePreserveAspectRatio(preserve)

  fun updateOutputFormat(outputFormat: OutputFormat) = settingsFeature.updateOutputFormat(outputFormat)

  fun compressSelectedImages() {
    // Release live work before the retained batch enters the same native pipeline.
    editorSession.request(null, _uiState.value.compressionSettings)
    compressionFeature.compressSelectedImages()
  }

  fun saveToGallery(image: CompressedImage) = gallerySaveFeature.saveToGallery(image)

  fun saveAllToGallery() = gallerySaveFeature.saveAllToGallery()

  fun startOver(onReady: () -> Unit = {}) {
    if (_uiState.value.isCompressing || _uiState.value.isSaving) return
    viewModelScope.launch {
      try { imageRepository.dismissBatch(); navigationFeature.startOver(); onReady() }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { showMessage(userMessage(failure, "Unable to close this batch.")) }
    }
  }

  fun cancelCompression() = compressionFeature.cancel()
  fun retryRemaining() = compressionFeature.retryRemaining()

  fun openImageCompare(image: CompressedImage) = navigationFeature.openImageCompare(image)

  fun deleteHistory(item: CompressionHistoryEntity) = historyFeature.deleteHistory(item)

  fun clearHistory() = historyFeature.clearHistory()

  fun setThemePreference(preference: ThemePreference) = appSettingsFeature.setThemePreference(preference)

  fun showMessage(message: String) = navigationFeature.showMessage(message)

  fun consumeMessage() = navigationFeature.consumeMessage()
}

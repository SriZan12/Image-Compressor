package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.ResizeMode
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class CompressionSettingsFeature(
    private val state: MutableStateFlow<ImageCompressorUiState>,
    private val rememberSettings: (CompressionSettings) -> Unit = {},
) {
  fun applySettings(settings: CompressionSettings) { updateSettings { settings } }

  fun updateQuality(quality: Int) {
    updateSettings { copy(quality = quality.coerceIn(10, 100)) }
  }

  fun updateTargetBytes(targetBytes: Long?) {
    updateSettings { copy(targetBytes = targetBytes) }
  }

  fun updateAllowTargetResize(allow: Boolean) {
    updateSettings { copy(allowTargetResize = allow) }
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
    if (state.value.isSaving) return
    val updated = state.value.compressionSettings.block()
    if (updated == state.value.compressionSettings) return
    state.update { it.copy(compressionSettings = updated, editorRetained = null, editorExportError = null) }
    rememberSettings(updated)
  }
}

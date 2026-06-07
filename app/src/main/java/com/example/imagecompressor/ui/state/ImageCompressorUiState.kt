package com.example.imagecompressor.ui.state

import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.ThemePreference

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
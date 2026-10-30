package com.example.imagecompressor.ui.state

import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.data.model.BatchSnapshot

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

enum class EditorAction { SAVE, SHARE }

data class BatchProgress(val completed: Int = 0, val total: Int = 0) {
  val fraction: Float
    get() = if (total == 0) 0f else completed.toFloat() / total
}

data class ImageCompressorUiState(
  val screen: AppScreen,
  val selectedImages: List<SelectedImage> = emptyList(),
  val editorImageId: String? = null,
  val compressionSettings: CompressionSettings = CompressionSettings(),
  val editorPreview: EditorPreview = EditorPreview.NotCalculated,
  val editorRetained: EditorPreview.Ready? = null,
  val editorAction: EditorAction? = null,
  val editorExportError: String? = null,
  val compressedImages: List<CompressedImage> = emptyList(),
  val compareImage: CompressedImage? = null,
  val compareReturnScreen: AppScreen = AppScreen.RESULTS,
  val history: List<CompressionHistoryEntity> = emptyList(),
  val themePreference: ThemePreference = ThemePreference.SYSTEM,
  val dynamicColor: Boolean = true,
  val progress: BatchProgress = BatchProgress(),
  val isLoadingImages: Boolean = false,
  val isCompressing: Boolean = false,
  val isSaving: Boolean = false,
  val isInitializing: Boolean = true,
  val initializationError: String? = null,
  val isCancelling: Boolean = false,
  val batch: BatchSnapshot? = null,
  val saveErrors: Map<Long, String> = emptyMap(),
  val message: String? = null,
) {
  val editorSource: SelectedImage?
    get() = selectedImages.find { it.id == editorImageId } ?: selectedImages.firstOrNull()
}

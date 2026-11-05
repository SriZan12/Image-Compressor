package com.example.imagecompressor.ui.state

import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SelectedImage

/** Live previews contain measured temporary candidates; estimates never authorize export. */
sealed interface EditorPreview {
  data object NotCalculated : EditorPreview
  data object Loading : EditorPreview
  data class Updating(val estimatedBytes: Long? = null, val previousImage: CompressedImage? = null) : EditorPreview
  data class Ready(val image: CompressedImage, val settings: CompressionSettings) : EditorPreview
  data class Failure(val message: String) : EditorPreview
}

data class PhotoEditorState(
  val original: SelectedImage?,
  val settings: CompressionSettings = CompressionSettings(),
  val preview: EditorPreview = EditorPreview.NotCalculated,
  val photoCount: Int = 1,
  val photoIndex: Int = 0,
  val dimensionsValid: Boolean = true,
  val isSaving: Boolean = false,
  val isSharing: Boolean = false,
  val exportError: String? = null,
) {
  val previewImage: CompressedImage?
    get() = when (val value = preview) {
      is EditorPreview.Ready -> value.image
      is EditorPreview.Updating -> value.previousImage
      else -> null
    }?.takeIf { it.original == original }

  // An older result is not exportable simply because it is still available to draw.
  val currentResult: CompressedImage?
    get() = (preview as? EditorPreview.Ready)?.takeIf {
      it.settings == settings && it.image.original == original && it.image.compressedSizeBytes > 0
    }?.image
}

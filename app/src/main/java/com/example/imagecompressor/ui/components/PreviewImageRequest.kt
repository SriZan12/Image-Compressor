package com.example.imagecompressor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.memory.MemoryCache
import coil.request.ImageRequest

/** Rebuild only when the source or measured pixel viewport changes, including window resizing. */
@Composable
internal fun rememberPreviewImageRequest(
  model: Any?,
  viewport: IntSize,
  previousPreviewKey: MemoryCache.Key?,
): ImageRequest? {
  val context = LocalContext.current
  // The last successful key is intentionally not a remember key: recording a success must not
  // restart its own request. A later viewport change may reuse those same-source cached pixels.
  return remember(context, model, viewport) {
    if (viewport.width <= 0 || viewport.height <= 0) null
    else {
      val builder = if (model is ImageRequest) model.newBuilder(context)
        else ImageRequest.Builder(context).data(model)
      builder.size(viewport.width, viewport.height)
        .apply { if (previousPreviewKey != null) placeholderMemoryCacheKey(previousPreviewKey) }
        .build()
    }
  }
}

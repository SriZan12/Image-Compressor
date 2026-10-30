package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun ImageCompareScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier,
  onSave: (CompressedImage) -> Unit, onShare: (CompressedImage) -> Unit) {
  val image = state.compareImage
  if (image == null) {
    ScreenColumn(modifier) { EmptyState(stringResource(R.string.no_comparison), stringResource(R.string.no_comparison_body)) }
    return
  }
  AdaptiveActionScreen(modifier, primary = { wide ->
    ComparisonViewer(image, if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), expanded = wide)
  }, detail = { _, inlineActions ->
    Column(verticalArrangement = Arrangement.spacedBy(Space.section)) {
      SavingsSummary(image.original.sizeBytes, image.compressedSizeBytes)
      inlineActions()
      if (image.savedUri != null) Notice(stringResource(R.string.saved_location), success = true)
      else Notice(stringResource(R.string.retained_hint))
      state.saveErrors[image.historyId]?.let { Notice(it, error = true) }
      PhotoDetails(image)
    }
  }, actions = { SaveShareActions(1, image.savedUri != null, state.isSaving, { onSave(image) }, { onShare(image) }) })
}

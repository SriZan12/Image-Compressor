package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun HomeScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier, onSelectImages: () -> Unit,
  onReviewSelection: () -> Unit, onOpenHistory: () -> Unit) {
  ScreenColumn(modifier) {
    Spacer(Modifier.height(Space.xl))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
      AppIcon(R.drawable.ic_add_photo_alternate, Modifier.padding(20.dp))
    }
    PageTitle(stringResource(R.string.home_title), stringResource(R.string.home_body), large = true)
    Spacer(Modifier.height(Space.sm))
    if (state.selectedImages.isNotEmpty()) {
      ActionButton(stringResource(R.string.continue_selection) + " · " + photoCount(state.selectedImages.size), onReviewSelection,
        Modifier.fillMaxWidth(), enabled = !state.isLoadingImages)
      TextButton(onSelectImages, Modifier.align(Alignment.CenterHorizontally), enabled = !state.isLoadingImages) {
        Text(stringResource(R.string.choose_new_photos))
      }
    } else {
      ActionButton(stringResource(R.string.select_photos), onSelectImages, Modifier.fillMaxWidth(), R.drawable.ic_add_photo_alternate,
        enabled = !state.isLoadingImages)
    }
    if (state.isLoadingImages) PhotoOpeningStatus()
    PrivacyNote()
    if (state.history.isNotEmpty()) {
      Spacer(Modifier.height(Space.section))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.recent_activity), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        TextButton(onOpenHistory) { Text(stringResource(R.string.view_history)) }
      }
      HistoryPreview(state.history.first(), onOpenHistory)
    }
  }
}

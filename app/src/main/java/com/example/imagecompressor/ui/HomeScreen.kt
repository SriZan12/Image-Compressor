package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.example.imagecompressor.data.model.preset

@Composable
fun HomeScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier, onSelectImages: () -> Unit,
  onReviewSelection: () -> Unit, onOpenHistory: () -> Unit) {
  ScreenColumn(modifier) {
    Spacer(Modifier.height(Space.sm))
    PageTitle(stringResource(R.string.home_title), stringResource(R.string.home_body), large = true)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
      Column(Modifier.fillMaxWidth().padding(Space.section), verticalArrangement = Arrangement.spacedBy(Space.section),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_shrink), null, Modifier.padding(vertical = Space.lg).size(88.dp), tint = MaterialTheme.colorScheme.primary)
        ActionButton(stringResource(if (state.selectedImages.isEmpty()) R.string.select_photos else R.string.add_photos), onSelectImages, Modifier.fillMaxWidth(), R.drawable.ic_add_photo_alternate,
          enabled = !state.isLoadingImages)
        Text(stringResource(R.string.home_pick_caption), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (state.isLoadingImages) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.selectedImages.isNotEmpty()) ActionButton(stringResource(R.string.continue_selection), onReviewSelection,
      Modifier.fillMaxWidth(), R.drawable.ic_arrow_forward, secondary = true, enabled = !state.isLoadingImages)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
      AppIcon(R.drawable.ic_tune)
      Column {
        Text(stringResource(R.string.last_settings), style = MaterialTheme.typography.titleSmall)
        Text(presetLabel(state.compressionSettings.preset()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    PrivacyNote()
    if (state.history.isNotEmpty()) {
      HorizontalDivider()
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.recent_activity), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onOpenHistory) { Text(stringResource(R.string.view_history)) }
      }
      state.history.take(2).forEach { HistoryPreview(it, onOpenHistory) }
    }
  }
}

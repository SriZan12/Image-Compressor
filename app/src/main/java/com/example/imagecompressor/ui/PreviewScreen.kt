package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun PreviewScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier, onSelectMany: () -> Unit,
  onRemove: (SelectedImage) -> Unit, onContinue: () -> Unit) {
  LazyVerticalGrid(GridCells.Adaptive(if (LocalDensity.current.fontScale > 1.5f) 240.dp else 156.dp),
    modifier.fillMaxSize(), contentPadding = PaddingValues(Space.page), horizontalArrangement = Arrangement.spacedBy(Space.md),
    verticalArrangement = Arrangement.spacedBy(Space.lg)) {
    item(span = { GridItemSpan(maxLineSpan) }) {
      PageTitle(if (state.selectedImages.isEmpty()) stringResource(R.string.empty_selection) else photoCount(state.selectedImages.size),
        stringResource(if (state.selectedImages.isEmpty()) R.string.empty_selection_body else R.string.selection_body))
    }
    item(span = { GridItemSpan(maxLineSpan) }) {
      if (state.selectedImages.isEmpty()) ActionButton(stringResource(R.string.add_photos), onSelectMany,
        Modifier.fillMaxWidth(), R.drawable.ic_add_photo_alternate, enabled = !state.isLoadingImages)
      else ResponsiveActions(primary = { ActionButton(stringResource(R.string.done), onContinue, it, R.drawable.ic_check,
        enabled = state.selectedImages.isNotEmpty() && !state.isLoadingImages) },
        secondary = { ActionButton(stringResource(R.string.add_photos), onSelectMany, it, R.drawable.ic_add_photo_alternate,
          secondary = true, enabled = !state.isLoadingImages) })
    }
    if (state.isLoadingImages) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    items(state.selectedImages, key = { it.id }) { image ->
      Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
          Photo(image.uri, image.displayName, Modifier.fillMaxWidth().aspectRatio(1f), ContentScale.Crop)
          Row(Modifier.padding(start = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = Space.md)) {
              Text(image.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
              Text(image.sizeBytes.toReadableSize(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ onRemove(image) }, enabled = !state.isLoadingImages) {
              AppIcon(R.drawable.ic_close, description = stringResource(R.string.remove_photo, image.displayName))
            }
          }
        }
      }
    }
  }
}

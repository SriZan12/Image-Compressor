package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier, onDelete: (CompressionHistoryEntity) -> Unit,
  onShare: (CompressionHistoryEntity) -> Unit, onOpen: (CompressionHistoryEntity) -> Unit, onSelectPhotos: () -> Unit) {
  var removalId by rememberSaveable { mutableStateOf<Long?>(null) }
  val removal = state.history.find { it.id == removalId }
  removal?.let { item ->
    AlertDialog(onDismissRequest = { removalId = null }, title = { Text(stringResource(R.string.remove_result_title)) },
      text = { Text(stringResource(R.string.remove_result_body)) },
      confirmButton = { TextButton({ onDelete(item); removalId = null }) { Text(stringResource(R.string.remove)) } },
      dismissButton = { TextButton({ removalId = null }) { Text(stringResource(R.string.keep)) } })
  }
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(Space.page),
      verticalArrangement = Arrangement.spacedBy(Space.lg)) {
      if (state.history.isEmpty()) {
        item { EmptyState(stringResource(R.string.empty_history_title), stringResource(R.string.empty_history_body), icon = R.drawable.ic_history) }
        item { ActionButton(stringResource(R.string.select_photos), onSelectPhotos, Modifier.fillMaxWidth(), R.drawable.ic_add_photo_alternate) }
      } else {
        item { Text(stringResource(R.string.retention_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.history, key = { it.id }) { item ->
          Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            HistoryPreview(item) { onOpen(item) }
            Text(describeSizeChange(item.originalSizeBytes, item.compressedSizeBytes), style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.availability == "UNAVAILABLE") Text(stringResource(R.string.unavailable_help), style = MaterialTheme.typography.bodySmall)
            val largeText = LocalDensity.current.fontScale > 1.3f
            val shareLabel = stringResource(R.string.share_named_photo, item.displayName)
            val removeLabel = stringResource(R.string.remove_photo, item.displayName)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
              TextButton({ onShare(item) }, modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier).semantics { contentDescription = shareLabel }, enabled = item.availability != "UNAVAILABLE") {
                AppIcon(R.drawable.ic_share); Spacer(Modifier.width(Space.sm)); Text(stringResource(R.string.share), if (largeText) Modifier.weight(1f) else Modifier)
              }
              TextButton({ removalId = item.id }, modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier).semantics { contentDescription = removeLabel }) { AppIcon(R.drawable.ic_delete); Spacer(Modifier.width(Space.sm)); Text(stringResource(R.string.remove), if (largeText) Modifier.weight(1f) else Modifier) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
        item { ActionButton(stringResource(R.string.choose_new_photos), onSelectPhotos, Modifier.fillMaxWidth(),
          R.drawable.ic_add_photo_alternate, secondary = true) }
      }
    }
  }
}

@Composable
fun HistoryPreview(item: CompressionHistoryEntity, onOpen: () -> Unit) {
  val preview = if (item.localPath != null) item.compressedUri else item.savedPath ?: item.compressedUri
  Surface(onClick = onOpen, enabled = item.availability != "UNAVAILABLE", shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(Space.sm)) {
      if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Photo(preview, null, Modifier.size(64.dp), ContentScale.Crop)
            Spacer(Modifier.weight(1f))
            AppIcon(R.drawable.ic_chevron_right)
          }
          HistoryMetadata(item, Modifier.fillMaxWidth())
        }
      } else Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        Photo(preview, null, Modifier.size(64.dp), ContentScale.Crop)
        HistoryMetadata(item, Modifier.weight(1f))
        AppIcon(R.drawable.ic_chevron_right)
      }
    }
  }
}

@Composable
private fun HistoryMetadata(item: CompressionHistoryEntity, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
    Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text("${item.compressedSizeBytes.toReadableSize()} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.createdAt))}",
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(when (item.availability) { "EXPORTED" -> R.string.exported; "RETAINED" -> R.string.retained; else -> R.string.unavailable }),
      style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

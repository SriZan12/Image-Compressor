package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.theme.LocalStatusColors
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlin.math.abs

@Composable
fun ResultsScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier, onSaveAll: () -> Unit,
  onShareAll: () -> Unit, onCompare: (CompressedImage) -> Unit, onStartOver: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
  if (state.isCompressing) { CompressionProgressScreen(state, modifier, onCancel); return }
  val images = state.compressedImages
  val before = if (images.all { it.original.sizeBytes > 0 }) images.sumOf { it.original.sizeBytes } else 0L
  val after = images.sumOf { it.compressedSizeBytes }
  val retry = state.batch?.items.orEmpty().filter { it.canRetry }
  val needsReview = retry.isNotEmpty() || images.any { it.targetSizeStatus == TargetSizeStatus.UNMET }
  AdaptiveActionScreen(modifier, hasActions = images.isNotEmpty(), primary = { wide ->
    if (images.isEmpty()) EmptyState(stringResource(R.string.no_results), stringResource(R.string.no_results_body))
    else Column(if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.section)) {
      if (!wide) SavingsSummary(before, after)
      if (images.size > 1) {
        if (!wide) BatchSizes(before, after)
        Text(stringResource(R.string.previewing_photo, images.first().original.displayName), style = MaterialTheme.typography.titleSmall)
      }
      ComparisonViewer(images.first(), if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), expanded = wide)
    }
  }, detail = { wide, inlineActions ->
    Column(verticalArrangement = Arrangement.spacedBy(Space.section)) {
      if (wide || needsReview || images.isEmpty()) PageTitle(stringResource(when {
        state.batch?.status == BatchStatus.CANCELLED -> R.string.results_cancelled
        state.batch?.status == BatchStatus.INTERRUPTED -> R.string.results_interrupted
        needsReview -> R.string.results_review
        else -> R.string.results_ready
      }))
      if (images.isNotEmpty()) {
        if (wide) {
          SavingsSummary(before, after)
          if (images.size > 1) BatchSizes(before, after)
        }
        if (state.batch != null) Text(pluralStringResource(R.plurals.finished_count, state.batch.items.size, images.size, state.batch.items.size),
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        inlineActions()
        val anySaved = images.any { it.savedUri != null }
        if (anySaved) Notice(stringResource(R.string.saved_location), success = true)
        if (images.any { it.savedUri == null }) Text(stringResource(R.string.retained_hint), style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.saveErrors.values.distinct().forEach { Notice(it, error = true) }
        if (images.size == 1) PhotoDetails(images.first())
        else {
          Text(stringResource(R.string.batch_details), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
          images.forEach { image -> ResultRow(image) { onCompare(image) } }
        }
      }
      retry.forEach { item -> SectionCard {
        Text(item.image.displayName, style = MaterialTheme.typography.titleSmall)
        Notice(item.error ?: stringResource(R.string.no_results_body), error = item.status == ItemStatus.FAILED)
      } }
      if (retry.isNotEmpty()) {
        ActionButton(stringResource(R.string.retry_remaining), onRetry, Modifier.fillMaxWidth(), R.drawable.ic_restart_alt,
          enabled = !state.isSaving, secondary = images.isNotEmpty())
        Text(stringResource(R.string.retry_settings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      ActionButton(stringResource(R.string.new_photos), onStartOver, Modifier.fillMaxWidth(), R.drawable.ic_add_photo_alternate,
        enabled = !state.isSaving, secondary = images.isNotEmpty() || retry.isNotEmpty())
    }
  }, actions = {
    if (images.isNotEmpty()) SaveShareActions(images.size, images.all { it.savedUri != null }, state.isSaving, onSaveAll, onShareAll)
  })
}

@Composable
fun SavingsSummary(before: Long, after: Long) {
  val difference = before - after
  Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
    if (before > 0 && difference != 0L) {
      Text(abs(difference).toReadableSize(), style = MaterialTheme.typography.displayMedium,
        color = if (difference > 0) LocalStatusColors.current.success else MaterialTheme.colorScheme.onSurface)
      Text(stringResource(if (difference > 0) R.string.saved_space else R.string.larger_space), style = MaterialTheme.typography.bodyLarge)
    }
    StatusPill(describeSizeChange(before, after), success = before > after && before > 0,
      icon = if (before > after && before > 0) R.drawable.ic_check_circle else R.drawable.ic_info)
  }
}

@Composable
fun SaveShareActions(count: Int, allSaved: Boolean, isSaving: Boolean, onSave: () -> Unit, onShare: () -> Unit) {
  ResponsiveActions(primary = { ActionButton(stringResource(when {
    isSaving -> R.string.saving; allSaved -> R.string.saved; count > 1 -> R.string.save_all; else -> R.string.save_gallery
  }), onSave, it, if (allSaved) R.drawable.ic_check else R.drawable.ic_download, enabled = !isSaving && !allSaved) },
    secondary = { ActionButton(stringResource(if (count > 1) R.string.share_all else R.string.share), onShare, it, R.drawable.ic_share,
      enabled = !isSaving, secondary = true) })
}

@Composable
fun PhotoDetails(image: CompressedImage) {
  SectionCard {
    Text(stringResource(R.string.image_details), style = MaterialTheme.typography.titleMedium)
    Text(image.original.displayName, style = MaterialTheme.typography.bodyMedium)
    Text("${image.width} × ${image.height} · ${image.format.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CompressionNotices(image)
  }
}

@Composable
private fun ResultRow(image: CompressedImage, onCompare: () -> Unit) {
  Surface(onClick = onCompare, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
      Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        Photo(image.outputUri, null, Modifier.size(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
          Text(image.original.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text("${image.original.sizeBytes.toReadableSize()} → ${image.compressedSizeBytes.toReadableSize()}", style = MaterialTheme.typography.bodySmall)
        }
        AppIcon(R.drawable.ic_chevron_right, description = stringResource(R.string.open_comparison, image.original.displayName))
      }
      StatusPill(describeSizeChange(image.original.sizeBytes, image.compressedSizeBytes), success = image.reductionPercent > 0)
      if (image.targetSizeStatus != TargetSizeStatus.NOT_REQUESTED) CompressionNotices(image)
    }
  }
}

@Composable
fun CompressionProgressScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier, onCancel: () -> Unit) {
  val current = state.batch?.items?.firstOrNull { it.status == ItemStatus.RUNNING }?.image
    ?: state.selectedImages.getOrNull(state.progress.completed)
  AdaptiveScreen(modifier, primary = { wide ->
    if (current != null) Photo(current.uri, current.displayName,
      if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(260.dp))
  }, detail = {
    Column(verticalArrangement = Arrangement.spacedBy(Space.section)) {
      PageTitle(stringResource(R.string.processing_title), stringResource(R.string.processing_body))
      SectionCard {
        Text(pluralStringResource(R.plurals.processing_count, state.progress.total, state.progress.completed, state.progress.total), style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { state.progress.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        current?.let { Text(stringResource(R.string.processing_item, it.displayName), style = MaterialTheme.typography.bodyMedium) }
      }
      ActionButton(stringResource(if (state.isCancelling) R.string.cancelling else R.string.cancel_compression), onCancel,
        Modifier.fillMaxWidth(), R.drawable.ic_close, enabled = !state.isCancelling, secondary = true)
      Text(stringResource(R.string.cancel_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      PrivacyNote()
    }
  })
}

@Composable
private fun BatchSizes(before: Long, after: Long) {
  Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
    Text(stringResource(R.string.batch_original_size, before.toReadableSize()), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.batch_output_size, after.toReadableSize()), style = MaterialTheme.typography.titleMedium)
  }
}

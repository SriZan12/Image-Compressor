package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.theme.LocalStatusColors
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultsScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier, onSaveAll: () -> Unit,
  onShareAll: () -> Unit, onCompare: (CompressedImage) -> Unit, onStartOver: () -> Unit, onCancel: () -> Unit,
  onRetry: () -> Unit, onAdjustSize: (() -> Unit)? = null) {
  if (state.isCompressing) { CompressionProgressScreen(state, modifier, onCancel); return }
  val images = state.compressedImages
  val before = if (images.all { it.original.sizeBytes > 0 }) images.sumOf { it.original.sizeBytes } else 0L
  val after = images.sumOf { it.compressedSizeBytes }
  val retry = state.batch?.items.orEmpty().filter { it.canRetry }
  val needsReview = retry.isNotEmpty() || images.any { it.targetSizeStatus == TargetSizeStatus.UNMET }
  val reviewTitle = when {
    state.batch?.status == BatchStatus.CANCELLED -> R.string.results_cancelled
    state.batch?.status == BatchStatus.INTERRUPTED -> R.string.results_interrupted
    else -> R.string.results_review
  }
  AdaptiveActionScreen(modifier, hasActions = images.isNotEmpty(), primary = { wide ->
    if (images.isEmpty()) EmptyState(stringResource(R.string.no_results), stringResource(R.string.no_results_body))
    else Column(if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
      if (!wide) {
        if (needsReview) Text(stringResource(reviewTitle), style = MaterialTheme.typography.titleMedium)
        SavingsSummary(before, after, images.size)
        if (images.size == 1) CompressionNotices(images.first())
      }
      if (images.size > 1) Text(stringResource(R.string.previewing_photo, images.first().original.displayName),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Photo(images.first().outputUri, stringResource(R.string.compressed),
        if (wide) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(280.dp))
      TextButton({ onCompare(images.first()) }, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
        AppIcon(R.drawable.ic_compare)
        Spacer(Modifier.width(Space.sm))
        Text(stringResource(R.string.compare_original))
      }
    }
  }, detail = { wide, inlineActions ->
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
      if (wide || images.isEmpty()) {
        if (needsReview) Text(stringResource(reviewTitle), style = MaterialTheme.typography.titleMedium)
        if (images.isNotEmpty()) {
          SavingsSummary(before, after, images.size)
          if (images.size == 1) CompressionNotices(images.first())
        }
      }
      if (images.isNotEmpty()) {
        if (state.batch != null && (state.batch.items.size > 1 || retry.isNotEmpty()))
          Text(pluralStringResource(R.plurals.finished_count, state.batch.items.size, images.size, state.batch.items.size),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        inlineActions()
        if (images.any { it.savedUri != null }) Text(stringResource(R.string.saved_location),
          style = MaterialTheme.typography.bodyMedium, color = LocalStatusColors.current.success)
        else Text(stringResource(R.string.retained_hint), style = MaterialTheme.typography.bodySmall,
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
      FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (onAdjustSize != null && (images.isNotEmpty() || state.selectedImages.isNotEmpty())) TextButton(onAdjustSize, enabled = !state.isSaving) {
          Text(stringResource(R.string.change_size))
        }
        TextButton(onStartOver, enabled = !state.isSaving) { Text(stringResource(R.string.new_photos)) }
      }
    }
  }, actions = {
    if (images.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
      val overLimit = images.count { it.targetSizeStatus == TargetSizeStatus.UNMET }
      if (overLimit > 0 && images.size > 1) Text(pluralStringResource(R.plurals.batch_over_limit, overLimit, overLimit),
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
      SaveShareActions(images.size, images.all { it.savedUri != null }, state.isSaving, onSaveAll, onShareAll)
    }
  })
}

@Composable
fun SavingsSummary(before: Long, after: Long, count: Int = 1) {
  Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
    Text(stringResource(if (count > 1) R.string.total_size else R.string.new_size), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(after.toReadableSize(), style = MaterialTheme.typography.displayMedium)
    Text(describeSizeChange(before, after), style = MaterialTheme.typography.titleMedium,
      color = if (before > after) LocalStatusColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant)
    if (before > 0) Text(stringResource(R.string.previous_size, before.toReadableSize()),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
  var expanded by rememberSaveable(image.historyId) { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
    TextButton({ expanded = !expanded }) {
      Text(stringResource(R.string.image_details))
      Spacer(Modifier.width(Space.sm))
      AppIcon(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
    }
    if (expanded) {
      Text(image.original.displayName, style = MaterialTheme.typography.bodyMedium)
      Text("${image.width} × ${image.height} · ${image.format.label}", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
      Text(describeSizeChange(image.original.sizeBytes, image.compressedSizeBytes), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (image.targetBytes != null) Text(pluralStringResource(R.plurals.editor_verified_bytes,
        image.compressedSizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), image.compressedSizeBytes),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (image.targetSizeStatus != TargetSizeStatus.NOT_REQUESTED) Text(stringResource(
        if (image.targetSizeStatus == TargetSizeStatus.MET) R.string.within_target else R.string.review_target,
        checkNotNull(image.targetBytes).toReadableSize()), style = MaterialTheme.typography.bodyMedium,
        color = if (image.targetSizeStatus == TargetSizeStatus.MET) LocalStatusColors.current.success else MaterialTheme.colorScheme.onSurface)
      CompressionNotices(image, showTarget = false)
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
        Text(pluralStringResource(R.plurals.processing_count, state.progress.total, state.progress.completed, state.progress.total),
          style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        LinearProgressIndicator(progress = { state.progress.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        current?.let { Text(stringResource(R.string.processing_item, it.displayName), style = MaterialTheme.typography.bodyMedium) }
      }
      ActionButton(stringResource(if (state.isCancelling) R.string.cancelling else R.string.cancel_compression), onCancel,
        Modifier.fillMaxWidth(), R.drawable.ic_close, enabled = !state.isCancelling, secondary = true)
      Text(stringResource(R.string.cancel_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  })
}

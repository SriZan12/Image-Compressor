package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.theme.LocalStatusColors
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.*
import kotlin.math.roundToInt

/** Stateless result presentation plus local disclosure/entry drafts. No compression or storage work. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
  state: PhotoEditorState,
  modifier: Modifier = Modifier,
  onSettingsChange: (CompressionSettings) -> Unit,
  onChangePhoto: () -> Unit,
  onPrepare: (() -> Unit)? = null,
  onSave: (() -> Unit)? = null,
  onShare: (() -> Unit)? = null,
  onCompare: (() -> Unit)? = null,
  onRetry: (() -> Unit)? = null,
  animatePlaceholders: Boolean = true,
  onTargetDraftValidityChange: (Boolean) -> Unit = {},
  photoSelector: @Composable () -> Unit = {},
) {
  val settings = state.settings
  val finishEditing = finishNumericEntry()
  val limits = remember { listOf(100_000L, 200_000L, 500_000L, 1_000_000L) }
  var advanced by rememberSaveable { mutableStateOf(false) }
  var custom by rememberSaveable { mutableStateOf(settings.targetBytes != null && settings.targetBytes !in limits) }
  var unit by rememberSaveable { mutableStateOf(TargetUnit.MB) }
  var target by rememberSaveable { mutableStateOf(targetText(settings.targetBytes ?: 500_000, unit)) }
  val targetValid = settings.targetBytes == null || !custom || parseTargetBytes(target, unit) != null
  val reportTargetValidity by rememberUpdatedState(onTargetDraftValidityChange)
  LaunchedEffect(targetValid) { reportTargetValidity(targetValid) }
  DisposableEffect(Unit) { onDispose { reportTargetValidity(true) } }
  val loading = state.preview is EditorPreview.Loading
  val result = state.currentResult
  val preview = if (state.preview is EditorPreview.Ready && result == null) EditorPreview.Updating() else state.preview
  val customized = settings.copy(quality = 80, targetBytes = null) != CompressionSettings()
  val busy = state.isSaving || state.isSharing
  val controlsEnabled = !loading && !busy && state.original != null
  val sliderEnabled = controlsEnabled && settings.outputFormat != OutputFormat.PNG
  val sliderInteractions = remember { MutableInteractionSource() }
  val canExport = result != null && targetValid && state.dimensionsValid && !busy
  val limitMet = result?.let { settings.targetBytes == null || it.compressedSizeBytes <= checkNotNull(settings.targetBytes) } == true
  val action: @Composable () -> Unit = {
    if (preview is EditorPreview.Failure && onRetry != null) {
      ActionButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth(), R.drawable.ic_restart_alt,
        enabled = controlsEnabled && targetValid && state.dimensionsValid)
    } else if (onPrepare != null) {
      ActionButton(pluralStringResource(R.plurals.prepare_photos, state.photoCount, state.photoCount), { finishEditing(); onPrepare() }, Modifier.fillMaxWidth(),
        enabled = controlsEnabled && targetValid && state.dimensionsValid)
    } else {
      ResponsiveActions(primary = { ActionButton(stringResource(when {
        state.isSaving && !state.isSharing -> R.string.saving
        result?.savedUri != null -> R.string.saved
        result != null && !limitMet -> R.string.editor_save_anyway
        else -> R.string.save_gallery
      }), { finishEditing(); onSave?.invoke() }, it, R.drawable.ic_download,
        enabled = canExport && onSave != null && result.savedUri == null) }, secondary = {
        ActionButton(stringResource(if (state.isSharing) R.string.editor_preparing_share else R.string.share),
          { finishEditing(); onShare?.invoke() }, it, R.drawable.ic_share,
          secondary = true, enabled = canExport && onShare != null)
      })
    }
  }

  AdaptiveActionScreen(modifier, primary = { wide ->
    Column(if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
      val frame = (if (wide) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(240.dp))
        .clip(MaterialTheme.shapes.medium).testTag("editor-photo-frame")
      Box(frame) {
        if (loading) EditorPlaceholder(Modifier.fillMaxSize(), animatePlaceholders)
        else Photo(state.previewImage?.outputUri ?: state.original?.uri, stringResource(R.string.editor_photo_preview), Modifier.fillMaxSize(), shape = MaterialTheme.shapes.medium)
      }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
          EditorText(stringResource(R.string.editor_original_size, state.original?.sizeBytes?.toReadableSize() ?: "—"),
            MaterialTheme.typography.bodyMedium, loading, animatePlaceholders, Modifier.testTag("editor-original-size"))
          EditorText(state.original?.let { "${it.width} × ${it.height}" } ?: "— × —",
            MaterialTheme.typography.bodySmall, loading, animatePlaceholders, Modifier.testTag("editor-dimensions"))
          if (state.photoCount > 1) Text(stringResource(R.string.editor_sample_photo, state.photoIndex + 1, state.photoCount),
            style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onChangePhoto, enabled = !loading && !busy) { Text(stringResource(R.string.edit_selection)) }
      }
      photoSelector()
    }
  }, detail = { _, inlineActions ->
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
      val summaryPreview = when {
        !targetValid -> EditorPreview.Failure(stringResource(R.string.target_invalid))
        !state.dimensionsValid -> EditorPreview.Failure(stringResource(R.string.resize_error))
        else -> preview
      }
      EditorSizeSummary(state.copy(preview = summaryPreview), summaryPreview, loading, animatePlaceholders)
      state.exportError?.let { message ->
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
          Notice(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, error = true)
          if (onRetry != null) TextButton(onRetry, enabled = controlsEnabled && targetValid && state.dimensionsValid) {
            Text(stringResource(R.string.editor_update_preview))
          }
        }
      }
      if (result?.savedUri != null) Text(stringResource(R.string.saved_location),
        style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.success,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
      Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
          Text(stringResource(R.string.editor_smaller_file), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
          Text(stringResource(R.string.editor_more_detail), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End)
        }
        val description = stringResource(R.string.editor_reduction)
        Slider(settings.quality.toFloat(), { onSettingsChange(settings.copy(quality = it.roundToInt())) },
          valueRange = 10f..100f, enabled = sliderEnabled, interactionSource = sliderInteractions,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = description },
          // Material's painted thumb is 44dp tall; give the slider itself a full 48dp hit region.
          thumb = { Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            SliderDefaults.Thumb(interactionSource = sliderInteractions, enabled = sliderEnabled)
          } },
          track = { SliderDefaults.Track(it, enabled = sliderEnabled,
            modifier = Modifier.height(4.dp)) })
        if (settings.outputFormat == OutputFormat.PNG) Text(stringResource(R.string.png_quality_help),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (settings.targetBytes != null) Text(stringResource(R.string.editor_limit_stays_on),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(if (state.photoCount > 1) R.string.editor_limit_per_photo else R.string.size_limit),
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
          val clearDescription = stringResource(R.string.editor_clear_limit_description)
          if (settings.targetBytes != null) TextButton({
            finishEditing(); custom = false; onSettingsChange(settings.copy(targetBytes = null))
          }, modifier = Modifier.semantics { contentDescription = clearDescription }, enabled = controlsEnabled) { Text(stringResource(R.string.editor_clear_limit)) }
        }
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
          limits.forEach { bytes ->
            FilterChip(!custom && settings.targetBytes == bytes, {
              finishEditing(); custom = false
              unit = if (bytes < 1_000_000) TargetUnit.KB else TargetUnit.MB
              target = targetText(bytes, unit)
              onSettingsChange(settings.copy(targetBytes = bytes))
            }, enabled = controlsEnabled, shape = MaterialTheme.shapes.small,
              label = { Text(editorLimitLabel(bytes)) })
          }
          FilterChip(custom, {
            custom = true
            val bytes = settings.targetBytes ?: 500_000L
            unit = TargetUnit.MB; target = targetText(bytes, unit)
            onSettingsChange(settings.copy(targetBytes = bytes))
          }, enabled = controlsEnabled, shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.editor_custom_limit)) })
        }
        if (custom) {
          OutlinedTextField(target, { value ->
            target = value
            reportTargetValidity(parseTargetBytes(value, unit) != null)
            parseTargetBytes(value, unit)?.let { onSettingsChange(settings.copy(targetBytes = it)) }
          }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.size_limit)) }, singleLine = true,
            enabled = controlsEnabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { finishEditing() }), isError = !targetValid,
            supportingText = { Text(stringResource(if (targetValid) R.string.target_hint else R.string.target_invalid)) })
          Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TargetUnit.entries.forEach { option -> FilterChip(unit == option, {
              val bytes = parseTargetBytes(target, unit) ?: settings.targetBytes ?: 500_000L
              unit = option; target = targetText(bytes, option)
            }, enabled = controlsEnabled, label = { Text(option.label) }) }
          }
        }
      }
      if (result != null && onCompare != null) TextButton({ finishEditing(); onCompare() },
        Modifier.heightIn(min = 48.dp), enabled = canExport) {
        AppIcon(R.drawable.ic_compare); Spacer(Modifier.width(Space.sm)); Text(stringResource(R.string.compare_original))
      }
      val disclosure = stringResource(if (advanced) R.string.editor_expanded else R.string.editor_collapsed)
      TextButton({ advanced = !advanced }, Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = disclosure },
        enabled = controlsEnabled) {
        Text(stringResource(R.string.advanced), Modifier.weight(1f))
        AppIcon(if (advanced) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
      }
      if (advanced) AdvancedSettings(settings, state.dimensionsValid, onSettingsChange, enabled = controlsEnabled)
      if (customized && !advanced) {
        Text(stringResource(R.string.more_options_changed), style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ onSettingsChange(CompressionSettings(targetBytes = settings.targetBytes)) }, enabled = controlsEnabled) {
          Text(stringResource(R.string.use_recommended))
        }
      }
      if (!state.dimensionsValid && !advanced) Notice(stringResource(R.string.resize_error), error = true)
      if (result != null && !limitMet) Text(stringResource(R.string.target_unmet_body),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (result != null && result.adjustments.isNotEmpty()) CompressionNotices(result, showTarget = false)
      Text(stringResource(R.string.original_safe),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      inlineActions()
    }
  }, actions = action)
}

@Composable
private fun EditorSizeSummary(state: PhotoEditorState, preview: EditorPreview, loading: Boolean, animate: Boolean) {
  val result = state.currentResult
  Column(Modifier.fillMaxWidth().testTag("editor-size-summary"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
    Text(stringResource(R.string.new_size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val bytes = result?.compressedSizeBytes ?: (preview as? EditorPreview.Updating)?.estimatedBytes?.takeIf { it > 0 }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val before = state.original?.sizeBytes?.toReadableSize() ?: "—"
      val after = bytes?.toReadableSize() ?: "—"
      val contents: @Composable () -> Unit = {
        EditorText(before, MaterialTheme.typography.bodyLarge, loading, animate, Modifier.widthIn(min = 88.dp))
        AppIcon(R.drawable.ic_arrow_forward, Modifier.clearAndSetSemantics { })
        EditorText(after, MaterialTheme.typography.displayMedium, loading, animate, Modifier.widthIn(min = 140.dp))
      }
      if (LocalDensity.current.fontScale > 1.3f || maxWidth < 300.dp)
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) { contents() }
      else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) { contents() }
    }
    val message = when (preview) {
      EditorPreview.NotCalculated -> stringResource(R.string.editor_size_pending)
      EditorPreview.Loading -> stringResource(R.string.editor_loading)
      is EditorPreview.Updating -> stringResource(if (bytes != null) R.string.editor_estimate else R.string.editor_updating)
      is EditorPreview.Failure -> preview.message
      is EditorPreview.Ready -> when {
        result == null -> stringResource(R.string.editor_updating)
        state.settings.targetBytes == null -> describeSizeChange(state.original?.sizeBytes ?: 0, result.compressedSizeBytes)
        result.compressedSizeBytes <= checkNotNull(state.settings.targetBytes) -> stringResource(R.string.within_target, editorLimitLabel(state.settings.targetBytes))
        else -> stringResource(R.string.review_target, editorLimitLabel(state.settings.targetBytes))
      }
    }
    val fits = result != null && state.settings.targetBytes?.let { result.compressedSizeBytes <= it } == true
    // Reserve this line while updating so the slider does not jump under the user's finger.
    // The previous count is only a geometry reference; hidden text is removed from semantics.
    val referenceBytes = result?.compressedSizeBytes ?: state.previewImage?.compressedSizeBytes
      ?: state.original?.sizeBytes ?: 100_000_000L
    EditorText(pluralStringResource(R.plurals.editor_verified_bytes,
      referenceBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), referenceBytes),
      MaterialTheme.typography.bodySmall, loading, animate,
      if (result == null && !loading) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier)
    Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
      if (fits) AppIcon(R.drawable.ic_check_circle)
      Text(message, style = MaterialTheme.typography.bodySmall,
        color = when { fits -> LocalStatusColors.current.success; preview is EditorPreview.Failure -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant },
        modifier = Modifier.semantics { if (result != null || preview is EditorPreview.Failure) liveRegion = LiveRegionMode.Polite })
    }
  }
}

@Composable
private fun EditorText(text: String, style: TextStyle, loading: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
  Box(modifier) {
    Text(text, style = style, modifier = if (loading) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier)
    if (loading) EditorPlaceholder(Modifier.matchParentSize().clip(RoundedCornerShape(4.dp)), animate)
  }
}

internal fun editorLimitLabel(bytes: Long): String {
  if (bytes < 1_000) return "$bytes B"
  val unit = if (bytes < 1_000_000) TargetUnit.KB else TargetUnit.MB
  return "${targetText(bytes, unit)} ${unit.label}"
}

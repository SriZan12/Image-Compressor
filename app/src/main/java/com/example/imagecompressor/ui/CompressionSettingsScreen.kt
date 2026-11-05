package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.example.imagecompressor.ui.state.PhotoEditorState
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.ui.state.EditorAction
import com.example.imagecompressor.util.compression.CompressionPolicy
import com.example.imagecompressor.util.compression.PixelSize
import kotlin.math.roundToInt

@Composable
fun presetLabel(preset: CompressionPreset?): String = stringResource(when (preset) {
  CompressionPreset.SMALLER -> R.string.preset_smaller
  CompressionPreset.BALANCED -> R.string.preset_balanced
  CompressionPreset.BEST_QUALITY -> R.string.preset_best
  CompressionPreset.SIZE_LIMIT -> R.string.preset_limit
  null -> R.string.custom_settings
})

@Composable
fun photoCount(count: Int): String = pluralStringResource(R.plurals.photo_count, count, count)

/** Single-photo actions use the current measured file; batches retain their explicit action. */
@Composable
fun CompressionSettingsScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier,
  onSettingsChange: (CompressionSettings) -> Unit, onCompress: () -> Unit, onEditSelection: () -> Unit,
  onRetryPreview: (() -> Unit)? = null, onTargetDraftValidityChange: (Boolean) -> Unit = {},
  animatePlaceholders: Boolean = true, onSave: (() -> Unit)? = null, onShare: (() -> Unit)? = null,
  onPreviewPhoto: (SelectedImage) -> Unit = {}) {
  val settings = state.compressionSettings
  var targetDraftValid by remember { mutableStateOf(true) }
  var comparing by rememberSaveable(state.editorSource?.id, settings) { mutableStateOf(false) }
  val dimensionsValid = remember(settings, state.selectedImages) {
    state.selectedImages.all { image -> runCatching {
      CompressionPolicy.requestedSize(PixelSize(image.width, image.height), settings)
    }.isSuccess }
  }
  val editor = PhotoEditorState(
    original = state.editorSource, settings = settings,
    preview = if (state.isLoadingImages) EditorPreview.Loading else state.editorPreview,
    photoCount = state.selectedImages.size, dimensionsValid = dimensionsValid,
    photoIndex = state.selectedImages.indexOf(state.editorSource).coerceAtLeast(0),
    isSaving = state.isSaving, isSharing = state.editorAction == EditorAction.SHARE,
    exportError = state.editorExportError,
  )
  val result = editor.currentResult
  val canCompare = result != null && targetDraftValid && dimensionsValid &&
    !editor.isSaving && !editor.isSharing
  PhotoEditorScreen(editor, modifier, onSettingsChange, onEditSelection,
    onPrepare = onCompress.takeIf { state.selectedImages.size > 1 }, onSave = onSave, onShare = onShare,
    onCompare = { if (canCompare) comparing = true },
    onRetry = onRetryPreview,
    photoSelector = {
      if (state.selectedImages.size > 1) EditorPhotoSelector(state.selectedImages, state.editorSource?.id,
        enabled = !state.isLoadingImages && !state.isSaving && !state.isCompressing, onSelect = onPreviewPhoto)
    },
    animatePlaceholders = animatePlaceholders,
    onTargetDraftValidityChange = { valid -> targetDraftValid = valid; onTargetDraftValidityChange(valid) })
  if (canCompare && comparing) {
    EditorComparisonDialog(checkNotNull(result), onDismiss = { comparing = false })
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdvancedSettings(settings: CompressionSettings, dimensionsValid: Boolean,
  onChange: (CompressionSettings) -> Unit, enabled: Boolean = true) {
  val finishEditing = finishNumericEntry()
  SectionCard {
    if (settings.targetBytes != null) EditorSettingSwitch(stringResource(R.string.target_resize),
      stringResource(R.string.target_resize_body), settings.allowTargetResize, enabled) {
      onChange(settings.copy(allowTargetResize = it))
    }
    Text(stringResource(R.string.output_format), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
      OutputFormat.entries.forEach { format ->
        FilterChip(settings.outputFormat == format, { onChange(settings.copy(outputFormat = format)) }, enabled = enabled, label = { Text(format.label) })
      }
    }
    Text(stringResource(when (settings.outputFormat) { OutputFormat.JPEG -> R.string.jpeg_help; OutputFormat.PNG -> R.string.png_help; OutputFormat.WEBP -> R.string.webp_help }),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider()
    Text(stringResource(R.string.dimensions), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
      ResizeMode.entries.forEach { mode -> FilterChip(settings.resizeMode == mode, { onChange(settings.copy(resizeMode = mode)) },
        enabled = enabled, label = { Text(stringResource(when (mode) { ResizeMode.ORIGINAL -> R.string.keep_original; ResizeMode.PERCENTAGE -> R.string.percentage; ResizeMode.CUSTOM -> R.string.custom_size })) }) }
    }
    when (settings.resizeMode) {
      ResizeMode.ORIGINAL -> Unit
      ResizeMode.PERCENTAGE -> {
        Text(stringResource(R.string.percent_dimensions, settings.resizePercent))
        val dimensionLabel = stringResource(R.string.dimensions)
        Slider(settings.resizePercent.toFloat(), { onChange(settings.copy(resizePercent = it.roundToInt())) }, valueRange = 10f..100f, steps = 89,
          enabled = enabled, modifier = Modifier.semantics { contentDescription = dimensionLabel })
      }
      ResizeMode.CUSTOM -> {
        OutlinedTextField(settings.customWidth, { onChange(settings.copy(customWidth = it)) }, Modifier.fillMaxWidth(),
          label = { Text(stringResource(R.string.width_pixels)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { finishEditing() }), singleLine = true, isError = !dimensionsValid, enabled = enabled)
        OutlinedTextField(settings.customHeight, { onChange(settings.copy(customHeight = it)) }, Modifier.fillMaxWidth(),
          label = { Text(stringResource(R.string.height_pixels)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { finishEditing() }), singleLine = true, isError = !dimensionsValid, enabled = enabled)
        EditorSettingSwitch(stringResource(R.string.preserve_aspect), null, settings.preserveAspectRatio, enabled) { onChange(settings.copy(preserveAspectRatio = it)) }
        if (!dimensionsValid) Text(stringResource(R.string.resize_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
    }
    Text(stringResource(R.string.memory_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun SettingSwitch(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
  EditorSettingSwitch(title, body, checked, true, onChange)
}

@Composable
private fun EditorSettingSwitch(title: String, body: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked, onCheckedChange = null, enabled = enabled)
  }
}

@Composable
internal fun finishNumericEntry(): () -> Unit {
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  return { focus.clearFocus(); keyboard?.hide() }
}

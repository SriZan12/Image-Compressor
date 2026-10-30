package com.example.imagecompressor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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

@Composable
fun CompressionSettingsScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier,
  onSettingsChange: (CompressionSettings) -> Unit, onCompress: () -> Unit, onEditSelection: () -> Unit) {
  val settings = state.compressionSettings
  val finishEditing = finishNumericEntry()
  var advanced by rememberSaveable { mutableStateOf(settings.preset() == null) }
  var unit by rememberSaveable { mutableStateOf(TargetUnit.MB) }
  var target by rememberSaveable { mutableStateOf(targetText(settings.targetBytes ?: 1_000_000, unit)) }
  val targetValid = settings.targetBytes == null || parseTargetBytes(target, unit) != null
  val dimensionsValid = remember(settings, state.selectedImages) {
    state.selectedImages.all { image -> runCatching {
      CompressionPolicy.requestedSize(PixelSize(image.width, image.height), settings)
    }.isSuccess }
  }
  AdaptiveActionScreen(modifier, primary = { wide ->
    Column(if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
      val image = state.selectedImages.firstOrNull()
      if (image != null) Photo(image.uri, image.displayName,
        if (wide) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(240.dp))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(photoCount(state.selectedImages.size), style = MaterialTheme.typography.titleMedium)
          Text(state.selectedImages.sumOf { it.sizeBytes }.toReadableSize(), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onEditSelection, enabled = !state.isLoadingImages) { Text(stringResource(R.string.edit_selection)) }
      }
    }
  }, detail = { wide, inlineActions ->
    Column(verticalArrangement = Arrangement.spacedBy(Space.section)) {
      if (wide) PageTitle(stringResource(R.string.choose_size), stringResource(R.string.settings_body))
      else Text(stringResource(R.string.settings_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      BoxWithConstraints {
        val columns = if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f) 1 else 2
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
          CompressionPreset.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
              row.forEach { preset ->
                PresetCard(preset, settings.preset() == preset, Modifier.weight(1f)) {
                  val next = preset.settings()
                  if (next.targetBytes != null) { unit = TargetUnit.MB; target = "1" }
                  onSettingsChange(next)
                }
              }
            }
          }
        }
      }
      if (settings.preset() == null) StatusPill(stringResource(R.string.custom_settings), icon = R.drawable.ic_tune)
      Text(stringResource(R.string.preset_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (settings.targetBytes != null) SectionCard {
        Text(stringResource(R.string.size_limit), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(target, { value ->
          target = value
          parseTargetBytes(value, unit)?.let { onSettingsChange(settings.copy(targetBytes = it)) }
        }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.size_limit)) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { finishEditing() }), isError = !targetValid,
          supportingText = { Text(stringResource(if (targetValid) R.string.target_hint else R.string.target_invalid)) })
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
          TargetUnit.entries.forEach { option ->
            FilterChip(unit == option, onClick = {
              // Preserve the limit when switching units, including any valid uncommitted text.
              val bytes = parseTargetBytes(target, unit) ?: settings.targetBytes
              unit = option
              target = targetText(bytes, option)
            }, label = { Text(option.label) })
          }
        }
        Text(stringResource(R.string.decimal_units), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSwitch(stringResource(R.string.target_resize), stringResource(R.string.target_resize_body), settings.allowTargetResize) {
          onSettingsChange(settings.copy(allowTargetResize = it))
        }
      }
      Surface(onClick = { advanced = !advanced }, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
          AppIcon(R.drawable.ic_tune)
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.advanced), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.advanced_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          AppIcon(if (advanced) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
            description = stringResource(if (advanced) R.string.advanced_close else R.string.advanced_open))
        }
      }
      if (advanced) AdvancedSettings(settings, dimensionsValid, onSettingsChange)
      if (!dimensionsValid && !advanced) Notice(stringResource(R.string.resize_error), error = true)
      inlineActions()
      PrivacyNote()
    }
  }, actions = {
    ActionButton(stringResource(R.string.compress_photos), onCompress, Modifier.fillMaxWidth(), R.drawable.ic_compress,
      enabled = targetValid && dimensionsValid && state.selectedImages.isNotEmpty() && !state.isLoadingImages && !state.isCompressing)
  })
}

@Composable
private fun PresetCard(preset: CompressionPreset, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  Surface(modifier.selectable(selected, onClick = onClick, role = Role.RadioButton), shape = MaterialTheme.shapes.large,
    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
    Column(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(presetLabel(preset), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (selected) AppIcon(R.drawable.ic_check_circle)
      }
      Text(stringResource(when (preset) {
        CompressionPreset.SMALLER -> R.string.preset_smaller_body
        CompressionPreset.BALANCED -> R.string.preset_balanced_body
        CompressionPreset.BEST_QUALITY -> R.string.preset_best_body
        CompressionPreset.SIZE_LIMIT -> R.string.preset_limit_body
      }), style = MaterialTheme.typography.bodySmall)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSettings(settings: CompressionSettings, dimensionsValid: Boolean, onChange: (CompressionSettings) -> Unit) {
  val finishEditing = finishNumericEntry()
  SectionCard {
    Text(stringResource(R.string.output_format), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
      OutputFormat.entries.forEach { format ->
        FilterChip(settings.outputFormat == format, { onChange(settings.copy(outputFormat = format)) }, label = { Text(format.label) })
      }
    }
    Text(stringResource(when (settings.outputFormat) { OutputFormat.JPEG -> R.string.jpeg_help; OutputFormat.PNG -> R.string.png_help; OutputFormat.WEBP -> R.string.webp_help }),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider()
    Text("${stringResource(R.string.quality)} · ${settings.quality}", style = MaterialTheme.typography.titleMedium)
    val qualityLabel = stringResource(R.string.quality)
    Slider(settings.quality.toFloat(), { onChange(settings.copy(quality = it.roundToInt())) }, valueRange = 10f..100f,
      steps = 89, enabled = settings.outputFormat != OutputFormat.PNG, modifier = Modifier.semantics { contentDescription = qualityLabel })
    Text(stringResource(if (settings.outputFormat == OutputFormat.PNG) R.string.png_quality_help else R.string.quality_help),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider()
    Text(stringResource(R.string.dimensions), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
      ResizeMode.entries.forEach { mode -> FilterChip(settings.resizeMode == mode, { onChange(settings.copy(resizeMode = mode)) },
        label = { Text(stringResource(when (mode) { ResizeMode.ORIGINAL -> R.string.keep_original; ResizeMode.PERCENTAGE -> R.string.percentage; ResizeMode.CUSTOM -> R.string.custom_size })) }) }
    }
    when (settings.resizeMode) {
      ResizeMode.ORIGINAL -> Unit
      ResizeMode.PERCENTAGE -> {
        Text(stringResource(R.string.percent_dimensions, settings.resizePercent))
        val dimensionLabel = stringResource(R.string.dimensions)
        Slider(settings.resizePercent.toFloat(), { onChange(settings.copy(resizePercent = it.roundToInt())) }, valueRange = 10f..100f, steps = 89, modifier = Modifier.semantics { contentDescription = dimensionLabel })
      }
      ResizeMode.CUSTOM -> {
        OutlinedTextField(settings.customWidth, { onChange(settings.copy(customWidth = it)) }, Modifier.fillMaxWidth(),
          label = { Text(stringResource(R.string.width_pixels)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { finishEditing() }), singleLine = true, isError = !dimensionsValid)
        OutlinedTextField(settings.customHeight, { onChange(settings.copy(customHeight = it)) }, Modifier.fillMaxWidth(),
          label = { Text(stringResource(R.string.height_pixels)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { finishEditing() }), singleLine = true, isError = !dimensionsValid)
        SettingSwitch(stringResource(R.string.preserve_aspect), null, settings.preserveAspectRatio) { onChange(settings.copy(preserveAspectRatio = it)) }
        if (!dimensionsValid) Text(stringResource(R.string.resize_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
    }
    Text(stringResource(R.string.memory_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun SettingSwitch(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
    horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked, onCheckedChange = null)
  }
}

@Composable
private fun finishNumericEntry(): () -> Unit {
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  return { focus.clearFocus(); keyboard?.hide() }
}

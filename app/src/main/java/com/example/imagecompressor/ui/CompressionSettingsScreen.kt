package com.example.imagecompressor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.ResizeMode
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.ui.components.BottomActionBar
import com.example.imagecompressor.ui.components.FigmaCard
import com.example.imagecompressor.ui.components.FigmaChip
import com.example.imagecompressor.ui.components.FigmaPrimaryButton
import com.example.imagecompressor.ui.components.FigmaTextField
import com.example.imagecompressor.ui.components.PrivacyBadge
import com.example.imagecompressor.ui.components.SectionCard
import com.example.imagecompressor.ui.components.roundToStep
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun CompressionSettingsScreen(
    state: ImageCompressorUiState,
    modifier: Modifier = Modifier,
    viewModel: ImageCompressorViewModel,
) {
  val settings = state.compressionSettings
  Box(modifier = modifier.fillMaxSize().background(FigmaUi.Background)) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 128.dp),
      verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
      item {
        PrivacyBadge(text = "Processed locally on your device")
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          state.selectedImages.take(3).forEach { image ->
            AsyncImage(
              model = image.uri,
              contentDescription = image.displayName,
              modifier =
                Modifier
                  .weight(1f)
                  .height(84.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(FigmaUi.Surface)
                  .border(1.dp, FigmaUi.Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
              contentScale = ContentScale.Crop,
            )
          }
          val remaining = (state.selectedImages.size - 3).coerceAtLeast(0)
          if (state.selectedImages.size < 3) {
            repeat(3 - state.selectedImages.size) {
              Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(12.dp)).background(FigmaUi.Surface))
            }
          }
          Box(
            modifier =
              Modifier
                .weight(1f)
                .height(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE6E8EE))
                .border(1.dp, FigmaUi.Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Text("+$remaining", color = FigmaUi.Body, fontSize = 16.sp, lineHeight = 24.sp)
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Quality", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
            Text("${settings.quality}%", color = FigmaUi.Primary, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
          }
          Slider(
            value = settings.quality.toFloat(),
            onValueChange = { viewModel.updateQuality(it.roundToStep()) },
            valueRange = 10f..100f,
            steps = 8,
            colors =
              SliderDefaults.colors(
                thumbColor = FigmaUi.Primary,
                activeTrackColor = FigmaUi.Primary,
                inactiveTrackColor = Color(0xFFE1E2E8),
              ),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FigmaChip(
              "Small Size",
              selected = settings.quality <= 50,
              onClick = { viewModel.updateQuality(50) })
            FigmaChip(
              "Balanced",
              selected = settings.quality in 51..85,
              onClick = { viewModel.updateQuality(80) })
            FigmaChip(
              "High Quality",
              selected = settings.quality > 85,
              onClick = { viewModel.updateQuality(95) })
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Target Size", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(null to "Off", 100L * 1024 to "100 KB", 500L * 1024 to "500 KB", 1024L * 1024 to "1 MB")
              .forEach { (bytes, label) ->
                FigmaChip(
                  label,
                  selected = settings.targetBytes == bytes,
                  onClick = { viewModel.updateTargetBytes(bytes) })
              }
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("Resize (Optional)", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            FigmaTextField(
              value = settings.customWidth.ifBlank { if (settings.resizeMode == ResizeMode.CUSTOM) "" else "Original" },
              label = "Width (px)",
              onValueChange = {
                viewModel.updateResizeMode(ResizeMode.CUSTOM)
                viewModel.updateCustomWidth(it)
              },
              modifier = Modifier.weight(1f),
            )
            FigmaTextField(
              value = settings.customHeight.ifBlank { if (settings.resizeMode == ResizeMode.CUSTOM) "" else "Original" },
              label = "Height (px)",
              onValueChange = {
                viewModel.updateResizeMode(ResizeMode.CUSTOM)
                viewModel.updateCustomHeight(it)
              },
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 75, 100).forEach { percent ->
              FigmaChip(
                "$percent%",
                selected = settings.resizeMode == ResizeMode.PERCENTAGE && settings.resizePercent == percent,
                onClick = {
                  viewModel.updateResizeMode(ResizeMode.PERCENTAGE)
                  viewModel.updateResizePercent(percent)
                },
              )
            }
            FigmaChip(
              "Original",
              selected = settings.resizeMode == ResizeMode.ORIGINAL,
              onClick = { viewModel.updateResizeMode(ResizeMode.ORIGINAL) })
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("Output Format", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
          Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FigmaUi.Surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
          ) {
            OutputFormat.entries.forEach { format ->
              Box(
                modifier =
                  Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (settings.outputFormat == format) Color.White else Color.Transparent)
                    .clickable { viewModel.updateOutputFormat(format) },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  format.label,
                  color = if (settings.outputFormat == format) FigmaUi.Ink else FigmaUi.Body,
                  fontSize = 14.sp,
                  lineHeight = 20.sp,
                  fontWeight = FontWeight.Medium,
                )
              }
            }
          }
        }
      }

      item {
        FigmaCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                "Preserve Aspect Ratio",
                color = FigmaUi.Ink,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium
              )
              Text(
                "Avoid stretching custom dimensions.",
                color = FigmaUi.Body,
                fontSize = 14.sp,
                lineHeight = 20.sp
              )
            }
            Switch(
              checked = settings.preserveAspectRatio,
              onCheckedChange = viewModel::updatePreserveAspectRatio,
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = FigmaUi.Primary,
                  uncheckedTrackColor = FigmaUi.Border,
                ),
            )
          }
        }
      }
    }

    BottomActionBar {
      FigmaPrimaryButton(
        text = "Compress ${state.selectedImages.size} Image${if (state.selectedImages.size == 1) "" else "s"}",
        onClick = viewModel::compressSelectedImages,
      )
    }
  }
}

@Composable
fun SettingsQualityCard(settings: CompressionSettings, onQualityChange: (Int) -> Unit) {
  SectionCard {
    Text("Quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("${settings.quality}% quality")
    Slider(
      value = settings.quality.toFloat(),
      onValueChange = { onQualityChange(it.roundToStep()) },
      valueRange = 10f..100f,
      steps = 8,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Smaller File", style = MaterialTheme.typography.labelMedium)
      Text("Better Quality", style = MaterialTheme.typography.labelMedium)
    }
  }
}

@Composable
fun SettingsTargetCard(settings: CompressionSettings, onTargetChange: (Long?) -> Unit) {
  SectionCard {
    Text(
      "Target file size",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
    Text("Optional. The app will try to get close to your chosen size.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(
        null to "Off",
        100L * 1024 to "100 KB",
        500L * 1024 to "500 KB",
        1024L * 1024 to "1 MB"
      )
        .forEach { (bytes, label) ->
          FilterChip(
            selected = settings.targetBytes == bytes,
            onClick = { onTargetChange(bytes) },
            label = { Text(label) })
        }
    }
  }
}

@Composable
fun SettingsResizeCard(
  settings: CompressionSettings,
  onResizeModeChange: (ResizeMode) -> Unit,
  onResizePercentChange: (Int) -> Unit,
  onCustomWidthChange: (String) -> Unit,
  onCustomHeightChange: (String) -> Unit,
  onPreserveChange: (Boolean) -> Unit,
) {
  SectionCard {
    Text("Resize", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    ResizeMode.entries.forEach { mode ->
      FilterChip(
        selected = settings.resizeMode == mode,
        onClick = { onResizeModeChange(mode) },
        label = { Text(mode.label) },
      )
    }
    when (settings.resizeMode) {
      ResizeMode.ORIGINAL -> Text("Keeps the original dimensions when memory allows.")
      ResizeMode.PERCENTAGE -> {
        Text("${settings.resizePercent}% of original width and height")
        Slider(
          value = settings.resizePercent.toFloat(),
          onValueChange = { onResizePercentChange(it.roundToStep()) },
          valueRange = 10f..100f,
          steps = 8,
        )
      }

      ResizeMode.CUSTOM -> {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = settings.customWidth,
            onValueChange = onCustomWidthChange,
            modifier = Modifier.weight(1f),
            label = { Text("Width") },
            singleLine = true,
          )
          OutlinedTextField(
            value = settings.customHeight,
            onValueChange = onCustomHeightChange,
            modifier = Modifier.weight(1f),
            label = { Text("Height") },
            singleLine = true,
          )
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text("Preserve aspect ratio")
      Switch(checked = settings.preserveAspectRatio, onCheckedChange = onPreserveChange)
    }
  }
}

@Composable
fun SettingsFormatCard(settings: CompressionSettings, onFormatChange: (OutputFormat) -> Unit) {
  SectionCard {
    Text(
      "Convert format",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutputFormat.entries.forEach { format ->
        FilterChip(
          selected = settings.outputFormat == format,
          onClick = { onFormatChange(format) },
          label = { Text(format.label) },
        )
      }
    }
    if (settings.outputFormat == OutputFormat.PNG) {
      Text("PNG keeps sharp edges and transparency. Its quality slider has less effect on file size.")
    }
  }
}

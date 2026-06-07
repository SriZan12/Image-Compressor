package com.example.imagecompressor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.ui.components.EmptyState
import com.example.imagecompressor.ui.components.FigmaCard
import com.example.imagecompressor.ui.components.FigmaPrimaryButton
import com.example.imagecompressor.ui.components.OutlinedPillButton
import com.example.imagecompressor.ui.components.StatCard
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun ImageCompareScreen(
    state: ImageCompressorUiState,
    modifier: Modifier = Modifier,
    onSave: (CompressedImage) -> Unit,
    onShare: (CompressedImage) -> Unit,
) {
  val image = state.compareImage
  if (image == null) {
    EmptyState(
      title = "No image selected",
      body = "Open a compressed result to compare it.",
      modifier = modifier.fillMaxSize(),
    )
    return
  }

  LazyColumn(
    modifier = modifier.fillMaxSize().background(FigmaUi.Background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      FigmaCard(shape = RoundedCornerShape(28.dp), padding = 20.dp) {
        Text(
          image.original.displayName,
          color = FigmaUi.Ink,
          fontSize = 22.sp,
          lineHeight = 28.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          "${image.reductionPercent}% smaller • ${image.format.label}",
          color = FigmaUi.Primary,
          fontSize = 16.sp,
          lineHeight = 24.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        ComparisonPanel(
          label = "Original",
          model = image.original.uri,
          size = image.original.sizeBytes.toReadableSize(),
          modifier = Modifier.weight(1f),
        )
        ComparisonPanel(
          label = "Compressed",
          model = image.outputUri,
          size = image.compressedSizeBytes.toReadableSize(),
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(
          "Before",
          image.original.sizeBytes.toReadableSize(),
          FigmaUi.SurfaceSoft,
          Modifier.weight(1f)
        )
        StatCard(
          "After",
          image.compressedSizeBytes.toReadableSize(),
          FigmaUi.Green,
          Modifier.weight(1f)
        )
      }
    }

    item {
      FigmaCard {
        Text(
          "Output Details",
          color = FigmaUi.Ink,
          fontSize = 16.sp,
          lineHeight = 24.sp,
          fontWeight = FontWeight.Medium
        )
        Text(
          "${image.width} x ${image.height}",
          color = FigmaUi.Body,
          fontSize = 14.sp,
          lineHeight = 20.sp
        )
        Text(
          image.outputFilePath,
          color = FigmaUi.Muted,
          fontSize = 12.sp,
          lineHeight = 16.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FigmaPrimaryButton(
          text = if (image.savedUri == null) "Save to Gallery" else "Saved",
          onClick = { onSave(image) },
          enabled = image.savedUri == null,
        )
        OutlinedPillButton(
          text = "Share",
          leading = "↗",
          onClick = { onShare(image) },
          modifier = Modifier.fillMaxWidth().height(56.dp)
        )
      }
    }
  }
}

@Composable
fun ComparisonPanel(label: String, model: Any, size: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .aspectRatio(0.78f)
          .clip(RoundedCornerShape(16.dp))
          .background(FigmaUi.Surface)
          .border(1.dp, FigmaUi.Border.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
    ) {
      AsyncImage(model = model, contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      Box(
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(9999.dp))
            .background(FigmaUi.Ink.copy(alpha = 0.82f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Text(label, color = FigmaUi.Background, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
      }
    }
    Text(size, color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
fun ImagePreview(model: Any, label: String, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    AsyncImage(
      model = model,
      contentDescription = label,
      modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
      contentScale = ContentScale.Crop,
    )
    Text(label, style = MaterialTheme.typography.labelMedium)
  }
}

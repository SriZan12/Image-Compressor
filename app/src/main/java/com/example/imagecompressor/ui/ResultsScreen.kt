package com.example.imagecompressor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.calculateReductionPercent
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.ui.components.EmptyState
import com.example.imagecompressor.ui.components.FigmaCard
import com.example.imagecompressor.ui.components.FigmaPrimaryButton
import com.example.imagecompressor.ui.components.OutlinedPillButton
import com.example.imagecompressor.ui.components.PrivacyBadge
import com.example.imagecompressor.ui.components.StatCard
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(
    state: ImageCompressorUiState,
    modifier: Modifier = Modifier,
    onSave: (CompressedImage) -> Unit,
    onSaveAll: () -> Unit,
    onShare: (CompressedImage) -> Unit,
    onShareAll: () -> Unit,
    onCompare: (CompressedImage) -> Unit,
    onStartOver: () -> Unit,
) {
  if (state.isCompressing) {
    CompressionProgressScreen(state = state, modifier = modifier)
    return
  }

  val totalBefore = state.compressedImages.sumOf { it.original.sizeBytes }
  val totalAfter = state.compressedImages.sumOf { it.compressedSizeBytes }
  val reduction = calculateReductionPercent(totalBefore, totalAfter)

  Column(modifier = modifier.fillMaxSize().background(FigmaUi.Background)) {
    if (state.compressedImages.isEmpty()) {
      EmptyState(
        title = "No results yet",
        body = "Compression did not produce any files. Go back and try a different image.",
        modifier = Modifier.weight(1f),
      )
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        item {
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
              modifier = Modifier.size(64.dp).clip(CircleShape).background(FigmaUi.Green),
              contentAlignment = Alignment.Center,
            ) {
              Text("✓", color = FigmaUi.GreenDark, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text("Compression Complete!", color = FigmaUi.Ink, fontSize = 24.sp, lineHeight = 32.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            PrivacyBadge(text = "On-Device Processing", compact = true)
          }
        }

        item {
          FigmaCard(shape = RoundedCornerShape(12.dp), padding = 24.dp) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  "Total Before",
                  color = FigmaUi.Body,
                  fontSize = 14.sp,
                  lineHeight = 20.sp,
                  fontWeight = FontWeight.Medium
                )
                Text(
                  totalBefore.toReadableSize(),
                  color = FigmaUi.Ink,
                  fontSize = 22.sp,
                  lineHeight = 28.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              Box(modifier = Modifier.height(40.dp).width(1.dp).background(FigmaUi.Border))
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  "Total After",
                  color = FigmaUi.Body,
                  fontSize = 14.sp,
                  lineHeight = 20.sp,
                  fontWeight = FontWeight.Medium
                )
                Text(
                  totalAfter.toReadableSize(),
                  color = FigmaUi.Primary,
                  fontSize = 22.sp,
                  lineHeight = 28.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
            Box(
              modifier =
                Modifier
                  .align(Alignment.CenterHorizontally)
                  .clip(RoundedCornerShape(9999.dp))
                  .background(FigmaUi.Primary)
                  .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
              Text(
                "$reduction% Saved",
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        item {
          Text("Summary", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
        }

        items(state.compressedImages, key = { it.outputFilePath }) { image ->
          ResultCard(
            image = image,
            onSave = { onSave(image) },
            onShare = { onShare(image) },
            onCompare = { onCompare(image) },
          )
        }

        item {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            FigmaPrimaryButton(
              text = if (state.isSaving) "Saving..." else "Save to Gallery",
              onClick = onSaveAll,
              enabled = !state.isSaving
            )
            OutlinedPillButton(
              text = "Share All",
              leading = "↗",
              onClick = onShareAll,
              modifier = Modifier.fillMaxWidth().height(56.dp)
            )
            TextButton(onClick = onStartOver, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
              Text("Compress More", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            }
          }
        }

        item {
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Text("Images were never uploaded.", color = FigmaUi.Muted, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
          }
        }
      }
    }
  }
}

@Composable
fun ResultCard(image: CompressedImage, onSave: () -> Unit, onShare: () -> Unit, onCompare: () -> Unit) {
  FigmaCard(shape = RoundedCornerShape(12.dp), padding = 12.dp) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier =
          Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FigmaUi.Surface)
            .clickable(onClick = onCompare),
      ) {
        AsyncImage(
          model = image.outputUri,
          contentDescription = image.original.displayName,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
        Box(
          modifier =
            Modifier
              .align(Alignment.TopEnd)
              .padding(8.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(FigmaUi.Ink.copy(alpha = 0.82f))
              .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
          Text(
            "${image.original.sizeBytes.toReadableSize()} → ${image.compressedSizeBytes.toReadableSize()}",
            color = FigmaUi.Background,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          image.original.displayName,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = FigmaUi.Ink,
          fontWeight = FontWeight.Bold
        )
        Text(
          "${image.reductionPercent}% smaller • ${image.width} x ${image.height} • ${image.format.label}",
          color = FigmaUi.Body,
          fontSize = 12.sp,
          lineHeight = 16.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = onCompare) { Text("Compare", color = FigmaUi.Primary) }
          TextButton(onClick = onShare) { Text("Share", color = FigmaUi.Primary) }
          TextButton(onClick = onSave, enabled = image.savedUri == null) {
            Text(
              if (image.savedUri == null) "Save" else "Saved",
              color = if (image.savedUri == null) FigmaUi.Primary else FigmaUi.Muted
            )
          }
        }
      }
    }
  }
}

@Composable
fun CompressionProgressScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier) {
  val fraction = state.progress.fraction.coerceIn(0f, 1f)
  val percent = (fraction * 100).roundToInt()
  val maxImageIndex = (state.selectedImages.size - 1).coerceAtLeast(0)
  val currentIndex = state.progress.completed.coerceIn(0, maxImageIndex)
  val currentCount = state.progress.completed.coerceIn(0, state.progress.total)
  val current = state.selectedImages.getOrNull(currentIndex)

  Box(modifier = modifier.fillMaxSize().background(FigmaUi.Background)) {
    Box(
      modifier =
        Modifier
          .align(Alignment.Center)
          .size(500.dp)
          .clip(CircleShape)
          .background(FigmaUi.Primary.copy(alpha = 0.08f))
    )
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 56.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(256.dp)) {
        CircularProgressIndicator(
          progress = { fraction },
          modifier = Modifier.size(256.dp),
          color = FigmaUi.Primary,
          trackColor = Color(0xFFD7E2EA),
          strokeWidth = 12.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("$percent%", color = FigmaUi.Primary, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-1.425f).sp)
          Text("COMPLETE", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
        }
      }
      Spacer(Modifier.height(48.dp))
      Text(
        "Compressing your images\nlocally...",
        color = FigmaUi.Ink,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        "Processing ${current?.displayName ?: "image"} ($currentCount of ${state.progress.total})",
        color = FigmaUi.Body,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(32.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(
          label = "Completed",
          value = "${state.progress.completed}/${state.progress.total}",
          tone = FigmaUi.Green,
          modifier = Modifier.weight(1f),
        )
        StatCard(
          label = "Status",
          value = "~working",
          tone = FigmaUi.SurfaceSoft,
          modifier = Modifier.weight(1f),
        )
      }
    }
    Box(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF2E3135))
          .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Text("Your images never leave your device.", color = Color(0xFFEFF0F7), fontSize = 14.sp, lineHeight = 20.sp)
    }
  }
}

package com.example.imagecompressor.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.calculateReductionPercent
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.ui.components.EmptyState
import com.example.imagecompressor.ui.components.FigmaCard
import com.example.imagecompressor.ui.components.OutlinedPillButton
import com.example.imagecompressor.ui.components.PrivacyBadge
import com.example.imagecompressor.ui.components.StatCard
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    state: ImageCompressorUiState,
    modifier: Modifier = Modifier,
    onDelete: (CompressionHistoryEntity) -> Unit,
    onShare: (CompressionHistoryEntity) -> Unit,
) {
  if (state.history.isEmpty()) {
    EmptyState(
      title = "No compression history",
      body = "Compressed images will appear here with their saved location.",
      modifier = modifier.fillMaxSize().background(FigmaUi.Background),
    )
    return
  }
  LazyColumn(
    modifier = modifier.fillMaxSize().background(FigmaUi.Background),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      PrivacyBadge(text = "Images were compressed locally")
    }
    items(state.history, key = { it.id }) { item ->
      HistoryCard(item = item, onDelete = { onDelete(item) }, onShare = { onShare(item) })
    }
  }
}

@Composable
fun HistoryCard(item: CompressionHistoryEntity, onDelete: () -> Unit, onShare: () -> Unit) {
  val reduction = calculateReductionPercent(item.originalSizeBytes, item.compressedSizeBytes)
  FigmaCard(shape = RoundedCornerShape(12.dp), padding = 16.dp) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          item.displayName,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = FigmaUi.Ink,
          fontWeight = FontWeight.Bold
        )
        Text(
          DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(item.createdAt)),
          color = FigmaUi.Body,
          fontSize = 12.sp,
          lineHeight = 16.sp
        )
      }
      Box(
        modifier = Modifier.clip(RoundedCornerShape(9999.dp)).background(FigmaUi.Green)
          .padding(horizontal = 10.dp, vertical = 4.dp)
      ) {
        Text(
          "$reduction%",
          color = FigmaUi.GreenText,
          fontSize = 12.sp,
          lineHeight = 16.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
      StatCard(
        "Before",
        item.originalSizeBytes.toReadableSize(),
        FigmaUi.SurfaceSoft,
        Modifier.weight(1f)
      )
      StatCard(
        "After",
        item.compressedSizeBytes.toReadableSize(),
        FigmaUi.Green,
        Modifier.weight(1f)
      )
    }
    Text(
      "${item.width} x ${item.height} • ${item.outputFormat}",
      color = FigmaUi.Body,
      fontSize = 14.sp,
      lineHeight = 20.sp
    )
    Text(
      item.savedPath ?: "Not saved to gallery",
      color = FigmaUi.Muted,
      fontSize = 12.sp,
      lineHeight = 16.sp,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedPillButton(
        text = "Share",
        leading = "↗",
        onClick = onShare,
        modifier = Modifier.weight(1f).height(44.dp)
      )
      TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
        Text(
          "Delete",
          color = FigmaUi.Body
        )
      }
    }
  }
}

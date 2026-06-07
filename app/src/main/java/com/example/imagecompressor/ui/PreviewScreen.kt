package com.example.imagecompressor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.ui.components.EmptyState
import com.example.imagecompressor.ui.components.FigmaPrimaryButton
import com.example.imagecompressor.ui.components.LoadingCard
import com.example.imagecompressor.ui.components.OutlinedPillButton
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun PreviewScreen(
    state: ImageCompressorUiState,
    modifier: Modifier = Modifier,
    onSelectOne: () -> Unit,
    onSelectMany: () -> Unit,
    onRemove: (SelectedImage) -> Unit,
    onContinue: () -> Unit,
) {
  Column(modifier = modifier.fillMaxSize().background(FigmaUi.Background).padding(horizontal = 16.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedPillButton(
        text = "Add One",
        leading = "+",
        onClick = onSelectOne,
        modifier = Modifier.weight(1f).height(48.dp)
      )
      OutlinedPillButton(
        text = "Add Multiple",
        leading = "▣",
        onClick = onSelectMany,
        modifier = Modifier.weight(1f).height(48.dp)
      )
    }
    if (state.isLoadingImages) {
      LoadingCard("Reading image details...")
    } else if (state.selectedImages.isEmpty()) {
      EmptyState(
        title = "No images selected",
        body = "Pick one image or choose several at once.",
        modifier = Modifier.weight(1f),
      )
    } else {
      Text(
        "${state.selectedImages.size} image${if (state.selectedImages.size == 1) "" else "s"} ready",
        color = FigmaUi.Ink,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
      )
      LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(state.selectedImages, key = { it.id }) { image ->
          SelectedImageCard(image = image, onRemove = { onRemove(image) })
        }
      }
      FigmaPrimaryButton(
        text = "Choose Compression Settings",
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      )
    }
  }
}

@Composable
fun SelectedImageCard(image: SelectedImage, onRemove: () -> Unit) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaUi.SurfaceSoft),
    border = BorderStroke(1.dp, FigmaUi.Border.copy(alpha = 0.35f)),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      AsyncImage(
        model = image.uri,
        contentDescription = image.displayName,
        modifier =
          Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(FigmaUi.Surface),
        contentScale = ContentScale.Crop,
      )
      Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(image.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = FigmaUi.Ink, fontWeight = FontWeight.Bold)
        Text(image.sizeBytes.toReadableSize(), color = FigmaUi.Body, fontSize = 12.sp, lineHeight = 16.sp)
        Text("${image.width} x ${image.height} • ${image.format}", color = FigmaUi.Body, fontSize = 12.sp, lineHeight = 16.sp)
        TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) {
          Text("Remove", color = FigmaUi.Primary)
        }
      }
    }
  }
}

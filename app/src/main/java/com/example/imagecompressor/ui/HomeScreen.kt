package com.example.imagecompressor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.ui.components.FigmaCard
import com.example.imagecompressor.ui.components.PrimaryButton
import com.example.imagecompressor.ui.components.PrivacyBadge
import com.example.imagecompressor.ui.components.StatCard
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun HomeScreen(
    state: ImageCompressorUiState,
    modifier: Modifier = Modifier,
    onSelectImages: () -> Unit,
    onReviewSelection: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val compressedCount = state.history.size
    val savedBytes = state.history.sumOf {
        (it.originalSizeBytes - it.compressedSizeBytes).coerceAtLeast(0)
    }
    val savedLabel = if (savedBytes == 0L) "0 MB" else savedBytes.toReadableSize()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            item {
                SelectImagesCard(
                    onClick = onSelectImages
                )
            }

            item {
                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    iconDrawable = R.drawable.space_saved,
                    label = "Space Saved",
                    value = savedLabel,
                    tone = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelStyle = MaterialTheme.typography.titleSmall,
                    valueStyle = MaterialTheme.typography.titleLarge,
                )

                Spacer(modifier = Modifier.height(16.dp))

                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    iconDrawable = R.drawable.image_compressed,
                    label = "Images Compressed",
                    value = compressedCount.toString(),
                    tone = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelStyle = MaterialTheme.typography.titleSmall,
                    valueStyle = MaterialTheme.typography.titleLarge,
                )
            }

            item {
                CompressionHistoryCard(
                    title = "Compression History",
                    imageDrawable = R.drawable.compression_history,
                    onClick = onOpenHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }

            if (state.selectedImages.isNotEmpty()) {
                item {
                    SelectedImagesCard(
                        count = state.selectedImages.size,
                        onReviewSelection = onReviewSelection
                    )
                }
            }

            item {
                EmptyHomeState()
            }

            item {
                PrivacyBadge(
                    text = "Your images stay on your device.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

    }
}

@Composable
fun CompressionHistoryCard(
    title: String,
    imageDrawable: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(border = CardDefaults.outlinedCardBorder(), shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(imageDrawable),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun SelectImagesCard(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp, max = 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AddCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )

            Text(
                text = "Select Images",
                color = Color.White,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "START PROCESSING",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.1.sp,
            )
        }
    }
}

@Composable
private fun SelectedImagesCard(
    count: Int,
    onReviewSelection: () -> Unit
) {
    FigmaCard {
        Text(
            text = "$count image${if (count == 1) "" else "s"} ready",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Your previous selection is waiting. Review it or choose a fresh batch.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        PrimaryButton(
            text = "Review Selected Images",
            onClick = onReviewSelection
        )
    }
}

@Composable
private fun EmptyHomeState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.figma_empty_home),
                contentDescription = null,
                modifier = Modifier
                    .size(108.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "No images yet",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = "Start by selecting some from your gallery.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
fun PreviewHomeScreen() {
    ImageCompressorTheme {
        HomeScreen(
            state = ImageCompressorUiState(screen = AppScreen.HOME),
            onSelectImages = {},
            onReviewSelection = {},
            onOpenHistory = {},
        )
    }

}

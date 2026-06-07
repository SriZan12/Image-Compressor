package com.example.imagecompressor.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.ui.components.FigmaCard
import com.example.imagecompressor.ui.components.FigmaPrimaryButton
import com.example.imagecompressor.ui.components.OutlinedPillButton
import com.example.imagecompressor.ui.components.PrivacyBadge
import com.example.imagecompressor.ui.components.StatCard
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
    val savedBytes =
        state.history.sumOf { (it.originalSizeBytes - it.compressedSizeBytes).coerceAtLeast(0) }
    val savedLabel = if (savedBytes == 0L) "0 MB" else savedBytes.toReadableSize()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(FigmaUi.Background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(205.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FigmaUi.PrimaryHero)
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                        .padding(32.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Compress\nyour first\nimage",
                        color = Color.White,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Reduce file size while\nkeeping high visual\nquality effortlessly.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.25.sp,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(112.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⇣", color = Color.White.copy(alpha = 0.35f), fontSize = 56.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(FigmaUi.Primary)
                            .shadow(10.dp, RoundedCornerShape(12.dp))
                            .clickable(onClick = onSelectImages),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("▣+", color = Color.White, fontSize = 34.sp, lineHeight = 40.sp)
                        Text("Select Images", color = Color.White, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "START PROCESSING",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.1.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        label = "Space Saved",
                        value = savedLabel,
                        tone = FigmaUi.Green,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Images Compressed",
                        value = compressedCount.toString(),
                        tone = Color(0xFFFFD9B5),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedPillButton(
                    text = "Compression History",
                    leading = "↺",
                    onClick = onOpenHistory,
                    modifier = Modifier.fillMaxWidth().height(82.dp),
                )
            }
        }

        if (state.selectedImages.isNotEmpty()) {
            item {
                FigmaCard {
                    Text(
                        "${state.selectedImages.size} image${if (state.selectedImages.size == 1) "" else "s"} ready",
                        color = FigmaUi.Ink,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Your previous selection is waiting. Review it or choose a fresh batch.",
                        color = FigmaUi.Body,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    FigmaPrimaryButton(text = "Review Selected Images", onClick = onReviewSelection)
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(192.dp)
                            .clip(CircleShape)
                            .background(FigmaUi.SurfaceSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.figma_empty_home),
                        contentDescription = null,
                        modifier = Modifier.size(128.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("No images yet", color = FigmaUi.Ink, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Start by selecting some from your gallery.",
                    color = FigmaUi.Body,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.25.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            PrivacyBadge(
                text = "Your images stay on your device.",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
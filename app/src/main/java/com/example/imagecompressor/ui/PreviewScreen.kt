package com.example.imagecompressor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.ui.components.EmptyState
import com.example.imagecompressor.ui.components.LoadingCard
import com.example.imagecompressor.ui.components.PrimaryButton
import com.example.imagecompressor.ui.components.PrivacyBadge
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun SelectedImageScreen(
    state: ImageCompressorUiState,
    onSelectMany: () -> Unit,
    onRemove: (SelectedImage) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrivacyBadge(
            text = "On-Device Processing",
            modifier = Modifier.padding(top = 20.dp, bottom = 24.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoadingImages -> {
                    LoadingCard("Reading image details...")
                }

                state.selectedImages.isEmpty() -> {
                    EmptyState(
                        title = "No images selected",
                        body = "Pick one image or choose several at once.",
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(
                            items = state.selectedImages,
                            key = { it.id }
                        ) { image ->
                            SelectedImageCard(
                                image = image,
                                onRemove = { onRemove(image) }
                            )
                        }

                        item {
                            AddMoreImageCard(
                                onClick = onSelectMany
                            )
                        }
                    }
                }
            }
        }

        PrimaryButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
fun SelectedImageCard(
    image: SelectedImage?,
    onRemove: () -> Unit
) {
    image?.let {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = FigmaUi.SurfaceSoft
            ),
            border = BorderStroke(
                width = 1.dp,
                color = FigmaUi.Border.copy(alpha = 0.35f)
            ),
        ) {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = image.uri,
                        contentDescription = image.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp
                                )
                            )
                            .background(FigmaUi.Surface),
                        contentScale = ContentScale.Crop,
                    )

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(all = 6.dp)
                            .align(Alignment.TopEnd),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(
                                0.9f
                            )
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove image",
                            modifier = Modifier.padding(all = 2.dp),
                            tint = Color.Black
                        )
                    }

                    Text(
                        text = image.sizeBytes.toReadableSize(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(FigmaUi.Primary)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Column(
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 14.dp
                    )
                ) {
                    Text(
                        text = image.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = FigmaUi.Ink,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${image.width}×${image.height}",
                        color = FigmaUi.Body,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.25.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMoreImageCard(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = FigmaUi.Border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(FigmaUi.Green),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = FigmaUi.GreenText,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Add More",
                color = FigmaUi.Body,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview
@Composable
fun PreviewSelectedImageCard() {
    ImageCompressorTheme() {
        SelectedImageCard(image = null, onRemove = {})
    }
}

@Preview
@Composable
fun PreviewSelectedImageScreen() {
    SelectedImageScreen(
        state = ImageCompressorUiState(screen = AppScreen.HOME),
        onSelectMany = {},
        onRemove = {},
    ) { }
}
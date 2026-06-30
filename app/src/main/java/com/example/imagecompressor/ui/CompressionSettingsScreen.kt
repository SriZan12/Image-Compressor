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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.imagecompressor.data.model.ResizeMode
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.ui.components.BottomActionBar
import com.example.imagecompressor.ui.components.FigmaCard
import com.example.imagecompressor.ui.components.FigmaChip
import com.example.imagecompressor.ui.components.FigmaTextField
import com.example.imagecompressor.ui.components.PrimaryButton
import com.example.imagecompressor.ui.components.PrivacyBadge
import com.example.imagecompressor.ui.components.roundToStep
import com.example.imagecompressor.ui.state.ImageCompressorUiState

private val SectionDivider = Color(0xFFD8569A) // matches the small pink divider tick in the design

@Composable
fun CompressionSettingsScreen(
    state: ImageCompressorUiState,
    modifier: Modifier = Modifier,
    viewModel: ImageCompressorViewModel,
) {
    val settings = state.compressionSettings

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FigmaUi.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 128.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    PrivacyBadge(text = "Processed locally on your device")
                }

                item {
                    SelectedImagesPreviewRow(images = state.selectedImages)
                }

                item { SectionTick() }

                item {
                    QualitySection(
                        quality = settings.quality,
                        onQualityChange = viewModel::updateQuality,
                    )
                }

                item { SectionTick() }

                item {
                    ResizeSection(
                        customWidth = settings.customWidth,
                        customHeight = settings.customHeight,
                        resizeMode = settings.resizeMode,
                        resizePercent = settings.resizePercent,
                        onCustomWidthChange = {
                            viewModel.updateResizeMode(ResizeMode.CUSTOM)
                            viewModel.updateCustomWidth(it)
                        },
                        onCustomHeightChange = {
                            viewModel.updateResizeMode(ResizeMode.CUSTOM)
                            viewModel.updateCustomHeight(it)
                        },
                        onPercentSelected = { percent ->
                            viewModel.updateResizeMode(ResizeMode.PERCENTAGE)
                            viewModel.updateResizePercent(percent)
                        },
                    )
                }

                item { SectionTick() }

                item {
                    OutputFormatSection(
                        selectedFormat = settings.outputFormat,
                        onFormatSelected = viewModel::updateOutputFormat,
                    )
                }

                item { SectionTick() }

                item {
                    StripMetadataCard(
                        checked = false,
                        onCheckedChange = {},
                    )
                }
            }
        }

        BottomActionBar {
            PrimaryButton(
                text = "Compress ${state.selectedImages.size} Image${if (state.selectedImages.size == 1) "" else "s"}",
                onClick = viewModel::compressSelectedImages,
            )
        }
    }
}

@Composable
private fun CompressionSettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = FigmaUi.Ink,
                modifier = Modifier.size(24.dp),
            )
        }

        Text(
            text = "Compression Settings",
            color = FigmaUi.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = "Privacy",
            tint = FigmaUi.Primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SelectedImagesPreviewRow(images: List<com.example.imagecompressor.data.model.SelectedImage>) {
    val visible = images.take(3)
    val remaining = (images.size - visible.size).coerceAtLeast(0)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        visible.forEach { image ->
            AsyncImage(
                model = image.uri,
                contentDescription = image.displayName,
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FigmaUi.Surface)
                    .border(1.dp, FigmaUi.Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        // Pad out empty slots so the row always shows 3 thumbnail-sized cells
        repeat((3 - visible.size).coerceAtLeast(0)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FigmaUi.Surface),
            )
        }

        if (remaining > 0) {
            Box(
                modifier = Modifier
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
}

@Composable
private fun SectionTick() {
    // The thin pink divider mark used between sections in the design
    Box(
        modifier = Modifier
            .width(32.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(SectionDivider),
    )
}

@Composable
private fun QualitySection(
    quality: Int,
    onQualityChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "Quality",
                color = FigmaUi.Ink,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "$quality%",
                color = FigmaUi.Primary,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = quality.toFloat(),
            onValueChange = { onQualityChange(it.roundToStep()) },
            valueRange = 10f..100f,
            steps = 8,
            colors = SliderDefaults.colors(
                thumbColor = FigmaUi.Primary,
                activeTrackColor = FigmaUi.Primary,
                inactiveTrackColor = Color(0xFFE1E2E8),
            ),
        )

        // Two rows of chips: presets wrap naturally onto a second line like the design
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FigmaChip("Small Size", selected = quality <= 50, onClick = { onQualityChange(50) })
            FigmaChip("Balanced", selected = quality in 51..85, onClick = { onQualityChange(80) })
            FigmaChip("High Quality", selected = quality > 85, onClick = { onQualityChange(95) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FigmaChip(
                "Custom",
                selected = false,
                onClick = { /* opens a custom quality input if needed */ })
        }
    }
}

@Composable
private fun ResizeSection(
    customWidth: String,
    customHeight: String,
    resizeMode: ResizeMode,
    resizePercent: Int,
    onCustomWidthChange: (String) -> Unit,
    onCustomHeightChange: (String) -> Unit,
    onPercentSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Resize (Optional)",
            color = FigmaUi.Ink,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FigmaTextField(
                    value = if (resizeMode == ResizeMode.CUSTOM) customWidth else "",
                    label = "Width (px)",
                    placeholder = "Original",
                    onValueChange = onCustomWidthChange,
                    modifier = Modifier.weight(1f),
                )
                FigmaTextField(
                    value = if (resizeMode == ResizeMode.CUSTOM) customHeight else "",
                    label = "Height (px)",
                    placeholder = "Original",
                    onValueChange = onCustomHeightChange,
                    modifier = Modifier.weight(1f),
                )
            }

            // Linked-dimensions indicator sitting on the seam between the two fields,
            // mirroring the chain-link icon shown in the design.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 12.dp)
                    .size(20.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = "Keep dimensions linked",
                    tint = FigmaUi.Body,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 75, 100).forEach { percent ->
                FigmaChip(
                    "$percent%",
                    selected = resizeMode == ResizeMode.PERCENTAGE && resizePercent == percent,
                    onClick = { onPercentSelected(percent) },
                )
            }
        }
    }
}

@Composable
private fun OutputFormatSection(
    selectedFormat: OutputFormat,
    onFormatSelected: (OutputFormat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Output Format",
            color = FigmaUi.Ink,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FigmaUi.Surface)
                .padding(4.dp),
        ) {
            OutputFormat.entries.forEach { format ->
                val selected = selectedFormat == format
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color.White else Color.Transparent)
                        .clickable { onFormatSelected(format) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        format.label,
                        color = if (selected) FigmaUi.Ink else FigmaUi.Body,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StripMetadataCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    FigmaCard(shape = RoundedCornerShape(20.dp), padding = 20.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = FigmaUi.Body,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Strip Metadata",
                        color = FigmaUi.Ink,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Location, camera info, etc.",
                        color = FigmaUi.Body,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FigmaUi.Primary,
                    uncheckedTrackColor = FigmaUi.Border,
                ),
            )
        }
    }
}
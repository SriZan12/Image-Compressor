package com.example.imagecompressor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.Space
import kotlin.math.roundToInt

/** Both images share fit, zoom, and pan. Only the original's screen-space mask moves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonViewer(image: CompressedImage, modifier: Modifier = Modifier, expanded: Boolean = false) {
  var fraction by rememberSaveable(image.historyId) { mutableFloatStateOf(.5f) }
  var zoom by rememberSaveable(image.historyId) { mutableFloatStateOf(1f) }
  var pan by remember(image.historyId) { mutableStateOf(Offset.Zero) }
  var size by remember { mutableStateOf(IntSize.Zero) }
  var originalMissing by remember(image.original.uri) { mutableStateOf(false) }
  var originalLoaded by remember(image.original.uri) { mutableStateOf(false) }
  var outputLoaded by remember(image.outputUri) { mutableStateOf(false) }
  var outputMissing by remember(image.outputUri) { mutableStateOf(false) }
  var originalPreviewKey by remember(image.original.uri) { mutableStateOf<MemoryCache.Key?>(null) }
  var outputPreviewKey by remember(image.outputUri) { mutableStateOf<MemoryCache.Key?>(null) }
  val originalRequest = rememberPreviewImageRequest(image.original.uri, size, originalPreviewKey)
  val outputRequest = rememberPreviewImageRequest(image.outputUri, size, outputPreviewKey)
  val loading = (!originalLoaded && !originalMissing) || (!outputLoaded && !outputMissing)
  val previewState = stringResource(if (loading) R.string.loading_preview else if (outputMissing) R.string.preview_unavailable else R.string.preview_ready)
  val checker = MaterialTheme.colorScheme.onSurface.copy(alpha = .04f)
  val line = MaterialTheme.colorScheme.onSurface
  val description = stringResource(R.string.compare_slider)
  val sliderInteractions = remember { MutableInteractionSource() }
  val position = stringResource(R.string.original_visible, (fraction * 100).roundToInt())
  fun constrain(value: Offset, scale: Float): Offset = Offset(
    value.x.coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2),
    value.y.coerceIn(-size.height * (scale - 1) / 2, size.height * (scale - 1) / 2))
  val transformState = rememberTransformableState { scale, delta, _ ->
    zoom = (zoom * scale).coerceIn(1f, 4f)
    pan = constrain(pan + delta, zoom)
  }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.md)) {
    val stage = if (expanded) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().height(320.dp)
    Box(stage.semantics(mergeDescendants = true) { stateDescription = previewState }.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
      .onSizeChanged { size = it; pan = constrain(pan, zoom) }
      // At fit, one-finger swipes belong to the page. Zoomed images can be panned.
      .transformable(transformState, canPan = { zoom > 1f }, enabled = outputLoaded), contentAlignment = Alignment.Center) {
      Canvas(Modifier.fillMaxSize()) {
        val cell = 16.dp.toPx()
        for (x in 0..(size.width / cell).toInt()) for (y in 0..(size.height / cell).toInt())
          if ((x + y) % 2 == 0) drawRect(checker, Offset(x * cell, y * cell), Size(cell, cell))
      }
      val transform = Modifier.fillMaxSize().graphicsLayer {
        scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
      }
      if (outputRequest != null) AsyncImage(outputRequest, stringResource(R.string.compressed), transform, contentScale = ContentScale.Fit,
        onLoading = { outputMissing = false; outputLoaded = false },
        onError = { outputMissing = true; outputLoaded = false }, onSuccess = {
          outputMissing = false; outputLoaded = true; outputPreviewKey = it.result.memoryCacheKey
        })
      // Keep the original request attached after an error so a new request can recover in place.
      Box(Modifier.fillMaxSize().drawWithContent {
        clipRect(right = this.size.width * fraction) { this@drawWithContent.drawContent() }
      }) {
        if (originalRequest != null) AsyncImage(originalRequest, stringResource(R.string.original), transform, contentScale = ContentScale.Fit,
          onLoading = { originalMissing = false; originalLoaded = false },
          onError = { originalMissing = true; originalLoaded = false }, onSuccess = {
            originalMissing = false; originalLoaded = true; originalPreviewKey = it.result.memoryCacheKey
          })
      }
      if (originalLoaded && outputLoaded) Canvas(Modifier.fillMaxSize()) {
        drawLine(line.copy(alpha = .85f), Offset(this.size.width * fraction, 0f),
          Offset(this.size.width * fraction, this.size.height), 2.dp.toPx())
      }
      if (loading) EditorPlaceholder(Modifier.matchParentSize())
      if (outputMissing) Text(stringResource(R.string.preview_unavailable), Modifier.padding(Space.page))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
          ComparisonSize(stringResource(R.string.original), image.original.sizeBytes)
          ComparisonSize(stringResource(R.string.compressed), image.compressedSizeBytes)
        }
      } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ComparisonSize(stringResource(R.string.original), image.original.sizeBytes)
        ComparisonSize(stringResource(R.string.compressed), image.compressedSizeBytes, alignEnd = true)
      }
    }
    Slider(fraction, { fraction = it }, enabled = originalLoaded && outputLoaded, interactionSource = sliderInteractions,
      thumb = { Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        SliderDefaults.Thumb(interactionSource = sliderInteractions, enabled = originalLoaded && outputLoaded)
      } },
      track = { SliderDefaults.Track(it, modifier = Modifier.height(4.dp)) },
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = description; stateDescription = position })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
      IconButton(onClick = { zoom = (zoom - .5f).coerceAtLeast(1f); pan = constrain(pan, zoom) }, enabled = zoom > 1f) {
        AppIcon(R.drawable.ic_zoom_out, description = stringResource(R.string.zoom_out))
      }
      TextButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.fit)); Text(" · ${(zoom * 100).roundToInt()}%")
      }
      IconButton(onClick = { zoom = (zoom + .5f).coerceAtMost(4f) }, enabled = zoom < 4f) {
        AppIcon(R.drawable.ic_zoom_in, description = stringResource(R.string.zoom_in))
      }
    }
    Text(stringResource(R.string.comparison_help), style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (originalMissing) Notice(stringResource(R.string.original_missing))
    if (outputMissing) Notice(stringResource(R.string.preview_unavailable_detail))
  }
}

@Composable
private fun ComparisonSize(label: String, bytes: Long, alignEnd: Boolean = false) {
  Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(bytes.toReadableSize(), style = MaterialTheme.typography.titleMedium)
  }
}

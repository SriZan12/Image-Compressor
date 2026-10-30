package com.example.imagecompressor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.theme.Space

/** One temporary preview at a time, with the selected sample identified independently of color. */
@Composable
fun EditorPhotoSelector(images: List<SelectedImage>, selectedId: String?, enabled: Boolean,
  onSelect: (SelectedImage) -> Unit) {
  val scroll = rememberLazyListState()
  LaunchedEffect(selectedId, images) {
    val index = images.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    if (scroll.layoutInfo.visibleItemsInfo.none { it.index == index }) scroll.scrollToItem(index)
  }
  LazyRow(Modifier.fillMaxWidth().selectableGroup(), state = scroll,
    horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
    itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
      val chosen = image.id == selectedId
      val description = stringResource(R.string.preview_selected_photo, index + 1, images.size, image.displayName)
      Surface(onClick = { onSelect(image) }, enabled = enabled, shape = MaterialTheme.shapes.medium,
        border = if (chosen) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        color = if (chosen) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(72.dp).semantics {
          contentDescription = description
          selected = chosen
          role = Role.RadioButton
        }) {
        Column(Modifier.padding(Space.xs).clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
          Photo(image.uri, null, Modifier.size(64.dp), ContentScale.Crop, MaterialTheme.shapes.small)
          Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

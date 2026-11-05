package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.ComparisonViewer

/** A local overlay keeps the editor and its temporary artifact alive without creating history. */
@Composable
internal fun EditorComparisonDialog(image: CompressedImage, onDismiss: () -> Unit) {
  val title = stringResource(R.string.compare_original)
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
    usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(Modifier.fillMaxSize().testTag("editor-comparison-dialog").semantics { paneTitle = title }) {
      BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        // A short window or large text needs a scrollable page; taller windows give the photo the rest.
        val expandPhoto = maxHeight >= 640.dp && LocalDensity.current.fontScale <= 1.3f
        Column(Modifier.widthIn(max = 1200.dp).fillMaxSize()) {
          Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Space.page, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) {
              Text(stringResource(R.string.editor_close_comparison))
            }
          }
          key(image.original.id, image.outputUri) {
            if (expandPhoto) ComparisonViewer(image,
              Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.page).padding(bottom = Space.page), expanded = true)
            else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
              .padding(horizontal = Space.page).padding(bottom = Space.page)) {
              ComparisonViewer(image, Modifier.fillMaxWidth())
            }
          }
        }
      }
    }
  }
}

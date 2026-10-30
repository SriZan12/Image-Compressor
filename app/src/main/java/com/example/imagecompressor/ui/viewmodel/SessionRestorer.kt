package com.example.imagecompressor.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.ui.state.AppScreen

/** Restores work, never a destination. A newly created session always opens on Home. */
internal data class RestoredSession(val selection: List<SelectedImage>, val settings: CompressionSettings,
  val message: String?)

internal suspend fun restoreSession(saved: SavedStateHandle, batch: BatchSnapshot?, repository: ImageRepository): RestoredSession {
  val previousScreen = saved.get<String>("screen")?.let { value -> AppScreen.entries.find { it.name == value } }
  val restoreBatch = batch != null && (batch.status == BatchStatus.INTERRUPTED || previousScreen == null || previousScreen == AppScreen.RESULTS)
  val selection = if (restoreBatch) SelectionResult(batch.items.map { it.image }, emptyList()) else {
    val uris = saved.get<ArrayList<String>>("selection").orEmpty().take(MAX_BATCH_IMAGES).map(Uri::parse)
    if (uris.isEmpty()) SelectionResult(emptyList(), emptyList()) else repository.inspectImages(uris)
  }
  return RestoredSession(selection.images,
    if (restoreBatch) batch.settings else SettingsCodec.decode(saved["settings"]),
    when {
      batch?.status == BatchStatus.INTERRUPTED -> "Some photos were not finished. Open Review previous photos to continue."
      selection.errors.isNotEmpty() -> "Some selected images are no longer readable. Select them again."
      else -> null
    })
}

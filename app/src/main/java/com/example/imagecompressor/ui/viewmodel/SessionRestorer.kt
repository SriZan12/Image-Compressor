package com.example.imagecompressor.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.ui.state.AppScreen

internal data class RestoredSession(val selection: List<SelectedImage>, val settings: CompressionSettings,
  val screen: AppScreen, val compare: CompressedImage?, val message: String?)

internal suspend fun restoreSession(saved: SavedStateHandle, batch: BatchSnapshot?, repository: ImageRepository,
  onboardingComplete: Boolean): RestoredSession {
  val screen = saved.get<String>("screen")?.let { value -> AppScreen.entries.find { it.name == value } }
  val restoreBatch = batch != null && (batch.status == BatchStatus.INTERRUPTED || screen == null || screen == AppScreen.RESULTS)
  val selection = if (restoreBatch) SelectionResult(batch.items.map { it.image }, emptyList()) else {
    val uris = saved.get<ArrayList<String>>("selection").orEmpty().take(MAX_BATCH_IMAGES).map(Uri::parse)
    if (uris.isEmpty()) SelectionResult(emptyList(), emptyList()) else repository.inspectImages(uris)
  }
  val compare = if (!restoreBatch && screen == AppScreen.IMAGE_COMPARE)
    saved.get<Long>("compareId")?.let { repository.getResult(it) } else null
  return RestoredSession(selection.images,
    if (restoreBatch) batch.settings else SettingsCodec.decode(saved["settings"]),
    when {
      !onboardingComplete -> AppScreen.ONBOARDING
      restoreBatch -> AppScreen.RESULTS
      compare != null -> AppScreen.IMAGE_COMPARE
      screen in setOf(AppScreen.HISTORY, AppScreen.SETTINGS, AppScreen.HOME) -> checkNotNull(screen)
      selection.images.isNotEmpty() -> if (screen == AppScreen.COMPRESSION_SETTINGS) screen else AppScreen.PREVIEW
      batch != null -> AppScreen.RESULTS
      else -> AppScreen.HOME
    }, compare,
    when {
      batch?.status == BatchStatus.INTERRUPTED -> "Compression was interrupted. Completed images were kept. Retry the remaining images."
      selection.errors.isNotEmpty() -> "Some selected images are no longer readable. Select them again."
      else -> null
    })
}

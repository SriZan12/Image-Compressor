package com.example.imagecompressor.ui.editor

import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.userMessage
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.util.TemporaryImageProcessor
import com.example.imagecompressor.util.TemporaryPreview
import com.example.imagecompressor.util.PreviewLease
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns one temporary editing session; no repository, history, gallery, or sharing work occurs here. */
class LiveEditorSession(
  scope: CoroutineScope,
  private val processor: TemporaryImageProcessor,
  private val debounceMillis: Long = 150L,
) : AutoCloseable {
  init { require(debounceMillis >= 0) }

  private data class Input(val image: SelectedImage, val settings: CompressionSettings)
  private data class Request(val generation: Long, val input: Input?)
  private val lock = Any()
  private val sessionJob = SupervisorJob(scope.coroutineContext[Job])
  private val sessionScope = CoroutineScope(scope.coroutineContext + sessionJob)
  private val requests = Channel<Request>(Channel.CONFLATED)
  private val mutableState = MutableStateFlow<EditorPreview>(EditorPreview.NotCalculated)
  val state: StateFlow<EditorPreview> = mutableState.asStateFlow()
  private var generation = 0L
  private var input: Input? = null
  private var active: Job? = null
  private var retained: TemporaryPreview? = null
  private var closed = false

  // A new job cannot start until the previous one drains, even if a codec ignores cancellation.
  private val worker = sessionScope.launch {
    try {
      for (request in requests) {
        synchronized(lock) { active }?.cancelAndJoin()
        if (request.input == null || !isCurrent(request)) continue
        val next = launch(start = CoroutineStart.LAZY) { prepare(request) }
        val start = synchronized(lock) {
          if (isCurrentLocked(request)) { active = next; true } else false
        }
        if (start) next.start() else next.cancel()
      }
    } finally {
      close()
    }
  }

  init {
    // Completion waits for the worker and its in-flight child. It also runs when the supplied
    // parent scope was already cancelled before the worker could enter its try/finally block.
    sessionJob.invokeOnCompletion {
      close()
      processor.close()
    }
  }

  /** Null suspends editing and synchronously invalidates any previously exportable result. */
  fun request(image: SelectedImage?, settings: CompressionSettings) {
    submit(image?.let { Input(it, settings) }, force = false)
  }

  fun retry() {
    val snapshot = synchronized(lock) { input?.let { it to generation } } ?: return
    submit(snapshot.first, force = true, expectedGeneration = snapshot.second)
  }

  /** Pins the exact Ready artifact while export copies it, even if editing subsequently stops. */
  fun acquire(image: SelectedImage, settings: CompressionSettings, expectedOutputPath: String? = null): PreviewLease? =
    synchronized(lock) { currentArtifactLocked(image, settings, expectedOutputPath)?.acquire() }

  /** Allows an asynchronous export receipt to update only the artifact it was started from. */
  fun isCurrent(image: SelectedImage, settings: CompressionSettings, outputFilePath: String): Boolean =
    synchronized(lock) { currentArtifactLocked(image, settings, outputFilePath) != null }

  private fun currentArtifactLocked(image: SelectedImage, settings: CompressionSettings,
    expectedOutputPath: String?): TemporaryPreview? {
    if (closed || input != Input(image, settings)) return null
    val ready = mutableState.value as? EditorPreview.Ready ?: return null
    val artifact = retained ?: return null
    if (artifact.isClosed || ready.settings != settings || ready.image != artifact.image || ready.image.original != image)
      return null
    if (expectedOutputPath != null && artifact.image.outputFilePath != expectedOutputPath) return null
    return artifact
  }

  private fun submit(next: Input?, force: Boolean, expectedGeneration: Long? = null) {
    val discard: TemporaryPreview?
    val cancel: Job?
    synchronized(lock) {
      if (closed || (expectedGeneration != null && generation != expectedGeneration)) return
      if (!force && input == next) return
      input = next
      generation++
      // Metadata is part of source identity: replacing a URI's content must not retain old pixels.
      discard = retained?.takeIf { next == null || it.image.original != next.image }
      if (discard != null) retained = null
      mutableState.value = if (next == null) EditorPreview.NotCalculated
        else EditorPreview.Updating(previousImage = retained?.image)
      cancel = active
      requests.trySend(Request(generation, next))
    }
    cancel?.cancel()
    discard?.close()
  }

  private suspend fun prepare(request: Request) {
    val snapshot = checkNotNull(request.input)
    var candidate: TemporaryPreview? = null
    try {
      delay(debounceMillis)
      if (!isCurrent(request)) return
      candidate = processor.preparePreview(snapshot.image, snapshot.settings)
      val canPublish = currentCoroutineContext().isActive
      var previous: TemporaryPreview? = null
      synchronized(lock) {
        if (canPublish && isCurrentLocked(request)) {
          val result = checkNotNull(candidate)
          if (result.image.original != snapshot.image)
            throw IOException("This photo changed. Choose it again to update the preview.")
          if (result.image.compressedSizeBytes <= 0L)
            throw IOException("We couldn’t check this copy. Please try again.")
          previous = retained?.takeUnless { it === result }
          retained = result
          candidate = null // Ownership moves to the session before Ready becomes observable.
          mutableState.value = EditorPreview.Ready(result.image, snapshot.settings)
        }
      }
      previous?.close()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failure: Exception) {
      val canPublish = currentCoroutineContext().isActive
      val discard = synchronized(lock) {
        if (!isCurrentLocked(request) || !canPublish) null
        else {
          mutableState.value = EditorPreview.Failure(userMessage(failure,
            "We couldn’t make a smaller copy. Please try again."))
          retained.also { retained = null }
        }
      }
      discard?.close()
    } finally {
      // Includes noncooperative completions from a cancelled or superseded generation.
      candidate?.close()
    }
  }

  private fun isCurrent(request: Request): Boolean = synchronized(lock) { isCurrentLocked(request) }
  private fun isCurrentLocked(request: Request): Boolean =
    !closed && generation == request.generation && input == request.input

  override fun close() {
    val discard = synchronized(lock) {
      if (closed) return
      closed = true
      generation++
      input = null
      mutableState.value = EditorPreview.NotCalculated
      requests.close()
      retained.also { retained = null }
    }
    sessionJob.cancel()
    discard?.close()
  }
}

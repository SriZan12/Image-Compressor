package com.example.imagecompressor.util

import android.content.Context
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SelectedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One editor owns one processor; preparing a preview never creates history or a shareable URI. */
interface TemporaryImageProcessor : AutoCloseable {
  suspend fun preparePreview(image: SelectedImage, settings: CompressionSettings): TemporaryPreview
}

/** A pinned view of one measured artifact. Closing a lease never closes another owner's lease. */
class PreviewLease internal constructor(val image: CompressedImage, private val release: () -> Unit) : AutoCloseable {
  private val closed = AtomicBoolean(false)
  override fun close() { if (closed.compareAndSet(false, true)) release() }
}

/** One session owner plus optional export leases; private files live until the final reference ends. */
class TemporaryPreview(val image: CompressedImage, private val dispose: () -> Unit) : AutoCloseable {
  private val lock = Any()
  private var ownerClosed = false
  private var references = 1
  val isClosed: Boolean get() = synchronized(lock) { ownerClosed }

  fun acquire(): PreviewLease? = synchronized(lock) {
    if (ownerClosed) null
    else PreviewLease(image, ::releaseReference).also { references++ }
  }

  override fun close() {
    val release = synchronized(lock) {
      if (ownerClosed) return
      ownerClosed = true
      --references == 0
    }
    if (release) dispose()
  }

  private fun releaseReference() {
    val release = synchronized(lock) { --references == 0 }
    if (release) dispose()
  }
}

internal class SessionTemporaryImageProcessor(
  private val context: Context,
  private val prepare: suspend (SelectedImage, CompressionSettings, File) -> CompressedImage,
) : TemporaryImageProcessor {
  private val lock = Any()
  private var closed = false
  private var directory: File? = null
  private var inFlight = 0
  private val artifacts = mutableSetOf<TemporaryPreview>()

  override suspend fun preparePreview(image: SelectedImage, settings: CompressionSettings): TemporaryPreview {
    // Keep ownership outside withContext: cancellation can discard a successful IO return before
    // the caller receives it, in which case that candidate still belongs to this cleanup scope.
    var published: TemporaryPreview? = null
    try {
      return withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val requestDirectory = synchronized(lock) {
          if (closed) throw CancellationException("The photo editing session is closed.")
          val session = directory ?: PreviewDirectories.create(context).also { directory = it }
          val request = Files.createTempDirectory(session.toPath(), "work_").toFile()
          inFlight++
          request
        }
        try {
          val prepared = prepare(image, settings, requestDirectory)
          val result = prepared.copy(verifiedSha256 = fingerprint(prepared))
          currentCoroutineContext().ensureActive()
          lateinit var artifact: TemporaryPreview
          artifact = TemporaryPreview(result) {
            requestDirectory.deleteRecursively()
            synchronized(lock) { artifacts.remove(artifact); cleanupClosedSession() }
          }
          synchronized(lock) {
            if (closed) throw CancellationException("The photo editing session is closed.")
            artifacts += artifact
            published = artifact
          }
          artifact
        } finally {
          if (published == null) requestDirectory.deleteRecursively()
          synchronized(lock) { inFlight--; cleanupClosedSession() }
        }
      }
    } catch (failure: Throwable) {
      published?.close()
      throw failure
    }
  }

  override fun close() {
    val owned = synchronized(lock) {
      if (closed) return
      closed = true
      artifacts.toList()
    }
    owned.forEach(TemporaryPreview::close)
    synchronized(lock) { cleanupClosedSession() }
  }

  /** Only called under lock, after publication is forbidden and no active request owns scratch. */
  private fun cleanupClosedSession() {
    if (closed && inFlight == 0 && artifacts.isEmpty()) {
      directory?.deleteRecursively()
      directory = null
    }
  }

  /** Fingerprint the finished candidate before any caller can lease it for export. */
  private suspend fun fingerprint(image: CompressedImage): String {
    val coroutine = currentCoroutineContext()
    val digest = MessageDigest.getInstance("SHA-256")
    var bytes = 0L
    File(image.outputFilePath).inputStream().use { input ->
      val buffer = ByteArray(65_536)
      while (true) {
        coroutine.ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        bytes += count
        digest.update(buffer, 0, count)
      }
    }
    coroutine.ensureActive()
    if (bytes <= 0 || bytes != image.compressedSizeBytes)
      throw IOException("The preview changed. Please try again before saving.")
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
  }
}

/** The application uses one Android process. Sweep once per cache root before creating its first
 * session; later sessions never sweep files owned by another active editor in this process. */
private object PreviewDirectories {
  private val lock = Any()
  private val initializedRoots = mutableSetOf<String>()
  private val sessionName = Regex("session_[0-9a-fA-F-]{36}")

  fun create(context: Context): File = synchronized(lock) {
    val root = File(context.cacheDir, "editor-previews").canonicalFile
    if (!root.isDirectory && !root.mkdirs()) throw IOException("Unable to create photo preview storage. Check available space.")
    if (initializedRoots.add(root.path)) {
      root.listFiles().orEmpty().filter {
        it.isDirectory && sessionName.matches(it.name) && it.canonicalFile.parentFile == root
      }.forEach { it.deleteRecursively() }
    }
    File(root, "session_${UUID.randomUUID()}").also {
      if (!it.mkdir()) throw IOException("Unable to create photo preview storage. Check available space.")
    }
  }
}

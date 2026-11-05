package com.example.imagecompressor.util.compression

import kotlin.math.sqrt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class EncodedCandidate<T>(val value: T, val size: PixelSize, val quality: Int, val bytes: Long)
internal data class SearchResult<T>(val candidate: EncodedCandidate<T>, val attempts: Int)

/** Retains a measured feasible candidate, or the smallest valid candidate if the target is unmet. */
internal class TargetSizeSearch(
  private val maxResolutions: Int = 6,
  private val minimumQuality: Int = 40,
  private val minimumLongEdge: Int = 256,
) {
  init { require(maxResolutions > 0 && minimumQuality in 10..100 && minimumLongEdge > 0) }

  suspend fun <T> search(
    initial: PixelSize,
    preferredQuality: Int,
    targetBytes: Long?,
    lossy: Boolean,
    allowResize: Boolean,
    encode: suspend (PixelSize, Int) -> EncodedCandidate<T>,
    discard: (T) -> Unit,
  ): SearchResult<T> {
    require(preferredQuality in 10..100 && (targetBytes == null || targetBytes > 0))
    var best: EncodedCandidate<T>? = null
    var attempts = 0
    var transferred = false
    fun fits(candidate: EncodedCandidate<T>) = targetBytes == null || candidate.bytes <= targetBytes
    suspend fun attempt(size: PixelSize, quality: Int): Long {
      currentCoroutineContext().ensureActive()
      val candidate = encode(size, quality)
      attempts++
      var retained = false
      try {
        currentCoroutineContext().ensureActive()
        check(candidate.bytes > 0 && candidate.size == size && candidate.quality == quality)
        val old = best
        val better = old == null || when {
          fits(candidate) && !fits(old) -> true
          !fits(candidate) && fits(old) -> false
          fits(candidate) -> candidate.size.pixels > old.size.pixels ||
            (candidate.size == old.size && candidate.quality > old.quality)
          else -> candidate.bytes < old.bytes
        }
        if (better) {
          best = candidate
          retained = true
          old?.let { discard(it.value) }
        }
        return candidate.bytes
      } finally {
        if (!retained) discard(candidate.value)
      }
    }

    try {
      var size = initial
      repeat(if (allowResize) maxResolutions else 1) {
        val highQuality = if (lossy) preferredQuality else 100
        var smallestAtSize = attempt(size, highQuality)
        if (best?.let(::fits) == true) {
          transferred = true
          return SearchResult(checkNotNull(best), attempts)
        }
        val lowQuality = minOf(minimumQuality, preferredQuality)
        if (lossy && highQuality > lowQuality) {
          val lowBytes = attempt(size, lowQuality)
          smallestAtSize = minOf(smallestAtSize, lowBytes)
          if (lowBytes <= checkNotNull(targetBytes)) {
            var low = lowQuality + 1
            var high = highQuality - 1
            // At most six midpoint probes for the supported [10,100] quality range.
            repeat(6) {
              if (low <= high) {
                val quality = (low + high) / 2
                val bytes = attempt(size, quality)
                if (bytes <= targetBytes) low = quality + 1 else high = quality - 1
              }
            }
          }
        }
        if (best?.let(::fits) == true || !allowResize) {
          transferred = true
          return SearchResult(checkNotNull(best), attempts)
        }
        val next = nextSize(size, checkNotNull(targetBytes), smallestAtSize)
        if (next == null) {
          transferred = true
          return SearchResult(checkNotNull(best), attempts)
        }
        size = next
      }
      transferred = true
      return SearchResult(checkNotNull(best), attempts)
    } finally {
      if (!transferred) best?.let { discard(it.value) }
    }
  }

  private fun nextSize(size: PixelSize, target: Long, actual: Long): PixelSize? {
    if (size.longEdge <= minimumLongEdge) return null
    val ratio = (sqrt(target.toDouble() / actual) * 0.95).coerceIn(0.5, 0.85)
    val edge = (size.longEdge * ratio).toInt().coerceAtLeast(minimumLongEdge)
    if (edge >= size.longEdge) return null
    return PixelSize((size.width.toLong() * edge / size.longEdge).toInt().coerceAtLeast(1),
      (size.height.toLong() * edge / size.longEdge).toInt().coerceAtLeast(1))
  }
}

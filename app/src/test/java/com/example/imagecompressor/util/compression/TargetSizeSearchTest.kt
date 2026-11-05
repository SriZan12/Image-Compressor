package com.example.imagecompressor.util.compression

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class TargetSizeSearchTest {
  private val size = PixelSize(1600, 1200)

  @Test fun findsHighestFittingQualityAndReleasesOtherCandidates() = runBlocking {
    val discarded = mutableListOf<Int>()
    val result = TargetSizeSearch().search(size, 80, 635, true, true,
      encode = { s, q -> EncodedCandidate(q, s, q, q * 10L) }, discard = discarded::add)
    assertEquals(63, result.candidate.quality)
    assertEquals(size, result.candidate.size)
    assertTrue(result.attempts <= 8)
    assertEquals(result.attempts - 1, discarded.size)
    assertFalse(discarded.contains(63))
  }

  @Test fun retainsFeasibleResultEvenWhenLaterProbesAreLarger() = runBlocking {
    val result = TargetSizeSearch().search(size, 80, 400, true, false,
      encode = { s, q -> EncodedCandidate(q, s, q, if (q == 40) 350 else 1000) }, discard = {})
    assertEquals(40, result.candidate.quality)
    assertTrue(result.candidate.bytes <= 400)
    assertEquals(size, result.candidate.size)
  }

  @Test fun noTargetUsesOneEncodeAtRequestedQuality() = runBlocking {
    val result = TargetSizeSearch().search(size, 83, null, true, true,
      encode = { s, q -> EncodedCandidate(q, s, q, 4000) }, discard = {})
    assertEquals(1, result.attempts)
    assertEquals(83, result.candidate.quality)
  }

  @Test fun impossiblePngIsBoundedAndDoesNotPretendQualityReducesBytes() = runBlocking {
    val attempted = mutableListOf<PixelSize>()
    val result = TargetSizeSearch().search(size, 80, 1, false, true,
      encode = { s, q ->
        assertEquals(100, q)
        attempted += s
        EncodedCandidate(s, s, q, s.pixels)
      }, discard = {})
    assertTrue(result.candidate.bytes > 1)
    assertEquals(256, result.candidate.size.longEdge)
    assertTrue(result.attempts <= 6)
    assertEquals(attempted.distinct().size, attempted.size)
  }

  @Test fun targetResizeCanBeDisabledAndExplicitLowQualityIsRespected() = runBlocking {
    val result = TargetSizeSearch().search(size, 20, 1, true, false,
      encode = { s, q -> EncodedCandidate(q, s, q, 999) }, discard = {})
    assertEquals(1, result.attempts)
    assertEquals(size, result.candidate.size)
    assertEquals(20, result.candidate.quality)
  }

  @Test fun errorDiscardsRetainedCandidate() = runBlocking {
    val discarded = mutableListOf<Int>()
    var calls = 0
    try {
      TargetSizeSearch().search(size, 80, 1, true, true,
        encode = { s, q ->
          if (++calls == 2) throw IOException("disk full")
          EncodedCandidate(calls, s, q, 999)
        }, discard = discarded::add)
      fail("Expected encoder failure")
    } catch (_: IOException) {
      assertEquals(listOf(1), discarded)
    }
  }

  @Test fun cancellationAfterEncodeDiscardsBothCandidateAndPreviousBest() = runBlocking {
    val discarded = mutableListOf<Int>()
    var calls = 0
    val job = Job()
    try {
      withContext(job) {
        TargetSizeSearch().search(size, 80, 1, true, true,
          encode = { s, q ->
            calls++
            if (calls == 2) currentCoroutineContext()[Job]!!.cancel()
            EncodedCandidate(calls, s, q, 999)
          }, discard = discarded::add)
      }
      fail("Expected cancellation")
    } catch (_: CancellationException) {
      assertEquals(setOf(1, 2), discarded.toSet())
      assertEquals(2, discarded.size)
    } finally { job.cancel() }
  }
}

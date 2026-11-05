package com.example.imagecompressor.play.review

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTrackerTest {
  @Test
  fun defaultsRequireFiveSavesInThreeSessionsAfterSevenDays() {
    val tracker = ReviewTracker(MemoryStore(), nowMillis = 0)
    tracker.recordSuccessfulSaves("one", 2, DAY)
    tracker.recordSuccessfulSaves("two", 2, 2 * DAY)
    assertFalse(tracker.tryBeginAttempt("two", 7 * DAY))
    tracker.recordSuccessfulSaves("three", 1, 7 * DAY - 1)
    assertFalse(tracker.tryBeginAttempt("three", 7 * DAY - 1))
    assertTrue(tracker.tryBeginAttempt("three", 7 * DAY))
  }

  @Test
  fun largeBatchInOneSessionDoesNotCountAsMultipleSessions() {
    val tracker = ReviewTracker(MemoryStore(), nowMillis = 0)
    tracker.recordSuccessfulSaves("one", 50, 7 * DAY)
    tracker.recordSuccessfulSaves("one", 50, 7 * DAY)
    assertEquals(100L, tracker.snapshot().successfulSaves)
    assertEquals(1L, tracker.snapshot().successfulSessions)
    assertFalse(tracker.tryBeginAttempt("one", 7 * DAY))
  }

  @Test
  fun noSuccessAndInvalidCountsCannotBecomeEligible() {
    val tracker = ReviewTracker(MemoryStore(), permissivePolicy(), 0)
    tracker.recordSuccessfulSaves("one", 0, DAY)
    tracker.recordSuccessfulSaves("one", -2, DAY)
    tracker.recordSuccessfulSaves(" ", 5, DAY)
    assertEquals(0L, tracker.snapshot().successfulSaves)
    assertFalse(tracker.tryBeginAttempt("one", DAY))
  }

  @Test
  fun currentSessionMustHaveSuccessfulUseBeforeAnAttempt() {
    val tracker = ReviewTracker(MemoryStore(), permissivePolicy(), 0)
    tracker.recordSuccessfulSaves("one", 1, DAY)
    assertFalse(tracker.tryBeginAttempt("new-session-with-no-saves", DAY))
    assertTrue(tracker.tryBeginAttempt("one", DAY))
  }

  @Test
  fun reservationIsPersistedBeforeReturningPermissionAndResetsUsage() {
    val store = MemoryStore()
    val tracker = ReviewTracker(store, permissivePolicy(), 0)
    tracker.recordSuccessfulSaves("one", 3, DAY)
    assertTrue(tracker.tryBeginAttempt("one", DAY))
    assertEquals(DAY, store.history!!.lastAttemptAtMillis)
    assertEquals("one", store.history!!.lastAttemptSessionId)
    assertEquals(0L, store.history!!.successfulSaves)
    assertEquals(0L, store.history!!.successfulSessions)
  }

  @Test
  fun duplicateRequestsAndDuplicateCompletionCallbacksCannotRepeatAnAttempt() {
    val tracker = ReviewTracker(MemoryStore(), permissivePolicy(), 0)
    tracker.recordSuccessfulSaves("one", 1, DAY)
    assertTrue(tracker.tryBeginAttempt("one", DAY))
    assertFalse(tracker.tryBeginAttempt("one", DAY))
    tracker.finishAttempt()
    tracker.finishAttempt()
    tracker.recordSuccessfulSaves("one", 10, 200 * DAY)
    assertFalse(tracker.tryBeginAttempt("one", 200 * DAY))
  }

  @Test
  fun racingReservationsAllowExactlyOneApiRequest() {
    val tracker = ReviewTracker(MemoryStore(), permissivePolicy(), 0)
    tracker.recordSuccessfulSaves("one", 1, DAY)
    val ready = CountDownLatch(8)
    val start = CountDownLatch(1)
    val allowed = AtomicInteger()
    val workers = List(8) {
      Thread {
        ready.countDown()
        start.await()
        if (tracker.tryBeginAttempt("one", DAY)) allowed.incrementAndGet()
      }.apply { start() }
    }
    ready.await()
    start.countDown()
    workers.forEach(Thread::join)
    assertEquals(1, allowed.get())
  }

  @Test
  fun cooldownSurvivesProcessReloadAndExpiresExactlyAtBoundary() {
    val store = MemoryStore()
    val first = ReviewTracker(store, permissivePolicy(), 0)
    first.recordSuccessfulSaves("one", 1, DAY)
    assertTrue(first.tryBeginAttempt("one", DAY))
    // Recreate without finishAttempt: a process death cannot erase the reservation.
    val restored = ReviewTracker(store, permissivePolicy(), 2 * DAY)
    restored.recordSuccessfulSaves("two", 1, 2 * DAY)
    assertFalse(restored.tryBeginAttempt("two", 91 * DAY - 1))
    assertTrue(restored.tryBeginAttempt("two", 91 * DAY))
  }

  @Test
  fun failedPlayRequestStillConsumesCooldown() {
    val tracker = ReviewTracker(MemoryStore(), permissivePolicy(), 0)
    tracker.recordSuccessfulSaves("one", 1, DAY)
    assertTrue(tracker.tryBeginAttempt("one", DAY))
    tracker.finishAttempt() // Same operation for API failure, cancellation, or API completion.
    tracker.recordSuccessfulSaves("two", 1, 2 * DAY)
    assertFalse(tracker.tryBeginAttempt("two", 2 * DAY))
    assertEquals(DAY, tracker.snapshot().lastAttemptAtMillis)
  }

  @Test
  fun laterAttemptRequiresFreshMeaningfulUsage() {
    val store = MemoryStore()
    val tracker = ReviewTracker(store, nowMillis = 0)
    tracker.recordSuccessfulSaves("one", 2, DAY)
    tracker.recordSuccessfulSaves("two", 2, 2 * DAY)
    tracker.recordSuccessfulSaves("three", 1, 7 * DAY)
    assertTrue(tracker.tryBeginAttempt("three", 7 * DAY))
    tracker.finishAttempt()
    val later = ReviewTracker(store, nowMillis = 100 * DAY)
    later.recordSuccessfulSaves("four", 1, 100 * DAY)
    assertFalse(later.tryBeginAttempt("four", 100 * DAY))
    later.recordSuccessfulSaves("five", 2, 101 * DAY)
    later.recordSuccessfulSaves("six", 2, 102 * DAY)
    assertTrue(later.tryBeginAttempt("six", 102 * DAY))
  }

  @Test
  fun activityRecreationAndReloadDoNotDoubleCountSuccessfulSession() {
    val store = MemoryStore()
    val tracker = ReviewTracker(store, nowMillis = 0)
    tracker.recordSuccessfulSaves("same-process", 2, DAY)
    val restored = ReviewTracker(store, nowMillis = DAY)
    restored.recordSuccessfulSaves("same-process", 2, DAY)
    assertEquals(4L, restored.snapshot().successfulSaves)
    assertEquals(1L, restored.snapshot().successfulSessions)
  }

  @Test
  fun failedDurableReservationPreventsApiRequestAndFurtherAttempts() {
    val store = MemoryStore()
    val tracker = ReviewTracker(store, permissivePolicy(), 0)
    tracker.recordSuccessfulSaves("one", 1, DAY)
    store.failWrites = true
    assertFalse(tracker.tryBeginAttempt("one", DAY))
    store.failWrites = false
    assertFalse(tracker.tryBeginAttempt("one", DAY))
    assertEquals(null, store.history!!.lastAttemptAtMillis)
  }

  @Test
  fun initialPersistenceFailureFailsClosed() {
    val store = MemoryStore().apply { failWrites = true }
    val tracker = ReviewTracker(store, permissivePolicy(), 0)
    store.failWrites = false
    tracker.recordSuccessfulSaves("one", 5, 100 * DAY)
    assertFalse(tracker.tryBeginAttempt("one", 100 * DAY))
  }

  @Test
  fun savePersistenceFailureCannotCreateEligibility() {
    val store = MemoryStore()
    val tracker = ReviewTracker(store, permissivePolicy(), 0)
    store.failWrites = true
    tracker.recordSuccessfulSaves("one", 5, 100 * DAY)
    assertEquals(0L, tracker.snapshot().successfulSaves)
    assertFalse(tracker.tryBeginAttempt("one", 100 * DAY))
  }

  @Test
  fun clockRollbackAndPersistedFutureDatesDoNotShortenCooldownOrAge() {
    val store = MemoryStore()
    val tracker = ReviewTracker(store, permissivePolicy(), 0)
    tracker.recordSuccessfulSaves("one", 1, 10 * DAY)
    assertFalse(tracker.tryBeginAttempt("one", 9 * DAY))
    tracker.recordSuccessfulSaves("two", 10, 9 * DAY)
    assertEquals(1L, tracker.snapshot().successfulSaves)
    val reloadedAfterClockRollback = ReviewTracker(store, permissivePolicy(), DAY)
    assertFalse(reloadedAfterClockRollback.tryBeginAttempt("one", DAY))
    assertTrue(reloadedAfterClockRollback.tryBeginAttempt("one", 10 * DAY))
  }

  @Test
  fun malformedMetadataStartsFreshWithProtectiveCooldown() {
    val store = MemoryStore().apply {
      history = ReviewHistory(firstSeenAtMillis = -1, successfulSaves = Long.MAX_VALUE)
    }
    val tracker = ReviewTracker(store, permissivePolicy(), DAY)
    tracker.recordSuccessfulSaves("one", 1, DAY)
    assertFalse(tracker.tryBeginAttempt("one", DAY))
    assertEquals(DAY, tracker.snapshot().firstSeenAtMillis)
    assertEquals(DAY, tracker.snapshot().lastAttemptAtMillis)
  }

  @Test
  fun readErrorsAndWriteExceptionsFailConservatively() {
    val store = object : ReviewStore {
      override fun load(): ReviewHistory? = error("Unreadable preferences")
      override fun save(history: ReviewHistory): Boolean = error("Storage unavailable")
    }
    val tracker = ReviewTracker(store, permissivePolicy(), DAY)
    tracker.recordSuccessfulSaves("one", 10, 200 * DAY)
    assertFalse(tracker.tryBeginAttempt("one", 200 * DAY))
    assertNotNull(tracker.snapshot().lastAttemptAtMillis)
  }

  @Test
  fun configuredThresholdsAreUsedAndCounterOverflowIsSaturated() {
    val store = MemoryStore().apply {
      history = ReviewHistory(
        firstSeenAtMillis = 0,
        successfulSaves = Long.MAX_VALUE - 1,
        successfulSessions = 1,
        lastSuccessfulSessionId = "one",
      )
    }
    val tracker = ReviewTracker(store, permissivePolicy(), 0)
    tracker.recordSuccessfulSaves("one", 10, DAY)
    assertEquals(Long.MAX_VALUE, tracker.snapshot().successfulSaves)
    assertTrue(tracker.tryBeginAttempt("one", DAY))
  }

  private class MemoryStore : ReviewStore {
    var history: ReviewHistory? = null
    var failWrites = false
    override fun load(): ReviewHistory? = history
    override fun save(history: ReviewHistory): Boolean {
      if (failWrites) return false
      this.history = history
      return true
    }
  }

  private fun permissivePolicy() = ReviewPolicy(
    minSuccessfulSaves = 1,
    minSuccessfulSessions = 1,
    minInstallAgeDays = 0,
  )

  private companion object {
    const val DAY = ReviewPolicy.MILLIS_PER_DAY
  }
}

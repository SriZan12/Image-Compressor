package com.example.imagecompressor.play.review

/** Product policy, deliberately independent of whether Google actually displays its review UI. */
data class ReviewPolicy(
  val minSuccessfulSaves: Int = 5,
  val minSuccessfulSessions: Int = 3,
  val minInstallAgeDays: Int = 7,
  val cooldownDays: Int = 90,
) {
  init {
    require(minSuccessfulSaves > 0)
    require(minSuccessfulSessions > 0)
    require(minInstallAgeDays >= 0)
    require(cooldownDays > 0)
  }

  val minInstallAgeMillis: Long get() = minInstallAgeDays.toLong() * MILLIS_PER_DAY
  val cooldownMillis: Long get() = cooldownDays.toLong() * MILLIS_PER_DAY

  companion object {
    const val MILLIS_PER_DAY = 24L * 60 * 60 * 1_000
  }
}

/** Saves and successful sessions are counted since the last reserved review attempt. */
data class ReviewHistory(
  val firstSeenAtMillis: Long,
  val successfulSaves: Long = 0,
  val successfulSessions: Long = 0,
  val lastSuccessfulSessionId: String? = null,
  val lastAttemptAtMillis: Long? = null,
  val lastAttemptSessionId: String? = null,
  val lastObservedAtMillis: Long = firstSeenAtMillis,
)

interface ReviewStore {
  /** Null means a new installation. Read errors and corrupt data must not be reported as absent. */
  fun load(): ReviewHistory?

  /** True only after the complete record is durably written. */
  fun save(history: ReviewHistory): Boolean
}

/**
 * An application-scoped tracker. Session IDs must be random IDs created once per process, shared by
 * every Activity, and never reused by a later process. No Activity or Google SDK object is retained.
 *
 * Methods are synchronized because save completion and lifecycle callbacks can race. Store writes
 * are synchronous: call mutation methods on an IO dispatcher. A failed write disables attempts for
 * this tracker, rather than risking a prompt without a durable cooldown.
 */
class ReviewTracker(
  private val store: ReviewStore,
  private val policy: ReviewPolicy = ReviewPolicy(),
  nowMillis: Long = System.currentTimeMillis(),
) {
  private var persistenceHealthy = true
  private var attemptInFlight = false
  private val attemptedSessions = mutableSetOf<String>()
  private val successfulSessionsSeen = mutableSetOf<String>()
  private var history: ReviewHistory

  init {
    require(nowMillis >= 0)
    val loaded = runCatching { store.load() }
    val candidate = loaded.getOrNull()
    history = when {
      loaded.isFailure || (candidate != null && !candidate.isValid()) ->
        // Corruption must never erase a cooldown and immediately allow another prompt.
        ReviewHistory(
          firstSeenAtMillis = nowMillis,
          lastAttemptAtMillis = nowMillis,
          lastObservedAtMillis = nowMillis,
        )
      candidate != null -> candidate
      else -> ReviewHistory(firstSeenAtMillis = nowMillis)
    }
    history.lastSuccessfulSessionId?.let(successfulSessionsSeen::add)
    history.lastAttemptSessionId?.let(attemptedSessions::add)
    if (candidate != history || loaded.isFailure) {
      persist(history)
    }
  }

  /** Count only confirmed newly saved photos, never compression, cancellation, or failed saves. */
  @Synchronized
  fun recordSuccessfulSaves(sessionId: String, count: Int, nowMillis: Long) {
    if (sessionId.isBlank() || count <= 0 || !validTime(nowMillis)) return
    val isNewSession = sessionId !in successfulSessionsSeen
    val updated = history.copy(
      successfulSaves = saturatedAdd(history.successfulSaves, count.toLong()),
      successfulSessions = saturatedAdd(history.successfulSessions, if (isNewSession) 1 else 0),
      lastSuccessfulSessionId = sessionId,
      lastObservedAtMillis = nowMillis,
    )
    if (persist(updated)) {
      successfulSessionsSeen += sessionId
    }
  }

  /**
   * Reserves an attempt before requesting ReviewInfo. True is permission to attempt the API once,
   * never evidence that the review UI appeared or that a rating was submitted. Call only at a safe
   * foreground pause, after work has finished and competing Play/update/share flows are absent.
   */
  @Synchronized
  fun tryBeginAttempt(sessionId: String, nowMillis: Long): Boolean {
    if (!persistenceHealthy || attemptInFlight || sessionId.isBlank() ||
      sessionId in attemptedSessions || !validTime(nowMillis)) return false
    if (history.lastSuccessfulSessionId != sessionId ||
      history.successfulSaves < policy.minSuccessfulSaves ||
      history.successfulSessions < policy.minSuccessfulSessions ||
      nowMillis - history.firstSeenAtMillis < policy.minInstallAgeMillis) return false
    val lastAttempt = history.lastAttemptAtMillis
    if (lastAttempt != null && nowMillis - lastAttempt < policy.cooldownMillis) return false

    val reserved = history.copy(
      successfulSaves = 0,
      successfulSessions = 0,
      lastSuccessfulSessionId = null,
      lastAttemptAtMillis = nowMillis,
      lastAttemptSessionId = sessionId,
      lastObservedAtMillis = nowMillis,
    )
    if (!persist(reserved)) return false
    attemptedSessions += sessionId
    successfulSessionsSeen.clear()
    attemptInFlight = true
    return true
  }

  /** Also call after request/launch failure or cancellation. The persisted cooldown is not undone. */
  @Synchronized
  fun finishAttempt() {
    attemptInFlight = false
  }

  @Synchronized
  fun snapshot(): ReviewHistory = history

  private fun validTime(nowMillis: Long): Boolean =
    nowMillis >= 0 && nowMillis >= history.lastObservedAtMillis

  private fun persist(updated: ReviewHistory): Boolean {
    if (!persistenceHealthy) return false
    val saved = runCatching { store.save(updated) }.getOrDefault(false)
    if (saved) history = updated else persistenceHealthy = false
    return saved
  }

  private fun ReviewHistory.isValid(): Boolean =
    firstSeenAtMillis >= 0 &&
      successfulSaves >= 0 && successfulSessions >= 0 && successfulSessions <= successfulSaves &&
      lastObservedAtMillis >= firstSeenAtMillis &&
      (lastAttemptAtMillis == null || lastAttemptAtMillis in firstSeenAtMillis..lastObservedAtMillis) &&
      (lastSuccessfulSessionId == null || lastSuccessfulSessionId.isNotBlank()) &&
      (lastAttemptSessionId == null || (lastAttemptSessionId.isNotBlank() && lastAttemptAtMillis != null)) &&
      if (successfulSaves == 0L) {
        successfulSessions == 0L && lastSuccessfulSessionId == null
      } else {
        successfulSessions > 0L && lastSuccessfulSessionId != null
      }

  private fun saturatedAdd(value: Long, amount: Long): Long =
    if (Long.MAX_VALUE - value < amount) Long.MAX_VALUE else value + amount
}

package com.example.imagecompressor.play.review

import android.content.Context
import android.annotation.SuppressLint

/** Review metadata shares the app's settings file, but never touches its existing settings keys. */
class SharedPreferencesReviewStore(context: Context) : ReviewStore {
  private val preferences = context.applicationContext.getSharedPreferences(
    "image_compressor_preferences", Context.MODE_PRIVATE,
  )

  override fun load(): ReviewHistory? {
    if (!preferences.contains(SCHEMA_VERSION)) return null
    check(preferences.getInt(SCHEMA_VERSION, 0) == 1) { "Unsupported review metadata version" }
    return ReviewHistory(
      firstSeenAtMillis = preferences.getLong(FIRST_SEEN, -1),
      successfulSaves = preferences.getLong(SAVES, 0),
      successfulSessions = preferences.getLong(SESSIONS, 0),
      lastSuccessfulSessionId = preferences.getString(SUCCESS_SESSION, null),
      lastAttemptAtMillis = if (preferences.contains(LAST_ATTEMPT)) {
        preferences.getLong(LAST_ATTEMPT, -1)
      } else null,
      lastAttemptSessionId = preferences.getString(ATTEMPT_SESSION, null),
      lastObservedAtMillis = preferences.getLong(LAST_OBSERVED, -1),
    )
  }

  // The KTX edit helper discards commit's result; eligibility must observe durable-write failure.
  @SuppressLint("UseKtx")
  override fun save(history: ReviewHistory): Boolean {
    val editor = preferences.edit()
      .putInt(SCHEMA_VERSION, 1)
      .putLong(FIRST_SEEN, history.firstSeenAtMillis)
      .putLong(SAVES, history.successfulSaves)
      .putLong(SESSIONS, history.successfulSessions)
      .putString(SUCCESS_SESSION, history.lastSuccessfulSessionId)
      .putString(ATTEMPT_SESSION, history.lastAttemptSessionId)
      .putLong(LAST_OBSERVED, history.lastObservedAtMillis)
    history.lastAttemptAtMillis?.let { editor.putLong(LAST_ATTEMPT, it) }
      ?: editor.remove(LAST_ATTEMPT)
    // apply() cannot confirm a durable reservation before the Play API is invoked.
    return editor.commit()
  }

  private companion object {
    const val SCHEMA_VERSION = "review_schema_version"
    const val FIRST_SEEN = "review_first_seen_at"
    const val SAVES = "review_successful_saves"
    const val SESSIONS = "review_successful_sessions"
    const val SUCCESS_SESSION = "review_last_successful_session"
    const val LAST_ATTEMPT = "review_last_attempt_at"
    const val ATTEMPT_SESSION = "review_last_attempt_session"
    const val LAST_OBSERVED = "review_last_observed_at"
  }
}

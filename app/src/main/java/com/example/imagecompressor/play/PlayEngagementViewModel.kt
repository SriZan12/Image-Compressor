package com.example.imagecompressor.play

import android.app.Activity
import android.app.Application
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagecompressor.ImageCompressorApplication
import com.example.imagecompressor.play.review.ReviewTracker
import com.example.imagecompressor.play.update.InstallPhase
import com.example.imagecompressor.play.update.PromptResult
import com.example.imagecompressor.play.update.UpdateInfo
import com.example.imagecompressor.play.update.UpdateStatus
import com.example.imagecompressor.play.update.UpdateType
import com.example.imagecompressor.play.update.WorkSafety
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.imagecompressor.play.update.UpdateAvailability as Availability

data class PlayFeedback(
  val message: String? = null,
  val reviewReady: Boolean = false,
  val reviewBusy: Boolean = false,
  val foreground: Boolean = false,
)

/**
 * Application-context SDK adapter. Activity and launcher references are used only at the moment of
 * launch; they are never retained by this ViewModel or by background request callbacks.
 */
class PlayEngagementViewModel(application: Application) : AndroidViewModel(application) {
  private val container = (application as ImageCompressorApplication).container
  private val coordinator = container.updateCoordinator
  val updates = coordinator.state
  private val feedback = MutableStateFlow(PlayFeedback())
  val uiState = feedback.asStateFlow()
  private val updateManager = AppUpdateManagerFactory.create(application)
  private val reviewManager = ReviewManagerFactory.create(application)
  private var appState: StateFlow<ImageCompressorUiState>? = null
  private var uiReady = false
  private var listening = false
  private var updateInfo: AppUpdateInfo? = null
  private var ownedPromptId: Long? = null
  private var checkJob: Job? = null
  private var activeCheck: Long? = null
  private var reviewJob: Job? = null
  private var reviewInfo: ReviewInfo? = null
  private var reservedTracker: ReviewTracker? = null
  private var reviewLaunched = false
  private var reviewEvaluatedThisPause = false

  private val installListener = InstallStateUpdatedListener { install ->
    viewModelScope.launch {
      coordinator.onInstallState(
        install.installStatus().toPhase(), install.bytesDownloaded(), install.totalBytesToDownload(),
      )
    }
  }

  fun bindAppState(state: StateFlow<ImageCompressorUiState>) {
    appState = state
    if (!naturalPause()) reviewEvaluatedThisPause = false
  }

  fun setUiReady(ready: Boolean) { uiReady = ready }

  fun onStart() {
    if (!listening) {
      runCatching { updateManager.registerListener(installListener) }
        .onSuccess { listening = true }
    }
  }

  fun onStop() {
    if (listening) {
      runCatching { updateManager.unregisterListener(installListener) }
      listening = false
    }
  }

  fun onResume() {
    feedback.update { it.copy(foreground = true) }
    // A fresh AppUpdateInfo recovers downloads and interrupted immediate flows after every return.
    if (!feedback.value.reviewBusy) check(manual = false)
  }

  fun onPause() {
    feedback.update { it.copy(foreground = false) }
    if (!reviewLaunched) {
      reviewJob?.cancel()
      finishReview()
    }
  }

  fun checkForUpdates() {
    if (!feedback.value.foreground || feedback.value.reviewBusy) return
    check(manual = true)
  }

  private fun check(manual: Boolean) {
    val token = coordinator.beginCheck(manual) ?: return
    activeCheck = token
    checkJob = viewModelScope.launch {
      try {
        val info = withTimeout(15_000) { updateManager.appUpdateInfo.awaitValue() }
        if (info.updateAvailability() == UpdateAvailability.UNKNOWN) {
          if (coordinator.onCheckFailed(token) && manual) message("Google Play couldn't check for updates. Try again later.")
        } else if (coordinator.onInfo(token, info.toUpdateInfo())) {
          updateInfo = info
          if (manual) {
            message(when (updates.value.status) {
              UpdateStatus.DOWNLOADED -> "Your update is ready. Finish your work, then restart to update."
              UpdateStatus.PENDING, UpdateStatus.DOWNLOADING -> "Your update is downloading. You can keep using the app."
              UpdateStatus.INSTALLING -> "Your update is being installed."
              UpdateStatus.UP_TO_DATE, UpdateStatus.INSTALLED -> "You're up to date."
              else -> if (!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) && !info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
                "This update isn't available in the app yet. Try again later or check Google Play."
              else "An update is available. Google Play will ask when you've finished your current action."
            })
          }
        }
      } catch (error: Exception) {
        if (coordinator.onCheckFailed(token) && manual) message(updateErrorMessage(error))
        if (error is CancellationException) throw error
      } finally {
        if (activeCheck == token) activeCheck = null
      }
    }
  }

  fun launchPendingUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
    if (!canShowPlayUi() || feedback.value.reviewBusy) return
    val info = updateInfo ?: return
    val prompt = coordinator.takePrompt(workSafety()) ?: return
    ownedPromptId = prompt.id
    // AppUpdateInfo is single-use. All retries obtain a fresh instance from Google Play.
    updateInfo = null
    try {
      val started = updateManager.startUpdateFlowForResult(
        info, launcher,
        AppUpdateOptions.newBuilder(
          if (prompt.type == UpdateType.IMMEDIATE) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE,
        ).build(),
      )
      if (!started) {
        coordinator.onPromptResult(prompt.id, PromptResult.FAILED)
        ownedPromptId = null
        message("Couldn't start the update. You can try again in Settings.")
      }
    } catch (_: Exception) {
      coordinator.onPromptResult(prompt.id, PromptResult.FAILED)
      ownedPromptId = null
      message("Couldn't start the update. You can try again in Settings.")
    }
  }

  fun onUpdateResult(resultCode: Int) {
    val promptId = ownedPromptId ?: return
    ownedPromptId = null
    val result = when (resultCode) {
      Activity.RESULT_OK -> PromptResult.ACCEPTED
      Activity.RESULT_CANCELED -> PromptResult.CANCELED
      else -> PromptResult.FAILED
    }
    if (coordinator.onPromptResult(promptId, result) && result != PromptResult.ACCEPTED) {
      message(if (result == PromptResult.CANCELED) "Update canceled. You can check again in Settings."
        else "The update couldn't start. You can try again in Settings.")
    }
    if (feedback.value.foreground) check(manual = false)
  }

  /** Called only by the explicit Restart to update button. Never called by a lifecycle callback. */
  fun restartUpdate() {
    if (!canShowPlayUi() || feedback.value.reviewBusy) return
    val token = coordinator.beginRestart(workSafety()) ?: run {
      message("Finish saving or sharing, then clear your selection before restarting.")
      return
    }
    // completeUpdate is not cancelable by a coroutine timeout. Never unlock photo commands on a
    // local timeout or host destruction: Play could still accept the request and restart later.
    // These callbacks retain no Activity and reconcile the process-scoped work gate even if this
    // ViewModel's host is finished before Play answers.
    try {
      updateManager.completeUpdate()
        .addOnSuccessListener {
          coordinator.onRestartCompleted(token)
          viewModelScope.launch {
            delay(30_000)
            if (feedback.value.foreground) check(manual = false)
          }
        }
        .addOnFailureListener {
          if (coordinator.onRestartFailed(token)) message("Couldn't install the update. Your photos are safe; try restarting to update again.")
        }
        .addOnCanceledListener {
          if (coordinator.onRestartFailed(token)) message("Update installation was canceled. You can try again when you're ready.")
        }
    } catch (_: Exception) {
      if (coordinator.onRestartFailed(token)) message("Couldn't install the update. Try restarting to update again.")
    }
  }

  fun canReviewAtPause(): Boolean = canShowPlayUi() && naturalPause() &&
    !feedback.value.reviewBusy && !updates.value.isChecking && updates.value.activePrompt == null &&
    updates.value.status in reviewCompatibleStatuses && !updates.value.isCompleting

  fun requestReviewIfEligible() {
    if (!canReviewAtPause() || reviewEvaluatedThisPause) return
    reviewEvaluatedThisPause = true
    feedback.update { it.copy(reviewBusy = true) }
    reviewJob = viewModelScope.launch {
      try {
        val tracker = container.reviewTracker.await()
        val reserved = withContext(Dispatchers.IO) {
          tracker.tryBeginAttempt(container.playSessionId, System.currentTimeMillis()).also {
            if (it) reservedTracker = tracker
          }
        }
        if (!reserved) return@launch
        val info = withTimeout(15_000) { reviewManager.requestReviewFlow().awaitValue() }
        if (!reviewStillSafe()) return@launch
        reviewInfo = info
        feedback.update { it.copy(reviewReady = true) }
      } catch (error: Exception) {
        // Reviews are best-effort. Never surface failures or imply a rating was submitted.
        if (error is CancellationException) throw error
      } finally {
        if (reviewInfo == null) finishReview()
      }
    }
  }

  fun launchReadyReview(activity: Activity) {
    val info = reviewInfo ?: return
    if (!reviewStillSafe() || activity.isFinishing || activity.isDestroyed) {
      finishReview()
      return
    }
    reviewInfo = null
    reviewLaunched = true
    feedback.update { it.copy(reviewReady = false) }
    try {
      reviewManager.launchReviewFlow(activity, info).addOnCompleteListener {
        // Completion says nothing about whether the dialog was shown or a review submitted.
        finishReview()
      }
    } catch (_: Exception) {
      finishReview()
    }
  }

  private fun finishReview() {
    reviewInfo = null
    reviewLaunched = false
    reservedTracker?.finishAttempt()
    reservedTracker = null
    feedback.update { it.copy(reviewReady = false, reviewBusy = false) }
  }

  private fun reviewStillSafe(): Boolean = canShowPlayUi() && naturalPause() &&
    !updates.value.isChecking && updates.value.activePrompt == null &&
    updates.value.status in reviewCompatibleStatuses && !updates.value.isCompleting

  private fun canShowPlayUi(): Boolean = feedback.value.foreground && uiReady &&
    appState?.value?.screen != null && appState?.value?.screen != AppScreen.ONBOARDING &&
    !workSafety().isBusy

  private fun naturalPause(): Boolean = appState?.value?.let { state ->
    state.screen == AppScreen.HOME && !workSafety().isBusy &&
      state.selectedImages.isEmpty() && state.compressedImages.isEmpty() && state.message == null
  } ?: false

  private fun workSafety(): WorkSafety = appState?.value?.let { state ->
    WorkSafety(
      isBusy = state.isCompressing || state.isSaving || state.isLoadingImages || state.isExternalFlowActive,
      hasPendingWork = state.selectedImages.isNotEmpty() || state.compressedImages.any { it.savedUri == null },
    )
  } ?: WorkSafety(isBusy = true, hasPendingWork = true)

  private fun message(text: String) { feedback.update { it.copy(message = text) } }
  fun consumeMessage() { feedback.update { it.copy(message = null) } }

  override fun onCleared() {
    onStop()
    activeCheck?.let(coordinator::onCheckFailed)
    // Unlike rotation (which retains this ViewModel), finishing the host destroys its result
    // registry. Release that orphaned consent lease and recover from fresh Play state next time.
    ownedPromptId?.let(coordinator::abandonPrompt)
    reviewJob?.cancel()
    finishReview()
  }

  private fun AppUpdateInfo.toUpdateInfo() = UpdateInfo(
    availability = when (updateAvailability()) {
      UpdateAvailability.UPDATE_AVAILABLE -> Availability.AVAILABLE
      UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> Availability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
      else -> Availability.NONE
    },
    flexibleAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
    immediateAllowed = isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
    priority = updatePriority(), stalenessDays = clientVersionStalenessDays(),
    installPhase = installStatus().toPhase(), bytesDownloaded = bytesDownloaded(),
    totalBytesToDownload = totalBytesToDownload(), availableVersionCode = availableVersionCode(),
  )

  private fun updateErrorMessage(error: Exception): String = when ((error as? InstallException)?.errorCode) {
    InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND -> "Google Play isn't available on this device. You can keep using the app."
    InstallErrorCode.ERROR_APP_NOT_OWNED -> "Install this app from Google Play to receive in-app updates."
    InstallErrorCode.ERROR_API_NOT_AVAILABLE -> "In-app updates aren't available for this installation. Check Google Play for updates."
    else -> "Couldn't check for updates. Check your connection and try again."
  }

  private companion object {
    val reviewCompatibleStatuses = setOf(UpdateStatus.IDLE, UpdateStatus.UP_TO_DATE, UpdateStatus.FAILED, UpdateStatus.CANCELED, UpdateStatus.INSTALLED)
  }
}

private fun Int.toPhase(): InstallPhase = when (this) {
  InstallStatus.PENDING -> InstallPhase.PENDING
  InstallStatus.DOWNLOADING -> InstallPhase.DOWNLOADING
  InstallStatus.DOWNLOADED -> InstallPhase.DOWNLOADED
  InstallStatus.INSTALLING -> InstallPhase.INSTALLING
  InstallStatus.INSTALLED -> InstallPhase.INSTALLED
  InstallStatus.FAILED -> InstallPhase.FAILED
  InstallStatus.CANCELED -> InstallPhase.CANCELED
  else -> InstallPhase.NONE
}

private suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
  addOnCompleteListener { task ->
    if (continuation.isActive) {
      when {
        task.isCanceled -> continuation.cancel()
        task.isSuccessful -> continuation.resume(task.result)
        else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Play request failed"))
      }
    }
  }
}

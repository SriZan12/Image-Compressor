package com.example.imagecompressor.play.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immediate updates are a release policy, never an inference from update availability alone. */
data class UpdatePolicy(
  val criticalUpdatesEnabled: Boolean = false,
  val minimumCriticalPriority: Int = 5,
  val minimumCriticalStalenessDays: Int = 7,
) {
  init {
    require(minimumCriticalPriority in 0..5)
    require(minimumCriticalStalenessDays >= 0)
  }

  fun isCritical(info: UpdateInfo): Boolean =
    criticalUpdatesEnabled && info.priority >= minimumCriticalPriority &&
      info.stalenessDays != null && info.stalenessDays >= minimumCriticalStalenessDays
}

data class WorkSafety(val isBusy: Boolean = false, val hasPendingWork: Boolean = false) {
  val canRestart: Boolean get() = !isBusy && !hasPendingWork
}

enum class UpdateAvailability { NONE, AVAILABLE, DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS }
enum class InstallPhase { NONE, PENDING, DOWNLOADING, DOWNLOADED, INSTALLING, INSTALLED, FAILED, CANCELED }
enum class UpdateType { FLEXIBLE, IMMEDIATE }
enum class PromptResult { ACCEPTED, CANCELED, FAILED }
enum class UpdateStatus { IDLE, UP_TO_DATE, AVAILABLE, PENDING, DOWNLOADING, DOWNLOADED, INSTALLING, INSTALLED, FAILED, CANCELED }
enum class UpdateFailure { CHECK, FLOW, DOWNLOAD, COMPLETION }

data class UpdateInfo(
  val availability: UpdateAvailability,
  val flexibleAllowed: Boolean = false,
  val immediateAllowed: Boolean = false,
  val priority: Int = 0,
  val stalenessDays: Int? = null,
  val installPhase: InstallPhase = InstallPhase.NONE,
  val bytesDownloaded: Long = 0,
  val totalBytesToDownload: Long = 0,
  val availableVersionCode: Int = 0,
)

data class UpdatePrompt(val id: Long, val type: UpdateType)

data class UpdateState(
  val status: UpdateStatus = UpdateStatus.IDLE,
  val isChecking: Boolean = false,
  val activePrompt: UpdatePrompt? = null,
  val isCompleting: Boolean = false,
  val bytesDownloaded: Long = 0,
  val totalBytesToDownload: Long = 0,
  val availableVersionCode: Int = 0,
  val lastCheckWasManual: Boolean = false,
  val failure: UpdateFailure? = null,
) {
  /** Shared synchronous gate for photo commands, including taps queued before UI recomposition. */
  val blocksPhotoWork: Boolean
    get() = isCompleting || status == UpdateStatus.INSTALLING || activePrompt?.type == UpdateType.IMMEDIATE

  val downloadFraction: Float?
    get() = if (totalBytesToDownload > 0) {
      (bytesDownloaded.toDouble() / totalBytesToDownload).coerceIn(0.0, 1.0).toFloat()
    } else null
}

/**
 * Process-session coordinator. The owner calls methods on the main thread, retains this object in
 * its application container, and keeps Activity/SDK objects in an adapter. Tokens reject late Task
 * callbacks after timeout, and install events take precedence over older availability queries.
 */
class UpdateCoordinator(private val policy: UpdatePolicy = UpdatePolicy()) {
  private val mutableState = MutableStateFlow(UpdateState())
  val state: StateFlow<UpdateState> = mutableState.asStateFlow()
  private var nextId = 0L
  private var installRevision = 0L
  private var check: Check? = null
  private var candidate: Candidate? = null
  private var completingId: Long? = null
  private var completionAcknowledged = false
  private var automaticPromptAttempted = false
  private var recoverySuppressed = false

  private data class Check(
    val id: Long,
    val manual: Boolean,
    val installRevision: Long,
    val acknowledgedCompletionId: Long?,
    val installationInProgress: Boolean,
  )
  private data class Candidate(val info: UpdateInfo, val manual: Boolean, val recovering: Boolean)

  /** Foreground checks are allowed during consent so Play state can be reconciled after a return. */
  fun beginCheck(manual: Boolean = false): Long? {
    if (check != null || (completingId != null && !completionAcknowledged)) return null
    val request = Check(
      ++nextId,
      manual,
      installRevision,
      completingId.takeIf { completionAcknowledged },
      installationInProgress = state.value.status == UpdateStatus.INSTALLING,
    )
    check = request
    candidate = null
    if (manual) recoverySuppressed = false
    mutableState.value = state.value.copy(isChecking = true, lastCheckWasManual = manual)
    return request.id
  }

  /** Returns false for a stale/timed-out callback; do not retain its SDK AppUpdateInfo in that case. */
  fun onInfo(checkId: Long, info: UpdateInfo): Boolean {
    val request = check?.takeIf { it.id == checkId } ?: return false
    check = null
    mutableState.value = state.value.copy(isChecking = false, availableVersionCode = info.availableVersionCode)
    // A listener event received since this request began is newer than its snapshot.
    if (request.installRevision != installRevision) return true

    val reconcilesCompletion = request.acknowledgedCompletionId != null &&
      request.acknowledgedCompletionId == completingId && completionAcknowledged
    val reconcilesInstallation = reconcilesCompletion ||
      (request.installationInProgress && state.value.status == UpdateStatus.INSTALLING)
    if (info.installPhase != InstallPhase.NONE) {
      applyInstallPhase(
        info.installPhase,
        info.bytesDownloaded,
        info.totalBytesToDownload,
        authoritativeTerminal = reconcilesInstallation,
      )
    }
    // Listeners are detached in the background. A terminal snapshot requested after flexible
    // completion acknowledgment or accepted immediate consent can recover a missed install event.
    // The revision guard above excludes snapshots requested before either transition.
    if (reconcilesInstallation && info.installPhase in terminalInstallPhases) return true
    if (state.value.activePrompt != null) return true
    // A new foreground query can reveal that a download canceled while no listener was attached.
    // Only terminal ready/installing states are sticky; a genuinely newer empty snapshot clears
    // transient progress. A query overtaken by a listener was already rejected above.
    if (info.installPhase == InstallPhase.NONE &&
      state.value.status in setOf(UpdateStatus.PENDING, UpdateStatus.DOWNLOADING)) {
      mutableState.value = state.value.copy(status = UpdateStatus.IDLE, bytesDownloaded = 0, totalBytesToDownload = 0)
    }
    val recovering = info.availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
    if (recovering && state.value.status !in setOf(UpdateStatus.DOWNLOADED, UpdateStatus.INSTALLED)) {
      candidate = Candidate(info, request.manual, recovering = true)
      if (state.value.status !in activeInstallStatuses) {
        mutableState.value = state.value.copy(status = UpdateStatus.AVAILABLE, failure = null)
      }
      return true
    }
    if (state.value.status in activeInstallStatuses) return true

    if (info.availability == UpdateAvailability.NONE) {
      mutableState.value = state.value.copy(status = UpdateStatus.UP_TO_DATE, failure = null)
      candidate = null
      return true
    }

    candidate = Candidate(info, request.manual, recovering = false)
    mutableState.value = state.value.copy(status = UpdateStatus.AVAILABLE, failure = null)
    return true
  }

  fun onCheckFailed(checkId: Long): Boolean {
    val request = check?.takeIf { it.id == checkId } ?: return false
    check = null
    candidate = null
    // Connectivity must not hide an already downloaded update or its restart action.
    val current = state.value
    mutableState.value = current.copy(
      isChecking = false,
      status = if (current.status in activeInstallStatuses) current.status else UpdateStatus.FAILED,
      failure = UpdateFailure.CHECK,
      lastCheckWasManual = request.manual,
    )
    return true
  }

  /** Returns a single-use launch reservation. Call only while the host Activity is resumed. */
  fun takePrompt(safety: WorkSafety): UpdatePrompt? {
    if (safety.isBusy || state.value.activePrompt != null || completingId != null) return null
    val next = candidate ?: return null
    if (!next.manual && (recoverySuppressed || (automaticPromptAttempted && !next.recovering))) return null
    val type = when {
      next.recovering -> {
        if (!policy.isCritical(next.info) || !next.info.immediateAllowed || !safety.canRestart) return null
        UpdateType.IMMEDIATE
      }
      policy.isCritical(next.info) && next.info.immediateAllowed -> {
        if (!safety.canRestart) return null
        UpdateType.IMMEDIATE
      }
      next.info.flexibleAllowed -> UpdateType.FLEXIBLE
      else -> return null
    }
    candidate = null
    automaticPromptAttempted = true
    val prompt = UpdatePrompt(++nextId, type)
    mutableState.value = state.value.copy(activePrompt = prompt, failure = null)
    return prompt
  }

  fun onPromptResult(promptId: Long, result: PromptResult): Boolean {
    val prompt = state.value.activePrompt?.takeIf { it.id == promptId } ?: return false
    candidate = null
    val current = state.value
    val installFailedBeforeResult = current.status == UpdateStatus.CANCELED ||
      (current.status == UpdateStatus.FAILED && current.failure == UpdateFailure.DOWNLOAD)
    val status = when {
      installFailedBeforeResult -> current.status
      current.status in activeInstallStatuses -> current.status
      result == PromptResult.ACCEPTED && prompt.type == UpdateType.IMMEDIATE -> UpdateStatus.INSTALLING
      result == PromptResult.ACCEPTED -> UpdateStatus.PENDING
      result == PromptResult.CANCELED -> UpdateStatus.CANCELED
      else -> UpdateStatus.FAILED
    }
    if (result != PromptResult.ACCEPTED) recoverySuppressed = true
    else installRevision++ // Ignore availability queries started before Play accepted consent.
    mutableState.value = current.copy(
      activePrompt = null,
      status = status,
      failure = when {
        installFailedBeforeResult -> current.failure
        result == PromptResult.FAILED -> UpdateFailure.FLOW
        else -> null
      },
    )
    return true
  }

  /**
   * Only use when the adapter knows a launch cannot deliver a result anymore. Never abandon on
   * normal pause/stop or rotation: ActivityResultRegistry owns that in-flight result.
   */
  fun abandonPrompt(promptId: Long): Boolean {
    if (state.value.activePrompt?.id != promptId) return false
    mutableState.value = state.value.copy(activePrompt = null)
    return true
  }

  fun onInstallState(phase: InstallPhase, bytesDownloaded: Long = 0, totalBytesToDownload: Long = 0) {
    installRevision++
    candidate = null
    applyInstallPhase(phase, bytesDownloaded, totalBytesToDownload, authoritativeTerminal = true)
  }

  private fun applyInstallPhase(
    phase: InstallPhase,
    bytes: Long,
    total: Long,
    authoritativeTerminal: Boolean = false,
  ) {
    if (phase == InstallPhase.NONE) return
    val current = state.value
    val status = when (phase) {
      InstallPhase.NONE -> return
      InstallPhase.PENDING -> UpdateStatus.PENDING
      InstallPhase.DOWNLOADING -> UpdateStatus.DOWNLOADING
      InstallPhase.DOWNLOADED -> UpdateStatus.DOWNLOADED
      InstallPhase.INSTALLING -> UpdateStatus.INSTALLING
      InstallPhase.INSTALLED -> UpdateStatus.INSTALLED
      InstallPhase.FAILED -> UpdateStatus.FAILED
      InstallPhase.CANCELED -> UpdateStatus.CANCELED
    }
    val terminalFailure = status == UpdateStatus.CANCELED || status == UpdateStatus.FAILED
    // A completion Task only acknowledges the request. An authoritative later listener failure
    // must release the work gate; stale progress/query snapshots still cannot erase ready state.
    if (current.status == UpdateStatus.INSTALLED && status != UpdateStatus.INSTALLED) return
    if (current.status in readyOrInstallingStatuses && status !in readyOrInstallingStatuses &&
      !(authoritativeTerminal && terminalFailure)) return
    if (current.status == UpdateStatus.INSTALLING && status == UpdateStatus.DOWNLOADED) return
    if (terminalFailure) recoverySuppressed = true
    val completionFinished = (authoritativeTerminal && terminalFailure) || status == UpdateStatus.INSTALLED
    if (completionFinished) {
      completingId = null
      completionAcknowledged = false
    }
    mutableState.value = current.copy(
      status = status,
      isCompleting = if (completionFinished) false else current.isCompleting,
      bytesDownloaded = if (status == UpdateStatus.DOWNLOADING) bytes.coerceAtLeast(0) else current.bytesDownloaded,
      totalBytesToDownload = if (status == UpdateStatus.DOWNLOADING) total.coerceAtLeast(0) else current.totalBytesToDownload,
      failure = if (status == UpdateStatus.FAILED) UpdateFailure.DOWNLOAD else null,
    )
  }

  /** This is the only completion entry point; callers must invoke it from explicit user consent. */
  fun beginRestart(safety: WorkSafety): Long? {
    if (!safety.canRestart || state.value.status != UpdateStatus.DOWNLOADED ||
      state.value.activePrompt != null || completingId != null) return null
    val id = ++nextId
    completingId = id
    installRevision++ // Availability snapshots requested before completion cannot unlock work.
    completionAcknowledged = false
    candidate = null
    mutableState.value = state.value.copy(isCompleting = true, failure = null)
    return id
  }

  fun onRestartCompleted(restartId: Long): Boolean {
    if (completingId != restartId) return false
    if (completionAcknowledged) return false
    completionAcknowledged = true
    installRevision++
    // Task success means Play accepted completion. Keep work blocked until restart or a later
    // explicit terminal install event/snapshot, including while the Activity is briefly resumed.
    mutableState.value = state.value.copy(status = UpdateStatus.INSTALLING, isCompleting = true)
    return true
  }

  fun onRestartFailed(restartId: Long): Boolean {
    if (completingId != restartId || completionAcknowledged || state.value.status == UpdateStatus.INSTALLED) return false
    completingId = null
    mutableState.value = state.value.copy(
      status = UpdateStatus.DOWNLOADED,
      isCompleting = false,
      failure = UpdateFailure.COMPLETION,
    )
    return true
  }

  private companion object {
    val terminalInstallPhases = setOf(InstallPhase.FAILED, InstallPhase.CANCELED, InstallPhase.INSTALLED)
    val readyOrInstallingStatuses = setOf(UpdateStatus.DOWNLOADED, UpdateStatus.INSTALLING, UpdateStatus.INSTALLED)
    val activeInstallStatuses = readyOrInstallingStatuses + setOf(UpdateStatus.PENDING, UpdateStatus.DOWNLOADING)
  }
}

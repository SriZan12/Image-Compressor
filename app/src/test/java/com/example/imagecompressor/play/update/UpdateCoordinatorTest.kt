package com.example.imagecompressor.play.update

import org.junit.Assert.*
import org.junit.Test

class UpdateCoordinatorTest {
  private val available = UpdateInfo(
    availability = UpdateAvailability.AVAILABLE,
    flexibleAllowed = true,
    immediateAllowed = true,
    priority = 5,
    stalenessDays = 14,
    availableVersionCode = 12,
  )
  private val criticalPolicy = UpdatePolicy(criticalUpdatesEnabled = true)

  private fun UpdateCoordinator.info(info: UpdateInfo = available, manual: Boolean = false) {
    assertTrue(onInfo(requireNotNull(beginCheck(manual)), info))
  }

  @Test fun flexibleIsDefaultEvenForHighPriorityStaleUpdates() {
    val coordinator = UpdateCoordinator()
    coordinator.info()
    assertEquals(UpdateType.FLEXIBLE, coordinator.takePrompt(WorkSafety())?.type)
  }

  @Test fun onlyOneCheckAndPromptCanBeInFlight() {
    val coordinator = UpdateCoordinator()
    val request = requireNotNull(coordinator.beginCheck())
    assertNull(coordinator.beginCheck(manual = true))
    coordinator.onInfo(request, available)
    val prompt = requireNotNull(coordinator.takePrompt(WorkSafety()))
    assertNull(coordinator.takePrompt(WorkSafety()))
    // Status refresh while consent is displayed must not queue another launch.
    coordinator.info(manual = true)
    assertEquals(prompt, coordinator.state.value.activePrompt)
    assertNull(coordinator.takePrompt(WorkSafety()))
    assertTrue(coordinator.onPromptResult(prompt.id, PromptResult.ACCEPTED))
    assertFalse(coordinator.onPromptResult(prompt.id, PromptResult.CANCELED))
    assertEquals(UpdateStatus.PENDING, coordinator.state.value.status)
  }

  @Test fun automaticPromptDoesNotRepeatAfterCancellationButManualCheckCanRetry() {
    val coordinator = UpdateCoordinator()
    coordinator.info()
    val first = requireNotNull(coordinator.takePrompt(WorkSafety()))
    coordinator.onPromptResult(first.id, PromptResult.CANCELED)
    assertEquals(UpdateStatus.CANCELED, coordinator.state.value.status)
    coordinator.info()
    assertNull(coordinator.takePrompt(WorkSafety()))
    coordinator.info(manual = true)
    assertNotNull(coordinator.takePrompt(WorkSafety()))
  }

  @Test fun failedLaunchAlsoNeedsManualRetryInThisSession() {
    val coordinator = UpdateCoordinator()
    coordinator.info()
    coordinator.onPromptResult(requireNotNull(coordinator.takePrompt(WorkSafety())).id, PromptResult.FAILED)
    assertEquals(UpdateFailure.FLOW, coordinator.state.value.failure)
    coordinator.info()
    assertNull(coordinator.takePrompt(WorkSafety()))
    coordinator.info(manual = true)
    assertNotNull(coordinator.takePrompt(WorkSafety()))
  }

  @Test fun flexibleConsentWaitsUntilBusyWorkFinishesButDoesNotDiscardPendingPhotos() {
    val coordinator = UpdateCoordinator()
    coordinator.info()
    assertNull(coordinator.takePrompt(WorkSafety(isBusy = true)))
    assertEquals(UpdateType.FLEXIBLE, coordinator.takePrompt(WorkSafety(hasPendingWork = true))?.type)
  }

  @Test fun immediateRequiresExplicitPolicyPriorityAndKnownStalenessThresholds() {
    for (info in listOf(available.copy(priority = 4), available.copy(stalenessDays = 6), available.copy(stalenessDays = null))) {
      val coordinator = UpdateCoordinator(criticalPolicy)
      coordinator.info(info)
      assertEquals(UpdateType.FLEXIBLE, coordinator.takePrompt(WorkSafety())?.type)
    }
    val coordinator = UpdateCoordinator(criticalPolicy)
    coordinator.info(available.copy(stalenessDays = 7))
    assertEquals(UpdateType.IMMEDIATE, coordinator.takePrompt(WorkSafety())?.type)
    assertTrue(coordinator.state.value.blocksPhotoWork)
  }

  @Test fun criticalImmediateWaitsForBothBusyAndUnsavedWork() {
    val coordinator = UpdateCoordinator(criticalPolicy)
    coordinator.info()
    assertNull(coordinator.takePrompt(WorkSafety(isBusy = true)))
    assertNull(coordinator.takePrompt(WorkSafety(hasPendingWork = true)))
    assertEquals(UpdateType.IMMEDIATE, coordinator.takePrompt(WorkSafety())?.type)
    assertTrue(coordinator.state.value.blocksPhotoWork)
  }

  @Test fun unavailableImmediateNeverFallsBackToImmediateAgainstPolicy() {
    val coordinator = UpdateCoordinator()
    coordinator.info(available.copy(flexibleAllowed = false))
    assertNull(coordinator.takePrompt(WorkSafety()))
    val critical = UpdateCoordinator(criticalPolicy)
    critical.info(available.copy(immediateAllowed = false))
    assertEquals(UpdateType.FLEXIBLE, critical.takePrompt(WorkSafety())?.type)
  }

  @Test fun downloadProgressIsBoundedAndUnknownLengthIsIndeterminate() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADING, 5, 10)
    assertEquals(0.5f, coordinator.state.value.downloadFraction)
    coordinator.onInstallState(InstallPhase.DOWNLOADING, 20, 10)
    assertEquals(1f, coordinator.state.value.downloadFraction)
    coordinator.onInstallState(InstallPhase.DOWNLOADING, -1, 0)
    assertEquals(0L, coordinator.state.value.bytesDownloaded)
    assertNull(coordinator.state.value.downloadFraction)
  }

  @Test fun downloadedRecoveryWorksInANewCoordinatorWithoutOfferingConsentAgain() {
    val coordinator = UpdateCoordinator()
    coordinator.info(available.copy(installPhase = InstallPhase.DOWNLOADED))
    assertEquals(UpdateStatus.DOWNLOADED, coordinator.state.value.status)
    assertNull(coordinator.takePrompt(WorkSafety()))
    assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
  }

  @Test fun staleQueryCannotEraseDownloadListenerProgressOrQueueASecondPrompt() {
    val coordinator = UpdateCoordinator()
    val request = requireNotNull(coordinator.beginCheck())
    coordinator.onInstallState(InstallPhase.DOWNLOADING, 50, 100)
    coordinator.onInfo(request, available)
    assertEquals(UpdateStatus.DOWNLOADING, coordinator.state.value.status)
    assertEquals(0.5f, coordinator.state.value.downloadFraction)
    assertNull(coordinator.takePrompt(WorkSafety()))
  }

  @Test fun downloadedStateSurvivesStaleAvailabilityAndEarlierInstallSnapshots() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    coordinator.info(available.copy(availability = UpdateAvailability.NONE, installPhase = InstallPhase.DOWNLOADING))
    coordinator.onInstallState(InstallPhase.PENDING)
    coordinator.info(available.copy(installPhase = InstallPhase.CANCELED))
    assertEquals(UpdateStatus.DOWNLOADED, coordinator.state.value.status)
  }

  @Test fun lateCheckCallbacksAfterTimeoutCannotOverrideNewerResults() {
    val coordinator = UpdateCoordinator()
    val first = requireNotNull(coordinator.beginCheck())
    assertTrue(coordinator.onCheckFailed(first))
    val second = requireNotNull(coordinator.beginCheck(manual = true))
    assertFalse(coordinator.onInfo(first, available))
    assertFalse(coordinator.onCheckFailed(first))
    coordinator.onInfo(second, available.copy(availability = UpdateAvailability.NONE))
    assertEquals(UpdateStatus.UP_TO_DATE, coordinator.state.value.status)
    assertFalse(coordinator.state.value.isChecking)
  }

  @Test fun failedRefreshPreservesReadyRestartAction() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    coordinator.onCheckFailed(requireNotNull(coordinator.beginCheck(manual = true)))
    assertEquals(UpdateStatus.DOWNLOADED, coordinator.state.value.status)
    assertEquals(UpdateFailure.CHECK, coordinator.state.value.failure)
    assertNotNull(coordinator.beginRestart(WorkSafety()))
  }

  @Test fun restartingIsOnlyExplicitSingleFlightAndRequiresAllWorkToBeSafe() {
    val coordinator = UpdateCoordinator()
    assertNull(coordinator.beginRestart(WorkSafety()))
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
    assertNull(coordinator.beginRestart(WorkSafety(isBusy = true)))
    assertNull(coordinator.beginRestart(WorkSafety(hasPendingWork = true)))
    val id = requireNotNull(coordinator.beginRestart(WorkSafety()))
    assertTrue(coordinator.state.value.isCompleting)
    assertTrue(coordinator.state.value.blocksPhotoWork)
    assertNull(coordinator.beginRestart(WorkSafety()))
    assertNull(coordinator.beginCheck())
    assertTrue(coordinator.onRestartFailed(id))
    assertEquals(UpdateStatus.DOWNLOADED, coordinator.state.value.status)
    assertEquals(UpdateFailure.COMPLETION, coordinator.state.value.failure)
    assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
    assertFalse(coordinator.onRestartFailed(id))
    assertNotNull(coordinator.beginRestart(WorkSafety()))
  }

  @Test fun successfulCompletionKeepsWorkLockedAndLateFailureCannotUndoIt() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    val id = requireNotNull(coordinator.beginRestart(WorkSafety()))
    assertTrue(coordinator.onRestartCompleted(id))
    assertEquals(UpdateStatus.INSTALLING, coordinator.state.value.status)
    assertTrue(coordinator.state.value.isCompleting)
    assertTrue(coordinator.state.value.blocksPhotoWork)
    assertFalse(coordinator.onRestartFailed(id))
    assertFalse(coordinator.onRestartCompleted(id))
  }

  @Test fun immediateRecoveryRequiresCriticalPolicyAndSafeWork() {
    val info = available.copy(availability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
    val disabled = UpdateCoordinator()
    disabled.info(info)
    assertNull(disabled.takePrompt(WorkSafety()))
    val enabled = UpdateCoordinator(criticalPolicy)
    enabled.info(info.copy(installPhase = InstallPhase.DOWNLOADING))
    assertNull(enabled.takePrompt(WorkSafety(hasPendingWork = true)))
    assertNull(enabled.takePrompt(WorkSafety(isBusy = true)))
    assertEquals(UpdateType.IMMEDIATE, enabled.takePrompt(WorkSafety())?.type)
  }

  @Test fun abandonedImmediateCanRecoverWithoutRepromptingAfterUserCancellation() {
    val coordinator = UpdateCoordinator(criticalPolicy)
    coordinator.info()
    val initial = requireNotNull(coordinator.takePrompt(WorkSafety()))
    assertTrue(coordinator.abandonPrompt(initial.id))
    assertFalse(coordinator.onPromptResult(initial.id, PromptResult.CANCELED))
    val recovery = available.copy(availability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
    coordinator.info(recovery)
    val resumed = requireNotNull(coordinator.takePrompt(WorkSafety()))
    coordinator.onPromptResult(resumed.id, PromptResult.CANCELED)
    coordinator.info(recovery)
    assertNull(coordinator.takePrompt(WorkSafety()))
    coordinator.info(recovery, manual = true)
    assertNotNull(coordinator.takePrompt(WorkSafety()))
  }

  @Test fun downloadedListenerBeforeConsentResultStillOffersSafeRestart() {
    val coordinator = UpdateCoordinator()
    coordinator.info()
    val prompt = requireNotNull(coordinator.takePrompt(WorkSafety()))
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    assertNull(coordinator.beginRestart(WorkSafety()))
    coordinator.onPromptResult(prompt.id, PromptResult.ACCEPTED)
    assertEquals(UpdateStatus.DOWNLOADED, coordinator.state.value.status)
    assertNotNull(coordinator.beginRestart(WorkSafety()))
  }

  @Test fun unavailableCheckDoesNotConsumeAutomaticOfferForLaterForegroundChecks() {
    val coordinator = UpdateCoordinator()
    coordinator.info(available.copy(availability = UpdateAvailability.NONE))
    assertEquals(UpdateStatus.UP_TO_DATE, coordinator.state.value.status)
    assertNull(coordinator.takePrompt(WorkSafety()))
    coordinator.info()
    assertNotNull(coordinator.takePrompt(WorkSafety()))
  }

  @Test fun failedAndCanceledDownloadsExposeDistinctStates() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADING)
    coordinator.onInstallState(InstallPhase.FAILED)
    assertEquals(UpdateStatus.FAILED, coordinator.state.value.status)
    assertEquals(UpdateFailure.DOWNLOAD, coordinator.state.value.failure)
    coordinator.onInstallState(InstallPhase.CANCELED)
    assertEquals(UpdateStatus.CANCELED, coordinator.state.value.status)
    assertNull(coordinator.state.value.failure)
  }
  @Test fun freshEmptySnapshotRecoversFromMissedBackgroundCancellation() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADING, 25, 100)
    coordinator.info(available.copy(availability = UpdateAvailability.NONE))
    assertEquals(UpdateStatus.UP_TO_DATE, coordinator.state.value.status)
    assertEquals(0L, coordinator.state.value.bytesDownloaded)
    assertNull(coordinator.state.value.downloadFraction)
  }

  @Test fun consentAcceptedWhileQueryInFlightCannotBeUndoneByOlderSnapshot() {
    val coordinator = UpdateCoordinator()
    coordinator.info()
    val prompt = requireNotNull(coordinator.takePrompt(WorkSafety()))
    val check = requireNotNull(coordinator.beginCheck())
    coordinator.onPromptResult(prompt.id, PromptResult.ACCEPTED)
    coordinator.onInfo(check, available.copy(availability = UpdateAvailability.NONE))
    assertEquals(UpdateStatus.PENDING, coordinator.state.value.status)
    assertNull(coordinator.takePrompt(WorkSafety()))
  }

  @Test fun authoritativeFailureAfterAcknowledgedCompletionReleasesWorkAndAllowsRecovery() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    val completion = requireNotNull(coordinator.beginRestart(WorkSafety()))
    coordinator.onRestartCompleted(completion)
    coordinator.onInstallState(InstallPhase.FAILED)
    assertEquals(UpdateStatus.FAILED, coordinator.state.value.status)
    assertEquals(UpdateFailure.DOWNLOAD, coordinator.state.value.failure)
    assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
    assertFalse(coordinator.onRestartCompleted(completion))
    coordinator.info(available.copy(installPhase = InstallPhase.DOWNLOADED), manual = true)
    assertNotNull(coordinator.beginRestart(WorkSafety()))
  }

  @Test fun authoritativeCancellationAfterCompletionAlsoReleasesWork() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    coordinator.onRestartCompleted(requireNotNull(coordinator.beginRestart(WorkSafety())))
    coordinator.onInstallState(InstallPhase.CANCELED)
    assertEquals(UpdateStatus.CANCELED, coordinator.state.value.status)
    assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
    assertNotNull(coordinator.beginCheck(manual = true))
  }

  @Test fun immediateInstallationFailureCanBeRetriedManually() {
    val coordinator = UpdateCoordinator(criticalPolicy)
    coordinator.info()
    val prompt = requireNotNull(coordinator.takePrompt(WorkSafety()))
    coordinator.onPromptResult(prompt.id, PromptResult.ACCEPTED)
    assertEquals(UpdateStatus.INSTALLING, coordinator.state.value.status)
    coordinator.onInstallState(InstallPhase.FAILED)
    assertEquals(UpdateStatus.FAILED, coordinator.state.value.status)
    coordinator.info(manual = true)
    assertEquals(UpdateType.IMMEDIATE, coordinator.takePrompt(WorkSafety())?.type)
    assertTrue(coordinator.state.value.blocksPhotoWork)
  }

  @Test fun delayedConsentResultCannotHideAnAlreadyReportedInstallFailure() {
    val coordinator = UpdateCoordinator()
    coordinator.info()
    val prompt = requireNotNull(coordinator.takePrompt(WorkSafety()))
    coordinator.onInstallState(InstallPhase.FAILED)
    coordinator.onPromptResult(prompt.id, PromptResult.ACCEPTED)
    assertEquals(UpdateStatus.FAILED, coordinator.state.value.status)
    assertEquals(UpdateFailure.DOWNLOAD, coordinator.state.value.failure)
    assertNull(coordinator.state.value.activePrompt)
    assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
  }

  @Test fun completionFailureMissedWhileStoppedIsRecoveredByFreshQuery() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    val completion = requireNotNull(coordinator.beginRestart(WorkSafety()))
    coordinator.onRestartCompleted(completion)
    coordinator.info(available.copy(installPhase = InstallPhase.FAILED))
    assertEquals(UpdateStatus.FAILED, coordinator.state.value.status)
    assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
    assertEquals(UpdateFailure.DOWNLOAD, coordinator.state.value.failure)
    assertFalse(coordinator.onRestartFailed(completion))
    assertNotNull(coordinator.beginCheck(manual = true))
  }

  @Test fun completionCancellationMissedWhileStoppedIsRecoveredByFreshQuery() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    coordinator.onRestartCompleted(requireNotNull(coordinator.beginRestart(WorkSafety())))
    coordinator.info(available.copy(installPhase = InstallPhase.CANCELED))
    assertEquals(UpdateStatus.CANCELED, coordinator.state.value.status)
    assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
    assertNotNull(coordinator.beginCheck(manual = true))
  }

  @Test fun installedListenerAndFreshInstalledSnapshotBothReleaseCompletionWorkGate() {
    for (useListener in listOf(true, false)) {
      val coordinator = UpdateCoordinator()
      coordinator.onInstallState(InstallPhase.DOWNLOADED)
      val completion = requireNotNull(coordinator.beginRestart(WorkSafety()))
      coordinator.onRestartCompleted(completion)
      if (useListener) coordinator.onInstallState(InstallPhase.INSTALLED)
      else coordinator.info(available.copy(installPhase = InstallPhase.INSTALLED))
      assertEquals(UpdateStatus.INSTALLED, coordinator.state.value.status)
      assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
      assertFalse(coordinator.onRestartCompleted(completion))
      assertNotNull(coordinator.beginCheck())
    }
  }

  @Test fun queryRequestedBeforeCompletionCannotUnlockWorkAfterAcknowledgment() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    val oldCheck = requireNotNull(coordinator.beginCheck())
    val completion = requireNotNull(coordinator.beginRestart(WorkSafety()))
    coordinator.onRestartCompleted(completion)
    coordinator.onInfo(oldCheck, available.copy(installPhase = InstallPhase.FAILED))
    assertEquals(UpdateStatus.INSTALLING, coordinator.state.value.status)
    assertTrue(coordinator.state.value.isCompleting)
    assertTrue(coordinator.state.value.blocksPhotoWork)
    coordinator.info(available.copy(installPhase = InstallPhase.FAILED))
    assertFalse(coordinator.state.value.isCompleting)
    assertFalse(coordinator.state.value.blocksPhotoWork)
  }

  @Test fun queryRequestedBeforeCompletionCannotUnlockWhileCompletionTaskIsPending() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    val oldCheck = requireNotNull(coordinator.beginCheck())
    val completion = requireNotNull(coordinator.beginRestart(WorkSafety()))
    coordinator.onInfo(oldCheck, available.copy(installPhase = InstallPhase.CANCELED))
    assertTrue(coordinator.state.value.isCompleting)
    assertTrue(coordinator.state.value.blocksPhotoWork)
    assertEquals(UpdateStatus.DOWNLOADED, coordinator.state.value.status)
    assertNull(coordinator.beginCheck())
    coordinator.onRestartCompleted(completion)
    assertNotNull(coordinator.beginCheck())
  }

  @Test fun nonterminalOrFailedReconciliationCannotPrematurelyUnlockWork() {
    val coordinator = UpdateCoordinator()
    coordinator.onInstallState(InstallPhase.DOWNLOADED)
    coordinator.onRestartCompleted(requireNotNull(coordinator.beginRestart(WorkSafety())))
    for (phase in listOf(InstallPhase.NONE, InstallPhase.DOWNLOADED, InstallPhase.DOWNLOADING, InstallPhase.INSTALLING)) {
      coordinator.info(available.copy(installPhase = phase))
      assertEquals(UpdateStatus.INSTALLING, coordinator.state.value.status)
      assertTrue(coordinator.state.value.isCompleting)
    assertTrue(coordinator.state.value.blocksPhotoWork)
      assertNull(coordinator.beginRestart(WorkSafety()))
    }
    coordinator.onCheckFailed(requireNotNull(coordinator.beginCheck()))
    assertTrue(coordinator.state.value.isCompleting)
    assertTrue(coordinator.state.value.blocksPhotoWork)
    assertEquals(UpdateStatus.INSTALLING, coordinator.state.value.status)
  }

  @Test fun immediateFailureMissedWhileStoppedIsRecoveredByFreshQueryAndCanRetry() {
    val coordinator = UpdateCoordinator(criticalPolicy)
    coordinator.info()
    coordinator.onPromptResult(requireNotNull(coordinator.takePrompt(WorkSafety())).id, PromptResult.ACCEPTED)
    coordinator.info(available.copy(installPhase = InstallPhase.FAILED))
    assertEquals(UpdateStatus.FAILED, coordinator.state.value.status)
    assertEquals(UpdateFailure.DOWNLOAD, coordinator.state.value.failure)
    assertFalse(coordinator.state.value.isCompleting)
    coordinator.info(manual = true)
    assertEquals(UpdateType.IMMEDIATE, coordinator.takePrompt(WorkSafety())?.type)
  }

  @Test fun immediateCancellationMissedWhileStoppedIsRecoveredByFreshQueryAndCanRetry() {
    val coordinator = UpdateCoordinator(criticalPolicy)
    coordinator.info()
    coordinator.onPromptResult(requireNotNull(coordinator.takePrompt(WorkSafety())).id, PromptResult.ACCEPTED)
    coordinator.info(available.copy(installPhase = InstallPhase.CANCELED))
    assertEquals(UpdateStatus.CANCELED, coordinator.state.value.status)
    assertFalse(coordinator.state.value.isCompleting)
    coordinator.info(manual = true)
    assertEquals(UpdateType.IMMEDIATE, coordinator.takePrompt(WorkSafety())?.type)
  }

  @Test fun immediatePreConsentTerminalSnapshotCannotOverrideAcceptedInstallation() {
    val coordinator = UpdateCoordinator(criticalPolicy)
    coordinator.info()
    val prompt = requireNotNull(coordinator.takePrompt(WorkSafety()))
    val oldCheck = requireNotNull(coordinator.beginCheck())
    coordinator.onPromptResult(prompt.id, PromptResult.ACCEPTED)
    coordinator.onInfo(oldCheck, available.copy(installPhase = InstallPhase.FAILED))
    assertEquals(UpdateStatus.INSTALLING, coordinator.state.value.status)
    coordinator.info(available.copy(installPhase = InstallPhase.FAILED))
    assertEquals(UpdateStatus.FAILED, coordinator.state.value.status)
  }

}

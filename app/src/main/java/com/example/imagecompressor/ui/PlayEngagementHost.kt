package com.example.imagecompressor.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.imagecompressor.play.PlayEngagementViewModel
import com.example.imagecompressor.ui.state.AppScreen
import kotlinx.coroutines.delay

/** Activity-owned launchers never escape into the process-scoped engagement policies. */
@Composable
fun PlayEngagementHost(
  appViewModel: ImageCompressorViewModel,
  playViewModel: PlayEngagementViewModel,
  uiReady: Boolean,
) {
  val appState by appViewModel.uiState.collectAsStateWithLifecycle()
  val updates by playViewModel.updates.collectAsStateWithLifecycle()
  val feedback by playViewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val updateLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result -> playViewModel.onUpdateResult(result.resultCode) }

  SideEffect {
    playViewModel.bindAppState(appViewModel.uiState)
    playViewModel.setUiReady(uiReady)
  }
  DisposableEffect(lifecycleOwner, playViewModel) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> playViewModel.onStart()
        Lifecycle.Event.ON_RESUME -> playViewModel.onResume()
        Lifecycle.Event.ON_PAUSE -> playViewModel.onPause()
        Lifecycle.Event.ON_STOP -> playViewModel.onStop()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      playViewModel.onPause()
      playViewModel.onStop()
    }
  }

  LaunchedEffect(updates, appState, feedback.foreground, feedback.reviewBusy, uiReady) {
    if (uiReady && feedback.foreground &&
      lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      playViewModel.launchPendingUpdate(updateLauncher)
    }
  }

  val naturalPause = appState.screen == AppScreen.HOME &&
    appState.selectedImages.isEmpty() && appState.compressedImages.isEmpty() &&
    !appState.isLoadingImages && !appState.isCompressing && !appState.isSaving &&
    !appState.isExternalFlowActive && appState.message == null
  // No loop on reviewBusy: an ineligible evaluation must not immediately schedule itself again.
  LaunchedEffect(naturalPause, feedback.foreground, uiReady, updates.isChecking, updates.status) {
    if (naturalPause && feedback.foreground && uiReady) {
      delay(2_000)
      if (playViewModel.canReviewAtPause()) playViewModel.requestReviewIfEligible()
    }
  }
  LaunchedEffect(feedback.reviewReady, feedback.foreground, uiReady, appState) {
    if (feedback.reviewReady && uiReady && feedback.foreground &&
      activity != null && !activity.isFinishing && !activity.isDestroyed &&
      lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      playViewModel.launchReadyReview(activity)
    }
  }
}

private fun Context.findActivity(): Activity? {
  var current = this
  val visited = mutableSetOf<Context>()
  while (visited.add(current)) {
    if (current is Activity) return current
    current = (current as? ContextWrapper)?.baseContext ?: return null
  }
  return null
}

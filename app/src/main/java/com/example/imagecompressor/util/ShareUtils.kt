package com.example.imagecompressor.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.example.imagecompressor.R

object ShareUtils {
  /** Accept only URIs resolved from the repository's current durable receipts. */
  fun createShareUrisIntent(context: Context, uris: List<Uri>, onError: (String) -> Unit): Intent? {
    if (uris.isEmpty()) return null
    val activity = context.activity()
    if (activity != null && (activity.isDestroyed || activity.isFinishing || activity.isChangingConfigurations)) {
      onError(context.getString(R.string.play_share_ready_retry))
      return null
    }
    return try {
      val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("Compressed image", uris.first()).apply {
          uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
      }
      Intent.createChooser(intent, context.getString(R.string.play_share_title)).apply {
        if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } catch (_: SecurityException) {
      onError(context.getString(R.string.play_share_error))
      null
    }
  }

  /** Convenience for non-Compose clients; the app UI uses ActivityResultLauncher for lifecycle gates. */
  fun shareUris(context: Context, uris: List<Uri>, onError: (String) -> Unit) {
    val chooser = createShareUrisIntent(context, uris, onError) ?: return
    try {
      context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
      onError(context.getString(R.string.play_share_no_target))
    } catch (_: SecurityException) {
      onError(context.getString(R.string.play_share_error))
    }
  }

  private fun Context.activity(): Activity? {
    var current = this
    val visited = mutableSetOf<Context>()
    while (visited.add(current)) {
      if (current is Activity) return current
      current = (current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
  }
}

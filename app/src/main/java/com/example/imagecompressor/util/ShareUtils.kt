package com.example.imagecompressor.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

object ShareUtils {
  fun shareUris(context: Context, uris: List<Uri>, onError: (String) -> Unit) {
    if (uris.isEmpty()) return
    try {
      val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("Compressed image", uris.first()).apply {
          uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
      }
      val chooser = Intent.createChooser(intent, "Share compressed images")
      if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
      onError("No app is available to share images. Save them to your gallery instead.")
    } catch (_: SecurityException) {
      onError("This image can no longer be shared. Reopen the result or select the original again.")
    }
  }
}

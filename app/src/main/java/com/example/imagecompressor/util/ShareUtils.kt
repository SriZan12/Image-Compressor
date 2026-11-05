package com.example.imagecompressor.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.imagecompressor.data.model.CompressedImage

object ShareUtils {
  fun createShareIntent(context: Context, images: List<CompressedImage>): Intent? {
    if (images.isEmpty()) return null
    val uris = ArrayList(images.map { it.outputUri })
    val intent =
      if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
          type = images.first().format.mimeType
          putExtra(Intent.EXTRA_STREAM, uris.first())
        }
      } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
          type = "image/*"
          putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
      }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData =
      ClipData.newUri(context.contentResolver, "Compressed image", uris.first()).apply {
        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
      }
    return Intent.createChooser(intent, "Share compressed images")
  }

  fun createShareUriIntent(context: Context, uri: Uri, mimeType: String = "image/*"): Intent {
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Compressed image", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    return Intent.createChooser(intent, "Share compressed image")
  }
}

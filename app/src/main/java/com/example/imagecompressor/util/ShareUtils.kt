package com.example.imagecompressor.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.imagecompressor.data.model.CompressedImage

object ShareUtils {
  fun share(context: Context, images: List<CompressedImage>) {
    if (images.isEmpty()) return
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
    context.startActivity(Intent.createChooser(intent, "Share compressed images"))
  }

  fun shareUri(context: Context, uri: Uri, mimeType: String = "image/*") {
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Compressed image", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    context.startActivity(Intent.createChooser(intent, "Share compressed image"))
  }
}

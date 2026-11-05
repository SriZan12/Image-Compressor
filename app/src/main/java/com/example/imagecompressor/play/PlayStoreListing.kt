package com.example.imagecompressor.play

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Explicit Settings action: bypasses the quota-limited review dialog entirely. */
object PlayStoreListing {
  fun open(context: Context): Boolean {
    val id = context.packageName
    val store = Intent(Intent.ACTION_VIEW, "market://details?id=$id".toUri())
      .setPackage("com.android.vending")
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (tryOpen(context, store)) return true
    return tryOpen(
      context,
      Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$id".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }

  private fun tryOpen(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    false
  } catch (_: SecurityException) {
    false
  }
}

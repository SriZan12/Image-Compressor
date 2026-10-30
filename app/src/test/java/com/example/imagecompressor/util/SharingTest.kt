package com.example.imagecompressor.util

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SharingTest {
  @Test fun grantsReadAccessForEverySharedUriWithoutWritePermission() {
    var chooser: Intent? = null
    val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
      override fun startActivity(intent: Intent) { chooser = intent }
    }
    val uris = listOf(Uri.parse("content://outputs/1"), Uri.parse("content://outputs/2"))
    ShareUtils.shareUris(context, uris) { fail(it) }
    val target = chooser!!.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
    assertEquals(Intent.ACTION_SEND_MULTIPLE, target.action)
    assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertEquals(0, target.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    assertEquals(uris, (0 until target.clipData!!.itemCount).map { target.clipData!!.getItemAt(it).uri })
  }

  @Test fun missingShareTargetProducesAnActionableErrorInsteadOfCrashing() {
    val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
      override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
    }
    var message: String? = null
    ShareUtils.shareUris(context, listOf(Uri.parse("content://outputs/1"))) { message = it }
    assertTrue(message!!.contains("Save them to your gallery"))
  }

  @Test fun fileProviderDoesNotExposeTheDatabaseOrOtherPrivateFiles() {
    val context = RuntimeEnvironment.getApplication()
    assertThrows(IllegalArgumentException::class.java) {
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(context.filesDir, "private.txt"))
    }
    assertThrows(IllegalArgumentException::class.java) {
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", context.getDatabasePath("image-compressor.db"))
    }
  }
}

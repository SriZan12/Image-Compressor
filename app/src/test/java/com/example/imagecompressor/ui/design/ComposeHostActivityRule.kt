package com.example.imagecompressor.ui.design

import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Registers Compose's host only in Robolectric's per-test package manager.
 * Release host tests must not depend on a test-only activity in the shipping APK.
 * This outer rule runs before ActivityScenario starts and after its teardown.
 */
class ComposeHostActivityRule : ExternalResource() {
  override fun before() {
    val context = RuntimeEnvironment.getApplication()
    val packageManager = shadowOf(context.packageManager)
    val host = packageManager.addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
    host.enabled = true
    host.exported = true
    host.theme = android.R.style.Theme_Material_Light_NoActionBar
    packageManager.addOrUpdateActivity(host)
  }
}

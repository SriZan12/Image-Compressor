package com.example.imagecompressor.security

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.imagecompressor.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** Inspect the merged package so a new dependency cannot silently broaden access. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PrivacyContractTest {
  private val context get() = RuntimeEnvironment.getApplication()

  @Test fun offlinePhotoPickerWorkflowRequiresNoNetworkOrBroadMediaPermission() {
    val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
      .requestedPermissions.orEmpty().toSet()
    val forbidden = setOf("android.permission.INTERNET", "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.MANAGE_EXTERNAL_STORAGE",
      "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
      "android.permission.ACCESS_MEDIA_LOCATION")
    assertEquals("Photos stay local and are accessed through explicit picker grants", emptySet<String>(), requested.intersect(forbidden))
  }

  @Test fun privateResultsAreNotBackedUpOrExposedByAnExportedProvider() {
    assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    val provider = checkNotNull(context.packageManager.resolveContentProvider("${context.packageName}.fileprovider", 0))
    assertFalse(provider.exported)
    assertTrue("Sharing requires explicit temporary URI grants", provider.grantUriPermissions)
  }

  @Test fun cloudAndDeviceTransferExcludePhotosHistoryAndPreferencesInEveryStorageDomain() {
    val expected = setOf("root", "file", "database", "sharedpref", "external",
      "device_root", "device_file", "device_database", "device_sharedpref")
    val excluded = mutableMapOf<String, MutableSet<String>>()
    var channel: String? = null
    context.resources.getXml(R.xml.data_extraction_rules).use { xml ->
      while (xml.eventType != XmlPullParser.END_DOCUMENT) {
        when (xml.eventType) {
          XmlPullParser.START_TAG -> when (xml.name) {
            "cloud-backup", "device-transfer" -> channel = xml.name
            "exclude" -> {
              assertEquals("Exclude the entire domain", ".", xml.getAttributeValue(null, "path"))
              excluded.getOrPut(checkNotNull(channel)) { mutableSetOf() }.add(xml.getAttributeValue(null, "domain"))
            }
            "include" -> fail("Private photos and their metadata must not be included in transfer rules")
          }
          XmlPullParser.END_TAG -> if (xml.name in setOf("cloud-backup", "device-transfer")) channel = null
        }
        xml.next()
      }
    }
    assertEquals(expected, excluded["cloud-backup"])
    assertEquals(expected, excluded["device-transfer"])
  }
}

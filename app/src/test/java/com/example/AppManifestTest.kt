package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppManifestTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `app does not request internet permission`() {
    val permissions = context.packageManager
      .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
      .requestedPermissions
      .orEmpty()

    assertFalse(permissions.contains(Manifest.permission.INTERNET))
  }
}

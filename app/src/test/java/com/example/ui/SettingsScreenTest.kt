package com.example.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.calendar.CalendarSync
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val app = ApplicationProvider.getApplicationContext<Application>()
  private var syncEnabled by mutableStateOf(false)

  private fun showSettings() {
    composeTestRule.setContent {
      MyApplicationTheme {
        SettingsScreen(calendarSyncEnabled = syncEnabled, onCalendarSyncEnabledChange = { syncEnabled = it })
      }
    }
  }

  private fun clickCalendarSwitch() {
    composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("settings_calendar_sync_switch"))
    composeTestRule.onNodeWithTag("settings_calendar_sync_switch").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun `turning calendar sync on with permission enables it right away`() {
    shadowOf(app).grantPermissions(*CalendarSync.PERMISSIONS)
    showSettings()

    clickCalendarSwitch()

    assertEquals(true, syncEnabled)
  }

  @Test
  fun `turning calendar sync on without permission asks for it first`() {
    showSettings()

    clickCalendarSwitch()

    val request = shadowOf(composeTestRule.activity).lastRequestedPermission
    assertArrayEquals(CalendarSync.PERMISSIONS, request.requestedPermissions)
    assertEquals(false, syncEnabled)

    shadowOf(app).grantPermissions(*CalendarSync.PERMISSIONS)
    @Suppress("DEPRECATION")
    composeTestRule.activity.onRequestPermissionsResult(
      request.requestCode,
      request.requestedPermissions,
      IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_GRANTED }
    )
    composeTestRule.waitForIdle()

    assertEquals(true, syncEnabled)
  }

  @Test
  fun `turning calendar sync off disables it`() {
    shadowOf(app).grantPermissions(*CalendarSync.PERMISSIONS)
    syncEnabled = true
    showSettings()

    clickCalendarSwitch()

    assertEquals(false, syncEnabled)
  }
}

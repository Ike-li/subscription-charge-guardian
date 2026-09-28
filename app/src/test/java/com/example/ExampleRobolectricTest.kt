package com.example

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ocr.SubscriptionParser
import com.example.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("订阅卫士", appName)
  }

  @Test
  fun `test subscription parser for chinese billing screenshot`() {
    val sampleOcrText = """
        微信支付凭证
        商户：上海宽娱数码科技有限公司
        商品名称：哔哩哔哩大会员连续包月
        支付金额：¥15.00
        扣费方式：自动续费
        下次扣费时间：2026-10-15
    """.trimIndent()

    val parsed = SubscriptionParser.parse(sampleOcrText)
    assertEquals("哔哩哔哩大会员连续包月", parsed.name)
    assertEquals(15.0, parsed.amount ?: 0.0, 0.01)
    assertEquals("CNY", parsed.currency)
    assertEquals("MONTHLY", parsed.billingCycle)
    assertNotNull(parsed.nextBillingDate)
  }

  @Test
  fun `test date utilities`() {
    val now = System.currentTimeMillis()
    val startOfDay = DateUtils.getStartOfDay(now)
    assertTrue(startOfDay <= now)
  }

  @Test
  fun `launch MainActivity test`() {
    composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
  }

  @Test
  fun `first home entry asks for notification permission`() {
    composeTestRule.waitForIdle()

    val request = shadowOf(composeTestRule.activity).lastRequestedPermission

    assertNotNull(request)
    assertTrue(request.requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS))
  }

  @Test
  fun `saving a new subscription returns home with a saved snackbar`() {
    composeTestRule.onNodeWithTag("bottom_nav_item_add").performClick()
    composeTestRule.onNodeWithTag("input_subscription_name").performTextInput("B站大会员")
    composeTestRule.onNode(hasScrollToNodeAction())
      .performScrollToNode(hasTestTag("input_subscription_amount"))
    composeTestRule.onNodeWithTag("input_subscription_amount").performTextInput("15")
    composeTestRule.onNodeWithTag("save_subscription_button").performClick()

    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithText("已保存").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
  }

  @Test
  fun `bottom tabs switch between add and settings`() {
    composeTestRule.onNodeWithTag("bottom_nav_item_add").performClick()
    composeTestRule.onNodeWithTag("save_subscription_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("bottom_nav_item_settings").performClick()
    composeTestRule.onNodeWithTag("settings_screen_list").assertIsDisplayed()
  }
}

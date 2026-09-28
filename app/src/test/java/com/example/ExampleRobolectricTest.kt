package com.example

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
import org.robolectric.annotation.Config
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick

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
  fun `bottom tabs switch between add and settings`() {
    composeTestRule.onNodeWithTag("bottom_nav_item_add").performClick()
    composeTestRule.onNodeWithTag("save_subscription_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("bottom_nav_item_settings").performClick()
    composeTestRule.onNodeWithTag("settings_screen_list").assertIsDisplayed()
  }
}

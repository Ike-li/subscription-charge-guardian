package com.example.ocr

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SubscriptionParserTest {

  @Test
  fun `HK dollar amount is detected as HKD`() {
    val parsed = SubscriptionParser.parse(
      """
      Netflix 標準方案
      每月 HK$78.00
      """.trimIndent()
    )

    assertEquals("HKD", parsed.currency)
    assertEquals(78.0, parsed.amount ?: 0.0, 0.001)
  }

  @Test
  fun `next billing date is preferred over earlier payment date`() {
    val parsed = SubscriptionParser.parse(
      """
      微信支付
      支付时间：2026-09-15 10:23:45
      商品：QQ音乐豪华绿钻连续包月
      ¥15.00
      下次扣费时间：2026-10-15
      """.trimIndent()
    )

    val expected = Calendar.getInstance().apply {
      clear()
      set(2026, Calendar.OCTOBER, 15)
    }.timeInMillis
    assertEquals(expected, parsed.nextBillingDate)
  }
}

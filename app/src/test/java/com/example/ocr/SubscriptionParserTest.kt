package com.example.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

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
}

package com.example.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrReadingOrderTest {

  @Test
  fun `lines are joined top to bottom then left to right`() {
    // ML Kit 的块顺序：下方的小标题、右侧的价格排在了前面
    val lines = listOf(
      PositionedLine("即将收取的费用", top = 640, left = 64),
      PositionedLine("US$12.99/个月", top = 700, left = 810),
      PositionedLine("ChatGPT", top = 330, left = 252),
      PositionedLine("开始日期:2026年11月5日", top = 702, left = 64),
      PositionedLine("ChatGPT Plus", top = 380, left = 252)
    )

    assertEquals(
      "ChatGPT\nChatGPT Plus\n即将收取的费用\nUS$12.99/个月\n开始日期:2026年11月5日",
      linesInReadingOrder(lines)
    )
  }
}

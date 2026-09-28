package com.example.ocr

import com.example.data.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * 真实手机截图（Google Play 订阅页，小米手机）经 App 内 ML Kit 识别、按屏幕位置排序后的原始文字。
 * 保留了 OCR 的原样错误："金额"识别成"全额 / 全颜 / 金額"、"$" 丢失、日期折行、状态栏被识别等。
 * 卡号尾号已替换为 0000，扣费日期和金额已换成虚构值，状态栏图标有删减。
 */
class RealScreenshotParsingTest {

  private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
    Calendar.getInstance().apply {
      clear()
      set(year, month - 1, dayOfMonth)
    }.timeInMillis

  @Test
  fun `google play manage page for ChatGPT`() {
    val parsed = SubscriptionParser.parse(CHATGPT_MANAGE)

    assertEquals("ChatGPT Plus", parsed.name)
    assertEquals(12.99, parsed.amount ?: 0.0, 0.001)
    assertEquals("USD", parsed.currency)
    assertEquals(Subscription.CYCLE_MONTHLY, parsed.billingCycle)
    assertEquals(day(2026, 11, 5), parsed.nextBillingDate)
  }

  @Test
  fun `google play manage page for Grok`() {
    val parsed = SubscriptionParser.parse(GROK_MANAGE)

    assertEquals("SuperGrok", parsed.name)
    assertEquals(24.99, parsed.amount ?: 0.0, 0.001)
    assertEquals("USD", parsed.currency)
    assertEquals(Subscription.CYCLE_MONTHLY, parsed.billingCycle)
    assertEquals(day(2026, 11, 18), parsed.nextBillingDate)
  }

  @Test
  fun `google play subscription list takes the first subscription`() {
    val parsed = SubscriptionParser.parse(SUBSCRIPTION_LIST)

    assertEquals("ChatGPT Plus", parsed.name)
    assertEquals(12.99, parsed.amount ?: 0.0, 0.001)
    assertEquals("USD", parsed.currency)
    assertNull("列表页没有写扣费周期", parsed.billingCycle)
    assertEquals(day(2026, 11, 5), parsed.nextBillingDate)
  }

  @Test
  fun `name candidates list every subscription title with the chosen name first`() {
    assertEquals(
      listOf("ChatGPT Plus", "ChatGPT", "SuperGrok", "Grok - 人工智能助理"),
      SubscriptionParser.parse(SUBSCRIPTION_LIST).nameCandidates
    )
    assertEquals(
      listOf("ChatGPT Plus", "ChatGPT"),
      SubscriptionParser.parse(CHATGPT_MANAGE).nameCandidates
    )
  }

  @Test
  fun `amount candidates keep each price with its currency`() {
    assertEquals(
      listOf(AmountCandidate(12.99, "USD"), AmountCandidate(24.99, "USD")),
      SubscriptionParser.parse(SUBSCRIPTION_LIST).amountCandidates
    )
    // "US12.99"（OCR 丢了 $）和 "US$12.99" 是同一笔
    assertEquals(
      listOf(AmountCandidate(12.99, "USD")),
      SubscriptionParser.parse(CHATGPT_MANAGE).amountCandidates
    )
  }

  @Test
  fun `date candidates include dates wrapped across lines`() {
    // Grok 的日期被折成 "2026年11月" / "18日" 两行
    assertEquals(
      listOf(day(2026, 11, 5), day(2026, 11, 18)),
      SubscriptionParser.parse(SUBSCRIPTION_LIST).dateCandidates
    )
    assertEquals(
      listOf(day(2026, 11, 5)),
      SubscriptionParser.parse(CHATGPT_MANAGE).dateCandidates
    )
  }

  companion object {
    private val CHATGPT_MANAGE = """
    1:19 M
    管理订阅
    个
    ChatGPT
    ChatGPT Plus
    下次付款全额:US12.99,下次付款日期:2026年11
    |月5日
    即将收取的费用
    US$12.99/个月
    开始日期:2026年11月5日
    主要付款方式
    更新
    Mastercard-0000
    备用付款方式
    设置
    无
    您的万案包含
    <使用GPT-5 进阶推理功能
    、传送更多信息、上传更多文件
    、生成更多图像
    、使用智能体模式
    取消订阅
    """.trimIndent()

    private val GROK_MANAGE = """
    ll99
    1:20 M
    管理订阅
    个
    Grok - 人工智能助理
    SuperGrok
    下次付歆全颜: US$24.99,下次付款日期: 2026年11
    |月18日
    即将收取的费用
    US$24.99/个月
    开始日期:2026年11月18日
    主要付款方式
    更新
    Mastercard-0000
    备用付款方式
    设置
    无
    暂停付款
    取消订阅
    """.trimIndent()

    private val SUBSCRIPTION_LIST = """
    l99
    1:20 M
    个 订阅
    为了帮助开发者更好地提供订阅服务,Go0gle 可能会将订
    阅数据共享给开发者,但此类数据不会泄露您的个人身
    份。详细了解订阅
    活跃
    ChatGPT
    ChatGPT Plus
    下次付款金額: US$12.99 ,下次付款日期:2026年11月5
    日
    您的方案包含
    <使用GPT-5进阶准理功能
    、传送更多信息、上传更多文件
    、生成更多图像
    <使用智能体模式
    Grok - 人工智能助理
    SuperGrok
    下次付款全颜:US$24.99,下次付款日期:2026年11月
    18日
    """.trimIndent()
  }
}

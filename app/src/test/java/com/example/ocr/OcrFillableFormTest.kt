package com.example.ocr

import com.example.data.Subscription
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrFillableFormTest {

  private val today = 1_790_000_000_000L
  private val ocrDate = 1_792_000_000_000L

  private val blankForm = OcrFillableForm(
    name = "",
    amountText = "",
    currency = "CNY",
    billingCycle = Subscription.CYCLE_MONTHLY,
    nextBillingDate = today
  )

  private val parsed = ParsedSubscriptionData(
    name = "哔哩哔哩大会员",
    amount = 15.0,
    currency = "USD",
    billingCycle = Subscription.CYCLE_YEARLY,
    nextBillingDate = ocrDate
  )

  @Test
  fun `fill blank only keeps typed name and amount and fills untouched fields`() {
    val form = blankForm.copy(name = "我的B站", amountText = "12")

    val filled = form.fillWith(parsed, overwriteAll = false)

    assertEquals("我的B站", filled.name)
    assertEquals("12", filled.amountText)
    assertEquals("USD", filled.currency)
    assertEquals(Subscription.CYCLE_YEARLY, filled.billingCycle)
    assertEquals(ocrDate, filled.nextBillingDate)
  }

  @Test
  fun `fill blank only keeps currency cycle and date the user chose`() {
    val form = blankForm.copy(
      currency = "HKD",
      billingCycle = Subscription.CYCLE_QUARTERLY,
      nextBillingDate = today + 86_400_000L,
      currencyChosen = true,
      billingCycleChosen = true,
      nextBillingDateChosen = true
    )

    val filled = form.fillWith(parsed, overwriteAll = false)

    assertEquals("HKD", filled.currency)
    assertEquals(Subscription.CYCLE_QUARTERLY, filled.billingCycle)
    assertEquals(today + 86_400_000L, filled.nextBillingDate)
  }

  @Test
  fun `replace all overwrites recognized fields and keeps unrecognized ones`() {
    val form = blankForm.copy(
      name = "我的B站",
      amountText = "12",
      billingCycle = Subscription.CYCLE_QUARTERLY,
      billingCycleChosen = true
    )
    val partial = parsed.copy(billingCycle = null, nextBillingDate = null)

    val filled = form.fillWith(partial, overwriteAll = true)

    assertEquals("哔哩哔哩大会员", filled.name)
    assertEquals("15.00", filled.amountText)
    assertEquals(Subscription.CYCLE_QUARTERLY, filled.billingCycle)
    assertEquals(today, filled.nextBillingDate)
  }

  @Test
  fun `only choosing a date counts as user input`() {
    assertEquals(false, blankForm.hasUserInput)
    assertEquals(true, blankForm.copy(nextBillingDateChosen = true).hasUserInput)
  }
}

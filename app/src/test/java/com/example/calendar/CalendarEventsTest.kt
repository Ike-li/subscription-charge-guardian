package com.example.calendar

import com.example.data.Subscription
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CalendarEventsTest {

  private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
    Calendar.getInstance().apply {
      clear()
      set(year, month - 1, dayOfMonth)
    }.timeInMillis

  private fun sub(
    nextBillingDate: Long = day(2026, 10, 15),
    billingCycle: String = Subscription.CYCLE_MONTHLY,
    autoRenew: Boolean = true,
    currency: String = "USD",
    amount: Double = 12.99
  ) = Subscription(
    name = "ChatGPT Plus",
    amount = amount,
    currency = currency,
    billingCycle = billingCycle,
    nextBillingDate = nextBillingDate,
    autoRenew = autoRenew
  )

  @Test
  fun `title names the subscription and the charge`() {
    assertEquals("扣费：ChatGPT Plus $12.99", billingEventTitle(sub()))
    assertEquals("扣费：ChatGPT Plus ¥15.00", billingEventTitle(sub(currency = "CNY", amount = 15.0)))
  }

  @Test
  fun `recurrence follows the billing cycle and stops without auto renew`() {
    assertEquals("FREQ=MONTHLY", recurrenceRule(sub()))
    assertEquals("FREQ=MONTHLY;INTERVAL=3", recurrenceRule(sub(billingCycle = Subscription.CYCLE_QUARTERLY)))
    assertEquals("FREQ=YEARLY", recurrenceRule(sub(billingCycle = Subscription.CYCLE_YEARLY)))
    assertEquals(null, recurrenceRule(sub(autoRenew = false)))
  }

  @Test
  fun `month end billing days fall back to the last day of shorter months`() {
    assertEquals(
      "FREQ=MONTHLY;BYMONTHDAY=28,29,30,31;BYSETPOS=-1",
      recurrenceRule(sub(nextBillingDate = day(2026, 10, 31)))
    )
    assertEquals(
      "FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=28,29,30;BYSETPOS=-1",
      recurrenceRule(sub(nextBillingDate = day(2026, 11, 30), billingCycle = Subscription.CYCLE_QUARTERLY))
    )
  }
}

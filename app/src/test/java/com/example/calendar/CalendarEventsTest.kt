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
    amount: Double = 12.99,
    reminderDaysBefore: Int = 3
  ) = Subscription(
    name = "ChatGPT Plus",
    amount = amount,
    currency = currency,
    billingCycle = billingCycle,
    nextBillingDate = nextBillingDate,
    autoRenew = autoRenew,
    reminderDaysBefore = reminderDaysBefore
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

  @Test
  fun `upcoming billing dates cover the next twelve months`() {
    val today = day(2026, 9, 28)

    val monthly = upcomingBillingDates(sub(nextBillingDate = day(2026, 10, 15)), today)
    assertEquals(12, monthly.size)
    assertEquals(day(2026, 10, 15), monthly.first())
    assertEquals(day(2027, 9, 15), monthly.last())

    assertEquals(
      listOf(day(2026, 10, 15)),
      upcomingBillingDates(sub(billingCycle = Subscription.CYCLE_YEARLY), today)
    )
    assertEquals(
      listOf(day(2026, 10, 15)),
      upcomingBillingDates(sub(autoRenew = false), today)
    )
  }

  @Test
  fun `month end billing dates do not drift earlier`() {
    val dates = upcomingBillingDates(sub(nextBillingDate = day(2026, 10, 31)), today = day(2026, 9, 28))

    assertEquals(
      listOf(day(2026, 10, 31), day(2026, 11, 30), day(2026, 12, 31), day(2027, 1, 31), day(2027, 2, 28), day(2027, 3, 31)),
      dates.take(6)
    )
  }

  @Test
  fun `synced events start at nine on each billing day and remind the chosen days before`() {
    val at = { y: Int, m: Int, d: Int, h: Int, min: Int ->
      Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis
    }
    // 扣费日存的是当天某个时刻（不一定是零点），事件都应落在当天 9 点
    val events = syncedEvents(
      sub(nextBillingDate = at(2026, 10, 15, 13, 30), reminderDaysBefore = 7),
      today = day(2026, 9, 28)
    )

    assertEquals(12, events.size)
    assertEquals(at(2026, 10, 15, 9, 0), events.first().begin)
    assertEquals(at(2027, 9, 15, 9, 0), events.last().begin)
    assertEquals(7 * 24 * 60, events.first().reminderMinutes)
    assertEquals("扣费：ChatGPT Plus $12.99", events.first().title)
  }
}

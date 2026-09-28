package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SubscriptionRollForwardTest {

  private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
    Calendar.getInstance().apply {
      clear()
      set(year, month - 1, dayOfMonth)
    }.timeInMillis

  private fun sub(
    nextBillingDate: Long,
    billingCycle: String = Subscription.CYCLE_MONTHLY,
    autoRenew: Boolean = true
  ) = Subscription(
    name = "B站大会员",
    amount = 15.0,
    billingCycle = billingCycle,
    nextBillingDate = nextBillingDate,
    autoRenew = autoRenew
  )

  @Test
  fun `overdue monthly subscription moves to same day next month`() {
    val rolled = sub(day(2026, 9, 10)).rollForward(today = day(2026, 9, 28))

    assertEquals(day(2026, 10, 10), rolled.nextBillingDate)
  }

  @Test
  fun `billing date today is not rolled`() {
    val rolled = sub(day(2026, 9, 28)).rollForward(today = day(2026, 9, 28) + 15 * 3600_000L)

    assertEquals(day(2026, 9, 28), rolled.nextBillingDate)
  }

  @Test
  fun `several missed cycles jump to first billing date on or after today`() {
    val rolled = sub(day(2026, 6, 10)).rollForward(today = day(2026, 9, 28))

    assertEquals(day(2026, 10, 10), rolled.nextBillingDate)
  }

  @Test
  fun `quarterly and yearly subscriptions roll by their own cycle`() {
    val today = day(2026, 9, 28)

    val quarterly = sub(day(2026, 9, 10), Subscription.CYCLE_QUARTERLY).rollForward(today)
    val yearly = sub(day(2026, 9, 10), Subscription.CYCLE_YEARLY).rollForward(today)

    assertEquals(day(2026, 12, 10), quarterly.nextBillingDate)
    assertEquals(day(2027, 9, 10), yearly.nextBillingDate)
  }

  @Test
  fun `subscription without auto renew is not rolled`() {
    val rolled = sub(day(2026, 9, 10), autoRenew = false).rollForward(today = day(2026, 9, 28))

    assertEquals(day(2026, 9, 10), rolled.nextBillingDate)
  }

  @Test
  fun `month end billing day is kept when skipping several months`() {
    val rolled = sub(day(2026, 1, 31)).rollForward(today = day(2026, 3, 5))

    assertEquals(day(2026, 3, 31), rolled.nextBillingDate)
  }
}

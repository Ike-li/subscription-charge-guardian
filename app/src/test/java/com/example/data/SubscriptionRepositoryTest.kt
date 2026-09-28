package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionRepositoryTest {

  private lateinit var db: AppDatabase
  private lateinit var repository: SubscriptionRepository

  private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
    Calendar.getInstance().apply {
      clear()
      set(year, month - 1, dayOfMonth)
    }.timeInMillis

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java
    ).allowMainThreadQueries().build()
    repository = SubscriptionRepository(db.subscriptionDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `rollForwardOverdue persists next cycle only for overdue active subscriptions`() = runTest {
    val overdueId = repository.insert(
      Subscription(name = "B站大会员", amount = 15.0, nextBillingDate = day(2026, 9, 10))
    )
    val upcomingId = repository.insert(
      Subscription(name = "网易云音乐", amount = 88.0, nextBillingDate = day(2026, 10, 1))
    )
    val cancelledId = repository.insert(
      Subscription(name = "腾讯视频", amount = 25.0, nextBillingDate = day(2026, 9, 10), isActive = false)
    )

    repository.rollForwardOverdue(today = day(2026, 9, 28))

    assertEquals(day(2026, 10, 10), repository.getSubscriptionByIdDirect(overdueId)?.nextBillingDate)
    assertEquals(day(2026, 10, 1), repository.getSubscriptionByIdDirect(upcomingId)?.nextBillingDate)
    assertEquals(day(2026, 9, 10), repository.getSubscriptionByIdDirect(cancelledId)?.nextBillingDate)
  }
}

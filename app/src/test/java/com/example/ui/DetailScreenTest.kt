package com.example.ui

import android.app.Application
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.calendar.CalendarSync
import com.example.data.AppDatabase
import com.example.data.Subscription
import com.example.data.SubscriptionRepository
import com.example.ui.screens.DetailScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailScreenTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var db: AppDatabase

  // 明年 3 月 15 日：在未来、且不在月末，结果不随运行日期变化
  private val billingDate = Calendar.getInstance().apply {
    val nextYear = get(Calendar.YEAR) + 1
    clear()
    set(nextYear, Calendar.MARCH, 15)
  }.timeInMillis

  private var subscriptionId = 0L

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java
    ).allowMainThreadQueries().build()
    subscriptionId = runBlocking {
      SubscriptionRepository(db.subscriptionDao()).insert(
        Subscription(name = "ChatGPT Plus", amount = 12.99, currency = "USD", nextBillingDate = billingDate)
      )
    }
  }

  private fun showDetail() {
    val viewModel = SubscriptionViewModel(SubscriptionRepository(db.subscriptionDao()), ApplicationProvider.getApplicationContext())
    composeTestRule.setContent {
      MyApplicationTheme {
        DetailScreen(subscriptionId = subscriptionId, viewModel = viewModel, onNavigateBack = {}, onNavigateToEdit = {})
      }
    }
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithText("订阅详情").fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithText("ChatGPT Plus").fetchSemanticsNodes().isNotEmpty()
    }
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `add to calendar opens a prefilled repeating calendar event`() {
    showDetail()
    composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("添加到手机日历"))
    composeTestRule.onNodeWithText("添加到手机日历").performClick()

    val intent = shadowOf(composeTestRule.activity).nextStartedActivity
    assertEquals(Intent.ACTION_INSERT, intent.action)
    assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
    assertEquals("扣费：ChatGPT Plus $12.99", intent.getStringExtra(CalendarContract.Events.TITLE))
    assertEquals("FREQ=MONTHLY", intent.getStringExtra(CalendarContract.Events.RRULE))
    assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
    assertEquals(billingDate, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
  }

  @Test
  fun `with calendar sync on the detail page says it is already synced`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(app).grantPermissions(*CalendarSync.PERMISSIONS)
    CalendarSync.setEnabled(app, true)
    showDetail()

    composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("已自动同步到手机日历", substring = true))
    composeTestRule.onAllNodesWithText("添加到手机日历").assertCountEquals(0)
  }
}

package com.example.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Subscription
import com.example.data.SubscriptionRepository
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var db: AppDatabase
  private lateinit var repository: SubscriptionRepository

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

  private fun sub(name: String, amount: Double, currency: String) = Subscription(
    name = name,
    amount = amount,
    currency = currency,
    nextBillingDate = System.currentTimeMillis() + 10 * 86_400_000L
  )

  private fun showHome(vararg subscriptions: Subscription) {
    runBlocking { subscriptions.forEach { repository.insert(it) } }
    val viewModel = SubscriptionViewModel(repository, ApplicationProvider.getApplicationContext())
    composeTestRule.setContent {
      MyApplicationTheme {
        HomeScreen(viewModel = viewModel, onNavigateToAdd = {}, onNavigateToDetail = {})
      }
    }
  }

  private fun waitForText(text: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun `overview shows cny total and lists other currencies separately`() {
    showHome(sub("B站大会员", 15.0, "CNY"), sub("iCloud+", 9.99, "USD"))
    waitForText("监控中 2 项")

    composeTestRule.onNodeWithTag("monthly_total_text").assertTextEquals("15.00")
    composeTestRule.onNodeWithText("另有 $9.99 / 月").assertExists()
    composeTestRule.onNodeWithText("¥180.00 + $119.88 / 年").assertExists()
  }
}

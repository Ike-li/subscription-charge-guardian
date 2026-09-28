package com.example.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.SubscriptionRepository
import com.example.ui.screens.AddEditScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi") // Pixel 7 的屏幕尺寸
@GraphicsMode(GraphicsMode.Mode.NATIVE) // 默认模式不真正测量文字宽度，复现不了折行
class AddEditScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var db: AppDatabase

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java
    ).allowMainThreadQueries().build()
    val viewModel = SubscriptionViewModel(
      SubscriptionRepository(db.subscriptionDao()),
      ApplicationProvider.getApplicationContext()
    )
    composeTestRule.setContent {
      MyApplicationTheme {
        AddEditScreen(subscriptionId = 0L, viewModel = viewModel, onNavigateBack = {}, onSaved = {})
      }
    }
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `reminder day options each fit on one line`() {
    composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("reminder_chip_14"))

    val layouts = mutableListOf<TextLayoutResult>()
    composeTestRule.onNodeWithText("14天前", useUnmergedTree = true).fetchSemanticsNode()
      .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)

    assertEquals(1, layouts.single().lineCount)
  }
}

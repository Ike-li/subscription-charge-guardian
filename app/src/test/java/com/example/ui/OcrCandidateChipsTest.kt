package com.example.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.OcrCandidateChips
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrCandidateChipsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `tapping a candidate picks it and the current value is highlighted`() {
    var picked = -1
    composeTestRule.setContent {
      MyApplicationTheme {
        OcrCandidateChips(
          labels = listOf("ChatGPT Plus", "ChatGPT"),
          selectedIndex = 0,
          onPick = { picked = it }
        )
      }
    }

    composeTestRule.onNodeWithText("ChatGPT Plus").assertIsSelected()
    composeTestRule.onNodeWithText("ChatGPT").performClick()

    assertEquals(1, picked)
  }

  @Test
  fun `a single candidate already in the field shows nothing`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        OcrCandidateChips(labels = listOf("ChatGPT Plus"), selectedIndex = 0, onPick = {})
      }
    }

    composeTestRule.onNodeWithText("ChatGPT Plus").assertDoesNotExist()
  }
}

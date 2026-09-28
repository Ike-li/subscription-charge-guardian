package com.example.ui.theme

import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertTrue
import org.junit.Test

// 需求面向中老年用户：标题不小于 20sp，正文不小于 16sp
class TypographyTest {

  private fun assertAtLeast(minSp: Float, styles: Map<String, TextStyle>) {
    val tooSmall = styles.filterValues { it.fontSize.value < minSp }
      .mapValues { it.value.fontSize }
    assertTrue("小于 ${minSp}sp: $tooSmall", tooSmall.isEmpty())
  }

  @Test
  fun `body and label styles are at least 16sp`() {
    assertAtLeast(
      16f,
      mapOf(
        "bodyLarge" to Typography.bodyLarge,
        "bodyMedium" to Typography.bodyMedium,
        "bodySmall" to Typography.bodySmall,
        "labelLarge" to Typography.labelLarge,
        "labelMedium" to Typography.labelMedium,
        "labelSmall" to Typography.labelSmall
      )
    )
  }

  @Test
  fun `title and larger styles are at least 20sp`() {
    assertAtLeast(
      20f,
      mapOf(
        "titleLarge" to Typography.titleLarge,
        "titleMedium" to Typography.titleMedium,
        "titleSmall" to Typography.titleSmall,
        "headlineSmall" to Typography.headlineSmall,
        "headlineMedium" to Typography.headlineMedium,
        "headlineLarge" to Typography.headlineLarge,
        "displaySmall" to Typography.displaySmall,
        "displayMedium" to Typography.displayMedium,
        "displayLarge" to Typography.displayLarge
      )
    )
  }
}

package com.example.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateUtilsTest {

  private lateinit var originalTimeZone: TimeZone

  @Before
  fun setUp() {
    originalTimeZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))
  }

  @After
  fun tearDown() {
    TimeZone.setDefault(originalTimeZone)
  }

  private fun at(year: Int, month: Int, dayOfMonth: Int, hour: Int): Long =
    Calendar.getInstance().apply {
      clear()
      set(year, month - 1, dayOfMonth, hour, 0)
    }.timeInMillis

  @Test
  fun `days between counts calendar days across daylight saving start`() {
    // 2026-03-08 美国中部时间开始夏令时，这一天只有 23 小时
    assertEquals(19, DateUtils.daysBetween(at(2026, 3, 1, 10), at(2026, 3, 20, 9)))
  }
}

package com.example.calendar

import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.Subscription
import com.example.util.DateUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** 在真机或模拟器的系统日历上跑，验证同步真的写进了 CalendarProvider */
@RunWith(AndroidJUnit4::class)
class CalendarSyncTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val resolver = context.contentResolver
  private val today = DateUtils.getStartOfDay(System.currentTimeMillis())

  // 10 天后的中午；按年扣费，12 个月内只有这一次
  private val billingDate = Calendar.getInstance().apply {
    timeInMillis = today
    add(Calendar.DAY_OF_MONTH, 10)
    set(Calendar.HOUR_OF_DAY, 12)
  }.timeInMillis

  private fun sub(name: String) = Subscription(
    name = name,
    amount = 88.0,
    currency = "CNY",
    billingCycle = Subscription.CYCLE_YEARLY,
    nextBillingDate = billingDate,
    reminderDaysBefore = 3
  )

  @Before
  fun grantCalendarPermission() {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    CalendarSync.PERMISSIONS.forEach { automation.grantRuntimePermission(context.packageName, it) }
  }

  @After
  fun cleanUp() {
    CalendarSync.removeCalendar(context)
  }

  private fun ourCalendarIds(): List<Long> = resolver.query(
    Calendars.CONTENT_URI,
    arrayOf(Calendars._ID),
    "${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.CALENDAR_DISPLAY_NAME}=?",
    arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, "订阅卫士"),
    null
  )!!.use { c -> generateSequence { if (c.moveToNext()) c.getLong(0) else null }.toList() }

  private data class Row(val id: Long, val title: String, val begin: Long)

  private fun events(calendarId: Long): List<Row> = resolver.query(
    Events.CONTENT_URI,
    arrayOf(Events._ID, Events.TITLE, Events.DTSTART),
    "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0",
    arrayOf(calendarId.toString()),
    "${Events.TITLE} ASC"
  )!!.use { c -> generateSequence { if (c.moveToNext()) Row(c.getLong(0), c.getString(1), c.getLong(2)) else null }.toList() }

  private fun reminderMinutes(eventId: Long): List<Int> =
    Reminders.query(resolver, eventId, arrayOf(Reminders.MINUTES, Reminders.METHOD))!!.use { c ->
      generateSequence {
        if (!c.moveToNext()) null
        else c.getInt(0).also { assertEquals(Reminders.METHOD_ALERT, c.getInt(1)) }
      }.toList()
    }

  @Test
  fun syncWritesBillingDaysWithRemindersIntoALocalCalendar() {
    CalendarSync.sync(context, listOf(sub("Netflix")), today)

    val calendarId = ourCalendarIds().single()
    val event = events(calendarId).single()
    assertEquals("扣费：Netflix ¥88.00", event.title)
    assertEquals(DateUtils.getStartOfDay(billingDate) + 9 * 3600_000L, event.begin)
    assertEquals(listOf(3 * 24 * 60), reminderMinutes(event.id))
  }

  @Test
  fun syncAgainReplacesThePreviousEvents() {
    CalendarSync.sync(context, listOf(sub("Netflix"), sub("Spotify")), today)
    CalendarSync.sync(context, listOf(sub("Spotify")), today)

    val calendarId = ourCalendarIds().single()
    assertEquals(listOf("扣费：Spotify ¥88.00"), events(calendarId).map { it.title })
  }

  @Test
  fun removeCalendarDeletesItWithItsEvents() {
    CalendarSync.sync(context, listOf(sub("Netflix")), today)
    val calendarId = ourCalendarIds().single()

    CalendarSync.removeCalendar(context)

    assertEquals(emptyList<Long>(), ourCalendarIds())
    assertEquals(emptyList<Row>(), events(calendarId))
  }
}

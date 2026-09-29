package com.example.calendar

import android.content.Intent
import android.provider.CalendarContract
import com.example.data.Subscription
import com.example.util.DateUtils
import java.util.Calendar
import java.util.Locale

fun billingEventTitle(subscription: Subscription): String =
    "扣费：${subscription.name} " +
        Subscription.getCurrencySymbol(subscription.currency) +
        String.format(Locale.US, "%.2f", subscription.amount)

/**
 * 日历重复规则（RFC 5545 RRULE）；不自动续费时不重复。
 * 按月、按季在 29–31 号扣费时，短月份取当月最后一天，否则日历会直接跳过那个月。
 */
fun recurrenceRule(subscription: Subscription): String? {
    if (!subscription.autoRenew) return null
    val base = when (subscription.billingCycle) {
        Subscription.CYCLE_YEARLY -> return "FREQ=YEARLY"
        Subscription.CYCLE_QUARTERLY -> "FREQ=MONTHLY;INTERVAL=3"
        else -> "FREQ=MONTHLY"
    }
    val dayOfMonth = Calendar.getInstance().apply { timeInMillis = subscription.nextBillingDate }
        .get(Calendar.DAY_OF_MONTH)
    if (dayOfMonth < 29) return base
    return "$base;BYMONTHDAY=${(28..dayOfMonth).joinToString(",")};BYSETPOS=-1"
}

/**
 * 今天起 [horizonMonths] 个月内的扣费日（当天零点）。
 * 每个日期都从下次扣费日一次加 n 期算出，月末日期（31 号）不会越推越早。
 */
fun upcomingBillingDates(subscription: Subscription, today: Long, horizonMonths: Int = 12): List<Long> {
    val start = DateUtils.getStartOfDay(today)
    val end = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.MONTH, horizonMonths)
    }.timeInMillis
    if (!subscription.autoRenew) return listOf(subscription.nextBillingDate).filter { it >= start }
    return generateSequence(0) { it + 1 }
        .map { cycles ->
            Calendar.getInstance().apply {
                timeInMillis = subscription.nextBillingDate
                add(Calendar.MONTH, cycles * subscription.monthsPerCycle())
            }.timeInMillis
        }
        .takeWhile { it <= end }
        .filter { it >= start }
        .toList()
}

fun billingEventDescription(subscription: Subscription): String = listOfNotNull(
    subscription.cancelNote?.let { "取消方式：$it" },
    subscription.cancelUrl?.let { "取消链接：$it" },
    "由订阅卫士添加"
).joinToString("\n")

/** 打开日历 App 并预填好全天事件，由用户在日历里确认保存；不需要日历权限 */
fun addToCalendarIntent(subscription: Subscription): Intent =
    Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, billingEventTitle(subscription))
        .putExtra(CalendarContract.Events.DESCRIPTION, billingEventDescription(subscription))
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, subscription.nextBillingDate)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, subscription.nextBillingDate + 24 * 3600_000L)
        .apply { recurrenceRule(subscription)?.let { putExtra(CalendarContract.Events.RRULE, it) } }

/** 自动同步写进“订阅卫士”日历的一条事件 */
data class SyncedEvent(val title: String, val description: String, val begin: Long, val reminderMinutes: Int)

/** 每个扣费日当天 9 点一条事件，按订阅设置的天数提前提醒。用定时事件，提醒才落在早上 9 点；全天事件从零点按分钟倒推会落在半夜 */
fun syncedEvents(subscription: Subscription, today: Long): List<SyncedEvent> =
    upcomingBillingDates(subscription, today).map { date ->
        val nineAm = Calendar.getInstance().apply {
            timeInMillis = DateUtils.getStartOfDay(date)
            set(Calendar.HOUR_OF_DAY, 9)
        }.timeInMillis
        SyncedEvent(
            title = billingEventTitle(subscription),
            description = billingEventDescription(subscription),
            begin = nineAm,
            reminderMinutes = subscription.reminderDaysBefore * 24 * 60
        )
    }

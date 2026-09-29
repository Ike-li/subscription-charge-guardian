package com.example.calendar

import android.content.Intent
import android.provider.CalendarContract
import com.example.data.Subscription
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


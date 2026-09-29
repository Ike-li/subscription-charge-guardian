package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.calendar.CalendarSync
import com.example.data.AppDatabase
import com.example.data.SubscriptionRepository
import com.example.notification.NotificationHelper
import com.example.util.DateUtils

class BillingReminderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(appContext)
        val todayMillis = System.currentTimeMillis()

        SubscriptionRepository(database.subscriptionDao()).rollForwardOverdue(todayMillis)
        val activeSubscriptions = database.subscriptionDao().getAllActiveDirect()

        for (sub in activeSubscriptions) {
            val daysRemaining = DateUtils.daysBetween(todayMillis, sub.nextBillingDate)
            if (daysRemaining == sub.reminderDaysBefore && daysRemaining >= 0) {
                NotificationHelper.sendBillingReminder(appContext, sub, daysRemaining)
            }
        }

        // 每天重写一次，日历里始终是今天起一年内的扣费日
        CalendarSync.update(appContext, activeSubscriptions, CalendarSync.isEnabled(appContext), todayMillis)

        return Result.success()
    }
}

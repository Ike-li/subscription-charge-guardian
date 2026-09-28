package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.notification.NotificationHelper
import com.example.util.DateUtils

class BillingReminderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(appContext)
        val activeSubscriptions = database.subscriptionDao().getAllActiveDirect()

        val todayMillis = System.currentTimeMillis()

        for (sub in activeSubscriptions) {
            val daysRemaining = DateUtils.daysBetween(todayMillis, sub.nextBillingDate)
            if (daysRemaining == sub.reminderDaysBefore && daysRemaining >= 0) {
                NotificationHelper.sendBillingReminder(appContext, sub, daysRemaining)
            }
        }

        return Result.success()
    }
}

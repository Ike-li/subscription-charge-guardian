package com.example.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val UNIQUE_PERIODIC_WORK_NAME = "subguard_daily_billing_reminder"

    /**
     * 注册每日定时检查任务（每 24 小时检查一次）
     */
    fun scheduleDailyReminder(context: Context) {
        try {
            val periodicWorkRequest = PeriodicWorkRequestBuilder<BillingReminderWorker>(
                24, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
        } catch (_: Exception) {
            // Safe fallback for unit tests or restricted environments
        }
    }

    /**
     * 立即执行一次检查（用于数据更新后或用户测试）
     */
    fun triggerImmediateCheck(context: Context) {
        try {
            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<BillingReminderWorker>().build()
            WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
        } catch (_: Exception) {
            // Safe fallback
        }
    }
}

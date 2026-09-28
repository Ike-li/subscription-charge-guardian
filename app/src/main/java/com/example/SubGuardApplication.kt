package com.example

import android.app.Application
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.SubscriptionRepository
import com.example.notification.NotificationHelper
import com.example.worker.ReminderScheduler

class SubGuardApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { SubscriptionRepository(database.subscriptionDao()) }

    override fun onCreate() {
        super.onCreate()

        try {
            // 初始化通知渠道
            NotificationHelper.createNotificationChannel(this)

            // 注册每日扣费检查后台任务
            ReminderScheduler.scheduleDailyReminder(this)
        } catch (t: Throwable) {
            Log.e("SubGuardApplication", "Error during application onCreate", t)
        }
    }
}

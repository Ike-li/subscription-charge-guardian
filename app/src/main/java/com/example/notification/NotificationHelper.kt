package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.Subscription
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_ID = "channel_billing_reminder"
    const val CHANNEL_NAME = "扣费提醒"
    const val EXTRA_SUBSCRIPTION_ID = "extra_subscription_id"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "在自动续费扣款前发送提醒通知"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun sendBillingReminder(context: Context, subscription: Subscription, daysRemaining: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SUBSCRIPTION_ID, subscription.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            subscription.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val symbol = Subscription.getCurrencySymbol(subscription.currency)
        val formattedAmount = String.format(Locale.getDefault(), "%.2f", subscription.amount)
        val message = "订阅卫士提醒：${subscription.name} 将于 ${daysRemaining} 天后扣费 ${symbol}${formattedAmount}，点击查看取消方式。"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle("即将扣费提醒")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(subscription.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission might have been revoked by user
        }
    }

    fun sendTestNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            99999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle("订阅卫士提醒测试")
            .setContentText("通知功能正常工作！系统将在扣费前准时提醒您。")
            .setStyle(NotificationCompat.BigTextStyle().bigText("通知功能正常工作！系统将在扣费前准时提醒您。"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(99999, notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }
}

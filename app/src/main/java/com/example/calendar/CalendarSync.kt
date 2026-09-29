package com.example.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.data.Subscription
import java.util.TimeZone

/**
 * 把扣费日写进手机上单独的“订阅卫士”日历，由日历 App 负责提醒。
 * 这个日历挂在本机账号（ACCOUNT_TYPE_LOCAL）下，不属于任何云端账号；关掉同步时整个删除。
 */
object CalendarSync {

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private const val PREFS = "subguard_settings"
    private const val KEY_ENABLED = "calendar_sync_enabled"
    private const val CALENDAR_NAME = "订阅卫士"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun hasPermission(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** 开着就重写日历，关着就删掉日历；没有日历权限时不动。日历出错不能影响 App 自己的提醒 */
    fun update(context: Context, subscriptions: List<Subscription>, enabled: Boolean, today: Long) {
        if (!hasPermission(context)) return
        try {
            if (enabled) sync(context, subscriptions, today) else removeCalendar(context)
        } catch (e: Exception) {
            Log.w("CalendarSync", "同步手机日历失败", e)
        }
    }

    /** 清空“订阅卫士”日历后按当前订阅重写，日历里的内容始终和 App 一致 */
    @Synchronized
    fun sync(context: Context, subscriptions: List<Subscription>, today: Long) {
        val calendarId = findCalendarId(context) ?: createCalendar(context)
        val zone = TimeZone.getDefault().id
        val ops = arrayListOf(
            ContentProviderOperation.newDelete(asSyncAdapter(Events.CONTENT_URI))
                .withSelection("${Events.CALENDAR_ID}=?", arrayOf(calendarId.toString()))
                .build()
        )
        for (event in subscriptions.flatMap { syncedEvents(it, today) }) {
            val eventIndex = ops.size
            ops += ContentProviderOperation.newInsert(asSyncAdapter(Events.CONTENT_URI))
                .withValue(Events.CALENDAR_ID, calendarId)
                .withValue(Events.TITLE, event.title)
                .withValue(Events.DESCRIPTION, event.description)
                .withValue(Events.DTSTART, event.begin)
                .withValue(Events.DTEND, event.begin + 30 * 60_000L)
                .withValue(Events.EVENT_TIMEZONE, zone)
                .withValue(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
                .withValue(Events.HAS_ALARM, 1)
                .build()
            ops += ContentProviderOperation.newInsert(asSyncAdapter(Reminders.CONTENT_URI))
                .withValueBackReference(Reminders.EVENT_ID, eventIndex)
                .withValue(Reminders.MINUTES, event.reminderMinutes)
                .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                .build()
        }
        context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
    }

    /** 删除“订阅卫士”日历，里面的事件和提醒随之删除 */
    @Synchronized
    fun removeCalendar(context: Context) {
        context.contentResolver.delete(
            asSyncAdapter(Calendars.CONTENT_URI),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
            arrayOf(CALENDAR_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL)
        )
    }

    private fun findCalendarId(context: Context): Long? = context.contentResolver.query(
        Calendars.CONTENT_URI,
        arrayOf(Calendars._ID),
        "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
        arrayOf(CALENDAR_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
        null
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun createCalendar(context: Context): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, CALENDAR_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.OWNER_ACCOUNT, CALENDAR_NAME)
            put(Calendars.NAME, CALENDAR_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(Calendars.CALENDAR_COLOR, 0xFF1565C0.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        return ContentUris.parseId(context.contentResolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)!!)
    }

    // 本机账号没有同步程序，普通删除只给事件打上删除标记、永远不会真正清掉，所以以同步适配器身份操作
    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, CALENDAR_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()
}

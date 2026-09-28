package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    private val displayFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    /**
     * 格式化时间戳为展示文本
     */
    fun formatDate(timestamp: Long): String {
        return displayFormat.format(Date(timestamp))
    }

    fun formatSimpleDate(timestamp: Long): String {
        return simpleDateFormat.format(Date(timestamp))
    }

    /**
     * 将时间戳规整为当天的 00:00:00.000
     */
    fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /**
     * 计算两个日期之间的相差天数 (targetDate - fromDate)
     */
    fun daysBetween(fromTimestamp: Long, targetTimestamp: Long): Int {
        val startFrom = getStartOfDay(fromTimestamp)
        val startTarget = getStartOfDay(targetTimestamp)
        val diffMillis = startTarget - startFrom
        return (diffMillis / (1000L * 60L * 60L * 24L)).toInt()
    }

    /**
     * 计算指定日期距离今天的相差天数
     */
    fun daysFromToday(targetTimestamp: Long): Int {
        return daysBetween(System.currentTimeMillis(), targetTimestamp)
    }

    /**
     * 获取友好的剩余天数文案
     */
    fun getRemainingDaysText(targetTimestamp: Long): String {
        val days = daysFromToday(targetTimestamp)
        return when {
            days < 0 -> "已逾期 ${-days} 天"
            days == 0 -> "今天扣费"
            days == 1 -> "明天扣费"
            days == 2 -> "后天扣费"
            else -> "$days 天后扣费"
        }
    }
}

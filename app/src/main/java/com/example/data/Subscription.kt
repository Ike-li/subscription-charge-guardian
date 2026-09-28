package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.util.DateUtils
import java.util.Calendar

/**
 * 订阅实体模型
 */
@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val amount: Double,
    val currency: String = "CNY",
    val billingCycle: String = CYCLE_MONTHLY, // MONTHLY / QUARTERLY / YEARLY
    val nextBillingDate: Long,
    val autoRenew: Boolean = true,
    val cancelUrl: String? = null,
    val cancelNote: String? = null,
    val reminderDaysBefore: Int = 3,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CYCLE_MONTHLY = "MONTHLY"
        const val CYCLE_QUARTERLY = "QUARTERLY"
        const val CYCLE_YEARLY = "YEARLY"

        fun getCycleDisplayName(cycle: String): String {
            return when (cycle) {
                CYCLE_MONTHLY -> "每月"
                CYCLE_QUARTERLY -> "每季"
                CYCLE_YEARLY -> "每年"
                else -> "每月"
            }
        }

        fun getCurrencySymbol(currency: String): String {
            return when (currency) {
                "CNY" -> "¥"
                "USD" -> "$"
                "HKD" -> "HK$"
                else -> "¥"
            }
        }
    }

    /**
     * 折算成月度费用
     */
    fun calculateMonthlyAmount(): Double {
        return when (billingCycle) {
            CYCLE_MONTHLY -> amount
            CYCLE_QUARTERLY -> amount / 3.0
            CYCLE_YEARLY -> amount / 12.0
            else -> amount
        }
    }

    /**
     * 扣费日已过且自动续费时，把下次扣费日推到今天或之后最近的一期
     */
    fun rollForward(today: Long): Subscription {
        val startOfToday = DateUtils.getStartOfDay(today)
        if (!autoRenew || nextBillingDate >= startOfToday) return this
        val monthsPerCycle = when (billingCycle) {
            CYCLE_QUARTERLY -> 3
            CYCLE_YEARLY -> 12
            else -> 1
        }
        // 从原扣费日一次加 n 期，一次跳多期时月末日期不会越滚越早；
        // 但结果会存回数据库，下次顺延从新日期起算，31 日过完二月后会停在 28 日
        val next = generateSequence(1) { it + 1 }
            .map { cycles ->
                Calendar.getInstance().apply {
                    timeInMillis = nextBillingDate
                    add(Calendar.MONTH, cycles * monthsPerCycle)
                }.timeInMillis
            }
            .first { it >= startOfToday }
        return copy(nextBillingDate = next)
    }

    /**
     * 折算成年度费用
     */
    fun calculateYearlyAmount(): Double {
        return when (billingCycle) {
            CYCLE_MONTHLY -> amount * 12.0
            CYCLE_QUARTERLY -> amount * 4.0
            CYCLE_YEARLY -> amount
            else -> amount * 12.0
        }
    }
}

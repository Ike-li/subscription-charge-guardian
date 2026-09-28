package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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

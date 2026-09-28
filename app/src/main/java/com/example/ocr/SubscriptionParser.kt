package com.example.ocr

import com.example.data.Subscription
import com.example.util.DateUtils
import java.util.Calendar
import java.util.regex.Pattern

data class ParsedSubscriptionData(
    val name: String? = null,
    val amount: Double? = null,
    val currency: String = "CNY",
    val billingCycle: String? = null,
    val nextBillingDate: Long? = null,
    val rawText: String = ""
)

object SubscriptionParser {

    private val amountPatterns = listOf(
        // ¥15.00, ￥15, CNY 15.00
        Pattern.compile("""(?:¥|￥|CNY|RMB)\s*([0-9]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        // 15.00元, 15元
        Pattern.compile("""([0-9]+(?:\.[0-9]{1,2})?)\s*(?:元|块)"""),
        // USD 9.99, $9.99
        Pattern.compile("""(?:\$|USD)\s*([0-9]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        // HKD 15.00, HK$15
        Pattern.compile("""(?:HK\$|HKD)\s*([0-9]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        // 金额/实付/费用: 15.00
        Pattern.compile("""(?:金额|实付|扣费|费用|价格|扣款)[:：\s]*([0-9]+(?:\.[0-9]{1,2})?)""")
    )

    private val fullDatePatterns = listOf(
        // 2026-10-15, 2026/10/15, 2026.10.15, 2026年10月15日
        Pattern.compile("""(20[2-9][0-9])[\-年\/\.]([0-1]?[0-9])[\-月\/\.]([0-3]?[0-9])[日号]?""")
    )

    private val shortDatePattern = Pattern.compile("""([0-1]?[0-9])[\-月\/\.]([0-3]?[0-9])[日号]""")

    private val commonIgnoredLines = setOf(
        "账单详情", "账单", "扣款成功", "自动续费管理", "订单详情", "交易详情",
        "微信支付", "支付宝", "收银台", "支付凭证", "扣费凭证", "Apple", "App Store",
        "订阅详情", "管理订阅", "完成", "取消订阅", "返回", "设置"
    )

    fun parse(rawText: String): ParsedSubscriptionData {
        if (rawText.isBlank()) {
            return ParsedSubscriptionData(rawText = rawText)
        }

        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val detectedCurrency = detectCurrency(rawText)
        val detectedAmount = extractAmount(rawText, lines)
        val detectedCycle = extractCycle(rawText)
        val detectedDate = extractDate(rawText, lines)
        val detectedName = extractName(lines)

        return ParsedSubscriptionData(
            name = detectedName,
            amount = detectedAmount,
            currency = detectedCurrency,
            billingCycle = detectedCycle,
            nextBillingDate = detectedDate,
            rawText = rawText
        )
    }

    private fun detectCurrency(text: String): String {
        // HK$ 也含 "$"，必须先于美元判断
        return when {
            text.contains("HK$", ignoreCase = true) || text.contains("HKD", ignoreCase = true) -> "HKD"
            text.contains("$") || text.contains("USD", ignoreCase = true) -> "USD"
            else -> "CNY"
        }
    }

    private fun extractAmount(rawText: String, lines: List<String>): Double? {
        // Priority 1: Check pattern matches in order
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(rawText)
            if (matcher.find()) {
                val numStr = matcher.group(1)
                numStr?.toDoubleOrNull()?.let { return it }
            }
        }

        // Priority 2: Look for line with numbers near price-related words
        for (line in lines) {
            if (line.contains("费") || line.contains("付") || line.contains("价") || line.contains("续费")) {
                val numMatcher = Pattern.compile("""([0-9]+(?:\.[0-9]{1,2})?)""").matcher(line)
                if (numMatcher.find()) {
                    numMatcher.group(1)?.toDoubleOrNull()?.let {
                        if (it > 0 && it < 10000) return it
                    }
                }
            }
        }

        return null
    }

    private fun extractCycle(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("连续包年") || lower.contains("按年") || lower.contains("每年") ||
                lower.contains("年卡") || lower.contains("包年") || lower.contains("年度") ||
                lower.contains("/年") -> Subscription.CYCLE_YEARLY

            lower.contains("连续包季") || lower.contains("按季") || lower.contains("每季") ||
                lower.contains("季卡") || lower.contains("包季") || lower.contains("季度") ||
                lower.contains("/季") -> Subscription.CYCLE_QUARTERLY

            lower.contains("连续包月") || lower.contains("按月") || lower.contains("每月") ||
                lower.contains("月卡") || lower.contains("包月") || lower.contains("月度") ||
                lower.contains("自动续费") || lower.contains("/月") -> Subscription.CYCLE_MONTHLY

            else -> null
        }
    }

    private fun extractDate(rawText: String, lines: List<String>): Long? {
        // Priority 1: Full date (yyyy-MM-dd)
        for (pattern in fullDatePatterns) {
            val matcher = pattern.matcher(rawText)
            if (matcher.find()) {
                val year = matcher.group(1)?.toIntOrNull()
                val month = matcher.group(2)?.toIntOrNull()
                val day = matcher.group(3)?.toIntOrNull()

                if (year != null && month != null && day != null && month in 1..12 && day in 1..31) {
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month - 1)
                        set(Calendar.DAY_OF_MONTH, day)
                    }
                    return DateUtils.getStartOfDay(calendar.timeInMillis)
                }
            }
        }

        // Priority 2: Short date (MM月dd日) - lines related to "下次扣费" or "到期"
        for (line in lines) {
            if (line.contains("到期") || line.contains("扣费") || line.contains("续费") || line.contains("时间")) {
                val matcher = shortDatePattern.matcher(line)
                if (matcher.find()) {
                    val month = matcher.group(1)?.toIntOrNull()
                    val day = matcher.group(2)?.toIntOrNull()
                    if (month != null && day != null && month in 1..12 && day in 1..31) {
                        val currentCal = Calendar.getInstance()
                        val currentYear = currentCal.get(Calendar.YEAR)
                        val targetCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, currentYear)
                            set(Calendar.MONTH, month - 1)
                            set(Calendar.DAY_OF_MONTH, day)
                        }
                        // If target date has already passed by more than 15 days, assume next year
                        if (targetCal.timeInMillis < currentCal.timeInMillis - 15L * 86400000L) {
                            targetCal.add(Calendar.YEAR, 1)
                        }
                        return DateUtils.getStartOfDay(targetCal.timeInMillis)
                    }
                }
            }
        }

        return null
    }

    private fun extractName(lines: List<String>): String? {
        // 1. Look for lines containing "会员", "VIP", "大会员", "订阅", "SVIP", "连续包"
        val keywordLine = lines.firstOrNull { line ->
            (line.contains("会员") || line.contains("VIP", ignoreCase = true) ||
             line.contains("订阅") || line.contains("连续包") || line.contains("畅享")) &&
             line.length in 3..30 &&
             !line.contains("管理") && !line.contains("取消")
        }

        if (keywordLine != null) {
            // Clean common prefixes
            val cleaned = keywordLine
                .replace(Regex("""^[0-9\.\s\-\*·、]+"""), "")
                .replace("自动续费", "")
                .replace("商品名称：", "")
                .replace("项目：", "")
                .trim()
            if (cleaned.isNotBlank()) return cleaned
        }

        // 2. Pick the first non-ignored line that looks like a brand or service title
        for (line in lines) {
            val cleaned = line.trim()
            if (cleaned.length in 2..25 &&
                !commonIgnoredLines.contains(cleaned) &&
                !cleaned.startsWith("¥") &&
                !cleaned.startsWith("￥") &&
                !cleaned.startsWith("$") &&
                !cleaned.matches(Regex("""^[0-9\.\s\:\-\/]+$"""))
            ) {
                return cleaned
            }
        }

        return null
    }
}

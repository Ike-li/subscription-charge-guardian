package com.example.ocr

import com.example.data.Subscription
import com.example.util.DateUtils
import java.util.Calendar
import java.util.regex.Pattern

data class AmountCandidate(val amount: Double, val currency: String)

data class ParsedSubscriptionData(
    val name: String? = null,
    val amount: Double? = null,
    val currency: String = "CNY",
    val billingCycle: String? = null,
    val nextBillingDate: Long? = null,
    val rawText: String = "",
    // 候选值供用户点选修正，第一个即上面采用的值
    val nameCandidates: List<String> = emptyList(),
    val amountCandidates: List<AmountCandidate> = emptyList(),
    val dateCandidates: List<Long> = emptyList()
) {
    /** 没识别出来、需要提示用户手动填写的字段（币种识别不到时默认 CNY，不算在内） */
    val missingFields: List<String>
        get() = listOfNotNull(
            "订阅名称".takeIf { name == null },
            "金额".takeIf { amount == null },
            "扣费周期".takeIf { billingCycle == null },
            "下次扣费日期".takeIf { nextBillingDate == null }
        )
}

object SubscriptionParser {

    private const val MAX_CANDIDATES = 5

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

    // 2026-10-15, 2026/10/15, 2026.10.15, 2026年10月15日；各段之间允许 OCR 折行或识别出的 "|"
    private val fullDatePattern =
        Regex("""(20[2-9][0-9])[\s|]*[\-年/.][\s|]*([0-1]?[0-9])[\s|]*[\-月/.][\s|]*([0-3]?[0-9])[日号]?""")

    private val shortDatePattern = Pattern.compile("""([0-1]?[0-9])[\-月\/\.]([0-3]?[0-9])[日号]""")

    private val nameLabelPattern = Regex("""^(商品名称|商品|项目|服务名称|产品名称)\s*[:：]\s*(.+)$""")

    // 价格样式的数字（12.99），前后不能紧挨数字或点，以排除 2026.10.15 这类日期
    private val priceLikePattern = Regex("""(?<![\d.])\d{1,5}\.\d{2}(?![\d.])""")

    // 截图顶部状态栏被识别出的时间，如 "1:19 M"
    private val statusBarTimePattern = Regex("""^\d{1,2}:\d{2}\b""")

    // 标明下次扣费日期的行；凭证上常先出现支付时间，需优先取这些行里的日期
    private val nextBillingKeywords = listOf("下次", "到期", "续费")

    private val commonIgnoredLines = setOf(
        "账单详情", "账单", "扣款成功", "自动续费管理", "订单详情", "交易详情",
        "微信支付", "支付宝", "收银台", "支付凭证", "扣费凭证", "Apple", "App Store",
        "订阅详情", "管理订阅", "完成", "取消订阅", "返回", "设置",
        // Google Play 订阅页的小标题
        "即将收取的费用", "主要付款方式", "备用付款方式", "您的方案包含", "活跃"
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
        val dateCandidates = (listOfNotNull(detectedDate) + fullDates(rawText)).distinct().take(MAX_CANDIDATES)
        val detectedName = extractName(lines)
        val nameCandidates = extractNameCandidates(detectedName, lines)
        val chosenAmount = detectedAmount?.let { AmountCandidate(it, detectedCurrency) }
        val amountCandidates = extractAmountCandidates(chosenAmount, rawText, detectedCurrency)

        return ParsedSubscriptionData(
            name = detectedName,
            amount = detectedAmount,
            currency = detectedCurrency,
            billingCycle = detectedCycle,
            nextBillingDate = detectedDate,
            rawText = rawText,
            nameCandidates = nameCandidates,
            amountCandidates = amountCandidates,
            dateCandidates = dateCandidates
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
                lower.contains("/年") || lower.contains("/12个月") -> Subscription.CYCLE_YEARLY

            lower.contains("连续包季") || lower.contains("按季") || lower.contains("每季") ||
                lower.contains("季卡") || lower.contains("包季") || lower.contains("季度") ||
                lower.contains("/季") || lower.contains("/3个月") -> Subscription.CYCLE_QUARTERLY

            lower.contains("连续包月") || lower.contains("按月") || lower.contains("每月") ||
                lower.contains("月卡") || lower.contains("包月") || lower.contains("月度") ||
                lower.contains("自动续费") || lower.contains("/月") ||
                lower.contains("/个月") || lower.contains("每个月") -> Subscription.CYCLE_MONTHLY

            else -> null
        }
    }

    private fun extractDate(rawText: String, lines: List<String>): Long? {
        // Priority 1: Full date (yyyy-MM-dd), lines naming the next billing date first
        val keywordLines = lines.filter { line -> nextBillingKeywords.any { line.contains(it) } }
        for (text in keywordLines + rawText) {
            fullDates(text).firstOrNull()?.let { return it }
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
        // 1. "商品名称：xxx" 这类带标签的行，冒号后就是名称（OCR 常把全角冒号识别成半角）
        lines.firstNotNullOfOrNull(::labeledName)?.let { return it }

        // 2. 第一个价格所在行正上方的标题行（应用商店订阅页、账单页通用的“名称 / 方案 / 价格”布局）
        priceLineIndices(lines).firstOrNull()
            ?.let { titlesAbove(lines, it).firstOrNull() }
            ?.let { return it }

        // 3. Look for lines containing "会员", "VIP", "大会员", "订阅", "SVIP", "连续包"
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
                // OCR 常把全角冒号识别成半角
                .replace(Regex("""^(商品名称|项目)\s*[:：]\s*"""), "")
                .trim()
            if (cleaned.isNotBlank()) return cleaned
        }

        // 4. Pick the first non-ignored line that looks like a brand or service title
        for (line in lines) {
            val cleaned = line.trim()
            if (cleaned.length in 2..25 &&
                !commonIgnoredLines.contains(cleaned) &&
                !statusBarTimePattern.containsMatchIn(cleaned) &&
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

    private fun isTitleLike(line: String): Boolean =
        line.length in 2..30 &&
            line !in commonIgnoredLines &&
            !statusBarTimePattern.containsMatchIn(line) &&
            !priceLikePattern.containsMatchIn(line) &&
            line.none { it in ":：,，。" } &&
            !line.matches(Regex("""^[0-9.\s\-/|]+$"""))

    private fun labeledName(line: String): String? =
        nameLabelPattern.find(line)?.groupValues?.get(2)?.trim()?.takeIf { it.isNotBlank() }

    private fun priceLineIndices(lines: List<String>): List<Int> =
        lines.indices.filter { priceLikePattern.containsMatchIn(lines[it]) }

    /** 价格行正上方的标题行，依次为 [推荐名称, 上一行, 下一行]（去重）；没有像标题的行时为空 */
    private fun titlesAbove(lines: List<String>, priceIndex: Int): List<String> {
        val lower = lines.getOrNull(priceIndex - 1)?.takeIf(::isTitleLike) ?: return emptyList()
        val upper = lines.getOrNull(priceIndex - 2)?.takeIf(::isTitleLike) ?: return listOf(lower)
        // 上一行是应用名、下一行是方案名；方案名已带品牌（ChatGPT Plus、SuperGrok）时只取方案名
        val brand = upper.split(' ', '-').first { it.isNotBlank() }
        val combined = when {
            lower.contains(brand, ignoreCase = true) -> lower
            upper.contains(lower, ignoreCase = true) -> upper
            else -> "$upper $lower"
        }
        return listOf(combined, upper, lower).distinct()
    }

    private fun extractNameCandidates(chosen: String?, lines: List<String>): List<String> =
        (listOfNotNull(chosen) +
            lines.mapNotNull(::labeledName) +
            priceLineIndices(lines).flatMap { titlesAbove(lines, it) }
        ).distinct().take(MAX_CANDIDATES)

    private fun extractAmountCandidates(
        chosen: AmountCandidate?,
        rawText: String,
        fallbackCurrency: String
    ): List<AmountCandidate> =
        (listOfNotNull(chosen) + priceLikePattern.findAll(rawText).map { match ->
            AmountCandidate(match.value.toDouble(), currencyBefore(rawText, match.range.first) ?: fallbackCurrency)
        }).distinct().take(MAX_CANDIDATES)

    /** 紧挨在数字前的币种标记（US$12.99、HK$78.00、¥15.00）；OCR 可能丢掉 $，只剩 "US" */
    private fun currencyBefore(text: String, index: Int): String? {
        val prefix = text.substring(maxOf(0, index - 4), index).uppercase()
        return when {
            "HK" in prefix -> "HKD"
            "US" in prefix || "$" in prefix -> "USD"
            "¥" in prefix || "￥" in prefix || "CNY" in prefix || "RMB" in prefix -> "CNY"
            else -> null
        }
    }

    /** 文本中所有合法的完整日期（当天零点），按出现顺序 */
    private fun fullDates(text: String): List<Long> =
        fullDatePattern.findAll(text).mapNotNull { match ->
            val (year, month, day) = match.destructured
            if (month.toInt() !in 1..12 || day.toInt() !in 1..31) return@mapNotNull null
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year.toInt())
                set(Calendar.MONTH, month.toInt() - 1)
                set(Calendar.DAY_OF_MONTH, day.toInt())
            }
            DateUtils.getStartOfDay(calendar.timeInMillis)
        }.toList()
}

package com.example.ocr

import java.util.Locale

/**
 * 添加/编辑表单中可由截图识别填充的字段。
 * 币种、周期、日期始终有默认值，靠 xxxChosen 区分是否由用户选过（编辑时载入的已有值也算选过）。
 */
data class OcrFillableForm(
    val name: String,
    val amountText: String,
    val currency: String,
    val billingCycle: String,
    val nextBillingDate: Long,
    val currencyChosen: Boolean = false,
    val billingCycleChosen: Boolean = false,
    val nextBillingDateChosen: Boolean = false
) {
    /** 用户已填写或选择过任一字段时，识别后需要先问用户再填 */
    val hasUserInput: Boolean
        get() = name.isNotBlank() || amountText.isNotBlank() ||
            currencyChosen || billingCycleChosen || nextBillingDateChosen

    /**
     * 把识别结果填进表单；overwriteAll 为 false 时只填空白项，识别不到的字段保持原值
     */
    fun fillWith(parsed: ParsedSubscriptionData, overwriteAll: Boolean): OcrFillableForm = copy(
        name = parsed.name?.takeIf { overwriteAll || name.isBlank() } ?: name,
        amountText = parsed.amount?.takeIf { overwriteAll || amountText.isBlank() }
            ?.let { String.format(Locale.US, "%.2f", it) } ?: amountText,
        currency = if (overwriteAll || !currencyChosen) parsed.currency else currency,
        billingCycle = parsed.billingCycle?.takeIf { overwriteAll || !billingCycleChosen } ?: billingCycle,
        nextBillingDate = parsed.nextBillingDate?.takeIf { overwriteAll || !nextBillingDateChosen } ?: nextBillingDate
    )
}

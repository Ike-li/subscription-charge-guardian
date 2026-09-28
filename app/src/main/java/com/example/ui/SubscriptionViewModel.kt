package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Subscription
import com.example.data.SubscriptionRepository
import com.example.ocr.OcrManager
import com.example.ocr.ParsedSubscriptionData
import com.example.ocr.SubscriptionParser
import com.example.util.DateUtils
import com.example.worker.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubscriptionViewModel(
    private val repository: SubscriptionRepository,
    private val appContext: Context
) : ViewModel() {

    // 所有监控中的订阅（按扣费日期升序）
    val activeSubscriptions: StateFlow<List<Subscription>> = repository.activeSubscriptions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 所有订阅记录（包括已取消）
    val allSubscriptions: StateFlow<List<Subscription>> = repository.allSubscriptions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 本月预计扣费，按币种分别合计（不联网拿不到汇率，不做换算）
    val monthlyTotalsByCurrency: StateFlow<Map<String, Double>> = activeSubscriptions.map { list ->
        list.groupBy { it.currency }.mapValues { (_, subs) -> subs.sumOf { it.calculateMonthlyAmount() } }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // 年度预计扣费，按币种分别合计
    val yearlyTotalsByCurrency: StateFlow<Map<String, Double>> = activeSubscriptions.map { list ->
        list.groupBy { it.currency }.mapValues { (_, subs) -> subs.sumOf { it.calculateYearlyAmount() } }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // 最近 7 天即将扣费列表（nextBillingDate 在 0..7 天内且 isActive 为 true，按日期升序）
    val upcoming7DaysSubscriptions: StateFlow<List<Subscription>> = activeSubscriptions.map { list ->
        list.filter { sub ->
            val days = DateUtils.daysFromToday(sub.nextBillingDate)
            days in 0..7
        }.sortedBy { it.nextBillingDate }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // 打开 App 时先顺延已过扣费日的订阅，避免后台任务还没跑时首页显示“已逾期”
        viewModelScope.launch {
            repository.rollForwardOverdue(System.currentTimeMillis())
        }
    }

    // OCR 识别状态
    private val _isOcrProcessing = MutableStateFlow(false)
    val isOcrProcessing: StateFlow<Boolean> = _isOcrProcessing.asStateFlow()

    private val _ocrRawText = MutableStateFlow<String?>(null)
    val ocrRawText: StateFlow<String?> = _ocrRawText.asStateFlow()

    private val _ocrError = MutableStateFlow<String?>(null)
    val ocrError: StateFlow<String?> = _ocrError.asStateFlow()

    fun getSubscriptionById(id: Long) = repository.getSubscriptionById(id)

    fun saveSubscription(subscription: Subscription, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            if (subscription.id == 0L) {
                repository.insert(subscription)
            } else {
                repository.update(subscription)
            }
            ReminderScheduler.triggerImmediateCheck(appContext)
            onComplete()
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            repository.delete(subscription)
        }
    }

    fun deleteSubscriptionById(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun markAsCancelled(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.setActive(id, false)
            onComplete()
        }
    }

    /**
     * 处理截图 OCR 识别与规则抽取
     */
    fun processImageOcr(
        context: Context,
        uri: Uri,
        onSuccess: (ParsedSubscriptionData) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isOcrProcessing.value = true
            _ocrError.value = null

            val result = OcrManager.recognizeTextFromUri(context, uri)
            _isOcrProcessing.value = false

            result.onSuccess { text ->
                if (text.isBlank()) {
                    val errorMsg = "没有识别到文字，请换一张更清晰的截图或手动填写"
                    _ocrError.value = errorMsg
                    onError(errorMsg)
                } else {
                    _ocrRawText.value = text
                    val parsedData = SubscriptionParser.parse(text)
                    onSuccess(parsedData)
                }
            }.onFailure { ex ->
                val errorMsg = "识别失败：${ex.localizedMessage ?: "无法解析图片"}，请手动填写"
                _ocrError.value = errorMsg
                onError(errorMsg)
            }
        }
    }

    fun clearOcrState() {
        _isOcrProcessing.value = false
        _ocrRawText.value = null
        _ocrError.value = null
    }

    companion object {
        fun provideFactory(
            repository: SubscriptionRepository,
            context: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SubscriptionViewModel(repository, context.applicationContext) as T
            }
        }
    }
}

package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Subscription
import com.example.ocr.OcrFillableForm
import com.example.ocr.ParsedSubscriptionData
import com.example.ui.SubscriptionViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DateSelectionDialog
import com.example.util.DateUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    subscriptionId: Long,
    viewModel: SubscriptionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isEditMode = subscriptionId > 0

    // 表单状态
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("CNY") }
    var billingCycle by remember { mutableStateOf(Subscription.CYCLE_MONTHLY) }
    var nextBillingDate by remember { mutableLongStateOf(DateUtils.getStartOfDay(System.currentTimeMillis())) }
    var autoRenew by remember { mutableStateOf(true) }
    var reminderDaysBefore by remember { mutableStateOf(3) }
    var cancelUrl by remember { mutableStateOf("") }
    var cancelNote by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(true) }
    var createdAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 币种、周期、日期都有默认值，单独记录用户是否选过，截图识别“仅填空白项”时据此判断
    var currencyChosen by remember { mutableStateOf(false) }
    var billingCycleChosen by remember { mutableStateOf(false) }
    var nextBillingDateChosen by remember { mutableStateOf(false) }

    // 校验错误状态
    var nameError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }

    // 对话框与底栏状态
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showImageSourceSheet by remember { mutableStateOf(false) }
    var showRawTextPreview by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var pendingParsedData by remember { mutableStateOf<ParsedSubscriptionData?>(null) }

    // 相机临时图片 URI
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // OCR 状态
    val isOcrProcessing by viewModel.isOcrProcessing.collectAsStateWithLifecycle()
    val ocrRawText by viewModel.ocrRawText.collectAsStateWithLifecycle()
    val ocrError by viewModel.ocrError.collectAsStateWithLifecycle()

    // 如果是编辑模式，载入既有数据
    LaunchedEffect(subscriptionId) {
        if (isEditMode) {
            viewModel.getSubscriptionById(subscriptionId).collect { existing ->
                existing?.let {
                    name = it.name
                    amountText = it.amount.toString()
                    currency = it.currency
                    billingCycle = it.billingCycle
                    nextBillingDate = it.nextBillingDate
                    autoRenew = it.autoRenew
                    reminderDaysBefore = it.reminderDaysBefore
                    cancelUrl = it.cancelUrl ?: ""
                    cancelNote = it.cancelNote ?: ""
                    isActive = it.isActive
                    createdAt = it.createdAt
                    currencyChosen = true
                    billingCycleChosen = true
                    nextBillingDateChosen = true
                }
            }
        } else {
            viewModel.clearOcrState()
        }
    }

    val currentOcrForm: () -> OcrFillableForm = {
        OcrFillableForm(
            name, amountText, currency, billingCycle, nextBillingDate,
            currencyChosen, billingCycleChosen, nextBillingDateChosen
        )
    }

    // 识别结果填入表单
    val applyOcr: (ParsedSubscriptionData, Boolean) -> Unit = { data, overwriteAll ->
        val filled = currentOcrForm().fillWith(data, overwriteAll)
        name = filled.name
        amountText = filled.amountText
        currency = filled.currency
        billingCycle = filled.billingCycle
        nextBillingDate = filled.nextBillingDate
        if (data.missingFields.isNotEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("未识别到：${data.missingFields.joinToString("、")}，请手动填写")
            }
        }
    }

    // 用户没填过任何内容时直接填入，否则弹窗让用户选择覆盖方式
    val onOcrParsed: (ParsedSubscriptionData) -> Unit = { data ->
        if (currentOcrForm().hasUserInput) {
            pendingParsedData = data
            showOverwriteDialog = true
        } else {
            applyOcr(data, true)
        }
    }

    // 相册选择器 (PickVisualMedia, 降级 GetContent)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            viewModel.processImageOcr(
                context = context,
                uri = it,
                onSuccess = onOcrParsed,
                onError = { err ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                }
            )
        }
    }

    val fallbackContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.processImageOcr(
                context = context,
                uri = it,
                onSuccess = onOcrParsed,
                onError = { err ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                }
            )
        }
    }

    // 相机拍照 Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            tempCameraUri?.let { uri ->
                viewModel.processImageOcr(
                    context = context,
                    uri = uri,
                    onSuccess = onOcrParsed,
                    onError = { err ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        }
    }

    // 相机权限请求 Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createTempCaptureUri(context)
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, "需要相机权限以拍摄截图", Toast.LENGTH_SHORT).show()
        }
    }

    // 触发拍照
    val startCameraCapture: () -> Unit = {
        val hasCamPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCamPermission) {
            val uri = createTempCaptureUri(context)
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 触发相册选择
    val startGalleryPick: () -> Unit = {
        try {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: Exception) {
            fallbackContentLauncher.launch("image/*")
        }
    }

    // 日期选择弹窗
    if (showDatePicker) {
        DateSelectionDialog(
            initialDateMillis = nextBillingDate,
            onDateSelected = { selected ->
                nextBillingDate = selected
                nextBillingDateChosen = true
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // 删除确认弹窗
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = "删除此订阅",
            message = "确定要删除该订阅吗？删除后将不再监控和提醒。",
            onConfirm = {
                viewModel.deleteSubscriptionById(subscriptionId)
                showDeleteConfirm = false
                onNavigateBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    // 覆盖确认对话框（当用户已手动填写某些内容时）
    if (showOverwriteDialog && pendingParsedData != null) {
        val parsed = pendingParsedData!!
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("智能识别填充", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "截图识别成功！检测到您已填写了部分信息。请选择填充方式：",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        applyOcr(parsed, true)
                        showOverwriteDialog = false
                    }
                ) {
                    Text("全部替换", style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        applyOcr(parsed, false)
                        showOverwriteDialog = false
                    }
                ) {
                    Text("仅填空白项", style = MaterialTheme.typography.titleMedium)
                }
            }
        )
    }

    // 拍照 / 相册 底部选择菜单
    if (showImageSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImageSourceSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "选择截图识别来源",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "将在您手机本地使用 ML Kit 识别，离线完成，绝不上传云端。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showImageSourceSheet = false
                            startGalleryPick()
                        }
                        .testTag("ocr_pick_gallery_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "从手机相册选择",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "适合已保存的扣费凭证或账单截图",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showImageSourceSheet = false
                            startCameraCapture()
                        }
                        .testTag("ocr_take_photo_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "拍照识别",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "使用相机拍摄另一台设备或纸质账单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "编辑订阅" else "添加新订阅",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            var hasError = false
                            if (name.isBlank()) {
                                nameError = "请输入订阅名称"
                                hasError = true
                            } else {
                                nameError = null
                            }

                            val amountValue = amountText.toDoubleOrNull()
                            if (amountValue == null || amountValue <= 0.0) {
                                amountError = "请输入有效的金额（大于0）"
                                hasError = true
                            } else {
                                amountError = null
                            }

                            if (!hasError && amountValue != null) {
                                val subscription = Subscription(
                                    id = if (isEditMode) subscriptionId else 0L,
                                    name = name.trim(),
                                    amount = amountValue,
                                    currency = currency,
                                    billingCycle = billingCycle,
                                    nextBillingDate = nextBillingDate,
                                    autoRenew = autoRenew,
                                    cancelUrl = cancelUrl.trim().ifBlank { null },
                                    cancelNote = cancelNote.trim().ifBlank { null },
                                    reminderDaysBefore = reminderDaysBefore,
                                    isActive = isActive,
                                    createdAt = createdAt
                                )
                                viewModel.saveSubscription(subscription) {
                                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("save_subscription_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditMode) "保存修改" else "完成添加",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // OCR 识别按钮与状态区域
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "从截图识别",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "上传续费账单或扣费凭证，自动填充表单",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Button(
                                onClick = { showImageSourceSheet = true },
                                modifier = Modifier.testTag("ocr_start_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("智能识别")
                            }
                        }

                        // 识别中进度指示
                        AnimatedVisibility(visible = isOcrProcessing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "正在本地识别截图文字，无需联网...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // 识别失败提示
                        ocrError?.let { err ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { showImageSourceSheet = true }) {
                                        Text("重试")
                                    }
                                }
                            }
                        }

                        // 识别到的原始文字预览区域
                        ocrRawText?.let { raw ->
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showRawTextPreview = !showRawTextPreview },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Notes,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "识别到的原始文字预览",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Icon(
                                    imageVector = if (showRawTextPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            AnimatedVisibility(visible = showRawTextPreview) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = raw,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { showImageSourceSheet = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("重新识别")
                                }
                            }
                        }
                    }
                }
            }

            // 1. 订阅名称 (必填)
            item {
                Column {
                    Text(
                        text = "订阅名称 *",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (it.isNotBlank()) nameError = null
                        },
                        placeholder = { Text("例如：哔哩哔哩大会员、爱奇艺VIP", style = MaterialTheme.typography.bodyLarge) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_subscription_name"),
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                }
            }

            // 2. 金额与币种 (必填)
            item {
                Column {
                    Text(
                        text = "扣费金额与币种 *",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = {
                                amountText = it
                                if (it.isNotBlank()) amountError = null
                            },
                            placeholder = { Text("0.00", style = MaterialTheme.typography.bodyLarge) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_subscription_amount"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = amountError != null,
                            supportingText = amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )

                        // 币种选择
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            listOf("CNY" to "¥", "USD" to "$", "HKD" to "HK$").forEach { (curr, label) ->
                                FilterChip(
                                    selected = currency == curr,
                                    onClick = {
                                        currency = curr
                                        currencyChosen = true
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. 扣费周期
            item {
                Column {
                    Text(
                        text = "扣费周期 *",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val cycles = listOf(
                            Subscription.CYCLE_MONTHLY to "每月",
                            Subscription.CYCLE_QUARTERLY to "每季",
                            Subscription.CYCLE_YEARLY to "每年"
                        )
                        cycles.forEach { (cycleKey, label) ->
                            val isSelected = billingCycle == cycleKey
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        billingCycle = cycleKey
                                        billingCycleChosen = true
                                    }
                                    .testTag("cycle_option_$cycleKey"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. 下次扣费日期
            item {
                Column {
                    Text(
                        text = "下次扣费日期 *",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .testTag("select_billing_date_card"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = DateUtils.formatDate(nextBillingDate),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "修改日期",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 5. 是否自动续费
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动续费",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "到期后平台将自动从支付账户扣款",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoRenew,
                            onCheckedChange = { autoRenew = it },
                            modifier = Modifier.testTag("switch_auto_renew")
                        )
                    }
                }
            }

            // 6. 提前提醒天数
            item {
                Column {
                    Text(
                        text = "提前几天提醒扣费",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 3, 7, 14).forEach { days ->
                            FilterChip(
                                selected = reminderDaysBefore == days,
                                onClick = { reminderDaysBefore = days },
                                label = {
                                    Text(
                                        text = "${days}天前",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reminder_chip_$days")
                            )
                        }
                    }
                }
            }

            // 7. 取消链接 (选填)
            item {
                Column {
                    Text(
                        text = "取消续费网页链接（选填）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = cancelUrl,
                        onValueChange = { cancelUrl = it },
                        placeholder = { Text("例如：https://...", style = MaterialTheme.typography.bodyLarge) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Link, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_cancel_url"),
                        singleLine = true
                    )
                }
            }

            // 8. 取消步骤备注 (选填)
            item {
                Column {
                    Text(
                        text = "取消步骤备注（选填）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = cancelNote,
                        onValueChange = { cancelNote = it },
                        placeholder = {
                            Text(
                                "例如：微信 -> 我 -> 服务 -> 钱包 -> 支付设置 -> 自动续费，找到此项关闭",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_cancel_note"),
                        minLines = 3,
                        maxLines = 6
                    )
                }
            }

            // 如果是编辑模式，底部提供明显的删除按钮
            if (isEditMode) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("edit_screen_delete_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除此订阅", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

/**
 * 为相机拍照生成临时文件 URI
 */
private fun createTempCaptureUri(context: Context): Uri {
    val tempFile = File.createTempFile("subguard_capture_", ".jpg", context.cacheDir).apply {
        createNewFile()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}

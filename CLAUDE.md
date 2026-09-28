# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

「订阅卫士」：纯本地的 Android 应用（Kotlin + Jetpack Compose，单模块 `:app`），记录自动续费订阅，扣费前本地通知，支持截图 OCR 自动填表。项目最初由 Google AI Studio 生成，`metadata.json`、`.env.example`、secrets 插件和 `app/build.gradle.kts` 里被注释掉的 Firebase/Gemini 依赖都是模板残留，与需求相反，不要启用。

## 构建与测试

需要 JDK 17–21（Gradle 9.3 / AGP 9.1），并且 `JAVA_HOME` 指向它。本地构建还依赖两个已被 gitignore 的文件：
- `local.properties`，内容是 `sdk.dir=<Android SDK 路径>`；SDK 需要装 platform `android-36.1`。
- 项目根目录下的 `debug.keystore`：debug 签名配置写死了这个路径，新 clone 下来要自己生成：
  `keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest            # 编译 + 全部单元测试
./gradlew :app:testDebugUnitTest --tests 'com.example.data.SubscriptionRollForwardTest'   # 单个测试类
./gradlew :app:testDebugUnitTest --tests 'com.example.ui.HomeScreenTest.long*'            # 单个方法（方法名带反引号和空格，用通配）
./gradlew :app:lintDebug                                       # 基线：0 errors，64 warnings
```

- **release 开了 R8（full mode）和资源裁剪**，单元测试只跑未压缩的代码，发现不了 R8 问题。凡是被反射实例化的类，都要在 `app/proguard-rules.pro` 里 keep 住，已有一条是给 ML Kit 的 `ComponentRegistrar` 的：缺了它，release 包里 OCR 会报 NullPointerException。改了依赖或混淆规则后，要把 release 包装到设备上，把 OCR 识别和保存流程走一遍。
- release 签名从环境变量读取 `KEYSTORE_PATH`、`STORE_PASSWORD`、`KEY_PASSWORD`，alias 固定为 `upload`；本地验证可以临时生成一个 alias 为 `upload` 的 keystore 传进去。
- 只打包 `arm64-v8a` 和 `armeabi-v7a`（ML Kit 原生库每个架构约 10MB），所以 x86 模拟器装不上。
- 测试结果在 `app/build/test-results/testDebugUnitTest/*.xml`。所有测试都是本地 JVM 测试，大部分用 Robolectric（`@Config(sdk = [34])`）；`androidTest` 里只有模板用例。
- 构建时 KSP 会打印一条 `AWT-EventQueue` 的 NullPointerException，这是无害的噪音。

## 架构

数据流只有一条：Room（单表 `subscriptions`）→ `SubscriptionRepository` → **一个共享的** `SubscriptionViewModel`。ViewModel 在 `MainActivity` 里创建，经 `ui/SubGuardAppNav.kt` 的 `SubGuardApp` 传给所有页面；表单字段是 `AddEditScreen` 里的本地 `remember` 状态。

**提醒链路**，跨越四个文件：
1. `SubGuardApplication.onCreate` 创建"扣费提醒"通知渠道，并用 `PeriodicWorkRequest`（24h，`KEEP`）注册 `BillingReminderWorker`。
2. `saveSubscription` 每次保存后都会触发一次立即运行的检查（`ReminderScheduler.triggerImmediateCheck`）。
3. Worker 先调用 `SubscriptionRepository.rollForwardOverdue` 顺延已过期的扣费日，再对 `DateUtils.daysBetween(today, nextBillingDate) == reminderDaysBefore`（需求原文就是"正好等于"）的订阅发通知。
4. 通知携带 `EXTRA_SUBSCRIPTION_ID`，`MainActivity` 仅在 `savedInstanceState == null` 时读取它，交给 `SubGuardApp` 导航到 `detail/{id}`。

**扣费日顺延**：规则在 `Subscription.rollForward`：只处理开着自动续费的订阅；扣费日当天不顺延；从原扣费日一次加 n 个周期，直到不早于今天。Worker 和 ViewModel 初始化时都会调用。已知限制：每月 29 到 31 号扣费的订阅过完短月后会停在较小的日期，因为没有"原始扣费日"字段（原因见下面的数据库一节）。

**OCR**：`OcrManager`（ML Kit 中文模型，打包在 APK 里，离线运行）→ `SubscriptionParser`（正则 + 关键词启发式）→ `ParsedSubscriptionData` → `OcrFillableForm.fillWith` 合并进 `AddEditScreen` 的表单。币种、周期、日期都有默认值，所以表单用 `currencyChosen` / `billingCycleChosen` / `nextBillingDateChosen` 记录"用户是否选过"；编辑模式载入已有记录时，这三项都算已选。

**金额合计按币种分组**（`monthlyTotalsByCurrency`），不做汇率换算：首页大数字只显示 CNY，外币单独列"另有 …"。

## 需求约束（改动前先对照）

以下都是原始需求或用户做过的决定，代码里不一定看得出来：
- **不申请网络权限**：`INTERNET` 是 ML Kit 的遥测依赖带进来的，`AndroidManifest.xml` 用 `tools:node="remove"` 把它剔除；`AppManifestTest` 会检查。新增依赖后，要确认合并后的 manifest 里没有重新出现 `INTERNET`。
- **数据只留在本机**：`allowBackup="false"`，没有云同步，也没有导出功能。
- **不引入 Gemini 或任何生成式 AI 依赖**。
- **面向中老年用户的字号**：body/label 样式 ≥ 16sp，title 及更大 ≥ 20sp，由 `TypographyTest` 检查。界面文字一律用 `MaterialTheme.typography`，不写死 `fontSize`。
- **OCR 结果只是建议值**：不能覆盖用户已填的内容（"仅填空白项"），没识别出来的字段要提示用户手动填写。
- 界面文案、代码注释都用中文。

## 数据库与测试的坑

- `AppDatabase` 用的是 `fallbackToDestructiveMigration(dropAllTables = true)`，而且 `exportSchema = false`：**只要改 `Subscription` 实体，用户数据就会被全部清空**。改表结构必须提升 version，并写一个真正的 `Migration`。
- Robolectric 的默认屏幕很小，`LazyColumn` 里屏幕外的条目根本不会被渲染。要先滚动再断言：首页列表的 tag 是 `home_screen_list`；表单的 `LazyColumn` 没有 tag，用 `onNode(hasScrollToNodeAction())` 定位。
- `ExampleRobolectricTest` 启动的是真实的 `MainActivity`，用的是基于文件的 `AppDatabase` 单例，写进去的数据会带到同一个类的其他用例里。依赖数据内容的界面测试，按 `HomeScreenTest` 的做法：内存 Room 加直接构造的 `SubscriptionViewModel`，再用 `createComposeRule()` 渲染单个页面。
- 当前 Compose BOM（2024.09）里 `combinedClickable` 需要 `@OptIn(ExperimentalFoundationApi::class)`。

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

「订阅卫士」：纯本地的 Android 应用（Kotlin + Jetpack Compose，单模块 `:app`），记录自动续费订阅，扣费前本地通知，支持截图 OCR 自动填表。项目最初由 Google AI Studio 生成，包名 `com.aistudio.subguard.kdypnx` 沿用至今：**不要改 applicationId**，改了已安装的 App 就无法覆盖升级（数据只存本机、关闭了备份，卸载重装会丢数据）。

## 构建与测试

需要 JDK 17–21（Gradle 9.3 / AGP 9.1），并且 `JAVA_HOME` 指向它。本地构建还依赖两个已被 gitignore 的文件：
- `local.properties`，内容是 `sdk.dir=<Android SDK 路径>`；SDK 需要装 platform `android-36.1`。
- 项目根目录下的 `debug.keystore`：debug 签名配置写死了这个路径，新 clone 下来要自己生成：
  `keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest            # 编译 + 全部单元测试
./gradlew :app:testDebugUnitTest --tests 'com.example.data.SubscriptionRollForwardTest'   # 单个测试类
./gradlew :app:testDebugUnitTest --tests 'com.example.ui.HomeScreenTest.long*'            # 单个方法（方法名带反引号和空格，用通配）
./gradlew :app:lintDebug                                       # 基线：0 errors，51 warnings
```

- **release 开了 R8（full mode）和资源裁剪**，单元测试只跑未压缩的代码，发现不了 R8 问题。凡是被反射实例化的类，都要在 `app/proguard-rules.pro` 里 keep 住，已有一条是给 ML Kit 的 `ComponentRegistrar` 的：缺了它，release 包里 OCR 会报 NullPointerException。改了依赖或混淆规则后，要把 release 包装到设备上，把 OCR 识别和保存流程走一遍。
- release 签名从环境变量读取 `KEYSTORE_PATH`、`STORE_PASSWORD`、`KEY_PASSWORD`，alias 固定为 `upload`；本地验证可以临时生成一个 alias 为 `upload` 的 keystore 传进去。
- 只打包 `arm64-v8a` 和 `armeabi-v7a`（ML Kit 原生库每个架构约 10MB），所以 x86 模拟器装不上。
- 测试结果在 `app/build/test-results/testDebugUnitTest/*.xml`。单元测试都是本地 JVM 测试，大部分用 Robolectric（`@Config(sdk = [34])`）。
- Robolectric 没有系统日历，所以写日历的 `CalendarSyncTest` 放在 `androidTest`，要在真机或模拟器上跑（arm64，理由见上一条）。`connectedDebugAndroidTest` 会在所有已连接的设备上跑，想只跑一台时手动装包：
  ```bash
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
  adb -s <serial> install -r -t app/build/outputs/apk/debug/app-debug.apk
  adb -s <serial> install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb -s <serial> shell am instrument -w -e class com.example.calendar.CalendarSyncTest com.aistudio.subguard.kdypnx.test/androidx.test.runner.AndroidJUnitRunner
  ```
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

**手机日历**（`calendar/`），为通知权限被关、后台被清理的情况兜底，有两条路：
- 详情页"添加到手机日历"发 `ACTION_INSERT` Intent，由日历 App 让用户确认保存，不需要权限。
- 设置页开关打开后，`CalendarSync` 在本机账号（`ACCOUNT_TYPE_LOCAL`）下建一个"订阅卫士"日历，每次清空后重写今天起 12 个月内的扣费日（当天 9:00，提前 `reminderDaysBefore` 天提醒）。ViewModel 把 `activeSubscriptions` 和开关状态 `combine` 起来，任一变化就重写；Worker 每天也重写一次；关掉开关会删除整个日历。开关状态存在 SharedPreferences（`subguard_settings`），不在 Room 里。
- 对这个日历的增删都以同步适配器身份（`CALLER_IS_SYNCADAPTER`）进行：本机账号没有同步程序，普通删除只给事件打删除标记，事件会一直留在日历数据库里。卸载 App 不会删除这个日历，设置页的说明文字提醒了用户。

**金额合计按币种分组**（`monthlyTotalsByCurrency`），不做汇率换算：首页大数字只显示 CNY，外币单独列"另有 …"。

## 需求约束（改动前先对照）

以下都是原始需求或用户做过的决定，代码里不一定看得出来：
- **不申请网络权限**：`INTERNET` 是 ML Kit 的遥测依赖带进来的，`AndroidManifest.xml` 用 `tools:node="remove"` 把它剔除；`AppManifestTest` 会检查。新增依赖后，要确认合并后的 manifest 里没有重新出现 `INTERNET`。
- **数据只留在本机**：`allowBackup="false"`，没有云同步，也没有导出功能。唯一的例外是用户主动打开的"同步到手机日历"：只写本机账号下的日历，不能写进任何云端账号的日历。
- **不引入 Gemini 或任何生成式 AI 依赖**。
- **面向中老年用户的字号**：body/label 样式 ≥ 16sp，title 及更大 ≥ 20sp，由 `TypographyTest` 检查。界面文字一律用 `MaterialTheme.typography`，不写死 `fontSize`。
- **OCR 结果只是建议值**：不能覆盖用户已填的内容（"仅填空白项"），没识别出来的字段要提示用户手动填写。
- 界面文案、代码注释都用中文。
- README 有中文（`README.md`）和英文（`README.en.md`）两份，GitHub Pages 介绍页也有中文（`docs/index.html`）和英文（`docs/en/index.html`）两份，改功能说明或常见问题时四处一起改。介绍页的常见问题同时写在页面正文和 `<script type="application/ld+json">` 的 FAQPage 里，两处文字要一致。`docs/screenshots/` 和 `docs/social-preview.png` 只能用虚构数据，不能出现真实的订阅、金额或账号。

## 数据库与测试的坑

- `AppDatabase` 用的是 `fallbackToDestructiveMigration(dropAllTables = true)`，而且 `exportSchema = false`：**只要改 `Subscription` 实体，用户数据就会被全部清空**。改表结构必须提升 version，并写一个真正的 `Migration`。给实体加计算值要写成函数（如 `monthsPerCycle()`）：带幕后字段的属性会被 Room 当成一列。
- Robolectric 的默认屏幕很小，`LazyColumn` 里屏幕外的条目根本不会被渲染。要先滚动再断言：首页列表的 tag 是 `home_screen_list`；表单的 `LazyColumn` 没有 tag，用 `onNode(hasScrollToNodeAction())` 定位。
- `ExampleRobolectricTest` 启动的是真实的 `MainActivity`，用的是基于文件的 `AppDatabase` 单例，写进去的数据会带到同一个类的其他用例里。依赖数据内容的界面测试，按 `HomeScreenTest` 的做法：内存 Room 加直接构造的 `SubscriptionViewModel`，再用 `createComposeRule()` 渲染单个页面。
- 当前 Compose BOM（2024.09）里 `combinedClickable` 需要 `@OptIn(ExperimentalFoundationApi::class)`。

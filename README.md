# 订阅卫士

记录自动续费订阅、在扣费前提醒你的 Android 应用。完全离线：不申请网络权限，数据只存在手机上。字号偏大，照顾中老年用户。

*SubGuard is an offline Android app that tracks auto-renewing subscriptions and reminds you before each charge. It requests no network permission and keeps all data on the device. Subscription details can be filled in from a screenshot using on-device text recognition.*

## 功能

- **记录订阅**：名称、金额（人民币 / 美元 / 港币）、每月 / 每季 / 每年、下次扣费日期，还可以记下取消链接和取消步骤。
- **首页总览**：本月和全年预计扣费（按币种分别合计，不做汇率换算），最近 7 天要扣费的订阅，以及全部订阅。长按某个订阅可以删除。
- **扣费提醒**：在扣费前 1 / 3 / 7 / 14 天发本地通知，点通知直接进入这条订阅的详情。开着自动续费的订阅，扣费日一过会自动顺延到下一期。
- **写进手机日历**：通知被关掉、或 App 被系统清理后台时，还可以靠日历提醒。详情页的"添加到手机日历"会打开日历 App 并预填好这次扣费，不需要任何权限；设置页打开"同步到手机日历"后，所有订阅未来一年的扣费日会自动写进一个单独的"订阅卫士"日历，并按设置的天数提前提醒。
- **截图识别**：从相册选图或直接拍照，在本地识别文字并自动填表。识别不准时，名称、金额、日期下面会列出候选值，点一下就能换。用户已经填好的内容不会被识别结果覆盖。

## 隐私

- 不申请 `INTERNET` 权限，任何数据都不会发出去。
- 数据存放在手机本地数据库里，并且关闭了系统云备份。没有账号，也没有统计。
- 日历权限只在打开"同步到手机日历"时申请。App 只写自己建的"订阅卫士"日历，这个日历挂在本机账号下，不属于任何云端账号。卸载 App 不会删除它，卸载前请先关掉这个开关。
- 用"添加到手机日历"时，事件存进你在日历 App 里选的那个日历。如果那个日历会同步到云端（比如小米账号或 Google 账号），订阅名称和金额也会一起上传。
- 文字识别使用 Google ML Kit。它是闭源 SDK，识别模型直接打包在 APK 里，离线运行。

## 安装

安装包见本仓库的 Releases 页面；如果还没有发布，可以按下面的步骤自行构建。

- 需要 Android 7.0 及以上。只打包了 ARM 架构，x86 模拟器装不上。
- 在小米等国产系统上，为了让提醒准时，建议把这个 App 的省电策略设为"无限制"，并允许自启动。App 的设置页里也有相应入口。

## 构建

需要 JDK 17–21，以及装有 platform `android-36.1` 的 Android SDK。

```bash
# 1. 指定 SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 2. 生成 debug 签名文件（debug 构建会读取项目根目录下的 debug.keystore）
keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"

# 3. 构建并运行单元测试
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

release 构建开启了 R8 压缩，签名信息从环境变量 `KEYSTORE_PATH`、`STORE_PASSWORD`、`KEY_PASSWORD` 读取，密钥别名固定为 `upload`。

架构、测试约定和开发时容易踩的坑，写在 [CLAUDE.md](CLAUDE.md) 里。

## 已知限制

- 截图识别的规则只用 Google Play 订阅页的真实截图验证过。微信、支付宝、App Store 的页面还没有验证，识别效果可能较差，这时可以从候选值里选，或者手动修改。
- 一张截图里有多个订阅时，只会自动填入第一个，其余的可以从候选值里选。
- 每月 29–31 号扣费的月付订阅，经过较短的月份顺延后，可能会停在较小的日期上，比如 31 号变成 28 号。
- 多种币种不做汇率换算，分别合计。
- 日历同步只在 Android 模拟器上验证过写入。本机日历能不能显示、到时会不会弹提醒，取决于手机自带的日历 App，小米等品牌的日历还没有验证。

## 技术栈

Kotlin、Jetpack Compose（Material 3）、Room、WorkManager、ML Kit 文字识别（中文模型）。项目最初由 Google AI Studio 生成，之后经过了大量修改。

## 许可证

[MIT](LICENSE)

# SubGuard (订阅卫士)

[中文](README.md) | English

**SubGuard is an open-source, fully offline Android app for tracking auto-renewing subscriptions: it records the memberships you pay for and reminds you before every charge, so you can cancel the ones you no longer want in time.** It requests no network permission and keeps all data on the phone. Subscription details can be filled in from a screenshot of a receipt, and billing dates can be added to the phone's calendar. The UI uses large type for older users.

> **The app's interface is in Chinese only.**

<table>
  <tr>
    <td><img src="docs/screenshots/home.webp" width="200" alt="SubGuard home screen: ¥72.50 expected this month plus $15.49, and two subscriptions renewing within 7 days"></td>
    <td><img src="docs/screenshots/ocr.webp" width="200" alt="Screenshot recognition: the subscription name and the amount 15.00 filled in from a WeChat Pay receipt"></td>
    <td><img src="docs/screenshots/detail.webp" width="200" alt="Subscription details: the Add to phone calendar button and saved steps for cancelling auto-renewal"></td>
    <td><img src="docs/screenshots/settings.webp" width="200" alt="Settings: charge notifications and the enabled Sync to phone calendar switch"></td>
  </tr>
  <tr>
    <td align="center">Overview</td>
    <td align="center">Screenshot recognition</td>
    <td align="center">Details and cancellation steps</td>
    <td align="center">Notifications and calendar</td>
  </tr>
</table>

## Features

- **Track subscriptions**: name, amount (CNY, USD or HKD), monthly / quarterly / yearly cycle and the next billing date, plus a cancellation link and cancellation steps.
- **Overview**: expected charges for this month and this year (totalled per currency, with no exchange-rate conversion), subscriptions renewing within 7 days, and the full list. Long-press a subscription to delete it.
- **Charge reminders**: a local notification 1, 3, 7 or 14 days before each charge; tapping it opens that subscription. For auto-renewing subscriptions the billing date moves to the next cycle once it has passed.
- **Phone calendar**: a fallback for when notifications are turned off or the system kills the app in the background. "Add to phone calendar" on the details page opens your calendar app with the charge pre-filled and needs no permission. With "Sync to phone calendar" turned on in settings, the billing dates of every subscription for the next 12 months are written to a separate "订阅卫士" calendar, with a reminder the chosen number of days in advance.
- **Screenshot recognition**: pick a screenshot or take a photo, and on-device text recognition fills in the form. When a field is misread, other candidates found in the image are listed under it and can be picked with one tap. Fields you have already filled in are never overwritten.
- **Large type**: body text is at least 16sp and titles at least 20sp.

## Supported phones

| Works | Does not work |
|---|---|
| Android 7.0 or later on an ARM processor. Xiaomi / Redmi, OPPO / OnePlus / realme, vivo / iQOO, Honor, Samsung, Pixel and other Android phones qualify when they meet these two conditions | Android 6.0 or earlier |
| Phones without Google services (common in mainland China) | Huawei HarmonyOS NEXT (HarmonyOS 5 and later), which cannot install any APK |
| Huawei HarmonyOS 4 and earlier | x86 devices (mostly Android emulators on PCs) |

## FAQ

### Does SubGuard need internet access? Does it upload my data?

No to both. The app does not request the `INTERNET` permission, so it cannot connect to the network. Subscription data is stored only in a local database on the phone, and Android cloud backup is turned off. Screenshot recognition uses Google ML Kit models bundled in the APK and runs entirely on the phone.

### Can it cancel subscriptions for me?

No. SubGuard records subscriptions and reminds you; cancelling has to be done on the service's own platform. Each subscription can store a cancellation link and cancellation steps to follow when the reminder arrives.

### Why did a reminder arrive late or not at all?

Xiaomi, Huawei, OPPO, vivo and other Android skins restrict background work, which can delay or block notifications. Set SubGuard's battery policy to "No restrictions" and allow autostart (the app's settings page links to these system settings), or turn on "Sync to phone calendar" so the system calendar delivers the reminders.

### Does it work without Google Play services?

Yes. The text-recognition model is bundled in the APK and does not depend on Google Play services. It was tested on an Android 14 emulator with no Google components at all: screenshot recognition, charge notifications and calendar sync all worked.

### Does it work on Huawei HarmonyOS?

HarmonyOS 4 and earlier can install and run it. HarmonyOS NEXT (HarmonyOS 5 and later) has no Android runtime and cannot install any APK, so SubGuard does not run there.

### Which screens can screenshot recognition read?

The rules are tuned on real screenshots of Google Play's subscription page and on constructed samples of WeChat Pay receipts. They extract the name, amount, currency, billing cycle and next billing date. Alipay, the App Store and other screens have not been tested yet; when recognition gets something wrong, pick a candidate or edit the field.

### Which currencies are supported?

Chinese yuan (CNY), US dollar (USD) and Hong Kong dollar (HKD). The app is offline and has no exchange rates, so totals are shown per currency.

### Does calendar sync send my data to the cloud?

Automatic sync writes only to a "订阅卫士" calendar under an on-device local account that belongs to no cloud account. With "Add to phone calendar", you choose the calendar in your calendar app; if it syncs to a cloud account such as Google or Xiaomi, the subscription name and amount are uploaded with it.

### How do I install and update it?

Download the APK from [Releases](https://github.com/Ike-li/subscription-charge-guardian/releases). The app is offline and cannot check for updates itself: watch this repository's releases on GitHub (Watch → Custom → Releases) or track them with a tool such as Obtainium. Install a new version over the old one to keep your data.

### How can I verify the APK has not been tampered with?

Every release is signed with the same certificate, whose SHA-256 fingerprint is:

```
b4f99528a9c2bd045058116a99b7e72bc8dfd11985cc3d74d2bcbaeb4e7ead8d
```

Check it with `apksigner verify --print-certs SubGuard.apk` from the Android SDK.

### Is it free?

Yes. SubGuard is free and open source under the MIT license.

## Privacy

- No `INTERNET` permission; no data ever leaves the phone.
- Data lives in a local database with Android cloud backup turned off. There are no accounts and no analytics.
- The calendar permission is requested only when "Sync to phone calendar" is turned on. The app writes only to its own "订阅卫士" calendar, which belongs to a local on-device account. Uninstalling the app does not remove that calendar, so turn the switch off before uninstalling.
- Text recognition uses Google ML Kit, a closed-source SDK whose models are bundled in the APK and run offline.

## Installation

Download the APK (about 11 MB) from [Releases](https://github.com/Ike-li/subscription-charge-guardian/releases), or build it yourself as described below.

- Requires Android 7.0 or later. Only ARM builds are included, so x86 emulators cannot install it.
- APKs downloaded in a browser need "Install unknown apps" permission, and some phones show an extra security prompt; on Huawei phones, turn off Pure Mode first.
- On Xiaomi and similar Android skins, set the app's battery policy to "No restrictions" and allow autostart so reminders arrive on time.

## Building

Requires JDK 17–21 and an Android SDK with platform `android-36.1`.

```bash
# 1. Point Gradle at the SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 2. Create the debug keystore (the debug build reads debug.keystore from the project root)
keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"

# 3. Build and run the unit tests
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Release builds are shrunk with R8 and read signing details from the `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` environment variables; the key alias is `upload`.

Architecture, testing conventions and development pitfalls are documented (in Chinese) in [CLAUDE.md](CLAUDE.md).

## Known limitations

- The interface is Chinese only.
- Screenshot recognition has been verified only on real Google Play subscription screenshots and constructed WeChat Pay receipt samples. Alipay and App Store screens are untested and may be recognised poorly; pick a candidate or edit the field instead.
- When a screenshot shows several subscriptions, only the first is filled in automatically; the others appear as candidates.
- A monthly subscription billed on the 29th–31st may settle on an earlier day after rolling through a shorter month, for example the 31st becoming the 28th.
- Totals in different currencies are not converted.
- Tested only on Android 14 (without Google services) and Android 16 emulators; Android 7–13 and real devices from each brand have not been verified.
- Calendar sync has been verified on emulators to write events and trigger reminders on time. Whether the events show up and the reminders pop up depends on the phone's own calendar app; Xiaomi's and other brands' calendars have not been verified.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Room, WorkManager and ML Kit text recognition (Chinese model). The project was first generated with Google AI Studio and has since been substantially rewritten.

## License

[MIT](LICENSE)

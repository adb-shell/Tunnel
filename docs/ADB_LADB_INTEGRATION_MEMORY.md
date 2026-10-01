# CloudSend ADB/LADB Integration Memory

Generated: 2026-05-20
Last synchronized with source: 2026-06-04

This document is the engineering memory for the CloudSend ADB integration. It is based on the current CloudSend source tree, the local `ADB-CODE/` source tree, and the local `LADB/` source tree.

This document is now both the implementation memory and source-of-truth risk boundary for the ADB work that has landed in the source tree.

## 1. Current Decision

ADB capability is implemented in CloudSend as an isolated Android-side module.

Do not put ADB logic directly into the existing screen-share, ignore-capture, penetrate, blank-screen, touch-block, video stream, screenshot stream, or monitor-panel core paths.

Recommended direction:

- Keep the CloudSend Android ADB module isolated; do not add a second app.
- Continue to reuse LADB's local `adb` execution model.
- Accessibility-assisted wireless-debugging automation now has a best-effort implementation inside the existing CloudSend accessibility service. Future work should harden ROM compatibility rather than add a second accessibility service.
- Keep the existing CloudSend accessibility service as the only accessibility service.
- Keep the narrow bridge between `CloudSendAdbManager` and `nZW99cdXQ0COhB2o` for wireless-debugging automation.
- Keep future PC remote ADB commands behind an explicit protocol, status model, whitelist, timeout, and audit log.
- Never reuse existing side-button masks for ADB commands.

Key limitation:

- Android 11+ is the primary target for no-PC wireless debugging pairing.
- Android 9/10 do not have the standard Android wireless-debugging pairing-code flow.
- Domestic ROMs can rename or move Developer Options and Wireless Debugging pages. Accessibility automation must be best-effort, cancellable, timeout-protected, and user-visible.
- Built-in ADB plus accessibility plus remote command execution is a high-sensitivity capability. It must be explicit, optional, and auditable.

Current landed scope:

- Android local ADB pairing, mDNS scan, connect, shell, command input, output terminal, and limited shell-restart recovery are implemented.
- Accessibility-assisted automatic wireless-debugging setup is implemented as a best-effort, user-visible, cancellable flow through `cloudsend_adb_wireless_debug_status`, `cloudsend_adb_wireless_debug_set`, `cloudsend_adb_wireless_debug_cancel`, and `nZW99cdXQ0COhB2o.wirelessDebugAutomation*`.
- PC remote ADB command protocol is not implemented yet.
- ADB is not required for screen sharing and does not participate in side-button or video/screenshot stream state.

## 2. Current CloudSend State

### 2.1 Mobile Home Pages

Current files:

- `flutter/lib/mobile/pages/home_page.dart`
- `flutter/lib/mobile/pages/server_page.dart`
- `flutter/lib/mobile/pages/adb_page.dart`

Current facts:

- Android mobile home now uses a `PageView`.
- The only active pages are:
  - `ServerPage()` for the screen-share page.
  - `AdbPage()` for the ADB page.
- The bottom navigation bar remains commented out.
- `ConnectionPage` remains inside `if (false)` and is not active.
- `SettingsPage` remains commented out and is not active.
- The ADB page is now interactive and calls the isolated CloudSend ADB MethodChannel methods only when the user taps ADB controls.

Current ADB page UI:

- First card title: `ADB`.
- First card has explanatory text above a full-width button. The displayed Chinese label is represented in source as `\u542f\u52a8\u670d\u52a1` (`Start service`).
- Tapping the first-card button opens a pairing dialog and then starts/pairs the bundled ADB runner through MethodChannel.
- Second card title is represented in source as `\u81ea\u52a8\u5316\u65e0\u7ebf\u8c03\u8bd5` (`Automated wireless debugging`).
- Second card has explanatory text above a full-width wireless-debugging action button. The displayed Chinese label is represented in source as `\u6253\u5f00\u8c03\u8bd5` (`Open debugging`) when wireless debugging is off.
- The automated wireless-debugging action calls `AndroidAdbManager.wirelessDebugStatus()`, `AndroidAdbManager.setWirelessDebugging(...)`, and `AndroidAdbManager.cancelWirelessDebugging()`.
- The ADB page does not call `serverModel`, `toggleService`, screen-share service methods, side-button methods, or Rust connection logic.

### 2.1.1 ADB Environment and Runtime Added on 2026-05-20/2026-05-21

Implemented ADB integration:

- Added `libadb.so` from local LADB into CloudSend Android `jniLibs` for:
  - `arm64-v8a`
  - `armeabi-v7a`
  - `x86_64`
- Added `flutter/android/app/src/main/jniLibs/LIBADB_LICENSE`.
- Added Gradle `packagingOptions.jniLibs.useLegacyPackaging = true` so `libadb.so` can be extracted to `applicationInfo.nativeLibraryDir`.
- Do not add Gradle `ndk.abiFilters` here: Flutter `--split-per-abi` already sets split ABI filters, and both configurations conflict.
- Added Manifest permission `CHANGE_WIFI_MULTICAST_STATE`.
- Added Manifest permission `CHANGE_WIFI_STATE` to match the LADB wireless-debugging environment baseline.
- Added Manifest declaration for `WRITE_SECURE_SETTINGS` with `tools:ignore="ProtectedPermissions"`.
- Added isolated Kotlin package:
  - `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbState.kt`
  - `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbRunner.kt`
  - `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbManager.kt`
  - `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbDnsDiscover.kt`
- `CloudSendAdbState` records support/binary/initialization/pairing/paired/connected/shell-ready/output/error state, the extracted `adbPath`, whether the binary exists, whether it is executable, and the ProcessBuilder environment map (`HOME` and `TMPDIR`).
- Added safe Flutter MethodChannel hooks on the existing Android channel:
  - `cloudsend_adb_init`: initializes the isolated ADB environment state and returns a map.
  - `cloudsend_adb_status`: returns the current ADB environment state map.
  - `cloudsend_adb_output`: returns the bounded terminal output buffer.
  - `cloudsend_adb_start`: starts the LADB-style ADB scan/connect/shell flow.
  - `cloudsend_adb_stop`: stops the current ADB shell/server state without clearing pairing memory.
  - `cloudsend_adb_local_shell`: legacy non-ADB local shell hook. The ADB page no longer uses this for `Auto`.
  - `cloudsend_adb_pair`: runs real `adb pair` with endpoint fallback (`localhost:<port>`, `127.0.0.1:<port>`, and the current Wi-Fi IPv4 address when available) plus the supplied pairing code.
  - `cloudsend_adb_command`: writes user input into the current shell process.
  - `cloudsend_adb_wireless_debug_status`: returns the current best-effort wireless-debugging automation state.
  - `cloudsend_adb_wireless_debug_set`: asks the existing AccessibilityService automation to enable or disable wireless debugging.
  - `cloudsend_adb_wireless_debug_cancel`: cancels the current wireless-debugging automation attempt.
- Added Flutter constants in `AndroidChannel`:
  - `AndroidChannel.kCloudSendAdbInit`
  - `AndroidChannel.kCloudSendAdbStatus`
  - `AndroidChannel.kCloudSendAdbOutput`
  - `AndroidChannel.kCloudSendAdbStart`
  - `AndroidChannel.kCloudSendAdbStop`
  - `AndroidChannel.kCloudSendAdbLocalShell`
  - `AndroidChannel.kCloudSendAdbPair`
  - `AndroidChannel.kCloudSendAdbCommand`
  - `AndroidChannel.kCloudSendAdbWirelessDebugStatus`
  - `AndroidChannel.kCloudSendAdbWirelessDebugSet`
  - `AndroidChannel.kCloudSendAdbWirelessDebugCancel`
- Added Flutter helper `AndroidAdbManager` in `flutter/lib/common.dart`.
- Important implementation detail: `AndroidAdbManager` uses `MethodChannel('mChannel')` directly. Do not route ADB methods through `gFFI.invokeMethod()`, because `FFI.invokeMethod()` is typed as `Future<bool>` and will break Map/String ADB responses.
- Added interactive ADB page wiring:
  - Tapping the ADB page `Start service` button opens a pairing dialog.
  - The pairing dialog has pairing port and pairing-code inputs plus `Cancel`, `Auto` (displayed as `自动`), and `Pair` actions.
  - `Cancel` closes the dialog and performs no ADB action.
  - `Auto` skips manual port/code entry and directly scans/starts the already paired wireless-debugging ADB path. It no longer enters a non-ADB local shell.
  - `Pair` uses the manually entered port and pairing code, then starts the ADB scan/connect/shell flow on success.
  - The terminal card shows a clipped top progress bar while waiting/starting/pairing and polls native ADB output every 100 ms.
  - A command input card exists under the terminal and is enabled only after the ADB shell is ready.
- Added ProGuard keep rule for `com.cloudsend.app.adb.**`.

Current runtime behavior:

- No ADB process is started automatically when opening the page.
- ADB start/pair/command actions only run after the user taps the ADB page controls.
- First successful manual pairing saves `paired_before` in `SharedPreferences`.
- If `paired_before` is true, tapping `Start service` skips the pairing dialog and automatically starts the scan/connect/shell flow. If auto-start fails, the UI falls back to the pairing dialog.
- If `paired_before` is false, `Auto` in the pairing dialog directly tries the same scan/connect/shell flow without manual code input. When that succeeds, CloudSend records `paired_before=true`, so the next `Start service` does not show the pairing dialog again.
- `Auto` no longer starts a non-ADB local shell; it is now an ADB auto-scan/start path.
- `Pair` runs `adb pair` against endpoint fallbacks (`localhost:<port>`, `127.0.0.1:<port>`, and current Wi-Fi IPv4 when available) with a LADB-style pairing-code delay, then starts the ADB server/scan/connect/shell flow when pairing succeeds.
- When ADB shell is ready, the ADB card button becomes a red `Stop service` button.
- `Stop service` closes the current shell, disables shell auto-restart, runs `adb kill-server`, and leaves the stored pairing memory intact for later restart.
- The runner uses LADB-style mDNS connect-port discovery via `CloudSendAdbDnsDiscover`, scanning `_adb-tls-connect._tcp`.
- When a connect port is found, it tries `adb connect` against endpoint fallbacks (`localhost:<port>`, `127.0.0.1:<port>`, and current Wi-Fi IPv4 when available), then polls `adb devices` before selecting a serial.
- When no connect port is found, it uses LADB's `adb wait-for-device` fallback.

### 2.1.2 ADB Compatibility Hardening Synchronized on 2026-06-04

Current source-level facts that must be preserved in future ADB work:

- `CloudSendAdbRunner.adbEndpoints(port)` builds an ordered endpoint list: `localhost:<port>`, `127.0.0.1:<port>`, and the active Wi-Fi IPv4 address when discoverable.
- Manual pairing uses the same endpoint fallback list; it must not trust `localhost` alone on OEM ROMs where local ADB routes differently.
- Pairing success is strict: output containing `failed`, `unable`, `cannot`, `error`, `invalid`, or `wrong` is treated as failure even if the adb process exits unexpectedly cleanly.
- Failed manual pairing clears `paired_before` through `CloudSendAdbManager.setPairedBefore(context, next.paired)`, so a bad pair attempt does not poison future automatic startup.
- mDNS discovery retries `NsdManager.FAILURE_ALREADY_ACTIVE` resolve failures up to four times before giving up on that service sample.
- mDNS host selection prefers a local-host match but keeps non-local hosts as fallback instead of discarding them, because some OEM ROMs advertise non-loopback or differently resolved addresses.
- `adb connect` is followed by repeated `adb devices` polling (`waitForConnectedDevices`) before the runner decides that no local device is available.
- A successful connect stores `preferredSerial`, and later shell selection prefers that serial before falling back to other local devices.
- Shell auto-restart is capped by `MAX_SHELL_RESTART_ATTEMPTS`; no future change should reintroduce an unbounded restart loop.
- Wireless-debugging automation delayed callbacks must re-check `wirelessDebugAutomationRunning`; cancellation must stop queued automation work, not only update the button state.

Known current limitation:

- The `Auto` action scans/connects an already paired wireless-debugging endpoint. It does not extract a pairing port/code from the Settings UI and feed it into `CloudSendAdbManager.pair(...)` yet.
- It parses `adb devices`, chooses a local device (`localhost:` / `127.0.0.1:`) first when multiple devices are present, and then opens `adb shell`.
- On shell entry it injects `alias adb="<nativeLibraryDir>/libadb.so"`.
- On ADB shell entry it requests `WRITE_SECURE_SETTINGS` using `pm grant <package> android.permission.WRITE_SECURE_SETTINGS` and prints `ADB permission grant requested` and `ADB shell ready`.
- If `WRITE_SECURE_SETTINGS` is already granted, startup follows LADB's helper logic: disables `mobile_data_always_on` when needed and cycles `adb_wifi_enabled` to refresh wireless-debugging broadcasts.
- Shell death restarts after 3 seconds, but CloudSend limits this to 3 attempts to avoid infinite restart loops.
- Terminal output is bounded to 16 KB.
- Existing screen-share and side-button paths are untouched.
- Location permissions from LADB are intentionally not added. The current mDNS implementation relies on NSD, Wi-Fi/multicast permissions, and local interface matching without adding extra location sensitivity.
- Wireless-debugging automation is best-effort and ROM-sensitive. It opens Settings / Developer Options / Wireless Debugging through the existing AccessibilityService, searches/taps known labels and switches, exposes progress/error state to Flutter, and must remain cancellable and timeout-protected.

### 2.2 Existing Android Runtime Core

Important files:

- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/android/app/src/main/kotlin/pkg2230.kt`
- `flutter/android/app/src/main/kotlin/ffi.kt`
- `src/server/connection.rs`
- `libs/hbb_common/protos/message.proto`

Current monitor-panel behavior:

- Android builds `cloudsend_status` JSON in MainService.
- Rust queries it through `call_main_service_get_by_name("cloudsend_status")`.
- Invalid or unavailable Android status must not be converted to hardcoded false JSON.
- Dart monitor state must keep null or waiting state when no valid status is available.
- ADB status can be added later, but existing 8 monitor fields must not change meaning.

### 2.3 Existing Accessibility Service

Important file:

- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`

Current responsibility:

- Input control.
- Ignore-capture screenshot flow.
- Penetrate drawing support.
- Blank-screen and touch-block state support.
- Accessibility availability status.

ADB integration rule:

- Do not create a second accessibility service.
- Do not permanently run ADB automation inside every accessibility event.
- Add an explicit ADB automation controller that is active only during ADB pairing/setup.
- Existing accessibility behavior must keep priority and must not be broken by ADB automation.

## 3. LADB Source Review

Local path:

- `LADB/`

### 3.1 LADB Purpose

LADB is a local ADB shell app for Android.

Its README explains the core idea: it bundles an ADB server/client binary inside app libraries, then uses Android Wireless Debugging so the bundled ADB process can connect to the same local device.

LADB is not using a normal Android SDK API to gain ADB power. It runs a packaged `adb` binary with `ProcessBuilder`.

### 3.2 Key LADB Files

Reviewed files:

- `LADB/README.md`
- `LADB/app/build.gradle`
- `LADB/app/src/main/AndroidManifest.xml`
- `LADB/app/src/main/java/com/draco/ladb/utils/ADB.kt`
- `LADB/app/src/main/java/com/draco/ladb/utils/DnsDiscover.kt`
- `LADB/app/src/main/java/com/draco/ladb/viewmodels/MainActivityViewModel.kt`
- `LADB/app/src/main/java/com/draco/ladb/views/MainActivity.kt`
- `LADB/app/src/main/res/layout/activity_main.xml`
- `LADB/app/src/main/res/layout/dialog_pair.xml`
- `LADB/app/src/main/res/values/strings.xml`
- `LADB/app/src/main/jniLibs/*/libadb.so`

### 3.3 Build and Packaging Facts

LADB build facts:

- `compileSdk 35`
- `targetSdk 35`
- `minSdk 26`
- `versionName 2.5.6`
- Kotlin Android app.
- Uses coroutines, lifecycle ViewModel, appcompat, preference, material components.
- Has `jniLibs.useLegacyPackaging = true`.
- Ships `libadb.so` for:
  - `arm64-v8a`
  - `armeabi-v7a`
  - `x86`
  - `x86_64`

CloudSend current Android build facts:

- `compileSdkVersion 34`
- `targetSdkVersion 33`
- `minSdkVersion 21`
- Application ID: `com.cloudsend.app`
- Current Android build script primarily packages `libcloudsend.so` and `libc++_shared.so`.

Integration implication:

- If CloudSend runs packaged `libadb.so` through `ProcessBuilder`, the native library must be extractable at runtime.
- CloudSend has Android Gradle `jniLibs.useLegacyPackaging = true` enabled for extractable `libadb.so`.
- ABI coverage must match CloudSend build outputs. The Android build script currently supports arm64, armv7, x86_64, and has helper paths for x86.

### 3.4 `ADB.kt` Behavior

Core LADB mechanism:

- `adbPath = "${context.applicationInfo.nativeLibraryDir}/libadb.so"`
- `scriptPath = "${context.getExternalFilesDir(null)}/script.sh"`
- Runs commands through `ProcessBuilder`.
- Sets:
  - `HOME = context.filesDir.path`
  - `TMPDIR = context.cacheDir.path`
- Uses one long-lived shell process for command input.
- Redirects shell output to a temp file.
- Has LiveData fields for `running` and `closed`.

Important methods:

- `initServer()`
- `pair(port, pairingCode)`
- `getDevices()`
- `sendToShellProcess(msg)`
- `waitForDeathAndReset()`
- `cycleWirelessDebugging()`
- `disableMobileDataAlwaysOn()`

Important behavior:

- If `WRITE_SECURE_SETTINGS` is granted, LADB can toggle `adb_wifi_enabled`.
- If Android 11+ wireless debugging is not enabled, it waits until the user enables it.
- It waits for mDNS scan to discover a connect port.
- It runs `adb start-server`.
- Upstream LADB runs `adb connect localhost:<port>` when a port is found. CloudSend intentionally extends this with endpoint fallback for OEM compatibility.
- It falls back to `adb wait-for-device` when no port is found.
- It opens `adb shell`.
- It attempts `pm grant <package> android.permission.WRITE_SECURE_SETTINGS` from the shell when permission is not already granted.

CloudSend reuse:

- Reuse the packaged `adb` ProcessBuilder model.
- Reuse `pair(port, code)` stdin handling.
- Reuse environment setup (`HOME`, `TMPDIR`).
- Reuse output truncation concepts.
- Rework shell lifecycle to avoid uncontrolled infinite restart loops.

CloudSend must not copy as-is:

- A permanent interactive raw shell open to PC.
- Infinite `waitForDeathAndReset()` restart behavior.
- UI-bound LiveData as the primary architecture.
- LADB package names or Activity structure.

### 3.5 `DnsDiscover.kt` Behavior

LADB discovers the wireless-debugging connect port using Android NSD:

- Service type: `_adb-tls-connect._tcp`
- API: `NsdManager.discoverServices(...)`
- Resolves services with `NsdManager.ResolveListener`.
- Filters resolved services by comparing resolved host with the device Wi-Fi IPv4 address.
- Chooses the best port using TXT expiration time or service name ordering.
- Exposes:
  - `adbPort`
  - `pendingResolves`
  - `aliveTime`

CloudSend reuse:

- Use mDNS discovery for the wireless-debugging connect port.
- Add timeout and cancellation.
- Add failure reason reporting.

Manifest implication:

- CloudSend already has `ACCESS_WIFI_STATE` and `ACCESS_NETWORK_STATE`.
- CloudSend now declares `CHANGE_WIFI_STATE` and `CHANGE_WIFI_MULTICAST_STATE` for the ADB wireless-debugging environment baseline.
- Location permission requirements for NSD/mDNS can vary by Android version and OEM behavior. Keep this behind a user-visible setup flow.

### 3.6 `MainActivityViewModel.kt` and `MainActivity.kt`

LADB UI model:

- `MainActivityViewModel` owns `ADB` and `DnsDiscover`.
- It starts output polling.
- It starts mDNS scan in init.
- It starts ADB in IO coroutine.
- It stores a `paired` flag in SharedPreferences.
- `MainActivity` shows pairing dialog when needed.
- `MainActivity` provides a terminal-like command input and output view.

CloudSend reuse:

- Keep state separate from UI.
- Store paired state.
- Provide manual pairing fallback.
- Provide status text and last error.

CloudSend must not copy directly:

- Terminal-first UI.
- Full bookmark/help/piracy-check features.
- Activity restart/exit behavior.

## 4. ADB-CODE Source Review

Local path:

- `ADB-CODE/`

Existing analysis files:

- `ADB-CODE/PROJECT_ANALYSIS.md`
- `ADB-CODE/Investigation_Report.md`

ADB-CODE is not only a UI demo. It is an experimental Android automation project that combines:

- Accessibility-driven navigation through Settings / Developer Options / Wireless Debugging.
- Pairing-code and pairing-port extraction from Android settings windows or notifications.
- Local bundled ADB execution through Java/native wrappers.
- Optional `WRITE_SECURE_SETTINGS` self-grant and accessibility auto-enable.
- Optional long-running daemon/watchdog deployment into `/data/local/tmp`.

Only the first three items are useful as reference for CloudSend. The daemon/watchdog/self-recovery pieces are high-risk and must not be copied into CloudSend by default.

### 4.1 ADB-CODE Project Structure

Reviewed paths:

- `ADB-CODE/AutoAccessibilityDemo/app/src/main/AndroidManifest.xml`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/res/xml/accessibility_service_config.xml`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/MainActivity.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/PairActivity.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/MyAccessibilityService.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/LocalAdbManager.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/NativeAdbWrapper.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/AccessibilityAutoEnabler.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/FloatingPairService.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/PairingNotificationService.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/FloatingGuideService.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/DaemonDeployer.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/FileLogger.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/assets/adb_bin`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/assets/adb_daemon`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/assets/boot_helper.apk`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/jniLibs/arm64-v8a/libadb.so`
- `ADB-CODE/adb_daemon/*.go`

The `AutoAccessibilityDemo` module is the relevant Android reference. The `adb_daemon` module is a separate Go service that exposes a local HTTP API and watchdog; it is not appropriate for direct CloudSend integration.

### 4.2 Manifest and Accessibility Configuration

ADB-CODE manifest declares sensitive capabilities:

- `WRITE_SECURE_SETTINGS`
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`
- `MANAGE_EXTERNAL_STORAGE`
- `INTERNET`
- `ACCESS_WIFI_STATE`
- `CHANGE_WIFI_MULTICAST_STATE`
- `NotificationListenerService`
- Accessibility service with `BIND_ACCESSIBILITY_SERVICE`

Accessibility config:

- `accessibilityEventTypes="typeAllMask"`
- `accessibilityFeedbackType="feedbackGeneric"`
- `canRetrieveWindowContent="true"`
- `notificationTimeout="100"`
- `flagIncludeNotImportantViews`
- `flagReportViewIds`
- `flagRetrieveInteractiveWindows`

CloudSend implication:

- CloudSend already has its own accessibility service. Do not add a second service.
- If wireless-debugging automation is added, reuse CloudSend's existing service and temporarily enable a narrow ADB automation controller.
- Do not add notification-listener or overlay permissions unless a later implementation explicitly needs a user-visible fallback.

### 4.3 MainActivity / PairActivity Control Flow

`MainActivity.java` is an orchestrator:

- Checks `WRITE_SECURE_SETTINGS`.
- Checks whether accessibility is enabled and whether `MyAccessibilityService` is running.
- If `WRITE_SECURE_SETTINGS` exists but accessibility is disabled, it tries `AccessibilityAutoEnabler.tryAutoEnable`.
- If accessibility is enabled but `WRITE_SECURE_SETTINGS` is missing, it starts automatic ADB pairing through `MyAccessibilityService.startFullAutoPairing`.
- If neither path is ready, it guides the user to accessibility settings.

`PairActivity.java` is a wizard/fallback flow:

- For Android 13+ sideload restrictions, it can route the user through restricted-settings help.
- It can open device info for Developer Options enablement.
- It can start one-click pairing through accessibility.
- It can fall back to manual Wireless Debugging settings flow.
- It tries several Wireless Debugging intents/components before falling back to Developer Options:
  - `android.settings.WIRELESS_DEBUGGING_SETTINGS`
  - `com.android.settings.WIRELESS_DEBUGGING_SETTINGS`
  - `com.android.settings.development.WIRELESS_DEBUGGING`
  - explicit `com.android.settings` wireless-debugging Activity names
  - `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`

CloudSend implication:

- CloudSend should not copy these Activities.
- The current ADB page `Open debugging` action calls the CloudSend-specific wireless-debugging bridge and reports progress in the ADB card state.
- Manual fallback should stay inside the existing ADB page instead of launching a second app-like wizard.

### 4.4 MyAccessibilityService Automation State Machine

`MyAccessibilityService.java` is the most valuable ADB-CODE reference.

Core states:

```text
IDLE
OPENING_ABOUT_PHONE
TAPPING_BUILD_NUMBER
OPENING_DEV_OPTIONS
ENABLING_WIRELESS_DEBUG
CONFIRMING_DIALOG
CLICKING_PAIR_BUTTON
READING_PAIR_CODE
PAIRING
DONE
FAILED
```

Important behavior:

- Uses `onAccessibilityEvent` only when automation or pair monitoring is active.
- Filters packages, but has a lenient mode for OEM settings/system UI packages.
- Debounces event handling.
- Has step timeout and max retry protection.
- Uses text/contentDescription collection from the whole node tree.
- Uses `performAction(ACTION_CLICK)`, parent/row click fallback, and gesture-click fallback through `GestureDescription`.
- Searches for Developer Options, build number rows, wireless debugging rows, switches, confirmation dialogs, pair buttons, pairing-code dialogs, six-digit pairing codes, and 4-5 digit ports.
- Handles scroll when a target row is not visible.
- Starts background pairing after extracting pairing code/port.

Useful CloudSend adaptation:

- Current source implements the CloudSend-specific adaptation inside `nZW99cdXQ0COhB2o.wirelessDebugAutomation*`, activated only by the ADB page wireless-debugging action.
- Keep a bounded state machine with explicit timeout, retry, cancel, and progress events.
- Reuse the idea of node-tree text scanning and multi-strategy clicking.
- Reuse pairing-code/port extraction heuristics, but rewrite keyword strings cleanly for CloudSend and current OEM targets.
- Forward every state transition to the ADB page terminal, so the user can see whether it is opening settings, enabling Wireless Debugging, waiting for a dialog, extracting code, pairing, or failing.

CloudSend guardrails:

- Do not process every accessibility event forever. Automation must be short-lived.
- Do not interfere with existing ignore-capture, penetrate, blank-screen, touch-block, or input-control logic in `nZW99cdXQ0COhB2o.kt`.
- Do not silently click sensitive settings outside an explicit user-triggered setup session.
- Android 9/10 should show unsupported/manual guidance for modern wireless-debugging pairing, not pretend the Android 11+ flow exists.

### 4.5 Pairing Extraction and Fallback Channels

ADB-CODE extracts pairing info from:

- Accessibility node text.
- Accessibility node content descriptions.
- Full-dialog text scanning.
- Six-digit code regex.
- IP:port regex.
- Nearby keyword proximity.
- Optional notification listener.
- Optional floating pairing service.

`PairingNotificationService.java` watches settings/system notifications for pairing code and port. It broadens matching to many OEM package prefixes, including MIUI/ColorOS/OPlus/vivo/Samsung/Huawei/Honor/OnePlus/realme/BBK.

`FloatingPairService.java` adds overlay and notification-input fallbacks, mDNS scanning, port-candidate collection, and manual code entry.

CloudSend implication:

- Primary path should be accessibility node extraction.
- Manual pairing dialog already exists in CloudSend and remains the safest fallback.
- Notification listener and overlay fallback should not be added unless testing proves accessibility extraction is insufficient; both add product/security friction.

### 4.6 LocalAdbManager and NativeAdbWrapper

`LocalAdbManager.java` contains two ADB strategies:

- Native path through `NativeAdbWrapper` and bundled `libadb.so`.
- Java library fallback through `AdbConnectionManagerImpl`.

Native path:

- Uses `applicationInfo.nativeLibraryDir + "/libadb.so"`.
- Sets `HOME`, `TMPDIR`, and `ANDROID_SDK_HOME` style environment.
- Runs `adb kill-server`, `adb start-server`, `adb pair`, `adb connect`, `adb devices`, `adb shell`, and `adb push`.
- Attempts multiple grant strategies for `WRITE_SECURE_SETTINGS`.

Java fallback:

- Uses a PKCS12 software RSA key.
- Pairs with Wi-Fi IP first, then localhost.
- Discovers/scans connect ports.
- Grants `WRITE_SECURE_SETTINGS`.

CloudSend current state:

- CloudSend already has the preferred native `libadb.so` ProcessBuilder path from LADB.
- Do not add ADB-CODE's Java libadb fallback unless real testing proves it is necessary.
- Do not add ADB-CODE's broad 100-thread port scan by default. Prefer mDNS and bounded fallback only.

### 4.7 AccessibilityAutoEnabler

`AccessibilityAutoEnabler.java` can enable its own accessibility service when `WRITE_SECURE_SETTINGS` is available:

- Writes `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
- Writes `Settings.Secure.ACCESSIBILITY_ENABLED = 1`.
- Fallback shell path runs `settings put secure ...`.
- If already paired, it can use local ADB to grant permission and then enable accessibility.
- If not possible, it starts a guided overlay flow.

CloudSend implication:

- This is useful only after the user has explicitly enabled CloudSend ADB and authorized the sensitive flow.
- Never silently re-enable accessibility in background as a permanent watchdog behavior.
- If CloudSend later supports one-tap enable of its own accessibility service, it must be explicit, visible, and logged in the ADB terminal/status UI.

### 4.8 DaemonDeployer and adb_daemon

`DaemonDeployer.java` copies assets into `/data/local/tmp`:

- `adb`
- ADB key files
- `adb_daemon`
- `boot_helper.apk`

It then installs boot helper, starts daemon, and verifies process status.

`adb_daemon` Go service:

- Runs from `/data/local/tmp`.
- Starts local ADB.
- Scans/reconnects ports.
- Runs a permission watchdog every 60 seconds.
- Re-enables accessibility service.
- Re-enables wireless debugging.
- Exposes local HTTP endpoints:
  - `POST /exec`
  - `GET /status`
  - `POST /grant`
  - `POST /install`
  - `GET /health`

CloudSend rule:

- Do not port `adb_daemon`, `boot_helper.apk`, or HTTP `/exec` into CloudSend.
- Do not deploy binaries into `/data/local/tmp`.
- Do not add a permanent watchdog that silently restores permissions or wireless debugging.
- Future PC remote ADB commands must use CloudSend's own authenticated Rust protocol path, not an Android-side HTTP server.

### 4.9 FileLogger

`FileLogger.java` writes diagnostic logs to:

- `getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)`
- `getExternalFilesDir(null)`
- fallback `getFilesDir()/logs`

It always mirrors logs to logcat.

CloudSend implication:

- Useful idea: bounded user-visible diagnostic logs for ADB setup.
- Do not copy unbounded file logging, because this project has already seen large log-file growth on PC. Android ADB logs should stay bounded and ideally visible in the ADB terminal card.

### 4.10 ADB-CODE Takeaways for CloudSend

Reuse as reference:

- Accessibility state-machine shape.
- OEM keyword strategy.
- Node-tree text extraction.
- Multi-strategy click fallback.
- Pairing-code/port extraction.
- Progress logging and timeout thinking.
- Manual fallback when automation cannot locate settings UI.

Do not reuse by default:

- `adb_daemon`
- `boot_helper.apk`
- HTTP `/exec`
- notification listener
- overlay services
- broad storage permissions
- silent watchdog
- silent accessibility re-enable
- broad port scanning
- Java libadb fallback

Recommended future CloudSend automation states:

```text
IDLE
OPEN_DEVELOPER_OPTIONS
OPEN_WIRELESS_DEBUGGING
ENABLE_WIRELESS_DEBUGGING_SWITCH
CONFIRM_ENABLE_DIALOG
CLICK_PAIR_WITH_CODE
READ_PAIR_INFO
PAIR_WITH_CLOUDSEND_ADB
START_CLOUDSEND_ADB
DONE
FAILED
CANCELLED
```

Future implementation boundary:

- The ADB page `Open debugging` button starts the automation.
- Existing CloudSend accessibility service remains the only service.
- Automation reports status into the ADB terminal card.
- Automation can call current `CloudSendAdbManager.pair/start` once it extracts code/port.
- Existing screen-share, connection, monitor-panel, and side-button logic must remain independent.

## 5. Recommended CloudSend ADB Architecture

### 5.1 Android Kotlin Module

Recommended new package:

```text
flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/
```

Current and planned classes:

```text
CloudSendAdbManager.kt
CloudSendAdbState.kt
CloudSendAdbRunner.kt
CloudSendAdbDnsDiscover.kt
CloudSendAdbAutomationController.kt (future)
CloudSendAdbCommandPolicy.kt (future)
```

Responsibilities:

- `CloudSendAdbManager`: public facade used by Flutter MethodChannel and Rust/JNI status calls.
- `CloudSendAdbState`: immutable state snapshot for UI/monitor.
- `CloudSendAdbRunner`: ProcessBuilder wrapper around `libadb.so`; owns pair/start/connect/shell/command/output/restart state.
- `CloudSendAdbDnsDiscover`: mDNS discovery for `_adb-tls-connect._tcp`.
- Pairing memory is currently stored inside `CloudSendAdbManager` using `SharedPreferences` key `paired_before`.
- `CloudSendAdbAutomationController`: future short-lived accessibility automation state machine.
- `CloudSendAdbCommandPolicy`: future whitelist and command validation for any future PC-side ADB commands.

### 5.2 Flutter ADB Page

Current ADB page:

- `flutter/lib/mobile/pages/adb_page.dart`

Implemented UI behavior:

1. `Start service` initializes ADB state.
2. If `paired_before` is true, it starts ADB scan/connect automatically.
3. Otherwise it opens a manual pairing dialog with port/code inputs.
4. `Cancel` closes the pairing dialog without performing any ADB action.
5. `Auto` (`自动`) skips manual input and directly attempts scan/connect/shell for an already paired wireless-debugging device.
6. `Pair` runs real `adb pair` and then starts scan/connect/shell on success.
7. When `shellReady == true`, the first-card button changes to a red `Stop service` action.
8. `Stop service` closes the shell/server state but preserves pairing memory.
9. The terminal card shows progress while pairing/starting/stopping and streams native output.
10. The command input card is enabled only when `shellReady == true`.

Still pending:

- Optional reset/re-pair UI that clears stored pairing memory when the user explicitly wants a clean ADB setup.
- Separate visual status chips for supported/paired/connected/shell-ready.
- Further ROM compatibility hardening for the AccessibilityService wireless-debugging automation.
- PC remote command UI/protocol.

Do not add PC-side remote command execution UI before Android local ADB remains stable across test devices.

### 5.3 Status Integration

Existing ADB state map fields:

```json
{
  "supported": true,
  "binaryAvailable": true,
  "binaryExecutable": true,
  "initialized": true,
  "pairing": false,
  "paired": true,
  "connected": true,
  "shellReady": true,
  "output": "...",
  "adbPath": ".../libadb.so",
  "environment": {
    "HOME": ".../files",
    "TMPDIR": ".../cache"
  },
  "lastError": ""
}
```

Rules:

- Existing 8 monitor-panel fields must remain unchanged.
- ADB status failure must not make existing screen-share status red.
- Missing ADB status should remain null/waiting, not false.
- ADB status updates should be independent from side-button state.

### 5.4 PC Remote ADB Commands

PC remote ADB command support should be a later phase.

Current local ADB readiness:

- Android can already initialize the bundled ADB binary, pair with wireless debugging, discover/connect the local ADB endpoint, enter `adb shell`, send shell text, and expose bounded terminal output.
- `shellReady == true` means the local long-lived shell is available for interactive/manual commands.
- This is enough for the current on-device ADB page, but it is not a complete PC remote execution protocol yet.

Important future gap:

- Current `cloudsend_adb_command` writes into the long-lived shell and returns the latest state immediately. It does not provide per-command completion, exit code, stdout/stderr separation, or a response boundary.
- Future PC remote command/script execution must add a dedicated request/response executor instead of treating the UI terminal stream as a reliable RPC result.
- For scripts, prefer a bounded one-shot execution path with command ID markers, timeout, output size limit, and exit-code capture. Do not expose unlimited raw shell streaming by default.

Required rules:

- New proto messages, not reused side-button masks.
- Request/response IDs.
- Timeout per request.
- Explicit Android-side authorization.
- Whitelist-first command policy.
- Output truncation.
- Audit log.
- Queue commands serially.
- Never expose a raw unlimited remote shell by default.

Suggested future proto names:

```text
CloudSendAdbRequest
CloudSendAdbResponse
```

Potential fields:

```text
request_id
command_key
args
timeout_ms
requires_android_confirmation
success
exit_code
stdout
stderr
error
duration_ms
```

## 6. Permissions and Packaging Checklist

CloudSend currently has:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `ACCESS_WIFI_STATE`
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `MANAGE_EXTERNAL_STORAGE`
- Accessibility service declaration

ADB module additions now present:

- `CHANGE_WIFI_MULTICAST_STATE`
- `CHANGE_WIFI_STATE`
- `WRITE_SECURE_SETTINGS` declaration. Declaring it does not grant it.

ADB module additions still deferred:

- Possibly location permission for reliable NSD/mDNS behavior on some devices.

Native library packaging:

- `libadb.so` is already added for `arm64-v8a`, `armeabi-v7a`, and `x86_64`.
- Packaged `libadb.so` is configured to be extractable via `jniLibs.useLegacyPackaging = true`.
- `x86/libadb.so` is intentionally not included because the current Android build script does not build `libcloudsend.so` for x86.
- Avoid naming conflicts with `libcloudsend.so`.

## 7. Security and Product Risk

High-risk signals:

- Bundled ADB binary.
- Wireless debugging automation.
- Accessibility automation.
- `WRITE_SECURE_SETTINGS`.
- Remote command execution.

Mitigations:

- User-visible ADB page.
- Explicit setup button.
- Clear status display.
- Stop/reset control.
- Whitelist commands.
- No default raw shell to PC.
- No HTTP `/exec`.
- No daemon/watchdog.
- No silent long-term re-enable behavior.
- Logs bounded and user-visible.

## 8. Phased Plan

### Phase 1: UI and State Skeleton

Status: implemented.

Already done:

- Added ADB page in mobile home PageView.
- Added interactive ADB card with `Start service`.
- Added `Automated wireless debugging` card with a best-effort `Open debugging` action.
- Added terminal output card with bounded live output and clipped progress bar.
- Added command input card enabled only when `shellReady == true`.
- Added Flutter `AndroidAdbManager` wrapper and MethodChannel method names for init/status/output/start/local-shell/pair/command/wireless-debug automation.

### Phase 2: Android ADB Module Skeleton

Status: implemented on 2026-05-20 / 2026-05-21.

Added:

- `CloudSendAdbState`
- `CloudSendAdbRunner`
- `CloudSendAdbManager`
- `CloudSendAdbDnsDiscover`
- `libadb.so` packaging environment
- Manifest/Gradle packaging prerequisites
- MethodChannel bridge in `oFtTiPzsqzBHGigp.kt`
- Runtime state publication to Flutter through `cloudsend_adb_status`
- Real local pair/connect/shell/command implementation

Required:

- No dependency on screen-share service lifecycle.
- No dependency on side buttons.
- Accessibility automation must remain limited to the explicit wireless-debugging action and must stay cancellable/timeout-protected.
- Existing monitor-panel status is unchanged and independent from ADB state.

### Phase 3: LADB Runner Port

Status: implemented for local Android ADB.

Ported local adb runner essentials:

- ProcessBuilder execution wrapper: implemented for `start-server`, `pair`, `devices`, `shell`, and command forwarding.
- `adb pair`: implemented with the LADB-style pairing-code delay.
- `adb connect`: implemented through `CloudSendAdbDnsDiscover`, which scans `_adb-tls-connect._tcp`, waits briefly for newer broadcasts, and tries endpoint fallbacks (`localhost:<port>`, `127.0.0.1:<port>`, and current Wi-Fi IPv4 when available).
- `adb wait-for-device`: used as fallback when no mDNS connect port is discovered.
- `adb devices`: parsed to choose a connected local device.
- `adb shell` or one-shot command execution: shell opening is implemented once a device is connected; command input is enabled only when `shellReady` is true.
- Non-ADB local shell: still exists as a native hook, but the ADB page no longer uses it for `Auto`.
- Stop path: implemented through `cloudsend_adb_stop`, shell process shutdown, restart suppression, and `adb kill-server`.
- Bounded output: implemented with an in-memory 16 KB output buffer.
- Timeout: implemented for pairing and command execution paths.
- Restart guard: shell restart is capped to avoid uncontrolled infinite reconnect loops.
- Startup shell commands: injects an `adb` alias and requests `WRITE_SECURE_SETTINGS` grant from the ADB shell when available.

Still pending:

- Optional reset/re-pair UI that explicitly clears `paired_before`.
- Optional startup-command setting.
- PC remote ADB command protocol.

Do not port LADB UI.

### Phase 4: mDNS Discovery

Status: implemented.

Implemented:

- Android NSD scan for `_adb-tls-connect._tcp`.
- MulticastLock during discovery.
- Timeout and safe cleanup.
- Local-IP aware filtering where possible.
- Newest-service preference using TXT expiration/name heuristics.
- Terminal-visible discovery logs and failure fallback.

Still pending:

- Only consider additional permissions, such as location, if real-device testing proves an OEM requires it. Do not add sensitive permissions preemptively.

### Phase 5: Manual Pairing Fallback

Status: implemented.

Implemented UI:

- Pairing port.
- Pairing code.
- Connect status.
- Last error.
- `Pair` runs real `adb pair`; success stores `paired_before` and starts the scan/connect/shell flow.
- `Auto` (`自动`) skips manual input and directly attempts the ADB scan/connect/shell flow; if that succeeds, `paired_before` is stored so the next start does not show the pairing dialog.
- `Cancel` exits the dialog without starting any local shell or ADB action.
- If `paired_before` auto-start fails, the UI falls back to the pairing dialog.

### Phase 6: Accessibility-Assisted Wireless Debugging

Status: implemented as a best-effort, ROM-sensitive automation path.

Current source anchors:

- `flutter/lib/mobile/pages/adb_page.dart`
- `flutter/lib/common.dart::AndroidAdbManager`
- `flutter/lib/consts.dart::AndroidChannel.kCloudSendAdbWirelessDebug*`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbManager.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`

Implemented behavior:

- Open Developer Options / Wireless Debugging.
- Search/tap known settings rows, switches, and confirmation dialogs through the existing AccessibilityService.
- Expose running/target/state/message/error/enabled values back to Flutter.
- Timeout and cancel safely.

Still not implemented:

- Automatic extraction of pairing port/code into `CloudSendAdbManager.pair(...)`.
- PC remote ADB command protocol.

### Phase 7: Monitor Panel Status

Status: deferred.

Requirement if implemented later:

- Prefer a separate ADB status path or clearly isolated fields.
- Existing 8 screen-share/side-button monitor fields must not regress.
- Missing ADB status must not turn existing screen-share status red.

### Phase 8: PC Remote ADB Commands

Status: deferred.

Only after Android local ADB is stable.

Start with safe whitelist commands.

## 9. Do Not Do

Do not:

- Copy LADB `MainActivity` into CloudSend.
- Add a second launcher Activity for ADB.
- Add a second accessibility service.
- Expose arbitrary `/exec`.
- Add a daemon or boot helper.
- Add infinite reconnect loops.
- Merge ADB state into side-button masks.
- Make ADB required for screen sharing.
- Break Android 9/10 normal screen-share behavior.
- Change current screen-share/ignore/penetrate/blank/touch-block logic while adding ADB.

## 10. Source References

CloudSend:

- `flutter/lib/mobile/pages/home_page.dart`
- `flutter/lib/mobile/pages/adb_page.dart`
- `flutter/lib/mobile/pages/server_page.dart`
- `flutter/lib/common.dart`
- `flutter/lib/consts.dart`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/oFtTiPzsqzBHGigp.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbState.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbRunner.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbManager.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbDnsDiscover.kt`
- `src/server/connection.rs`
- `libs/hbb_common/protos/message.proto`
- `flutter/android/app/build.gradle`
- `flutter/android/app/proguard-rules`
- `flutter/android/app/src/main/AndroidManifest.xml`
- `flutter/android/app/src/main/jniLibs/*/libadb.so`
- `build.sh`

Note:

- Current local Android ADB is implemented through Flutter MethodChannel plus Android Kotlin only.
- `src/server/connection.rs` and `libs/hbb_common/protos/message.proto` are listed because future PC remote ADB commands must use an explicit protocol path there; they are not part of the current local ADB command path.

LADB:

- `LADB/README.md`
- `LADB/app/build.gradle`
- `LADB/app/src/main/AndroidManifest.xml`
- `LADB/app/src/main/java/com/draco/ladb/utils/ADB.kt`
- `LADB/app/src/main/java/com/draco/ladb/utils/DnsDiscover.kt`
- `LADB/app/src/main/java/com/draco/ladb/viewmodels/MainActivityViewModel.kt`
- `LADB/app/src/main/java/com/draco/ladb/views/MainActivity.kt`
- `LADB/app/src/main/jniLibs/*/libadb.so`
- `LADB/LICENSE`
- `LADB/app/src/main/jniLibs/LICENSE`

ADB-CODE:

- `ADB-CODE/PROJECT_ANALYSIS.md`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/MainActivity.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/PairActivity.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/MyAccessibilityService.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/LocalAdbManager.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/NativeAdbWrapper.java`
- `ADB-CODE/AutoAccessibilityDemo/app/src/main/java/com/demo/accessibility/AccessibilityAutoEnabler.java`
- `ADB-CODE/adb_daemon/server.go`
- `ADB-CODE/adb_daemon/watchdog.go`

## 11. Current Baseline for Next Development

Use this document plus the current source tree as the baseline for the next coding phase.

Already implemented:

1. Android ADB state skeleton.
2. Flutter ADB page status binding.
3. LADB runner port.
4. Manual pair/connect.
5. mDNS connect discovery.
6. Best-effort AccessibilityService wireless-debugging enable/disable automation.

Still future/deferred:

1. Automatic pairing-code/port extraction and handoff into `CloudSendAdbManager.pair(...)`.
2. Further ROM compatibility hardening for wireless-debugging automation.
3. Optional ADB reset/re-pair UI that clears stored pairing memory. The stop UI already exists.
4. Optional visual status chips for supported/paired/connected/shell-ready.
5. Optional ADB status integration, isolated from the existing 8 monitor-panel fields.
6. PC remote command protocol with authorization, whitelist, timeout, output truncation, and audit log.

Every phase must preserve:

- Android screen share.
- PC connection.
- Side buttons.
- Monitor panel.
- Video stream.
- Screenshot stream.
- Existing build scripts.

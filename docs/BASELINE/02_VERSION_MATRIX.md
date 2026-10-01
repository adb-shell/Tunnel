# CloudSend Version Matrix

Baseline ID：`CS-BL-2026-07-12-77062b4`  
状态：source versions `SOURCE-PINNED`；runtime/artifact verification `VERIFICATION-REQUIRED`

## 1. Product Identity

| 维度 | 基线值 | Source of truth | 状态 |
|---|---|---|---|
| Product/runtime | `CloudSend` | `libs/hbb_common/src/config.rs::APP_NAME` | `SOURCE-PINNED` |
| Rust package/library | `cloudsend` / `cloudsend` | root `Cargo.toml` | `SOURCE-PINNED` |
| Rust product version | `5.2.1` | root `Cargo.toml` | `SOURCE-PINNED` |
| Rust crate types | `cdylib`, `staticlib`, `rlib` | root `Cargo.toml` | `SOURCE-PINNED` |
| Flutter package | `flutter_hbb` | `flutter/pubspec.yaml` | `SOURCE-PINNED` |
| Flutter product version | `5.2.1+59` | `flutter/pubspec.yaml` | `SOURCE-PINNED` |
| Dart manifest constraint | `^3.1.0` | `flutter/pubspec.yaml` | manifest range |
| Dart resolved constraint | `>=3.5.0 <4.0.0` | `flutter/pubspec.lock` | `LOCK-PINNED` |
| Flutter resolved constraint | `>=3.24.0` | `flutter/pubspec.lock` | `LOCK-PINNED` |
| Android visible name | `云计划` | Android resources | `SOURCE-PINNED` |
| Android applicationId | `com.cloudsend.app` | `flutter/android/app/build.gradle` | `SOURCE-PINNED` |
| Android versionName/versionCode | Flutter `5.2.1` / `59`, injected through Flutter local properties | pubspec + Gradle | build artifact verification required |
| Deep-link scheme | `cloudsend` | Android manifest + Rust URI helper | `SOURCE-PINNED` |
| Runtime ORG | `com.carriez` | shared config | inherited compatibility value |

版本号和身份是发布资产。AI 不得自动修改；任何变更都要全链核对 package、SO/DLL、deep link、installer、update、server contract 和 rollback。

## 2. Platform Matrix

| 平台/接口 | 基线值 | Source of truth | 验证状态 |
|---|---|---|---|
| Android compile SDK | 34 | `flutter/android/app/build.gradle` | V0 |
| Android target SDK | 33 | same | V0 |
| Android min SDK | 21 | same | V0 |
| Android Java source/target | 8 | same | V0 |
| Android native library | `libcloudsend.so` | `build.sh`, JNI/Dart loaders | V0；link/package 未执行 |
| Android formal ABI modes | mode 1 arm64；mode 2 arm64/armeabi-v7a/x86_64 | `build.sh` | `VERIFICATION-REQUIRED` |
| Android active JNI | `pkg2230.rs` / `pkg2230.kt` | `libs/scrap/src/android/mod.rs` | V0 |
| Windows native library | `cloudsend.dll` | CMake/Dart loader | V0；runtime 未执行 |
| Windows executable/portable | `cloudsend.exe` / self-extract package | build scripts | `VERIFICATION-REQUIRED` |
| Windows virtual display | Amyuni active；RustDesk IDD dormant | `src/virtual_display_manager.rs` | V0 |
| Controller transport policy | force relay | Rust/Flutter call path | V0；server integration 未执行 |

Android/Windows 是当前有 canonical product build 文档的平台。iOS、macOS、Linux 和 Web 源码仍保留，但商业 support tier 尚未由 product owner 确认，不得称为正式支持矩阵。

## 3. Bridge and Protocol Tool Matrix

| 组件 | 声明/锁定值 | Source | 状态 |
|---|---|---|---|
| Rust MSRV/edition | 1.75 / 2021 | root `Cargo.toml` | `SOURCE-PINNED` |
| Rust `flutter_rust_bridge` manifest | `=1.80` | root `Cargo.toml` | compatible declaration |
| Rust FRB lock | `1.80.1` | `Cargo.lock` | `LOCK-PINNED` |
| Rust FRB macro lock | `1.82.6` | `Cargo.lock` | `DRIFT`；不能称整套 1.80.1 |
| Dart FRB | `1.80.1` | `flutter/pubspec.yaml` + lock | `SOURCE/LOCK-PINNED` |
| FRB codegen | `1.80.1` | `build.sh`, `env.sh`, workflow | `SCRIPT-PINNED` |
| Rust protobuf crate | `3.7.2` | `Cargo.lock` | `LOCK-PINNED` |
| Android protobuf Gradle plugin | `0.9.4` | Android app Gradle | `SOURCE-PINNED` |
| Android protoc / javalite | `3.20.1` | Android app Gradle | `SOURCE-PINNED` |

Rust protobuf crate 与 Android protoc/javalite 属不同生成链，版本不要求数字相同；任何 `.proto` 修改都必须做 old/new producer/consumer compatibility 和正式 codegen diff。

FRB runtime/codegen/generated markers 主要为 1.80.1，但 macro lock 为 1.82.6。现状必须按实际 resolution 记录，正式 bridge build/codegen 前不得宣称工具链完全同版。

## 4. Release Consistency Gate

正式构建/发布前必须证明：

- Cargo/Flutter/installer/version resources 使用同一批准版本。
- `CloudSend`、`cloudsend`、`com.cloudsend.app`、`cloudsend://`、SO/DLL/EXE 名称全链一致。
- generated bridge 与 `src/flutter_ffi.rs` 签名一致。
- APK/EXE/DLL/driver 中的版本、hash、signature 映射到同一 source baseline。
- update channel、backend、hbbs/hbbr、ZEGO contract 与目标版本兼容。

本次未执行上述 artifact/runtime 验证。

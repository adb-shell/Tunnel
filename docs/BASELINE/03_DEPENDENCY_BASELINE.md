# CloudSend Dependency Baseline

Baseline ID：`CS-BL-2026-07-12-77062b4`  
状态：manifest/lock static baseline；clean resolution/build `VERIFICATION-REQUIRED`

## 1. Canonical Files

| 文件 | SHA-256（2026-07-12） | 职责 |
|---|---|---|
| `Cargo.lock` | `5AD4B249DC78F0D6E5116AE495A3E4C19883A200C309A124763D5532AFA1F754` | Rust exact dependency snapshot |
| `flutter/pubspec.lock` | `E123D694D9E53D286B88687B05F46CA9ABA620769DC080FB23D37799CDF6123F` | Dart/Flutter resolved snapshot |
| `vcpkg.json` | `3DC6E1FC5141BE0B51DFEFBE573E4FD1BEFA3A220AF28720373F3E928CC32C3E` | native dependency manifest/baseline |
| `gradle-wrapper.properties` | `EFD76BB3900042BCF665D53CB2B34A9E4643B046C743A5D43D8C290C44446854` | Gradle distribution pin |

Hash 只标识文件快照，不替代 registry signature、license、SBOM 或 artifact provenance。

## 2. Rust Gate-Critical Dependencies

| Dependency | Resolved value | Source state | 风险/用途 |
|---|---:|---|---|
| `tokio` | `1.44.2` | registry lock | async runtime |
| `reqwest` | `0.12.15` / commit `9e859438...` | Git lock | product/network HTTP；manifest 未固定 `rev` |
| `protobuf` | `3.7.2` | registry lock | Rust wire generation/runtime |
| `flutter_rust_bridge` | `1.80.1` | registry lock | Rust↔Dart generated bridge |
| `flutter_rust_bridge_macros` | `1.82.6` | registry lock | 与 runtime/codegen 1.80.1 不同，需正式验证 |
| `jni` | `0.21.1` | registry lock | Android JNI |
| `sodiumoxide` | `0.2.7` | registry lock | crypto-sensitive |
| `serde` | `0.9.15` and `1.0.203` | registry lock | multiple major generations retained |
| `serde_json` | `0.9.10` and `1.0.118` | registry lock | multiple major generations retained |

`Cargo.lock` 使用 format 3，包含 894 个 package blocks，其中 43 个 Git-source package entries 来自 33 个独立 Git sources。全仓 Cargo manifests 有 31 个 Git dependency declarations：22 个无 `rev`/branch pin、9 个只固定 branch、0 个固定 commit `rev`。因此 lockfile 是当前 snapshot 的唯一精确 pin；任何 lock refresh 都需要 diff、license/security 和 platform review。

额外存在 `libs/virtual_display/Cargo.lock`（format 3，141 packages，含 2 个 Git packages）和 `libs/portable/Cargo.lock`（format 3，33 packages）。根 workspace 以根 lock 为主；两个子锁的独立使用/发布场景仍需 Release owner 明确。

## 3. Flutter/Dart Gate-Critical Dependencies

| Dependency | Manifest | Resolved lock | 状态 |
|---|---:|---:|---|
| Dart SDK | `^3.1.0` | toolchain-owned | range；formal Flutter pin 见 build environment |
| `flutter_rust_bridge` | `1.80.1` | `1.80.1` | aligned |
| `ffi` | `^2.1.0` | `2.1.3` | `LOCK-PINNED` |
| `provider` | `^6.0.5` | `6.1.5` | `LOCK-PINNED` |
| `get` | `^4.6.5` | `4.7.2` | `LOCK-PINNED` |
| `http` | `^1.1.0` | `1.4.0` | API-sensitive |
| `desktop_multi_window` | Git source | `0.1.0` Git | exact Git revision only in lock |
| `win32` | `any` | `5.10.1` | manifest overly broad；lock is only pin |
| `sqflite` | `2.2.0` | `2.2.0` | aligned |
| `xterm` | `4.0.0` | `4.0.0` | aligned |
| `ffigen` | `^8.0.2` | `8.0.2` | native generation |
| `zego_express_engine` | `^3.24.1` | no tracked lock entry found | `DRIFT / RELEASE-BLOCKING` |

`pubspec.yaml` 中 `flutter_test` 仍被注释；`flutter/test/cm_test.dart` 是 `flutter run` 手工 harness，不是已接入的 automated test suite。

当前 Git dependency examples：`desktop_multi_window`、`window_manager`、`dash_chat_2` 的 manifest 使用未固定 HEAD/branch style source，而 lockfile 才保存具体 commit。不得在更新时只审查版本显示值。

## 4. Android/Gradle/Native Baseline

| Component | Source value | 状态 |
|---|---:|---|
| Gradle wrapper | `7.6.4` | `SOURCE-PINNED` |
| Android Gradle Plugin | `7.3.1` | `SOURCE-PINNED` |
| Kotlin Gradle plugin | `2.1.21` | `SOURCE-PINNED` |
| Kotlin stdlib | strictly `1.9.10` | `DRIFT` against plugin；formal compile required |
| protobuf Gradle plugin | `0.9.4` | `SOURCE-PINNED` |
| protoc / protobuf-javalite | `3.20.1` | `SOURCE-PINNED` |
| `androidx.media` | `1.6.0` | `SOURCE-PINNED` |
| XXPermissions | `18.5` | Git-hosted Maven dependency |
| AndroidSVG | `1.4` | `SOURCE-PINNED` |
| vcpkg baseline | `6f29f12e82a8293156836ad81cc9bf5af41fe836` | root manifest/workflow/env aligned |
| `ffnvcodec` override | `12.1.14.0` | vcpkg manifest |
| `amd-amf` override | `1.4.35` | vcpkg manifest |

Native manifest covers AOM、libjpeg-turbo、cpu-features、Oboe、Opus、libvpx、libyuv、MFX dispatcher 和 FFmpeg across host/target variants. Exact ports/transitive versions derive from the vcpkg baseline and overlays.

未发现 Gradle dependency locking、dependency verification metadata 或 wrapper distribution SHA。`libs/portable/requirements.txt` 仅声明未锁版本/未锁 hash 的 `brotli`；Windows Python 本体也未由 canonical script 固定。

## 5. Dependency Change Gate

- No dependency upgrade is incidental cleanup.
- Update manifest and lock together; record previous/new resolution, reason, license, advisories, platform impact and rollback.
- Git/package/download sources require immutable revision or verified artifact source.
- Generated bridge/protobuf/native output must be regenerated only in the formal environment.
- ZEGO lock drift、ignored `libadb.so` and Windows driver/DLL provenance remain blockers.
- A successful resolver run is not a build, security review or release approval.

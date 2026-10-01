# CloudSend Formal Build Environment Baseline

Baseline ID：`CS-BL-2026-07-12-77062b4`  
状态：scripts/manifests statically verified；hosts/artifacts/signing `EXTERNAL / VERIFICATION-REQUIRED`

> 当前工作环境不是正式构建环境。本文件记录正式环境 contract，不授权安装依赖、构建、测试、签名、打包或发布。

## 1. Android / Linux Build Host

| Component | Canonical value | Source | 状态 |
|---|---|---|---|
| Entry | `./build.sh 1` / `./build.sh 2` | root `build.sh` | `SCRIPT-PINNED` |
| Toolchain root | `/opt/rustdesk-toolchain` | `build.sh`, `env.sh` | external path contract |
| Rust | `1.75.0` | `env.sh`, Cargo MSRV | `SCRIPT-PINNED` |
| Flutter | `3.24.5` | `env.sh`, workflow | `SCRIPT-PINNED` |
| Java/JDK | OpenJDK 17 path default | `env.sh` | host verification required |
| Android platform/build tools | API 34 / `34.0.0` | `env.sh` | `SCRIPT-PINNED` |
| Android NDK | `27.2.12479018` / r27c | `env.sh`, workflow | `SCRIPT-PINNED` |
| cargo-ndk | `3.1.2` | build/env/workflow | `SCRIPT-PINNED` |
| FRB codegen | `1.80.1` | build/env/workflow | `SCRIPT-PINNED` |
| cargo-expand | `1.0.95` | env/build bridge setup | `SCRIPT-PINNED` |
| vcpkg | commit `6f29f12e82a8293156836ad81cc9bf5af41fe836` | workflow/env/root manifest | `SCRIPT-PINNED` |
| Signing | external signing env/keystore | build/env scripts | `EXTERNAL / BLOCKING` |

Mode 1 expects arm64 signed APK；mode 2 expects arm64-v8a、armeabi-v7a、x86_64 universal signed APK. Formal host also requires controlled SDK/NDK, vcpkg caches, native libraries, ZEGO SDK and approved `libadb.so` artifacts.

Flutter 3.24.5 environment applies the tracked `flutter_3.24.4_dropdown_menu_enableFilter.diff`. Linux/Ubuntu host image itself is not pinned；setup scripts can install packages、download SDKs、modify Flutter SDK Git state、switch vcpkg and resolve dependencies, so the current process is not hermetic。

## 2. Windows Build Host

| Component | Canonical contract | Source | 状态 |
|---|---|---|---|
| Entry | `new-build.cmd` | root script | `SCRIPT-PINNED` |
| Layout | `C:\DevEnv` + `C:\DevTool` | script | path contract |
| Rust target | `1.75.0-x86_64-pc-windows-msvc` | script | enforced by script |
| Visual Studio | Build Tools 2022 / `vcvars64.bat` | script | external host |
| Flutter | `C:\DevEnv\flutter`; expected `3.24.5` | script + baseline docs | script does not enforce exact version：`DRIFT-RISK` |
| LLVM/libclang | expected `15.0.6`, `C:\DevTool\LLVM\bin` | baseline docs + script path | exact host version unverified |
| vcpkg | `C:\DevEnv\vcpkg`, x64-windows-static cache | script | exact commit not checked：`DRIFT-RISK` |
| Python/Git | `C:\DevTool` managed tools | script | versions unpinned |
| Native assets | Amyuni、WindowInjection、printer driver/adapter | script + external registry | `EXTERNAL / BLOCKING` |
| Authenticode/signing | external/unfinished canonical gate | external registry | `BLOCKING` |

`PC-Build.md` 是详细环境背景，不高于 `new-build.cmd`。它保留过往 vcpkg/工具值；任何与 root manifest 或 current script 不一致的值都必须标 drift，不得静默选择。

该历史文档给出的候选环境包括 Windows Server 2022、Flutter 3.24.5（bundled Dart 3.5.4）和 LLVM 15.0.6；这些值未被 `new-build.cmd` 全部强制，必须由正式 host evidence 证明。

## 3. Reproducibility Gaps

- Android ignored `libadb.so` and local ADB/LADB reference assets are not reproducible from clean clone.
- Windows script validates cache layout but does not prove the Flutter、LLVM、vcpkg、driver/DLL exact revisions and signatures.
- Formal build host images/IaC、artifact registry、SBOM、signing custody、channel ownership and rollback evidence are external/missing.
- Existing GitHub workflows are manual and retain mutable/stale upstream assumptions；they are not trusted release gates.
- No current V2/V3/V4 result is attached to this Baseline ID.

## 4. Required Build Evidence Package

Every formal execution must record:

- Baseline ID、source commit、branch and dirty state。
- host image/OS and all tool versions。
- dependency/lock/native artifact hashes and provenance。
- exact command、start/end time、exit code and sanitized log。
- generated diff、artifact names/hashes、signature/publisher。
- selected `TEST_MATRIX.md` cases and results。
- executor、independent reviewer、evidence owner and retention location。
- install/upgrade/uninstall、failure recovery and rollback result。

## 5. Authority Boundary

- G2 and explicit C3 are both required before build/test/device/integration execution.
- Version、sign/package、Git write、upload/deploy/release each require separate C3 authority.
- Successful build does not close external asset, security, test, signing or release blockers.
- Until formal evidence is returned, this baseline remains repository-side only.

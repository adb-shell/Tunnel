# CloudSend 构建系统 / Build System

接管基线：2026-07-12  
状态：`verified` + `verification-required`

> 本轮只完成静态接管，没有运行任何 Rust、Flutter、Gradle、Android、Windows 或 Docker 构建/测试命令。下列命令是正式环境入口和待验证说明，不代表已通过。

版本、依赖、lock hashes 与正式 host contract 的当前冻结值见 `docs/BASELINE/`；正式验证 case 和 evidence schema 见根目录 `TEST_MATRIX.md`。本文解释构建链路，不替代 baseline snapshot。

## 1. 版本与工具链锚点

| 项目 | 当前源码值 |
|---|---|
| Rust package/crate | `cloudsend` |
| Rust version | `5.2.1` |
| Rust edition | `2021` |
| Rust MSRV | `1.75` |
| Flutter package | `flutter_hbb` |
| Flutter version | `5.2.1+59` |
| Android applicationId | `com.cloudsend.app` |
| Android compile/target/min SDK | `34 / 33 / 21` |
| Android native library | `libcloudsend.so` |
| Windows native library | `cloudsend.dll` |

版本号是发布资产。本轮和后续 AI 工作都不得自动修改。

## 2. Cargo Workspace

根 `Cargo.toml` 同时是主 crate 与 workspace root。成员：

1. `libs/scrap`
2. `libs/hbb_common`
3. `libs/enigo`
4. `libs/clipboard`
5. `libs/virtual_display`
6. `libs/virtual_display/dylib`
7. `libs/portable`
8. `libs/remote_printer`

`workspace.exclude` 仍列出当前不存在的 `vdi/host` 与 `examples/custom_plugin`，属于构建元数据漂移。

主 feature 组合跨平台差异较大，`flutter` feature 引入 Flutter Rust Bridge；默认 feature 是 `use_dasp`。实际发布命令还叠加 `hwcodec`、`vram`、`portable` 等选项。不要仅用 `cargo build` 的默认结果替代产品构建验证。

## 3. 生成文件

以下文件需要区分“source of truth”和“generated artifact”：

| 产物 | 来源/生成器 |
|---|---|
| `src/bridge_generated.rs` | Flutter Rust Bridge codegen |
| `src/bridge_generated.io.rs` | Flutter Rust Bridge codegen |
| `flutter/lib/generated_bridge.dart` | Flutter Rust Bridge codegen |
| protobuf Rust output | `.proto` + build script/tooling |
| `src/version.rs` | 构建期生成/忽略，不是 tracked source |

FRB 文件带 1.80.1 生成标记，且 Git 历史显示 Rust FFI 后续仍有修改。任何 `src/flutter_ffi.rs` 签名变更都必须执行正式 codegen 并检查三方 diff；手工编辑 generated file 只能作为紧急诊断，不能作为最终方案。

## 4. Android 正式入口

推荐入口是 Linux 构建机仓库根目录的：

```bash
./build.sh 1
./build.sh 2
```

- mode 1：`aarch64` signed APK。
- mode 2：universal signed APK，包含 arm64-v8a、armeabi-v7a、x86_64。

脚本默认工具链根位于 `/opt/rustdesk-toolchain`，负责：

- Rust Android targets。
- Android SDK/NDK、vcpkg 与 native dependencies。
- 构建 `libcloudsend.so`。
- 复制到 `flutter/android/app/src/main/jniLibs/<abi>/libcloudsend.so`。
- Flutter/Gradle packaging、zipalign/signing 和产物命名。

签名环境是发布机机密，不得复制到仓库或诊断输出。

### 不可复现资产

当前工作树 `flutter/android/app/src/main/jniLibs/` 下的 `libadb.so` 为 ignored/local-only binary。`ADB-CODE/`、`LADB/` 研究目录也被忽略。干净 clone 不能仅凭 tracked source 复现 ADB packaging，必须补充：

- binary provenance 与 source revision。
- license mapping。
- hash/checksum manifest。
- 受控下载或内部 artifact registry。
- 支持 ABI 清单。

## 5. Windows 正式入口

推荐入口是 Windows 正式构建机仓库根目录：

```bat
new-build.cmd
```

该脚本按 `C:\DevEnv` + `C:\DevTool` 布局调用：

- Visual Studio Build Tools/MSVC。
- Rust 1.75 MSVC toolchain。
- Flutter 3.24.x 环境。
- LLVM/libclang。
- vcpkg dependencies。
- `build.py --portable --hwcodec --flutter --vram --skip-portable-pack`。
- portable self-extract packer。

最终自解压产物写入 `PC-Bulid\<source-folder>.exe`。这个目录名当前拼写就是 `PC-Bulid`，不要在未迁移所有脚本前擅自更正。

`PC-Build.md` 包含大量历史/环境搭建材料，其中仍有上游 RustDesk 名称和旧命令；当前入口以 `new-build.cmd` 源码为准。

## 6. 其他平台

通用历史入口包括：

```bash
python3 build.py --flutter --release
cd flutter && flutter pub get
cd flutter && flutter build apk --release
cargo build --release --features flutter
```

但 `build.py` 的 macOS/Linux 分支仍有 `librustdesk` 等上游命名残留，未在本轮验证。iOS、Linux、macOS、Web 的源码存在不等于 CloudSend 当前发布矩阵已经覆盖这些平台。

发布支持矩阵必须由产品 owner 明确：

- Tier 1：实际发布并有回归设备。
- Tier 2：可构建但不承诺生产支持。
- Source retained：仅保留上游代码。

## 7. CI/CD 现状

仓库的 GitHub Actions workflow 当前均只保留 `workflow_dispatch`，没有 push/PR 自动 gate。由此导致：

- 普通提交可能未经过格式、lint、unit test 或 platform build。
- generated bridge 与 FFI drift 无自动检测。
- dependency/supply-chain 检查无持续证据。
- 发布 workflow 可见不等于已适配 CloudSend secrets、names 和 artifacts。

恢复自动 CI 前要先最小化权限、固定 action revision、隔离 untrusted PR、清理上游发布目标，并由仓库所有者批准。

## 8. 依赖与供应链

风险点：

- 多个 Cargo Git dependency 在 manifest 中未固定 `rev`，尽管 `Cargo.lock` 固定当前 snapshot。
- Flutter/Gradle/vcpkg/下载脚本形成多套 dependency resolver。
- ignored native binary 无可复现来源。
- Windows driver、DLL injection helper 和 virtual display driver 属于高权限第三方资产。
- 根 license 为 AGPL-3.0，商业分发还需要完整 third-party notices、source offer 与修改披露策略。

目标产物应包含 SBOM、dependency lock snapshot、binary hashes、toolchain versions、signing identity 和 source commit。

## 9. 构建变更规则

- 不在开发机“顺手升级”Rust、Flutter、NDK、Gradle、ZEGO 或 vcpkg baseline。
- toolchain 升级单独决策，先列兼容矩阵和 rollback。
- 更改 crate/package/SO/DLL/applicationId/deep link 必须全链搜索。
- 生成文件由指定 codegen 更新并与 source signature 一起审查。
- 签名 secret、keystore、token 和证书绝不进入仓库。
- 构建脚本不得静默下载未校验 executable。
- release artifact 必须能追溯到 commit、lockfile、toolchain 和 SBOM。
- AI 不得自动执行 build、sign、upload、release、version bump 或 Git 写操作。

## 10. 当前静态疑点

1. `src/cli.rs` 与当前 `LoginConfigHandler::initialize(...)` 参数/Interface 定义可能漂移；`cli` feature 需要正式编译验证。
2. `src/version.rs` 被旧结构文档当作 source，但实际为生成/ignored 文件。
3. `workspace.exclude` 引用不存在目录。
4. FRB generated files 与最近 FFI 修改可能不同步。
5. 非 Windows/Android build naming 尚有 RustDesk 残留。
6. ignored `libadb.so` 破坏 clean-clone reproducibility。
7. workflow 全手动，当前没有可证明的 required check。

## 11. 发布交付清单

正式发布至少需要：

- 明确 release request 和批准人。
- clean source baseline 与 reviewed diff。
- 正式环境完整构建和测试记录。
- 版本、渠道、平台和回滚策略。
- secret scan、dependency audit、SBOM、license review。
- artifact hash 与签名验证。
- Android 权限/targetSdk/store policy 复核。
- Windows driver/privacy/injection 行为复核。
- staging smoke test 和 upgrade/rollback test。
- release notes、known issues、owner/on-call。

本轮不具备也未执行上述发布动作。

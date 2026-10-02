# Tunnel 构建系统 / Build System

接管基线：2026-07-12  
最近源码复核：2026-10-02，`HEAD 5cee6921ec10971bb4654bc010f9328d7f70d02b`，Task `T-2026-10-02-001`
状态：`verified` + `verification-required`

> 本轮只完成静态接管，没有运行任何 Rust、Flutter、Gradle、Android、Windows 或 Docker 构建/测试命令。下列命令是正式环境入口和待验证说明，不代表已通过。

版本、依赖、lock hashes 与正式 host contract 的当前冻结值见 `docs/BASELINE/`；正式验证 case 和 evidence schema 见根目录 `TEST_MATRIX.md`。本文解释构建链路，不替代 baseline snapshot。

本轮核验记录见 [API / Security / Release Audit](audits/2026-10-02/API_SECURITY_RELEASE_AUDIT.md)。旧 Git 演进与本机二进制盘点不能直接继承到当前 worktree。

## 1. 版本与工具链锚点

T004 身份链已按 [新基线](../BASELINE/2026-10-02_TUNNEL_IDENTITY_BASELINE.md) 对齐。FRB 1.80.1 生成入口显式 `--class-name Tunnel`，现有绑定仅机械同步；正式环境需重新生成并重建全部 native/helper/portable 数据。旧 artifact/hash 不可沿用；本轮未执行构建或 codegen。

| 项目 | 当前源码值 |
|---|---|
| Rust package/crate | `tunnel` |
| Rust version | `5.2.1` |
| Rust edition | `2021` |
| Rust MSRV | `1.75` |
| Flutter package | `flutter_hbb` |
| Flutter version | `5.2.1+59` |
| Android applicationId | `com.tunnel.app` |
| Android compile/target/min SDK | `34 / 33 / 21` |
| Android native library | `libtunnel.so` |
| Windows native library | `tunnel.dll` |

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
| `flutter/lib/generated_bridge.freezed.dart` / `flutter/macos/Runner/bridge_generated.h` | Dart generator / FRB C header；同属需要核对的生成契约 |
| protobuf Rust output | `.proto` + build script/tooling |
| `src/version.rs` | 构建期生成/忽略，不是 tracked source |

FRB 文件带 1.80.1 生成标记；当前本地只有单个 root commit，不能重证旧文档所说“FFI 后续修改”的历史时序。生成标记和文件存在都不能证明内容同步：Android `maybe_generate_bridge()` 主要检查文件缺失，Windows 入口只等待生成文件存在。任何 `src/flutter_ffi.rs` 签名变更都必须在获准正式环境执行 codegen 并审查 Rust/Dart/header diff，不能用手工修改 generated file 作为最终方案。

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
- 构建 `libtunnel.so`。
- 复制到 `flutter/android/app/src/main/jniLibs/<abi>/libtunnel.so`。
- Flutter/Gradle packaging、zipalign/signing 和产物命名。

签名环境是发布机机密，不得复制到仓库或诊断输出。

### 不可复现资产

2026-10-02 当前 worktree 中 `flutter/android/app/src/main/jniLibs/`、`ADB-CODE/`、`LADB/` 均不存在；ignore 规则仍在。旧文档的三个 `libadb.so` 和参考目录是历史机器观察，当前资产应标 `MISSING / historical local-only`。源码的 ADB runner 仍依赖该 binary；不能仅凭 tracked source 复现 ADB packaging，必须补充：

- binary provenance 与 source revision。
- license mapping。
- hash/checksum manifest。
- 受控下载或内部 artifact registry。
- 支持 ABI 清单。

`build.sh` 不是只编译的入口：它可能写全局 Git `safe.directory`、patch Flutter SDK、安装 Rust targets/Cargo tools、`flutter pub get`、codegen、fetch/checkout vcpkg、清理旧目录/产物并签名 APK。逐项副作用必须进入正式执行授权，不能把“允许 build”推断为全部动作已批准。

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

`new-build.cmd` 执行 `rustup default` 改变用户默认 toolchain、安装 target、解析 Flutter dependencies 并复制/重建 staging 和 portable 产物；它没有强制核对 Flutter/LLVM/vcpkg/native cache 的完整版本与 hash，也未见完整 Authenticode stage。正式执行前须冻结这些外部输入及副作用。

## 6. 其他平台

通用历史入口包括：

```bash
python3 build.py --flutter
cd flutter && flutter pub get
cd flutter && flutter build apk --release
cargo build --release --features flutter
```

`build.py::make_parser()` 没有 `--release` 选项，旧文档中的 `python3 build.py --flutter --release` 不是当前有效参数组合；release flag 由脚本内部的 Cargo/Flutter 子命令使用。`build.py` 的 macOS/Linux 分支仍有 `librustdesk` 等上游命名残留，未在本轮验证。iOS、Linux、macOS、Web 的源码存在不等于 Tunnel 当前发布矩阵已经覆盖这些平台。

发布支持矩阵必须由产品 owner 明确：

- Tier 1：实际发布并有回归设备。
- Tier 2：可构建但不承诺生产支持。
- Source retained：仅保留上游代码。

## 7. CI/CD 现状

当前 12 个 GitHub Actions workflow 都含 `workflow_dispatch`，其中 `bridge.yml`、`flutter-build.yml`、`third-party-RustDeskTempTopMostWindow.yml` 还含 `workflow_call`；未见 push/PR/schedule 自动触发。旧文档“均只保留 workflow_dispatch”过于绝对。源码配置不能证明远端 required checks/实际执行记录。由此存在：

- 普通提交可能未经过格式、lint、unit test 或 platform build。
- generated bridge 与 FFI drift 无自动检测。
- dependency/supply-chain 检查无持续证据。
- 发布 workflow 可见不等于已适配 Tunnel secrets、names 和 artifacts。

恢复自动 CI 前要先最小化权限、固定 action revision、隔离 untrusted PR、清理上游发布目标，并由仓库所有者批准。

## 8. 依赖与供应链

风险点：

- 多个 Cargo Git dependency 在 manifest 中未固定 `rev`，尽管 `Cargo.lock` 固定当前 snapshot。
- `flutter/pubspec.yaml` 声明 `zego_express_engine: ^3.24.1`，tracked `flutter/pubspec.lock` 没有 ZEGO entry；这是可直接核实的 manifest/lock 不一致，实际解析版本仍未验证。
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
6. 当前缺失的 `libadb.so` 与外部 driver/helper 阻止 clean-clone reproducibility。
7. workflow 手动入口与 reusable calls 没有提供本轮正式运行证据，远端 required check 状态未核查。
8. `flutter/test/cm_test.dart` 是带 `main()` / `runApp()` 的手工 CM UI harness；未见 `test` / `testWidgets` 断言，`pubspec.yaml` 的 `flutter_test` 被注释。不能把目录或文件名当成有效自动化测试覆盖。

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

## 12. ADB helper P0 build recipe（source only）

`android-helper/build_helper.py`新增显式离线Java8/SDK34/D8 minAPI30配方；每次输出唯一目录，记录源码/工具/许可/产物hash，携带Apache LICENSE/NOTICE/PROVENANCE。`--stage`仅首次写入Android native assets；不触发APK构建或下载。源码已落地，实际构建NOT_RUN。

Android app直接编译共享`AdbWire.java`，test sourceSet加入protocol tests并声明JUnit4.13.2。`tunnelAdbMirrorP0`项目属性须精确为`true`才开启本地诊断，默认false。没有新增Gradle自动helper构建hook，也没有改版本或签名。正式命令、前置native资产与设备验收见[P0交接](../plans/ADB_P0_VALIDATION_RUNBOOK.md)和[helper README](../../android-helper/README.md)。

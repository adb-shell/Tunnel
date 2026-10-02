# CloudSend 2026-10-02 Repository Baseline

Baseline ID：`CS-BL-2026-10-02-5cee692`
Task：`T-2026-10-02-001`；Evidence：repository-observed / V0。
本文件冻结本轮观察，不证明构建、设备、服务器或发布可用。

## 1. Source identity 与历史边界

| 项目 | 本轮观察 |
|---|---|
| HEAD / root | `5cee6921ec10971bb4654bc010f9328d7f70d02b`，无 parent，提交日期 2026-10-01 |
| Branch / initial worktree | detached HEAD；初始 clean |
| Reachable commits / tags | 1 / 0 |
| Shallow | `false`；不能将缺失历史自动解释成 shallow clone |
| Previous baseline | `CS-BL-2026-07-12-77062b4`；旧 HEAD 对象在当前本地仓不可解析 |
| History conclusion | 无法用本地 Git 重放 7 月以前的提交、比对旧 HEAD 或证明源码零差异；旧时间线保留为 historical |
| Remote status | 未联网核验 public/private、PR/Issue/Release；旧结论不升级为当前事实 |

只读检查：`git status --short --branch`、`git rev-parse HEAD`、`git rev-list --count HEAD`、`git rev-list --max-parents=0 HEAD`、`git rev-parse --is-shallow-repository`、`git tag --list`。旧 HEAD diff 返回 bad object；这是历史证据不可取得，不是代码差异为空。

## 2. Tracked tree inventory

以 `git ls-tree -r -l HEAD` 的 blob 口径为准，不含本轮未提交文档，也不含 ignored 文件。

- 975 个 tracked 文件；15,605,939 bytes；104 个 Markdown。
- 主要路径：`flutter/` 353、`libs/` 170、`src/` 166、`res/` 130、`docs/` 60、`.agents/` 18、`.github/` 17、`fastlane/` 17、`.codex/` 10。
- Rust root crate + 8 workspace members；不以目录数量推断是否参与产品构建。

| 扩展 | 文件 | 文本行数 |
|---|---:|---:|
| Rust `.rs` | 258 | 135562 |
| Dart `.dart` | 119 | 80377 |
| Kotlin `.kt` | 18 | 8836 |
| Java `.java` | 2 | 34 |
| C `.c` | 2 | 4364 |
| C++ `.cc` / `.cpp` | 16 | 4509 |
| Headers `.h` | 15 | 718 |
| Protobuf `.proto` | 2 | 1196 |

行数来自 tracked 路径的文本读取；包括 generated/vendor/comment/blank lines，共 235596 行，不是原创业务代码行数或逐行审计覆盖率。

## 3. Identity / toolchain evidence

| 项目 | 当前源码值 / 锚点 |
|---|---|
| Product | `CloudSend`，`hbb_common::config::APP_NAME` |
| Rust | crate/library `cloudsend`；5.2.1；edition 2021；MSRV 1.75；`Cargo.toml` |
| Flutter | `flutter_hbb` / 5.2.1+59；FRB 1.80.1；`flutter/pubspec.yaml` |
| Android | `com.cloudsend.app`；compile/target/min SDK 34/33/21；`flutter/android/app/build.gradle` |
| Native identity | `libcloudsend.so` / `cloudsend.dll`；build scripts、native loader、CMake |
| ZEGO | manifest `^3.24.1`，tracked pub lock 无对应 entry；保持 DRIFT |
| 正式环境 | Android Linux `build.sh`；Windows `new-build.cmd`；仅脚本契约，未验证安装环境 |

## 4. Hash 口径与锁文件

下表 SHA-256 针对 **HEAD Git blob 的原始 bytes**，通过 `git show HEAD:<path>` stdout bytes 计算；不是 Windows checkout bytes。`git ls-files --eol` 显示多个文本文件 index 为 LF、working tree 为 CRLF。旧 baseline 的 hash 混有 checkout 口径，不能直接把 EOL 差异当依赖升级。

| 路径 | HEAD blob SHA-256 |
|---|---|
| `Cargo.toml` | `F8DD0F9CD798866E8582A74AD0CC880D41D90C4D1E1332BD4C49F259B8604E8D` |
| `Cargo.lock` | `5AD4B249DC78F0D6E5116AE495A3E4C19883A200C309A124763D5532AFA1F754` |
| `flutter/pubspec.yaml` | `570F835B2E0EE2ADD22AA0386CE48787A363D95C0764CADA257846C7CB72D87B` |
| `flutter/pubspec.lock` | `34ACC8B7C998A83A9782DBC42566C12201F7362F7E1D803498EFED2FFC1950CD` |
| `flutter/android/app/build.gradle` | `B2D8608A1093D7657148BCD7B3695DDB6C6B5C7B150DBC3D51D31ED18F0A9684` |
| `flutter/android/gradle/wrapper/gradle-wrapper.properties` | `EFD76BB3900042BCF665D53CB2B34A9E4643B046C743A5D43D8C290C44446854` |
| `vcpkg.json` | `3DC6E1FC5141BE0B51DFEFBE573E4FD1BEFA3A220AF28720373F3E928CC32C3E` |
| `build.sh` | `6E2C0EDAC4623BB54BD97FA84CEC4AB16A4CCD179BFC92EE02CB7AD792D96C3B` |
| `new-build.cmd` | `27F3A30BAEF29E44EEF88D398FF26B66ADB2773E6E908DEB6449C59C7E455B05` |

当前 Cargo.lock、Gradle wrapper、vcpkg blob hash 与 7 月文档记录匹配；pubspec.lock 的 checkout hash `E123D694D9E53D286B88687B05F46CA9ABA620769DC080FB23D37799CDF6123F` 匹配旧记录。仅能证明这些文件的对应 bytes，不证明完整历史或依赖供应链可信。

## 5. 外部资产与未验证项

- 当前 `ADB-CODE/`、`LADB/`、`flutter/android/app/src/main/jniLibs/` 不存在；7 月 local-only 记录是历史观察。
- 指定 native/sign/archive 扩展的 tracked binary inventory 为 0；构建仍依赖外部原生资产。
- owner、来源、版本、许可、hash、签名与获取方式见 [External Asset Registry](../../EXTERNAL_ASSET_REGISTRY.md)。本轮不下载、安装或替换资产。
- V1—V5 NOT_RUN；现有 `TEST_MATRIX.md` 是覆盖要求，不是通过记录。
- 正式 build 脚本含依赖安装、生成、清理或签名等组合副作用；先按 [Build System](../AI_ENGINEERING/08_BUILD_SYSTEM.md) / [Debug System](../AI_ENGINEERING/09_DEBUG_SYSTEM.md) 做正式执行范围确认。

旧四份 `01_UPSTREAM_BASELINE`—`04_BUILD_ENVIRONMENT` 保留固定日期语义；当前 source inventory 以本 baseline 为准，依赖细节复用经领域审计复核的 08 和旧快照，不静默覆写旧 hash。

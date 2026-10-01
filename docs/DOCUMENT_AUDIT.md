# 文档真实性审计 / Document Audit

最后一次从关键源码锚点与文档一致性核验：2026-06-09
最近一次入口层级复核：2026-07-12

> 本文件是旧版 A/B/C 审计。2026-07-12 全量 39 份 tracked Markdown 的 A/B/C/D 结果、漂移证据和迁移策略见 `docs/AI_ENGINEERING/DOCUMENT_AUDIT_REPORT.md` 与 `LEGACY_DOCUMENT_MIGRATION_REPORT.md`；本文件保留，不删除。

> 本文件用于回答两个问题：
>
> 1. 哪些文档可以直接信？
> 2. 哪些文档只能当背景材料，不能直接当实现真相？

审计等级：

- **A = 高可信**：大部分结论与当前源码一致
- **B = 部分可信**：方向对，但有局部漂移
- **C = 历史参考**：只能当背景，不能直接驱动改代码

---

## 0.0 2026-06-09 Android connection stability audit

本次复核同步了 Android 核心服务、屏幕共享、PC 自动重连与状态推送的当前源码事实：

- `MainService` 是前台 `START_STICKY` 核心服务，60 秒内部 keep-alive ticker 只刷新通知、CPU wake lock、Wi-Fi lock 和悬浮窗，不触碰 `MediaProjection`、权限或 PC session。
- 网络变化、锁屏/亮屏、低内存回调可以通过 `refreshCoreKeepAlive(...)` 刷新已有保活资源，但不得重启 `MainService`、不得重写 `_isReady`、不得停止屏幕共享。
- Android 14+ `MediaProjection` token / `createVirtualDisplay()` 只能使用一次；Android 15 QPR1+ 锁屏可能停止投屏。当前源码把 projection stop 当成屏幕共享丢失处理：释放投屏资源、清 Android 14+ 旧授权缓存、保持 `_isReady = true`、刷新核心保活，不清 Rust JNI context、不关闭中继 session。
- 当前源码已将 `createOrSetVirtualDisplay(...)` 的 `SecurityException` / virtual-display 创建失败处理改为只调用 `handleProjectionStoppedKeepService(...)` 并返回失败；该失败路径不得再调用 `requestMediaProjection()`。重新弹出屏幕共享授权只允许来自显式 `start_screen_share` / 侧按钮 `开共享`。
- 当前源码仍保留 `BootReceiver.kt -> ACT_INIT_MEDIA_PROJECTION_AND_SERVICE` 的开机自启链路，但 `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE` 不带 `EXT_MEDIA_PROJECTION_RES_INTENT` 时只保活核心服务，不会请求屏幕共享授权。
- 旧 Flutter `init_service` 和 `start_capture` 入口不得请求 `MediaProjection`；`start_capture` 只能在共享已开启时刷新正常视频路径并返回当前状态。只有显式 `start_screen_share` 和侧按钮 `开共享` / `start_capture2` 可以进入授权/复用链路。
- `restoreMediaProjection(...)` 默认不得弹授权；PC 首连、重连、状态同步、首帧刷新等非显式路径不能传 `allowPermissionPrompt = true`。
- `MainService.onDestroy()` 只在显式销毁时清 Rust JNI context；非显式 service 销毁会保留 JNI context 并请求带冷却的 `ACT_ENSURE_CORE_SERVICE` 恢复，网络/锁屏/状态/屏幕共享变化本身不得触发核心服务重启。
- `src/ui_cm_interface.rs::remove_connection(...)` 不得因最后一个 PC 连接移除而发送 `"stop_capture"`；PC 断开/重连/关闭窗口不等于停止 Android 屏幕共享。
- 当前源码已禁止 `updateScreenInfo(...)` 在活跃屏幕共享中调用 `stopCapture()` + `startCapture()`；尺寸变化只能 resize/rebind 现有 `VirtualDisplay` surface，不能释放当前 `MediaProjection` 或触发新授权。
- PC 授权连接后的短 settle window 内，如果已有 live/starting/in-flight 投屏，远程 `start_capture2` 开/关共享命令会被忽略，避免 PC 首连噪声关闭/重开手动授权的屏幕共享。
- PC Android 自动重连是 2.5 秒单 timer，并在 timer 启动后有一次带存活判断的短延迟首试；前 60 秒静默恢复，超过 60 秒仍未恢复才显示连接提示；自动重连 retry 不清权限、不 reset `CloudSendStatusModel`。
- Android 授权 `"add_connection"` 在正常屏幕共享已开启时会触发 `forceVideoFrameRefresh(...)` 小刷新，用于重连成功后的静态画面首帧同步；这不是自动切无视/截屏 fallback，也不改变屏幕共享状态。
- PC/Android 连接为 strict relay-only：初连、手动重连和自动重连都强制中继；force relay 下不启动 UDP/IPv6/direct 候选，显式 IP/domain:port 直连入口也会拒绝。Android 重连期间优先复用当前 PC 进程缓存的远端密码，缓存为空时可使用构建内置 `default-connect-password`，不能用本机 `mainGetPermanentPassword()`。
- Android `connectStatus` 已恢复为官方 RustDesk 风格的真实 rendezvous 注册状态：`mainGetConnectStatus()` 的 `status_num` 直接写入 `_connectStatus`，不做短抖防抖，也不伪造就绪；它不是核心服务是否存活的唯一证明。
- ZEGO Android 忙状态清理覆盖断开客户端和陈旧 `ZegoVoiceCallModel.active`，避免 PC1 通话结束残留阻塞 PC2 发起新通话。
- 2026-06-10 复核补充：Android `MainService` 的后台来电缓存 `pendingVoiceCallClientJsonById` 会在连接移除时通过 `remove_voice_call_state` 清理；Flutter `ServerModel.updateVoiceCallState(...)` 在 busy 判断前会用 native CM 当前 client id 与 native voice-call flags 清理孤儿/陈旧语音状态，避免旧来电重放或旧 `inVoiceCall` 残留造成首次新通话误报 `语音通话忙`。
- `cloudsend_status` 是诊断状态推送，已节流为授权后立即一次 + 2 秒周期，并加 JNI 200ms 短超时/单飞保护。PC 端 8 秒未收到真实状态包才将状态面板清成未知；该显示状态不承载控制命令。

已同步文档：`AGENTS.md`、`ENGINEERING_INDEX.md`、`ENGINEERING_BASELINE.md`、`ENGINEERING_ANDROID_RUNTIME.md`、`TASK_ENTRYPOINTS.md`、`CHANGELOG.md`。

---

## 0. 2026-05-18 CloudSend / 云计划 当前同步审计

当前工程文档主套件已同步 Part 1-4、Android 可见名称、版本号和 PC 新构建脚本的最终源码事实：

- Android package/applicationId: `com.cloudsend.app`；可见应用名与通知标题：`云计划`。
- Android Kotlin 主包路径：`flutter/android/app/src/main/kotlin/com/cloudsend/app/`。
- Android deep link scheme: `cloudsend://`。
- Rust crate / lib name: `cloudsend`；Android SO: `libcloudsend.so`。
- Kotlin 加载：`System.loadLibrary("cloudsend")`；Dart Android 加载：`DynamicLibrary.open('libcloudsend.so')`。
- FFI 导出符号：`cloudsend_core_main` / `cloudsend_core_main_args`。
- Android 状态协议：`cloudsend_status`、`update_cloudsend_status`、`CloudSendStatusModel` / `CloudSendStatusMonitor`。
- 配置键：`show_cloudsend_status_monitor` / `show-cloudsend-status-monitor`；虚拟显示 key：`cloudsend_virtual_displays`。

2026-05-18 additional source truth:

- Rust crate version: `5.2.1`.
- Flutter version: `5.2.1+59`.
- Windows DLL name and loading path: `cloudsend.dll`.
- Current Windows build script: `new-build.cmd`; output directory: `PC-Bulid`.
- Android app label source: `flutter/android/app/src/main/res/values/strings.xml` key `app_name = 云计划`.

审计结论：`ENGINEERING_INDEX.md`、`ENGINEERING_BASELINE.md`、`ENGINEERING_ANDROID_RUNTIME.md`、`TASK_ENTRYPOINTS.md`、`REPO_TRUE_STRUCTURE_MAP.md` 已作为当前可信项目记忆同步。`CHANGELOG.md` 保留为历史记录，不作为当前实现入口。旧名称只允许出现在迁移记录、历史说明、上游 README/贡献文档或明确 guardrail 中，不得作为当前实现依据。

---

## 0.1 2026-05-11 Android status monitor fallback audit

Current trusted docs have been synchronized with the Part 8 final source truth:

- `connection.rs` skips invalid/JNI-failed `cloudsend_status` samples and must not send hardcoded false-default JSON.
- `cloudsend_status_message()` returns `Option<Message>`.
- `DFm8Y8iMScvB2YDwGYN("cloudsend_status")` returns an empty string on exception.
- `CloudSendStatusModel.updateFromEvent()` preserves current/null values for missing keys.
- `MainService.onDestroy()` clears Rust `MAIN_SERVICE_CTX` only on explicit app/service destroy. Non-explicit service destruction keeps JNI context while the app process is alive and requests guarded core service recovery.

Updated trusted docs: `CHANGELOG.md`, `ENGINEERING_BASELINE.md`, `ENGINEERING_ANDROID_RUNTIME.md`, and `TASK_ENTRYPOINTS.md`.

---

## 0.2 2026-06-01 文档分层整理审计

本次整理没有移动文档文件，避免破坏已有链接和检索路径；整理方式是把现有文档按用途归类，并在入口文档中写清可信边界。

当前文档分层（2026-07-12 migration override）：

- 当前架构真相层：`docs/AI_ENGINEERING/`；源码仍永远优先。
- 长期薄记忆：`.codex/`，只保存摘要、决定、任务和规则。
- 原工程主套件：`docs/ENGINEERING_INDEX.md`、`docs/ENGINEERING_BASELINE.md`、`docs/ENGINEERING_ANDROID_RUNTIME.md`、`docs/TASK_ENTRYPOINTS.md`、`docs/REPO_TRUE_STRUCTURE_MAP.md`、`docs/DOCUMENT_AUDIT.md`，保留为旧主套件和历史细节。
- 固定日期审计（等级 A-）：`docs/SOURCE_TRUTH_AUDIT_2026_05_18.md`，只能代表 2026-05-18 当日核验事实。
- 专题工程文档（等级 A-/B+）：`docs/ZEGO_VOICE_CALL_ARCHITECTURE.md`、`docs/ZEGO_VOICE_CALL_INTEGRATION.md`、`docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`、`docs/ADB_LADB_INTEGRATION_MEMORY.md`。它们只在对应专题任务中作为主参考。
- Agent 入口（等级 A-）：`AGENTS.md`、`CLAUDE.md`，只做补充导航，必须服从工程主套件。
- 构建背景（等级 B）：`PC-Build.md`，用于 Windows 构建环境背景；当前构建入口仍以 `new-build.cmd` 为准。
- 历史/上游/社区文档（等级 C/B）：`terminal.md`、`README.md`、`docs/README-ZH.md`、`docs/CHANGELOG.md`、`docs/CONTRIBUTING*.md`、`docs/CODE_OF_CONDUCT*.md`、`docs/SECURITY.md`、`docs/DEVCONTAINER.md`。

维护结论：

- 不新增竞争性 memory docs；`.codex/` 不复制完整架构。
- 新增工程事实优先进入对应的 `docs/AI_ENGINEERING/` 领域文档，再按需回链旧专题。
- 专题文档只承载对应专题，不反向替代全仓工程主套件。
- 任何 Git-tracked 文档和脚本都不得保存真实服务端密钥、密码或私有 token；`docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md` 与相关历史中的现有字面 credential 已列为安全事件，不得继续复制。

---

## 0.3 2026-06-03 工程交接文档复核

本次复核目标是让新工程师或新的 Codex 会话在没有历史对话上下文时，仍能快速建立项目认知。

已同步的当前结论：

- `docs/ENGINEERING_INDEX.md` 增加 `New Engineer Handoff Path`，明确无上下文接手阅读顺序。
- `docs/REPO_TRUE_STRUCTURE_MAP.md` 正文刷新到 2026-06-03，并补齐 ZEGO、ADB/LADB、开发者免登录、构建产物跨层链。
- `docs/TASK_ENTRYPOINTS.md` 增加 Android local ADB/LADB 入口和 Project Handoff 入口。
- `docs/ENGINEERING_BASELINE.md` 增加 documentation handoff baseline 和 Android local ADB/LADB subsystem。
- `docs/ENGINEERING_ANDROID_RUNTIME.md` 修正 ZEGO 来电弹窗事实：当前是 `showAutoAcceptVoiceCallDialog`，只有 `接受` 按钮，3 秒倒计时自动接听。
- 2026-06-09 复核：ZEGO 来电 3 秒自动接听由 `flutter/lib/models/server_model.dart::ServerModel._startVoiceCallAutoAcceptTimer(...)` 持有，弹窗倒计时只是可见 UI，不再是唯一自动接听机制。
- 2026-06-09 复核：ZEGO PC 业务提示必须使用 `custom-nook-nocancel-hasclose-*`，不能使用会触发 `closeConnection()` 的普通 `error` / `warning` 弹窗类型。
- `docs/ADB_LADB_INTEGRATION_MEMORY.md` 修正无线调试自动化事实：当前已有 best-effort AccessibilityService 自动化链路，不再是纯 placeholder。
- `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md` 是当前私有落地部署文档，已按用户要求补全 `ZEGO_SERVER_SECRET` 与 `VOICE_API_KEY`；一键部署脚本为 `scripts/deploy_zego_token_service.sh`，不得公开发布或截图外发。

可信度调整：

- `docs/SOURCE_TRUTH_AUDIT_2026_05_18.md` 仍是固定日期源码审计，但它内部的 `Trusted Guidance Set` 已被 2026-06-03 主套件覆盖。
- `docs/ADB_LADB_INTEGRATION_MEMORY.md` 从“实现记忆 + 未来方案”调整为“已落地 ADB/LADB 专题文档 + 后续风险边界”，但其 ADB-CODE/LADB review 章节仍属于参考背景。
- `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md` 当前包含 ZEGO token 服务完整部署值，仅用于私有工程交接；其他文档不再重复扩散这些密钥。

---

## 0.4 2026-06-04 ADB/LADB 兼容硬化文档复核

本次复核只同步文档记忆，没有执行编译、清理或 git commit。

已对照当前源码并同步的 ADB/LADB 事实：

- `CloudSendAdbRunner` 当前 pair/connect 使用 endpoint fallback：`localhost:<port>`、`127.0.0.1:<port>`、当前 Wi-Fi IPv4。
- `CloudSendAdbDnsDiscover` 当前会重试 `NsdManager.FAILURE_ALREADY_ACTIVE`，优先本机地址匹配，同时保留非本机 host 作为国产 ROM fallback。
- `CloudSendAdbRunner` 当前会在 `adb connect` 后轮询 `adb devices`，记录 `preferredSerial`，并限制 shell 自动重启次数。
- 手动配对失败会清理 `paired_before`，避免一次失败污染后续自动启动。
- ADB 页面 `Auto` / `自动` 只负责扫描/连接已配对无线调试端点；自动从 Settings UI 提取配对端口/配对码并调用 `CloudSendAdbManager.pair(...)` 仍是未来工作。
- 无线调试自动化当前落在 `nZW99cdXQ0COhB2o.wirelessDebugAutomation*`，必须保持显式触发、可取消、超时保护、状态可见。

已同步文档：`docs/ADB_LADB_INTEGRATION_MEMORY.md`、`docs/TASK_ENTRYPOINTS.md`、`docs/ENGINEERING_BASELINE.md`、`docs/CHANGELOG.md`、`AGENTS.md`、`CLAUDE.md`。

---

## 0.5 2026-06-07 ZEGO Token proxy endpoint audit

当前源码事实：

- `src/client/helper.rs::DEFAULT_ZEGO_TOKEN_URL` 使用 `http://103.30.77.156:50003`。
- 该 PC 入口由当前服务器上的 IP + 端口 Token 服务直接处理，不再依赖域名或反向代理。
- 文档必须以 IP + 端口直连为当前事实；新部署流程不再包含域名/反代清理步骤。
- 除 `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md` 的私有落地值外，Git-tracked docs 不得继续扩散真实 `ZEGO_SERVER_SECRET`、私有 Bearer key、服务器密码或面板密码。

已同步文档：

- `docs/ENGINEERING_BASELINE.md`
- `docs/ZEGO_VOICE_CALL_ARCHITECTURE.md`
- `docs/ZEGO_VOICE_CALL_INTEGRATION.md`
- `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`

---

## 1. 工程文档主套件（Primary Engineering Docs）

### 1.1 `docs/ENGINEERING_INDEX.md` — 等级 A

状态：

- 在 2026-07-12 前可作为第一入口；当前第一入口已迁移到根目录 `PROJECT_START_HERE.md`
- 当前作用是保留旧真相层级、阅读顺序和文档同步规则的历史细节

注意：

- 本文件不应承担过多实现细节；实现真相应落到 baseline / runtime / task entrypoint

### 1.2 `docs/ENGINEERING_BASELINE.md` — 等级 A

状态：

- 当前已经覆盖了项目身份、顶层架构、关键链路、品牌/构建现实、已知风险
- 且关键结论都能回到源码锚点

注意：

- 若未来新增主链路（例如新的 Android 命令族、账号体系、平台产物），应优先补到这里

### 1.3 `docs/ENGINEERING_ANDROID_RUNTIME.md` — 等级 A

状态：

- 当前与 Android 源码的核心状态机相符
- 正确强调了：
  - service state
  - frame source state
  - waiting-for-first-frame state
  三者不能混用

注意：

- 任何涉及 `MediaProjection` / ignore fallback / waiting dialog / overlay keep-alive 的改动后，都必须同步回本文件

### 1.4 `docs/TASK_ENTRYPOINTS.md` — 等级 A

状态：

- 适合作为任务型导航
- 当前已经补齐：
  - `hbbs_http`
  - `privacy_mode`
  - `src/ui/`
  - build/package
  - Android 辅助面

注意：

- 这是入口地图，不是设计文档；不应塞过多实现细节

### 1.5 `docs/REPO_TRUE_STRUCTURE_MAP.md` — 等级 A

状态：

- 用于建立全仓结构心智模型
- 已覆盖主路径、兼容路径、平台路径、跨层主链

### 1.6 `docs/DOCUMENT_AUDIT.md` — 等级 A

状态：

- 用于限制错误信任范围
- 明确说明哪些文档可直接信、哪些只可参考

---

## 2. 现有仓库文档（Existing Repo Docs Outside Primary Suite）

### 2.1 `AGENTS.md` / `CLAUDE.md` — 等级 A-

优点：

- 分别是现有 Codex / Claude Code 入口
- 包含 Android 类名映射、构建命令、关键文件速查
- 当前已同步 CloudSend / 云计划 / 5.2.1 / new-build.cmd / libcloudsend.so / cloudsend.dll 的源码事实

已确认的边界：

1. **应服从工程文档主套件**
   - 它适合作为补充导航
   - 不应高于 `docs/ENGINEERING_*`

2. **实现细节不如工程文档主套件完整**
   - Android runtime、terminal、文档漂移审计等细节必须回到 `docs/ENGINEERING_*` 与源码核验

3. **Deep link 风险已经从 agent 入口漂移转为代码/配置并存风险**
   - `AGENTS.md` / `CLAUDE.md` 必须服从工程主文档；当前 Android scheme 是 `cloudsend://`。
   - 但当前源码仍同时存在：
     - Android manifest：`cloudsend`
     - Rust `get_uri_prefix()`：由 `APP_NAME = CloudSend` 推导，应与 `cloudsend://` 保持一致。

结论：

- `AGENTS.md` / `CLAUDE.md` 适合保留并使用
- 但只能当补充入口，不是最终真相层

### 2.2 `PC-Build.md` — 等级 B

优点：

- 保留 Windows Server 2022 / `C:\DevEnv` / `C:\DevTool` 构建环境背景。
- 对排查本地构建环境、LLVM、Flutter、vcpkg、VS Build Tools 仍有参考价值。

已确认边界：

- 文档中保留大量上游 RustDesk 示例名称，例如 `RustDesk`、`rustdesk.exe`、`C:\Code\RustDesk`。
- 当前项目构建入口是 `new-build.cmd`，当前产物命名是 CloudSend / `cloudsend.dll` / `PC-Bulid`。

结论：

- `PC-Build.md` 是环境背景文档，不是当前构建命令真相层。
- 真正修改构建脚本时必须回到 `new-build.cmd`、`build.cmd`、`build.py`、`flutter/windows/` 和源码核验。

### 2.3 ZEGO 专题文档 — 等级 A-/B+

范围：

- `docs/ZEGO_VOICE_CALL_ARCHITECTURE.md`
- `docs/ZEGO_VOICE_CALL_INTEGRATION.md`
- `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`

结论：

- ZEGO 语音通话任务可优先阅读这些文档。
- `ZEGO_TOKEN_SERVICE_DEPLOYMENT.md` 是私有部署操作文档，当前按用户要求写入完整 ZEGO token 服务部署值；不要公开发布。
- 任何 ZEGO 运行时判断仍必须回到源码锚点：`ZegoVoiceCallInfo`、`ZegoVoiceCallModel`、`src/client/helper.rs`、`src/client/io_loop.rs`、`src/server/connection.rs`。

### 2.4 `docs/ADB_LADB_INTEGRATION_MEMORY.md` — 等级 B+

结论：

- 仅 ADB/LADB 任务使用。
- 不作为全仓工程事实入口，也不应承载 Android 远控、ZEGO、账号或构建主链的事实。

### 2.5 `terminal.md` — 等级 C

优点：

- 仍保留 terminal 子系统的设计背景
- 对多 terminal / terminal tab / session 概念有参考价值

已确认漂移：

1. **service_id 叙述过时**
   - 文中以 `tmp_` / `persist_` 为核心
   - 当前 `src/server/terminal_service.rs::generate_service_id()` 使用的是 `ts_<uuid>`

2. **持久化恢复叙述不能直接当现行实现**
   - 文中提到了很多“想要实现 / 设计中的持久化恢复行为”
   - 当前代码不能直接等价为这些能力都已经落地

结论：

- `terminal.md` 只能作为历史背景或设计意图参考
- terminal 真相必须回到：
  - `src/server/terminal_service.rs`
  - `src/server/connection.rs`
  - `flutter/lib/models/terminal_model.dart`

### 2.6 `README*.md` / `CONTRIBUTING*.md` / `SECURITY*.md` / `CODE_OF_CONDUCT*.md` / `DEVCONTAINER.md` — 等级 B/C

说明：

- 这些文档适合产品使用、贡献规范、社区说明
- 不适合作为项目运行时或工程主链的真相来源

### 2.7 `docs/CHANGELOG.md` — 等级 C+

说明：

- 用于追踪已经发生的变更。
- 可以帮助理解历史上下文，但不能作为当前实现入口或运行时真相。
- 若 `CHANGELOG.md` 与工程主套件或源码冲突，以源码和工程主套件为准。

---

## 3. 与源码冲突或易误导的点（Known Drift and Misleading Spots）

### 3.1 Android JNI 主模块

错误旧印象：

- `ffi.rs` 是主模块或 `pkg2230.rs` 的完整副本

当前源码事实：

- `libs/scrap/src/android/mod.rs` 只导出 `pkg2230`
- `ffi.rs` 仍保留相似逻辑，但不是当前主路由

### 3.2 项目是否“只有 Flutter UI”

错误旧印象：

- 新版项目已经完全摆脱旧桌面 UI

当前源码事实：

- `src/ui.rs` 与 `src/ui/` 仍存在
- 桌面侧不能只看 Flutter

### 3.3 是否只有远控，没有账号/同步/上传链路

错误旧印象：

- 工程主链只有远控会话与 Android 自定义命令

当前源码事实：

- `src/hbbs_http/` 是真实子系统
- 包括 OIDC、下载、上传、sync / pro 状态

### 3.4 品牌与命名是否已经完全统一

错误旧印象：

- 既然产品名是 CloudSend，则所有平台命名都已一致

当前源码事实：

- Android 当前加载 `libcloudsend.so`。
- Windows 当前加载 `cloudsend.dll`。
- `ORG` 仍是 `com.carriez`
- deep link scheme 与 Rust URI prefix 当前已统一到 `cloudsend` / `CloudSend`。
- Android visible label 是 `云计划`，但 runtime `APP_NAME` 仍是 `CloudSend`；不要把这两者混为同一个字段。

---

## 4. 当前最可信的项目记忆读取顺序（Recommended Memory Read Order）

对于 Codex / Claude Code：

1. `docs/ENGINEERING_INDEX.md`
2. `docs/ENGINEERING_BASELINE.md`
3. 若任务涉及 Android runtime，再读 `docs/ENGINEERING_ANDROID_RUNTIME.md`
4. `docs/TASK_ENTRYPOINTS.md`
5. `docs/REPO_TRUE_STRUCTURE_MAP.md`
6. 再去读源码
7. `AGENTS.md` / `CLAUDE.md` 仅作补充
8. `PC-Build.md` 仅作构建环境背景
9. `terminal.md` 仅作历史背景

---

## 5. 后续审计规则（How to Keep This Audit Current）

当以下任一情况发生时，必须重新审计本文件：

- 某份工程文档与源码出现冲突
- `AGENTS.md` / `CLAUDE.md` 更新后与工程文档出现分歧
- terminal / Android runtime / build 逻辑发生结构性变化
- 新增了新的“项目记忆型文档”

审计更新时必须写清：

- 哪份文档
- 等级变化
- 哪条事实过时
- 现在应该以哪份源码 / 工程文档为准

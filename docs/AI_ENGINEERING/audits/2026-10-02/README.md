# Tunnel 2026-10-02 接管结果与维护入口

Task：`T-2026-10-02-001`；Baseline：`CS-BL-2026-10-02-5cee692`；Evidence：V0。
源码观察点：`5cee6921ec10971bb4654bc010f9328d7f70d02b`；日期按 Asia/Shanghai。

## 1. 本轮交付与结论

已完成本轮仓库级盘点、关键功能链深入静态核验和接管文档整理。已有工程体系继续使用，源码实证支持的内容保留，错误/过期事实定点校正；旧历史原位保留，不增加一套竞争规则。本轮没有产品行为变化。

接管成果可用于定位后续需求、确定修改范围和检查跨层回归。它不等于 235596 行源码逐行审核、所有平台已掌握到运行层面、外部服务器接管或发布就绪。未覆盖深度和所缺材料在下文明确列出，不能以“全面接管”掩盖这些边界。

| 需要做什么 | 主入口 |
|---|---|
| 新会话恢复 | [PROJECT_START_HERE.md](../../../../PROJECT_START_HERE.md) → Session Start Protocol / `.codex` state / current work |
| 按产品需求找代码和对接 | [12_FEATURE_MAP.md](../../12_FEATURE_MAP.md) |
| 查看架构与目录 | [01_ARCHITECTURE.md](../../01_ARCHITECTURE.md)、[02_SOURCE_MAP.md](../../02_SOURCE_MAP.md)、[03_MODULE_DESIGN.md](../../03_MODULE_DESIGN.md) |
| 查看逐项证据 | [Android / Flutter](ANDROID_FLUTTER_AUDIT.md)、[Rust / Network / Windows](RUST_NETWORK_WINDOWS_AUDIT.md)、[API / Security / Release](API_SECURITY_RELEASE_AUDIT.md) |
| 决定旧文档怎么使用 | [104份文档登记](DOCUMENT_REGISTER.md)；保留历史，按职责分层 |
| 当前提交/规模/hash | [2026-10-02 source baseline](../../../BASELINE/2026-10-02_SOURCE_BASELINE.md) |
| 接手具体开发任务 | [Task Template](../../../../TASK_TEMPLATE.md)、[Test Matrix](../../../../TEST_MATRIX.md)、[Development Workflow](../../../../DEVELOPMENT_WORKFLOW.md) |
| 外部资产与接管缺口 | [External Asset Registry](../../../../EXTERNAL_ASSET_REGISTRY.md) |
| 本轮授权、计划、验证与收尾 | [TAKEOVER_TASK.md](TAKEOVER_TASK.md)；`.codex/TASK_HISTORY.md` 的同 Task ID |

## 2. 当前仓库事实

- 产品 Rust `5.2.1`、Flutter `5.2.1+59`，crate/library `tunnel`，Android applicationId `com.tunnel.app`，显示名 `隧道`。
- 初始工作区 clean、detached HEAD；本地非 shallow、只有一个可达 root commit。旧 `77062b4` 对象不存在，无法用本地 Git 比较旧基线或重放旧时间线。
- HEAD tree 有 975 文件、104 Markdown、15,605,939 bytes；tracked 源码/headers/proto约 23.56 万文本行，包含 generated/vendor/空行。
- 当前 `ADB-CODE/`、`LADB/`、`flutter/android/app/src/main/jniLibs/` 均不存在；旧 local-only 与 hash 记录不能当当前资产已就绪。
- 当前远端公开性、PR/Issue/Release、线上部署版本、credential有效性均未联网核验。
- Git blob LF 与 checkout CRLF 的 hash 口径已分开。部分旧 lock hash 与当前 bytes 匹配，但不证明整个源码历史一致。

## 3. 覆盖深度

| 领域 | 本轮深入核验 | 明确未证明 |
|---|---|---|
| Android | core/share/frame/waiting、permission、normal/SKL/ignore、black/touch-block/Dev、JNI、ADB、voice、主要cleanup | 每个异常分支、ROM/设备行为、最终APK/native一致性 |
| Flutter | startup/window/engine/session、三类bridge、waiting/reconnect、timer/owner、account/ADB/voice状态 | 每个widget/视觉/可访问性、所有异步交错、真实多窗口回归 |
| Rust / Network | crate/cfg装配、服务注册、relay/login/permission、协议producer/consumer、file/terminal/tunnel/clipboard | 全部unsafe proof、全部codec/KCP实现、旧版本互通、密码学攻击复现 |
| Windows | capture/input、portable、privacy、Amyuni、printer、DLL/helper/恢复边界 | 外部DLL/driver内部、OS/EDR差异、显示恢复/崩溃实测 |
| API / Security | 客户端契约、OIDC、AB/group、sync、download、dormant upload、broker内嵌源码、风险source→sink | 后端ACL/schema/数据库、实际token部署/有效性、可利用性/影响范围 |
| Build / Release | manifests/locks/scripts/features、generated与native边界、CI triggers、副作用、既有测试资产 | 依赖实际解析、clean build、签名产物、SBOM与发布渠道 |
| Linux/macOS/iOS/Web、vendored库 | 目录、入口、cfg、README/构建残留及跨域相关调用 | 完整平台功能、正式支持矩阵、所有vendor内部实现 |
| 文档/记忆 | 104份逐项职责登记、入口与关键源码断言复核、旧/新baseline、状态和任务指针 | 全部历史文档逐句再认证、不可取得的旧Git对象 |

## 4. 已纠正的高价值文档偏差

| 旧表述/容易误读处 | 当前源码支持的结论 | 证据归属 |
|---|---|---|
| main / 77062b4 / 59 commits是当前状态 | 当前5cee692单root、detached、初始clean；旧历史未复核 | 新Baseline、00、Project State |
| 本机有ADB研究目录与3 ABI binary | 当前目录缺失；7月存在性/hash仅历史 | External Registry、02/08 |
| Server::new统一注册全部服务 | `src/server.rs::new()` free function；video/terminal另有动态创建路径 | 03、Rust审计 |
| terminal只是临时会话，persistence尚未实现 | 有进程内persistent registry、service-ID reattach和cleanup；重启耐久性/owner隔离另论 | 03/06、terminal.md、11 |
| 禁止waiting fallback等于没有自动fallback | PC waiting仅normal refresh；Android screen-off/projection-stop有条件fallback | 04、Android审计 |
| start_capture是一个统一语义 | Activity MethodChannel为兼容normal refresh；JNI set-by-name同名命令可切SKL | 04、Android审计 |
| wheelstop是已接通的侧按钮stop | 只确认FRB mapping；未找到active Dart sender与JNI专用sink | 04、Android审计 |
| ADB完全没有output限制 | 有16KiB显示缓存；wait-before-read/readText临时buffer风险仍在 | 04、Android审计 |
| Flutter产品API全部直接http | wrapper按proxy状态走Dart或Rust URL-key map | 01/07、API审计 |
| ZEGO broker完全无控制 | 内嵌Go有静态Bearer、4KiB request cap、ID清洗和TTL；仍缺产品session/room授权证明 | 07/10、API审计 |
| workflow均只有dispatch | 无push/PR自动gate；3份还保留workflow_call | 02/08、API审计 |
| build.py支持--release | parser无该选项；通用入口示例去除此参数，内部仍执行release builds | AGENTS/CLAUDE、08 |

## 5. 风险与目标实现的差距

本表用于后续需求优先级，不授权修复。完整前提、source anchor和severity以 [10_SECURITY_MODEL.md](../../10_SECURITY_MODEL.md) 与各审计为准。所有利用/设备结果未复现。

| 事项 | 源码证据 / 限制 | 回归入口 |
|---|---|---|
| Credential与敏感日志 | tracked credential-type literals；MethodChannel arguments可含voice token，RDP args可含password；未复述值、未测试有效性 | API-08、FLT-06、NET-06 |
| PortForward认证顺序 | LoginRequest阶段先检查enable-tunnel并connect目标，再校验账号/密码等；字节转发另有authorized门，不能写成已证实认证前数据隧道 | NET-04/06 |
| Android capability | 已认证分支的input/custom masks与MultiClipboards缺同类permission gate | AND-05、NET-04、E2E-04 |
| Terminal隔离 | registry按service_id查询；有终端权限且知有效ID的另一peer能否reattach需验证 | NET-04/06、RST-05 |
| Windows privacy恢复 | hook错误可被吞而仍成功；unhook错误可能提前阻断后续恢复 | WIN-03/04/06、E2E-02 |
| Android foreign memory | Image.use关闭后Rust仍保存pointer，后续才复制；static mut和worker/JNI替换存在并发风险 | AND-02、RST-03/04 |
| 语音同意与token域 | 自动接听/cancel=accept；broker静态credential与默认HTTP；麦克风同意不能由会话授权替代 | AND-07、API-08、E2E-05 |
| 产品登录失败清理 | 本地user/token写入可早于资格校验；客户端残留isLogin不等于服务端授权绕过 | API-01/02、E2E-03 |
| Transport/update/plugin | 既有handshake降级、nonce方向、HTTP、下载执行/解压边界仍待隔离验证 | NET-03、API-04/06、RST-05 |
| 生命周期/可复现性 | unowned timer、未await配置同步、worker cleanup、缺native assets、ZEGO lock漂移 | FLT-02/04/05、AND-06/08、RST-01 |

优先建议：先由项目owner确认credential及外部资产责任人；首次实现需求优先处理认证/权限/consent/日志/内存等可控边界，并同步定义兼容、回滚和正式验证。新功能仍按用户实际目标与产品取舍排期，本报告不自动展开代码改造。

## 6. 后续完整系统接管需要的材料

材料先提供版本、owner、脱敏contract/证据；不要求把secret或生产访问凭据放进聊天/仓库。

1. 产品API与DB：仓库/commit、schema/migration、授权/租户边界、备份恢复/retention、owner。
2. hbbs/hbbr：source/version、部署拓扑、配置模板、密钥责任人、隔离测试环境、监控/容量/回滚。
3. ZEGO：broker实际部署revision、dependency lock、auth/room/TTL规则、RTC SDK来源、日志脱敏与owner。
4. ADB与Windows native：每ABI/driver/DLL的source、version、hash、signature、license、获取/重建方法。
5. Android/Windows正式环境：工具链snapshot、既有构建日志、产物hash和签名证明、设备/OS回归结果。
6. 历史与发行：旧Git bundle/受控历史或upstream fork依据、真实支持平台/渠道、发布/事故/回滚记录。

这些缺口有对应External Asset IDs；保持 OWNER-REQUIRED/MISSING/EXTERNAL，不能用文档补猜。

## 7. 验证与交付边界

- 执行：本地Git状态与tree统计、源码调用链/符号/guard/owner核查、文档引用/路径/围栏/diff/敏感内容V0检查；主agent整合和领域间只读交叉复核。
- 未执行：Cargo/Flutter/Gradle build/test/analyze/codegen、依赖安装、设备/服务集成、网络/生产访问、凭据测试、Git写入、删除/移动、版本、签名、打包、上传/发布。
- 运行、安全、可复现性与发布状态：verification-required / NOT_RUN；release仍BLOCKED。文档验收不替代这些门。
- 持久记忆：`.codex/PROJECT_MEMORY.md`与`ARCHITECTURE_MEMORY.md`保存稳定摘要；`PROJECT_STATE.md`记录本轮snapshot；Task/Changelog/Event记录实际工作；Current Work最后关闭。后续会话从文件恢复，不依赖本次聊天可见性。

### 《编译验证需求》登记（未执行）

| 命令候选 | 环境 / 目录 | 目标与边界 |
|---|---|---|
| `./build.sh 1`、`./build.sh 2` | E-A正式Linux Android host / repo root | ABI/JNI/artifact + AND cases；脚本含安装/codegen/Git config/清理/签名，必须先逐项审查和界定执行阶段 |
| `new-build.cmd` | E-W正式Windows host / repo root | Windows artifact、capture/privacy恢复；driver/cache/pack副作用先明确 |
| `cargo build --release --features flutter`、目标相关`cargo test` | E-R匹配Rust/native依赖 / repo root | 仅对应feature/target，不等于整个workspace/all-features；需另列精确包/feature矩阵 |
| `flutter analyze`，配置适用后再选`flutter test` | E-F锁定Flutter / `flutter/` | 先解决依赖/测试发现条件；manual cm_test harness不是自动测试集 |
| 测试owner制定的隔离设备/协议/API步骤 | E-A/E-W/E-N/E-P | 按上表case与TEST_MATRIX记录expected/actual/negative/recovery；不接生产 |

本轮文档改动本身不需要产品编译；此表是后续验证工作清单。具体命令及副作用以 [08](../../08_BUILD_SYSTEM.md)、[09](../../09_DEBUG_SYSTEM.md) 和源码为准，不可盲目运行。

# PC 远程 ADB 配对与手机界面移除

> T005后续变更：[会话生命周期任务](ADB_SESSION_LIFETIME_TASK.md) / [ADR-0017](../ADR/0017-adb-session-lifetime.md)取代下文10分钟期限；本任务记录保留当时交付。

- Task：T-2026-10-03-004；Principal：tunnel-master；Android / Flutter / Network / Security specialist review。
- Baseline：TUN-BL-2026-10-03-ADB；起点 test / 3c22a25f0151f0dea408c0c03c6e818cdf31fe98。
- 状态：T6 source/V0 handoff；正式构建 / 设备 / 集成 V1—V5 NOT_RUN；权限风险 high，正式最高验证 V4。
- Registry：WORK-20261003-007；State STATE-20261003-004；Event：CE-20261003-T004-01；Decision：D-018 / ADR-0016；Cases ADBP-01—12 / ADBM；Assets EXT-BIN-ADB-001 / EXT-SRC-ADB-HELPER-001。

## T0 权限、恢复与并行保护

用户明确要求修改 Android/PC 配对、ADB 集成、图标与状态面板；原请求提供 C2，并明确授权移除 Android 专用 ADB 页面及仅其引用的两个诊断/授权 widget。恢复点为当前 Git 对象；不删原生 runner/helper/probe/shell。保留 T003 未提交的 Windows 脚本/文档变更及 T002 helper Lambda 修复。Git 写入、项目 build/test/analyze/codegen、安装/设备、签名/上传/发布、生产操作均未授权。

## T1 问题与验收

原 pair 成功即返回 paired，不连接独立的 connection port；ADB_MDNS_AUTO_CONNECT=0。因此配对不等于 shellReady。原 -H 127.0.0.1 在 AOSP 中被当作 remote server，不会启动本地尚不存在的 daemon；修订为 localhost。原 PC typed 协议缺少 pair 入口；移除旧手机页还会移除唯一 consent 控件。

验收：手机没有 ADB 专用 tab；PC 有可拖动配对弹窗、六位码与端口校验、后台立即开始、可取消、有明确终态；自动发现连接端口失败可手动提供；只有 fresh 本机 UID2000/nonce probe 通过才显示就绪；ADB 状态进入 Tunnel 状态监测面板；Android 11—16 正式设备矩阵待验证。

## T2/T3 调用链与不变量

PC dialog → 独立 pairing model → 既有 sessionPeerOption(android-control) → Rust 严格 typed parse / secure authenticated keyboard+video permission → JNI MainService → RemoteAdbPairing worker → Manager/Runner → APK libadb.so → 手机 adbd。结果经 adbRuntimeState 返回。

pair / authorize / pair_cancel / revoke 不使用视频 epoch；pairing不改变输入冻结或 MediaProjection，revoke才退出已有owner并正常回退。端口只允许手机本地；不接受 remote host、serial、任意 shell。单 worker、有限预算、断线/撤权取消；配对码不持久化、不回显、不进入日志/replay cache。取消只在worker终于退出后发CANCELLED，否则PC明确未确认。临时撤权取消job/scopes而保留仍在线连接身份资格，实际disconnect才注销；恢复输入权限后必须显式重新authorize。

删除对象：flutter/lib/mobile/pages/adb_page.dart、flutter/lib/mobile/widgets/adb_mirror_probe_card.dart、flutter/lib/mobile/widgets/adb_remote_consent_card.dart；引用迁移为 PC 配对/授权，内部 native suite 保留。

## T4 授权变更

当前用户明确要求 PC 输入系统配对码传给 APK 并立即执行，替代旧 ADR 中 pairing code phone-only 限制。PC 明示授予当前加密已授权连接 10 分钟的投屏、输入、无障碍和侧按钮 scopes，只有 fresh 本机 probe 成功后生效；历史 paired hint 不授予权限，断线撤销。authorize 显式复用系统已信任密钥；未配对时仍失败，不绕过系统确认。视频/input readiness 仍由 helper capability 和帧事务独立证明。

## T5/T6 验证与交付

V0：协议字段/拒绝任意 shell、跨层引用、秘密生命周期、异步取消、文档和 diff 检查。V1—V5 不在本地执行。服务器配套重建 PC/APK/helper；Android 11/12/13/14/15/16 原生设备及 Android16 OnePlus ACE 6T/iQOO Neo9 验证正确码、过期码、错码、独立连接端口、取消/断网/撤权/重连、ADB/MP 切换、侧按钮与 A11y。

V0结果：九个关键文件注释/string/括号词法闭合、全Flutter旧UI引用0、三删除对象确认、文档链接与diff通过，专项审查修复home空页、权限恢复身份、ADB_BUSY旧ready、cancel lategrant/确认。不是Kotlin/Rust/Dart类型检查或编译通过；新增Rustpayload/port/code/revoke/secret-error单测源码未执行。未下载native binary、不更改固定供应锁；原生16KiB页兼容未证实，不标全Android11—16 PASS。

回滚：按文件撤销本任务源码（保留 T003），恢复旧手机页面和旧 phone-local consent；不可只恢复 UI 而保留不同授权策略。构建产物、驱动供应和版本号不在本任务修改范围。

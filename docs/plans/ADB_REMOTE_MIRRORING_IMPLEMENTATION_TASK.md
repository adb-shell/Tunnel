# T-2026-10-02-003：ADB 投屏 P0 原型源码

## Task / Authority

- Status：handoff / T6等待正式验证；主owner tunnel-master。源码交付不等于P0整体完成。
- User authorization：用户在收到完整方案后要求“开始吧，确保完善”；接受按方案分阶段实施。C2源码/文档，未授权Git写、正式build/test/codegen、设备操作或发布。
- Workspace：独立worktree，test/532637a；CS-BL-2026-10-02-5cee692业务基线。初始已有T002的14份文档修改，原样保留并延续必要状态记录。
- ADR：ADR-0014按当前用户指令接受分阶段实施；ADR-0007的local-only运行边界在P0证据通过前仍保留。
- 当前精确范围：本机显式诊断入口、真实ADB shell身份探测、受信任helper源码/来源、共享有界认证协议、APK原型supervisor和独立构建配方。默认编译flag关闭，不新增PC/relay远程入口。
- 限制：不把编码帧计数当PC解码/relay证据；不修改MediaProjection/无障碍运行行为，不引入任意远程shell；不执行产物。
- Review owners：Android probe/Flutter卡片、Java wire/build、shell helper三个子agent；主agent整合APK/Activity/页面和文档。

## P0 Gate / Known Missing Inputs

- 当前worktree没有jniLibs/libadb.so或已构建helper；没有绑定当前源码的APK/PC/设备证据。
- 当前环境没有通过PATH定位到java/javac/adb/flutter/cargo；这不代表整台机器没有工具，只说明本会话未发现正式环境。
- 已向用户询问有权控制的测试设备/ROM和正式Android/Windows构建及native来源路径。
- 主投屏生产链P1—P6在P0真机结果到位后推进；本轮不提前打开remote feature。

## Impact / Design

| Area | Change | Boundary |
|---|---|---|
| Android local probe | 本机target、uid/nonce、有限process排水/timeout | 无grant/无线调试cycle/kill-server |
| Helper | shell-only采集、MediaCodec H264、固定主display0、旋转结束原型 | 无input/settings/节点/任意命令 |
| Shared wire | 固定版本、双方认证、role/epoch/sequence、包长与帧限额 | 不进入message.proto/FRB |
| APK prototype | 本地用户点击、固定hash产物、本地socket、10秒限时、取消/销毁回收 | 无远程调用、无默认持久画面 |
| Flutter | ADB页开发原型卡片，仅flag构建显示 | 不加正式PC顶栏假入口 |
| Build | 独立javac/d8/jar配方、asset manifest | 不下载SDK、不自动构建/签名/发布 |

## Acceptance / Verification

- V0：路径/调用/数据拥有权/取消/安全边界、三领域交叉审查与diff检查。
- 编写协议负向与探测parser/target/process测试源码；本轮不执行项目测试。
- V1—V4：由正式环境执行，至少ADBM-01—07/12/30及后续完整纵向链；结果未到前NOT_RUN。
- 明确区分helper身份、配置、编码AU/关键帧收到与真实decoder/render完成。
- 任一错误/取消都清理本次资源，不停止core、不杀共享ADB server、不改变其他无障碍。

## Change / Handoff

- 初始事件CE-20261002-T003-01；源码与交接事件CE-20261002-T003-02。
- Source/provenance变化同步External Registry；任务、state、current work及change log分别保持实际状态。
- 已交付本机原型、共享协议、受限helper源码/provenance、build配方、JUnit源码、默认关闭的本地UI与生命周期回收。未修改Rust/JNI/PC远程协议/无障碍运行，未生成或提交二进制。
- V0交叉复核修正：卡片挂载、native assets路径、Activity重建互斥计数、错误包不能当完成、API范围和helper期限；保留旧Runner重连/持久shell长命令的未隔离边界，P1需处理。
- 构建、测试、真机、decode/render/relay及九项需求整体验收均未完成；不得将P0源码交付写成完整ADB投屏已完成。
- 精确入口、命令、设备教程和结果模板见[ADB_P0_VALIDATION_RUNBOOK.md](ADB_P0_VALIDATION_RUNBOOK.md)。未取得正式环境/native来源/设备信息，后续执行还需C3。
- 回滚：默认不设tunnelAdbMirrorP0属性；本机取消只结束本次helper/socket/临时staging，不停止core或全局ADB。新源码与文档保留待后续有范围的修改；不自动删除或Git回退。
- 最后V0记录：22份本次相关Markdown、44个本地链接及围栏检查无错误；tracked diff whitespace检查通过，新增源码无尾随空白；Python脚本仅AST语法读取。此记录不替代Kotlin/Java/Dart编译、JUnit或运行验证。

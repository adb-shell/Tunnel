# ADB 配对、交互与 Windows 打包故障修复

- Task：T-2026-10-03-008；状态 T6 source/V0 handoff；用户本轮明确授权修复源码、精简 UI、拉取官方 LADB 源码研究。
- Principal / Security review：tunnel-master；Android / Flutter / Release 分工，root 复核跨层请求与授权。
- Source：test / 0720e4964c4868394ca24aa8b30aba9089f8aacf；保留并续修 T007 未提交 Windows 脚本和文档。
- Baseline：TUN-BL-2026-10-03-ADB、TUN-BL-2026-10-03-WINDOWS-BUILD；ADR-0016/0017。
- 验证：本地 V0；正式 Windows/Linux 构建与 Android 设备 V1—V4 待执行。ADB 配对属于高权限边界。

## T0—T4 授权与范围

按 TASK_TEMPLATE.md 记录本任务。范围为 PC 配对弹窗、手机本地 ADB runner/discovery/process 生命周期、远程请求/回包、状态面板、Windows 自解压供应链。用户报告正确配对信息仍无响应/错误，按钮与输入失效，Windows 打包未成功。验收需真实配对、shell 身份验证和完整自解压产物证据，不能用源码存在代替通过。

允许可逆源码/文档修改与官方 LADB 源码下载；无 Git 提交/推送、产品 build/test/analyze/codegen、设备安装、发布授权。正式构建环境在用户其他服务器。禁止泄露配对码、去掉会话权限检查或以普通应用 shell 冒充 ADB shell。

## T2/T3 影响与方案

PC dialog → pairing model → Rust typed AndroidControl → MainService → Runtime → RemoteAdbPairing → native libadb/NSD/local identity probe → JNI status queue → PC。保留 pair 与 video lease 隔离、瞬态配对码和本机会话授权。先修实际阻断点，再审查取消、重试、错误返回与 UI 操作一致性。

Windows 路径为 CMD → PowerShell → Rust/Flutter → stage/assets → portable generator/Cargo → payload 校验。上传 launcher 日志止于旧 Flutter dialog 类型错误，不能将该日志描述成打包失败证据；独立审查打包路径。

恢复点为上述 HEAD 与本任务前 T007 diff；后续回滚按任务文件差异实施，不覆盖已有工作。外部 LADB 源码放仓外研究目录，固定 commit 与许可见最终研究记录。

## T5—T8 变更、验证与交接

### 已修复的源码路径

| 问题 | 依据与修改 |
|---|---|
| 取消后无法再执行 | BoundedProcessRunner 以中断状态进入 join/reap，正常退出线程可被误判为无法回收，设置永久 unavailable；清中断后有界清理并恢复中断，补 fake-process 取消→再运行测试源码 |
| ADB daemon 与 LADB 混用 | 原所有命令使用 TCP5037；改应用私有 localfilesystem socket，原 HOME/key 保留，Runner/probe/helper/P0 同源；不停止其他应用 daemon |
| 自动发现拿到旧端口 | NSD 首个结果即 countdown 返回；改收集窗口+解析队列完成，尝试多个实际本机候选，连接后短暂等待真实 get-state/nonce UID2000 |
| 手机服务重建后拒绝授权 | authorizedAdbClients 原仅由 CM add_connection 填充，服务重建不会必然重发；Rust 当前权限门后私有 JNI 刷新资格；保持 secure/auth/keyboard/video、native 回调检查及撤权 |
| 输入、点击无反馈 | 弹窗显式持本地键盘租约和 FocusNode；阻止窗口 focus 再抓远控键盘。按钮与mode/pairing条件一致、原因可见；紧凑首次/已配对双入口，连接端口按需展示 |
| 等待状态误导 | 本地 SENDING/WAITING_ACK 与手机回包区分，12秒无ACK/90秒未完成→请求取消→确认或10秒未确认；不把桥提交成功当作手机收到 |
| 信息过多/错误笼统 | 检测面板ADB/投屏模式两行；helper固定阶段错误与中文说明，避免所有阶段覆盖为HELPER_OR_TRANSPORT_FAILED |
| WindowInjection 工具集不符 | 上游 vcxproj 固定v142，改按初始化VS选择并显式OutDir/IntDir；显式toolset override可用 |
| 产物选择/打包假成功 | x64 Cargo target、--locked、真实artifact、Release逐文件SHA256、统一Release override/UTF8、PE架构/EXE校验；保留T007日志/暂停 |
| 自解压文件异常被吞 | Brotli/MD5/读写/metadata/spawn异常传播，GUI提示并退出1，不启动不完整旧文件；修clear=true时间戳短路 |

### LADB 对照与来源

官方仓库：[tytydraco/LADB](https://github.com/tytydraco/LADB/tree/60f48029cf9d8e0bc848ca41a7bd76694d4ab796)，commit `60f48029cf9d8e0bc848ca41a7bd76694d4ab796`，与已有native供应锁一致。仓外 `../ladb-reference` 保存55个文本源码/配置/许可，SOURCE_PROVENANCE.json逐文件记录SHA256及固定下载URL；SOURCE_COMMIT.txt记录提交。没有下载执行native binary，也未整体vendor参考项目。

重点对照 `app/src/main/java/com/draco/ladb/utils/ADB.kt` 的 pair stdin、start/connect/shell、HOME/TMPDIR，`DnsDiscover.kt` 的持续发现/队列解析；`views/MainActivity.kt` 的系统配对窗口生命周期。输入码用短stdin立即flush符合CLI读取语义；未照抄LADB全局kill-server或切换无线调试设置。使用独立daemon避免共享key环境，配对与connect仍是两步，最后必须真实UID验证。参考LICENSE原文保留；仓库LICENSE含非作者Google Play发布限制，native LICENSE另为Apache文本；不能当作完整发行许可审查或ADB可复现构建证明。

`-L localfilesystem` 的启动/客户端语义另对照 AOSP `packages/modules/adb/client/commandline.cpp`、`client/adb_client.cpp`、`socket_spec.cpp`。原始无超时LocalSocket预检已在review去掉，以有界fixed adb get-state为检查；允许CLI启动自己的daemon，不选择/连接远端目标。

### 验证记录

- 已执行 V0：4个Python文件AST；Windows PowerShell 5.1.19041.6456及PowerShell7.6.5 parser；15个改动Dart/Kotlin文件词法定界符检查；git diff whitespace；私有JNI producer/consumer与peer白名单、native socket调用点、错误码及许可/来源复核。
- 未执行：Dart/Kotlin/Rust类型检查、产品构建、项目单测/新增fake-process与PE合同测试、包生成/安装、设备配对/投屏/长稳、驱动安装。语法/词法检查不等于编译或运行PASS。
- 回归：ADBP-01—16、ADBM、WIN-05/07/08。优先同机LADB并存、正确码首次配对、错码后正确重试、取消后重试、多个NSD端口、manual connect、弹窗焦点/粘贴/Enter/关闭重开、service重建/权限恢复、x64单工具集、自解压文件占用/不可写/坏payload。
- 用户已有launcher日志仅证明旧Dart类型错误阻断Flutter；无本轮修改后的APK/EXE成功证据。所有确定根因均为源码路径证据，不能断言全部用户设备失败已复现。

《编译验证需求》
- Android：用户Linux正式环境，完整源码根目录，`./build.sh 1`（或既有多ABI `./build.sh 2`）；PC：用户Windows x64正式环境，源码根目录，`new-build.cmd`。
- 主程序已经由同批源码构建成功才使用 `new-build.cmd -PackageOnly -ReleaseDir "<实际Release绝对路径>"`；该模式仍编译自解压壳。
- 必须同批更新PC/APK，手机保持无线调试配对窗口，PC首次配对→shell已验证→开始ADB投屏；OnePlus ACE6T与iQOO Neo9 Android16分别记录结果。LADB已有配对不代表Tunnel已配对。
- Windows成功必须有本轮SUCCESS/退出0、PC-Bulid下EXE及payload/build/SHA256记录，并实际启动验证解压与驱动文件；旧EXE存在不能当成功。

交接：指南、03/04/05/06/08领域地图、TEST_MATRIX、External Asset Registry、D-020及项目薄记忆已同步。STATE-20261003-011 / WORK-20261003-016 / CE-20261003-T008-01。无Git写入、产品构建/测试执行、签名或发布。源码保留在独立worktree，正式设备验收未关闭。

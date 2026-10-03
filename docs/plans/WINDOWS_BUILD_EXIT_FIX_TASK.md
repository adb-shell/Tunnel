# Windows 构建提前退出与窗口保留修复

- Task T-2026-10-03-007；Owner tunnel-release-engineer；Baseline TUN-BL-2026-10-03-WINDOWS-BUILD；Source test/0720e4964c4868394ca24aa8b30aba9089f8aacf，起点干净。
- 用户报告PC编译未跑完窗口就关闭；当前指令授权排查与构建脚本修复/C2。本地只V0，未授权产品build/test、下载、设备、Git写入、签名/发布。
- Event CE-20261003-T007-01/02；STATE-20261003-010 / WORK-20261003-014；Cases WIN-08与Windows构建指南故障路径；无新ADR/外部资产变化。

## T0—T4 范围、证据与方案

最初无日志，先按交互运行覆盖默认保留窗口。后续用户提供launcher-4807-28083.log：Rust release阶段完成，Flutter Windows报AndroidAdbPairingDialog不能作为CustomAlertDialog返回；python退出-1，外层[EXIT]1，未到驱动/打包阶段。编译输入反映旧弹窗代码；GitHub test/0720e49已经有T006修复，但日志未记录服务器HEAD，不能据此断言服务器分支或同步方式。大量RemoteException是PS stderr包装噪音，不是首个编译错误。

源码另可确定：new-build.cmd在VS初始化失败与PowerShell返回后直接exit；原-Pause只在PS finally，无法覆盖CMD/PS启动早期失败。Resolve-Path在try之前；transcript停止异常可能跳过锁释放。窗口收尾修复与实际Dart根因分开，不能把“保留窗口”当编译成功。

改动链：build.cmd/pc-bulid.cmd → new-build.cmd → scripts/windows-build.ps1。CMD统一finish、真实退出码保留、默认pause、CI/env/NoPause不等待；启动前创建日志保存VCVARS输出，PS追加同一日志。PS初始化置于try、原生stdout/stderr进入host/transcript、检查global:LASTEXITCODE而不沿用旧值、收尾分别保护锁和transcript；阶段/失败行便于定位。原Rust/Flutter构建、资产锁、驱动/portable验证合同不改。

Microsoft [SHIFT文档](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/shift)确认扫描参数不会改变%*；[PowerShell源码](https://github.com/PowerShell/PowerShell/blob/v7.4.6/src/System.Management.Automation/engine/SpecialVariables.cs)的原生退出码使用global:LASTEXITCODE，非函数本地变量。源路径空格/感叹号继续DisableDelayedExpansion和引号；不组成破坏性跨shell文件操作。

## T5—T8 实施、验证与交接

源码修复完成；日志增量加入只读Git提交号/已跟踪改动数量/关键Dart文件SHA256，写入日志与成功产物build.json；支持无Git ZIP源码，无法获取时unavailable/null，不伪造身份。不记录配置值；Native ErrorRecord只显示Exception.Message，保留stderr实质和原生exitcode。

本地V0 PowerShell AST解析、CMD标签/单一退出路径/参数与exitcode契约、引用/文档/diff。未执行真实或合成构建、工具链、下载、驱动、测试或Git写入。用户日志证明服务器到达Flutter失败，不是修订版构建成功证据。

《编译验证需求》：正式Windows Server x64完整源码根先核对git log/status及实际Dart文件，确保T006适配器已在编译目录；再双击new-build.cmd（另两入口同测），观察最后[STEP]/[FAILED]/失败行；命令行自动化用new-build.cmd -NoPause，检查%errorlevel%。按指南验证缺VS、VCVARS非零、PS参数/解析错误、目录不可写、原生命令缺失/非零、0成功、robocopy0—7及8+，日志与一次暂停/退出码正确。核对Git/unavailable、dirty/hash和stderr错误不丢失。完整构建与PackageOnly验新EXE/payload/driver。回传日志末尾与source/toolchain，不传.info值/密钥。T006修订版真实构建与后续阶段仍待验。

回滚仅T007两脚本与文档，不删除日志/Release/staging/cache，不覆盖ADB修复。运行handoff，不声明Windows编译已通过。

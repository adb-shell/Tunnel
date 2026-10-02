# T-2026-10-03-003：Windows 构建、驱动供应与自解压修复

## T0—T4：授权、基线与方案

- 用户明确要求检查并完善 Windows 脚本，对照 build.cmd 与 RustDesk 官方 Windows 流程，修复产物整理、驱动下载、自解压打包。
- Source：test / 3c22a25f0151f0dea408c0c03c6e818cdf31fe98；初始干净；既有 android-helper 修复保留。Baseline：TUN-BL-2026-10-03-WINDOWS-BUILD，继承ADB基线。
- 授权：构建脚本、相关 Windows 构建代码与文档修改；本地只做 V0 源码、AST/配置/PowerShell 语法和 diff 检查。正式编译/下载二进制/驱动安装、Git 写入、签名/发布不在本轮执行范围。
- Recovery：T001 仍等待服务器/设备验收。本任务承接其 Windows 打包子范围，不修改 ADB runtime；历史基线的 dirty/HEAD 是原始快照，不改写。Owner：tunnel-release-engineer；Security：主 agent 静态检查下载/解压/载荷边界。
- 不变量：保留 Release 与旧输出；新 staging；不依赖仓外 Driver.ps1、不静默跳过缺驱动；失败立即传播。只使用明确官方来源、版本与来源记录；生成 manifest 不修改 res/manifest.xml。不改变工具链/版本/签名。
- 修改面：new-build.cmd/build.cmd/pc-bulid.cmd → scripts/windows-build.ps1 → build.py/CMake → scripts/windows_assets.py → portable/generate.py/build.rs。
- 官方证据：rustdesk/rustdesk master 的 flutter-build.yml、libs/portable/generate.py、Windows build 文档；读取于2026-10-03。版本与路径按本仓源码适配，不能直接复制上游品牌或签名步骤。

## T5—T8：实施、验证与交接

- 当前：源码/V0交付，handoff / T6至正式服务器；最终源码差异及正式验证要求记录在 WINDOWS_BUILD_GUIDE.md。
- 实现：三个CMD统一入口、日志/失败码与新staging；native DLL实际路径及CMake；固定驱动与校验/安全解压、缺失注入DLL源码构建；packer真实artifact、独立manifest、本轮完整payload核验。测试源码已添加但NOT_RUN。
- V0：Python AST、来源锁JSON、新文档链接、原主程序manifest XML/DPI保留、PowerShell5.1.19041.6456语法及diff；没有执行构建脚本、下载binary、项目build/test/analyze/codegen、驱动操作或Git写入。全部正式环境证据待用户服务器运行。
- Memory/Event：STATE-20261003-003、WORK-20261003-005、CE-20261003-T003-01；同步Build/Windows/Debug、External Registry、Test Matrix与全局记忆。没有新的长期产品决定。
- 验收：完整/仅打包路径、带空格/感叹号路径、旧/新 Flutter Release 布局、非默认 Cargo target、首次下载/离线缓存/错误hash/网络失败、驱动和 DLL 全部进入载荷、EXE包含本次payload且可解压启动。
- TEST_MATRIX：WIN-05、WIN-08；正式服务器 V2/V3 待执行。本地 V0不能计作产品编译或自解压运行PASS。
- ADR assessment：修复既有构建合同，N/A（无新增产品架构）；固定供应信息写 External Asset Registry，工具链基线保持。
- 回滚：通过 Git 对照本轮修改恢复源码；不删除已有缓存、Release、用户产物。生成临时文件仅位于新 staging/来源缓存，并保留排障。

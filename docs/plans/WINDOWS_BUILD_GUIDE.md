# Windows 正式构建与自解压产物

2026-10-03 T008：修正仅有 VS2022 工具集时 WindowInjection 固定 v142 的阻断；按已初始化 VS 选工具集并固定输出目录，允许 `TUNNEL_WINDOW_INJECTION_TOOLSET=v143` 显式指定。DLL/packer 明确 x64 target、Cargo --locked；Release→staging 逐文件 SHA256；Python UTF-8；自解压运行时不再吞掉解压、校验、文件/metadata 写入和启动错误，失败提示并退出1。新增 PE 架构/EXE 标志校验，完整服务器构建及解压启动仍待验。见 [修复记录](ADB_RELIABILITY_REPAIR_TASK.md)。

2026-10-03 · T-2026-10-03-003 · Windows x64 · 本轮仅源码/V0，服务器编译、下载、解压启动与驱动运行待验证。

## 1. 本轮修复与官方对照

RustDesk 官方 [Windows workflow](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml) 的顺序是构建 Flutter Release、准备虚拟显示/打印驱动与 WindowInjection、生成 portable 载荷、编译自解压 EXE。其 [generate.py](https://github.com/rustdesk/rustdesk/blob/master/libs/portable/generate.py) 使用 Brotli 包格式与 Cargo packer；本项目沿用该机制，产品 marker 和启动程序为 tunnel。

| 断点 | 源码证据 | 修复 |
|---|---|---|
| 产物路径与复制 | build.py 固定 target/release/deps；旧 build.cmd /MOVE 并仅接受0/1，exec_step吞日志并可重复执行失败命令 | Cargo JSON artifact定位DLL；CMake接收真实DLL；新/旧Flutter布局发现；完整Release复制进新staging，robocopy 0—7视为成功 |
| 缺驱动下载 | new-build只读外部cache；build.cmd依赖仓外 D:\DaXian\Driver.ps1 | 仓内windows_assets.py直接准备官方文件、版本锁与checksum；首次构建无须预先手工解压 |
| 缺自解压闭环 | 旧入口固定packer路径；此前仅检查PE头/文件大小，不能证明本次payload已嵌入 | package/bin明确选择；读取Cargo真实EXE；核验本次payload整段SHA256后复制到最终路径 |
| manifest恢复 | 旧脚本修改res/manifest.xml，异常退出可留下缺DPI声明 | 独立portable-manifest.xml移除dpiAware和dpiAwareness，主程序manifest保留 |
| 多套入口漂移 | build.cmd/new-build.cmd各维护一套命令、驱动和输出逻辑 | build.cmd、pc-bulid.cmd转发new-build.cmd；构建流程由scripts/windows-build.ps1统一执行 |

以上是源码断点修复，未拿到本轮服务器失败日志，不能写成已复现全部机器环境根因。

## 2. 执行入口

在正式 Windows x64 构建服务器的完整源码根目录运行：

```bat
new-build.cmd
rem 默认结束或失败后保留窗口；也可显式要求：
new-build.cmd -Pause
rem 自动化/不需要等待按键：
new-build.cmd -NoPause
```

`build.cmd` 和 `pc-bulid.cmd` 现在使用同一实现，参数和输出位置也一致。入口根据自身路径定位源码，不依赖当前工作目录。T007把保留窗口移至CMD父进程：VS未找到/初始化失败、PowerShell启动或参数错误、构建失败/成功均经过统一收尾；`-Pause`不会暂停两次，`-NoPause`优先。CI环境或TUNNEL_BUILD_NO_PAUSE=1默认不等待，显式-Pause可开启；自动化建议始终传-NoPause。默认 DevEnv=C:\DevEnv、DevTool=C:\DevTool；保留调用者已有路径配置与Rust工具链设置，兼容.info中的服务器构建默认值，仅记录导入的key而不打印value。默认Rust为1.75.0-x86_64-pc-windows-msvc，只设置本次进程的RUSTUP_TOOLCHAIN，不改全局rustup default。需VS2022 x64 Build Tools/Windows SDK（rc.exe、MSBuild）、既有Flutter/Python/Brotli和x64-windows-static vcpkg依赖。

编译已经成功时，仅重做驱动整理和自解压：

```bat
new-build.cmd -PackageOnly
rem 新/旧Release布局同时存在时明确选择：
new-build.cmd -PackageOnly -ReleaseDir "C:\Code\Tunnel\flutter\build\windows\x64\runner\Release"
```

PackageOnly仍会编译portable packer；不会重新编译主Rust/Flutter。Release必须来自需要测试的同一源码版本且已有tunnel.exe、tunnel.dll、flutter_windows.dll、dylib_virtual_display.dll及完整data。旧Flutter布局 windows/runner/Release 也支持；两种布局同时存在时要求明确选择，避免误打包旧程序。

联网准备过完整缓存和依赖后，可使用：

```bat
new-build.cmd -PackageOnly -Offline
```

Offline禁止asset下载，Cargo设CARGO_NET_OFFLINE，pip使用--no-index；完整编译时pub get使用--offline且Windows构建--no-pub。需要提前安装工具链/目标、缓存Cargo/Flutter/native资产及Python依赖；缺件直接失败。

## 3. 输出与排错

T007用户日志launcher-4807-28083.log定位：Rust release完成后Flutter编译旧ADB配对窗返回类型失败，外层正确返回1，尚未到驱动/打包阶段；test提交0720e49已含T006适配器修复。编译前在实际源码目录核对git log/status和android_adb_pairing_dialog.dart，不能只更新启动脚本。新日志记录Source commit/Tracked source changes/ADB pairing source SHA256，成功build.json也保存这些字段；ZIP源码无法取得Git身份时明确unavailable/null。此身份是源码追踪，不是签名/构建通过证明。

成功输出：

```text
PC-Bulid/<源码文件夹名>.exe
PC-Bulid/<源码文件夹名>.exe.payload.json
PC-Bulid/<源码文件夹名>.exe.build.json
PC-Bulid/<源码文件夹名>.exe.sha256
PC-Bulid/staging/<本轮标识>/         完整未压缩运行目录和驱动
PC-Bulid/logs/<本轮标识>.log       本轮控制台记录
PC-Bulid/logs/launcher-*.log       CMD启动记录、VS初始化及追加的构建transcript
```

通过CMD运行时使用同一launcher日志追加VS初始化与PowerShell构建记录；直接运行PowerShell脚本仍使用时间/guid日志。VS初始化前即创建launcher日志；原生构建stdout/stderr逐行显示并进入transcript，显示当前阶段和捕获失败行。PowerShell解析/参数错误发生在transcript之前时，错误保留在控制台，launcher记录最终退出码；源目录不可写无法创建日志时仍显示原因并按CMD策略暂停。进程/OS被强制终止或调用者主动关闭窗口不在暂停保证范围。

源Release与staging保留；前一次成功EXE及已有sidecar在替换前备份为带本轮标识的.bak。统一入口持有本checkout的OS文件锁，防止并行脚本覆盖同一portable输入；进程退出后自动释放。任一步失败返回非零并显示日志路径，不能把历史EXE的存在当本轮成功。CMD在pause/日志操作前保存真实退出码，清理transcript失败也不会跳过锁释放。无自动clean、驱动安装、签名或发布。

载荷要求包括tunnel.dll、flutter_windows.dll、data/app.so、data/icudtl.dat、flutter_assets、dylib_virtual_display.dll、WindowInjection.dll、usbmmidd_v2/usbmmIdd.inf、drivers/RustDeskPrinterDriver/RustDeskPrinterDriver.inf、printer_driver_adapter.dll和windows-assets.json。原ZIP中的相关SYS/CAT及签名目录一并保留；仅排除usbmmidd的Win32、deviceinstaller.exe、usbmmidd.bat，保留deviceinstaller64.exe以符合本仓运行契约。

## 4. 来源、缓存与路径配置

`scripts/windows-assets.lock.json`记录2026-10-03读取官方GitHub release API得到的资产ID/大小/digest。打印驱动v4-1.4、adapter与sha256sums使用固定SHA256，同时要求官方checksum表一致。上游覆盖同名文件而字节不同会拒绝，不静默跟随。

usbmmidd_v2旧release没有提供digest，脚本校验大小/ZIP/必需INF-SYS-CAT并记录实际取得的SHA256；可由资产owner设置TUNNEL_USBMMIDD_SHA256强化固定。此receipt不是已证明的驱动publisher/签名或OS运行验收。默认缓存C:\DevEnv\downloads\rustdesk-drivers，按来源ID/hash命名保存archive，避免误用旧版同名缓存。解压限制路径、大小、Windows保留名和重复路径，不执行下载的驱动。

WindowInjection优先使用指定的TUNNEL_WINDOW_INJECTION_DLL、Release、DevEnv第三方目录或源码根/父目录中的x64 DLL，并记录hash；缺件时使用官方RustDeskTempTopMostWindow固定commit `53b548a5398624f7149a382000397993542ad796`源码archive，在服务器用MSBuild构建。源码固定地址与取得archive的hash进入receipt，签名/完整构建provenance仍待正式环境补证。

可选环境变量：DEVENV、DEVTOOL、VCVARS、RUSTUP_TOOLCHAIN、VCPKG_ROOT、VCPKG_INSTALLED_ROOT、LIBCLANG_PATH、CARGO_TARGET_DIR、TUNNEL_WINDOWS_ASSET_CACHE、TUNNEL_WINDOW_INJECTION_DLL、TUNNEL_WINDOW_INJECTION_TOOLSET、TUNNEL_WINDOWS_RELEASE_DIR。CARGO_TARGET_DIR按源码根规范为绝对路径；DLL与packer从Cargo输出定位。目标支持x86_64-pc-windows-msvc，错误CARGO_BUILD_TARGET明确拒绝。下载使用Python HTTPS、系统/环境代理配置与有限重试，TLS失败不会绕过校验。

## 5. 编译验证需求

- 命令：new-build.cmd；已有Release另验证new-build.cmd -PackageOnly；离线准备后验证-Offline。
- 环境与目录：上述正式Windows x64工具链；完整源码根目录；本地开发worktree未执行。
- 核验：首次空cache可准备全部资产；带空格/感叹号源码路径；新/旧Flutter布局；自定义CARGO_TARGET_DIR；bad hash/网络失败必须非零；EXE包含本轮payload、启动后正确解压运行；虚拟显示和打印安装/卸载另按WIN-05/07/08执行。
- T007故障路径：双击三个入口；VS不存在/初始化失败、PS参数错误、原生命令不存在或退出非零、源目录不可写；窗口保留并显示原因，退出码不被pause覆盖，日志已创建时保留。自动化-NoPause/CI不等待；显式-Pause只等待一次。完整build/PackageOnly stdout与stderr都进入日志。原生0允许继续、robocopy0—7继续、其他失败立即停止，无历史产物假成功。
- 独立合同测试源码：tests/windows_build_contract_test.py（本轮NOT_RUN）。在服务器已有Brotli依赖时可运行 `python tests/windows_build_contract_test.py`，只检查合成payload、坏ZIP、Windows路径、Win32重复INF和离线缺件，不运行产品编译或下载。
- 回传：source commit+dirty、本轮log、EXE SHA256/payload/build manifest、工具链及Windows版本。源码/V0、合同测试、正式编译与驱动运行分开记录。

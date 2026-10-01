> [!IMPORTANT]
> Current CloudSend / 云计划 source truth (verified 2026-06-03): this file is a Windows Server environment background note derived from the upstream RustDesk build setup. It is not the current build command reference.
>
> Current project build entry: run `new-build.cmd` from the repository root. Current version: `5.2.1`. Current Windows DLL: `cloudsend.dll`. Current portable output directory: `PC-Bulid`. The script uses the `C:\DevEnv` / `C:\DevTool` toolchain layout described here, then drives the local `build.py`/Flutter packaging flow for this fork.
>
> Treat old names in this document such as `RustDesk`, `rustdesk-1.4.6`, `rustdesk.exe`, and `C:\Code\RustDesk` as upstream environment examples unless a current script or source file confirms them.
>
> Do not directly copy old build commands from the body to modify the current repository. For current PC builds, use `new-build.cmd`; for current engineering truth, read `docs/ENGINEERING_INDEX.md`, `docs/ENGINEERING_BASELINE.md`, and `docs/TASK_ENTRYPOINTS.md`.

下面是按你当前实际情况重新整理的 **Windows Server 2022 从零搭建 RustDesk 官方 x86-64 自解压 `.exe` 编译环境教程**。

你现在的关键条件我按下面固定：

```text
系统：Windows Server 2022 x64
源码：C:\Code\RustDesk（随时换地方）

已安装：
Visual Studio Build Tools 2022：
C:\DevTool\Microsoft Visual Studio\2022\BuildTools

Git：
C:\DevTool\Git

Python：
C:\DevTool\Python

LLVM 15.0.6：
C:\DevTool\LLVM
```

本教程目标：

```text
只编译官方标准 Windows x86-64 Flutter 桌面端
目标产物：rustdesk-1.4.6-x86_64.exe
类型：官方 portable self-extracted executable，自解压 EXE
包含：RustDesk 主程序、Flutter 运行文件、虚拟显示器驱动、打印驱动、WindowInjection.dll
不编译：Android、macOS、Linux、Web、MSI
不永久修改：flutter/pubspec.yaml 里的 extended_text: 14.0.0
```

我已重新核对官方资料：RustDesk 官方构建文档要求 Windows 构建准备 Visual Studio C++ 工具链、Rust、vcpkg、LLVM 并设置 `LIBCLANG_PATH`；官方 Windows Flutter CI 当前固定 `LLVM_VERSION=15.0.6`、`FLUTTER_VERSION=3.24.5`、`RUST_VERSION=1.75`、`VCPKG_COMMIT_ID=120deac3062162151622ca4860575a33844ba10b`，x64 构建目标是 `x86_64-pc-windows-msvc` + `x64-windows-static`。([RustDesk](https://rustdesk.com/docs/en/dev/build/windows/))

------

# 一、官方版本锁定表

| 项目                        | 使用版本 / 路径                            |
| --------------------------- | ------------------------------------------ |
| RustDesk                    | `1.4.6`                                    |
| Windows 架构                | `x86_64`                                   |
| Rust target                 | `x86_64-pc-windows-msvc`                   |
| vcpkg triplet               | `x64-windows-static`                       |
| Rust                        | `1.75.0`                                   |
| Flutter                     | `3.24.5`                                   |
| Dart                        | Flutter 3.24.5 自带 Dart 3.5.4             |
| LLVM                        | `15.0.6`                                   |
| LLVM 路径                   | `C:\DevTool\LLVM`                          |
| libclang                    | `C:\DevTool\LLVM\bin\libclang.dll`         |
| vcpkg commit                | `120deac3062162151622ca4860575a33844ba10b` |
| flutter_rust_bridge_codegen | `1.80.1`                                   |
| cargo-expand                | `1.0.95`                                   |

官方 release 页面显示 RustDesk `1.4.6` 是当前 latest，并提供 Windows x86-64 EXE/MSI；官方 workflow 也把 `VERSION` 固定为 `1.4.6`。([GitHub](https://github.com/rustdesk/rustdesk/releases))

------

# 二、最终目录规划

```text
C:\DevEnv
  ├─ flutter
  ├─ vcpkg
  ├─ vcpkg-binary-cache
  ├─ cargo
  ├─ rustup
  ├─ pub-cache
  ├─ pip-cache
  ├─ rustdesk-targets
  ├─ downloads
  ├─ third-party
  └─ scripts

C:\DevTool
  ├─ Git
  ├─ Python
  ├─ LLVM
  └─ Microsoft Visual Studio\2022\BuildTools

C:\Code
  └─ RustDesk
```

------

# 三、管理员 PowerShell 初始化目录

打开 **管理员 PowerShell**，执行：

```powershell
New-Item -ItemType Directory -Force `
  C:\DevEnv, `
  C:\DevEnv\downloads, `
  C:\DevEnv\vcpkg-binary-cache, `
  C:\DevEnv\cargo, `
  C:\DevEnv\rustup, `
  C:\DevEnv\pub-cache, `
  C:\DevEnv\pip-cache, `
  C:\DevEnv\rustdesk-targets, `
  C:\DevEnv\third-party, `
  C:\DevEnv\scripts, `
  C:\DevTool, `
  C:\Code | Out-Null
```

启用 Git 长路径，避免 Windows 路径过深导致构建失败：

```powershell
git config --global core.longpaths true
```

------

# 四、检查 LLVM 15.0.6 是否正确

你现在已经把 LLVM 15.0.6 安装到：

```text
C:\DevTool\LLVM
```

执行：

```powershell
dir C:\DevTool\LLVM\bin\libclang.dll
C:\DevTool\LLVM\bin\clang.exe --version
```

正常应该看到：

```text
clang version 15.0.6
```

如果 `libclang.dll` 不存在，说明 LLVM 安装不完整，需要重新安装 LLVM 15.0.6 x64 installer。

RustDesk 官方 Windows FAQ 也明确说明，`bindgen` 找不到 clang/libclang 时，需要安装 LLVM，并把 `LIBCLANG_PATH` 设置为 `llvm_install_dir/bin`。([RustDesk](https://rustdesk.com/docs/en/dev/build/faq/))

------

# 五、配置系统环境变量

管理员 PowerShell 执行：

```powershell
[Environment]::SetEnvironmentVariable("DEVENV", "C:\DevEnv", "Machine")
[Environment]::SetEnvironmentVariable("DEVTOOL", "C:\DevTool", "Machine")

[Environment]::SetEnvironmentVariable("CARGO_HOME", "C:\DevEnv\cargo", "Machine")
[Environment]::SetEnvironmentVariable("RUSTUP_HOME", "C:\DevEnv\rustup", "Machine")

[Environment]::SetEnvironmentVariable("PUB_CACHE", "C:\DevEnv\pub-cache", "Machine")
[Environment]::SetEnvironmentVariable("PIP_CACHE_DIR", "C:\DevEnv\pip-cache", "Machine")

[Environment]::SetEnvironmentVariable("VCPKG_ROOT", "C:\DevEnv\vcpkg", "Machine")
[Environment]::SetEnvironmentVariable("VCPKG_DEFAULT_TRIPLET", "x64-windows-static", "Machine")
[Environment]::SetEnvironmentVariable("VCPKG_DEFAULT_HOST_TRIPLET", "x64-windows-static", "Machine")
[Environment]::SetEnvironmentVariable("X_VCPKG_BINARY_CACHE", "C:\DevEnv\vcpkg-binary-cache", "Machine")
[Environment]::SetEnvironmentVariable("VCPKG_BINARY_SOURCES", "clear;files,C:\DevEnv\vcpkg-binary-cache,readwrite", "Machine")

[Environment]::SetEnvironmentVariable("LIBCLANG_PATH", "C:\DevTool\LLVM\bin", "Machine")
```

追加 PATH：

```powershell
function Add-MachinePath($p) {
  $old = [Environment]::GetEnvironmentVariable("Path", "Machine")
  if ($old -notlike "*$p*") {
    [Environment]::SetEnvironmentVariable("Path", "$old;$p", "Machine")
  }
}

Add-MachinePath "C:\DevTool\Git\cmd"
Add-MachinePath "C:\DevTool\Git\bin"
Add-MachinePath "C:\DevTool\Python"
Add-MachinePath "C:\DevTool\Python\Scripts"
Add-MachinePath "C:\DevTool\LLVM\bin"
Add-MachinePath "C:\DevEnv\cargo\bin"
Add-MachinePath "C:\DevEnv\flutter\bin"
Add-MachinePath "C:\DevEnv\vcpkg"
```

执行完后，**关闭 PowerShell，重新打开管理员 PowerShell**。

------

# 六、检查 Visual Studio Build Tools 2022 C++ 工具链

你已经安装到：

```text
C:\DevTool\Microsoft Visual Studio\2022\BuildTools
```

检查是否有 MSVC C++ 工具链：

```powershell
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"

& $vswhere -products Microsoft.VisualStudio.Product.BuildTools `
  -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
  -property installationPath
```

如果没有输出，补齐 C++ 组件：

```powershell
$installer = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\setup.exe"

& $installer modify `
  --installPath "C:\DevTool\Microsoft Visual Studio\2022\BuildTools" `
  --add Microsoft.VisualStudio.Workload.VCTools `
  --add Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
  --add Microsoft.VisualStudio.Component.Windows10SDK.19041 `
  --includeRecommended `
  --passive `
  --norestart
```

------

# 七、安装 Rust 1.75.0 MSVC

RustDesk 官方 CI 使用 Rust `1.75`；bridge workflow 也使用 Rust `1.75`。([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

```powershell
cd C:\DevEnv\downloads

Invoke-WebRequest `
  -Uri "https://win.rustup.rs/x86_64" `
  -OutFile "rustup-init.exe"

$env:RUSTUP_HOME = "C:\DevEnv\rustup"
$env:CARGO_HOME  = "C:\DevEnv\cargo"

.\rustup-init.exe -y --no-modify-path --default-toolchain 1.75.0-x86_64-pc-windows-msvc
```

安装并固定工具链：

```powershell
C:\DevEnv\cargo\bin\rustup.exe toolchain install 1.75.0-x86_64-pc-windows-msvc
C:\DevEnv\cargo\bin\rustup.exe default 1.75.0-x86_64-pc-windows-msvc
C:\DevEnv\cargo\bin\rustup.exe target add x86_64-pc-windows-msvc
```

检查：

```powershell
rustc -V
cargo -V
rustup show
```

应看到：

```text
rustc 1.75.0
```

------

# 八、安装 Flutter 3.24.5

官方 Windows Flutter CI 使用 Flutter `3.24.5`，并执行 `flutter precache --windows`，然后替换 RustDesk 自定义 Windows Flutter engine。([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

```powershell
cd C:\DevEnv

if (Test-Path C:\DevEnv\flutter) {
  Rename-Item C:\DevEnv\flutter C:\DevEnv\flutter_old_$(Get-Date -Format yyyyMMddHHmmss)
}

git clone --branch 3.24.5 --depth 1 https://github.com/flutter/flutter.git C:\DevEnv\flutter
```

检查 Flutter：

```powershell
flutter --version
flutter doctor -v
flutter precache --windows
```

------

# 九、替换 RustDesk 官方自定义 Flutter Windows engine

官方 CI 会下载：

```text
https://github.com/rustdesk/engine/releases/download/main/windows-x64-release.zip
```

并覆盖 Flutter 的：

```text
bin/cache/artifacts/engine/windows-x64-release
```

对应 workflow 步骤在官方 Windows Flutter 构建里。([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

执行：

```powershell
cd C:\DevEnv\downloads

Invoke-WebRequest `
  -Uri "https://github.com/rustdesk/engine/releases/download/main/windows-x64-release.zip" `
  -OutFile "windows-x64-release.zip"

Remove-Item -Recurse -Force .\windows-x64-release -ErrorAction SilentlyContinue
Expand-Archive .\windows-x64-release.zip -DestinationPath .\windows-x64-release -Force

$engineDir = "C:\DevEnv\flutter\bin\cache\artifacts\engine\windows-x64-release"

Remove-Item "$engineDir\*" -Recurse -Force
Copy-Item ".\windows-x64-release\*" $engineDir -Recurse -Force
```

------

# 十、安装并固定 vcpkg

官方 CI 固定 vcpkg commit：

```text
120deac3062162151622ca4860575a33844ba10b
```

官方 Windows Flutter CI 的 vcpkg triplet 是：

```text
x64-windows-static
```

([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

执行：

```powershell
cd C:\DevEnv

if (!(Test-Path C:\DevEnv\vcpkg)) {
  git clone https://github.com/microsoft/vcpkg.git C:\DevEnv\vcpkg
}

cd C:\DevEnv\vcpkg
git fetch --all
git checkout 120deac3062162151622ca4860575a33844ba10b

.\bootstrap-vcpkg.bat -disableMetrics
```

检查：

```powershell
C:\DevEnv\vcpkg\vcpkg.exe version
```

------

# 十一、准备 RustDesk 源码

如果你已经有 `C:\Code\RustDesk`，先进入目录：

```powershell
cd C:\Code\RustDesk
```

如果目录还不是 Git 仓库，重新 clone：

```powershell
if (!(Test-Path C:\Code\RustDesk\.git)) {
  git clone --recurse-submodules https://github.com/rustdesk/rustdesk.git C:\Code\RustDesk
}
```

固定到官方稳定版 `1.4.6`：

```powershell
cd C:\Code\RustDesk

git fetch --tags
git checkout 1.4.6
git submodule update --init --recursive
```

确认当前版本：

```powershell
git describe --tags
git status
```

应看到类似：

```text
1.4.6
```

------

# 十二、确保 `extended_text: 14.0.0` 保留

你明确要求保留：

```yaml
extended_text: 14.0.0
```

先检查：

```powershell
cd C:\Code\RustDesk

Select-String -Path .\flutter\pubspec.yaml -Pattern "extended_text:"
```

如果不是 `14.0.0`，恢复：

```powershell
git checkout -- .\flutter\pubspec.yaml .\flutter\pubspec.lock

Select-String -Path .\flutter\pubspec.yaml -Pattern "extended_text:"
```

> 说明：官方 bridge workflow 里确实有一行会临时把 `extended_text: 14.0.0` 替换成 `13.0.0` 后再 `flutter pub get`，但那是 CI 生成 bridge artifact 的流程；你本地最终源码要求保留 14.0.0，所以本教程默认不永久修改它。官方 bridge workflow 的这一步可在源码中看到。([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/bridge.yml))

------

# 十三、给 Flutter 3.24.5 打 RustDesk 官方 patch

官方 Windows Flutter CI 会对 Flutter 3.24.5 应用：

```text
.github/patches/flutter_3.24.4_dropdown_menu_enableFilter.diff
```

([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

执行：

```powershell
cd C:\DevEnv\flutter

Copy-Item `
  C:\Code\RustDesk\.github\patches\flutter_3.24.4_dropdown_menu_enableFilter.diff `
  C:\DevEnv\flutter\flutter_3.24.4_dropdown_menu_enableFilter.diff `
  -Force

git apply --check .\flutter_3.24.4_dropdown_menu_enableFilter.diff
if ($LASTEXITCODE -eq 0) {
  git apply .\flutter_3.24.4_dropdown_menu_enableFilter.diff
  Write-Host "Flutter patch 已应用。"
} else {
  Write-Host "Flutter patch 可能已经应用过，继续。"
}
```

------

# 十四、安装 flutter-rust-bridge 生成工具

官方 bridge workflow 使用：

```text
cargo-expand 1.0.95
flutter_rust_bridge_codegen 1.80.1
```

([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/bridge.yml))

执行：

```powershell
cd C:\Code\RustDesk

cargo install cargo-expand --version 1.0.95 --locked
cargo install flutter_rust_bridge_codegen --version 1.80.1 --features "uuid" --locked
```

检查：

```powershell
C:\DevEnv\cargo\bin\flutter_rust_bridge_codegen.exe --version
```

------

# 十五、Flutter 依赖获取，保持 `extended_text: 14.0.0`

执行：

```powershell
cd C:\Code\RustDesk\flutter

flutter pub get

Select-String -Path .\pubspec.yaml -Pattern "extended_text:"
```

必须确认仍然是：

```text
extended_text: 14.0.0
```

如果不是，执行：

```powershell
cd C:\Code\RustDesk
git checkout -- .\flutter\pubspec.yaml .\flutter\pubspec.lock
cd .\flutter
flutter pub get
```

------

# 十六、生成 flutter-rust-bridge 文件

重点：你现在 LLVM 在：

```text
C:\DevTool\LLVM
```

所以 codegen 必须带：

```powershell
--llvm-path "C:\DevTool\LLVM"
```

不要单独执行 `--llvm-path`，它不是命令，它是 `flutter_rust_bridge_codegen.exe` 的参数。

执行完整命令：

```powershell
cd C:\Code\RustDesk

$LLVM_ROOT = "C:\DevTool\LLVM"

if (!(Test-Path "$LLVM_ROOT\bin\libclang.dll")) {
  throw "找不到 $LLVM_ROOT\bin\libclang.dll，请检查 LLVM 15.0.6 是否安装完整。"
}

$env:LIBCLANG_PATH = "$LLVM_ROOT\bin"
$env:PATH = "$LLVM_ROOT\bin;$env:PATH"

C:\DevEnv\cargo\bin\flutter_rust_bridge_codegen.exe `
  --rust-input .\src\flutter_ffi.rs `
  --dart-output .\flutter\lib\generated_bridge.dart `
  --c-output .\flutter\macos\Runner\bridge_generated.h `
  --llvm-path "$LLVM_ROOT"
```

成功后复制 iOS header：

```powershell
Copy-Item .\flutter\macos\Runner\bridge_generated.h .\flutter\ios\Runner\bridge_generated.h -Force
```

检查生成文件：

```powershell
dir C:\Code\RustDesk\src\bridge_generated.rs
dir C:\Code\RustDesk\src\bridge_generated.io.rs
dir C:\Code\RustDesk\flutter\lib\generated_bridge.dart
dir C:\Code\RustDesk\flutter\lib\generated_bridge.freezed.dart
dir C:\Code\RustDesk\flutter\macos\Runner\bridge_generated.h
dir C:\Code\RustDesk\flutter\ios\Runner\bridge_generated.h
```

最后再次确认 `extended_text` 没被改：

```powershell
Select-String -Path C:\Code\RustDesk\flutter\pubspec.yaml -Pattern "extended_text:"
```

必须是：

```text
extended_text: 14.0.0
```

------

# 十七、如果 bridge 生成失败的备用方案：临时改、立即恢复

正常先用上一节，不要动 `extended_text`。

只有当你遇到 `build_runner` / `ffigen` / `extended_text` 相关兼容错误时，再用这个备用方案。这个方案虽然临时按官方 bridge workflow 改成 `13.0.0`，但最后会强制恢复为 `14.0.0`。

```powershell
cd C:\Code\RustDesk

Copy-Item .\flutter\pubspec.yaml .\flutter\pubspec.yaml.keep14.bak -Force

(Get-Content .\flutter\pubspec.yaml -Raw).Replace("extended_text: 14.0.0", "extended_text: 13.0.0") |
  Set-Content .\flutter\pubspec.yaml -Encoding UTF8

cd .\flutter
flutter pub get

cd C:\Code\RustDesk

$LLVM_ROOT = "C:\DevTool\LLVM"
$env:LIBCLANG_PATH = "$LLVM_ROOT\bin"
$env:PATH = "$LLVM_ROOT\bin;$env:PATH"

C:\DevEnv\cargo\bin\flutter_rust_bridge_codegen.exe `
  --rust-input .\src\flutter_ffi.rs `
  --dart-output .\flutter\lib\generated_bridge.dart `
  --c-output .\flutter\macos\Runner\bridge_generated.h `
  --llvm-path "$LLVM_ROOT"

Copy-Item .\flutter\macos\Runner\bridge_generated.h .\flutter\ios\Runner\bridge_generated.h -Force

Move-Item .\flutter\pubspec.yaml.keep14.bak .\flutter\pubspec.yaml -Force
git checkout -- .\flutter\pubspec.lock

cd .\flutter
flutter pub get

Select-String -Path .\pubspec.yaml -Pattern "extended_text:"
```

最终仍必须是：

```text
extended_text: 14.0.0
```

------

# 十八、预缓存 vcpkg 依赖

官方 CI 在源码根目录执行 vcpkg manifest install，triplet 是 `x64-windows-static`，安装根是 `$VCPKG_ROOT/installed`。([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

执行：

```powershell
cd C:\Code\RustDesk

$env:VCPKG_ROOT = "C:\DevEnv\vcpkg"
$env:VCPKG_DEFAULT_TRIPLET = "x64-windows-static"
$env:VCPKG_DEFAULT_HOST_TRIPLET = "x64-windows-static"
$env:X_VCPKG_BINARY_CACHE = "C:\DevEnv\vcpkg-binary-cache"
$env:VCPKG_BINARY_SOURCES = "clear;files,C:\DevEnv\vcpkg-binary-cache,readwrite"

C:\DevEnv\vcpkg\vcpkg.exe install `
  --triplet x64-windows-static `
  --x-install-root="C:\DevEnv\vcpkg\installed"
```

这个过程第一次会很慢，尤其是 `ffmpeg`、`aom`、`libvpx`、`libyuv`、`opus` 等依赖。

后续只要不删除下面目录，就不会每次从头来：

```text
C:\DevEnv\vcpkg\downloads
C:\DevEnv\vcpkg\buildtrees
C:\DevEnv\vcpkg\packages
C:\DevEnv\vcpkg\installed
C:\DevEnv\vcpkg-binary-cache
```

------

# 十九、预缓存 Cargo / Flutter

Cargo：

```powershell
cd C:\Code\RustDesk
cargo fetch --locked
```

Flutter：

```powershell
cd C:\Code\RustDesk\flutter
flutter pub get
flutter precache --windows
```

------

# 二十、正式编译 RustDesk Windows x64 Flutter Release

官方 Windows Flutter CI 的核心构建命令是：

```text
python3 .\build.py --portable --hwcodec --flutter --vram --skip-portable-pack
```

然后把：

```text
flutter/build/windows/x64/runner/Release
```

移动为：

```text
./rustdesk
```

([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

建议使用 **x64 Native Tools Command Prompt for VS 2022**。

如果你用普通管理员 CMD，先执行：

```cmd
call "C:\DevTool\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
```

然后执行：

```cmd
set CARGO_HOME=C:\DevEnv\cargo
set RUSTUP_HOME=C:\DevEnv\rustup
set PUB_CACHE=C:\DevEnv\pub-cache
set PIP_CACHE_DIR=C:\DevEnv\pip-cache

set VCPKG_ROOT=C:\DevEnv\vcpkg
set VCPKG_DEFAULT_TRIPLET=x64-windows-static
set VCPKG_DEFAULT_HOST_TRIPLET=x64-windows-static
set X_VCPKG_BINARY_CACHE=C:\DevEnv\vcpkg-binary-cache
set VCPKG_BINARY_SOURCES=clear;files,C:\DevEnv\vcpkg-binary-cache,readwrite

set LIBCLANG_PATH=C:\DevTool\LLVM\bin

set PATH=C:\DevEnv\cargo\bin;C:\DevEnv\flutter\bin;C:\DevEnv\vcpkg;C:\DevTool\Python;C:\DevTool\Python\Scripts;C:\DevTool\Git\cmd;C:\DevTool\Git\bin;C:\DevTool\LLVM\bin;%PATH%

cd /d C:\Code\RustDesk

python .\build.py --portable --hwcodec --flutter --vram --skip-portable-pack
```

构建成功后整理目录：

```powershell
cd C:\Code\RustDesk

Remove-Item .\rustdesk -Recurse -Force -ErrorAction SilentlyContinue
Move-Item .\flutter\build\windows\x64\runner\Release .\rustdesk
```

检查：

```powershell
dir C:\Code\RustDesk\rustdesk\rustdesk.exe
dir C:\Code\RustDesk\rustdesk\dylib_virtual_display.dll
```

------

# 二十一、编译并加入 WindowInjection.dll

官方 Windows Flutter CI 会构建 `RustDeskTempTopMostWindow`，并把生成的 `WindowInjection.dll` 下载到 `./rustdesk`。([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

执行：

```powershell
cd C:\DevEnv\third-party

if (!(Test-Path .\RustDeskTempTopMostWindow\.git)) {
  git clone https://github.com/rustdesk-org/RustDeskTempTopMostWindow.git
}

cd .\RustDeskTempTopMostWindow

git fetch --all
git checkout 53b548a5398624f7149a382000397993542ad796

$msbuild = "C:\DevTool\Microsoft Visual Studio\2022\BuildTools\MSBuild\Current\Bin\MSBuild.exe"

& $msbuild .\WindowInjection\WindowInjection.vcxproj `
  -p:Configuration=Release `
  -p:Platform=x64 `
  /p:TargetVersion=Windows10

Copy-Item .\WindowInjection\x64\Release\WindowInjection.dll C:\Code\RustDesk\rustdesk\WindowInjection.dll -Force
```

检查：

```powershell
dir C:\Code\RustDesk\rustdesk\WindowInjection.dll
```

------

# 二十二、加入官方虚拟显示器驱动 usbmmidd_v2

官方 CI 会下载 `usbmmidd_v2.zip`，解压后删除 `Win32`、`deviceinstaller64.exe`、`deviceinstaller.exe`、`usbmmidd.bat`，再移动到 `./rustdesk`。([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

执行：

```powershell
$drv = "C:\DevEnv\downloads\rustdesk-drivers"
New-Item -ItemType Directory -Force $drv | Out-Null
cd $drv

Invoke-WebRequest `
  -Uri "https://github.com/rustdesk-org/rdev/releases/download/usbmmidd_v2/usbmmidd_v2.zip" `
  -OutFile "usbmmidd_v2.zip"

Remove-Item .\usbmmidd_v2 -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive .\usbmmidd_v2.zip -DestinationPath . -Force

Remove-Item .\usbmmidd_v2\Win32 -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item `
  .\usbmmidd_v2\deviceinstaller64.exe, `
  .\usbmmidd_v2\deviceinstaller.exe, `
  .\usbmmidd_v2\usbmmidd.bat `
  -Force -ErrorAction SilentlyContinue

Remove-Item C:\Code\RustDesk\rustdesk\usbmmidd_v2 -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item .\usbmmidd_v2 C:\Code\RustDesk\rustdesk\usbmmidd_v2 -Recurse -Force
```

检查：

```powershell
dir C:\Code\RustDesk\rustdesk\usbmmidd_v2
```

------

# 二十三、加入官方打印驱动

官方 CI 会下载：

```text
rustdesk_printer_driver_v4-1.4.zip
printer_driver_adapter.zip
sha256sums
```

校验 SHA256 后，把打印驱动放到：

```text
./rustdesk/drivers/RustDeskPrinterDriver
```

并把：

```text
printer_driver_adapter.dll
```

放到：

```text
./rustdesk
```

([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

执行：

```powershell
$drv = "C:\DevEnv\downloads\rustdesk-drivers"
New-Item -ItemType Directory -Force $drv | Out-Null
cd $drv

Invoke-WebRequest `
  -Uri "https://github.com/rustdesk/hbb_common/releases/download/driver/rustdesk_printer_driver_v4-1.4.zip" `
  -OutFile "rustdesk_printer_driver_v4-1.4.zip"

Invoke-WebRequest `
  -Uri "https://github.com/rustdesk/hbb_common/releases/download/driver/printer_driver_adapter.zip" `
  -OutFile "printer_driver_adapter.zip"

Invoke-WebRequest `
  -Uri "https://github.com/rustdesk/hbb_common/releases/download/driver/sha256sums" `
  -OutFile "sha256sums"

$checksumDriver = (Select-String -Path .\sha256sums -Pattern '^([a-fA-F0-9]{64}) \*rustdesk_printer_driver_v4-1.4\.zip$').Matches[0].Groups[1].Value
$checksumAdapter = (Select-String -Path .\sha256sums -Pattern '^([a-fA-F0-9]{64}) \*printer_driver_adapter\.zip$').Matches[0].Groups[1].Value

$hashDriver = (Get-FileHash .\rustdesk_printer_driver_v4-1.4.zip -Algorithm SHA256).Hash
$hashAdapter = (Get-FileHash .\printer_driver_adapter.zip -Algorithm SHA256).Hash

if (($checksumDriver -ieq $hashDriver) -and ($checksumAdapter -ieq $hashAdapter)) {
  Remove-Item .\rustdesk_printer_driver_v4-1.4 -Recurse -Force -ErrorAction SilentlyContinue
  Expand-Archive .\rustdesk_printer_driver_v4-1.4.zip -DestinationPath . -Force

  New-Item -ItemType Directory -Force C:\Code\RustDesk\rustdesk\drivers | Out-Null

  Remove-Item C:\Code\RustDesk\rustdesk\drivers\RustDeskPrinterDriver -Recurse -Force -ErrorAction SilentlyContinue
  Copy-Item .\rustdesk_printer_driver_v4-1.4 C:\Code\RustDesk\rustdesk\drivers\RustDeskPrinterDriver -Recurse -Force

  Remove-Item .\printer_driver_adapter.dll -Force -ErrorAction SilentlyContinue
  Expand-Archive .\printer_driver_adapter.zip -DestinationPath . -Force
  Copy-Item .\printer_driver_adapter.dll C:\Code\RustDesk\rustdesk\printer_driver_adapter.dll -Force

  Write-Host "打印驱动已复制完成。"
} else {
  throw "打印驱动 SHA256 校验失败，停止。"
}
```

检查：

```powershell
dir C:\Code\RustDesk\rustdesk\drivers\RustDeskPrinterDriver
dir C:\Code\RustDesk\rustdesk\printer_driver_adapter.dll
```

------

# 二十四、复制 Runner.res

官方 CI 会查找 `Runner.res`，复制到：

```text
libs/portable/Runner.res
```

因为 `Runner.rc` 没有真实版本信息，但 `Runner.res` 有。([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

执行：

```powershell
cd C:\Code\RustDesk

$runnerRes = Get-ChildItem C:\Code\RustDesk -Filter Runner.res -Recurse | Select-Object -First 1

if ($runnerRes) {
  Copy-Item $runnerRes.FullName C:\Code\RustDesk\libs\portable\Runner.res -Force
  Write-Host "已复制 Runner.res: $($runnerRes.FullName)"
} else {
  Write-Warning "未找到 Runner.res，自解压 EXE 仍可能生成，但版本资源可能不完整。"
}
```

------

# 二十五、生成官方自解压 EXE

官方 CI 的自解压打包步骤是：

```text
sed -i '/dpiAware/d' res/manifest.xml
pushd ./libs/portable
pip3 install -r requirements.txt
python3 ./generate.py -f ../../rustdesk/ -o . -e ../../rustdesk/rustdesk.exe
popd
mkdir -p ./SignOutput
mv ./target/release/rustdesk-portable-packer.exe ./SignOutput/rustdesk-${VERSION}-${arch}.exe
```

([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

推荐使用 **Git Bash** 执行，因为官方 Windows 文档也提示文档里的 shell 命令需要在 Git Bash 运行。([RustDesk](https://rustdesk.com/docs/en/dev/build/windows/))

打开 Git Bash，执行：

```bash
cd /c/Code/RustDesk

export PATH="/c/DevTool/Python:/c/DevTool/Python/Scripts:/c/DevEnv/cargo/bin:/c/DevEnv/flutter/bin:/c/DevEnv/vcpkg:/c/DevTool/Git/cmd:/c/DevTool/Git/bin:/c/DevTool/LLVM/bin:$PATH"

export CARGO_HOME="C:/DevEnv/cargo"
export RUSTUP_HOME="C:/DevEnv/rustup"
export PUB_CACHE="C:/DevEnv/pub-cache"
export PIP_CACHE_DIR="C:/DevEnv/pip-cache"

export VCPKG_ROOT="C:/DevEnv/vcpkg"
export VCPKG_DEFAULT_TRIPLET="x64-windows-static"
export VCPKG_DEFAULT_HOST_TRIPLET="x64-windows-static"
export X_VCPKG_BINARY_CACHE="C:/DevEnv/vcpkg-binary-cache"
export VCPKG_BINARY_SOURCES="clear;files,C:/DevEnv/vcpkg-binary-cache,readwrite"

export LIBCLANG_PATH="C:/DevTool/LLVM/bin"

sed -i '/dpiAware/d' res/manifest.xml

pushd ./libs/portable
python -m pip install -r requirements.txt
python ./generate.py -f ../../rustdesk/ -o . -e ../../rustdesk/rustdesk.exe
popd

mkdir -p ./SignOutput
mv ./target/release/rustdesk-portable-packer.exe ./SignOutput/rustdesk-1.4.6-x86_64.exe

git checkout -- res/manifest.xml
```

最终文件：

```text
C:\Code\RustDesk\SignOutput\rustdesk-1.4.6-x86_64.exe
```

------

# 二十六、最终检查

PowerShell 执行：

```powershell
dir C:\Code\RustDesk\SignOutput\rustdesk-1.4.6-x86_64.exe

Test-Path C:\Code\RustDesk\rustdesk\rustdesk.exe
Test-Path C:\Code\RustDesk\rustdesk\WindowInjection.dll
Test-Path C:\Code\RustDesk\rustdesk\usbmmidd_v2
Test-Path C:\Code\RustDesk\rustdesk\drivers\RustDeskPrinterDriver
Test-Path C:\Code\RustDesk\rustdesk\printer_driver_adapter.dll
Test-Path C:\Code\RustDesk\rustdesk\dylib_virtual_display.dll

Select-String -Path C:\Code\RustDesk\flutter\pubspec.yaml -Pattern "extended_text:"
```

最后一行必须显示：

```text
extended_text: 14.0.0
```

运行测试：

```powershell
C:\Code\RustDesk\SignOutput\rustdesk-1.4.6-x86_64.exe
```

注意：如果你没有 RustDesk 官方签名服务或自己的代码签名证书，最终 EXE 是 **未签名自解压程序**。官方 CI 只有在签名密钥存在时才执行签名步骤。([GitHub](https://github.com/rustdesk/rustdesk/blob/master/.github/workflows/flutter-build.yml))

------

# 二十七、以后重复编译的最短流程

后续只要不要删除这些目录：

```text
C:\DevEnv\vcpkg
C:\DevEnv\vcpkg-binary-cache
C:\DevEnv\cargo
C:\DevEnv\rustup
C:\DevEnv\pub-cache
C:\DevEnv\pip-cache
C:\DevEnv\rustdesk-targets
C:\Code\RustDesk\flutter\build
```

重复构建时只需要：

```cmd
call "C:\DevTool\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"

set CARGO_HOME=C:\DevEnv\cargo
set RUSTUP_HOME=C:\DevEnv\rustup
set PUB_CACHE=C:\DevEnv\pub-cache
set PIP_CACHE_DIR=C:\DevEnv\pip-cache

set VCPKG_ROOT=C:\DevEnv\vcpkg
set VCPKG_DEFAULT_TRIPLET=x64-windows-static
set VCPKG_DEFAULT_HOST_TRIPLET=x64-windows-static
set X_VCPKG_BINARY_CACHE=C:\DevEnv\vcpkg-binary-cache
set VCPKG_BINARY_SOURCES=clear;files,C:\DevEnv\vcpkg-binary-cache,readwrite

set LIBCLANG_PATH=C:\DevTool\LLVM\bin

set PATH=C:\DevEnv\cargo\bin;C:\DevEnv\flutter\bin;C:\DevEnv\vcpkg;C:\DevTool\Python;C:\DevTool\Python\Scripts;C:\DevTool\Git\cmd;C:\DevTool\Git\bin;C:\DevTool\LLVM\bin;%PATH%

cd /d C:\Code\RustDesk

python .\build.py --portable --hwcodec --flutter --vram --skip-portable-pack
```

然后重新执行：

```text
1. Move Release 到 .\rustdesk
2. 复制 WindowInjection.dll
3. 复制 usbmmidd_v2
4. 复制打印驱动
5. 复制 Runner.res
6. 生成 self-extracted EXE
```

------

# 二十八、常见错误对应处理

## 1. `Couldn't find bin\libclang.dll`

原因：`ffigen` 没找到 LLVM。

修复：

```powershell
$LLVM_ROOT = "C:\DevTool\LLVM"
$env:LIBCLANG_PATH = "$LLVM_ROOT\bin"
$env:PATH = "$LLVM_ROOT\bin;$env:PATH"

C:\DevEnv\cargo\bin\flutter_rust_bridge_codegen.exe `
  --rust-input .\src\flutter_ffi.rs `
  --dart-output .\flutter\lib\generated_bridge.dart `
  --c-output .\flutter\macos\Runner\bridge_generated.h `
  --llvm-path "$LLVM_ROOT"
```

注意：

```text
正确：--llvm-path "C:\DevTool\LLVM"
错误：--llvm-path "C:\DevTool\LLVM\bin"
```

------

## 2. PowerShell 报 `一元运算符“--”后面缺少表达式`

原因：你把参数单独执行了。

错误：

```powershell
--llvm-path "C:\DevTool\LLVM"
```

正确：必须跟完整命令一起执行：

```powershell
C:\DevEnv\cargo\bin\flutter_rust_bridge_codegen.exe `
  --rust-input .\src\flutter_ffi.rs `
  --dart-output .\flutter\lib\generated_bridge.dart `
  --c-output .\flutter\macos\Runner\bridge_generated.h `
  --llvm-path "C:\DevTool\LLVM"
```

------

## 3. `bridge_generated.h` 不存在

原因：上一条 codegen 失败，header 没生成。

重新执行第十六节，成功后再复制：

```powershell
Copy-Item .\flutter\macos\Runner\bridge_generated.h .\flutter\ios\Runner\bridge_generated.h -Force
```

------

## 4. `extended_text` 被改成 13.0.0

立即恢复：

```powershell
cd C:\Code\RustDesk
git checkout -- .\flutter\pubspec.yaml .\flutter\pubspec.lock

cd .\flutter
flutter pub get

Select-String -Path .\pubspec.yaml -Pattern "extended_text:"
```

确保显示：

```text
extended_text: 14.0.0
```

------


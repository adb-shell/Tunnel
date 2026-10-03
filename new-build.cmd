@echo off
chcp 65001 >nul
setlocal DisableDelayedExpansion
rem Canonical Windows x64 entry. Paths are independent of the caller's CWD.
set "TUNNEL_SOURCE_ROOT=%~dp0"
set "TUNNEL_BUILD_LAUNCHER_ACTIVE=1"
set "TUNNEL_EXIT_CODE=1"
rem Keep interactive consoles open, including failures before PowerShell starts.
rem SHIFT does not change %%*: forward the original arguments to PowerShell.
set "TUNNEL_PAUSE_AT_END=1"
set "TUNNEL_EXPLICIT_PAUSE=0"
set "TUNNEL_EXPLICIT_NO_PAUSE=0"
if defined CI set "TUNNEL_PAUSE_AT_END=0"
if "%TUNNEL_BUILD_NO_PAUSE%"=="1" set "TUNNEL_PAUSE_AT_END=0"
:parse_options
if "%~1"=="" goto init_logs
if /i "%~1"=="-Pause" set "TUNNEL_EXPLICIT_PAUSE=1"
if /i "%~1"=="-NoPause" set "TUNNEL_EXPLICIT_NO_PAUSE=1"
shift
goto parse_options
:init_logs
if "%TUNNEL_EXPLICIT_PAUSE%"=="1" set "TUNNEL_PAUSE_AT_END=1"
if "%TUNNEL_EXPLICIT_NO_PAUSE%"=="1" set "TUNNEL_PAUSE_AT_END=0"
if not exist "%TUNNEL_SOURCE_ROOT%PC-Bulid\logs" mkdir "%TUNNEL_SOURCE_ROOT%PC-Bulid\logs"
if not exist "%TUNNEL_SOURCE_ROOT%PC-Bulid\logs\" goto log_failed
:choose_log
set "TUNNEL_BUILD_LAUNCH_LOG=%TUNNEL_SOURCE_ROOT%PC-Bulid\logs\launcher-%RANDOM%-%RANDOM%.log"
if exist "%TUNNEL_BUILD_LAUNCH_LOG%" goto choose_log
> "%TUNNEL_BUILD_LAUNCH_LOG%" echo [INFO] Windows build launcher
if errorlevel 1 goto log_failed
echo [INFO] Log: "%TUNNEL_BUILD_LAUNCH_LOG%"
if not defined DEVENV set "DEVENV=C:\DevEnv"
if not defined DEVTOOL set "DEVTOOL=C:\DevTool"
if not defined CARGO_HOME set "CARGO_HOME=%DEVENV%\cargo"
if not defined RUSTUP_HOME set "RUSTUP_HOME=%DEVENV%\rustup"
if not defined RUSTUP_TOOLCHAIN set "RUSTUP_TOOLCHAIN=1.75.0-x86_64-pc-windows-msvc"
if not defined PUB_CACHE set "PUB_CACHE=%DEVENV%\pub-cache"
if not defined PIP_CACHE_DIR set "PIP_CACHE_DIR=%DEVENV%\pip-cache"
if not defined VCPKG_ROOT set "VCPKG_ROOT=%DEVENV%\vcpkg"
if not defined VCPKG_INSTALLED_ROOT set "VCPKG_INSTALLED_ROOT=%VCPKG_ROOT%\installed"
if not defined VCPKG_DEFAULT_TRIPLET set "VCPKG_DEFAULT_TRIPLET=x64-windows-static"
if not defined VCPKG_DEFAULT_HOST_TRIPLET set "VCPKG_DEFAULT_HOST_TRIPLET=x64-windows-static"
if not defined VCPKG_BINARY_SOURCES set "VCPKG_BINARY_SOURCES=clear;files,%DEVENV%\vcpkg-binary-cache,readwrite"
if not defined LIBCLANG_PATH set "LIBCLANG_PATH=%DEVTOOL%\LLVM\bin"
rem vcvars may override VCPKG_ROOT. Preserve the user's configured root.
set "TUNNEL_VCPKG_ROOT=%VCPKG_ROOT%"
set "PATH=%CARGO_HOME%\bin;%DEVENV%\flutter\bin;%VCPKG_ROOT%;%DEVTOOL%\Python;%DEVTOOL%\Python\Scripts;%DEVTOOL%\Git\cmd;%DEVTOOL%\Git\bin;%DEVTOOL%\LLVM\bin;%PATH%"
if not defined VCVARS set "VCVARS=%DEVTOOL%\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VCVARS%" set "VCVARS=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VCVARS%" goto missing_vs
call "%VCVARS%" >> "%TUNNEL_BUILD_LAUNCH_LOG%" 2>&1
set "TUNNEL_EXIT_CODE=%errorlevel%"
type "%TUNNEL_BUILD_LAUNCH_LOG%"
if not "%TUNNEL_EXIT_CODE%"=="0" goto finish
set "VCPKG_ROOT=%TUNNEL_VCPKG_ROOT%"
set "PATH=%CARGO_HOME%\bin;%DEVENV%\flutter\bin;%VCPKG_ROOT%;%DEVTOOL%\Python;%DEVTOOL%\Python\Scripts;%DEVTOOL%\Git\cmd;%DEVTOOL%\Git\bin;%DEVTOOL%\LLVM\bin;%PATH%"
if not exist "%TUNNEL_SOURCE_ROOT%scripts\windows-build.ps1" goto missing_script
where powershell.exe >nul 2>&1
if errorlevel 1 goto missing_powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%TUNNEL_SOURCE_ROOT%scripts\windows-build.ps1" -SourceRoot "%TUNNEL_SOURCE_ROOT%." %*
set "TUNNEL_EXIT_CODE=%errorlevel%"
goto finish
:missing_vs
echo [FAILED] Visual Studio x64 Build Tools environment not found: "%VCVARS%"
>> "%TUNNEL_BUILD_LAUNCH_LOG%" echo [FAILED] Visual Studio x64 Build Tools environment not found.
set "TUNNEL_EXIT_CODE=1"
goto finish
:missing_script
echo [FAILED] scripts\windows-build.ps1 is missing. Use a complete source checkout.
>> "%TUNNEL_BUILD_LAUNCH_LOG%" echo [FAILED] scripts\windows-build.ps1 is missing.
set "TUNNEL_EXIT_CODE=1"
goto finish
:missing_powershell
echo [FAILED] powershell.exe was not found.
>> "%TUNNEL_BUILD_LAUNCH_LOG%" echo [FAILED] powershell.exe was not found.
set "TUNNEL_EXIT_CODE=1"
goto finish
:log_failed
echo [FAILED] Cannot create the build log. Check source directory write permissions.
set "TUNNEL_BUILD_LAUNCH_LOG="
set "TUNNEL_EXIT_CODE=1"
:finish
if not "%TUNNEL_EXIT_CODE%"=="0" echo [FAILED] Build stopped with exit code %TUNNEL_EXIT_CODE%.
if defined TUNNEL_BUILD_LAUNCH_LOG echo [INFO] Retained log: "%TUNNEL_BUILD_LAUNCH_LOG%"
if defined TUNNEL_BUILD_LAUNCH_LOG >> "%TUNNEL_BUILD_LAUNCH_LOG%" echo [EXIT] %TUNNEL_EXIT_CODE%
if "%TUNNEL_PAUSE_AT_END%"=="1" pause
rem PAUSE/TYPE/logging must never replace the build process exit code.
exit /b %TUNNEL_EXIT_CODE%

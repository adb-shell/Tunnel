@echo off
chcp 65001 >nul
setlocal DisableDelayedExpansion
rem Canonical Windows x64 entry. Paths are independent of the caller's CWD.
set "TUNNEL_SOURCE_ROOT=%~dp0"
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
call "%VCVARS%"
if errorlevel 1 exit /b 1
set "VCPKG_ROOT=%TUNNEL_VCPKG_ROOT%"
set "PATH=%CARGO_HOME%\bin;%DEVENV%\flutter\bin;%VCPKG_ROOT%;%DEVTOOL%\Python;%DEVTOOL%\Python\Scripts;%DEVTOOL%\Git\cmd;%DEVTOOL%\Git\bin;%DEVTOOL%\LLVM\bin;%PATH%"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%TUNNEL_SOURCE_ROOT%scripts\windows-build.ps1" -SourceRoot "%TUNNEL_SOURCE_ROOT%." %*
exit /b %errorlevel%
:missing_vs
echo [FAILED] Visual Studio x64 Build Tools environment not found: "%VCVARS%"
exit /b 1

@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion
title CloudSend PC Build Script
color 0B

:: ============================================================
:: CloudSend Windows x64 portable build script
:: Adapted for the PC.md server layout:
::   C:\DevEnv + C:\DevTool
:: Script location is always treated as source root.
:: ============================================================

set "ROOT_FOLDER=%~dp0"
if "%ROOT_FOLDER:~-1%"=="\" set "ROOT_FOLDER=%ROOT_FOLDER:~0,-1%"
for %%A in ("%ROOT_FOLDER%") do set "FOLDER_NAME=%%~nxA"

if not defined DEVENV set "DEVENV=C:\DevEnv"
if not defined DEVTOOL set "DEVTOOL=C:\DevTool"
if not defined CARGO_HOME set "CARGO_HOME=%DEVENV%\cargo"
if not defined RUSTUP_HOME set "RUSTUP_HOME=%DEVENV%\rustup"
if not defined PUB_CACHE set "PUB_CACHE=%DEVENV%\pub-cache"
if not defined PIP_CACHE_DIR set "PIP_CACHE_DIR=%DEVENV%\pip-cache"
if not defined VCPKG_ROOT set "VCPKG_ROOT=%DEVENV%\vcpkg"
if not defined VCPKG_DEFAULT_TRIPLET set "VCPKG_DEFAULT_TRIPLET=x64-windows-static"
if not defined VCPKG_DEFAULT_HOST_TRIPLET set "VCPKG_DEFAULT_HOST_TRIPLET=x64-windows-static"
if not defined X_VCPKG_BINARY_CACHE set "X_VCPKG_BINARY_CACHE=%DEVENV%\vcpkg-binary-cache"
if not defined VCPKG_BINARY_SOURCES set "VCPKG_BINARY_SOURCES=clear;files,%DEVENV%\vcpkg-binary-cache,readwrite"
if not defined VCPKG_INSTALLED_ROOT set "VCPKG_INSTALLED_ROOT=%DEVENV%\vcpkg\installed"
if not defined LIBCLANG_PATH set "LIBCLANG_PATH=%DEVTOOL%\LLVM\bin"

set "PATH=%CARGO_HOME%\bin;%DEVENV%\flutter\bin;%VCPKG_ROOT%;%DEVTOOL%\Python;%DEVTOOL%\Python\Scripts;%DEVTOOL%\Git\cmd;%DEVTOOL%\Git\bin;%DEVTOOL%\LLVM\bin;%PATH%"

set "VCVARS=%DEVTOOL%\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VCVARS%" set "VCVARS=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"

echo ============================================================
echo CloudSend PC build script
echo ============================================================
echo [INFO] Source root : "%ROOT_FOLDER%"
echo [INFO] App name    : "%FOLDER_NAME%"
echo [INFO] DEVENV   : "%DEVENV%"
echo [INFO] DEVTOOL  : "%DEVTOOL%"
echo [INFO] VC vars     : "%VCVARS%"
echo.

if not exist "%VCVARS%" (
    echo [ERROR] vcvars64.bat is missing.
    echo [ERROR] Expected: "%VCVARS%"
    goto fail
)

echo [INFO] Calling Visual Studio Build Tools environment...
call "%VCVARS%"
if errorlevel 1 (
    echo [ERROR] Visual Studio Build Tools environment initialization failed.
    goto fail
)
echo [OK] Visual Studio Build Tools environment is ready.
echo.

:: vcvars64.bat can override VCPKG_ROOT to Visual Studio's bundled vcpkg.
:: CloudSend must stay pinned to the PC.md dependency cache under C:\DevEnv.
set "VCPKG_ROOT=%DEVENV%\vcpkg"
set "VCPKG_DEFAULT_TRIPLET=x64-windows-static"
set "VCPKG_DEFAULT_HOST_TRIPLET=x64-windows-static"
set "X_VCPKG_BINARY_CACHE=%DEVENV%\vcpkg-binary-cache"
set "VCPKG_BINARY_SOURCES=clear;files,%DEVENV%\vcpkg-binary-cache,readwrite"
set "VCPKG_INSTALLED_ROOT=%DEVENV%\vcpkg\installed"
set "LIBCLANG_PATH=%DEVTOOL%\LLVM\bin"
set "PATH=%CARGO_HOME%\bin;%DEVENV%\flutter\bin;%VCPKG_ROOT%;%DEVTOOL%\Python;%DEVTOOL%\Python\Scripts;%DEVTOOL%\Git\cmd;%DEVTOOL%\Git\bin;%DEVTOOL%\LLVM\bin;%PATH%"
if errorlevel 1 goto fail
echo [OK] CloudSend dependency roots were restored after vcvars64.
echo [INFO] VCPKG_ROOT          : "%VCPKG_ROOT%"
echo [INFO] VCPKG_INSTALLED_ROOT: "%VCPKG_INSTALLED_ROOT%"
echo.

if not exist "%ROOT_FOLDER%\.info" (
    echo [ERROR] .info file is missing: "%ROOT_FOLDER%\.info"
    goto fail
)

echo [INFO] .info file: "%ROOT_FOLDER%\.info"
for /f "usebackq eol=# tokens=1* delims==" %%i in ("%ROOT_FOLDER%\.info") do (
    if not "%%i"=="" (
        if not defined %%i set "%%i=%%j"
        echo   %%i=%%j
    )
)

if not defined VERSION set "VERSION=5.2.1"
if not defined ARCH set "ARCH=x64"

echo.
echo [INFO] Folder Name: "%FOLDER_NAME%"
echo [INFO] Version    : v%VERSION% (%ARCH%)
echo [INFO] Output dir : "%ROOT_FOLDER%\PC-Bulid"
echo.

:confirm
choice /c YN /n /t 10 /d Y /m "Please input Y to continue, or N to cancel. Auto-confirm in 10 seconds: "
if %errorlevel% equ 1 (
    goto continue
) else (
    echo [INFO] canceled
    exit /b 1
)

:continue
echo [INFO] confirmed
echo.

if not exist "%DEVENV%\flutter" (
    echo [ERROR] Missing Flutter root:
    echo         "%DEVENV%\flutter"
    goto fail
)
if not exist "%VCPKG_ROOT%" (
    echo [ERROR] Missing vcpkg root:
    echo         "%VCPKG_ROOT%"
    goto fail
)
if not exist "%VCPKG_ROOT%\vcpkg.exe" (
    echo [ERROR] Missing vcpkg.exe:
    echo         "%VCPKG_ROOT%\vcpkg.exe"
    goto fail
)
if not exist "%VCPKG_INSTALLED_ROOT%\x64-windows-static" (
    echo [ERROR] Missing vcpkg x64-windows-static install root:
    echo         "%VCPKG_INSTALLED_ROOT%\x64-windows-static"
    goto fail
)
if not exist "%LIBCLANG_PATH%\libclang.dll" (
    echo [ERROR] Missing libclang.dll:
    echo         "%LIBCLANG_PATH%\libclang.dll"
    goto fail
)
if not exist "%VCPKG_INSTALLED_ROOT%\x64-windows-static\include\libavcodec\avcodec.h" (
    echo [ERROR] Missing FFmpeg avcodec headers:
    echo         "%VCPKG_INSTALLED_ROOT%\x64-windows-static\include\libavcodec\avcodec.h"
    goto fail
)
if not exist "%VCPKG_INSTALLED_ROOT%\x64-windows-static\include\libavutil\attributes.h" (
    echo [ERROR] Missing FFmpeg avutil headers:
    echo         "%VCPKG_INSTALLED_ROOT%\x64-windows-static\include\libavutil\attributes.h"
    goto fail
)
if not exist "%VCPKG_INSTALLED_ROOT%\x64-windows-static\include\opus\opus_multistream.h" (
    echo [ERROR] Missing Opus multistream headers:
    echo         "%VCPKG_INSTALLED_ROOT%\x64-windows-static\include\opus\opus_multistream.h"
    goto fail
)
if not exist "%ROOT_FOLDER%\build.py" (
    echo [ERROR] Missing build.py:
    echo         "%ROOT_FOLDER%\build.py"
    goto fail
)
if not exist "%ROOT_FOLDER%\flutter\pubspec.yaml" (
    echo [ERROR] Missing flutter pubspec.yaml:
    echo         "%ROOT_FOLDER%\flutter\pubspec.yaml"
    goto fail
)
if not exist "%ROOT_FOLDER%\libs\portable\generate.py" (
    echo [ERROR] Missing portable generate.py:
    echo         "%ROOT_FOLDER%\libs\portable\generate.py"
    goto fail
)

where rustup >nul 2>nul
if errorlevel 1 (
    echo [ERROR] rustup was not found in PATH.
    goto fail
)
where cargo >nul 2>nul
if errorlevel 1 (
    echo [ERROR] cargo was not found in PATH.
    goto fail
)
where flutter >nul 2>nul
if errorlevel 1 (
    echo [ERROR] flutter was not found in PATH.
    goto fail
)
where python >nul 2>nul
if errorlevel 1 (
    echo [ERROR] python was not found in PATH.
    goto fail
)

echo.
echo ========== Step: 1. Pin Rust toolchain ==========
echo [COMMAND] rustup default 1.75.0-x86_64-pc-windows-msvc
rustup default 1.75.0-x86_64-pc-windows-msvc
if errorlevel 1 goto fail
echo [SUCCESS] 1. Pin Rust toolchain

echo.
echo ========== Step: 1.1 Ensure Windows MSVC target ==========
echo [COMMAND] rustup target add x86_64-pc-windows-msvc
rustup target add x86_64-pc-windows-msvc
if errorlevel 1 goto fail
echo [SUCCESS] 1.1 Ensure Windows MSVC target

set MAX_RETRY=1800
set retry_count=0

:check_loop
set "all_exist=1"
for %%f in (
    "%ROOT_FOLDER%\src\bridge_generated.rs"
    "%ROOT_FOLDER%\src\bridge_generated.io.rs"
    "%ROOT_FOLDER%\flutter\lib\generated_bridge.dart"
    "%ROOT_FOLDER%\flutter\lib\generated_bridge.freezed.dart"
) do (
    if not exist "%%~f" (
        set "all_exist=0"
        echo [WARN] Generated file missing: %%~nxf
    )
)

if !all_exist! equ 1 (
    echo [OK] Generated bridge files are ready.
    goto build_start
)

set /a "retry_count+=1"
if !retry_count! gtr !MAX_RETRY! (
    echo [ERROR] Generated bridge file wait timed out.
    goto fail
)

echo [INFO] Waiting for generated bridge files (!retry_count!/!MAX_RETRY!)...
timeout /t 1 /nobreak > nul
goto check_loop

:build_start
echo.
echo ========== Step: 2. Get Flutter dependencies ==========
echo [WORKDIR] "%ROOT_FOLDER%\flutter"
echo [COMMAND] flutter pub get
pushd "%ROOT_FOLDER%\flutter"
if errorlevel 1 goto fail
call flutter pub get
set "pub_get_code=!errorlevel!"
popd
if not "!pub_get_code!"=="0" goto fail
echo [SUCCESS] 2. Get Flutter dependencies

echo.
echo ========== Step: 3. Build CloudSend Windows release ==========
echo [WORKDIR] "%ROOT_FOLDER%"
echo [COMMAND] python .\build.py --portable --hwcodec --flutter --vram --skip-portable-pack
pushd "%ROOT_FOLDER%"
if errorlevel 1 goto fail
python .\build.py --portable --hwcodec --flutter --vram --skip-portable-pack
set "build_code=!errorlevel!"
popd
if not "!build_code!"=="0" goto fail
echo [SUCCESS] 3. Build CloudSend Windows release

set "RELEASE_DIR=%ROOT_FOLDER%\flutter\build\windows\x64\runner\Release"
set "STAGING_DIR=%ROOT_FOLDER%\cloudsend"
set "OUTPUT_DIR=%ROOT_FOLDER%\PC-Bulid"
set "FINAL_EXE=%OUTPUT_DIR%\%FOLDER_NAME%.exe"
set "PORTABLE_DIR=%ROOT_FOLDER%\libs\portable"

if not exist "%RELEASE_DIR%" (
    echo [ERROR] Missing Flutter Windows Release:
    echo         "%RELEASE_DIR%"
    goto fail
)
if not exist "%RELEASE_DIR%\cloudsend.exe" (
    echo [ERROR] Missing cloudsend.exe:
    echo         "%RELEASE_DIR%\cloudsend.exe"
    goto fail
)
if not exist "%RELEASE_DIR%\dylib_virtual_display.dll" (
    echo [ERROR] Missing dylib_virtual_display.dll:
    echo         "%RELEASE_DIR%\dylib_virtual_display.dll"
    goto fail
)

if exist "%STAGING_DIR%" rmdir /s /q "%STAGING_DIR%"
echo.
echo ========== Step: 4. Move Release files ==========
echo [COMMAND] robocopy "%RELEASE_DIR%" "%STAGING_DIR%" /E /MOVE
robocopy "%RELEASE_DIR%" "%STAGING_DIR%" /E /MOVE
set "robocopy_release_code=!errorlevel!"
if !robocopy_release_code! gtr 7 goto fail
echo [SUCCESS] 4. Move Release files

set "dll_file=%DEVENV%\third-party\RustDeskTempTopMostWindow\WindowInjection\x64\Release\WindowInjection.dll"
if not exist "%dll_file%" set "dll_file=%ROOT_FOLDER%\WindowInjection.dll"
if not exist "%dll_file%" set "dll_file=%ROOT_FOLDER%\..\WindowInjection.dll"
if exist "%dll_file%" (
    copy /Y "%dll_file%" "%STAGING_DIR%\WindowInjection.dll" >nul
    echo [INFO] WindowInjection.dll copied.
) else (
    echo [ERROR] WindowInjection.dll not found.
    echo [ERROR] Expected one of:
    echo         "%DEVENV%\third-party\RustDeskTempTopMostWindow\WindowInjection\x64\Release\WindowInjection.dll"
    echo         "%ROOT_FOLDER%\WindowInjection.dll"
    echo         "%ROOT_FOLDER%\..\WindowInjection.dll"
    goto fail
)

set "usbmmidd_dir=%DEVENV%\downloads\rustdesk-drivers\usbmmidd_v2"
if exist "%usbmmidd_dir%" (
    robocopy "%usbmmidd_dir%" "%STAGING_DIR%\usbmmidd_v2" /E >nul
    if errorlevel 8 (
        echo [ERROR] usbmmidd_v2 copy failed.
        goto fail
    ) else (
        echo [INFO] usbmmidd_v2 copied.
    )
) else (
    echo [ERROR] usbmmidd_v2 not found:
    echo         "%usbmmidd_dir%"
    goto fail
)

set "driver_root=%DEVENV%\downloads\rustdesk-drivers"
set "driver_dir=%driver_root%\rustdesk_printer_driver_v4-1.4"
if not exist "%driver_dir%" set "driver_dir=%driver_root%\rustdesk_printer_driver_v4"
if exist "%driver_dir%" (
    if not exist "%STAGING_DIR%\drivers" mkdir "%STAGING_DIR%\drivers"
    robocopy "%driver_dir%" "%STAGING_DIR%\drivers\RustDeskPrinterDriver" /E >nul
    if errorlevel 8 (
        echo [ERROR] printer driver copy failed.
        goto fail
    ) else (
        echo [INFO] printer driver copied.
    )
) else (
    echo [ERROR] printer driver not found under:
    echo         "%driver_root%"
    goto fail
)
if exist "%driver_root%\printer_driver_adapter.dll" (
    copy /Y "%driver_root%\printer_driver_adapter.dll" "%STAGING_DIR%\printer_driver_adapter.dll" >nul
    echo [INFO] printer_driver_adapter.dll copied.
) else (
    echo [ERROR] printer_driver_adapter.dll not found under:
    echo         "%driver_root%"
    goto fail
)

set "runner_res=%ROOT_FOLDER%\flutter\build\windows\x64\runner\cloudsend.dir\Release\Runner.res"
set "runner_res_backup=0"
if exist "%PORTABLE_DIR%\Runner.res" set "runner_res_backup=1"
if exist "%runner_res%" (
    copy /Y "%runner_res%" "%PORTABLE_DIR%\Runner.res" >nul
    echo [INFO] Runner.res copied.
) else (
    echo [WARN] Runner.res was not found at the standard CloudSend release path.
    echo [WARN] Portable EXE can still build, but version resources may be incomplete.
)

set "MANIFEST_FILE=%ROOT_FOLDER%\res\manifest.xml"
set "MANIFEST_BACKUP=%ROOT_FOLDER%\res\manifest.pc.bak.xml"
if not exist "%MANIFEST_FILE%" (
    echo [ERROR] Missing res manifest.xml:
    echo         "%MANIFEST_FILE%"
    goto fail
)
copy /Y "%MANIFEST_FILE%" "%MANIFEST_BACKUP%" >nul
if errorlevel 1 goto fail

echo.
echo ========== Step: 5. Remove dpiAware from portable manifest ==========
echo [COMMAND] powershell -NoProfile -Command remove dpiAware lines
powershell -NoProfile -Command "$p = '%MANIFEST_FILE%'; $lines = [System.IO.File]::ReadAllLines($p); $filtered = New-Object 'System.Collections.Generic.List[string]'; foreach ($line in $lines) { if ($line -notmatch 'dpiAware') { [void]$filtered.Add($line) } }; [System.IO.File]::WriteAllLines($p, $filtered.ToArray(), (New-Object System.Text.UTF8Encoding($false)))"
if errorlevel 1 goto restore_manifest_fail
echo [SUCCESS] 5. Remove dpiAware from portable manifest

:: Re-pin packager paths before the final portable stage.
:: This keeps the packaging hand-off deterministic even after nested CMD/PowerShell calls.
set "STAGING_DIR=%ROOT_FOLDER%\cloudsend"
set "OUTPUT_DIR=%ROOT_FOLDER%\PC-Bulid"
set "FINAL_EXE=%OUTPUT_DIR%\%FOLDER_NAME%.exe"
set "PORTABLE_DIR=%ROOT_FOLDER%\libs\portable"

if not exist "%PORTABLE_DIR%" (
    echo [ERROR] Missing portable packer workdir:
    echo         "%PORTABLE_DIR%"
    goto restore_manifest_fail
)
if not exist "%STAGING_DIR%" (
    echo [ERROR] Missing portable staging folder:
    echo         "%STAGING_DIR%"
    goto restore_manifest_fail
)
if not exist "%STAGING_DIR%\cloudsend.exe" (
    echo [ERROR] Missing portable startup executable:
    echo         "%STAGING_DIR%\cloudsend.exe"
    goto restore_manifest_fail
)

echo.
echo ========== Step: 6. Install portable packer Python requirements ==========
echo [WORKDIR] "%PORTABLE_DIR%"
echo [COMMAND] python -m pip install -r requirements.txt
pushd "%PORTABLE_DIR%"
if errorlevel 1 goto restore_manifest_fail
python -m pip install -r requirements.txt
set "portable_requirements_code=!errorlevel!"
popd
if not "!portable_requirements_code!"=="0" goto restore_manifest_fail
echo [SUCCESS] 6. Install portable packer Python requirements

echo.
echo ========== Step: 7. Generate portable self-extract EXE ==========
echo [WORKDIR] "%PORTABLE_DIR%"
echo [COMMAND] python .\generate.py -f "%STAGING_DIR%" -o . -e "%STAGING_DIR%\cloudsend.exe"
pushd "%PORTABLE_DIR%"
if errorlevel 1 goto restore_manifest_fail
python .\generate.py -f "%STAGING_DIR%" -o . -e "%STAGING_DIR%\cloudsend.exe"
set "portable_pack_code=!errorlevel!"
popd
if not "!portable_pack_code!"=="0" (
    echo [FAILED] 7. Generate portable self-extract EXE
    echo [FAILED] Error code: !portable_pack_code!
    goto restore_manifest_fail
)
echo [SUCCESS] 7. Generate portable self-extract EXE

if exist "%MANIFEST_BACKUP%" move /Y "%MANIFEST_BACKUP%" "%MANIFEST_FILE%" >nul

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
if not exist "%ROOT_FOLDER%\target\release\cloudsend-portable-packer.exe" (
    echo [ERROR] Missing cloudsend-portable-packer.exe:
    echo         "%ROOT_FOLDER%\target\release\cloudsend-portable-packer.exe"
    goto fail
)
move /Y "%ROOT_FOLDER%\target\release\cloudsend-portable-packer.exe" "%FINAL_EXE%" >nul
if errorlevel 1 goto fail

if exist "%PORTABLE_DIR%\data.bin" del /q "%PORTABLE_DIR%\data.bin"
if exist "%PORTABLE_DIR%\app_metadata.toml" del /q "%PORTABLE_DIR%\app_metadata.toml"
if "%runner_res_backup%"=="0" if exist "%PORTABLE_DIR%\Runner.res" del /q "%PORTABLE_DIR%\Runner.res"

echo.
echo [INFO] Unpacked staging folder retained:
echo        "%STAGING_DIR%"
echo [INFO] PC self-extract EXE:
echo        "%FINAL_EXE%"
echo [INFO] Output directory:
echo        "%OUTPUT_DIR%"
echo.
echo [INFO] Press Enter to close now, or wait 60 seconds for automatic close.
powershell -NoProfile -Command "$deadline = (Get-Date).AddSeconds(60); try { while ((Get-Date) -lt $deadline) { if ([Console]::KeyAvailable) { $key = [Console]::ReadKey($true); if ($key.Key -eq [ConsoleKey]::Enter) { exit 0 } }; Start-Sleep -Milliseconds 100 } } catch { Start-Sleep -Seconds 60 }"
exit /b 0

:restore_manifest_fail
if exist "%MANIFEST_BACKUP%" move /Y "%MANIFEST_BACKUP%" "%MANIFEST_FILE%" >nul
goto fail

:fail
echo.
echo [FAILED] new-build.cmd stopped. Review the last visible step above.
pause
exit /b 1

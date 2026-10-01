@echo off
chcp 65001 > nul

call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"

rustup default 1.75.0-x86_64-pc-windows-msvc
rustup target add x86_64-pc-windows-msvc

setlocal enabledelayedexpansion

set "ROOT_FOLDER=%CD%"
echo [INFO] root folder: "%ROOT_FOLDER%"

:: if not exist "%ROOT_FOLDER%\.info" (
    :: echo [ERROR] .info file is missing: "%ROOT_FOLDER%\"
    :: pause
    :: exit /b 1
:: )

:: for %%A in ("%ROOT_FOLDER%") do set "FOLDER_NAME=%%~nxA"

:: for /f "usebackq tokens=1* delims==" %%i in ("%ROOT_FOLDER%\.info") do (
    :: if not "%%i"=="FOLDER_NAME" (
        :: if not defined %%i (
            :: set "%%i=%%j"
            :: echo   %%i=%%j
        :: )
    :: )
:: )

if exist "%ROOT_FOLDER%\.info" (
    echo [INFO] .info file: "%ROOT_FOLDER%\.info"
) else (
    
    if exist "..\.info" (
        set "ROOT_FOLDER=%CD%\.."
        
    ) else (
        echo [ERROR] .info file is missing:
        pause
        exit /b 1
    )
)

:: for %%A in ("%ROOT_FOLDER%") do set "FOLDER_NAME=%%~nxA"



for /f "usebackq tokens=1* delims==" %%i in ("%ROOT_FOLDER%\.info") do (
    if not "%%i"=="FOLDER_NAME" (
        if not defined %%i (
            set "%%i=%%j"
            echo   %%i=%%j
        )
    )
)

set "ROOT_FOLDER=%CD%"
for %%A in ("%ROOT_FOLDER%") do set "FOLDER_NAME=%%~nxA"


if not defined VERSION set "VERSION=5.2.1"
if not defined ARCH set "ARCH=x64"

echo [INFO] Folder Name: "%FOLDER_NAME%"
echo [INFO] Version: v%VERSION% (%ARCH%)"


:confirm
echo.
choice /c YN /n /t 10 /d Y /m "please input（Y）to continue，or input N to cancel："
if %errorlevel% equ 1 (
goto continue
) else (
echo [INFO] canceled
exit /b 1
)

:continue
echo [INFO] confirmed


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
        echo [WARN] file missed: %%~nxf
    )
)

if !all_exist! equ 1 (
    echo [OK] All files are ready
    goto build_start
)

set /a "retry_count+=1"
if %retry_count% gtr %MAX_RETRY% (
    echo [ERROR] timed out!
    pause
    exit /b 1
)

echo [INFO] Waiting for the file to be generated（%retry_count%/%MAX_RETRY%）...
timeout /t 1 /nobreak > nul
goto check_loop

:build_start


call :exec_step "1. Cleaning Flutter" "cd /d "%ROOT_FOLDER%\flutter" && (if exist pubspec.yaml flutter clean || echo [ERROR] Flutter does not exist)"
call :exec_step "2. Get dependencies" "cd /d "%ROOT_FOLDER%\flutter" && (if exist pubspec.yaml flutter pub get || echo [ERROR] Flutter does not exist)"
call :exec_step "3. Preserve Cargo target" "cd /d "%ROOT_FOLDER%" && echo Keeping target directory for incremental builds"
:: call :exec_step "4. Build program" "cd /d "%ROOT_FOLDER%" && python .\build.py --portable --flutter --skip-portable-pack"
call :exec_step "4. Build program" "cd /d "%ROOT_FOLDER%" && python .\build.py --portable --hwcodec --flutter --vram --skip-portable-pack"


if exist "%ROOT_FOLDER%\cloudsend" (
    
    rmdir /s /q "%ROOT_FOLDER%\cloudsend" 2>nul
)

set "RELEASE_DIR=%ROOT_FOLDER%\flutter\build\windows\x64\runner\Release"
if not exist "%RELEASE_DIR%" (
    echo [ERROR] The Release directory does not exist: "%RELEASE_DIR%"
    pause
    exit /b 1
)

call :exec_step "5. Move build files" "robocopy "%RELEASE_DIR%" "%ROOT_FOLDER%\cloudsend" /E /MOVE" "0 1"
if %errorlevel% gtr 1 (
    echo [ERROR] Failed: %errorlevel%
	pause
    exit /b 1
)


set "runner_res=%ROOT_FOLDER%\flutter\build\windows\x64\runner\cloudsend.dir\Release\Runner.res"
if exist "%runner_res%" (
    if not exist "%ROOT_FOLDER%\libs\portable\Runner.res" (
        call :exec_step "7. Copy Runner.res" "copy /Y "%runner_res%" "%ROOT_FOLDER%\libs\portable\""
    ) else (
        echo [INFO] Skipping copy
    )
) else (
    echo [WARN] File not found！
)


set "DLL_FILE=%ROOT_FOLDER%\..\WindowInjection.dll"
if exist "%DLL_FILE%" (
    call :exec_step "8. Copy File" "copy /Y "%DLL_FILE%" "%ROOT_FOLDER%\cloudsend\""
) else (
    echo [WARN] File not found "%DLL_FILE%"
)

if exist "D:\DaXian\Driver.ps1" (
    powershell -ExecutionPolicy Bypass -File "D:\DaXian\Driver.ps1"
) else (
    echo [WARN] Driver script not found "D:\DaXian\Driver.ps1", skipping
)


copy res\manifest.xml res\manifest_backup.xml || (
    echo [ERROR] backup manifest.xml fail
    exit /b 1
)

powershell -Command "(Get-Content res\manifest.xml) | Where-Object { $_ -notmatch 'dpiAware' } | Set-Content res\manifest.xml"

pushd libs\portable

pip3 install -r requirements.txt || (
    echo Dependency installation failed
    exit /b 1
)

python generate.py -f ../../cloudsend/ -o . -e ../../cloudsend/cloudsend.exe || (
    echo Failed to generate configuration
    exit /b 1
)

popd


if not exist ZClient mkdir ZClient


copy /Y target\release\cloudsend-portable-packer.exe ZClient\%FOLDER_NAME%.exe

del res\manifest.xml
ren res\manifest_backup.xml manifest.xml || (
    echo [ERROR] backup failed
    exit /b 1
)


del libs\portable\Runner.res 2>nul || (
    echo [WARNING] failed
)


del printer_driver_adapter.zip 2>nul || (
    echo [WARNING] failed
)

del rustdesk_printer_driver_v4.zip 2>nul || (
    echo [WARNING] failed
)

del rustdesk_printer_driver_v4.zip 2>nul || (
    echo [WARNING] failed
)

del usbmmidd_v2.zip 2>nul || (
    echo [WARNING] failed
)

if exist "target\" (
    echo [INFO] Keeping target folder: "%ROOT_FOLDER%\target"
) else (
    echo [WARNING] The target folder does not exist.
)



echo [SUCCESS]PC-Client File: "%ROOT_FOLDER%\ZClient\%FOLDER_NAME%.exe"
:: pause
exit /b


:exec_step
setlocal enabledelayedexpansion
echo.
echo ========== Step: %~1 ==========
echo [COMMAND] %~2


set "allowed_errors=0"
if "%~3" neq "" set "allowed_errors=%~3"


cmd /c "%~2 >nul 2>&1"
set "error_code=!errorlevel!"


echo !allowed_errors! | findstr /C:"!error_code!" >nul
if !errorlevel! equ 0 (
    echo [SUCCESS] Executed successfully
    endlocal
    exit /b 0
)

if !error_code! neq 0 (
    echo [FAILED] Failed: "%~1"
    echo Error: !error_code!
    echo [DEBUG] ZClient:
    cmd /c "%~2"
    set /p "choice=Do you want to continue? (Y/N): "
    if /i "!choice!"=="n" exit /b 1
    echo [WARN] Choos to continue...
)
endlocal
goto :eof

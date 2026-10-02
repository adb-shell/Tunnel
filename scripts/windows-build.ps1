# Formal Windows x64 build host only. CMD initializes the VS toolchain first.
[CmdletBinding()]
param(
    [string]$SourceRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$ReleaseDir,
    [switch]$PackageOnly,
    [switch]$Offline,
    [switch]$Pause
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = (Resolve-Path -LiteralPath $SourceRoot).Path
$output = Join-Path $root 'PC-Bulid'
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$stage = Join-Path $output ('staging\' + $runId)
$log = Join-Path $output ('logs\' + $runId + '.log')
$exitCode = 1
$transcribing = $false
$buildLock = $null

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    Write-Host ('[RUN] ' + $Command + ' ' + ($Arguments -join ' '))
    # Windows PowerShell 5.1 treats some native stderr as nonterminating errors.
    # Preserve it in the log and use the process exit code as the oracle.
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Command @Arguments
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    if ($code -ne 0) { throw "$Command failed with exit code $code" }
}

function Require-File([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf) -or (Get-Item -LiteralPath $Path).Length -eq 0) {
        throw "Required file is missing or empty: $Path"
    }
}

function Find-Release {
    if ($ReleaseDir) {
        $candidate = (Resolve-Path -LiteralPath $ReleaseDir).Path
        Require-File (Join-Path $candidate 'tunnel.exe')
        return $candidate
    }
    # New Flutter uses windows/x64; older Flutter uses windows/runner.
    $candidates = @(@(
        (Join-Path $root 'flutter\build\windows\x64\runner\Release'),
        (Join-Path $root 'flutter\build\windows\runner\Release')
    ) | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'tunnel.exe') -PathType Leaf })
    if ($candidates.Count -ne 1) {
        throw 'Expected one Windows Release. If both layouts exist, select the current one with -ReleaseDir.'
    }
    return $candidates[0]
}

try {
    New-Item -ItemType Directory -Path (Split-Path -Parent $log) -Force | Out-Null
    Start-Transcript -LiteralPath $log | Out-Null
    $transcribing = $true
    # OS-held lock; automatically released even if the build host process exits.
    $buildLock = [System.IO.File]::Open((Join-Path $output 'windows-build.lock'),
        [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    Write-Host "[INFO] Source: $root"
    Write-Host "[INFO] Log: $log"
    $info = Join-Path $root '.info'
    if (Test-Path -LiteralPath $info -PathType Leaf) {
        foreach ($line in (Get-Content -LiteralPath $info)) {
            if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
                $name = $Matches[1]; $value = $Matches[2]
                if ($name -notin @('HOME', 'CODEX_HOME', 'PATH', 'COMSPEC', 'PATHEXT', 'PSMODULEPATH', 'SYSTEMROOT', 'TEMP', 'TMP') -and
                    ![System.Environment]::GetEnvironmentVariable($name, 'Process')) {
                    [System.Environment]::SetEnvironmentVariable($name, $value, 'Process')
                    Write-Host "[INFO] Imported build setting: $name"
                }
            }
        }
    }
    if ($Offline) {
        $env:CARGO_NET_OFFLINE = 'true'; $env:TUNNEL_BUILD_OFFLINE = '1'
        if (!(Get-Command rustup -ErrorAction SilentlyContinue)) { throw 'Offline mode requires an installed Rust toolchain.' }
        $installed = @(& rustup toolchain list)
        if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect installed offline Rust toolchains.' }
        $selected = $env:RUSTUP_TOOLCHAIN
        $matching = @($installed | Where-Object {
            $name = ($_ -split '\s+')[0]
            $name -eq $selected -or $name.StartsWith($selected + '-')
        })
        if ($matching.Count -eq 0) { throw "Offline Rust toolchain is not installed: $selected" }
    }
    if ($env:CARGO_TARGET_DIR -and ![System.IO.Path]::IsPathRooted($env:CARGO_TARGET_DIR)) {
        $env:CARGO_TARGET_DIR = [System.IO.Path]::GetFullPath((Join-Path $root $env:CARGO_TARGET_DIR))
    }
    if ($env:CARGO_BUILD_TARGET -and $env:CARGO_BUILD_TARGET -ne 'x86_64-pc-windows-msvc') {
        throw 'This entry builds Windows x64 MSVC; CARGO_BUILD_TARGET selects a different architecture.'
    }
    foreach ($tool in @('cargo', 'python', 'rc.exe')) {
        if (!(Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Tool not found: $tool" }
    }
    if (!$PackageOnly) {
        if ($ReleaseDir) { $env:TUNNEL_WINDOWS_RELEASE_DIR = [System.IO.Path]::GetFullPath($ReleaseDir) }
        foreach ($tool in @('rustup', 'flutter')) {
            if (!(Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Tool not found: $tool" }
        }
        foreach ($file in @(
            "$env:LIBCLANG_PATH\libclang.dll",
            "$env:VCPKG_INSTALLED_ROOT\x64-windows-static\include\libavcodec\avcodec.h",
            "$env:VCPKG_INSTALLED_ROOT\x64-windows-static\include\opus\opus_multistream.h",
            (Join-Path $root 'src\bridge_generated.rs'),
            (Join-Path $root 'src\bridge_generated.io.rs'),
            (Join-Path $root 'flutter\lib\generated_bridge.dart'),
            (Join-Path $root 'flutter\lib\generated_bridge.freezed.dart')
        )) { Require-File $file }
        if (!$Offline) {
            Invoke-Checked 'rustup' @('target', 'add', 'x86_64-pc-windows-msvc', '--toolchain', $env:RUSTUP_TOOLCHAIN)
        }
        Push-Location (Join-Path $root 'flutter')
        try {
            $pubArgs = @('pub', 'get')
            if ($Offline) { $pubArgs += '--offline' }
            Invoke-Checked 'flutter' $pubArgs
        } finally { Pop-Location }
        Push-Location $root
        try {
            Invoke-Checked 'python' @('build.py', '--portable', '--hwcodec', '--flutter', '--vram', '--skip-portable-pack')
        } finally { Pop-Location }
    }

    $release = Find-Release
    foreach ($file in @('tunnel.exe', 'tunnel.dll', 'flutter_windows.dll', 'dylib_virtual_display.dll', 'data\icudtl.dat', 'data\app.so')) {
        Require-File (Join-Path $release $file)
    }
    if (!(Test-Path -LiteralPath (Join-Path $release 'data\flutter_assets') -PathType Container)) {
        throw 'Release has no data/flutter_assets directory.'
    }
    New-Item -ItemType Directory -Path $stage | Out-Null
    Write-Host "[STEP] Collect Release: $release -> $stage"
    # Robocopy returns 0..7 for successful results, not just 0/1.
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & robocopy $release $stage /E /COPY:DAT /DCOPY:DAT /R:2 /W:2 /XJ /NP
        $copyCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    if ($copyCode -gt 7) { throw "Release copy failed (robocopy $copyCode)" }
    Require-File (Join-Path $stage 'tunnel.exe')

    $cache = if ($env:TUNNEL_WINDOWS_ASSET_CACHE) { $env:TUNNEL_WINDOWS_ASSET_CACHE } else {
        Join-Path $env:DEVENV 'downloads\rustdesk-drivers'
    }
    $assetArgs = @((Join-Path $PSScriptRoot 'windows_assets.py'), '--stage', $stage, '--cache', $cache, '--root', $root)
    if ($Offline) { $assetArgs += '--offline' }
    if ($env:TUNNEL_WINDOW_INJECTION_DLL) { $assetArgs += @('--injection-dll', $env:TUNNEL_WINDOW_INJECTION_DLL) }
    Invoke-Checked 'python' $assetArgs

    $portable = Join-Path $root 'libs\portable'
    Push-Location $portable
    try {
        $pipArgs = @('-m', 'pip', 'install', '-r', 'requirements.txt')
        if ($Offline) { $pipArgs += '--no-index' }
        Invoke-Checked 'python' $pipArgs
        $folderName = Split-Path -Leaf $root
        $final = Join-Path $output ($folderName + '.exe')
        # Retain the previous successful artifact before replacing it.
        if (Test-Path -LiteralPath $final -PathType Leaf) {
            Copy-Item -LiteralPath $final -Destination ($final + '.' + $runId + '.bak')
        }
        foreach ($suffix in @('.payload.json', '.build.json', '.sha256')) {
            if (Test-Path -LiteralPath ($final + $suffix) -PathType Leaf) {
                Copy-Item -LiteralPath ($final + $suffix) -Destination ($final + $suffix + '.' + $runId + '.bak')
            }
        }
        $packArgs = @('generate.py', '-f', $stage, '-o', $portable, '-e', (Join-Path $stage 'tunnel.exe'), '--dist', $final)
        foreach ($required in @('tunnel.dll', 'flutter_windows.dll', 'data/app.so', 'data/icudtl.dat', 'data/flutter_assets',
            'dylib_virtual_display.dll', 'WindowInjection.dll', 'usbmmidd_v2/usbmmIdd.inf',
            'drivers/RustDeskPrinterDriver/RustDeskPrinterDriver.inf', 'printer_driver_adapter.dll', 'windows-assets.json')) {
            $packArgs += @('--require', $required)
        }
        if ($env:CARGO_BUILD_TARGET) { $packArgs += @('--target', $env:CARGO_BUILD_TARGET) }
        Invoke-Checked 'python' $packArgs
        Require-File $final
        Require-File ($final + '.payload.json')
        $hash = (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash.ToLowerInvariant()
        ($hash + '  ' + (Split-Path -Leaf $final)) | Set-Content -LiteralPath ($final + '.sha256') -Encoding ascii
        [ordered]@{
            format = 1; createdAt = (Get-Date).ToString('o'); sourceRoot = $root;
            release = $release; staging = $stage; executable = $final; sha256 = $hash;
            packageOnly = [bool]$PackageOnly; offline = [bool]$Offline; log = $log
        } | ConvertTo-Json | Set-Content -LiteralPath ($final + '.build.json') -Encoding UTF8
        Write-Host "[SUCCESS] Self-extract EXE: $final"
        Write-Host "[SUCCESS] SHA256: $hash"
        Write-Host "[INFO] Unpacked runtime and driver directory: $stage"
        $exitCode = 0
    } finally { Pop-Location }
} catch {
    Write-Host ('[FAILED] ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host "[INFO] Retained staging: $stage"
    Write-Host "[INFO] Log: $log"
} finally {
    if ($transcribing) { Stop-Transcript | Out-Null }
    if ($buildLock) { $buildLock.Dispose() }
    if ($Pause) { Read-Host 'Press Enter to close' | Out-Null }
}
exit $exitCode

#!/usr/bin/env python3
"""Supply fixed upstream Windows assets on the formal build host; never install drivers."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import struct
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile

MAX_DOWNLOAD = 64 * 1024 * 1024
MAX_EXTRACTED = 256 * 1024 * 1024
WINDOWS_RESERVED_NAMES = {'CON', 'PRN', 'AUX', 'NUL'} | {
    f'{prefix}{index}' for prefix in ('COM', 'LPT') for index in range(1, 10)}


def sha256(path):
    with path.open('rb') as stream:
        digest = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def acquired(spec, cache, offline):
    expected = spec.get('sha256')
    if spec['name'] == 'usbmmidd_v2.zip':
        expected = os.environ.get('TUNNEL_USBMMIDD_SHA256') or expected
    if expected and not re.fullmatch('[a-fA-F0-9]{64}', expected):
        raise ValueError('Expected SHA256 must contain 64 hexadecimal characters')
    key = expected or str(spec.get('asset_id') or spec.get('commit'))
    path = cache / (key + '-' + spec['name'])

    def verify(candidate):
        if not candidate.is_file() or candidate.stat().st_size == 0:
            return False
        if spec.get('size') and candidate.stat().st_size != spec['size']:
            return False
        if spec['name'].endswith('.zip') and not zipfile.is_zipfile(candidate):
            return False
        return not expected or sha256(candidate).lower() == expected.lower()

    # Older builds cached usbmmidd under its release asset ID before its digest
    # was pinned. Reuse those bytes only after the new full digest check, so a
    # repaired PackageOnly/Offline run need not download the same package again.
    legacy_key = spec.get('asset_id') or spec.get('commit')
    if expected and legacy_key and not path.exists():
        legacy = cache / (str(legacy_key) + '-' + spec['name'])
        if verify(legacy):
            shutil.copy2(legacy, path)
    if path.exists() and not verify(path):
        raise ValueError(f'Cached asset is invalid; preserve/rename it before retry: {path}')
    if not path.exists():
        if offline:
            raise ValueError(f'Offline cache is missing: {path}')
        failure = None
        # No shell/downloader command composition; urllib honors HTTPS_PROXY.
        for attempt in range(3):
            try:
                with tempfile.NamedTemporaryFile(prefix=path.name + '.', suffix='.part', dir=cache, delete=False) as temp:
                    pending = Path(temp.name)
                request = urllib.request.Request(spec['url'], headers={'User-Agent': 'Tunnel-Windows-Build/1'})
                print(f'Download {spec["name"]} (attempt {attempt + 1}/3)', flush=True)
                with urllib.request.urlopen(request, timeout=90) as response, pending.open('wb') as stream:
                    size = 0
                    for chunk in iter(lambda: response.read(1024 * 1024), b''):
                        size += len(chunk)
                        if size > MAX_DOWNLOAD:
                            raise ValueError('Asset exceeds the download size limit')
                        stream.write(chunk)
                if spec.get('size') and size != spec['size']:
                    raise ValueError(f'Unexpected asset size: {spec["name"]}')
                if expected and sha256(pending).lower() != expected.lower():
                    raise ValueError(f'Upstream SHA256 mismatch: {spec["name"]}')
                if spec['name'].endswith('.zip') and not zipfile.is_zipfile(pending):
                    raise ValueError(f'Upstream returned an invalid ZIP: {spec["name"]}')
                os.replace(pending, path)
                failure = None
                break
            except (OSError, ValueError) as error:
                failure = error
                if attempt < 2:
                    time.sleep(2 * (attempt + 1))
        if failure:
            raise RuntimeError(f'Download failed: {spec["url"]}: {failure}')
    return path, {'url': spec['url'], 'assetId': spec.get('asset_id'), 'commit': spec.get('commit'),
                  'sha256': sha256(path), 'upstreamDigestVerified': bool(expected)}


def extract(archive, cache):
    destination = Path(tempfile.mkdtemp(prefix='extract-', dir=cache)).resolve()
    seen = set()
    with zipfile.ZipFile(archive) as bundle:
        infos = bundle.infolist()
        if len(infos) > 10000 or sum(item.file_size for item in infos) > MAX_EXTRACTED:
            raise ValueError('ZIP exceeds extraction limits')
        for item in infos:
            relative = PurePosixPath(item.filename.replace('\\', '/'))
            if (relative.is_absolute() or '..' in relative.parts or ':' in item.filename
                    or stat.S_ISLNK(item.external_attr >> 16)
                    or any(p.endswith(('.', ' ')) or p.split('.')[0].upper() in
                           WINDOWS_RESERVED_NAMES for p in relative.parts)):
                raise ValueError('Unsafe ZIP path or symlink')
            normalized = str(relative).casefold()
            if normalized in seen:
                raise ValueError('Duplicate ZIP entry')
            seen.add(normalized)
            target = destination.joinpath(*relative.parts)
            target.resolve().relative_to(destination)
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with bundle.open(item) as source, target.open('wb') as output:
                    shutil.copyfileobj(source, output)
    return destination


def unique_file(folder, name, exclude_32bit=False):
    files = [p for p in folder.rglob('*') if p.is_file() and p.name.lower() == name.lower()
             and (not exclude_32bit or not {'win32', 'x86', 'i386'}.intersection(
                 part.lower() for part in p.relative_to(folder).parts))]
    if len(files) != 1:
        raise ValueError(f'Expected exactly one {name}, got {len(files)} in {folder}')
    return files[0]


def require_suffix(folder, suffix):
    if not any(p.is_file() and p.suffix.lower() == suffix for p in folder.rglob('*')):
        raise ValueError(f'Driver package lacks {suffix}: {folder}')


def require_x64_pe(path, dll=False):
    with path.open('rb') as stream:
        header = stream.read(64)
        if len(header) < 64 or header[:2] != b'MZ':
            raise ValueError(f'Not a Windows PE file: {path}')
        stream.seek(struct.unpack_from('<I', header, 60)[0])
        pe = stream.read(24)
        if len(pe) != 24 or pe[:4] != b'PE\0\0' or struct.unpack_from('<H', pe, 4)[0] != 0x8664:
            raise ValueError(f'PE must be Windows x64: {path}')
        if dll and not struct.unpack_from('<H', pe, 22)[0] & 0x2000:
            raise ValueError(f'PE is not marked as a DLL: {path}')


def require_x64_dll(path):
    require_x64_pe(path, dll=True)


def require_amyuni_x64(folder):
    # usbmmidd_v2 is a UMDF driver. WUDFRd.sys in its INF is supplied by
    # Windows; the vendor payload is x64/usbmmIdd.dll, not a vendor .sys.
    # Keep the INF's x64 source directory intact and validate every file used
    # by our runtime installer, rather than accepting any DLL/CAT in the ZIP.
    required = ('usbmmIdd.inf', 'usbmmidd.cat', 'x64/usbmmIdd.dll',
                'deviceinstaller64.exe', 'License.txt')
    for name in required:
        path = folder / name
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f'Amyuni x64 package lacks required file: {name}')
    require_x64_dll(folder / 'x64/usbmmIdd.dll')
    require_x64_pe(folder / 'deviceinstaller64.exe')


def copy_tree(source, destination, exclude_32bit=False):
    destination.mkdir(parents=True, exist_ok=True)
    for path in source.rglob('*'):
        if path.is_symlink():
            raise ValueError('Symlink is not allowed in staged driver files')
        if not path.is_file():
            continue
        relative = path.relative_to(source)
        if exclude_32bit and ('win32' in [p.lower() for p in relative.parts]
                             or path.name.lower() in {'deviceinstaller.exe', 'usbmmidd.bat'}):
            continue
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)


def injection(spec, options, cache):
    devenv = Path(os.environ.get('DEVENV', r'C:\DevEnv'))
    candidates = []
    if options.injection_dll:
        explicit = Path(options.injection_dll).resolve(strict=True)
        require_x64_dll(explicit)
        return explicit, {'source': 'explicit-owner-supplied', 'sha256': sha256(explicit)}
    candidates += [options.stage / 'WindowInjection.dll',
                   devenv / 'third-party/RustDeskTempTopMostWindow/WindowInjection/x64/Release/WindowInjection.dll',
                   options.root / 'WindowInjection.dll', options.root.parent / 'WindowInjection.dll']
    for path in candidates:
        if path.is_file():
            require_x64_dll(path)
            return path, {'source': 'local-owner-supplied', 'sha256': sha256(path)}
    archive, provenance = acquired(spec, cache, options.offline)
    source = extract(archive, cache)
    project = unique_file(source, 'WindowInjection.vcxproj')
    msbuild = shutil.which('msbuild.exe') or shutil.which('msbuild')
    if not msbuild:
        raise ValueError('WindowInjection.dll missing; source build needs MSBuild or TUNNEL_WINDOW_INJECTION_DLL')
    # The pinned upstream vcxproj requests v142 (VS2019). The canonical Tunnel
    # host initializes VS2022/v143, which does not necessarily install v142.
    # Select the initialized compiler rather than failing with MSB8020 halfway
    # through packaging; an explicit toolset remains available to asset owners.
    toolset = os.environ.get('TUNNEL_WINDOW_INJECTION_TOOLSET')
    if not toolset:
        vs_major = os.environ.get('VisualStudioVersion', '').split('.')[0]
        toolset = {'17': 'v143', '16': 'v142', '15': 'v141'}.get(vs_major)
        if not toolset:
            compiler = os.environ.get('VCToolsVersion', '')
            if compiler.startswith(('14.3', '14.4')):
                toolset = 'v143'
            elif compiler.startswith('14.2'):
                toolset = 'v142'
    if toolset and not re.fullmatch(r'v[0-9]{3}', toolset):
        raise ValueError('TUNNEL_WINDOW_INJECTION_TOOLSET must be a VS toolset such as v143')
    output = project.parent / 'x64/Release'
    intermediate = project.parent / 'x64/Intermediate'
    arguments = [msbuild, str(project), '/m', '/p:Configuration=Release', '/p:Platform=x64',
                 '/p:TargetVersion=Windows10', '/p:OutDir=' + str(output) + os.sep,
                 '/p:IntDir=' + str(intermediate) + os.sep]
    if toolset:
        arguments.append('/p:PlatformToolset=' + toolset)
    print('Build WindowInjection with toolset ' + (toolset or 'upstream project default'), flush=True)
    subprocess.run(arguments, cwd=project.parent.parent, check=True, timeout=600)
    dll = unique_file(output, 'WindowInjection.dll')
    require_x64_dll(dll)
    provenance['source'] = 'fixed-commit-source-built'
    provenance['platformToolset'] = toolset or 'upstream-project-default'
    provenance['dllSha256'] = sha256(dll)
    return dll, provenance


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--injection-dll')
    options = parser.parse_args()
    options.stage = options.stage.resolve(strict=True)
    options.root = options.root.resolve(strict=True)
    cache = options.cache.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    if cache == options.stage or options.stage in cache.parents or cache in options.stage.parents:
        raise ValueError('Asset cache and staging directory must be separate')
    lock_path = Path(__file__).with_name('windows-assets.lock.json')
    lock = json.loads(lock_path.read_text(encoding='utf-8'))
    require_x64_pe(options.stage / 'tunnel.exe')
    for name in ('tunnel.dll', 'flutter_windows.dll', 'dylib_virtual_display.dll'):
        require_x64_dll(options.stage / name)
    archives, sources = {}, {}
    for kind in ('usbmmidd', 'printer', 'adapter', 'checksums'):
        archives[kind], sources[kind] = acquired(lock[kind], cache, options.offline)
    # Require both the pinned digest and the upstream checksum table to agree.
    checksum_text = archives['checksums'].read_text(encoding='utf-8-sig')
    for kind in ('printer', 'adapter'):
        matches = re.findall(r'^([a-fA-F0-9]{64})\s+\*?' + re.escape(lock[kind]['name']) + r'\s*$', checksum_text, re.M)
        if len(matches) != 1 or matches[0].lower() != sources[kind]['sha256']:
            raise ValueError(f'Upstream checksum table disagrees for {kind}')
    usb = unique_file(extract(archives['usbmmidd'], cache), 'usbmmIdd.inf', exclude_32bit=True).parent
    require_amyuni_x64(usb)
    copy_tree(usb, options.stage / 'usbmmidd_v2', exclude_32bit=True)
    require_amyuni_x64(options.stage / 'usbmmidd_v2')
    printer = unique_file(extract(archives['printer'], cache), 'RustDeskPrinterDriver.inf').parent
    require_suffix(printer, '.cat')
    copy_tree(printer, options.stage / 'drivers/RustDeskPrinterDriver')
    adapter = unique_file(extract(archives['adapter'], cache), 'printer_driver_adapter.dll')
    require_x64_dll(adapter)
    shutil.copy2(adapter, options.stage / adapter.name)
    dll, sources['injection'] = injection(lock['injection_source'], options, cache)
    target = options.stage / 'WindowInjection.dll'
    if dll.resolve() != target.resolve():
        shutil.copy2(dll, target)
    files = []
    for path in options.stage.rglob('*'):
        if path.is_file() and (path.name in {'WindowInjection.dll', 'printer_driver_adapter.dll'}
                              or 'usbmmidd_v2' in path.parts or 'RustDeskPrinterDriver' in path.parts):
            files.append({'path': path.relative_to(options.stage).as_posix(), 'sha256': sha256(path)})
    receipt = {'format': 1, 'lockSha256': sha256(lock_path), 'sources': sources, 'files': files,
               'note': 'Files staged only; driver installation/signature/OS validation is a separate server/device check.'}
    (options.stage / 'windows-assets.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('Windows driver and injection assets staged successfully', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        print(f'Windows asset preparation failed: {error}', file=sys.stderr)
        sys.exit(1)

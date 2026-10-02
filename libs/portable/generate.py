#!/usr/bin/env python3
"""Build a portable payload and copy the actual Cargo artifact to its destination."""
import argparse
import datetime
import hashlib
import json
import mmap
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

import brotli

# Keep in sync with IDENTIFIER in src/bin_reader.rs.
PACKAGE_MARKER = b'tunnel'
MAX_FIELD_LENGTH = (1 << 32) - 1


def contained_file(folder, value):
    supplied = Path(value)
    # Accept both absolute paths and the historical folder-relative CLI spelling.
    candidate = supplied if supplied.is_absolute() else folder / supplied
    if not candidate.is_file():
        candidate = supplied.resolve()
    candidate = candidate.resolve(strict=True)
    candidate.relative_to(folder)
    if not candidate.is_file():
        raise ValueError('Startup executable must be a file inside the staging folder')
    return candidate


def checked_relative(value):
    relative = Path(value)
    if relative.is_absolute() or '..' in relative.parts:
        raise ValueError('Required payload paths must be relative to the staging folder')
    return relative


def write_payload(folder, output_folder, executable, level, required):
    for name in required:
        target = folder / checked_relative(name)
        target.resolve(strict=True).relative_to(folder)
        if (not target.exists() or (target.is_file() and target.stat().st_size == 0)
                or (target.is_dir() and not any(p.is_file() and p.stat().st_size > 0 for p in target.rglob('*')))):
            raise ValueError(f'Required payload is missing or empty: {name}')
    entries = sorted(p for p in folder.rglob('*') if p.is_file())
    if not entries:
        raise ValueError('Staging folder is empty')
    output_path = output_folder / 'data.bin'
    pending = output_folder / 'data.bin.new'
    records = []
    with pending.open('wb') as stream:
        stream.write(PACKAGE_MARKER)
        for path in entries:
            if path.is_symlink():
                raise ValueError(f'Symlink is not accepted in the payload: {path.name}')
            path.resolve(strict=True).relative_to(folder)
            name = './' + path.relative_to(folder).as_posix()
            name_bytes = name.encode('utf-8')
            content = path.read_bytes()
            compressed = brotli.compress(content, quality=level)
            if max(len(name_bytes), len(compressed)) > MAX_FIELD_LENGTH:
                raise ValueError(f'Payload entry exceeds the format limit: {name}')
            stream.write(len(name_bytes).to_bytes(4, 'big'))
            stream.write(name_bytes)
            stream.write(len(compressed).to_bytes(4, 'big'))
            stream.write(compressed)
            stream.write(hashlib.md5(content).hexdigest().encode('ascii'))
            records.append({'path': name, 'size': len(content),
                            'sha256': hashlib.sha256(content).hexdigest()})
            print(f'Packed {name} ({len(content)} bytes)', flush=True)
        stream.write(PACKAGE_MARKER)
        stream.write(('./' + executable.relative_to(folder).as_posix()).encode('utf-8'))
    os.replace(pending, output_path)
    (output_folder / 'app_metadata.toml').write_text(
        f'timestamp = {int(datetime.datetime.now().timestamp() * 1000)}\n', encoding='utf-8')
    (output_folder / 'payload-manifest.json').write_text(
        json.dumps({'format': 1, 'marker': PACKAGE_MARKER.decode(),
                    'executable': executable.relative_to(folder).as_posix(),
                    'files': records}, indent=2) + '\n', encoding='utf-8')
    return output_path


def portable_manifest(output_folder):
    """Remove the extractor's DPI declaration without modifying the app manifest."""
    source = output_folder.parent.parent / 'res/manifest.xml'
    ET.register_namespace('', 'urn:schemas-microsoft-com:asm.v1')
    ET.register_namespace('asmv3', 'urn:schemas-microsoft-com:asm.v3')
    tree = ET.parse(source)
    for parent in tree.iter():
        for child in list(parent):
            if child.tag.rsplit('}', 1)[-1] in ('dpiAware', 'dpiAwareness'):
                parent.remove(child)
    destination = output_folder / 'portable-manifest.xml'
    tree.write(destination, encoding='utf-8', xml_declaration=True)
    return destination


def build_portable(output_folder, target):
    command = ['cargo', 'build', '--manifest-path', str(output_folder / 'Cargo.toml'),
               '--package', 'tunnel-portable-packer', '--bin', 'tunnel-portable-packer',
               '--release', '--message-format=json-render-diagnostics', '--color', 'never']
    if target:
        command.extend(['--target', target])
    print('Building tunnel-portable-packer', flush=True)
    artifact = None
    environment = os.environ.copy()
    if os.name == 'nt':
        environment['TUNNEL_PORTABLE_MANIFEST'] = str(portable_manifest(output_folder))
    with subprocess.Popen(command, cwd=output_folder, stdout=subprocess.PIPE,
                          text=True, encoding='utf-8', errors='replace', env=environment) as process:
        for line in process.stdout:
            try:
                message = json.loads(line)
            except json.JSONDecodeError:
                print(line, end='', flush=True)
                continue
            if message.get('reason') == 'compiler-message':
                rendered = message.get('message', {}).get('rendered')
                if rendered:
                    print(rendered, end='', file=sys.stderr, flush=True)
            elif (message.get('reason') == 'compiler-artifact'
                  and message.get('target', {}).get('name') == 'tunnel-portable-packer'
                  and 'bin' in message.get('target', {}).get('kind', [])
                  and message.get('executable')):
                artifact = Path(message['executable']).resolve()
        return_code = process.wait()
    if return_code != 0:
        raise RuntimeError(f'Cargo portable build failed (exit {return_code})')
    if artifact is None or not artifact.is_file():
        raise RuntimeError('Cargo did not report an existing portable executable')
    return artifact


def verify_embedded_payload(artifact, payload):
    """Reject a stale packer or a plain runner even if its size looks plausible."""
    digest = hashlib.sha256()
    with payload.open('rb') as stream:
        needle = stream.read(64)
        stream.seek(0)
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    size = payload.stat().st_size
    expected = digest.digest()
    with artifact.open('rb') as stream, mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as image:
        offset = image.find(needle)
        while offset >= 0:
            if offset + size <= len(image):
                with memoryview(image)[offset:offset + size] as region:
                    if hashlib.sha256(region).digest() == expected:
                        return digest.hexdigest()
            offset = image.find(needle, offset + 1)
    raise RuntimeError('Portable EXE does not embed the exact current payload; refusing to publish a stale/plain EXE')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('-f', '--folder', default='./tunnel')
    parser.add_argument('-o', '--output', dest='output_folder', default='.')
    parser.add_argument('-e', '--executable', default='tunnel.exe')
    parser.add_argument('-t', '--target')
    parser.add_argument('-l', '--level', type=int, default=6)
    parser.add_argument('--dist', help='Copy the built executable here (honors CARGO_TARGET_DIR)')
    parser.add_argument('--require', action='append', default=[],
                        help='Require a staged file/directory, repeat for each driver asset')
    options = parser.parse_args()
    if not 0 <= options.level <= 11:
        parser.error('Compression level must be between 0 and 11')
    folder = Path(options.folder).resolve(strict=True)
    output_folder = Path(options.output_folder).resolve(strict=True)
    if not folder.is_dir() or not (output_folder / 'Cargo.toml').is_file():
        parser.error('Use an existing staging folder and the portable Cargo project directory')
    # Output must not be recursively packed as an input.
    if output_folder == folder or folder in output_folder.parents:
        parser.error('The portable project directory must be outside the staging folder')
    executable = contained_file(folder, options.executable)
    payload = write_payload(folder, output_folder, executable, options.level, options.require)
    artifact = build_portable(output_folder, options.target)
    if artifact.stat().st_size < payload.stat().st_size:
        raise RuntimeError('Reported executable is too small to contain the current payload')
    if os.name == 'nt':
        with artifact.open('rb') as stream:
            if stream.read(2) != b'MZ':
                raise RuntimeError('Portable artifact does not have a Windows PE header')
        payload_hash = verify_embedded_payload(artifact, payload)
        manifest_path = output_folder / 'payload-manifest.json'
        manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
        manifest['payloadSha256'] = payload_hash
        manifest['payloadSize'] = payload.stat().st_size
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    if options.dist:
        destination = Path(options.dist).resolve()
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination != artifact:
            pending = destination.with_suffix(destination.suffix + '.new')
            shutil.copy2(artifact, pending)
            os.replace(pending, destination)
        shutil.copy2(output_folder / 'payload-manifest.json',
                     destination.with_suffix(destination.suffix + '.payload.json'))
        print(f'Portable output: {destination}', flush=True)
    else:
        print(f'Portable output: {artifact}', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, ET.ParseError, subprocess.SubprocessError) as error:
        print(f'Portable packaging failed: {error}', file=sys.stderr)
        sys.exit(1)

#!/usr/bin/env python3
"""Stage pinned LADB bytes from the vendored source, with network fallback. Never executes adb."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import tempfile
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = Path(__file__).with_name("ladb-prebuilt.lock.json")
NATIVE = ROOT / "flutter/android/app/src/main/jniLibs"
ASSETS = ROOT / "flutter/android/app/src/main/assets/adb-provenance"
VENDORED = ROOT / "third_party/ladb"


def blob_sha1(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def verify(data, entry):
    if len(data) != entry["size"] or blob_sha1(data) != entry["gitBlobSha1"]:
        raise ValueError("pinned upstream blob mismatch: " + entry["path"])
    if "elfClass" in entry:
        if (len(data) < 64 or data[:4] != b"\x7fELF" or data[4] != entry["elfClass"]
                or data[5] != 1 or struct.unpack_from("<H", data, 16)[0] != 3
                or struct.unpack_from("<H", data, 18)[0] != entry["elfMachine"]):
            raise ValueError("expected Android PIE/ABI mismatch: " + entry["path"])


def contained(path, root):
    resolved = path.resolve()
    if ROOT not in root.resolve().parents or root.resolve() not in resolved.parents:
        raise ValueError("artifact destination escaped expected directory")
    return path


def write_atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".adb-stage-", delete=False) as output:
        temporary = Path(output.name)
        output.write(data)
        output.flush()
        os.fsync(output.fileno())
    try:
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def acquire(path, entry, commit, offline):
    if path.exists():
        if not path.is_file() or path.is_symlink():
            raise ValueError("refusing non-regular artifact destination")
        data = path.read_bytes()
        verify(data, entry)  # Do not silently replace an existing different local binary.
        return data
    vendored = VENDORED / entry["path"]
    if vendored.exists():
        if not vendored.is_file() or vendored.is_symlink():
            raise ValueError("refusing non-regular vendored ADB asset")
        vendored.resolve().relative_to(VENDORED.resolve())
        data = vendored.read_bytes()
        verify(data, entry)
        write_atomic(path, data)
        return data
    if offline:
        raise ValueError("offline artifact missing: " + entry["path"])
    url = "https://raw.githubusercontent.com/tytydraco/LADB/" + commit + "/" + entry["path"]
    request = urllib.request.Request(url, headers={"User-Agent": "Tunnel-pinned-adb-acquisition/1"})
    with urllib.request.urlopen(request, timeout=45) as response:
        if urllib.parse.urlparse(response.geturl()).hostname != "raw.githubusercontent.com":
            raise ValueError("unexpected artifact redirect")
        data = response.read(entry["size"] + 1)
    verify(data, entry)
    write_atomic(path, data)
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--abi", action="append", choices=["arm64-v8a", "armeabi-v7a", "x86", "x86_64"], required=True)
    parser.add_argument("--offline", action="store_true", help="use staged or vendored pinned bytes, never download")
    args = parser.parse_args()
    lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
    if lock["schema"] != 1 or len(lock["commit"]) != 40:
        raise ValueError("unsupported source lock")
    receipt = {"schema": 1, "repository": lock["repository"], "commit": lock["commit"],
               "artifactKind": lock["artifactKind"], "artifacts": {}, "notices": {}}
    # Preserve receipts for other already staged ABIs, but revalidate every byte before keeping it.
    abis = set(args.abi)
    abis.update(abi for abi in lock["artifacts"] if (NATIVE / abi / "libadb.so").is_file())
    for abi in sorted(abis):
        entry = lock["artifacts"][abi]
        path = contained(NATIVE / abi / "libadb.so", NATIVE)
        data = acquire(path, entry, lock["commit"], args.offline)
        path.chmod(0o755)
        receipt["artifacts"][abi] = {"gitBlobSha1": entry["gitBlobSha1"], "size": len(data),
                                       "sha256": hashlib.sha256(data).hexdigest()}
    for name, entry in lock["notices"].items():
        data = acquire(contained(ASSETS / name, ASSETS), entry, lock["commit"], args.offline)
        receipt["notices"][name] = {"gitBlobSha1": entry["gitBlobSha1"], "sha256": hashlib.sha256(data).hexdigest()}
    write_atomic(contained(ASSETS / "manifest.json", ASSETS),
                 (json.dumps(receipt, indent=2, sort_keys=True) + "\n").encode("utf-8"))
    print("Pinned libadb.so and notices prepared for: " + ", ".join(sorted(abis)))
    print("Prebuilt acquisition only; source-build reproducibility and device execution are not verified.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        raise SystemExit("ADB preparation failed: " + str(error)) from None

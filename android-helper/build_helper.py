#!/usr/bin/env python3
"""Explicit, offline helper build with verified, recoverable staging; never downloads tools."""

import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
import zipfile

UPSTREAM_COMMIT = "2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0"
ENTRY_POINT = "com.tunnel.adbhelper.Server"
ROOT = Path(__file__).resolve().parent
REPOSITORY = ROOT.parent


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def within(path, parent):
    if os.path.commonpath((str(path.resolve()), str(parent.resolve()))) != str(parent.resolve()):
        raise ValueError("Output path leaves its expected repository directory")


def required_file(value, label):
    path = Path(value).expanduser().resolve(strict=True)
    if not path.is_file():
        raise ValueError(label + " must be a file")
    return path


def check_jar(path, required_entry):
    with zipfile.ZipFile(path) as archive:
        if required_entry not in archive.namelist():
            raise ValueError("Tool input lacks required entry: " + required_entry)


def deterministic_jar(path, entries):
    with path.open("xb") as output:
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, source in sorted(entries):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                archive.writestr(info, source.read_bytes())


def run(arguments, cwd):
    # Argument arrays deliberately bypass the shell; supplied tools remain trusted build inputs.
    subprocess.run([str(value) for value in arguments], cwd=str(cwd), check=True, timeout=600)


def stage(jar, manifest):
    assets = REPOSITORY / "flutter" / "android" / "app" / "src" / "main" / "assets"
    within(assets, REPOSITORY)
    assets.mkdir(parents=True, exist_ok=True)
    destination = assets / "adb-mirror-p0"
    lock = assets / ".adb-mirror-stage.lock"
    temporary = assets / (".adb-mirror-next-" + uuid.uuid4().hex)
    backup = assets / (".adb-mirror-previous-" + uuid.uuid4().hex)

    def validate_directory(directory):
        within(directory, assets)
        if directory.is_symlink() or not directory.is_dir():
            raise ValueError("Helper stage must be a real directory")
        if {item.name for item in directory.iterdir()} != {"helper.jar", "manifest.json"}:
            raise ValueError("Helper stage contains unexpected files; retain it for review")
        for name in ("helper.jar", "manifest.json"):
            path = directory / name
            if path.is_symlink() or not path.is_file():
                raise ValueError("Helper stage contains a non-regular file")
        data = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
        if (data.get("schema") != 1 or data.get("protocol") not in (1, 2)
                or data.get("implementation") != "tunnel-local-adb-p0"
                or data.get("entryPoint") != ENTRY_POINT
                or data.get("sha256") != sha256(directory / "helper.jar")
                or data.get("size") != (directory / "helper.jar").stat().st_size):
            raise ValueError("Existing helper stage is not a verified compatible artifact")

    def remove_verified_directory(directory):
        # Only these two validated files are owned here. Never recursively remove a path.
        validate_directory(directory)
        (directory / "helper.jar").unlink()
        (directory / "manifest.json").unlink()
        directory.rmdir()

    # Exclusive staging prevents concurrent builds mixing jar and manifest generations.
    with lock.open("x", encoding="ascii") as reservation:
        reservation.write(str(os.getpid()) + "\n")
    try:
        if destination.exists() or destination.is_symlink():
            validate_directory(destination)
        temporary.mkdir(exist_ok=False)
        for source, name in ((jar, "helper.jar"), (manifest, "manifest.json")):
            with source.open("rb") as incoming, (temporary / name).open("xb") as outgoing:
                shutil.copyfileobj(incoming, outgoing)
        validate_directory(temporary)
        if sha256(temporary / "helper.jar") != sha256(jar):
            raise ValueError("Staged helper hash mismatch; retain temporary directory for review")
        if destination.exists():
            destination.rename(backup)
        try:
            temporary.rename(destination)
        except OSError:
            if backup.exists() and not destination.exists():
                backup.rename(destination)
            raise
        if backup.exists():
            remove_verified_directory(backup)
        return destination
    finally:
        lock.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-jar", required=True, help="Android SDK platform android-34/android.jar")
    parser.add_argument("--d8-jar", required=True, help="Existing Android SDK build-tools lib/d8.jar")
    parser.add_argument("--jdk-bin", required=True, help="Explicit JDK bin directory (JDK 17 recommended)")
    parser.add_argument("--stage", action="store_true", help="Verify and atomically replace the generated Android helper assets")
    args = parser.parse_args()

    android_jar = required_file(args.android_jar, "android.jar")
    d8_jar = required_file(args.d8_jar, "d8.jar")
    lambda_stubs = required_file(d8_jar.parent.parent / "core-lambda-stubs.jar",
                                 "build-tools core-lambda-stubs.jar")
    jdk = Path(args.jdk_bin).expanduser().resolve(strict=True)
    suffix = ".exe" if os.name == "nt" else ""
    java = required_file(jdk / ("java" + suffix), "java")
    javac = required_file(jdk / ("javac" + suffix), "javac")
    properties = required_file(android_jar.parent / "source.properties", "SDK source.properties")
    platform = {}
    for line in properties.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            platform[key.strip()] = value.strip()
    if platform.get("AndroidVersion.ApiLevel") != "34":
        raise ValueError("P0 requires the explicit Android SDK API 34 platform")
    check_jar(android_jar, "android/media/MediaCodec.class")
    check_jar(d8_jar, "com/android/tools/r8/D8.class")
    check_jar(lambda_stubs, "java/lang/invoke/LambdaMetafactory.class")
    # android.jar alone omits the javac bootstrap API for Java 8 lambdas.
    # Compile against the SDK stubs; D8 desugars lambdas without packaging them.
    bootclasspath = os.pathsep.join((str(android_jar), str(lambda_stubs)))

    source_roots = (ROOT / "protocol/src/main/java", ROOT / "server/src/main/java")
    sources = sorted(path for directory in source_roots for path in directory.rglob("*.java"))
    if not sources or not any(path.name == "Server.java" for path in sources):
        raise ValueError("Missing P0 protocol/server sources")
    for source in sources:
        within(source, ROOT)
    source_hashes = {source.relative_to(ROOT).as_posix(): sha256(source) for source in sources}
    resources = [("META-INF/" + name, required_file(ROOT / "server" / name, name))
                 for name in ("LICENSE.scrcpy", "NOTICE", "PROVENANCE.md")]
    for _, resource in resources:
        within(resource, ROOT)
    resource_hashes = {path.relative_to(ROOT).as_posix(): sha256(path) for _, path in resources}
    tree_digest = hashlib.sha256(json.dumps(source_hashes, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    tool_paths = dict((
        ("androidJar", android_jar), ("androidPlatformProperties", properties),
        ("d8Jar", d8_jar), ("coreLambdaStubsJar", lambda_stubs),
        ("java", java), ("javac", javac)))
    release = jdk.parent / "release"
    if release.is_file():
        tool_paths["jdkRelease"] = release
    tools = {name: {"file": path.name, "sha256": sha256(path)} for name, path in tool_paths.items()}
    script_hash = sha256(Path(__file__).resolve())

    output_root = ROOT / "out"
    within(output_root, ROOT)
    output_root.mkdir(exist_ok=True)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = output_root / ("p0-" + stamp + "-" + uuid.uuid4().hex[:12])
    output.mkdir(exist_ok=False)
    classes, dex = output / "classes", output / "dex"
    classes.mkdir(); dex.mkdir()
    # Relative Java paths keep the javac argument file independent of host path/encoding.
    arguments = output / "sources.args"
    arguments.write_text("\n".join('"' + source.relative_to(ROOT).as_posix() + '"' for source in sources) + "\n",
                         encoding="utf-8")
    run((javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-proc:none",
         "-bootclasspath", bootclasspath, "-d", classes, "@" + str(arguments)), ROOT)
    class_files = [(path.relative_to(classes).as_posix(), path) for path in classes.rglob("*.class")]
    if "com/tunnel/adbhelper/Server.class" not in {name for name, _ in class_files}:
        raise ValueError("javac did not produce the P0 entry point")
    compiled_jar = output / "classes.jar"
    deterministic_jar(compiled_jar, class_files)
    run((java, "-cp", d8_jar, "com.android.tools.r8.D8", "--release", "--min-api", "30",
         "--lib", android_jar, "--output", dex, compiled_jar), ROOT)
    dex_files = sorted(dex.glob("*.dex"))
    if [path.name for path in dex_files] != ["classes.dex"]:
        raise ValueError("Expected exactly one classes.dex")
    jar = output / "helper.jar"
    deterministic_jar(jar, [("classes.dex", dex_files[0])] + resources)
    # Reject a mixed source snapshot if a concurrent editor changed files during compilation.
    if (sorted(path for directory in source_roots for path in directory.rglob("*.java")) != sources
            or any(sha256(path) != source_hashes[path.relative_to(ROOT).as_posix()] for path in sources)):
        raise ValueError("Source changed during build; output retained but cannot be staged")
    if (any(sha256(path) != tools[name]["sha256"] for name, path in tool_paths.items())
            or sha256(Path(__file__).resolve()) != script_hash
            or any(sha256(path) != resource_hashes[path.relative_to(ROOT).as_posix()] for _, path in resources)):
        raise ValueError("Build input changed during build; output retained but cannot be staged")
    manifest = output / "manifest.json"
    data = {"schema": 1, "protocol": 2, "sha256": sha256(jar), "size": jar.stat().st_size,
            "upstreamCommit": UPSTREAM_COMMIT, "entryPoint": ENTRY_POINT,
            "implementation": "tunnel-local-adb-p0", "sourceTreeSha256": tree_digest,
            "sourceFiles": source_hashes, "resourceFiles": resource_hashes, "toolInputs": tools,
            "build": {"javaSource": 8, "javaTarget": 8, "androidCompileApi": 34, "minApi": 30,
                      "scriptSha256": script_hash}}
    with manifest.open("x", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, sort_keys=True, indent=2)
        handle.write("\n")
    print("P0 helper artifact: " + str(jar))
    print("SHA-256: " + data["sha256"])
    if args.stage:
        print("P0 assets staged: " + str(stage(jar, manifest)))
    print("Build only: no APK build, installation, device probe, signing or remote publication performed.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        print("P0 helper build failed: " + str(error), file=sys.stderr)
        sys.exit(1)

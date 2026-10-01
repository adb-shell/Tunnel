#!/usr/bin/env bash
set -Eeuo pipefail

# ===== CloudSend Android 本地构建脚本 =====
# 最终成品输出:
# - flutter/build/app/outputs/flutter-apk/app-aarch64-release.apk
# - flutter/build/app/outputs/flutter-apk/app-universal-release.apk

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"
cd "$REPO_ROOT"

# ===== 全局工具链路径 =====
: "${CLOUDSEND_TOOLCHAIN_ROOT:=/opt/rustdesk-toolchain}"
: "${FLUTTER_HOME:=$CLOUDSEND_TOOLCHAIN_ROOT/flutter}"
: "${ANDROID_SDK_ROOT:=$CLOUDSEND_TOOLCHAIN_ROOT/android-sdk}"
: "${ANDROID_HOME:=$ANDROID_SDK_ROOT}"
: "${ANDROID_NDK_HOME:=$ANDROID_SDK_ROOT/ndk/27.2.12479018}"
: "${ANDROID_NDK_ROOT:=$ANDROID_NDK_HOME}"
: "${VCPKG_ROOT:=$CLOUDSEND_TOOLCHAIN_ROOT/vcpkg}"
: "${VCPKG_DEFAULT_BINARY_CACHE:=$CLOUDSEND_TOOLCHAIN_ROOT/cache/vcpkg}"
: "${JAVA_HOME:=/usr/lib/jvm/java-17-openjdk-amd64}"

detect_android_build_tools_dir() {
  local bt_root="$ANDROID_SDK_ROOT/build-tools"
  [[ -d "$bt_root" ]] || return 1
  find "$bt_root" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n1
}

refresh_path() {
  local bt=""
  bt="$(detect_android_build_tools_dir || true)"
  if [[ -n "$bt" ]]; then
    export PATH="$HOME/.cargo/bin:$FLUTTER_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$bt:$JAVA_HOME/bin:$PATH"
  else
    export PATH="$HOME/.cargo/bin:$FLUTTER_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$JAVA_HOME/bin:$PATH"
  fi
}
refresh_path

export ANDROID_SDK_ROOT ANDROID_HOME ANDROID_NDK_HOME ANDROID_NDK_ROOT
export VCPKG_ROOT VCPKG_DEFAULT_BINARY_CACHE JAVA_HOME

# ===== 签名配置 =====
SIGN_ENV_DEFAULT="/opt/rustdesk-toolchain/signing/android/signing.env"
SIGN_ENV="${CLOUDSEND_SIGN_ENV:-$SIGN_ENV_DEFAULT}"

# ===== 构建参数 =====
RELTYPE="release"
OUT_DIR="$REPO_ROOT/out/android"
TMP_DIR="$REPO_ROOT/out/tmp"
FLUTTER_APK_DIR="$REPO_ROOT/flutter/build/app/outputs/flutter-apk"
mkdir -p "$OUT_DIR" "$TMP_DIR" "$FLUTTER_APK_DIR"

BUILD_MODE=""
AUTO_BRIDGE=1
SKIP_FLUTTER_PATCH=0
SKIP_DEPS=0
SKIP_BRIDGE_CHECK=0
VERBOSE=0
FLUTTER_PUB_GOT=0

# ===== 彩色输出 =====
if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'
  C_BOLD=$'\033[1m'
  C_DIM=$'\033[2m'
  C_RED=$'\033[31m'
  C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'
  C_BLUE=$'\033[34m'
  C_MAGENTA=$'\033[35m'
  C_CYAN=$'\033[36m'
else
  C_RESET="" C_BOLD="" C_DIM="" C_RED="" C_GREEN="" C_YELLOW="" C_BLUE="" C_MAGENTA="" C_CYAN=""
fi

ts() { date '+%F %T'; }
info() { echo "[$(ts)] ${C_BLUE}$*${C_RESET}"; }
ok()   { echo "[$(ts)] ${C_GREEN}$*${C_RESET}"; }
warn() { echo "[$(ts)] ${C_YELLOW}[WARN] $*${C_RESET}" >&2; }
err()  { echo "[$(ts)] ${C_RED}[ERR ] $*${C_RESET}" >&2; }
die()  { err "$*"; exit 1; }
section() { echo; echo "[$(ts)] ${C_BOLD}${C_MAGENTA}==== $* ====${C_RESET}"; }

run() {
  if [[ "$VERBOSE" -eq 1 ]]; then
    echo "[$(ts)] ${C_DIM}+ $*${C_RESET}"
  fi
  "$@"
}

require_cmd()  { command -v "$1" >/dev/null 2>&1 || die "Missing command: $1"; }
require_file() { [[ -f "$1" ]] || die "Missing file: $1"; }
require_dir()  { [[ -d "$1" ]] || die "Missing directory: $1"; }

# ===== CRLF 提示 =====
show_crlf_hint_if_needed() {
  if LC_ALL=C grep -q $'\r' "$0" 2>/dev/null; then
    warn "检测到脚本包含 CRLF 行尾, 请执行: sed -i 's/\r$//' \"$0\""
    warn "或执行: dos2unix \"$0\""
  fi
}

usage() {
  cat <<'USAGE'
Usage:
  ./build.sh [OPTION]

Options:
  1 | aarch64            Build arm64-v8a signed APK
  2 | universal          Build universal signed APK (arm64 + armv7 + x86_64)
  -m, --mode <mode>      mode = aarch64 | universal
  --no-bridge            Disable auto bridge generation
  --skip-bridge-check    Skip bridge check/generation
  --skip-flutter-patch   Skip Flutter patch
  --skip-deps            Skip flutter/build_android_deps.sh
  -v, --verbose          Verbose logs
  -h, --help             Show help
USAGE
}

parse_args() {
  if [[ $# -eq 0 ]]; then
    echo
    echo "请选择构建选项:"
    echo "  (1) aarch64-signed.apk"
    echo "  (2) universal-signed.apk"
    echo
    read -r -p "输入选项 [1/2]: " choice
    case "$choice" in
      1) BUILD_MODE="aarch64" ;;
      2) BUILD_MODE="universal" ;;
      *) die "无效选项: $choice" ;;
    esac
    return
  fi

  while [[ $# -gt 0 ]]; do
    case "$1" in
      1|aarch64) BUILD_MODE="aarch64"; shift ;;
      2|universal) BUILD_MODE="universal"; shift ;;
      -m|--mode) [[ $# -ge 2 ]] || die "Missing value for $1"; BUILD_MODE="$2"; shift 2 ;;
      --no-bridge) AUTO_BRIDGE=0; shift ;;
      --skip-bridge-check) SKIP_BRIDGE_CHECK=1; shift ;;
      --skip-flutter-patch) SKIP_FLUTTER_PATCH=1; shift ;;
      --skip-deps) SKIP_DEPS=1; shift ;;
      -v|--verbose) VERBOSE=1; shift ;;
      -h|--help) usage; exit 0 ;;
      *) die "Unknown argument: $1" ;;
    esac
  done

  case "${BUILD_MODE:-}" in
    aarch64|universal) ;;
    *) die "BUILD_MODE 未指定或无效, 请使用 1/2 或 --mode aarch64|universal" ;;
  esac
}

# ===== 基础检查 =====
check_repo_layout() {
  require_dir "$REPO_ROOT/flutter"
  require_dir "$REPO_ROOT/src"
  require_file "$REPO_ROOT/Cargo.toml"
  require_file "$REPO_ROOT/flutter/pubspec.yaml"
  require_file "$REPO_ROOT/flutter/build_android_deps.sh"
  require_file "$REPO_ROOT/flutter/ndk_arm64.sh"

  if [[ "$BUILD_MODE" == "universal" ]]; then
    require_file "$REPO_ROOT/flutter/ndk_arm.sh"
    require_file "$REPO_ROOT/flutter/ndk_x64.sh"
  fi
}

ensure_git_safe_directory() {
  local d="$1"
  [[ -d "$d" ]] || return 0
  if [[ -d "$d/.git" ]]; then
    git config --global --add safe.directory "$d" >/dev/null 2>&1 || true
  fi
}

ensure_flutter_sdk_health() {
  section "检查 Flutter SDK 状态"

  ensure_git_safe_directory "$FLUTTER_HOME"

  local out=""
  out="$(flutter --version 2>&1 || true)"
  echo "$out" | head -n 6

  [[ -n "$out" ]] || die "flutter --version 无输出, 请检查 FLUTTER_HOME/PATH"
  if echo "$out" | grep -q '0\.0\.0-unknown'; then
    die "Flutter SDK 状态异常 (0.0.0-unknown), 请检查 safe.directory 或 Flutter SDK 完整性"
  fi

  ok "Flutter SDK 状态正常"
}

check_global_env() {
  section "检查全局环境"

  local bt_dir=""
  bt_dir="$(detect_android_build_tools_dir || true)"
  [[ -n "$bt_dir" ]] || die "未找到 Android build-tools 目录: $ANDROID_SDK_ROOT/build-tools"
  info "Build-tools: $bt_dir"

  for c in bash git sed awk grep rustup cargo flutter dart sdkmanager adb keytool apksigner zipalign pkg-config zip find sort tail; do
    require_cmd "$c"
  done

  require_dir "$FLUTTER_HOME"
  require_dir "$ANDROID_SDK_ROOT"
  require_dir "$ANDROID_NDK_HOME"
  require_dir "$VCPKG_ROOT"

  info "Rust   : $(rustc -V || true)"
  info "Cargo  : $(cargo -V || true)"
  info "Flutter : $(command -v flutter || true)"
  info "Java   : $(java -version 2>&1 | head -n 1 || true)"
  info "NDK    : $ANDROID_NDK_HOME"
}

ensure_flutter_pub_get_once() {
  if [[ "$FLUTTER_PUB_GOT" -eq 1 ]]; then
    return 0
  fi

  section "准备 Flutter 依赖 (pub get)"
  (cd "$REPO_ROOT/flutter" && run flutter pub get)
  require_file "$REPO_ROOT/flutter/.dart_tool/package_config.json"
  FLUTTER_PUB_GOT=1
  ok "Flutter 依赖准备完成"
}

ensure_host_openssl_prereqs() {
  section "检查主机 OpenSSL 依赖"

  local pc_paths=("/usr/lib/x86_64-linux-gnu/pkgconfig" "/usr/lib/pkgconfig" "/usr/share/pkgconfig")
  local joined=""
  local d
  for d in "${pc_paths[@]}"; do
    [[ -d "$d" ]] && joined="${joined:+$joined:}$d"
  done
  export PKG_CONFIG_PATH="${PKG_CONFIG_PATH:-$joined}"

  if pkg-config --exists openssl; then
    ok "openssl.pc 已可用"
    return 0
  fi

  warn "未检测到 openssl.pc, 尝试安装 pkg-config libssl-dev"
  if command -v apt-get >/dev/null 2>&1; then
    run apt-get update -y
    run apt-get install -y pkg-config libssl-dev
  fi

  pkg-config --exists openssl || die "仍未找到 openssl.pc, 请检查 libssl-dev / PKG_CONFIG_PATH"
  ok "OpenSSL 主机依赖检查通过"
}

ensure_host_autotools_prereqs() {
  section "检查主机 autotools 依赖"

  local missing=()
  command -v autoconf >/dev/null 2>&1 || missing+=("autoconf")
  command -v automake >/dev/null 2>&1 || missing+=("automake")
  command -v m4 >/dev/null 2>&1 || missing+=("m4")
  if ! command -v libtool >/dev/null 2>&1 && ! command -v libtoolize >/dev/null 2>&1; then
    missing+=("libtool/libtoolize")
  fi

  if [[ "${#missing[@]}" -eq 0 ]]; then
    ok "autotools 依赖已满足"
    return 0
  fi

  warn "缺少主机工具: ${missing[*]}"
  if command -v apt-get >/dev/null 2>&1; then
    info "尝试自动安装: autoconf automake libtool libtool-bin m4"
    run apt-get update -y
    run apt-get install -y autoconf automake libtool libtool-bin m4
  fi

  command -v autoconf >/dev/null 2>&1 || die "仍缺少主机工具: autoconf"
  command -v automake >/dev/null 2>&1 || die "仍缺少主机工具: automake"
  command -v m4 >/dev/null 2>&1 || die "仍缺少主机工具: m4"
  if ! command -v libtool >/dev/null 2>&1 && ! command -v libtoolize >/dev/null 2>&1; then
    die "仍缺少主机工具: libtool/libtoolize"
  fi

  ok "autotools 依赖检查通过"
}

check_signing() {
  section "检查全局签名配置"

  require_file "$SIGN_ENV"
  # shellcheck disable=SC1090
  source "$SIGN_ENV"

  [[ "${CLOUDSEND_ANDROID_SIGN_ENABLED:-0}" == "1" ]] || die "签名未启用: CLOUDSEND_ANDROID_SIGN_ENABLED != 1"
  require_file "${CLOUDSEND_ANDROID_KEYSTORE_PATH:-}"
  [[ -n "${CLOUDSEND_ANDROID_KEY_ALIAS:-}" ]] || die "CLOUDSEND_ANDROID_KEY_ALIAS 未设置"
  [[ -n "${CLOUDSEND_ANDROID_STORE_PASSWORD:-}" ]] || die "CLOUDSEND_ANDROID_STORE_PASSWORD 未设置"
  [[ -n "${CLOUDSEND_ANDROID_KEY_PASSWORD:-}" ]] || die "CLOUDSEND_ANDROID_KEY_PASSWORD 未设置"

  keytool -list \
    -keystore "${CLOUDSEND_ANDROID_KEYSTORE_PATH}" \
    -alias "${CLOUDSEND_ANDROID_KEY_ALIAS}" \
    -storepass "${CLOUDSEND_ANDROID_STORE_PASSWORD}" >/dev/null

  export CLOUDSEND_ANDROID_KEYSTORE_PATH CLOUDSEND_ANDROID_KEY_ALIAS
  export CLOUDSEND_ANDROID_STORE_PASSWORD CLOUDSEND_ANDROID_KEY_PASSWORD

  ok "签名配置有效"
}

prepare_key_properties() {
  local key_props="$REPO_ROOT/flutter/android/key.properties"
  section "写入 key.properties"

  cat > "$key_props" <<EOF2
storeFile=${CLOUDSEND_ANDROID_KEYSTORE_PATH}
storePassword=${CLOUDSEND_ANDROID_STORE_PASSWORD}
keyAlias=${CLOUDSEND_ANDROID_KEY_ALIAS}
keyPassword=${CLOUDSEND_ANDROID_KEY_PASSWORD}
EOF2

  chmod 600 "$key_props" || true
  ok "已写入: $key_props"
}

# ===== Flutter patch / Rust / bridge =====
maybe_patch_flutter() {
  [[ "$SKIP_FLUTTER_PATCH" -eq 1 ]] && { info "跳过 Flutter patch"; return 0; }

  local patch_file="$REPO_ROOT/.github/patches/flutter_3.24.4_dropdown_menu_enableFilter.diff"
  [[ -f "$patch_file" ]] || { warn "未找到 Flutter patch, 跳过: $patch_file"; return 0; }

  local flutter_sdk_root
  flutter_sdk_root="$(dirname "$(dirname "$(command -v flutter)")")"
  require_dir "$flutter_sdk_root"
  ensure_git_safe_directory "$flutter_sdk_root"

  if [[ ! -d "$flutter_sdk_root/.git" ]]; then
    warn "Flutter SDK 目录不是 git 仓库, 跳过 patch: $flutter_sdk_root"
    return 0
  fi

  section "检测 Flutter patch"
  if git -C "$flutter_sdk_root" apply --check "$patch_file" >/dev/null 2>&1; then
    run git -C "$flutter_sdk_root" apply "$patch_file"
    ok "Flutter patch 已应用"
  elif git -C "$flutter_sdk_root" apply --reverse --check "$patch_file" >/dev/null 2>&1; then
    info "Flutter patch 已存在, 跳过"
  else
    warn "Flutter patch 无法应用 (可能版本不匹配或已修改)"
  fi
}

ensure_rust_android_setup() {
  section "检查 Rust Android targets 与 cargo-ndk"

  run rustup target add aarch64-linux-android >/dev/null
  if [[ "$BUILD_MODE" == "universal" ]]; then
    run rustup target add armv7-linux-androideabi >/dev/null
    run rustup target add x86_64-linux-android >/dev/null
  fi

  if ! cargo install --list | grep -q '^cargo-ndk v'; then
    info "安装 cargo-ndk (全局)"
    run cargo install cargo-ndk --version 3.1.2 --locked
  fi

  ok "Rust Android 环境检查完成"
}

bridge_android_files_missing() {
  local f
  for f in "$REPO_ROOT/flutter/lib/generated_bridge.dart" "$REPO_ROOT/flutter/lib/bridge_generated.dart"; do
    [[ -f "$f" ]] && return 1
  done
  warn "缺少 Dart bridge 文件 (需要 generated_bridge.dart 或 bridge_generated.dart)"
  return 0
}

parse_bridge_tool_versions() {
  local bridge_yml="$REPO_ROOT/.github/workflows/bridge.yml"
  BRIDGE_CARGO_EXPAND_VERSION="1.0.95"
  BRIDGE_FRB_VERSION="1.80.1"

  if [[ -f "$bridge_yml" ]]; then
    BRIDGE_CARGO_EXPAND_VERSION="$(grep -E 'CARGO_EXPAND_VERSION:' "$bridge_yml" | head -n1 | sed -E 's/.*"([^"]+)".*/\1/' || echo "1.0.95")"
    BRIDGE_FRB_VERSION="$(grep -E 'FLUTTER_RUST_BRIDGE_VERSION:' "$bridge_yml" | head -n1 | sed -E 's/.*"([^"]+)".*/\1/' || echo "1.80.1")"
  fi
}

guess_frb_inputs() {
  local rust_input="" dart_output="" c_output=""
  local f

  for f in "$REPO_ROOT/src/flutter_ffi.rs" "$REPO_ROOT/src/ffi.rs"; do
    [[ -f "$f" ]] && { rust_input="$f"; break; }
  done

  if [[ -f "$REPO_ROOT/flutter/lib/generated_bridge.dart" ]]; then
    dart_output="$REPO_ROOT/flutter/lib/generated_bridge.dart"
  elif [[ -f "$REPO_ROOT/flutter/lib/bridge_generated.dart" ]]; then
    dart_output="$REPO_ROOT/flutter/lib/bridge_generated.dart"
  else
    dart_output="$REPO_ROOT/flutter/lib/generated_bridge.dart"
  fi

  mkdir -p "$REPO_ROOT/flutter/macos/Runner" || true
  c_output="$REPO_ROOT/flutter/macos/Runner/bridge_generated.h"

  echo "${rust_input}|${dart_output}|${c_output}"
}

maybe_generate_bridge() {
  [[ "$SKIP_BRIDGE_CHECK" -eq 1 ]] && { info "跳过 bridge 检查/生成"; return 0; }

  if ! bridge_android_files_missing; then
    ok "Bridge 文件已满足 Android 构建要求"
    return 0
  fi

  [[ "$AUTO_BRIDGE" -eq 1 ]] || die "缺少 bridge 文件且已禁用自动生成 (--no-bridge)"

  section "生成 Flutter-Rust Bridge 文件"
  parse_bridge_tool_versions
  info "cargo-expand=${BRIDGE_CARGO_EXPAND_VERSION}, flutter_rust_bridge_codegen=${BRIDGE_FRB_VERSION}"

  if ! cargo install --list | grep -q '^cargo-expand v'; then
    run cargo install cargo-expand --version "${BRIDGE_CARGO_EXPAND_VERSION}" --locked
  fi
  if ! cargo install --list | grep -q '^flutter_rust_bridge_codegen v'; then
    run cargo install flutter_rust_bridge_codegen --version "${BRIDGE_FRB_VERSION}" --features "uuid" --locked
  fi

  ensure_flutter_pub_get_once

  local guessed rust_input dart_output c_output
  guessed="$(guess_frb_inputs)"
  rust_input="${guessed%%|*}"
  guessed="${guessed#*|}"
  dart_output="${guessed%%|*}"
  c_output="${guessed#*|}"

  [[ -n "$rust_input" && -f "$rust_input" ]] || die "无法确定 FRB rust 输入文件 (候选: src/flutter_ffi.rs / src/ffi.rs)"

  info "FRB rust input : $rust_input"
  info "FRB dart output: $dart_output"

  run "$HOME/.cargo/bin/flutter_rust_bridge_codegen" \
    --rust-input "$rust_input" \
    --dart-output "$dart_output" \
    --c-output "$c_output"

  if [[ -f "$c_output" ]]; then
    mkdir -p "$REPO_ROOT/flutter/ios/Runner" || true
    cp "$c_output" "$REPO_ROOT/flutter/ios/Runner/bridge_generated.h" || true
  fi

  if bridge_android_files_missing; then
    die "bridge 生成后 Android 必需的 Dart bridge 文件仍缺失"
  fi

  ok "Bridge 文件生成完成"
}

prepare_flutter_project() {
  ensure_flutter_pub_get_once
}

# ===== Target / ABI 映射 =====
android_target_to_abi() {
  case "$1" in
    aarch64-linux-android) echo "arm64-v8a" ;;
    armv7-linux-androideabi) echo "armeabi-v7a" ;;
    x86_64-linux-android) echo "x86_64" ;;
    i686-linux-android) echo "x86" ;;
    *) die "未知 target: $1" ;;
  esac
}

target_to_flutter_platform() {
  case "$1" in
    aarch64-linux-android) echo "android-arm64" ;;
    armv7-linux-androideabi) echo "android-arm" ;;
    x86_64-linux-android) echo "android-x64" ;;
    i686-linux-android) echo "android-x86" ;;
    *) die "未知 target: $1" ;;
  esac
}

target_to_ndk_script() {
  case "$1" in
    aarch64-linux-android) echo "./flutter/ndk_arm64.sh" ;;
    armv7-linux-androideabi) echo "./flutter/ndk_arm.sh" ;;
    x86_64-linux-android) echo "./flutter/ndk_x64.sh" ;;
    i686-linux-android) echo "./flutter/ndk_x86.sh" ;;
    *) die "未知 target: $1" ;;
  esac
}

target_to_jni_dir() {
  case "$1" in
    aarch64-linux-android) echo "arm64-v8a" ;;
    armv7-linux-androideabi) echo "armeabi-v7a" ;;
    x86_64-linux-android) echo "x86_64" ;;
    i686-linux-android) echo "x86" ;;
    *) die "未知 target: $1" ;;
  esac
}

target_to_libcpp_path() {
  case "$1" in
    aarch64-linux-android) echo "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" ;;
    armv7-linux-androideabi) echo "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so" ;;
    x86_64-linux-android) echo "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so" ;;
    i686-linux-android) echo "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/i686-linux-android/libc++_shared.so" ;;
    *) die "未知 target: $1" ;;
  esac
}

# ===== vcpkg / deps =====
sync_vcpkg_commit_if_needed() {
  local target="$1"
  local workflow_file="$REPO_ROOT/.github/workflows/flutter-build.yml"
  local desired_commit=""

  [[ -d "$VCPKG_ROOT/.git" ]] || { warn "VCPKG_ROOT 不是 git 仓库, 跳过 vcpkg commit 同步"; return 0; }
  ensure_git_safe_directory "$VCPKG_ROOT"

  if [[ -f "$workflow_file" ]]; then
    if [[ "$target" == "armv7-linux-androideabi" ]]; then
      desired_commit="$(grep -E '^[[:space:]]*ARMV7_VCPKG_COMMIT_ID:' "$workflow_file" | head -n1 | sed -E 's/.*"([^"]+)".*/\1/' || true)"
    else
      desired_commit="$(grep -E '^[[:space:]]*VCPKG_COMMIT_ID:' "$workflow_file" | head -n1 | sed -E 's/.*"([^"]+)".*/\1/' || true)"
    fi
  fi

  [[ -n "$desired_commit" ]] || { warn "未读取到 vcpkg commit, 跳过同步"; return 0; }

  local current_commit
  current_commit="$(git -C "$VCPKG_ROOT" rev-parse HEAD 2>/dev/null || true)"

  if [[ "$current_commit" == "$desired_commit" ]]; then
    info "vcpkg commit 已匹配: $desired_commit"
    return 0
  fi

  section "同步 vcpkg commit"
  info "${current_commit:-<unknown>} -> $desired_commit"
  run git -C "$VCPKG_ROOT" fetch --all --tags
  run git -C "$VCPKG_ROOT" checkout "$desired_commit"
  (cd "$VCPKG_ROOT" && run ./bootstrap-vcpkg.sh -disableMetrics)

  local new_commit
  new_commit="$(git -C "$VCPKG_ROOT" rev-parse HEAD)"
  [[ "$new_commit" == "$desired_commit" ]] || die "vcpkg commit 同步失败: 期望 $desired_commit, 实际 $new_commit"

  ok "vcpkg 已切换到: $new_commit"
}

cleanup_armv7_vcpkg_move_conflict() {
  local stale="$VCPKG_ROOT/installed/arm-android/arm-neon-android"
  if [[ -d "$stale" ]]; then
    warn "清理 armv7 遗留目录冲突: $stale"
    run rm -rf "$stale"
  fi
}

install_android_deps_for_target() {
  local target="$1"
  [[ "$SKIP_DEPS" -eq 1 ]] && { info "跳过 build_android_deps.sh ($target)"; return 0; }

  sync_vcpkg_commit_if_needed "$target"

  if [[ "$target" == "armv7-linux-androideabi" ]]; then
    cleanup_armv7_vcpkg_move_conflict
  fi

  local abi
  abi="$(android_target_to_abi "$target")"
  section "安装/检查 Android 依赖 ($abi)"
  (cd "$REPO_ROOT" && run bash ./flutter/build_android_deps.sh "$abi")
  ok "Android 依赖完成 ($abi)"
}

# ===== Rust / Flutter 构建 =====
build_rust_lib_for_target() {
  local target="$1"
  local ndk_script jni_dir libcpp src_so dst_dir

  ndk_script="$(target_to_ndk_script "$target")"
  jni_dir="$(target_to_jni_dir "$target")"
  libcpp="$(target_to_libcpp_path "$target")"

  require_file "$ndk_script"
  require_file "$libcpp"

  section "编译 Rust 动态库 ($target)"
  if ! (cd "$REPO_ROOT" && run bash "$ndk_script"); then
    warn "Rust 构建失败 ($target)"
    warn "如果日志中出现 signal: 9 (SIGKILL), 通常是系统 OOM 杀进程, 不是脚本语法问题"
    return 1
  fi

  src_so="$REPO_ROOT/target/$target/release/libcloudsend.so"
  require_file "$src_so"

  dst_dir="$REPO_ROOT/flutter/android/app/src/main/jniLibs/$jni_dir"
  mkdir -p "$dst_dir"
  cp "$src_so" "$dst_dir/libcloudsend.so"
  cp "$libcpp" "$dst_dir/libc++_shared.so"

  ok "JNI 库已放置: $dst_dir"
}

# ===== APK 搜索 =====
pick_existing_apk() {
  local preferred="${1:-}"
  shift || true

  require_dir "$FLUTTER_APK_DIR"

  if [[ -n "$preferred" && -f "$FLUTTER_APK_DIR/$preferred" ]]; then
    echo "$FLUTTER_APK_DIR/$preferred"
    return 0
  fi

  local p f
  for p in "$@"; do
    for f in "$FLUTTER_APK_DIR"/$p; do
      [[ -f "$f" ]] || continue
      echo "$f"
      return 0
    done
  done

  return 1
}

flutter_build_split_abi_apk() {
  local target="$1"
  local flutter_platform apk_in

  flutter_platform="$(target_to_flutter_platform "$target")"

  section "Flutter 打包 (split-per-abi, $target)"
  (cd "$REPO_ROOT/flutter" && run flutter build apk "--$RELTYPE" --target-platform "$flutter_platform" --split-per-abi)

  case "$target" in
    aarch64-linux-android)
      apk_in="$(pick_existing_apk \
        "app-arm64-v8a-$RELTYPE.apk" \
        "app-arm64-v8a-*.apk" \
        "app-*-arm64*.apk" \
        "app-*release*.apk")" || die "未找到 arm64 split APK 输出文件"
      ;;
    armv7-linux-androideabi)
      apk_in="$(pick_existing_apk \
        "app-armeabi-v7a-$RELTYPE.apk" \
        "app-armeabi-v7a-*.apk" \
        "app-*-armeabi*.apk" \
        "app-*release*.apk")" || die "未找到 armv7 split APK 输出文件"
      ;;
    x86_64-linux-android)
      apk_in="$(pick_existing_apk \
        "app-x86_64-$RELTYPE.apk" \
        "app-x86_64-*.apk" \
        "app-*-x86_64*.apk" \
        "app-*release*.apk")" || die "未找到 x86_64 split APK 输出文件"
      ;;
    i686-linux-android)
      apk_in="$(pick_existing_apk \
        "app-x86-$RELTYPE.apk" \
        "app-x86-*.apk" \
        "app-*-x86*.apk" \
        "app-*release*.apk")" || die "未找到 x86 split APK 输出文件"
      ;;
    *)
      die "未知 target: $target"
      ;;
  esac

  require_file "$apk_in"
  echo "$apk_in"
}

flutter_build_universal_apk() {
  section "Flutter 打包 (universal)"
  (cd "$REPO_ROOT/flutter" && run flutter build apk "--$RELTYPE" --target-platform android-arm64,android-arm,android-x64)

  local apk_in=""
  apk_in="$(pick_existing_apk \
    "app-$RELTYPE.apk" \
    "app-release.apk" \
    "app-*.apk")" || die "未找到 universal APK 输出文件"

  require_file "$apk_in"
  echo "$apk_in"
}

# ===== 手动签名 =====
sign_apk_manual() {
  local input_apk="$1"
  local output_apk="$2"

  require_file "$input_apk"

  local work_unsigned="$TMP_DIR/$(basename "${output_apk%.apk}")-unaligned-unsigned.apk"
  local work_aligned="$TMP_DIR/$(basename "${output_apk%.apk}")-aligned-unsigned.apk"

  rm -f "$work_unsigned" "$work_aligned" "$output_apk"
  cp "$input_apk" "$work_unsigned"

  zip -d "$work_unsigned" 'META-INF/*' >/dev/null 2>&1 || true
  run zipalign -p -f 4 "$work_unsigned" "$work_aligned"

  section "签名 APK"
  run apksigner sign \
    --ks "$CLOUDSEND_ANDROID_KEYSTORE_PATH" \
    --ks-key-alias "$CLOUDSEND_ANDROID_KEY_ALIAS" \
    --ks-pass "pass:$CLOUDSEND_ANDROID_STORE_PASSWORD" \
    --key-pass "pass:$CLOUDSEND_ANDROID_KEY_PASSWORD" \
    --out "$output_apk" \
    "$work_aligned"

  run apksigner verify --verbose "$output_apk" >/dev/null
  ok "签名完成: $output_apk"
}

# ===== 最终成品落地 =====
finalize_named_apk_to_flutter_dir() {
  local signed_tmp_apk="$1"
  local final_name="$2"

  require_file "$signed_tmp_apk"
  require_dir "$FLUTTER_APK_DIR"
  mkdir -p "$OUT_DIR"

  local final_in_flutter="$FLUTTER_APK_DIR/$final_name"
  local final_in_out="$OUT_DIR/$final_name"

  rm -f "$final_in_flutter" "$final_in_out"

  cp -f "$signed_tmp_apk" "$final_in_flutter"
  cp -f "$signed_tmp_apk" "$final_in_out"

  require_file "$final_in_flutter"
  require_file "$final_in_out"

  ok "目标目录成品: $final_in_flutter"
  ok "归档副本    : $final_in_out"
}

# ===== 构建模式 =====
build_mode_aarch64() {
  local target="aarch64-linux-android"
  local apk_in
  local signed_tmp_apk="$TMP_DIR/app-aarch64-release.signed.tmp.apk"

  section "开始构建: aarch64"
  install_android_deps_for_target "$target"
  build_rust_lib_for_target "$target"
  apk_in="$(flutter_build_split_abi_apk "$target")"
  sign_apk_manual "$apk_in" "$signed_tmp_apk"
  finalize_named_apk_to_flutter_dir "$signed_tmp_apk" "app-aarch64-release.apk"

  echo
  echo "${C_BOLD}${C_GREEN}✅ 构建完成 (aarch64)${C_RESET}"
  echo "${C_BOLD}目标目录:${C_RESET} $FLUTTER_APK_DIR/app-aarch64-release.apk"
}

build_mode_universal() {
  local targets=(aarch64-linux-android armv7-linux-androideabi x86_64-linux-android)
  local t
  local apk_in
  local signed_tmp_apk="$TMP_DIR/app-universal-release.signed.tmp.apk"

  section "开始构建: universal"
  for t in "${targets[@]}"; do
    install_android_deps_for_target "$t"
    build_rust_lib_for_target "$t"
  done

  apk_in="$(flutter_build_universal_apk)"
  sign_apk_manual "$apk_in" "$signed_tmp_apk"
  finalize_named_apk_to_flutter_dir "$signed_tmp_apk" "app-universal-release.apk"

  echo
  echo "${C_BOLD}${C_GREEN}✅ 构建完成 (universal)${C_RESET}"
  echo "${C_BOLD}目标目录:${C_RESET} $FLUTTER_APK_DIR/app-universal-release.apk"
}

main() {
  show_crlf_hint_if_needed
  parse_args "$@"
  check_repo_layout
  check_global_env
  ensure_flutter_sdk_health
  ensure_git_safe_directory "$REPO_ROOT"
  check_signing
  ensure_host_openssl_prereqs
  ensure_host_autotools_prereqs
  ensure_rust_android_setup
  maybe_patch_flutter
  maybe_generate_bridge
  prepare_flutter_project
  prepare_key_properties

  case "$BUILD_MODE" in
    aarch64) build_mode_aarch64 ;;
    universal) build_mode_universal ;;
    *) die "未知 BUILD_MODE: $BUILD_MODE" ;;
  esac
}

main "$@"

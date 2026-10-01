#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# CloudSend Android 全局环境菜单脚本(Ubuntu)
# 目标: 为本地 CloudSend 源码构建准备全局环境
# 配套: 源码目录内 Build.sh
# ============================================================

# ===== 彩色输出 =====
if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'; C_BOLD=$'\033[1m'; C_DIM=$'\033[2m'
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
  C_BLUE=$'\033[34m'; C_MAGENTA=$'\033[35m'; C_CYAN=$'\033[36m'
else
  C_RESET=""; C_BOLD=""; C_DIM=""
  C_RED=""; C_GREEN=""; C_YELLOW=""
  C_BLUE=""; C_MAGENTA=""; C_CYAN=""
fi

ts() { date '+%F %T'; }
log()  { echo "[$(ts)] ${C_CYAN}$*${C_RESET}"; }
info() { echo "[$(ts)] ${C_BLUE}$*${C_RESET}"; }
ok()   { echo "[$(ts)] ${C_GREEN}$*${C_RESET}"; }
warn() { echo "[$(ts)] ${C_YELLOW}[WARN] $*${C_RESET}" >&2; }
err()  { echo "[$(ts)] ${C_RED}[ERR ] $*${C_RESET}" >&2; }
die()  { err "$*"; exit 1; }
section() { echo; echo "[$(ts)] ${C_BOLD}${C_MAGENTA}==== $* ====${C_RESET}"; }

run() {
  echo "[$(ts)] ${C_DIM}+ $*${C_RESET}"
  "$@"
}

pause_enter() {
  echo
  read -r -p "按回车返回菜单..." _
}

# 安全累加
inc_failed() {
  failed=$((failed + 1))
}

# ===== 全局参数 =====
TOOLCHAIN_ROOT="${TOOLCHAIN_ROOT:-/opt/rustdesk-toolchain}"
CACHE_ROOT="${CACHE_ROOT:-$TOOLCHAIN_ROOT/cache}"
SIGNING_ROOT="${SIGNING_ROOT:-$TOOLCHAIN_ROOT/signing/android}"
SIGN_ENV_PATH="${CLOUDSEND_SIGN_ENV:-${SIGN_ENV_PATH:-$SIGNING_ROOT/signing.env}}"

RUST_VERSION="${RUST_VERSION:-1.75.0}"
CARGO_NDK_VERSION="${CARGO_NDK_VERSION:-3.1.2}"
FLUTTER_VERSION="${FLUTTER_VERSION:-3.24.5}"

ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-34}"
ANDROID_BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-34.0.0}"
ANDROID_NDK_PACKAGE="${ANDROID_NDK_PACKAGE:-ndk;27.2.12479018}"
ANDROID_NDK_VERSION_DIR="${ANDROID_NDK_VERSION_DIR:-27.2.12479018}"

VCPKG_COMMIT_ID="${VCPKG_COMMIT_ID:-6f29f12e82a8293156836ad81cc9bf5af41fe836}"

# 下载地址
ANDROID_CMDLINE_TOOLS_URL="${ANDROID_CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip}"
FLUTTER_TARBALL_URL="${FLUTTER_TARBALL_URL:-https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_${FLUTTER_VERSION}-stable.tar.xz}"

export CLOUDSEND_TOOLCHAIN_ROOT="$TOOLCHAIN_ROOT"
export FLUTTER_HOME="$TOOLCHAIN_ROOT/flutter"
export ANDROID_SDK_ROOT="$TOOLCHAIN_ROOT/android-sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION_DIR"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export VCPKG_ROOT="$TOOLCHAIN_ROOT/vcpkg"
export VCPKG_DEFAULT_BINARY_CACHE="$CACHE_ROOT/vcpkg"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"

detect_build_tools_dir() {
  local bt_root="$ANDROID_SDK_ROOT/build-tools"
  [[ -d "$bt_root" ]] || return 1
  find "$bt_root" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n1
}

refresh_path() {
  local bt=""
  bt="$(detect_build_tools_dir || true)"
  if [[ -n "$bt" ]]; then
    export PATH="$HOME/.cargo/bin:$FLUTTER_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$bt:$JAVA_HOME/bin:$PATH"
  else
    export PATH="$HOME/.cargo/bin:$FLUTTER_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$JAVA_HOME/bin:$PATH"
  fi
}
refresh_path

# ===== 通用检查 =====
require_root() {
  if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
    die "请使用 root 执行(或 sudo -i 后执行)"
  fi
}
require_cmd() { command -v "$1" >/dev/null 2>&1 || die "缺少命令: $1"; }

apt_noninteractive_preset() {
  export DEBIAN_FRONTEND=noninteractive
  export NEEDRESTART_MODE=a
}

# ===== 目录 / 环境变量 =====
create_global_dirs() {
  section "创建统一目录与缓存目录"
  run mkdir -p "$TOOLCHAIN_ROOT" "$CACHE_ROOT/vcpkg" "$SIGNING_ROOT"
  run chmod 700 "$TOOLCHAIN_ROOT/signing" 2>/dev/null || true
  run chmod 700 "$SIGNING_ROOT" 2>/dev/null || true
  run mkdir -p "$HOME/.cargo" "$HOME/.rustup" "$HOME/.gradle" "$HOME/.android" "$HOME/.pub-cache"
  ok "目录准备完成"
}

write_profile_env() {
  section "写入全局环境变量"
  local f="/etc/profile.d/rustdesk-toolchain.sh"

  cat > "$f" <<EOF
export CLOUDSEND_TOOLCHAIN_ROOT="$TOOLCHAIN_ROOT"
export FLUTTER_HOME="\$CLOUDSEND_TOOLCHAIN_ROOT/flutter"
export ANDROID_SDK_ROOT="\$CLOUDSEND_TOOLCHAIN_ROOT/android-sdk"
export ANDROID_HOME="\$ANDROID_SDK_ROOT"
export ANDROID_NDK_HOME="\$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION_DIR"
export ANDROID_NDK_ROOT="\$ANDROID_NDK_HOME"
export VCPKG_ROOT="\$CLOUDSEND_TOOLCHAIN_ROOT/vcpkg"
export VCPKG_DEFAULT_BINARY_CACHE="\$CLOUDSEND_TOOLCHAIN_ROOT/cache/vcpkg"
export JAVA_HOME="$JAVA_HOME"

if [ -d "\$ANDROID_SDK_ROOT/build-tools" ]; then
  _BT_LATEST="\$(find "\$ANDROID_SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n1)"
else
  _BT_LATEST=""
fi

export PATH="\$HOME/.cargo/bin:\$FLUTTER_HOME/bin:\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/platform-tools\${_BT_LATEST:+:\$_BT_LATEST}:\$JAVA_HOME/bin:\$PATH"
EOF

  chmod 644 "$f"
  # shellcheck disable=SC1091
  source "$f"
  refresh_path
  ok "已写入并加载: $f"
}

# ===== Flutter root 场景修复 =====
ensure_flutter_git_safe_directory() {
  section "修复 Flutter Git safe.directory(root 运行兼容)"

  local flutter_real=""
  if [[ -L "$FLUTTER_HOME" ]]; then
    flutter_real="$(readlink -f "$FLUTTER_HOME" || true)"
  elif [[ -d "$FLUTTER_HOME" ]]; then
    flutter_real="$FLUTTER_HOME"
  fi

  [[ -n "$flutter_real" && -d "$flutter_real" ]] || die "无法定位 Flutter 实际目录"

  run git config --global --add safe.directory "$flutter_real"
  run git config --global --add safe.directory "$FLUTTER_HOME"

  ok "已加入 Flutter safe.directory"
  echo "  - $flutter_real"
  echo "  - $FLUTTER_HOME"
}

ensure_flutter_usable() {
  section "检查 Flutter 可用性"

  refresh_path
  require_cmd flutter

  local ver_out=""
  ver_out="$(flutter --version 2>&1 || true)"
  echo "$ver_out" | head -n 6

  if echo "$ver_out" | grep -q '0\.0\.0-unknown'; then
    die "Flutter 版本识别异常(0.0.0-unknown)。请先修复 safe.directory 或重装 Flutter。"
  fi

  if ! echo "$ver_out" | grep -qi 'Flutter '; then
    die "Flutter 版本输出异常，无法确认 Flutter 可用。"
  fi

  ok "Flutter 可用"
}

# ===== 安装模块 =====
install_system_deps() {
  section "系统更新 + 全局依赖安装"
  apt_noninteractive_preset

  run apt-get update -y
  run apt-get upgrade -y

  run apt-get install -y \
    ca-certificates curl wget git unzip zip tar xz-utils tree \
    build-essential cmake ninja-build pkg-config \
    python3 python3-pip python3-venv jq rsync file patchelf \
    sed gawk grep findutils coreutils \
    openjdk-17-jdk openjdk-17-jdk-headless \
    adb clang lldb lld llvm-dev libclang-dev \
    gcc-multilib g++ g++-multilib libc6-dev \
    autoconf automake libtool libtool-bin m4 gettext \
    dos2unix nasm \
    libssl-dev \
    libayatana-appindicator3-dev libasound2-dev libunwind-dev \
    libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev \
    libgtk-3-dev libpam0g-dev libpulse-dev libva-dev \
    libxcb-randr0-dev libxcb-shape0-dev libxcb-xfixes0-dev \
    libxdo-dev libxfixes-dev

  ok "系统依赖安装完成"
}

install_rust_toolchain() {
  section "安装 Rust + Android targets + cargo-ndk"

  if [[ ! -x "$HOME/.cargo/bin/rustup" ]]; then
    run bash -lc 'curl https://sh.rustup.rs -sSf | sh -s -- -y'
  else
    info "rustup 已存在，跳过安装"
  fi

  # shellcheck disable=SC1090
  source "$HOME/.cargo/env"
  refresh_path

  run rustup toolchain install "$RUST_VERSION"
  run rustup default "$RUST_VERSION"
  run rustup component add rustfmt

  run rustup target add aarch64-linux-android
  run rustup target add armv7-linux-androideabi
  run rustup target add x86_64-linux-android
  run rustup target add i686-linux-android || true

  if ! cargo install --list | grep -q '^cargo-ndk v'; then
    run cargo install cargo-ndk --version "$CARGO_NDK_VERSION" --locked
  else
    info "cargo-ndk 已安装"
  fi

  ok "Rust 环境安装完成"
}

install_flutter() {
  section "安装 Flutter SDK($FLUTTER_VERSION)"

  local tarball="/tmp/flutter_linux_${FLUTTER_VERSION}-stable.tar.xz"
  local tmp_extract="/tmp/flutter_extract_$$"

  run mkdir -p "$TOOLCHAIN_ROOT"
  run rm -rf "$tmp_extract"
  run mkdir -p "$tmp_extract"

  run wget -O "$tarball" "$FLUTTER_TARBALL_URL"
  run tar -xf "$tarball" -C "$tmp_extract"

  [[ -d "$tmp_extract/flutter" ]] || die "Flutter 解压后未找到目录"

  run rm -rf "$TOOLCHAIN_ROOT/flutter-$FLUTTER_VERSION"
  run mv "$tmp_extract/flutter" "$TOOLCHAIN_ROOT/flutter-$FLUTTER_VERSION"
  run ln -sfn "$TOOLCHAIN_ROOT/flutter-$FLUTTER_VERSION" "$TOOLCHAIN_ROOT/flutter"

  refresh_path

  # 关键修复: root 场景 Flutter SDK git 安全目录
  ensure_flutter_git_safe_directory

  # 关键修复: 防止 0.0.0-unknown
  ensure_flutter_usable

  run flutter config --no-analytics || true
  run flutter precache --android || true
  run flutter doctor -v || true

  run rm -f "$tarball"
  run rm -rf "$tmp_extract"

  ok "Flutter 安装完成"
}

install_android_sdk() {
  section "安装 Android SDK(含 build-tools/apksigner)"

  local sdk_root="$ANDROID_SDK_ROOT"
  local cmdline_latest="$sdk_root/cmdline-tools/latest"
  local tmp_dir
  tmp_dir="$(mktemp -d)"

  run mkdir -p "$sdk_root/cmdline-tools"

  if [[ ! -x "$cmdline_latest/bin/sdkmanager" ]]; then
    run wget -O "$tmp_dir/cmdline-tools.zip" "$ANDROID_CMDLINE_TOOLS_URL"
    run rm -rf "$cmdline_latest"
    run mkdir -p "$cmdline_latest"
    run unzip -q "$tmp_dir/cmdline-tools.zip" -d "$tmp_dir/extract"
    [[ -d "$tmp_dir/extract/cmdline-tools" ]] || die "Android cmdline-tools 解压结构异常"
    run mv "$tmp_dir/extract/cmdline-tools/"* "$cmdline_latest/"
  else
    info "Android cmdline-tools 已存在，跳过下载"
  fi

  refresh_path
  require_cmd sdkmanager

  yes | sdkmanager --sdk_root="$sdk_root" --licenses >/dev/null || true

  run sdkmanager --sdk_root="$sdk_root" \
    "platform-tools" \
    "platforms;android-${ANDROID_API_LEVEL}" \
    "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
    "cmdline-tools;latest"

  refresh_path

  local bt="$sdk_root/build-tools/${ANDROID_BUILD_TOOLS_VERSION}"
  [[ -x "$bt/apksigner" ]] || die "未找到 apksigner: $bt/apksigner"
  [[ -x "$bt/zipalign" ]] || die "未找到 zipalign: $bt/zipalign"

  run rm -rf "$tmp_dir"
  ok "Android SDK 安装完成"
}

install_android_ndk() {
  section "安装 Android NDK($ANDROID_NDK_PACKAGE)"
  refresh_path
  require_cmd sdkmanager

  run sdkmanager --sdk_root="$ANDROID_SDK_ROOT" "$ANDROID_NDK_PACKAGE"

  [[ -d "$ANDROID_NDK_HOME" ]] || die "NDK 目录不存在: $ANDROID_NDK_HOME"
  [[ -x "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] || die "NDK clang 缺失"

  ok "Android NDK 安装完成"
}

install_vcpkg() {
  section "安装/更新 vcpkg(固定 commit)"

  run mkdir -p "$TOOLCHAIN_ROOT" "$VCPKG_DEFAULT_BINARY_CACHE"

  if [[ ! -d "$VCPKG_ROOT/.git" ]]; then
    run git clone https://github.com/microsoft/vcpkg.git "$VCPKG_ROOT"
  else
    info "vcpkg 已存在，执行更新"
    run git -C "$VCPKG_ROOT" fetch --all --tags
  fi

  run git -C "$VCPKG_ROOT" checkout "$VCPKG_COMMIT_ID"
  run bash -lc "cd '$VCPKG_ROOT' && ./bootstrap-vcpkg.sh -disableMetrics"

  ok "vcpkg 安装完成"
}

install_frb_tools() {
  section "安装 FRB 工具(bridge 自动生成所需)"

  # shellcheck disable=SC1090
  source "$HOME/.cargo/env" || true
  refresh_path

  if ! cargo install --list | grep -q '^cargo-expand v'; then
    run cargo install cargo-expand --version 1.0.95 --locked
  else
    info "cargo-expand 已安装"
  fi

  if ! cargo install --list | grep -q '^flutter_rust_bridge_codegen v'; then
    run cargo install flutter_rust_bridge_codegen --version 1.80.1 --features "uuid" --locked
  else
    info "flutter_rust_bridge_codegen 已安装"
  fi

  ok "FRB 工具安装完成"
}

install_all_global_env() {
  section "一键安装全局环境(不含签名)"
  require_root
  create_global_dirs
  write_profile_env
  install_system_deps
  install_rust_toolchain
  install_flutter
  install_android_sdk
  install_android_ndk
  install_vcpkg
  install_frb_tools
  ok "全局环境安装完成(签名单独配置)"
}

# ===== 签名 =====
load_signing_env_if_exists() {
  if [[ -f "$SIGN_ENV_PATH" ]]; then
    # shellcheck disable=SC1090
    source "$SIGN_ENV_PATH" || true
    return 0
  fi
  return 1
}

configure_global_signing_menu() {
  section "配置全局固定签名"
  require_root
  require_cmd keytool

  run mkdir -p "$SIGNING_ROOT"
  run chmod 700 "$TOOLCHAIN_ROOT/signing" 2>/dev/null || true
  run chmod 700 "$SIGNING_ROOT" 2>/dev/null || true

  local keystore_path="${SIGNING_ROOT}/rustdesk-upload.keystore"
  local sign_env="$SIGN_ENV_PATH"
  local key_alias="RustDesk"

  read -r -p "Key Alias(默认 RustDesk): " _alias_in || true
  [[ -n "${_alias_in:-}" ]] && key_alias="$_alias_in"

  echo "(1) 新建 keystore"
  echo "(2) 使用已有 keystore(路径固定: $keystore_path)"
  read -r -p "输入 [1/2]: " op

  case "$op" in
    1)
      if [[ -f "$keystore_path" ]]; then
        warn "keystore 已存在: $keystore_path"
        read -r -p "是否覆盖重建?(yes/no): " yn
        [[ "$yn" == "yes" ]] || return 0
        run cp -f "$keystore_path" "${keystore_path}.bak.$(date +%F_%H%M%S)" || true
      fi
      run keytool -genkeypair \
        -v \
        -keystore "$keystore_path" \
        -alias "$key_alias" \
        -keyalg RSA \
        -keysize 4096 \
        -sigalg SHA256withRSA \
        -validity 10000
      ;;
    2)
      [[ -f "$keystore_path" ]] || die "未找到 keystore: $keystore_path"
      ;;
    *)
      die "无效选项"
      ;;
  esac

  [[ -f "$keystore_path" ]] || die "keystore 不存在"

  echo "请输入密码(不回显)"
  read -r -s -p "Store Password: " store_pw; echo
  read -r -s -p "Key Password  : " key_pw; echo

  cat > "$sign_env" <<EOF
CLOUDSEND_ANDROID_SIGN_ENABLED=1
CLOUDSEND_ANDROID_KEYSTORE_PATH=$keystore_path
CLOUDSEND_ANDROID_KEY_ALIAS=$key_alias
CLOUDSEND_ANDROID_STORE_PASSWORD=$store_pw
CLOUDSEND_ANDROID_KEY_PASSWORD=$key_pw
EOF

  run chmod 600 "$sign_env"
  run chmod 600 "$keystore_path"

  if keytool -list -keystore "$keystore_path" -alias "$key_alias" -storepass "$store_pw" >/dev/null 2>&1; then
    ok "签名配置完成并验证通过"
  else
    warn "signing.env 已写入，但 alias/密码验证失败，请检查"
  fi
}

verify_signing_env() {
  section "验证签名环境(自动读取 signing.env)"

  if ! load_signing_env_if_exists; then
    die "未找到 signing.env: $SIGN_ENV_PATH"
  fi

  echo "SIGN_ENABLED=${CLOUDSEND_ANDROID_SIGN_ENABLED:-0}"
  echo "KEYSTORE_PATH=${CLOUDSEND_ANDROID_KEYSTORE_PATH:-<unset>}"
  echo "KEY_ALIAS=${CLOUDSEND_ANDROID_KEY_ALIAS:-<unset>}"

  [[ "${CLOUDSEND_ANDROID_SIGN_ENABLED:-0}" == "1" ]] || die "签名未启用"
  [[ -f "${CLOUDSEND_ANDROID_KEYSTORE_PATH:-}" ]] || die "keystore 不存在"

  if keytool -list \
    -keystore "${CLOUDSEND_ANDROID_KEYSTORE_PATH}" \
    -alias "${CLOUDSEND_ANDROID_KEY_ALIAS}" \
    -storepass "${CLOUDSEND_ANDROID_STORE_PASSWORD}" >/dev/null 2>&1; then
    ok "keystore + alias + password 验证通过"
  else
    die "签名验证失败(alias 或密码错误)"
  fi
}

# ===== 一键环境状态检查 =====
check_item() {
  local name="$1"
  local expr="$2"
  if eval "$expr" >/dev/null 2>&1; then
    printf "  ${C_GREEN}[✔]${C_RESET} %s\n" "$name"
    return 0
  else
    printf "  ${C_RED}[✘]${C_RESET} %s\n" "$name"
    return 1
  fi
}

one_key_check_current_env_status() {
  section "一键检查当前环境状态"

  refresh_path
  local failed=0

  echo "${C_BOLD}环境变量与目录${C_RESET}"
  if ! check_item "TOOLCHAIN_ROOT 目录存在" "[[ -d '$TOOLCHAIN_ROOT' ]]"; then inc_failed; fi
  if ! check_item "FLUTTER_HOME 目录存在" "[[ -d '$FLUTTER_HOME' ]]"; then inc_failed; fi
  if ! check_item "ANDROID_SDK_ROOT 目录存在" "[[ -d '$ANDROID_SDK_ROOT' ]]"; then inc_failed; fi
  if ! check_item "ANDROID_NDK_HOME 目录存在" "[[ -d '$ANDROID_NDK_HOME' ]]"; then inc_failed; fi
  if ! check_item "VCPKG_ROOT 目录存在" "[[ -d '$VCPKG_ROOT' ]]"; then inc_failed; fi
  if ! check_item "VCPKG 缓存目录存在" "[[ -d '$VCPKG_DEFAULT_BINARY_CACHE' ]]"; then inc_failed; fi
  if ! check_item "/etc/profile.d 环境文件存在" "[[ -f /etc/profile.d/rustdesk-toolchain.sh ]]"; then inc_failed; fi

  echo
  echo "${C_BOLD}核心命令${C_RESET}"
  local c
  for c in bash git curl wget unzip zip tar java javac rustup cargo flutter sdkmanager adb keytool apksigner zipalign pkg-config cmake ninja; do
    if ! check_item "命令: $c" "command -v '$c'"; then inc_failed; fi
  done

  # 关键修复: 避免 flutter 命令存在但版本识别异常
  if ! check_item "Flutter 版本可识别(非 0.0.0-unknown)" "! flutter --version 2>&1 | grep -q '0\.0\.0-unknown'"; then inc_failed; fi

  echo
  echo "${C_BOLD}构建前置依赖${C_RESET}"
  if ! check_item "autoconf" "command -v autoconf"; then inc_failed; fi
  if ! check_item "automake" "command -v automake"; then inc_failed; fi
  if ! check_item "m4" "command -v m4"; then inc_failed; fi
  if ! check_item "libtool 或 libtoolize" "command -v libtool || command -v libtoolize"; then inc_failed; fi
  if ! check_item "openssl.pc(pkg-config)" "pkg-config --exists openssl"; then inc_failed; fi
  if ! check_item "cargo-ndk" "cargo ndk --help"; then inc_failed; fi
  if ! check_item "cargo-expand" "command -v cargo-expand"; then inc_failed; fi
  if ! check_item "flutter_rust_bridge_codegen" "command -v flutter_rust_bridge_codegen"; then inc_failed; fi

  echo
  echo "${C_BOLD}Rust Android targets${C_RESET}"
  if ! check_item "aarch64-linux-android target" "rustup target list --installed | grep -q '^aarch64-linux-android$'"; then inc_failed; fi
  if ! check_item "armv7-linux-androideabi target" "rustup target list --installed | grep -q '^armv7-linux-androideabi$'"; then inc_failed; fi
  if ! check_item "x86_64-linux-android target" "rustup target list --installed | grep -q '^x86_64-linux-android$'"; then inc_failed; fi

  echo
  echo "${C_BOLD}Android 关键组件${C_RESET}"
  if ! check_item "build-tools 目录存在" "[[ -d '$ANDROID_SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS_VERSION' ]]"; then inc_failed; fi
  if ! check_item "apksigner 可执行" "[[ -x '$ANDROID_SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS_VERSION/apksigner' ]]"; then inc_failed; fi
  if ! check_item "zipalign 可执行" "[[ -x '$ANDROID_SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS_VERSION/zipalign' ]]"; then inc_failed; fi
  if ! check_item "NDK clang 可执行" "[[ -x '$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang' ]]"; then inc_failed; fi

  echo
  echo "${C_BOLD}签名环境${C_RESET}"
  if [[ -f "$SIGN_ENV_PATH" ]]; then
    # shellcheck disable=SC1090
    source "$SIGN_ENV_PATH" || true
    if ! check_item "signing.env 存在" "[[ -f '$SIGN_ENV_PATH' ]]"; then inc_failed; fi
    if ! check_item "签名启用" "[[ '${CLOUDSEND_ANDROID_SIGN_ENABLED:-0}' == '1' ]]"; then inc_failed; fi
    if ! check_item "keystore 文件存在" "[[ -f '${CLOUDSEND_ANDROID_KEYSTORE_PATH:-/nonexistent}' ]]"; then inc_failed; fi
    if ! check_item "签名 alias 非空" "[[ -n '${CLOUDSEND_ANDROID_KEY_ALIAS:-}' ]]"; then inc_failed; fi
  else
    printf "  ${C_YELLOW}[!]${C_RESET} signing.env 未配置(如果暂不签名可稍后配置)\n"
    inc_failed
  fi

  echo
  if [[ "$failed" -eq 0 ]]; then
    echo "${C_BOLD}${C_GREEN}环境正常，适合打包环境${C_RESET}"
  else
    echo "${C_BOLD}${C_RED}环境异常，存在缺失项: ${failed}${C_RESET}"
    echo "请根据上面的 [✘] 项先执行安装/修复。"
  fi
}

# ===== 构建前核对(aarch64 / universal) + bridge 自动检测/生成 =====
repo_check_common_layout() {
  local repo="$1"
  [[ -d "$repo" ]] || die "源码目录不存在: $repo"
  [[ -f "$repo/Cargo.toml" ]] || die "缺少文件: $repo/Cargo.toml"
  [[ -d "$repo/src" ]] || die "缺少目录: $repo/src"
  [[ -d "$repo/flutter" ]] || die "缺少目录: $repo/flutter"
  [[ -f "$repo/flutter/pubspec.yaml" ]] || die "缺少文件: $repo/flutter/pubspec.yaml"
  [[ -f "$repo/flutter/build_android_deps.sh" ]] || die "缺少文件: $repo/flutter/build_android_deps.sh"
  [[ -f "$repo/flutter/ndk_arm64.sh" ]] || die "缺少文件: $repo/flutter/ndk_arm64.sh"
}

repo_check_for_mode() {
  local repo="$1" mode="$2"
  repo_check_common_layout "$repo"
  if [[ "$mode" == "universal" ]]; then
    [[ -f "$repo/flutter/ndk_arm.sh" ]] || die "universal 模式缺少: $repo/flutter/ndk_arm.sh"
    [[ -f "$repo/flutter/ndk_x64.sh" ]] || die "universal 模式缺少: $repo/flutter/ndk_x64.sh"
  fi
}

bridge_missing_for_android() {
  local repo="$1"
  [[ -f "$repo/flutter/lib/generated_bridge.dart" ]] && return 1
  [[ -f "$repo/flutter/lib/bridge_generated.dart" ]] && return 1
  return 0
}

guess_frb_inputs_repo() {
  local repo="$1"
  local rust_input="" dart_output="" c_output=""

  local f
  for f in "$repo/src/flutter_ffi.rs" "$repo/src/ffi.rs"; do
    [[ -f "$f" ]] && { rust_input="$f"; break; }
  done

  if [[ -f "$repo/flutter/lib/generated_bridge.dart" ]]; then
    dart_output="$repo/flutter/lib/generated_bridge.dart"
  elif [[ -f "$repo/flutter/lib/bridge_generated.dart" ]]; then
    dart_output="$repo/flutter/lib/bridge_generated.dart"
  else
    dart_output="$repo/flutter/lib/generated_bridge.dart"
  fi

  mkdir -p "$repo/flutter/macos/Runner" || true
  c_output="$repo/flutter/macos/Runner/bridge_generated.h"

  echo "${rust_input}|${dart_output}|${c_output}"
}

try_generate_bridge_for_repo() {
  local repo="$1"
  section "尝试自动生成 bridge 文件"

  # shellcheck disable=SC1090
  source "$HOME/.cargo/env" || true
  refresh_path

  command -v cargo >/dev/null 2>&1 || die "缺少 cargo"
  command -v flutter >/dev/null 2>&1 || die "缺少 flutter"
  command -v dart >/dev/null 2>&1 || die "缺少 dart(Flutter 内置)"

  # 修复 root + Flutter git safe.directory
  ensure_flutter_git_safe_directory
  ensure_flutter_usable

  local guessed rust_input dart_output c_output
  guessed="$(guess_frb_inputs_repo "$repo")"
  rust_input="${guessed%%|*}"
  guessed="${guessed#*|}"
  dart_output="${guessed%%|*}"
  c_output="${guessed#*|}"

  [[ -n "$rust_input" && -f "$rust_input" ]] || die "无法确定 FRB rust 输入文件(候选: src/flutter_ffi.rs / src/ffi.rs)"

  info "FRB rust input : $rust_input"
  info "FRB dart output: $dart_output"

  section "准备 Flutter 依赖(bridge 生成前置)"
  (cd "$repo/flutter" && run flutter pub get)

  [[ -f "$repo/flutter/.dart_tool/package_config.json" ]] || die "缺少 $repo/flutter/.dart_tool/package_config.json(flutter pub get 可能失败)"

  mkdir -p "$(dirname "$dart_output")"
  mkdir -p "$(dirname "$c_output")"

  if command -v flutter_rust_bridge_codegen >/dev/null 2>&1; then
    info "尝试直接调用 flutter_rust_bridge_codegen"
    if flutter_rust_bridge_codegen \
      --rust-input "$rust_input" \
      --dart-output "$dart_output" \
      --c-output "$c_output"; then
      :
    else
      warn "直接调用失败，回退到 dart run flutter_rust_bridge_codegen generate"
      (cd "$repo/flutter" && run dart run flutter_rust_bridge_codegen generate \
        --rust-input "$rust_input" \
        --dart-output "$dart_output" \
        --c-output "$c_output")
    fi
  else
    info "未找到全局 flutter_rust_bridge_codegen，使用 dart run 方式"
    (cd "$repo/flutter" && run dart run flutter_rust_bridge_codegen generate \
      --rust-input "$rust_input" \
      --dart-output "$dart_output" \
      --c-output "$c_output")
  fi

  if [[ -f "$c_output" ]]; then
    mkdir -p "$repo/flutter/ios/Runner" || true
    cp "$c_output" "$repo/flutter/ios/Runner/bridge_generated.h" || true
  fi

  if bridge_missing_for_android "$repo"; then
    die "bridge 生成后 Android 必需的 Dart bridge 文件仍缺失"
  fi

  ok "Bridge 文件生成完成"
}

prebuild_full_check_menu() {
  section "构建前完整核对(aarch64 / universal)"

  refresh_path
  one_key_check_current_env_status || true

  echo
  read -r -p "请输入本地 CloudSend 源码目录(例如 /root/CloudSend): " repo
  [[ -n "$repo" ]] || die "源码目录不能为空"

  echo "(1) aarch64"
  echo "(2) universal"
  read -r -p "请选择构建模式 [1/2]: " mode_in
  local mode=""
  case "$mode_in" in
    1) mode="aarch64" ;;
    2) mode="universal" ;;
    *) die "无效选项" ;;
  esac

  repo_check_for_mode "$repo" "$mode"
  ok "源码目录结构检查通过($mode)"

  if load_signing_env_if_exists; then
    ok "已自动读取 signing.env: $SIGN_ENV_PATH"
    [[ "${CLOUDSEND_ANDROID_SIGN_ENABLED:-0}" == "1" ]] || die "signing.env 已读取，但签名未启用"
    [[ -f "${CLOUDSEND_ANDROID_KEYSTORE_PATH:-}" ]] || die "签名 keystore 不存在"
  else
    die "未找到全局 signing.env: $SIGN_ENV_PATH"
  fi

  if bridge_missing_for_android "$repo"; then
    warn "检测到 Android 所需 Dart bridge 文件缺失"
    read -r -p "是否自动生成 bridge? (yes/no): " yn
    if [[ "$yn" == "yes" ]]; then
      try_generate_bridge_for_repo "$repo"
    else
      die "bridge 缺失，已取消"
    fi
  else
    ok "Bridge 文件已存在，满足 Android 构建要求"
  fi

  echo
  echo "${C_BOLD}${C_GREEN}构建前完整核对通过($mode)${C_RESET}"
  echo "你现在可以进入源码目录执行:"
  echo "  bash ./Build.sh --mode $mode --verbose"
}

# ===== 输出环境安装列表 + 目录树 =====
print_install_list_and_tree() {
  section "环境安装列表 + 目录树"

  cat <<EOF
[安装目标]
- TOOLCHAIN_ROOT: $TOOLCHAIN_ROOT
- Flutter       : $FLUTTER_HOME
- Android SDK   : $ANDROID_SDK_ROOT
- Android NDK   : $ANDROID_NDK_HOME
- vcpkg         : $VCPKG_ROOT
- vcpkg cache   : $VCPKG_DEFAULT_BINARY_CACHE
- signing env   : $SIGN_ENV_PATH

[用户缓存目录]
- $HOME/.cargo
- $HOME/.rustup
- $HOME/.gradle
- $HOME/.android
- $HOME/.pub-cache
EOF

  echo
  if command -v tree >/dev/null 2>&1; then
    tree -L 4 "$TOOLCHAIN_ROOT" || true
  else
    find "$TOOLCHAIN_ROOT" -maxdepth 4 -print | sed 's#^#  #'
  fi
}

# ===== 菜单 =====
menu_main() {
  while true; do
    clear || true
    echo "${C_BOLD}${C_MAGENTA}CloudSend Android 全局环境部署菜单${C_RESET}"
    echo "=========================================================="
    echo " (1) 一键安装全局环境(系统更新 + 依赖 + Rust + Flutter + SDK + NDK + Vcpkg + FRB)"
    echo " (2) 配置全局固定签名(单独)"
    echo " (3) 一键检查当前环境状态(总体环境检查)"
    echo " (4) 构建前完整核对(Aarch64/Universal + 自动读签名 + Bridge检查/生成)"
    echo " (5) 验证签名环境(自动读取 Signing.env)"
    echo " (6) 输出环境安装列表及目录树"
    echo " (0) 退出"
    echo "=========================================================="
    echo

    read -r -p "请输入选项: " choice
    echo

    case "$choice" in
      1) install_all_global_env || err "全局环境安装失败"; pause_enter ;;
      2) configure_global_signing_menu || err "签名配置失败"; pause_enter ;;
      3) one_key_check_current_env_status || err "环境检查失败"; pause_enter ;;
      4) prebuild_full_check_menu || err "构建前核对失败"; pause_enter ;;
      5) verify_signing_env || err "签名验证失败"; pause_enter ;;
      6) print_install_list_and_tree || err "目录树输出失败"; pause_enter ;;
      0) echo "Bye."; exit 0 ;;
      *) warn "无效选项: $choice"; pause_enter ;;
    esac
  done
}

main() {
  require_root
  menu_main
}
main "$@"

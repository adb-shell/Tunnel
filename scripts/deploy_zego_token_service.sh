#!/usr/bin/env bash
set -Eeuo pipefail

SERVICE_NAME="${SERVICE_NAME:-cloudsend-zego-token}"
INSTALL_DIR="${INSTALL_DIR:-/www/wwwroot/cloudsend-zego-token}"
INSTALL_DIR="${INSTALL_DIR%/}"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

HOST="${HOST:-0.0.0.0}"
PORT="${PORT:-50003}"
ZEGO_APP_ID="${ZEGO_APP_ID:-726162948}"
ZEGO_SERVER_SECRET="${ZEGO_SERVER_SECRET:-360a56369441ee640841cb4c82144186}"
VOICE_TOKEN_TTL_SECONDS="${VOICE_TOKEN_TTL_SECONDS:-3600}"
VOICE_API_KEY="${VOICE_API_KEY:-PHFfBRiEXVKFvEGD2cJp}"
GOPROXY_VALUE="${GOPROXY_VALUE:-https://goproxy.cn,direct}"
ACTION=""

log() {
  printf '\033[1;32m[cloudsend-zego]\033[0m %s\n' "$*"
}

warn() {
  printf '\033[1;33m[cloudsend-zego]\033[0m %s\n' "$*"
}

fail() {
  printf '\033[1;31m[cloudsend-zego]\033[0m %s\n' "$*" >&2
  exit 1
}

on_error() {
  local line="$1"
  warn "脚本在第 ${line} 行失败。最近服务日志如下："
  journalctl -u "${SERVICE_NAME}" -n 80 --no-pager 2>/dev/null || true
}

trap 'on_error $LINENO' ERR

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    fail "请使用 root 执行：sudo bash $0"
  fi
  command -v systemctl >/dev/null 2>&1 || fail "当前系统未检测到 systemctl，脚本需要 systemd 环境"
}

assert_safe_install_dir() {
  if [[ -z "${INSTALL_DIR}" || "${INSTALL_DIR}" != /* ]]; then
    fail "INSTALL_DIR 必须是绝对路径，当前值：${INSTALL_DIR}"
  fi

  case "${INSTALL_DIR}" in
    "/"|"/www"|"/www/"|"/www/wwwroot"|"/www/wwwroot/"|"/etc"|"/etc/"|"/usr"|"/usr/"|"/root"|"/root/"|"/var"|"/var/")
      fail "INSTALL_DIR 指向高风险目录，拒绝继续：${INSTALL_DIR}"
      ;;
  esac
}

install_go_if_needed() {
  if command -v go >/dev/null 2>&1; then
    log "Go 已安装：$(go version)"
    return
  fi

  log "未检测到 Go，开始自动安装 Go 构建环境"
  if command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    apt-get install -y golang-go ca-certificates curl
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y golang ca-certificates curl
  elif command -v yum >/dev/null 2>&1; then
    yum install -y golang ca-certificates curl
  elif command -v apk >/dev/null 2>&1; then
    apk add --no-cache go ca-certificates curl
  else
    fail "未识别当前 Linux 包管理器，请手动安装 Go 后重试"
  fi

  command -v go >/dev/null 2>&1 || fail "Go 安装失败，请检查服务器软件源"
  log "Go 安装完成：$(go version)"
}

ensure_curl() {
  if command -v curl >/dev/null 2>&1; then
    return
  fi
  log "未检测到 curl，开始安装"
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update
    apt-get install -y curl
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y curl
  elif command -v yum >/dev/null 2>&1; then
    yum install -y curl
  elif command -v apk >/dev/null 2>&1; then
    apk add --no-cache curl
  else
    fail "未识别当前 Linux 包管理器，请手动安装 curl 后重试"
  fi
}

write_project_files() {
  log "写入服务文件到 ${INSTALL_DIR}"
  mkdir -p "${INSTALL_DIR}"
  cd "${INSTALL_DIR}"

  umask 077
  cat > .env <<EOF
HOST=${HOST}
PORT=${PORT}
ZEGO_APP_ID=${ZEGO_APP_ID}
ZEGO_SERVER_SECRET=${ZEGO_SERVER_SECRET}
VOICE_TOKEN_TTL_SECONDS=${VOICE_TOKEN_TTL_SECONDS}
VOICE_API_KEY=${VOICE_API_KEY}
EOF
  chmod 600 .env
  umask 022

  cat > go.mod <<'EOF'
module cloudsend-zego-token

go 1.18

require github.com/ZEGOCLOUD/zego_server_assistant/token/go/src v0.0.0-20231103072415-8c895c31df9d
EOF

  cat > main.go <<'EOF'
package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/ZEGOCLOUD/zego_server_assistant/token/go/src/token04"
)

type createRequest struct {
	PcPeerId           string `json:"pcPeerId"`
	AndroidPeerId      string `json:"androidPeerId"`
	CloudsendSessionId string `json:"cloudsendSessionId"`
}

type createResponse struct {
	RtcProvider    string `json:"rtcProvider"`
	AppId          uint32 `json:"appId"`
	RoomId         string `json:"roomId"`
	CallerUserId   string `json:"callerUserId"`
	CalleeUserId   string `json:"calleeUserId"`
	CallerStreamId string `json:"callerStreamId"`
	CalleeStreamId string `json:"calleeStreamId"`
	CallerToken    string `json:"callerToken"`
	CalleeToken    string `json:"calleeToken"`
	ExpiresAt      int64  `json:"expiresAt"`
}

type errorResponse struct {
	Error string `json:"error"`
}

func main() {
	host := getenv("HOST", "0.0.0.0")
	port := getenv("PORT", "50003")

	http.HandleFunc("/", handleRoot)
	http.HandleFunc("/api/v1/health", handleHealth)
	http.HandleFunc("/api/v1/voice-call/create", handleCreate)

	addr := host + ":" + port
	log.Println("cloudsend zego token service listening on", addr)
	log.Fatal(http.ListenAndServe(addr, nil))
}

func handleRoot(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodPost && r.URL.Path == "/" {
		handleCreate(w, r)
		return
	}
	writeJSON(w, http.StatusNotFound, errorResponse{"not_found"})
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func handleCreate(w http.ResponseWriter, r *http.Request) {
	log.Println(r.Method, r.URL.Path)

	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, errorResponse{"method_not_allowed"})
		return
	}

	if !authorized(r) {
		writeJSON(w, http.StatusUnauthorized, errorResponse{"unauthorized"})
		return
	}

	var req createRequest
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, errorResponse{"invalid_json"})
		return
	}

	pcPeerId := clean(req.PcPeerId)
	androidPeerId := clean(req.AndroidPeerId)
	sessionId := clean(req.CloudsendSessionId)

	if pcPeerId == "" || androidPeerId == "" || sessionId == "" {
		writeJSON(w, http.StatusBadRequest, errorResponse{"missing_required_id"})
		return
	}

	appId64, err := strconv.ParseUint(os.Getenv("ZEGO_APP_ID"), 10, 32)
	if err != nil || appId64 == 0 {
		writeJSON(w, http.StatusInternalServerError, errorResponse{"invalid_zego_app_id"})
		return
	}

	appId := uint32(appId64)
	secret := os.Getenv("ZEGO_SERVER_SECRET")
	if secret == "" || strings.Contains(secret, "<") {
		writeJSON(w, http.StatusInternalServerError, errorResponse{"missing_zego_server_secret"})
		return
	}

	ttl := int64(3600)
	if v, err := strconv.ParseInt(getenv("VOICE_TOKEN_TTL_SECONDS", "3600"), 10, 64); err == nil && v >= 60 {
		ttl = v
	}

	nonce := randomHex(8)

	roomId := trim(fmt.Sprintf("cs_voice_%s_%s_%s_%s", androidPeerId, pcPeerId, sessionId, nonce), 128)
	callerUserId := trim(fmt.Sprintf("pc_%s_%s", pcPeerId, sessionId), 64)
	calleeUserId := trim(fmt.Sprintf("android_%s_%s", androidPeerId, sessionId), 64)
	callerStreamId := trim(fmt.Sprintf("cs_voice_pub_%s_pc", nonce), 64)
	calleeStreamId := trim(fmt.Sprintf("cs_voice_pub_%s_android", nonce), 64)

	callerToken, err := makeToken(appId, callerUserId, secret, ttl)
	if err != nil {
		log.Println("caller token error:", err)
		writeJSON(w, http.StatusInternalServerError, errorResponse{"caller_token_failed"})
		return
	}

	calleeToken, err := makeToken(appId, calleeUserId, secret, ttl)
	if err != nil {
		log.Println("callee token error:", err)
		writeJSON(w, http.StatusInternalServerError, errorResponse{"callee_token_failed"})
		return
	}

	writeJSON(w, http.StatusOK, createResponse{
		RtcProvider:    "zego",
		AppId:          appId,
		RoomId:         roomId,
		CallerUserId:   callerUserId,
		CalleeUserId:   calleeUserId,
		CallerStreamId: callerStreamId,
		CalleeStreamId: calleeStreamId,
		CallerToken:    callerToken,
		CalleeToken:    calleeToken,
		ExpiresAt:      time.Now().Unix() + ttl,
	})
}

func makeToken(appId uint32, userId string, secret string, ttl int64) (string, error) {
	return token04.GenerateToken04(appId, userId, secret, ttl, "")
}

func authorized(r *http.Request) bool {
	expected := strings.TrimSpace(os.Getenv("VOICE_API_KEY"))
	if expected == "" || strings.Contains(expected, "<") {
		return false
	}
	return strings.TrimSpace(r.Header.Get("Authorization")) == "Bearer "+expected
}

func writeJSON(w http.ResponseWriter, code int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func getenv(key string, fallback string) string {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	return v
}

func clean(s string) string {
	s = strings.TrimSpace(s)
	var b strings.Builder
	for _, r := range s {
		if r >= 'a' && r <= 'z' ||
			r >= 'A' && r <= 'Z' ||
			r >= '0' && r <= '9' ||
			r == '_' || r == '-' {
			b.WriteRune(r)
		}
	}
	return b.String()
}

func trim(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max]
}

func randomHex(n int) string {
	buf := make([]byte, n)
	if _, err := rand.Read(buf); err != nil {
		return strconv.FormatInt(time.Now().UnixNano(), 16)
	}
	return hex.EncodeToString(buf)
}
EOF
}

build_service() {
  cd "${INSTALL_DIR}"
  log "拉取 Go 依赖并构建服务"
  GOPROXY="${GOPROXY_VALUE}" go mod tidy
  GOPROXY="${GOPROXY_VALUE}" go build -o "${SERVICE_NAME}" main.go
  chmod +x "${SERVICE_NAME}"
}

install_systemd_service() {
  log "写入 systemd 服务：${SERVICE_FILE}"
  cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=CloudSend ZEGO Token Service
After=network.target

[Service]
Type=simple
WorkingDirectory=${INSTALL_DIR}
EnvironmentFile=${INSTALL_DIR}/.env
ExecStart=${INSTALL_DIR}/${SERVICE_NAME}
Restart=always
RestartSec=3
User=root
Group=root

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable "${SERVICE_NAME}" >/dev/null
  systemctl restart "${SERVICE_NAME}"
}

verify_service() {
  log "等待服务启动"
  sleep 1
  systemctl is-active --quiet "${SERVICE_NAME}" || fail "服务未正常启动"

  local health_url="http://127.0.0.1:${PORT}/api/v1/health"
  log "测试健康接口：${health_url}"
  curl -fsS "${health_url}" >/tmp/cloudsend-zego-health.json
  grep -q '"ok":true' /tmp/cloudsend-zego-health.json || fail "健康检查返回异常：$(cat /tmp/cloudsend-zego-health.json)"

  local token_url="http://127.0.0.1:${PORT}"
  log "测试 Token 创建接口：POST ${token_url}"
  curl -fsS -X POST "${token_url}" \
    -H "Authorization: Bearer ${VOICE_API_KEY}" \
    -H "Content-Type: application/json" \
    -d '{"pcPeerId":"pc_test","androidPeerId":"android_test","cloudsendSessionId":"sess_test"}' \
    >/tmp/cloudsend-zego-token-test.json

  grep -q '"callerToken"' /tmp/cloudsend-zego-token-test.json || fail "Token 测试返回异常：$(cat /tmp/cloudsend-zego-token-test.json)"
  grep -q '"calleeToken"' /tmp/cloudsend-zego-token-test.json || fail "Token 测试缺少 calleeToken：$(cat /tmp/cloudsend-zego-token-test.json)"
}

print_summary() {
  local public_ip
  public_ip="$(curl -4 -fsS --max-time 3 https://api.ipify.org 2>/dev/null || true)"
  if [[ -z "${public_ip}" ]]; then
    public_ip="<当前服务器公网IP>"
  fi

  cat <<EOF

============================================================
CloudSend ZEGO Token Service 部署完成
============================================================

服务名：${SERVICE_NAME}
目录：${INSTALL_DIR}
监听：${HOST}:${PORT}
本机健康检查：http://127.0.0.1:${PORT}/api/v1/health
公网访问入口：http://${public_ip}:${PORT}

PC 端 Token URL 应配置为：
http://${public_ip}:${PORT}

常用命令：
systemctl status ${SERVICE_NAME} --no-pager
journalctl -u ${SERVICE_NAME} -f
systemctl restart ${SERVICE_NAME}

注意：
1. 脚本不会写死服务器 IP；公网入口取决于你在哪台服务器执行。
2. 请在云服务器安全组/宝塔防火墙放行 TCP ${PORT}。
3. 如果 PC 源码里的 DEFAULT_ZEGO_TOKEN_URL 不是上述公网入口，需要修改 PC 源码并重新编译 PC 端。

============================================================
EOF
}

install_token_service() {
  log "开始安装 ZEGO Token 服务"
  require_root
  assert_safe_install_dir
  ensure_curl
  install_go_if_needed
  write_project_files
  build_service
  install_systemd_service
  verify_service
  print_summary
}

uninstall_token_service() {
  log "开始卸载 ZEGO Token 服务"
  require_root
  assert_safe_install_dir

  log "停止 systemd 服务：${SERVICE_NAME}"
  systemctl stop "${SERVICE_NAME}" 2>/dev/null || true

  log "取消开机自启：${SERVICE_NAME}"
  systemctl disable "${SERVICE_NAME}" 2>/dev/null || true

  if [[ -f "${SERVICE_FILE}" ]]; then
    log "删除 systemd 服务文件：${SERVICE_FILE}"
    rm -f "${SERVICE_FILE}"
  else
    warn "未找到 systemd 服务文件：${SERVICE_FILE}"
  fi

  log "重载 systemd"
  systemctl daemon-reload
  systemctl reset-failed "${SERVICE_NAME}" 2>/dev/null || true

  if [[ -d "${INSTALL_DIR}" ]]; then
    log "删除服务目录：${INSTALL_DIR}"
    rm -rf -- "${INSTALL_DIR}"
  else
    warn "未找到服务目录：${INSTALL_DIR}"
  fi

  rm -f /tmp/cloudsend-zego-health.json /tmp/cloudsend-zego-token-test.json

  systemctl daemon-reload
  if systemctl is-active --quiet "${SERVICE_NAME}" 2>/dev/null; then
    fail "卸载后服务仍在运行，请手动检查：systemctl status ${SERVICE_NAME} --no-pager"
  fi
  if [[ -f "${SERVICE_FILE}" ]]; then
    fail "卸载后服务文件仍存在：${SERVICE_FILE}"
  fi
  if [[ -d "${INSTALL_DIR}" ]]; then
    fail "卸载后服务目录仍存在：${INSTALL_DIR}"
  fi

  cat <<EOF

============================================================
CloudSend ZEGO Token Service 卸载完成
============================================================

已处理：
- 停止服务：${SERVICE_NAME}
- 取消开机自启：${SERVICE_NAME}
- 删除 systemd 服务文件：${SERVICE_FILE}
- 删除服务目录：${INSTALL_DIR}
- 重载 systemd 并清理 failed 状态

如需重新部署，再次执行脚本并选择 1 即可。

============================================================
EOF
}

show_menu() {
  cat <<EOF

============================================================
CloudSend ZEGO Token Service 部署脚本
============================================================

1.安装Token服务
2.卸载Token服务

EOF
  read -r -p "请输入选项 [1/2]: " choice
  set_action "${choice}"
}

set_action() {
  local choice="${1:-}"
  case "${choice}" in
    1|install|INSTALL|"安装"|"安装Token服务")
      ACTION="install"
      ;;
    2|uninstall|UNINSTALL|remove|REMOVE|"卸载"|"卸载Token服务")
      ACTION="uninstall"
      ;;
    *)
      fail "无效选项：${choice}，请输入 1 或 2"
      ;;
  esac
}

main() {
  if [[ "$#" -gt 0 ]]; then
    set_action "$1"
  else
    show_menu
  fi

  case "${ACTION}" in
    install)
      install_token_service
      ;;
    uninstall)
      uninstall_token_service
      ;;
    *)
      fail "未选择有效操作"
      ;;
  esac
}

main "$@"

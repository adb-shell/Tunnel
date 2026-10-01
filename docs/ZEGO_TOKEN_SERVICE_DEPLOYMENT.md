# ZEGO Token Service Deployment / 一键脚本部署

最后同步：2026-07-01

本文是 CloudSend 1v1 ZEGO 语音通话 Token 服务部署文档。当前部署方式为 **Linux 服务器本机 IP + 端口直连**，不使用域名、SSL 或外层反向代理。

一键部署脚本：

```text
scripts/deploy_zego_token_service.sh
```

脚本不写死服务器公网 IP。它会在执行脚本的当前 Linux 服务器上监听：

```text
0.0.0.0:50003
```

外部访问方式为：

```text
http://<当前服务器公网IP>:50003
```

---

## 当前 ZEGO 配置

脚本已内置当前 ZEGO 信息，可直接执行：

```text
ZEGO_APP_ID=726162948
ZEGO_SERVER_SECRET=360a56369441ee640841cb4c82144186
VOICE_TOKEN_TTL_SECONDS=3600
VOICE_API_KEY=PHFfBRiEXVKFvEGD2cJp
```

注意：本部署文档和脚本已经按当前私有部署值补全，只适合私有工程/私有交付使用，不要公开到公共仓库、截图或外部文档。

---

## 一键部署

脚本适用于常见 systemd Linux 服务器，自动识别 `apt-get`、`dnf`、`yum`、`apk` 包管理器安装 Go/curl。宝塔只负责放行端口和日常查看，不再需要手动创建站点、域名或反向代理。

在服务器宝塔面板里先放行 TCP `50003`，然后把仓库中的脚本上传到服务器，例如：

```text
scripts/deploy_zego_token_service.sh
```

在服务器终端执行：

```bash
chmod +x deploy_zego_token_service.sh
sudo ./deploy_zego_token_service.sh
```

执行后会出现菜单：

```text
1.安装Token服务
2.卸载Token服务
```

输入 `1` 后开始安装 Token 服务。

脚本会自动完成：

- 检查是否 root 执行。
- 自动安装 Go 构建环境和 curl。
- 创建 `/www/wwwroot/cloudsend-zego-token`。
- 写入 `.env`、`go.mod`、`main.go`。
- 拉取 Go 依赖并构建 `cloudsend-zego-token`。
- 写入 systemd 服务 `/etc/systemd/system/cloudsend-zego-token.service`。
- 启动并设置开机自启。
- 自动测试：
  - `GET /api/v1/health`
  - `POST /`
  - 检查返回中是否包含 `callerToken` 和 `calleeToken`。

也可以直接传入参数跳过菜单：

```bash
sudo ./deploy_zego_token_service.sh install
```

---

## 一键卸载

执行同一个脚本：

```bash
sudo ./deploy_zego_token_service.sh
```

输入 `2` 后开始卸载 Token 服务。脚本会自动完成：

- 停止 `cloudsend-zego-token` systemd 服务。
- 取消开机自启。
- 删除 `/etc/systemd/system/cloudsend-zego-token.service`。
- 删除 `/www/wwwroot/cloudsend-zego-token` 服务目录。
- 执行 `systemctl daemon-reload`。
- 清理 `cloudsend-zego-token` 的 failed 状态。
- 删除脚本自检产生的临时文件。
- 自检服务是否仍在运行、服务文件是否残留、服务目录是否残留。

也可以直接传入参数跳过菜单：

```bash
sudo ./deploy_zego_token_service.sh uninstall
```

卸载不会删除 Go 环境、curl、宝塔配置、云服务器安全组规则，也不会修改 PC 源码。

---

## 自定义端口或参数

默认端口是 `50003`。如果以后要换端口，可在执行脚本时覆盖：

```bash
PORT=50003 sudo -E ./deploy_zego_token_service.sh
```

可覆盖变量：

| 变量 | 默认值 |
|---|---|
| `HOST` | `0.0.0.0` |
| `PORT` | `50003` |
| `INSTALL_DIR` | `/www/wwwroot/cloudsend-zego-token` |
| `SERVICE_NAME` | `cloudsend-zego-token` |
| `ZEGO_APP_ID` | `726162948` |
| `ZEGO_SERVER_SECRET` | `360a56369441ee640841cb4c82144186` |
| `VOICE_TOKEN_TTL_SECONDS` | `3600` |
| `VOICE_API_KEY` | `PHFfBRiEXVKFvEGD2cJp` |

脚本不会自动修改 PC 源码。更换服务器 IP 或端口后，需要同步 `src/client/helper.rs::DEFAULT_ZEGO_TOKEN_URL` 并重新编译 PC 端。

---

## 服务接口

当前 PC 兼容入口：

```text
POST http://<当前服务器公网IP>:50003
```

标准运维入口：

```text
GET  http://<当前服务器公网IP>:50003/api/v1/health
POST http://<当前服务器公网IP>:50003/api/v1/voice-call/create
```

请求头：

```text
Authorization: Bearer PHFfBRiEXVKFvEGD2cJp
Content-Type: application/json
```

请求体：

```json
{
  "pcPeerId": "pc_test",
  "androidPeerId": "android_test",
  "cloudsendSessionId": "sess_test"
}
```

正常响应应包含：

```json
{
  "rtcProvider": "zego",
  "appId": 726162948,
  "roomId": "cs_voice_xxx",
  "callerUserId": "pc_xxx",
  "calleeUserId": "android_xxx",
  "callerStreamId": "cs_voice_pub_xxx_pc",
  "calleeStreamId": "cs_voice_pub_xxx_android",
  "callerToken": "04...",
  "calleeToken": "04...",
  "expiresAt": 1780000000
}
```

---

## 运维命令

查看状态：

```bash
systemctl status cloudsend-zego-token --no-pager
```

实时日志：

```bash
journalctl -u cloudsend-zego-token -f
```

最近日志：

```bash
journalctl -u cloudsend-zego-token -n 100 --no-pager
```

重启服务：

```bash
systemctl restart cloudsend-zego-token
```

检查监听：

```bash
ss -lntp | grep 50003
```

---

## 验收清单

- 宝塔/云服务器安全组已放行 TCP `50003`。
- `systemctl status cloudsend-zego-token --no-pager` 显示 `active (running)`。
- `ss -lntp | grep 50003` 显示服务监听 `0.0.0.0:50003`。
- `curl http://127.0.0.1:50003/api/v1/health` 返回 `{"ok":true}`。
- `curl http://<当前服务器公网IP>:50003/api/v1/health` 返回 `{"ok":true}`。
- `POST http://<当前服务器公网IP>:50003` 返回 `callerToken` 和 `calleeToken`。
- PC 客户端 `DEFAULT_ZEGO_TOKEN_URL` 指向 `http://<当前服务器公网IP>:50003`。
- PC 发起语音通话后，服务日志出现 `POST /` 或 `POST /api/v1/voice-call/create`。
- ZEGO 后台能看到房间登录和推拉流数据。

---

## 常见问题

### 公网访问不通

检查宝塔和云服务器安全组是否放行 TCP `50003`：

```bash
ss -lntp | grep 50003
systemctl status cloudsend-zego-token --no-pager
```

### `401 unauthorized`

说明 PC / curl 的 Bearer key 与服务端 `.env` 中 `VOICE_API_KEY` 不一致：

```bash
grep '^VOICE_API_KEY=' /www/wwwroot/cloudsend-zego-token/.env
```

### `caller_token_failed`

说明 ZEGO token 生成失败，重点检查：

- `ZEGO_APP_ID` 是否是 `726162948`。
- `ZEGO_SERVER_SECRET` 是否属于同一个 ZEGO 项目。
- 修改 `.env` 后是否执行 `systemctl restart cloudsend-zego-token`。
- 服务器时间是否正常。

查看日志：

```bash
journalctl -u cloudsend-zego-token -n 100 --no-pager
```

### PC 仍然请求旧服务器

脚本只负责部署服务器，不会修改 PC 源码。检查：

```text
src/client/helper.rs::DEFAULT_ZEGO_TOKEN_URL
```

该值必须等于：

```text
http://<当前服务器公网IP>:50003
```

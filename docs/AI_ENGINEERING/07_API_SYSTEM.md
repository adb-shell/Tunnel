# Tunnel API 系统 / API System

接管基线：2026-07-12  
最近源码复核：2026-10-02，`5cee6921ec10971bb4654bc010f9328d7f70d02b`
状态：`verified` + `external` + `verification-required`

`verified` 仅表示本地源码支持，不表示服务端或运行验证通过。

## 1. 结论先行

2026-10-02 T004：ZEGO token request 与仓内服务示例改为 `tunnelSessionId`，版本 API typ 使用 `tunnel-client` / `tunnel-server`；现有远端服务没有随仓库文件自动升级。生产地址和 credentials 未变，部署契约与验证要求见`EXTERNAL_ASSET_REGISTRY.md`。

本仓库不是 Tunnel 后台仓库。它包含：

- Flutter 产品账号与设备 API client。
- Rust OIDC/account client。
- Rust heartbeat/config sync client。
- download 与 dormant record upload client。
- ZEGO Token service 的调用端和部署资料。

本仓库不包含：

- 产品 API server implementation。
- `hbbs` / `hbbr` server source。
- 数据库 schema、migration、ORM model 或备份任务。
- ZEGO Token service 的独立可审计后端工程。

因此账号表结构、token 签发规则、服务端授权、数据保留和多租户隔离都属于外部未接管资产。

## 2. API 客户端分层

| 层 | 主要职责 | 锚点 |
|---|---|---|
| Flutter user | login、current user、资格/时间校验 | `flutter/lib/models/user_model.dart` |
| Flutter address book | 设备、地址簿、共享和写权限 | `flutter/lib/models/ab_model.dart` |
| Flutter group | accessible device groups 与缓存 | `flutter/lib/models/group_model.dart` |
| Flutter HTTP common | API server、Bearer header、proxy bridge | `flutter/lib/common.dart` |
| Flutter HTTP transport | 无 proxy 时 Dart HTTP；有 proxy 时 Rust bridge | `flutter/lib/utils/http_service.dart` |
| Rust account | OIDC/account auth state | `src/hbbs_http/account.rs` |
| Rust sync | heartbeat、sysinfo、config、disconnect | `src/hbbs_http/sync.rs` |
| Rust downloader | async download job lifecycle | `src/hbbs_http/downloader.rs` |
| Rust record upload | recording upload loop，当前默认关闭 | `src/hbbs_http/record_upload.rs` |
| Rust HTTP client | proxy/TLS/client construction | `src/hbbs_http/http_client.rs`, `src/hbbs_http.rs` |
| ZEGO token client | RTC token acquisition | `src/client/helper.rs` |

## 3. 产品账号链

当前主产品账号链在 Flutter：

```text
login form
  -> POST product login endpoint
  -> access token
  -> POST currentUser
  -> validateUser()
  -> refresh address book and groups
```

`UserModel.validateUser()` 对 admin 直接通过；其他用户把非空 `email` 字段拆成到期时间与可选 machine code，后者与 `mainGetUuid()` 比较。`ChinaNetworkTimeService.getTime()` 先用 5 分钟缓存，再尝试 NTP、HTTP Date，最后退回本机时间。这些是客户端 policy，不可替代服务端认证与授权；`email` 在此承担的业务语义需要后端契约明确。

当前顺序存在本地状态问题：`login()` 先调用 `getLoginResponseFromAuthBody()`，其中 `_parseAndUpdateUser()` 已写入 `userName`、`user_info`、`user_email`，随后才执行资格校验。`login.dart` 的普通登录失败处理显示错误，但该路径未见对上述状态执行 `reset()`。这是静态确认的写入顺序与潜在陈旧登录状态，不是服务端授权绕过证明；验证选 `API-01`、`API-02`、`E2E-03`。

`getHttpHeaders()` 从本地 option 中取得 access token 并构造 Bearer header。任何日志、异常上报和诊断导出都必须对该 header 全量脱敏。

### 与 `verify_login()` 的区别

`src/common.rs::verify_login()` 当前直接返回 `true`，只影响使用它的 legacy/custom UI gate。它不是：

- 产品账号 API 登录。
- hbbs ID 注册。
- 远程 endpoint 的 password/click/2FA authentication。

文档和代码审查中禁止用一个“登录已绕过”的结论覆盖三个不同系统。

## 4. 地址簿、设备与分组

`AbModel` 和 `GroupModel` 导入 `utils/http_service.dart`，经统一 wrapper 调用外部 API：无 proxy 时使用 Dart `http`，有 proxy 时转入 Rust。它们提供：

- 地址簿加载与缓存。
- 设备/peer 元数据。
- 可写地址簿判定。
- 设备分组与 accessible group。
- 创建、更新、删除和共享类操作。

当前业务模型跨 `UserModel`、`AbModel`、`GroupModel`、`PeerModel` 和 `PeerTabModel` 分散，服务端 contract 未以 OpenAPI/JSON Schema 纳入仓库。字段变化主要依赖运行时 JSON 解析，存在 silent default 与 client/server drift 风险。

地址簿同时保留 legacy `/api/ab` GET/POST 整体同步与新 `/api/ab/peers`、`/api/ab/peer/*/{guid}`、`/api/ab/tag/*/{guid}` 分项操作；settings/personal 的 404 可回退 legacy。分组页通过 `/api/device-group/accessible`、`/api/users`、`/api/peers` 分页读取。客户端可写判定与缓存隔离只是客户端控制，server ACL、幂等、版本冲突与跨租户隔离仍为 `external`。

建议未来将以下资产纳入独立契约包：

- endpoint、method、request/response schema。
- error code 与可重试性。
- auth scope/role requirement。
- pagination、idempotency 和 concurrency semantics。
- client/server supported-version matrix。

## 5. Rust OIDC/account

`src/hbbs_http/account.rs` 管理 OIDC/account auth 流程、取消和结果状态：POST `/api/oidc/auth`，随后每秒 GET `/api/oidc/auth-query`，外层查询窗口为 180 秒。这里没有为每次 blocking request 设置独立 timeout；取消只修改 `keep_querying`，仍要等待当前请求结束，不能把 180 秒视为全部 I/O 的硬上限。

OIDC 与普通账号链不是同一个状态机，但 `remember_me` 会写入同一 `LocalConfig` 的 `access_token` / `user_info`，Flutter 的 OIDC 回调也复用 `getLoginResponseFromAuthBody()`。因此“身份域分开分析”不等于“当前 token storage 完全隔离”；issuer、logout、资格校验顺序需要共同验证。

维护时必须明确当前 UI 入口调用哪一条链，并避免：

- 同一 token key 被不同 issuer 复用。
- logout 只清理其中一个账户域。
- OIDC callback 与产品 API user state 相互覆盖。
- proxy/TLS policy 在 Dart 与 Rust 两套 client 中不一致。

## 6. Heartbeat 与配置同步

`src/hbbs_http/sync.rs` 负责：

- 周期 heartbeat。
- 系统信息版本比较与上传。
- 接收 config/state 更新。
- 接收服务端 disconnect 指令。
- 发布内部 signal。

静态审计显示 heartbeat/sysinfo 会携带设备、系统和连接相关信息；部分请求未观察到与产品 Bearer 一致的认证 header。需要后台 contract 与抓包共同确认实际鉴权方式。

具体源码：`sync.rs::start_hbbs_sync_async()` 每 3 秒 tick，无连接时 heartbeat 最多每 15 秒发送；sysinfo 失败重试使用 120 秒窗口。`/api/sysinfo_ver`、`/api/sysinfo`、`/api/heartbeat` 调用 `post_request(..., "")`，后者设 12 秒 timeout 和 JSON header，未加产品 Bearer。响应 `disconnect` 发布内部 signal，`strategy.config_options` 经 `handle_config_options()` 合并/删除配置项，未见客户端 key allowlist。当前可证明的是客户端缺少可见的响应身份/完整性校验，不能仅凭这些调用断言外部服务器没有任何认证。

该链路风险：

- 明文 HTTP 时泄露设备和连接元数据。
- server-driven disconnect/config 缺少可见的强鉴权说明。
- 后台字段变化可能直接影响 endpoint 行为。
- endpoint 迁移频繁，地址散落会增加配置漂移。

## 7. Downloader

`src/hbbs_http/downloader.rs` 使用全局 job map，支持：

- 下载到文件或内存。
- 查询进度与结果。
- cancel/remove。
- 可选 job-map 自动移除参数；不是自动删除下载文件的完整保证。

`download_file()` 用 URL 作为 job ID；同 URL 再调用直接返回已有 ID，即使目标路径不同。`do_download()` 先要求 HEAD 成功且存在 `Content-Length`，再 GET；GET 后未检查 HTTP status，没有显式总大小/内存上限、hash 或签名 gate。普通错误只写 `error`，取消分支才尝试移除 partial file。延迟移除 task 在该函数私有 `current_thread` runtime 上 `tokio::spawn` 后函数立即返回，其实际清理时机需专门验证，不能当作可靠 TTL。

正式安全审计需要验证：

- HTTP status 与 redirect policy。
- `Content-Length` 缺失、伪造和超大值。
- 内存下载 size cap。
- 目标路径、覆盖、symlink 和 partial file semantics。
- hash/signature verification。
- cancellation 后资源与临时文件清理。

在这些条件未明确前，不应把 downloader 当作可信更新通道。

## 8. Record Upload

`src/hbbs_http/record_upload.rs` 存在上传 loop，但私有 `ENABLE` 默认 false，仓库内未发现启用 setter/path。`src/server/video_service.rs::get_recorder()` 仅在 `is_enable()` 为 true 时连接上传 channel；录制与上传是不同能力。当前上传应分类为 dormant subsystem，而不是已上线能力。

若未来启用，必须先定义：

- 明确用户同意与录制指示。
- endpoint auth 与 TLS。
- 文件加密、保留期和删除。
- 失败重试、去重、流量限制。
- 审计事件与地区合规。

## 9. ZEGO Token API

调用端位于 `src/client/helper.rs`。当前风险：

- token endpoint 使用明文 HTTP。
- client credential 硬编码在源码/二进制可提取位置。
- repository 和部署资料中存在 credential 类型的字面值。

本轮只确认 credential 类型字面值存在于 tracked 文件，不验证其有效性、生产用途、当前公网可达性或历史传播范围。静态暴露仍需 owner 处置。

`scripts/deploy_zego_token_service.sh` 内嵌的 Go `/api/v1/voice-call/create`（兼容根路径 POST）已有静态 Bearer 比较、4 KiB request cap、ID 字符清洗和非空检查；默认 TTL 3600 秒，配置接受不小于 60 秒的值，未见上限。`GenerateToken04(..., "")` 的 payload 为空；创建 room/user/stream ID 不等于 token 已绑定 room privilege，也未见向产品后端核验 `pcPeerId` / `androidPeerId` / `tunnelSessionId`、replay 或 rate-limit。脚本是 partial server evidence，不能证明生产部署与它一致。

客户端不应持有可签发任意 token 的高权限 secret。目标设计应是：

```text
authenticated Tunnel session
  -> HTTPS token broker
  -> short-lived, room/user-bound token
  -> ZEGO SDK
```

broker 必须校验当前 Tunnel 用户、peer、room、用途、TTL 与 replay，不接受仅凭静态客户端 key 的无限制签发。

## 10. Token 与本地存储

当前存在多种 credential/domain：

- 产品 API access token。
- OIDC/account state。
- 远程 peer password cache。
- permanent password/default connect password。
- ZEGO token 与 token-service credential。
- rendezvous signed key/public key material。

它们必须分域管理，不得共享 storage key 或日志字段。已确认本地 password protection 使用可读设备 UUID 和固定 nonce 的弱保护方式，不能把它描述为平台级 secure storage。

未来目标：

- Android Keystore / iOS Keychain / Windows DPAPI 等平台密钥封装。
- access/refresh token 最小 TTL 与 rotation。
- logout/revoke 清理所有相关缓存。
- crash report 与 debug log 自动 redaction。

## 11. 数据库边界

仓库没有可确认的业务数据库。Flutter dependency 中即使存在数据库 package，也不能据此推断产品数据持久化实现。当前缓存主要由本地配置/JSON/model 机制承担。

后台接管仍缺少：

- DB engine/version。
- schema/migration。
- tenant boundary。
- backup/restore/RPO/RTO。
- PII inventory 和 retention。
- audit log。
- disaster recovery owner。

这些事实必须由后端与运维提供实际接口、配置和验证结果后确认。

## 12. HTTP Proxy Bridge 风险

`HttpService.sendRequest()` 在 proxy 开启时调用 `mainHttpRequest()`，`src/ui_interface.rs::http_request()` 将占位符和最终结果都写入 `ASYNC_HTTP_STATUS[url]`；Dart 每 100ms 用同一 URL 查询。相同 URL 的并发请求会共享同一槽位，存在覆盖/错配风险；读取未移除该槽位。Rust bridge 单请求设 12 秒 timeout，Dart 直接 HTTP 分支没有统一 timeout，poll loop 本身也没有 deadline。`http_client.rs` 在 proxy 配置/构建失败时回退直接 `Client::new()`，不能宣称严格遵循代理或统一网络策略。

未来应使用独立 request ID，并记录：method、normalized endpoint、timeout、status class、retry count；不得记录 token 或完整 body。

## 13. API 变更规则

- 先确认 API server 所属仓库和 owner；本仓库只能证明 client contract。
- endpoint 统一配置，不在多个 Dart/Rust/脚本位置复制常量。
- 新接口必须 HTTPS、认证、timeout、size limit 和 error mapping。
- 写操作定义 idempotency、重试和冲突策略。
- schema 变更同时提供向后兼容期。
- client validation 不能替代 server authorization。
- token/secret 不进源码、文档、脚本默认值、Git 历史或日志。
- 生产 endpoint 变更必须走发布与回滚审查，不能作为普通文本替换。

## 14. 待补资产

1. 产品 API/OpenAPI 或等价契约。
2. hbbs/hbbr 版本、配置和部署拓扑。
3. 数据库 schema、migration 和备份恢复说明。
4. token issuer、TTL、scope、rotation 与 revoke 规则。
5. 生产/测试环境清单及 owner。
6. 数据分类、跨境、retention 和删除策略。
7. ZEGO broker 独立受控 project/commit、dependency lock、tests/SBOM 与生产部署日志；当前 deployment script 内嵌 source 只算 partial evidence。

在这些资产到位前，`07_API_SYSTEM.md` 只代表 endpoint client 侧接管完成，不代表后台系统完成接管。

# CloudSend API 系统 / API System

接管基线：2026-07-12  
状态：`verified` + `external` + `verification-required`

## 1. 结论先行

本仓库不是 CloudSend 后台仓库。它包含：

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

`UserModel` 同时执行产品定制校验，包括 network time 与设备 UUID 相关逻辑。该校验属于客户端 policy，不可替代服务端认证与授权。

`getHttpHeaders()` 从本地 option 中取得 access token 并构造 Bearer header。任何日志、异常上报和诊断导出都必须对该 header 全量脱敏。

### 与 `verify_login()` 的区别

`src/common.rs::verify_login()` 当前近似绕过，只影响 legacy/custom UI 的局部 gate。它不是：

- 产品账号 API 登录。
- hbbs ID 注册。
- 远程 endpoint 的 password/click/2FA authentication。

文档和代码审查中禁止用一个“登录已绕过”的结论覆盖三个不同系统。

## 4. 地址簿、设备与分组

`AbModel` 和 `GroupModel` 直接通过 Flutter `http` 调用外部 API，提供：

- 地址簿加载与缓存。
- 设备/peer 元数据。
- 可写地址簿判定。
- 设备分组与 accessible group。
- 创建、更新、删除和共享类操作。

当前业务模型跨 `UserModel`、`AbModel`、`GroupModel`、`PeerModel` 和 `PeerTabModel` 分散，服务端 contract 未以 OpenAPI/JSON Schema 纳入仓库。字段变化主要依赖运行时 JSON 解析，存在 silent default 与 client/server drift 风险。

建议未来将以下资产纳入独立契约包：

- endpoint、method、request/response schema。
- error code 与可重试性。
- auth scope/role requirement。
- pagination、idempotency 和 concurrency semantics。
- client/server supported-version matrix。

## 5. Rust OIDC/account

`src/hbbs_http/account.rs` 管理 OIDC/account auth 流程、取消和结果状态。它与 Flutter 产品账号链共存，但不是同一个状态机。

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
- 可选自动删除。

正式安全审计需要验证：

- HTTP status 与 redirect policy。
- `Content-Length` 缺失、伪造和超大值。
- 内存下载 size cap。
- 目标路径、覆盖、symlink 和 partial file semantics。
- hash/signature verification。
- cancellation 后资源与临时文件清理。

在这些条件未明确前，不应把 downloader 当作可信更新通道。

## 8. Record Upload

`src/hbbs_http/record_upload.rs` 存在上传 loop，但 `ENABLE` 默认 false，仓库内未发现启用 setter/path。当前应分类为 dormant subsystem，而不是已上线能力。

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
- repository 和部署资料中存在真实 credential 类型的字面值。

客户端不应持有可签发任意 token 的高权限 secret。目标设计应是：

```text
authenticated CloudSend session
  -> HTTPS token broker
  -> short-lived, room/user-bound token
  -> ZEGO SDK
```

broker 必须校验当前 CloudSend 用户、peer、room、用途、TTL 与 replay，不接受仅凭静态客户端 key 的无限制签发。

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

这些必须由后端/运维资产补充后单独出具 API/数据接管报告。

## 12. HTTP Proxy Bridge 风险

Flutter 与 Rust 间存在以 URL 作为 async result key 的 HTTP proxy/bridge 机制。相同 URL 的并发请求可能覆盖结果或错配 waiter，需验证 request correlation 是否唯一。

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

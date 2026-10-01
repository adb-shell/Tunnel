# CloudSend 安全模型 / Security Model

接管基线：2026-07-12  
状态：`verified` + `inferred` + `external` + `verification-required`

> 本文是源码与公开仓库资产的静态安全接管，不是渗透测试。未测试任何 credential，有关值均不复述。当前结论足以判定：在 P0 人工处置和正式验证完成前，CloudSend 不应进入新的正式发布。

## 1. 总体判断

当前最重要的安全问题不是一般代码质量，而是多个边界可以组合：

```text
公开 credential / 共享远控凭据
  + 明文或 fail-open transport
  + 可改写配置的 Android deep link
  + 未签名/未哈希的 update 或 plugin
  + 高权限 Android/Windows runtime
  = 远程控制、数据泄露或代码执行的高影响路径
```

已确认的 incident fact：公开仓库及 Git 历史中存在真实 ZEGO/Token service credential 类型的字面值。应按已泄露处理；只删除当前文件内容不能撤销历史泄露。

静态发现但未动态复现的内容必须继续标 `verification-required`，包括握手降级、nonce reuse、deep-link 配置劫持、update/plugin 利用、DLL 劫持、JNI 悬空读等。

## 2. 保护资产

### 身份与密钥

- 产品账号 access/refresh token。
- endpoint password、permanent/default password、2FA/trusted-device state。
- rendezvous signed key/public key trust material。
- ZEGO server secret、token-service credential、room token。
- Android/Windows signing key、certificate、keystore。
- CI/CD secret 与发布权限。

### 用户数据

- 屏幕帧、录屏、摄像头、音频。
- 剪贴板、文件、terminal、tunnel 流量。
- 联系人/Dev selector 数据、地址簿、设备分组。
- peer/device ID、UUID、IP、system info、连接状态。
- 本地 ADB shell 和 `WRITE_SECURE_SETTINGS` 能力。

### 完整性与可用性

- rendezvous/API/relay/key 配置。
- CloudSend executable、SO/DLL、APK、driver、plugin、update。
- Android core service/screen-share state。
- Windows privacy/display recovery。
- hbbs/hbbr/API/ZEGO external services。

## 3. Actors 与 Trust Zones

| Zone | 默认信任 | 主要风险 |
|---|---|---|
| local user/device | 只信当前明确用户操作 | 其他本机 app、备份、恶意输入、低权限进程 |
| controller | 通过 endpoint auth 后只获授予 capability | 恶意/旧/修改版 controller |
| controlled endpoint | 强制最终 authorization | UI-only gate、兼容分支、权限漂移 |
| hbbs/hbbr | 只承担定义的 rendezvous/relay | 恶意/被劫持服务、降级、中间人 |
| product API/sync | 认证且完整的 control/data plane | plaintext、伪响应、过宽 control input |
| ZEGO/broker | 只签发短期绑定 token | 静态客户端 key、token 滥用、隐私 |
| build/CI/release | 可复现、最小权限、签名 | mutable dependency、未校验 binary、自动 push/upload |
| plugin/update | 仅接受已签名受信来源 | RCE、zip-slip、path/host 替换 |

连接通过认证不等于 controller 自动获得输入、文件、terminal、ADB、麦克风或配置权限。每个 capability 必须由受控端再次强制。

## 4. 身份与授权模型

CloudSend 至少存在六个身份域：

1. Flutter 产品账号。
2. Rust OIDC/account。
3. hbbs/rendezvous registration。
4. endpoint password/click/2FA/trusted-device login。
5. legacy/custom UI `verify_login()` gate。
6. ZEGO room/token authorization。

`src/common.rs::verify_login()` 当前直接成功，且 Flutter 存在 developer login bypass，但二者不等于自动绕过 endpoint password authentication。准确风险是：任何把这两个 gate 当作产品授权边界的功能都不安全。

### 已确认的认证风险

- 构建内置共享远控密码可由公开源码获知，设置 permanent password 的实现没有真正更新该值。
- Developer/toolbar 功能密码存在于公开客户端源码，只能算 UI gate。
- trusted-device 依据客户端可提交的 tuple，保留期长，缺少强密码学设备绑定。
- product access token 保存在普通本地配置。
- Android custom commands/input 在受控端未统一执行 keyboard permission gate。

目标模型：每设备生成高熵独立凭据，支持安全轮换/撤销；敏感 token 进入平台 secure storage；所有 capability 在受控端检查；开发入口在 production build 中不可达且不承载授权。

## 5. Transport 与 Cryptography

### S-TR-01：Peer handshake fail-open — Critical / E1

`src/client.rs::secure_connection(...)` 和服务端相关路径在 signed key 缺失、无效或 mismatch 的部分情形继续 non-secure connection。`secure_tcp()` 的异常/parse/timeout 处理也需要复核，且主要 controller 调用路径存在被绕开迹象。

要求：握手和 key validation 默认 fail-closed；如必须兼容 legacy peer，使用显式、可审计、默认关闭的 compatibility policy，并在 UI 清晰警告。

### S-TR-02：Secretbox direction nonce reuse — Critical / E1

`libs/hbb_common/src/tcp.rs` 双向使用相同 secretbox key，send/receive counter 从相同初值开始。若相同 key 下方向 nonce 重复，会破坏机密性安全假设。

要求：独立 directional keys 或 direction-separated nonce domain；定义 rollover、reconnect 和 retransmission 行为；由密码学 review 和 wire test 验证。

### S-TR-03：Weak local protection — High / E1

本地敏感配置以可读取 device UUID 派生 key，并对多项数据复用固定 zero nonce。这是可恢复 obfuscation，不是可靠 at-rest encryption。

要求：使用 Android Keystore、iOS Keychain、Windows DPAPI/credential protection 等平台封装，随机 nonce，版本化迁移和安全清除。

### S-TR-04：Plaintext product control/data — Critical / E1

默认 API、login、heartbeat/sysinfo、record upload 与 ZEGO token 路径存在 HTTP。登录请求可含原始产品账号密码；heartbeat response 能驱动 disconnect/config changes；录屏和 voice metadata 都是高敏数据。

要求：全面 HTTPS、证书校验、服务端认证、response integrity、最小 scope、timeout/size limit、replay protection。server-driven disconnect/config 必须作为高权限控制输入验证。

## 6. Credential Incident

### 受影响资产路径

- `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`
- `scripts/deploy_zego_token_service.sh`
- `src/client/helper.rs`
- 相关 Git 历史与已构建客户端

这里只列路径，不列值。

### 必须由 owner 执行的顺序

1. 确认 credential owner、环境、权限和部署依赖。
2. 轮换/撤销已暴露 credential，审计服务用量和访问日志。
3. 停用客户端共享 bearer secret，改为登录/session 绑定的短期 token broker。
4. 更新服务端和客户端配置，验证 rollback credential。
5. 将 docs/scripts 模板化并接入 secret store。
6. 评估 forks、clones、caches、artifacts 和 history rewrite。
7. 最后决定是否在协调窗口执行 history rewrite/force-push。

轮换、撤销、服务日志访问、history rewrite、force-push 和部署均未在本轮执行，必须取得明确授权。

## 7. Android Security Boundary

### Deep link — Critical / E1

Manifest 注册的 `cloudsend` custom scheme 缺少 host/path 限制。Flutter config URI path 可无确认导入并绕过 server validation，改写 rendezvous、relay、API 与信任 key；其他 URI 还可携带 connection password 并触发 remote/file/tunnel/terminal path，且完整 URI 可能进入日志。

要求：

- 使用 verified App Link 或严格 allowlisted host/path/action。
- 配置/信任 key 变更显示 diff、要求本机用户确认和强认证。
- 禁止 URI 携带或记录 password/token。
- 校验 source、scheme ownership、nonce/expiry 和 one-time intent。
- `password`/config actions 默认拒绝，不能依赖当前 no-op setter 偶然无害。

### Runtime permission — High / E1

Accessibility、overlay、全文件、录音、系统设置、MediaProjection、本地 ADB 和 `WRITE_SECURE_SETTINGS` 组合形成极高权限面。BootReceiver exported + debug action 允许其他 app 触发 core start，虽不等于获得 MediaProjection。

要求：permission purpose matrix、最小化、明确 user gesture、持续可见状态、撤销/恢复、审计和 store-policy review。声明 backup/data extraction policy，避免 token/config 被设备备份。

### Native memory — High/Critical / E1

- ImageReader plane DirectBuffer pointer 被 Rust 保存后，Kotlin `Image.use` 立即关闭，存在 use-after-release 路径。
- `static mut PIXEL_SIZE*` 跨 JNI/capture/video threads 读写，存在 Rust data race/UB。
- screenshot shared buffer、scale factor 和 MainService replacement GlobalRef 也有竞态/生命周期风险。

要求：JNI callback 内复制到 Rust-owned buffer、移除 unsynchronized mutable globals、建立明确 state/lock/atomic contract，并用 CheckJNI/sanitizer/real-device matrix 验证。

### ADB 与 Voice — High / E1

- Local ADB UI 可运行任意 shell 并自动尝试 `pm grant`，必须保持本地、显式、可撤销和可审计。
- PC remote ADB 尚不存在，不能复用现有输入/terminal 协议直接开放。
- ZEGO token client credential 可提取；Android incoming call 自动接受，取消/返回也进入接受语义，缺少明确拒绝。

要求：voice 必须当前用户明确接受，麦克风状态持续可见；ADB 远程化必须新协议、allowlist、consent、audit 和 kill switch。

## 8. Update、Plugin 与 Supply Chain

### Update — Critical / E1

Flutter update path 接受 URL，下载后执行 EXE/MSI，未见强制 signature/hash/publisher/host allowlist。旧 Rust updater 主要以 size 判断完成并可能提权启动。

要求：固定受信 host、HTTPS、manifest signature、artifact hash、Authenticode/平台签名、最大 size、唯一临时文件、原子替换、downgrade prevention 和 rollback。任何 verification failure 都 hard fail。

### Plugin archive — High conditional / E1

Plugin ZIP 解压使用 archive-provided name 拼接 target path，未见 `enclosed_name()` 等 containment check，存在 zip-slip path。当前 plugin source/feature 可能 dormant，但一旦启用就是高风险 sink。

要求：canonical containment、禁止 absolute/parent/symlink escape、文件数/大小上限、签名 manifest、隔离执行和最小 capability。

### Dependencies/artifacts — High / E1

- 多个 Cargo Git dependency 未在 manifest 固定 `rev`；lockfile 只固定当前 snapshot。
- Gradle/Maven、vcpkg、Python 和 Actions 存在 mutable source/online download。
- ignored `libadb.so`、Windows driver/helper/DLL 缺少统一 source revision/hash/license registry。
- 未见完整 SBOM、NOTICE、binary provenance。

目标：建立 `THIRD_PARTY_ARTIFACTS.lock`/等价清单，记录 URL label、version、source commit、SHA-256、signature/publisher、license、owner；构建使用 locked/verified dependency。

## 9. Windows Security Boundary

- Privacy topmost path 会将 `WindowInjection.dll` 注入其他进程，当前只检查文件存在。
- Virtual display dylib 按裸库名加载，存在 search-order risk。
- device installer、Amyuni/usbmmidd/print driver/helper 从本地 cache 复制，应用层 provenance/hash/signature 不完整。
- 某些 privacy modes 在 hook/capture verification 失败时可能 fail-open。
- `new-build.cmd` 未见 Windows Authenticode signing stage。

要求：

- 安装目录和 DLL search 使用固定绝对路径/安全 loader flag。
- 验证 Authenticode publisher、certificate chain、timestamp 和 pinned hash。
- privacy 任一 hook/window/capture protection 失败都向 UI 返回真实失败并恢复安全状态。
- portable directory 不作为高权限进程加载任意 DLL 的信任根。
- EXE/MSI/DLL/driver 均进入签名与 provenance gate。

## 10. CI/CD 与 Release

现有 workflows 虽主要为 manual `workflow_dispatch`，但不能视为可信发布系统：

- 存在 RustDesk/旧版本/旧 artifact 名称和上游发布目标残留。
- 有 workflow 自动 `git commit`/`git push`。
- Android/Windows/macOS 存在 debug-key 或 unsigned publish path。
- 多个 third-party Action 使用 mutable branch/tag。
- 多数 workflow 未显式声明最小 `permissions`。
- 构建期下载 binary 未普遍固定 hash。

发布 gate：

- CloudSend-only workflow，默认 `contents: read`。
- third-party Actions 固定 full commit SHA。
- untrusted PR、build、sign、publish job 隔离。
- GitHub Environment 人工审批和受保护 signing secret。
- 缺 version/name/package/SO/DLL/deep-link 一致性、SBOM、hash、signature 或正式测试时 hard fail。
- 禁止自动 commit/push；发布动作需要独立用户授权。

## 11. Risk Register

| ID | Severity | Evidence | Finding | Current disposition |
|---|---|---|---|---|
| SEC-001 | Critical | confirmed exposure | public/history credential literals | open incident；待 owner rotation |
| SEC-002 | Critical | E1 | shared fixed remote password + ineffective setter | release blocker |
| SEC-003 | Critical | E1 | peer secure handshake fail-open | protocol review/negative test |
| SEC-004 | Critical | E1 | same key/nonce sequence in both directions | crypto redesign/review |
| SEC-005 | Critical | E1 | plaintext login/API/sync/ZEGO paths | HTTPS/auth migration |
| SEC-006 | Critical | E1 | unauthenticated heartbeat control/config | fail-closed control auth |
| SEC-007 | Critical | E1 | Android config deep-link trust rewrite | disable/harden + consent |
| SEC-008 | Critical | E1 | unverified update execution | suspend until signed manifest |
| SEC-009 | High | E1 conditional | plugin archive traversal | block enable/install |
| SEC-010 | High/Critical | E1 | Android DirectBuffer lifetime/static mut UB | ownership fix + sanitizer |
| SEC-011 | High | E1 | endpoint input permission/UI-only gates | server enforcement |
| SEC-012 | High | E1 | ZEGO static client auth/auto-accept | consent/token redesign |
| SEC-013 | High/Critical | E1 | Windows unverified DLL/driver/injection + unsigned release | signed provenance gate |
| SEC-014 | High | E1 | mutable CI/dependencies/auto push | isolate/harden workflows |
| SEC-015 | High | E1 | weak local token/password protection | platform secure storage |
| SEC-016 | High | E1 | developer/account gate bypasses | remove from production boundary |

Severity reflects static path and impact; except SEC-001, exploit success was not dynamically proven in this takeover.

## 12. Positive Controls

- CloudSend controller does enforce relay-only and rejects explicit direct entry.
- Endpoint still retains password/click/2FA/trusted-device authentication code.
- HTTP client did not show an explicit disable-TLS-verification option.
- Android AccessibilityService is not exported; MediaProjection permission remains system-mediated.
- Local ADB pairing code is passed through stdin and no PC remote ADB protocol was found.
- Workflows are manual-triggered today and repository has no GitHub Release observed at takeover time.

These controls reduce some paths but do not close the P0/P1 findings.

## 13. Security Change Rules

- Start with trust boundary and untrusted-source → privileged-sink trace.
- Auth, crypto, config, update and permission failures default fail-closed.
- UI visibility, obfuscation, developer password and connection state are not authorization.
- Every high-privilege action needs current actor, scope, consent, audit, revoke and recovery.
- Secrets never enter client binaries, source, docs, scripts, logs or Git history.
- Unsafe memory requires explicit ownership/threading proof and dynamic diagnostics.
- Download/plugin/update/driver requires fixed provenance, signature/hash, containment and bounds.
- Release requires signed artifacts, SBOM, source provenance and rollback.
- Any production/security action needs explicit authority and isolated verification.

## 14. Security Verification Needs

Formal isolated verification must cover:

- signed-key missing/invalid/mismatch and secure handshake fail-closed。
- directional secretbox key/nonce uniqueness and counter rollover。
- HTTP→HTTPS migration, MITM resistance and authenticated heartbeat/config。
- deep-link hostile source, config/key rewrite, URI redaction and user consent。
- Android input permission off, DirectBuffer lifetime, data races and service replacement。
- ADB local-only reachability and permission revoke。
- ZEGO explicit accept/reject, token scope/TTL/rate limit and microphone consent。
- update wrong host/hash/signature/publisher/downgrade and atomic rollback。
- plugin absolute/parent/symlink/oversize archive cases。
- Windows DLL search/signature/driver/privacy failure recovery。
- CI permissions, immutable actions, secret boundaries, SBOM and artifact signing。

具体项目构建命令、环境、目录和矩阵见 `09_DEBUG_SYSTEM.md`《编译验证需求》。本轮未执行上述动态验证。

## 15. 外部与合规缺口

- hbbs/hbbr production version/config/key/log 不在仓库。
- product backend、DB、tenant ACL、token revoke、audit、backup 不在仓库。
- ZEGO broker 缺独立、可审计工程。
- upstream RustDesk fork point 未记录，影响 CVE/diff/license traceability。
- AGPL-3.0 distribution、Corresponding Source、network-use 和 third-party notices 需专业法律意见；本文不是法律建议。
- CloudSend 自有 security contact、incident SLA、disclosure policy、SBOM/NOTICE 尚未建立。

这些资产补齐前，安全接管只能覆盖本仓库端点源码，不能宣称全系统安全闭环。

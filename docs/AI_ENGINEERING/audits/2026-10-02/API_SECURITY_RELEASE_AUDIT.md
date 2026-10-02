# API / Security / Release 源码复核

日期：2026-10-02（Asia/Shanghai）
Task：`T-2026-10-02-001`；Change Event：`CE-20261002-T001-02`
Source：`5cee6921ec10971bb4654bc010f9328d7f70d02b`；Baseline：`CS-BL-2026-10-02-5cee692`
权限：C0 本地只读核验 + C1 分配文档编辑；最高验证 V0。

本报告记录本轮读到的源码与边界，供后续功能修改定位。它不替代持续维护的 [API 系统](../../07_API_SYSTEM.md)、[构建系统](../../08_BUILD_SYSTEM.md)、[调试体系](../../09_DEBUG_SYSTEM.md)、[安全模型](../../10_SECURITY_MODEL.md) 和 [外部资产表](../../../../EXTERNAL_ASSET_REGISTRY.md)。没有测试 credential、调用服务、执行项目 build/test/analyze/codegen、安装依赖、Git 写入、删除/移动或发布。

## 1. 本轮结论及可信边界

- 本仓库有产品 API/OIDC clients、heartbeat/config client、downloader、dormant record uploader，以及内嵌 Go ZEGO broker 的部署脚本；没有可接管的独立产品 backend/database 工程或生产证据。`src/server.rs` 是受控端，不能当作 `hbbs`。
- 当前 API/OIDC 状态机不同，却共用 `access_token` / `user_info` 本地存储。API 资格校验存在先写本地状态再校验的顺序风险；不能把 UI 登录状态作为服务端授权。
- 当前 tracked credential 类型字面值、HTTP、更新/插件 sink、权限和日志风险仍在；本轮不确认凭据有效性或攻击成功。旧文档的 public remote、59 commits 和 7 月本机 native assets 是历史声明，当前本地无法重证。
- `ADB-CODE/`、`LADB/`、`flutter/android/app/src/main/jniLibs/` 在当前 worktree 均不存在。三个历史 ABI hash 保留为交接线索；当前状态是 `MISSING`，不是 `LOCAL-ONLY`。
- `zego_express_engine` 已在 manifest 声明但 tracked pub lock 没有 entry。正式构建、运行、服务端 ACL、签名/SBOM/provenance 仍缺证据，不能宣称 release-ready。

`verified` 表示直接可读源码事实；`inferred` 表示基于路径的风险推断；`historical` 表示旧记录；`external` 表示仓外未知；需要执行的行为均 `verification-required`。不把检索未命中等同于不存在所有可能实现。

## 2. 阅读和核验覆盖

| 范围 | 核验入口及关键路径 | 当前边界 |
|---|---|---|
| Account/UI contract | `common/hbbs/hbbs.dart::LoginRequest/UserPayload`，`common/widgets/login.dart`，`models/user_model.dart` | 普通登录、OIDC 回调、2FA/email 分支、refresh/logout、expiry/UUID；没有真实账号调用 |
| AB/group/device | `models/ab_model.dart::LegacyAb/Ab`，`models/group_model.dart`，`common.dart::getHttpHeaders` | legacy/new API、分页、缓存 token、客户端可写判断；server ACL unknown |
| HTTP/bridge | `utils/http_service.dart` → `mainHttpRequest` → `ui_interface.rs::http_request` → `common.rs::http_request_sync` → `hbbs_http/http_client.rs` | 两套传输和 proxy 回退；未进行网络抓包 |
| Rust OIDC | `hbbs_http/account.rs::OidcSession`，`hbbs_http.rs::HbbHttpResponse` | auth/query/cancel/state/local token；issuer/backend 未验证 |
| Sync/upload/download | `hbbs_http/sync.rs`、`downloader.rs`、`record_upload.rs`，`server/video_service.rs::get_recorder` | 完整 client 核心状态与副作用；upload 保持 dormant |
| ZEGO broker | `client/helper.rs`，`scripts/deploy_zego_token_service.sh` | client 请求/response/control payload、脚本内嵌 Go/server/systemd；不是已部署工程证明 |
| Security cross-check | Android MethodChannel/token、PortForward、MultiClipboards、terminal registry、Windows hook/recovery；update/plugin/local crypt | 与平台 agents 交叉检查源码，不执行攻击或运行测试 |
| Build/dependency/artifact | `Cargo.toml`/lock、pub manifest/lock、Gradle、`build.rs`、`libs/hbb_common/build.rs`、`build.sh`、`new-build.cmd`、`build.py`、`env.sh` | 静态入口、生成条件、toolchain 合约、外部资产；没有 build |
| CI/testing | 12 个 `.github/workflows/*.yml` 的触发/调用/风险入口，`flutter/test/cm_test.dart`、test dependency、API 模块 test 标记 | 不是所有 CI shell 的逐行安全证明；远端 permissions/checks/runs 未检查 |
| Historical materials | 旧 `07`—`10`、External Registry、Baseline/ADR、`ENGINEERING_BASELINE`、ADB/ZEGO/security 文档 | 比较关键结论；不复制旧生产地址或 credential 值 |

路径中未带根的 Dart 文件均位于 `flutter/lib/`，Rust 文件均位于 `src/`。本报告的行号是当前源快照导航，不代替函数名；后续代码改动应重查。

## 3. 身份、状态与数据地图

| 身份/状态域 | Source of truth | 持久化/出口 | 不能推导的事实 |
|---|---|---|---|
| 产品账号 | `UserModel` 与外部 `/api/login`、`/api/currentUser` | LocalConfig access token/user info、AB/group refresh | 不能由客户端 `isLogin` 证明 server scope/ACL |
| OIDC | Rust `OIDC_SESSION` auth/query 状态 | remember-me 写相同 access token/user info keys，Flutter 复用 auth body parser | 不是独立 secure storage，也不是 peer password |
| 客户端资格 policy | `validateUser()`、`ChinaNetworkTimeService` | `email` 被解释为 expiry + optional machine code | 客户端时间/UUID 检查不等于后台 subscription enforcement |
| 远端认证 | `Connection` password/click/2FA/trusted-device | session authorized/capability 状态 | 产品账号或 legacy `verify_login()` 不能代替它 |
| Heartbeat/pro 状态 | sync `PRO`、sysinfo hash/version、strategy timestamp | Config options、disconnect broadcast | `PRO` 不是当前产品用户权限证明 |
| ZEGO | `ZegoVoiceCallInfo`、room/user/stream/token | caller token 留 PC，callee token 随 control payload 到受控端 | token 有值、room ID 唯一不等于用户同意或 broker ACL |

`UserModel.validateUser()` 对 admin 直接通过；非 admin 的空 `email` 也没有到期/机器绑定检查。非空值按前 12 位解释年月日时分，可选 `@` 后字符串与 UUID 比较。网络时间按 NTP → HTTP Date → 本机时间回退；旧文档只写“网络时间校验”会掩盖回退边界。

`login()` 在 `user_model.dart:370` 调 `getLoginResponseFromAuthBody()`；后者在 `:396` 更新本地用户，之后 `:373` 才做资格校验。普通登录 UI `login.dart:546` 失败分支显示错误但未见 reset。此顺序可能留下错误的本地已登录状态；没有证据表明外部 API 会授权该用户，也没有运行复现。

## 4. 当前客户端 API 契约

只记录相对 route、字段名和来源；不记录真实 host、ID、token 或请求数据。后端 OpenAPI/schema/ACL/retention 未提供，下表是 client-observed contract。

| 子系统 | Method / route | Request / response / 身份 | Timeout、分页、错误与副作用 |
|---|---|---|---|
| Login | POST `/api/login` | `LoginRequest` 可带 username/password、id/uuid、autoLogin/type、verification/tfa/secret、deviceInfo；返回 type/token/user | 非 200 或 JSON error 抛错；Dart direct transport 无统一 deadline；账号状态写入顺序见上 |
| Current user | POST `/api/currentUser` | Bearer + id/uuid；`UserPayload` | 400/401 reset；其他异常保留 networkError/本地缓存，finally 刷新 AB/group |
| Logout | POST `/api/logout` | Bearer + id/uuid | caller 2 秒 timeout；finally reset token/user/expiry、AB/group；服务端 revoke 是否成功 unknown |
| Login options | GET `/api/login-options` | `oidc/` 或 `common-oidc/` provider 列表 | 异常返回空列表；provider callback 安全由外部 owner 证明 |
| OIDC | POST `/api/oidc/auth`；GET `/api/oidc/auth-query` | op/id/uuid/deviceInfo；code/URL；query code/id/uuid；AuthBody | 每秒 query，外层 180 秒；blocking request 无显式独立 timeout；cancel flag 不取消在途 request；remember-me 写 LocalConfig |
| AB discovery | POST `/api/ab/settings`、`/api/ab/personal`、`/api/ab/shared/profiles` | Bearer；personal/shared guid、权限/配置 | settings/personal 404 回退 legacy；shared profiles pageSize 100 |
| Legacy AB | GET/POST `/api/ab` | Bearer；peers/tags/data 整体数据，含兼容压缩分支 | 整体 push；client write/cache 不证明 server conflict/idempotency |
| New AB peers | POST `/api/ab/peers`；POST `peer/add/{guid}`；PUT `peer/update/{guid}`；DELETE `peer/{guid}`（后三者均在 `/api/ab/` 下） | Bearer；guid、peer ids/metadata/alias/tags/password fields | 分页拉取，单项写操作；无可确认 versioned error schema 或 idempotency key |
| New AB tags | POST `/api/ab/tags/{guid}`；POST `tag/add/{guid}`；PUT `tag/rename/{guid}` / `tag/update/{guid}`；DELETE `tag/{guid}`（均在 `/api/ab/` 下） | Bearer；tag 名称、颜色、guid | response helper 将 HTTP/JSON error 转为客户端错误；server ACL unknown |
| Group/device | GET `/api/device-group/accessible`、`/api/users`、`/api/peers` | Bearer；分页 group/user/peer 元数据 | pageSize 100；直到 `current * pageSize >= total`；未知 schema/巨大 total 需限制测试 |
| Sync | POST `/api/sysinfo_ver`、`/api/sysinfo`、`/api/heartbeat` | 当前调用传空 header；设备/sysinfo/连接元数据、strategy timestamp | 3 秒 tick、无连接 15 秒 heartbeat、sysinfo 120 秒失败间隔；shared helper 12 秒 timeout；响应控制 disconnect/config |
| Recording upload | POST `/api/record`，type=new/part/tail/remove | filename、offset、length 与 bytes；未见 auth header | dormant；若启用需独立 consent/auth/retention/大小/幂等设计 |
| ZEGO | POST 配置 token URL；脚本提供 `/api/v1/voice-call/create` 与根路径兼容入口 | 静态 Bearer；pcPeerId/androidPeerId/tunnelSessionId；返回 RTC IDs/tokens/expiry | client 8 秒 timeout、检查 status/结构；未见产品授权校验或 room privilege token payload |

AB/group cache 保存 access-token 关联，load 时匹配当前 token，减少不同登录缓存混用；这不等于加密隔离。所有返回数据都要按用户/设备/联系人敏感数据处理。数据库表、租户关系、备份、删除和保留策略仍属 `EXT-DATA-001`。

## 5. HTTP、sync 与 downloader 维护重点

### HTTP wrapper

`HttpService.sendRequest()` 查询 proxy 状态：无 proxy 时直接 Dart HTTP；有 proxy 时调用 FRB，Rust `ASYNC_HTTP_STATUS` 以 URL 为键保存占位和最终响应，Dart 每 100ms 轮询。相同 URL 的并发请求没有唯一 request ID，可共享/覆盖结果；读取不移除 map entry。Rust HTTP bridge 设置 12 秒 timeout，Dart direct 分支与 poll loop 没有对应统一 deadline。

`configure_http_client!` 正常构建先禁用系统环境 proxy，再读取项目 socks/proxy 配置；配置/构建失败回退 `Client::new()`。此错误回退绕过项目配置路径，不能宣称 fail-closed proxy。未发现该 builder 显式关闭 TLS 证书验证，但这不能保护 HTTP endpoint。

### Sync

`sync.rs:270` 的 heartbeat response 解析后，`:277` 发布 disconnect；`:289` 接收 strategy，`:313` 的 `handle_config_options()` 按任意返回 key 合并或删除 Config options。字段解析与 sysinfo hash 不是 response authentication；调用方 `post_request(..., "")` 没有产品 Bearer。外部防护不可从 client 补猜。

### Downloader/update

`download_file()` URL-key 去重；目标已存在会拒绝，但主 update caller 可先移除目标。`do_download()` 要求 HEAD 成功并有 Content-Length，GET 本身未检查 status；没有显式最大体积、hash/signature、host allowlist。普通错误记录 error，取消路径才尝试删 partial file，`remove()` 只移除 map。延迟 map cleanup 用该函数私有 runtime 上的 spawned task，函数返回后的执行保证未验证。

`UpdateProgress` 主要以已下载字节达到 total 判断完成，再发 `update-me`；`flutter_ffi.rs:2622` 按临时路径执行 EXE/MSI，`updater.rs::get_download_file_from_url()` 取 URL 最后一段拼 temp path。静态路径未见可信 manifest/signature/hash gate，需 `API-06` 与 `SEC-008` 负向验证。

`record_upload.rs` 私有 ENABLE 默认 false，仅有 reader；`video_service.rs:967` 的 gate 保持 uploader 不接入正常 recording。不能写成“没有录制功能”，也不能写成“已经上传录屏”。

## 6. ZEGO 服务边界

Go source 内嵌在 `write_project_files()` heredoc；脚本写 go.mod（固定 token helper pseudo-version）和 main.go，运行时 `go mod tidy`，没有独立 tracked go.sum/tests/CI。`install_token_service()` 同时包含包安装、构建、root systemd 服务、健康/token 请求；`uninstall_token_service()` 删除安装目录。仅阅读这些函数，不运行其任何模式。

已有控制：POST method、静态 Bearer 精确比较、空/占位 key 拒绝、4 KiB body cap、ID 字符清洗/非空、默认一小时 TTL、JSON status errors、`.env` 0600。

缺口：client 能提取共享 bearer；ID 与产品账号/session 未核验；TTL 配置只有下限；`GenerateToken04` 最后一项 payload 为空；未见 room privilege、replay/rate-limit、HTTP server timeouts、应用 TLS、最小服务用户、正式 production logs/provenance。反向代理/防火墙是否另有防护 unknown。需要 broker owner 提供真实契约和脱敏部署证据，不能把脚本等同生产事实。

`client/helper.rs::new_zego_voice_call_request()` 把 caller token 留在 PC、只发 callee token 是已有隔离控制；`payload_json()` 仍含当前角色 token，跨 JNI/MethodChannel 日志必须 redact。

## 7. 安全复核清单

持续 ID/优先级在 `10_SECURITY_MODEL.md`，本表解释本轮证据限制。没有 exploit reproduction 或 credential validity test。

| ID | Source / 前置条件 | 本轮可以确认 | 未证明 / 验证需求 |
|---|---|---|---|
| SEC-001/002 | ZEGO docs/script/helper、`config.rs::set_permanent_password/get_permanent_password` | credential 类型字面值、共享 permanent 常量、setter 不更新配置 | 当前有效性、公开远端/旧历史/生产使用；owner incident work |
| SEC-005/006 | `common.rs::get_api_server_`、sync POST/response handlers | HTTP 路径、空 auth header、config/disconnect sink | 实际生产认证/网络边界；API-04/05 |
| SEC-008/009 | Flutter update→FFI executable；plugin `manager.rs::do_install_file` | 下载后执行缺完整 integrity gate；archive name 直接 join target | host/feature/preconditions、实际利用；API-06/RST-05 |
| SEC-011 | authorized message→Android MultiClipboards→JNI，input 路径由平台报告覆盖 | Android MultiClipboards 缺 desktop 的 `self.clipboard` gate | permission-off 真机效果；AND-05/NET-04/E2E-04 |
| SEC-012 | broker static key/empty token payload；Android voice consent 由平台报告覆盖 | broker 业务授权缺口，不是完全无基础检查 | RTC 实际 scope/accept/mic behavior；API-08/AND-07 |
| SEC-013 | `win_virtual_display.rs:511/513/524` | hook error 被允许后返回成功；unhook error 可提前退出 restore | 真实 WinAPI failure/recovery；WIN-03/04/06 |
| SEC-014 | CI actions/auto-push、build dependencies/native cache | mutable refs、维护 workflow commit/push、无完整本地 provenance gate | 远端 permission/environment/保护策略；Release owner |
| SEC-015/016 | `password_security.rs::symmetric_crypt`、product/OIDC login | UUID-derived key + zero nonce；普通本地 token；资格校验之前写 user state | 具体缓存暴露/失败UI影响；API-01/02/E2E-03 |
| SEC-017 | `connection.rs:2095/2112`，tunnel enabled 且可达协议入口 | PortForward connect 位于 endpoint username/password/click/2FA 之前 | 未认证完整转发不成立于此证据；需隔离 outbound-connect oracle |
| SEC-018 | MethodChannel `debugPrint`、RDP args `println!`、OIDC result log | token/password/code-bearing 数据可到日志 sink | release 构建日志可见性/采集/实际泄露；仅 synthetic secret 验证 |
| SEC-019 | 已登录 + terminal enabled + 已知仍活跃 service ID | registry 仅 ID key、无 owner 字段、client 可提交 ID 重新接入 | 跨 peer 效果未复现；不是未登录 shell；NET-04/06/RST-05 |

Peer handshake、directional nonce、Android DirectBuffer/static mut、deep links 的细节由同轮 Rust/Network/Android 报告维护，本文不以概览替代完整链路。实现修复需 C2；主动扫描、fuzzing、设备、服务测试和 credential 操作需相应 C3。

## 8. 构建、生成物、依赖与测试

| 项目 | 当前可核对事实 | 验收缺口 |
|---|---|---|
| Product | Cargo `tunnel 5.2.1` / edition 2021 / MSRV 1.75；Flutter `flutter_hbb 5.2.1+59` | 未改版本；发布渠道未知 |
| Android | applicationId `com.tunnel.app`；SDK 34/33/21；`libtunnel.so`；NDK path 27.2.12479018 | 各 ROM/API/ABI 运行与真实签名未验 |
| Windows | `new-build.cmd`、`tunnel.dll`、`PC-Bulid/<source-folder>.exe` | 外部 VS/Flutter/LLVM/vcpkg/cache/signature 未验 |
| FRB | Rust manifest `=1.80`；Dart/generated 1.80.1；额外 freezed/header 产物 | Android 主要检查缺文件，Windows 等待存在；不能证明生成一致 |
| Protobuf/version | `libs/hbb_common/build.rs` 由 proto 生成至 OUT_DIR；root build.rs 调 `gen_version()` 写 ignored `src/version.rs` | 源码生成物缺失是预期状态，不能误当已删源文件 |
| ZEGO | pubspec `^3.24.1`；pubspec.lock 无 entry | 实际 resolved SDK/native binary/version/provenance 未验 |
| Other deps | Cargo lock 固定当前 snapshot，部分 manifest Git deps 没有 rev；Gradle/Maven/vcpkg/脚本多个 resolver | clean reproducibility、SBOM、license、供应链与锁更新 review |
| Automated tests | 仅有 `flutter/test/cm_test.dart` 手工 UI harness，`flutter_test` 注释；API Rust 模块未检得 `#[test]` | TEST_MATRIX 是计划，不是执行覆盖；Rust全仓测试标记也不等同通过 |

`build.sh` mode 1 为 arm64 signed APK，mode 2 为三个 ABI universal signed APK；它可能安装依赖、改变外部 checkout、patch SDK、codegen、清理并签名。`new-build.cmd` 会改变默认 Rust toolchain、pub get、构建并生成 package。`build.py` 不支持 `--release` CLI 参数，内部才向 Cargo/Flutter 传 release。所有入口须按实际副作用审查授权。

12 个 workflow 均含 manual dispatch；三个有 `workflow_call`；未见 push/PR/schedule 自动触发。`update_submodules.yml` 有 commit/push，`flutter-build.yml` 保留 upstream version/name、unsigned/publish 分支和 mutable actions。manual 本身不等于质量、安全或发布通过。远端 required checks/runner/environment/secrets 本轮未读。

Lock hash 对比必须区分 HEAD blob（LF）与工作区（CRLF）；不能把 EOL byte hash 变化当 dependency drift。本轮真实 manifest/lock 不一致指 ZEGO 缺 entry，具体基线 hash 由 `docs/BASELINE/` 记录。

## 9. 外部资产交接清单

沿用 [External Asset Registry](../../../../EXTERNAL_ASSET_REGISTRY.md) 的 ID，不新建第二套 inventory。只接收脱敏材料，不把 secret/生产地址放进报告。

| IDs | 需要项目方/owner 提供 | 用途 |
|---|---|---|
| EXT-HIST-001 | 原始 Git bundle/repo、fork commit、旧 baseline 对照、来源/修改归属 | 重建 upstream/CVE/授权历史，区分真实源码 delta 与快照缺历史 |
| EXT-SVC-001 / EXT-DATA-001 | backend repo/version、OpenAPI/error schema、ACL/tenant model、migration、backup/restore/retention 证据 | API和数据接管；不推测表结构 |
| EXT-SVC-002/003 | hbbs/hbbr source/version、脱敏 topology/config、test environment 与 owner | relay/auth/interop、容量与恢复 |
| EXT-SVC-004/005 | broker 独立工程/lock、scope/TTL/rate-limit/revoke、incident owner；RTC SDK/provenance | 语音权限、token 与发布复现 |
| EXT-BIN-ADB-001 / EXT-REF-ADB-002/003 | 三 ABI binary 或可复现源码、source revision、hash/license/recipe；需要的参考原始材料 | 当前文件缺失；历史 hash 不够 |
| EXT-WIN-001—009 | active Amyuni、injection、printer artifacts 的 version/source/hash/signature/license；dormant IDD 单独标识 | 高权限Windows资产与回滚 |
| EXT-BUILD-* / EXT-SIGN-* / EXT-CI-001 | 正式 host/toolchain manifest、受控 artifact store、签名 custody、脱敏 CI gate/runner 权限证据 | G2/G3 readiness |
| EXT-REL-001 / EXT-OPS-* / EXT-COMP-001 | 渠道 owner、rollout/rollback、监控告警、SBOM/NOTICE/source-offer 材料 | 完整交付与长期维护 |

## 10. 《编译验证需求》与交付

本任务不运行以下命令；正式环境、精确副作用和签名/清理/依赖操作须先单独授权。完整说明见 `09_DEBUG_SYSTEM.md`。

| 命令/任务 | 环境 / 执行目录 | 验证目标 / case IDs |
|---|---|---|
| `cargo build --release --features flutter`、`cargo test`；CLI另行受控feature build | 正式Rust/native dependencies；repo root | RST-01/02/03/06；真实 target inventory，不把无测试算通过 |
| `flutter pub get`、`flutter analyze`；建立有效targets后 `flutter test` | 正式Flutter/Dart；`flutter/` | FLT-01/05/06；ZEGO lock resolution、bridge与state/error行为 |
| `./build.sh 1`、`./build.sh 2` | 正式Linux/SDK/NDK/keystore/native assets；repo root | AND-06/07/08、RST-03；三ABI、JNI、ADB和RTC依赖 |
| `new-build.cmd` | 正式Windows/MSVC/cache/signing条件；repo root | WIN-03/04/05/06/08；privacy恢复、driver/signature、产物身份 |
| 隔离 API/broker/peer contract suite（命令由owner设计） | 脱敏测试 credential、隔离服务/可恢复设备 | API-01—09、NET-04/06、E2E-03/04/05；失败顺序、scope、replay、并发、大小/timeout、token日志 |

本轮实际验证：本地源码读取/定向检索、manifest/工作流/路径存在性、历史文档差异与跨域路径交叉核对；最高 V0。所有 V1—V5 `NOT_RUN`，外部资产缺口 `MISSING/EXTERNAL`。没有把任何正式测试记为 PASS。

本 agent 修改：`07_API_SYSTEM.md`、`08_BUILD_SYSTEM.md`、`09_DEBUG_SYSTEM.md`、`10_SECURITY_MODEL.md`、`EXTERNAL_ASSET_REGISTRY.md` 和本报告；未修改运行代码/脚本/锁文件。主 agent 负责 Task/Event/State/Current Work 的共同记忆同步。后续按具体需求挑选模块、风险 ID 和测试 cases，无需再次建立重复文档体系。

# CloudSend Commercial Test Matrix

最后更新：2026-07-12  
Baseline：`CS-BL-2026-07-12-77062b4`  
当前执行状态：本次封版只完成 V0 文档/源码静态盘点；下列 V1—V5 cases 均未执行

> 本矩阵定义覆盖和证据，不授权 build、test、analyze、codegen、device、network、production 或 release。V1—V4 需要独立 C3 + G2；V5 另需 G3、staging/production 和 rollout 授权。

## 1. Result and Evidence Rules

| Result | 含义 |
|---|---|
| `PASS` | 在指定 Baseline、环境和步骤上满足 oracle，并有完整 evidence |
| `FAIL` | 结果不满足 oracle；不得降级验收 |
| `BLOCKED` | 缺环境、owner、asset、contract 或 authority |
| `NOT_RUN` | 尚未执行 |
| `N/A` | 确认不适用，并记录理由/批准者 |
| `WAIVED` | 风险被明确接受；不计 PASS |

每个 PASS 必须绑定：Task/ADR/Baseline ID、commit + dirty state、toolchain/service/device、steps/command + exit/result、expected/actual、sanitized evidence、executor、independent reviewer、evidence owner、日期和 retention location。

## 2. Verification Levels

| Level | Evidence | Authority |
|---|---|---|
| V0 | diff/path/reference/schema/docs/Skill/static secret pattern | C0/C1 task scope |
| V1 | format/lint/unit/static analyzer | explicit C3 + suitable toolchain |
| V2 | formal product build/package | G2 + explicit C3 |
| V3 | Android/Windows device/OS/runtime regression | G2 + explicit device C3 |
| V4 | isolated hbbs/hbbr/API/ZEGO/security integration | G2 + isolated environment authority |
| V5 | staging rollout、monitoring and rollback observation | G3 + production/release authority |

Static review, an empty test suite, a build without runtime cases or “no error observed” never upgrades evidence automatically.

## 3. Formal Environment IDs

| ID | Required Environment | Evidence Owner |
|---|---|---|
| `E-A` | Controlled Linux Android build host；Android 10/13/14/15；AOSP/Huawei/ColorOS；arm64-v8a/armeabi-v7a/x86_64；isolated test account/relay | Android QA owner |
| `E-W` | Formal Windows host；supported Win10/11；installed/portable；single/multi display；Amyuni present/absent；recoverable snapshot | Windows QA owner |
| `E-F` | Baseline-pinned Flutter/Dart；desktop + Android；multi-window capable | Flutter QA owner |
| `E-R` | Baseline-pinned Rust/MSVC/native dependencies；workspace features/platform targets | Rust QA owner |
| `E-N` | Isolated versioned hbbs/hbbr；old/new controller/endpoint；latency/loss injection；disposable credentials | Network/infra evidence owner |
| `E-P` | Mock/contract + isolated backend/DB/token broker；synthetic data；clock/TTL control；DB snapshot | Backend/data evidence owner |

## 4. Current Test Asset Baseline

- Rust source contains 55 `#[test]` / `#[tokio::test]` marker lines across 26 files；none were executed in this seal.
- `flutter/test/cm_test.dart` is a manual `flutter run` harness；`flutter_test` remains commented in `pubspec.yaml`, so it is not an automated suite.
- No tracked Kotlin `src/test` or `src/androidTest` files were found.
- No repository evidence proves a complete Windows、Network or API E2E harness or current formal results.

These are coverage facts, not pass results.

## 5. Android Suite

| ID | P | Target | Positive + Negative / Compatibility / Recovery | Pass Oracle | Level / Env | Owners |
|---|---|---|---|---|---|---|
| AND-01 | P0 | Core/share lifecycle + permission | explicit start/stop；hidden boot/reconnect/legacy paths；Android 14+ one-shot token；projection stop/service death | hidden paths never prompt；projection loss only loses share；core/JNI/relay remain；guarded recovery has no loop | V3 / E-A | Android / Android QA |
| AND-02 | P0 | First frame and frame source | normal/SKL/ignore/one-shot；RGBA + Texture；static Huawei screen；no frame/failure | real frame clears waiting；normal refresh never auto-fallback/rebind/prompt/forge state | V3 / E-A | Android+Flutter / Android QA |
| AND-03 | P1 | Display metrics | rotate、split、fold、narrow/small、density change；rapid changes | no divide-by-zero、token restart or rebind storm；orientation/topology recover | V3 / E-A | Android / Android QA |
| AND-04 | P0 | Reconnect | 10s/70s loss、latency、jitter、relay failure、process background | one 2.5s timer；60s behavior and last frame hold；strict relay；no timer pileup | V3+V4 / E-A+E-N | Android+Network / QA+infra |
| AND-05 | P0 | Input authorization | permission on/off；mouse/key/touch；command masks 37–44；modified controller | controlled endpoint rejects unauthorized actions；UI hiding is not oracle | V3+V4 / E-A+E-N | Android+Network+Security / QA |
| AND-06 | P0 | Local ADB/LADB | API 29/30+；pair/connect/mDNS/fallback/cancel/high output/concurrent/page dispose；3 ABI | local-only reachability；safe cancel/timeout；no deadlock/race；revocation works | V3 / E-A | Android+Security / Android QA |
| AND-07 | P0 | ZEGO voice consent | incoming/accept/reject/cancel/background/busy/reconnect/mic denial | no auto mic；reject is distinct；busy state clears；token/room scoped | V3+V4 / E-A+E-P | Android+Flutter+API+Security / QA+backend |
| AND-08 | P0 | Packaging/permissions | clean approved native assets；ABI/JNI；manifest/boot/exported components；permission revoke | hashes/license/provenance match；all ABIs link；no hidden privilege grant | V2+V3 / E-A | Android+Release+Security / Release evidence owner |

## 6. Windows Suite

| ID | P | Target | Positive + Negative / Compatibility / Recovery | Pass Oracle | Level / Env | Owners |
|---|---|---|---|---|---|---|
| WIN-01 | P1 | Capture/fallback | DXGI/GDI/magnifier；single/multi screen；display change/lock | correct first frame/display；fallback truthful；resource cleanup | V3 / E-W | Rust/Windows / Windows QA |
| WIN-02 | P0 | Portable/input/SAS/UAC | installed/portable；keyboard layouts；UAC/secure desktop；permission off | authorized remote input only；failure visible；service/helper cleanup | V3 / E-W | Rust/Windows+Security / QA |
| WIN-03 | P0 | Privacy modes | topmost/exclude/magnifier/virtual；entry/exit；unsupported OS/driver missing | protection success is real or fail-closed；desktop/input always recover | V3 / E-W | Windows+Security / QA |
| WIN-04 | P0 | Virtual display recovery | add/remove/multi；kill/crash/reboot；Windows 10/11/24H2；third-party VD | original topology restored；no fast-loop crash、orphan display or input loss | V3 / E-W | Windows+Release / QA |
| WIN-05 | P0 | Driver/native artifacts | clean install/update/uninstall/rollback；bad hash/signature/publisher/ABI | untrusted artifact hard-fails；rollback succeeds；reboot state known | V2+V3 / E-W | Release+Security / Release evidence owner |
| WIN-06 | P0 | Privacy hooks/injection | local input blocked/remote allowed；DLL missing/tampered；escape/recovery | no unsafe loader fallback；hook/window failure restores safe state | V3 / E-W | Windows+Security / QA |
| WIN-07 | P1 | Remote printer | install/update/uninstall；consent；default/selected printer；large/concurrent/invalid XPS | no partial state、OOM or handle leak；cancel/cleanup and OS compatibility proven | V3 / E-W | Windows+Security / QA |
| WIN-08 | P1 | Artifact smoke | installed/portable launch、upgrade、uninstall、repair、wrong architecture | name/version/hash/signature correct；clean removal and rollback | V2+V3 / E-W | Release / Release evidence owner |

## 7. Flutter Suite

| ID | P | Target | Positive + Negative / Compatibility / Recovery | Pass Oracle | Level / Env | Owners |
|---|---|---|---|---|---|---|
| FLT-01 | P1 | Analyzer/unit/widget baseline | analyzer + actual test discovery；failure/empty suite | configured tests run；zero discovered is not PASS | V1 / E-F | Flutter / Flutter QA |
| FLT-02 | P1 | Startup/multi-window/session | mobile/desktop/CM；open/reuse/hide/close；wrong/late window ID | events stay in correct engine/session；teardown complete | V1+V3 / E-F | Flutter / QA |
| FLT-03 | P0 | EventToUI/first frame | session ID；RGBA/Texture；duplicate/late/malformed event | correct model receives event；both frame types clear waiting | V1+V3 / E-F+E-A | Flutter+Rust / QA |
| FLT-04 | P0 | Lifecycle/reconnect | timers/subscriptions/controllers；logout/nav/background/close；race/late callback | no leak、double reconnect、post-dispose update or stale client | V1+V3 / E-F | Flutter / QA |
| FLT-05 | P0 | Bridge contract | FRB/handwritten FFI/MethodChannel；generated drift；bad ABI/error | signatures match all sides；generated diff reviewed；errors bounded | V1+V2 / E-F+E-R | Flutter+Rust+Android / QA |
| FLT-06 | P1 | Product flows | account/file/terminal/ADB/ZEGO success、deny、timeout、back navigation | state/error/consent remains domain-correct；no sensitive logging | V1+V3+V4 / E-F+E-A+E-P | Flutter+API+Security / QA |
| FLT-07 | P2 | UX/accessibility | narrow/wide、localization、keyboard/focus、window close/restore | no inaccessible controls、layout loss or stale dialog overlay | V1+V3 / E-F | Flutter / QA |

## 8. Rust Suite

| ID | P | Target | Positive + Negative / Compatibility / Recovery | Pass Oracle | Level / Env | Owners |
|---|---|---|---|---|---|---|
| RST-01 | P1 | Workspace/features/cfg | all workspace crates；flutter/cli/platform features；missing optional deps | intended matrix compiles；dormant feature failures reported, not hidden | V1+V2 / E-R | Rust+Release / Rust QA |
| RST-02 | P1 | Existing inline tests | all discovered test targets；zero/skipped/panic | 55 marker baseline reconciled with executed inventory；all required pass | V1 / E-R | Rust / QA |
| RST-03 | P0 | FFI/JNI/generated bridge | symbols、types、ownership、pkg2230/ffi drift、bad inputs | ABI matches consumers；foreign memory lifetime proven | V1+V2+V3 / E-R+E-A | Rust+Android+Flutter / QA |
| RST-04 | P0 | Unsafe/concurrency | raw frame、GlobalRef、static state、shutdown/replacement、panic/error | no race/UAF/alias violation；resources released；failure bounded | V1+V3 / E-R+E-A | Rust+Security / QA |
| RST-05 | P0 | File/clipboard/terminal/path | traversal/symlink/oversize/cancel/partial/concurrent/untrusted command | containment、permission、size and cleanup hold | V1+V4 / E-R+E-N | Rust+Network+Security / QA |
| RST-06 | P1 | Config/process/artifact identity | corrupt/old config；role args；SO/DLL names；migration rollback | correct defaults/migration；no role or artifact ambiguity | V1+V2 / E-R | Rust+Release / QA |
| RST-07 | P1 | Fuzz/property | protocol/path/parser corpus；malformed/large/random input | no panic/hang/OOM/escape；repro corpus retained sanitized | V4 / isolated | Rust+Security / Security evidence owner |

## 9. Network Suite

| ID | P | Target | Positive + Negative / Compatibility / Recovery | Pass Oracle | Level / Env | Owners |
|---|---|---|---|---|---|---|
| NET-01 | P0 | Controller strict relay | normal connect；explicit IP/domain；UDP/IPv6/direct candidates | controller uses relay and rejects direct entry/candidates | V4 / E-N | Network / infra evidence owner |
| NET-02 | P0 | Rendezvous/relay recovery | resolve/register/relay loss；latency/loss/60s+ disconnect | bounded retry、no resource leak、truthful state and recovery | V4 / E-N | Network / infra |
| NET-03 | P0 | Handshake/crypto | missing/invalid/mismatched key、tamper、downgrade、directional nonce | failures are fail-closed；keys/nonces direction-separated | V4 / E-N | Network+Security / Security evidence owner |
| NET-04 | P0 | Authentication/capability | password/click/2FA/trusted device；permission on/off；replay | endpoint enforces auth and each capability；revocation works | V4 / E-N | Network+Security / QA |
| NET-05 | P0 | Protocol compatibility | old/new controller/endpoint；unknown fields/commands；duplicate/out-of-order | published numbers unchanged；unknown behavior safe；matrix documented | V4 / E-N | Network+Rust / Network QA |
| NET-06 | P0 | Service transport | video/input/file/terminal/tunnel interruption、malicious path、oversize | permission/bounds/cleanup hold across reconnect/cancel | V4 / E-N | Network+Rust / QA |
| NET-07 | P1 | Voice control | invite/accept/reject/cancel/busy/reconnect/platform unknown | control state consistent；no stale busy or implicit media consent | V4 / E-N+E-P | Network+API+Flutter / QA |
| NET-08 | P1 | Endpoint direct compatibility boundary | compatible endpoint requests with/without force relay | retained endpoint behavior matches policy；not mislabeled global relay-only | V4 / E-N | Network / infra |

## 10. API and Data Suite

| ID | P | Target | Positive + Negative / Compatibility / Recovery | Pass Oracle | Level / Env | Owners |
|---|---|---|---|---|---|---|
| API-01 | P0 | Product login/token | login/logout、expiry、revoke、clock skew、bad token | server authorization and local cleanup; no token log | V4 / E-P | API+Security / backend evidence owner |
| API-02 | P1 | Identity separation | Flutter product account、Rust OIDC、endpoint auth cross-state | no domain token/state substitutes another | V1+V4 / E-F+E-P | API+Flutter+Network / QA |
| API-03 | P1 | AB/device/group contracts | CRUD、old/new schema、pagination、idempotency、conflict/concurrency | versioned contract and deterministic conflict behavior | V4 / E-P | API / backend QA |
| API-04 | P0 | Heartbeat/config/disconnect | TLS、server auth、replay/tamper/timeout/stale response | unauthenticated control rejected；safe retry/rollback | V4 / E-P | API+Security / backend QA |
| API-05 | P0 | HTTP policy | 2xx/4xx/5xx、timeout、malformed/oversize、concurrent same URL | unique request IDs、size/time bounds、redaction and correct errors | V1+V4 / E-P | API / QA |
| API-06 | P0 | Downloader | redirect/host、path/symlink、partial/cancel、hash/size | approved host/integrity/containment；partial cleanup | V4 / E-P | API+Security / QA |
| API-07 | P0 | Record upload dormant | all normal builds/configs；attempted enable path | remains unreachable/disabled until new ADR + consent/security | V0+V2 / E-R | API+Release+Security / QA |
| API-08 | P0 | ZEGO broker | user/peer/room scope、TTL、replay、rate limit、TLS、revoke | short scoped token；no client signing secret；fail-closed | V4 / E-P | API+Security / backend evidence owner |
| API-09 | P0 | External DB migration/recovery | expand/contract、tenant isolation、backup/restore、RPO/RTO | migration and rollback preserve isolation/data；owner evidence | V4 / E-P | Backend/Data / data evidence owner |

## 11. Cross-domain Release Journeys

| ID | P | Journey | Required Result |
|---|---|---|---|
| E2E-01 | P0 | Android relay → auth → share → first frame → input → loss/reconnect | AND/FLT/RST/NET cases remain consistent；no permission or fallback drift |
| E2E-02 | P0 | Windows capture → input → privacy → forced failure → topology restore | real desktop/input/topology fully restored |
| E2E-03 | P0 | Product account/device → remote connect → logout/revoke | identity domains stay isolated；revocation clears capability |
| E2E-04 | P0 | Permission on/off file/terminal/clipboard with interruption | endpoint denies unauthorized action and cleans partial state |
| E2E-05 | P0 | ZEGO invite → consent/reject → busy clear/reconnect | no auto accept/mic；token and state expire correctly |

Every release-affecting task selects all impacted domain cases and at least the matching E2E journey. Omission needs explicit rationale and owner approval.

## 12. Negative, Compatibility and Rollback Gate

- P0/P1 applicable cases must PASS before release；P2 non-PASS needs explicit risk acceptance.
- `BLOCKED`、`NOT_RUN` and `WAIVED` never count as PASS.
- Auth、permission、crypto、signature、hash、path containment and update failures must fail closed.
- Protocol/schema/config/driver changes require old/new compatibility and downgrade/rollback cases.
- State、driver、display、database or installer changes require recovery/rollback PASS, not only happy-path PASS.
- External backend/hbbs/hbbr/database claims cannot PASS from client-only repository evidence.

## 13. Evidence Package Template

```text
Task / ADR / Baseline ID:
Commit + dirty state:
Artifact/source hash:
Toolchain/service/device/OS/ROM/ABI:
TEST_MATRIX case ID:
Steps or command + exit/result:
Expected / actual:
Sanitized log/screenshot/dump/metrics:
Negative / compatibility / rollback result:
Executor / independent reviewer / evidence owner:
UTC + local timestamp:
Retention location:
```

Never include token、password、credential、production URL/IP、peer/device ID、contacts、file/clipboard/terminal content or PII.

## 14. Formal Verification Request

When execution is needed but not authorized, output and stop:

```text
《编译验证需求》
- 命令：<exact approved command set>
- 环境要求：<E-A/E-W/E-F/E-R/E-N/E-P and prerequisites>
- 执行目录：<absolute/declared project directory>
- 验证目标：<case IDs and observable pass/fail oracle>
```

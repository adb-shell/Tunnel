---
name: cloudsend-rust-engineer
description: Analyze and maintain CloudSend Rust implementation mechanisms: workspace crates, core services, Flutter FFI, JNI, unsafe ownership, concurrency, native ABI, and Windows capture/input/privacy/virtual-display runtime. Use for Rust implementation and Cargo mechanics while preserving Network, API, Android, Flutter, Security, and Release semantic ownership.
---

# CloudSend Rust Engineer

## Purpose

Maintain Rust behavior without losing platform, ABI, protocol, lifetime, or compatibility constraints. Treat unsafe and foreign-memory changes as explicit ownership designs, not local edits.

## Read First

Resolve all paths from the CloudSend repository root. Complete the mandatory baseline in `PROJECT_START_HERE.md`, then read `.codex/AI_RULES.md`, `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`, `docs/AI_ENGINEERING/01_ARCHITECTURE.md`, `docs/AI_ENGINEERING/02_SOURCE_MAP.md`, `docs/AI_ENGINEERING/03_MODULE_DESIGN.md`, and the relevant full-path platform, network, or security document. Inspect root `Cargo.toml` and the target crate manifest before changing Rust. Follow protocol T0—T8 and stop at the confirmation gate before mutation.

For development, record the Baseline ID and related ADRs in `TASK_TEMPLATE.md`, then select Rust and cross-domain cases from `TEST_MATRIX.md`.

## Routing Boundaries

- Own Rust implementation mechanics, unsafe/FFI/ABI, task/thread/lifetime behavior, crate boundaries and Windows native runtime implementation.
- Peer/rendezvous/wire semantics remain Network-led; product HTTP/OIDC/backend contracts remain API-led; Android OS lifecycle remains Android-led; build/release reproducibility remains Release-led.
- Use `cloudsend-master` when a Rust API/ABI change requires coordinated Flutter, Kotlin, protocol or external-contract changes.
- Add Security review for raw memory, authentication, crypto, path handling, injection, driver or privileged behavior.

## Responsibilities

- Identify the active crate/module/feature/platform path and every consumer.
- Define ownership, lifetime, concurrency, cancellation, cleanup and error contracts.
- Preserve Rust 1.75 / edition 2021 and generated-code boundaries.
- Implement semantic decisions made by the owning Network/API/Android/Release domain without silently changing them.
- Produce a feature/platform verification matrix and clearly separate static evidence from formal compilation.

## Workflow

### 1. Locate the active implementation

- Identify crate, feature gates, platform `cfg`, active module exports, and all call sites.
- Distinguish source from generated files and compatibility copies.
- For Android JNI, treat `pkg2230.rs` as active and inspect `ffi.rs` for drift.
- For Windows virtual display, confirm Amyuni vs dormant RustDesk IDD before using configuration keys.

### 2. State the contract

- Record inputs, outputs, ownership, thread/task, lifetime, cancellation, error behavior, and platform assumptions.
- For unsafe code, explain why each pointer/reference remains valid and synchronized.
- For protocol-facing code, record authentication and permission gates.
- For long-running tasks, identify shutdown and reconnect behavior.

### 3. Trace the full impact

- Flutter export: `src/flutter_ffi.rs` -> FRB generated Rust/Dart -> callers.
- Protocol: client -> protobuf -> server -> platform service.
- Android JNI: exported symbol -> Kotlin native declaration -> Service/Accessibility callback.
- Windows: helper DLL/driver/config key -> recovery and cleanup path.
- Cargo: workspace feature/dependency -> lockfile/toolchain/platform build.

### 4. Make a minimal safe change

- Preserve Rust 1.75 and edition 2021 unless an approved toolchain migration exists.
- Prefer owned buffers and explicit state objects over borrowed JNI memory or `static mut`.
- Use atomics only for single-value invariants; use a lock/state machine for coordinated fields.
- Propagate errors with context while redacting credentials and PII.
- Keep protocol/auth failures fail-closed unless an explicitly approved compatibility path says otherwise.

### 5. Review and verify

- Review unsafe blocks separately for aliasing, lifetime, alignment, thread safety, and panic behavior.
- Check every feature/platform branch touched.
- Check generated bridge drift without hand-editing generated output.
- Request formal Cargo/platform verification when builds are not authorized.
- Update domain docs and memory only when an authorized implementation changes a runtime fact.

## Project-Specific Invariants

- `LoginConfigHandler` currently forces CloudSend controller relay; do not infer removal of endpoint direct/NAT compatibility.
- `src/common.rs::verify_login()` is not remote endpoint authentication.
- Android raw frame ownership must not outlive the Java/Kotlin buffer owner.
- JNI GlobalRef replacement and destruction must be explicit and race-safe.
- File/terminal paths are untrusted peer input.
- `record_upload` is dormant while its enable state remains unreachable/false.
- Current Windows virtual display is Amyuni.

## Forbidden Actions

- Do not run Cargo build/test or platform builds without current authorization.
- Do not upgrade MSRV/edition/dependencies as a side effect.
- Do not manually edit generated FRB/protobuf artifacts as the final fix.
- Do not add unsynchronized global mutable state or retain foreign pointers without ownership proof.
- Do not weaken authentication, encryption, permission, path, or size checks for compatibility convenience.
- Do not change crate/SO/DLL names, features, version, or wire fields without full-chain approval.
- Do not expose secret values in source, tests, logs, comments, or reports.
- Do not stage, commit, push, merge, rebase, branch, delete, change versions, sign, package, upload, deploy, or release without explicit authority.

## Checklist

- [ ] Active module and feature/platform gates identified.
- [ ] Callers, consumers, generated bridges, and compatibility copies checked.
- [ ] Ownership, lifetime, concurrency, cancellation, and cleanup documented.
- [ ] Authentication/authorization and untrusted input boundaries checked.
- [ ] Rust 1.75/edition 2021 compatibility preserved.
- [ ] Error paths release resources and remain fail-closed.
- [ ] No secret or PII enters logs/output.
- [ ] If authorized implementation facts changed, domain docs and decision/task memory are updated.
- [ ] Formal verification request or results provided.

## Verification

When authorized, select the smallest relevant Cargo feature/platform matrix, then the product build. Include sanitizer or platform diagnostics for unsafe/JNI work. When not authorized, provide 《编译验证需求》 with exact command, formal environment, directory, and observable targets; never claim static source review proves compilation or runtime safety.

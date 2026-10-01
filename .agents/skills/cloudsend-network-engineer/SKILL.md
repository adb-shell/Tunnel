---
name: cloudsend-network-engineer
description: Analyze and maintain CloudSend peer/rendezvous/session wire semantics: controller relay policy, protobuf compatibility, endpoint authentication/authorization, encryption, reconnect, file, terminal, tunnel, and voice-control messages. Use for protocol and hbbs/hbbr contract tasks regardless of implementation language; product HTTP/OIDC belongs to the API domain.
---

# CloudSend Network Engineer

## Purpose

Maintain wire behavior with explicit trust boundaries, compatibility, authorization, and failure semantics. Separate repository endpoint facts from external hbbs/hbbr infrastructure assumptions.

## Read First

Resolve all paths from the CloudSend repository root. Complete the mandatory baseline in `PROJECT_START_HERE.md`, then read `.codex/AI_RULES.md`, `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`, `docs/AI_ENGINEERING/06_NETWORK_PROTOCOL.md`, `docs/AI_ENGINEERING/10_SECURITY_MODEL.md`, and the relevant full-path platform/API document. Inspect `libs/hbb_common/protos/`, `src/client.rs`, `src/client/io_loop.rs`, `src/server/connection.rs`, and `src/rendezvous_mediator.rs` for the requested path. Follow protocol T0—T8 and stop at the confirmation gate before mutation.

For development, record the Baseline ID and related ADRs in `TASK_TEMPLATE.md`, then select NET and affected E2E cases from `TEST_MATRIX.md`.

## Routing Boundaries

- Own peer/rendezvous/session wire behavior regardless of whether the implementation is Rust, protobuf, Dart or Kotlin.
- Route Rust ownership/concurrency/ABI mechanics to `cloudsend-rust-engineer` and product HTTP/OIDC/account authentication to `cloudsend-api-engineer`.
- Add Security review for crypto, trust, permission, downgrade, malicious peer input and sensitive transport.
- Use `cloudsend-master` when protocol changes affect multiple platform clients, generated bridges or external hbbs/hbbr assumptions.

## Responsibilities

- Define controller/endpoint roles, connection checkpoints and trust boundaries.
- Preserve protobuf field compatibility, unknown behavior and version-skew matrices.
- Enforce authentication and capability permission at the controlled endpoint.
- Separate CloudSend controller relay-only policy from endpoint direct/NAT compatibility.
- Specify transport negative tests, external hbbs/hbbr requirements and fail-closed behavior.

## Core Boundaries

- CloudSend controller currently forces relay and rejects explicit direct addresses.
- Controlled endpoint code still retains direct server, LAN, NAT, PunchHole, and compatibility behavior.
- `verify_login()` is a legacy UI gate, not endpoint authentication.
- Peer invitation authorization, ZEGO token authorization, and microphone consent are distinct.

## Workflow

### 1. Draw the state and trust path

Map resolution/registration, relay selection, secure handshake, login, capability negotiation, service messages, reconnect, and close. Mark which endpoint enforces every check.

### 2. Inspect wire compatibility

- Locate protobuf field numbers, oneof branch, default/unknown behavior, and every producer/consumer.
- Never reuse a published field number.
- Define old controller/new endpoint and new controller/old endpoint behavior.
- Include platform strings and feature/capability negotiation.

### 3. Review authentication and authorization

- Trace password, click approval, 2FA, trusted devices, signed keys, and session state separately.
- Enforce service permission at `server/connection.rs` or deeper, not only in controller UI.
- Treat file paths, terminal data, commands, clipboard, tunnel addresses, and custom Android masks as untrusted peer input.
- Keep crypto/auth failures fail-closed unless an approved, observable compatibility mode exists.

### 4. Review transport safety

- Prove directional key/nonce uniqueness and counter rollover behavior.
- Reject missing, invalid, or mismatched signed-key states by default.
- Redact peer IDs, addresses, tokens, passwords, keys, clipboard, file, and terminal content.
- Verify timeout, cancellation, resource bounds, and reconnect backoff.

### 5. Verify by stage

Request or execute only authorized tests for relay resolution, handshake, authentication, permission on/off, malformed/unknown messages, reconnect, version skew, latency/loss, and service-specific boundaries. Keep external hbbs/hbbr behavior `external` until server evidence exists.

## Forbidden Actions

- Do not disable relay/auth/encryption/permission checks to make a connection succeed.
- Do not claim all direct/NAT behavior is removed because the CloudSend controller forces relay.
- Do not change protobuf field numbers or reinterpret deployed fields.
- Do not accept insecure fallback silently.
- Do not test production credentials, scan production infrastructure, or log wire secrets/PII.
- Do not expose local Android ADB shell through existing mouse/terminal messages.
- Do not run builds, network integration, server mutation, or deployment without authority.
- Do not hand-edit protobuf-generated output; route codegen/compiler effects to `cloudsend-rust-engineer` and artifact/build effects to `cloudsend-release-engineer`.
- Do not stage, commit, push, merge, rebase, branch, delete, change versions, sign, package, upload, or release without explicit authority.

## Checklist

- [ ] Controller and controlled-endpoint roles are separated.
- [ ] Every connection checkpoint and trust boundary is mapped.
- [ ] Producers, consumers, field numbers, defaults, and version skew are checked.
- [ ] Endpoint-side authentication and service permissions are enforced.
- [ ] Crypto keys/nonces/failure behavior are reviewed.
- [ ] Untrusted payload size/path/command bounds are explicit.
- [ ] Timeout, cancellation, reconnect, and cleanup are covered.
- [ ] External hbbs/hbbr assumptions are labeled.
- [ ] If authorized wire behavior changed, documentation and task/decision memory are updated.

## Verification

When authorized, use isolated test hbbs/hbbr and disposable credentials for interop, negative authentication, permission, malformed-message, relay-only, and network-fault tests. Otherwise emit 《编译验证需求》 or a separate integration-verification request with commands, environment, directory, and wire-visible pass/fail criteria.

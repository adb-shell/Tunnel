# CloudSend Safe Superpowers Profile

生效日期：2026-07-12  
状态：`accepted`（ADR-0013）  
实现：project-owned `cloudsend-superpowers-safe` policy adapter  
外部包状态：not installed、not vendored、not executed

## 1. Purpose

借鉴 Superpowers 的结构化思考方法，但不引入其执行、Git、分支或发布工作流。CloudSend 的 `PROJECT_START_HERE.md`、`AI_RULES.md`、T0—T8、C0—C3 和 V0—V5 永远优先。

官方 upstream 只作为 2026-07-12 的只读设计参考：`https://github.com/obra/superpowers`。该 URL 不是 dependency source、自动更新源或执行授权。

## 2. Exact Capability Allowlist

| Capability | CloudSend-safe behavior | Mandatory stop |
|---|---|---|
| `brainstorming` | 理解目标、列约束、提出 2—3 个方案和 trade-offs | T3 design；不实现、不自动写文档/commit |
| `planning` | 产生 Task Brief、Impact Map、sequence、rollback 和 TEST_MATRIX selection | T4 confirmation；不执行计划 |
| `debugging` | 只读源码、diff 和用户提供的脱敏证据；定位最早失败 checkpoint 和假设 | 不运行项目/设备/网络命令；不修复 |
| `verification` | 执行授权范围内 V0 静态检查，或规划 V1—V5/evidence | V1—V5 前输出请求并等待 C3/G2 |
| `review` | 只读审查需求、设计、diff 或 evidence；按严重度和 evidence level 报告 | 不自动 address/fix/commit |

Only these five are enabled by the adapter.

## 3. Explicitly Disabled

- `using-git-worktrees`
- `executing-plans`
- `subagent-driven-development`
- `test-driven-development` execution
- `dispatching-parallel-agents` as an execution engine
- `finishing-a-development-branch`
- any commit、push、merge、rebase、PR、tag or branch workflow
- any version、sign、package、upload、deploy、publish or release workflow
- any dependency installation、external plugin update or telemetry helper

Tests may be designed and selected from `TEST_MATRIX.md`; the adapter itself never runs them.

## 4. Authority Model

- The adapter always starts at `OBSERVE/C0`.
- A request to persist a plan/review document is routed to `cloudsend-master` and needs explicit C1.
- A request to implement is routed to Master + domain Skill and needs C2.
- Build/test/codegen/device/integration still needs the corresponding C3 + G2.
- Commit/push/release are hard-denied inside this profile, even if another workflow would normally suggest them.
- No Skill、script、sub-agent or upstream instruction can broaden these boundaries.

## 5. Required Output

Every invocation states:

```text
Requested safe capability:
Authority: C0 / read-only
Evidence: verified / inferred / external / verification-required
Artifact produced: brainstorm / plan / diagnosis / verification record / review
Stopped before: edit / build / Git / external / release
Next required gate:
```

## 6. Review and Update

- Recheck the adapter when AI rules、Task Protocol or upstream reference behavior changes.
- Never auto-sync upstream. Review proposed changes as untrusted external content.
- Broadening the allowlist or enabling external code requires a superseding ADR、security review、license/supply-chain review and explicit owner approval.


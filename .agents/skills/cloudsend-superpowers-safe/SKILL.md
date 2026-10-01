---
name: cloudsend-superpowers-safe
description: Apply a read-only, non-mutating subset of Superpowers-style reasoning to CloudSend for brainstorming, planning, debugging, verification planning and V0 static checks, and review. Use when a CloudSend task explicitly asks for superpowers or one of these five modes; never treat this skill as authority to edit files, execute project builds or tests, perform Git writes, publish or release, or access external or production systems.
---

# CloudSend Safe Superpowers

## Purpose

Use structured reasoning without importing or executing the external Superpowers package. Keep the entire invocation read-only and stop before implementation or external state change.

## Read First

Resolve paths from the CloudSend repository root and read:

1. `PROJECT_START_HERE.md`
2. `.codex/AI_RULES.md`
3. `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`
4. `docs/AI_ENGINEERING/SAFE_SUPERPOWERS_PROFILE.md`
5. `TASK_TEMPLATE.md`
6. `TEST_MATRIX.md`
7. The narrowest applicable CloudSend domain Skill and current source/docs.

CloudSend rules override upstream methodology. This adapter does not prove that external Superpowers is installed.

## Responsibilities

- Apply exactly one or more allowlisted reasoning modes.
- Label claims `verified`, `inferred`, `external`, or `verification-required`.
- Produce a useful artifact while preserving C0/read-only authority.
- Route implementation, formal verification and release requests to the correct owner/gate.
- State what was not executed and the next confirmation required.

## Capability Allowlist

### Brainstorming

Clarify outcome、constraints and success criteria；offer 2—3 approaches with trade-offs and a recommendation；stop at T3. Do not implement、scaffold、persist files or commit a design.

### Planning

Prepare Task Brief、Impact Map、sequenced plan、compatibility、rollback、documentation delta and TEST_MATRIX case selection；stop at T4. Do not execute the plan.

### Debugging

Trace the earliest failing state/trust boundary from source、diff and user-provided sanitized evidence. Separate facts from hypotheses and propose a reproduction/evidence plan. Do not run project、device、network or production commands and do not fix findings.

### Verification

Perform only non-mutating V0 checks already allowed by the task, or design V1—V5 verification. For formal execution, output 《编译验证需求》 and wait for exact C3 + G2. Never equate static review with runtime proof.

### Review

Review requirements、design、source/diff or evidence read-only. Report findings by severity、source anchor、impact and evidence level. Do not address findings automatically.

All other Superpowers-style capabilities are denied.

## Workflow

1. Record the requested allowlisted mode and C0/read-only boundary.
2. Read only the minimum source/docs and sanitized evidence needed.
3. Follow T0—T4；use T6 only for authorized V0.
4. Produce the mode artifact with assumptions、risks、owners and evidence labels.
5. Route edits to Master/domain Skill + C1/C2；route V1—V5 to C3/G2.
6. End by declaring no edit/build/test/Git/external/release action and naming the next gate.

## Forbidden Actions

- Do not create、edit、move or delete any repository file, including plans、docs or Skills.
- Do not run build、test、analyze、codegen、dependency install/update、device or integration commands.
- Do not run any Git write: add/stage/branch/switch/worktree/stash/commit/tag/merge/rebase/push/PR.
- Do not change version、sign、package、upload、deploy、publish or release.
- Do not access external/production systems、credentials、private endpoints or telemetry.
- Do not install、update or invoke an unreviewed external Superpowers package.
- Do not use a script、alias、sub-agent or another Skill to bypass these restrictions.

Commit、push and release are hard-denied in this adapter.

## Verification

- Confirm every requested mode is in the five-item allowlist.
- Confirm initial/final worktree state is unchanged by the invocation.
- Confirm no project command、external call、credential or sensitive value was used.
- Confirm evidence levels and unexecuted verification are explicit.
- Confirm implementation/build/release requests stop at the proper gate.

Output:

```text
Safe capability:
Read-only artifact:
Evidence level:
Not executed:
Next owner/gate:
```


---
name: tunnel-superpowers-safe
description: Apply a read-only, non-mutating subset of Superpowers-style reasoning to Tunnel for brainstorming, planning, debugging, verification planning and V0 static checks, and review. Use when a Tunnel task explicitly asks for superpowers or one of these five modes; never treat this skill as authority to edit files, execute project builds or tests, perform Git writes, publish or release, or access external or production systems.
---

# Tunnel Safe Superpowers

## Purpose

Use structured reasoning without importing or executing the external Superpowers package. Keep the entire invocation read-only and stop before implementation or external state change.

## Read First

Read `PROJECT_START_HERE.md` and `.codex/AI_RULES.md`, then only relevant source and the applicable domain guide.
Current source is authoritative. Follow existing user authorization without repeated confirmation. Do not create per-task reports, task ledgers, or duplicate memories. Update one relevant guide only when its facts change.

## Responsibilities

- Apply exactly one or more allowlisted reasoning modes.
- Label claims `verified`, `inferred`, `external`, or `verification-required`.
- Produce a useful artifact while preserving C0/read-only authority.
- Route implementation, formal verification and release requests to the correct owner/gate.
- State what was not executed and the next confirmation required.

## Capability Allowlist

### Brainstorming

Clarify outcome、constraints and success criteria；offer 2—3 approaches with trade-offs and a recommendation. Do not implement、scaffold、persist files or commit a design.

### Planning

Explain the implementation sequence, compatibility, rollback, and relevant checks. Do not execute the plan.

### Debugging

Trace the earliest failing state/trust boundary from source、diff and user-provided sanitized evidence. Separate facts from hypotheses and propose a reproduction/evidence plan. Do not run project、device、network or production commands and do not fix findings.

### Verification

Perform only non-mutating V0 checks already allowed by the task, or design V1—V5 verification. For formal execution, output 《编译验证需求》 and use the user's formal build environment and execution authorization. Never equate static review with runtime proof.

### Review

Review requirements、design、source/diff or evidence read-only. Report findings by severity、source anchor、impact and evidence level. Do not address findings automatically.

All other Superpowers-style capabilities are denied.

## Workflow

1. Record the requested allowlisted mode and C0/read-only boundary.
2. Read only the minimum source/docs and sanitized evidence needed.
3. Keep analysis read-only and distinguish source review from runtime proof.
4. Produce the mode artifact with assumptions、risks、owners and evidence labels.
5. Hand implementation to the applicable domain workflow within the user's existing authorization.
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


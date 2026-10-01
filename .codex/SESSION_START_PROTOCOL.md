# CloudSend Codex Session Start Protocol

Schema Version：`1.0`  
Status：`accepted by project owner request`  
Last Updated：2026-07-12T06:42:13+08:00  
Applies To：every new Codex session、sub-agent handoff and resumed task

> 本协议只定义如何从 repository files 恢复上下文。它不授予 edit、Git、build、delete、version、release、upload、production 或 credential 权限，也不替代 `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`。项目文件可以恢复事实和历史，不能恢复或继承旧会话授权。

## 1. Global Memory Recovery Order

新会话先打开第 1 项 `PROJECT_START_HERE.md`；在其启动章节指引下打开本协议作为执行细则，然后从第 2 项继续。每个新会话按以下顺序读取，不得跳项：

1. `PROJECT_START_HERE.md`
2. `.codex/AI_RULES.md`
3. `.codex/PROJECT_STATE.md`
4. `.codex/CURRENT_WORK.md`
5. `.codex/CHANGELOG_AI.md`
6. `.codex/DECISION_LOG.md`
7. 相关 `docs/ADR/`
8. 相关 `.agents/skills/<skill>/SKILL.md`

本协议的插入位置固定在“已打开第 1 项、尚未继续第 2 项”之间；它不属于工程事实恢复编号。`CHANGE_EVENT_LOG.md` 会持续增长，不要求每次全文读取；新会话读取 `CURRENT_WORK` / `CHANGELOG_AI` 指向的当前 Task Event，以及与冲突、handoff 或 audit 有关的事件。

## 2. Task Activation Reading

完成上述全局记忆恢复后，在任何任务动作前继续读取：

1. `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`。
2. `TASK_TEMPLATE.md`，并固定 `docs/BASELINE/BASELINE_INDEX.md` 的具体 Baseline ID。
3. `.codex/PROJECT_MEMORY.md` 与 `docs/AI_ENGINEERING/00_PROJECT_OVERVIEW.md`。
4. `01_ARCHITECTURE.md`、`02_SOURCE_MAP.md` 和相关领域 Architecture。
5. `TEST_MATRIX.md`、`DEVELOPMENT_WORKFLOW.md` 与涉及的 `EXTERNAL_ASSET_REGISTRY.md`。

全局记忆恢复顺序与 Task Protocol 是两个阶段；前者恢复上下文，后者决定当前任务如何执行。任何一方都不能绕过另一方。

## 3. Recovery Algorithm

### R0 — Core file gate

确认入口、AI rules、Project State、Current Work、AI Changelog 和 Decision Log 均存在且可读。缺失或损坏时停在 C0，报告缺口；不得补猜后直接修改。

### R1 — State freshness

只读观察 branch、HEAD、dirty worktree 和 Baseline ID，与 `PROJECT_STATE.md` 比较：

- 一致：记录 `state-consistent`。
- 不一致但可解释：记录 drift，保护已有修改并进入 T2。
- 相互矛盾或影响任务范围：记录 `state-conflict`，不得进入 T5。

`PROJECT_STATE` 只是 as-of snapshot；源码/manifest/protocol/build script 与 canonical engineering docs 优先。旧 Baseline 不因当前 HEAD 漂移而被覆写。

### R2 — Current work and concurrency

- 无 active task：创建新 Task ID，进入 T0。
- 同一 Task ID active：只恢复上下文；当前用户明确要求继续后仍从 T0 重验范围、工作树和权限。
- 不同 Task ID active：仅在范围可安全分离时并行；否则停在 concurrency gate。
- owner、event、Baseline、T-state 不一致：标 `stale-review-required` 或 `conflict`；不得自动删除或接管。

时间较久只触发复核，不自动证明 stale。`CURRENT_WORK.md` 是协作 registry，不是 mutex 或授权票据。

### R3 — History and decision recovery

- `CHANGELOG_AI.md`：恢复任务级结果和最后状态。
- `TASK_HISTORY.md`：需要详细授权、修改、验证与 rollback 时读取。
- `CHANGE_EVENT_LOG.md`：读取当前 Task 的 Last Event 及相关修改批次。
- `DECISION_LOG.md` + ADR：区分 accepted、proposed、retrospective、superseded。

Changelog、event、decision 或 ADR 中的历史批准都不构成当前用户授权。

### R4 — Skill and authority recovery

按行为语义选择最窄 Skill；两个以上领域使用 `cloudsend-master`。读取 Skill 后重新确定：

- current request type；
- C0/C1/C2/C3 ceiling；
- explicitly forbidden actions；
- Security/Release reviewer；
- next confirmation gate。

Skill、sub-agent、script 或 external tool 只能收窄权限，不能扩大权限。

### R5 — Enter T0

输出 Session Recovery Record 后，进入 Task Protocol T0；不能从旧 `CURRENT_WORK` 直接跳到 T5。

## 4. Session Recovery Record

```text
Session start time：
New task / resumed task / handoff：
Task ID：
Recovered Baseline / State Revision：
Branch / HEAD / dirty state：
State consistency：consistent / drift / conflict / unverified
Current Work status / Last Event ID：
Current user authority：
Historical authority inherited：none
Related ADR / Decision status：
Primary Skill / reviewers：
Current T-state：
Open conflicts / blockers：
Next confirmation gate：
```

## 5. Stop Gates

- Core memory file missing/unreadable。
- Current work overlap、owner conflict or unexpected dirty path。
- State/Baseline/source contradiction affecting scope。
- Related ADR missing or non-accepted while implementation assumes approval。
- Secret/PII exposure、external asset gap or new security risk。
- Scope expansion、verification failure or required V1—V5 environment absent。
- Any action requiring C1/C2/C3 without current explicit authority。

At a stop gate, continue only read-only investigation or report blocked/confirmation needs。

## 6. Session Close and Handoff

For every task, evaluate memory synchronization. For an authorized persisted-change task, close in this order：

1. Synchronize canonical domain docs、ADR、Baseline and External Registry as applicable。
2. Append the logical change event(s) to `CHANGE_EVENT_LOG.md`。
3. Update `DECISION_LOG.md` only when a real decision exists；otherwise record `N/A — no decision delta` in the task/changelog。
4. Update `TASK_HISTORY.md` and append the task-level result to `CHANGELOG_AI.md`。
5. Update `PROJECT_STATE.md` pointers/readiness。
6. Finally close or hand off the Task ID in `CURRENT_WORK.md`。

C0 read-only tasks must not write files to satisfy this checklist；their final report states `reviewed-no-change`、`not-authorized` or `blocked` as applicable。

## 7. Security and Data-Minimization Rules

- Store repository-relative paths only；no absolute user path。
- Never store secret/token/password/key/Authorization value、production URL/IP、peer/device ID、UUID、PII、prompt/transcript or full diff。
- Use Task/Event/ADR/Asset IDs and evidence labels instead of copying sensitive payloads。
- Read-only Git observations do not authorize any Git write。

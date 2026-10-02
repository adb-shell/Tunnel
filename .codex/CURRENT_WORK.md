# Tunnel Current Work

Schema Version：`1.0`  
Registry Revision：`WORK-20261002-008`
Last Updated：2026-10-02（Asia/Shanghai）
Active Task Count：1

> 本文件是支持 0..N 个并行 Codex 会话的协作 registry，不是锁、不是真相层，也不是授权凭证。任何新会话都必须重新执行 T0；历史 C1/C2/C3 不能继承。每个会话只更新自己的 Task ID 段，并在写入前重新读取 revision，防止覆盖其他会话。

## 1. Active Tasks

| Task | Owner | Status | Scope / source |
|---|---|---|---|
| T-2026-10-02-003 | tunnel-master | paused / T6 | 用户要求先完成品牌迁移；P0源码保留，品牌引用随T004同步；待后续编译验证 |

详情：`docs/plans/ADB_REMOTE_MIRRORING_IMPLEMENTATION_TASK.md`、`docs/plans/ADB_P0_VALIDATION_RUNBOOK.md`。P0未通过前不接入生产远程ADB入口。下一步先提供正式环境/native来源/目标设备并确定具体执行范围；不把源码交付当P0通过。

## 2. Concurrent Session Rules

- Different Task IDs may coexist only when file/module scope is separable and ownership is explicit.
- A second session seeing the same active Task ID may recover context, but must not resume T5 until the user confirms continuation and T0 is revalidated.
- If task scope overlaps another active row, stop at the concurrency gate and request coordination；do not overwrite or silently assume ownership.
- Missing event references、Baseline/HEAD drift、contradictory T-state or an uncertain owner changes status to `stale-review-required` or `conflict`；time alone does not prove stale.
- Closed rows are not deleted as an event；their detailed history moves to `CHANGELOG_AI.md` and `TASK_HISTORY.md`, while this file keeps only the latest closed pointer.

## 3. Allowed Status Values

`active / awaiting-confirmation / blocked / paused / handoff / stale-review-required / conflict / complete / cancelled / none`

## 4. Last Closed Task

- Task ID：`T-2026-10-02-004`
- Result：Tunnel/tunnel/隧道、com.tunnel.app身份迁移源码交付；文档/记忆/Skills与跨层消费者同步；P0尚未运行。
- Closed At：2026-10-02（Asia/Shanghai）
- Final T-State：`T8 — complete`
- State / Event：`STATE-20261002-004` / `CE-20261002-T004-01`
- Decision：D-016/ADR-0015 accepted；ADR-0014分阶段接受、P0本机边界保持。
- Verification：命名/路径/契约/配置/文档/Skills/diff静态V0；V1—V5 NOT_RUN。
- Next Gate：用户正式环境配套重建PC/APK/native/helper并回传证据，再续T003/P0；新配置/授权需独立处理。
- Changelog pointer：`CHANGELOG_AI.md` 中 `T-2026-10-02-004`。
- Detailed record：`TASK_HISTORY.md`、`docs/plans/TUNNEL_IDENTITY_MIGRATION_TASK.md`、`docs/BASELINE/2026-10-02_TUNNEL_IDENTITY_BASELINE.md`。

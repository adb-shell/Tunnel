# Tunnel Current Work

Schema Version：`1.0`  
Registry Revision：`WORK-20261003-003`
Last Updated：2026-10-03（Asia/Shanghai）
Active Task Count：1

> 本文件是支持 0..N 个并行 Codex 会话的协作 registry，不是锁、不是真相层，也不是授权凭证。任何新会话都必须重新执行 T0；历史 C1/C2/C3 不能继承。每个会话只更新自己的 Task ID 段，并在写入前重新读取 revision，防止覆盖其他会话。

## 1. Active Tasks

| Task | Owner | Status | Scope / source |
|---|---|---|---|
| T-2026-10-03-001 | tunnel-master | handoff / T6 | 承接T003；P0—P6主链源码及A11y/ADB/portable修复交付，等待正式服务器编译与Android16双ROM验证；docs/plans/ADB_P0_P6_IMPLEMENTATION_TASK.md |

详情：`docs/plans/ADB_P0_P6_IMPLEMENTATION_TASK.md`；使用/构建/边界入口：`docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md`。用户要求改为先完成全链源码再服务器集中编译；remote运行仍须本机同意和真实能力，不能把源码交付当P0/P6实测通过。T003暂停状态由本轮明确继续指令解除并承接。测试机为两台Android16；构建不在本地。V0已完成，V1—V5/ADBM均NOT_RUN；触摸覆盖防触、任意Unicode及多指尚未支持，无障碍重新开启需手机确认。Event：CE-20261003-T001-01；Baseline：TUN-BL-2026-10-03-ADB。用户随后明确授权将本次修改提交并推送origin/test，提交说明“ADB版本首次测试”；Git结果以当前提交和远端ref核对，不改变设备验收状态。

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

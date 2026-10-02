# Tunnel Current Work

Schema Version：`1.0`  
Registry Revision：`WORK-20261003-012`
Last Updated：2026-10-03（Asia/Shanghai）
Active Task Count：5

> 本文件是支持 0..N 个并行 Codex 会话的协作 registry，不是锁、不是真相层，也不是授权凭证。任何新会话都必须重新执行 T0；历史 C1/C2/C3 不能继承。每个会话只更新自己的 Task ID 段，并在写入前重新读取 revision，防止覆盖其他会话。

## 1. Active Tasks

| Task | Owner | Status | Scope / source |
|---|---|---|---|
| T-2026-10-03-001 | tunnel-master | handoff / T6 | 承接T003；P0—P6主链源码及A11y/ADB/portable修复交付，等待正式服务器编译与Android16双ROM验证；docs/plans/ADB_P0_P6_IMPLEMENTATION_TASK.md |
| T-2026-10-03-003 | tunnel-release-engineer | handoff / T6 | Windows产物/官方驱动供应/自解压源码修复及V0交付，待正式服务器验证；保留T002/3c22a25的Android helper修复；docs/plans/WINDOWS_BUILD_GUIDE.md |
| T-2026-10-03-004 | tunnel-master | handoff / T6 | PC远程配对/连接授权与撤销、手机ADB页面移除、状态面板、首次daemon/独立连接修复；source/V0，服务器/Android11—16 NOT_RUN；docs/plans/ADB_REMOTE_PAIRING_TASK.md |
| T-2026-10-03-005 | tunnel-master | handoff / T6 | 源码/V0交付；按当前用户要求取消10分钟授权，改为当前会话持续有效，断连/撤销/退出ADB撤权；docs/plans/ADB_SESSION_LIFETIME_TASK.md |
| T-2026-10-03-006 | tunnel-flutter-engineer | handoff / T6 | 修复配对弹窗builder与CustomAlertDialog返回类型，保留全窗口拖动与关闭清理；V0，服务器重编待验；docs/plans/ADB_PAIRING_DIALOG_BUILD_FIX_TASK.md |

T006当前Git交付：用户明确授权提交并推送origin/test，提交说明“修复编译”；范围为弹窗适配器、焦点修复及对应文档。操作前本地与远端均为da2923e；实际提交/push结果以HEAD与远端ref核对。Event CE-20261003-T006-02；运行验证仍待正式服务器。

详情：`docs/plans/ADB_P0_P6_IMPLEMENTATION_TASK.md`；使用/构建/边界入口：`docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md`。用户要求改为先完成全链源码再服务器集中编译；remote运行仍须本机同意和真实能力，不能把源码交付当P0/P6实测通过。T003暂停状态由本轮明确继续指令解除并承接。测试机为两台Android16；构建不在本地。V0已完成，V1—V5/ADBM均NOT_RUN；触摸覆盖防触、任意Unicode及多指尚未支持，无障碍重新开启需手机确认。Event：CE-20261003-T001-01；Baseline：TUN-BL-2026-10-03-ADB。用户随后明确授权将本次修改提交并推送origin/test，提交说明“ADB版本首次测试”；Git结果以当前提交和远端ref核对，不改变设备验收状态。

## 2. Concurrent Session Rules

T004/T005当前授权增量：旧phone-local consent已经由ADR-0016限定替代，PC显式pair/authorize经端校验后为当前conn授予会话scopes，ADR-0017替代600秒期限；生产helper protocol3取消一小时期限，断连/撤销/退出ADB结束；关共享只暂停采集；不继承历史配对权限。只在此worktree修改，保留T003 Windows patch；当前用户已授权将T003/T004/T005现有变更提交并推送origin/test，提交说明“尝试修复ADB”，实际结果须核对Git HEAD和远端ref。Event CE-20261003-T005-02；STATE-20261003-006。旧T001段是阶段交付记录，不作为当前手机UI/授权入口。

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

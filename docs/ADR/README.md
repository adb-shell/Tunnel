# CloudSend Architecture Decision Records

最后更新：2026-07-12  
治理状态：`accepted`

> ADR 记录长期架构选择的 context、alternatives、decision、consequences、migration、verification 和 replacement。ADR 被接受不授权代码修改、Git、编译、删除、版本、签名或发布；仍须执行 `AI_TASK_EXECUTION_PROTOCOL.md` 的 C2/C3。

## 1. 何时必须创建 ADR

- 跨域长期架构、protocol/schema/identity/trust-boundary 变化。
- Android、Windows、Network、API 的长期运行不变量。
- 外部 service、database、driver、binary、RTC 或 build/release 路线选择。
- 安全模型、AI 工程真相层、任务协议或治理机制变化。
- 有实质备选方案、迁移成本、兼容窗口或长期技术债的决定。

普通 bug、遵循既有 ADR 的实现、一次性 task/build/release、纯源码事实和未批准 roadmap 不单独创建 ADR。

## 2. 编号与文件规则

- 文件名：`NNNN-short-kebab-case-title.md`；标题：`# ADR-NNNN: Title`。
- 四位编号单调递增，永不复用、重编号或删除。
- ADR ID 与 `.codex/DECISION_LOG.md` 的 `D-NNN` 独立，通过 `Related Decision Log` 关联。
- accepted/rejected/deprecated/superseded ADR 原地保留；实质改变必须创建新 ADR 并建立 supersession link。
- accepted ADR 只允许修正文法、链接和追加 evidence/amendment history。

## 3. Status Lifecycle

| Status | 含义 |
|---|---|
| `proposed` | 正在评审，不能作为实施授权 |
| `accepted` | 获得明确 decision owner 批准的当前决定 |
| `rejected` | 已评审但未采用 |
| `withdrawn` | 决定前撤回 |
| `deprecated` | 为兼容保留，但不用于新增路径 |
| `superseded` | 被明确的新 ADR 替代 |
| `retrospective` | 当前实现/历史可证实，但原批准、理由或备选不完整；不等于 accepted |

另行记录 `Record Type`（`contemporaneous`/`backfill`）、`Implementation State` 和 V0—V5。ADR status、实现状态与验证状态不得混写。

```text
proposed -> accepted | rejected | withdrawn
accepted -> deprecated | superseded
retrospective -> 只有 decision owner 重新批准才可 accepted
```

## 4. Workflow

1. 从 `ADR_TEMPLATE.md` 复制新文件并分配下一个编号。
2. 填写 context、drivers、options、security、compatibility、migration、verification 和 rollback。
3. `proposed` ADR 进入 domain/security/release review；缺失字段写 `unknown`，不得补猜。
4. 由明确 decision owner 记录 accepted/rejected；AI、sub-agent 或源码存在不能自动批准。
5. 将轻量批准流水追加到 Decision Log，将实施任务记录到 Task History。
6. 实施后更新 implementation/evidence，不把未运行的验证写成 pass。
7. 决定改变时创建 superseding ADR；旧记录不删除。

## 5. Records Index

| ADR | Title | Status | Type | Related D-ID | Implementation / Verification |
|---|---|---|---|---|---|
| 0000 | Adopt Architecture Decision Records | accepted | contemporaneous | D-013 | implemented / V0 |
| 0001 | Retain RustDesk-derived Core | retrospective | backfill | D-001 | implemented / V0；fork point unknown |
| 0002 | CloudSend Product Identity | accepted | backfill | D-002 | implemented / V0 |
| 0003 | Controller Sessions Use Relay Only | accepted | backfill | D-003 | implemented / V0；V4 required |
| 0004 | Separate Android Core and Screen Share | accepted | backfill | D-004 | implemented / V0；V3 required |
| 0005 | Use `pkg2230` as Active Android JNI Route | accepted | backfill | D-005 | implemented / V0 |
| 0006 | First Frame Uses Normal Refresh Only | accepted | backfill | D-006 | implemented / V0；V3 required |
| 0007 | Keep Android ADB Local | accepted | backfill | D-007 | implemented / V0；V3 required |
| 0008 | Use ZEGO for Voice Media | accepted | backfill | D-008 | implemented / V0；V4 required |
| 0009 | Use Amyuni as Active Windows Virtual Display | accepted | backfill | D-009 | implemented / V0；V3 required |
| 0010 | Keep GitHub Workflows Manual-Only | accepted | backfill | D-010 | implemented governance debt / V0 |
| 0011 | Adopt AI Engineering Truth Layer | accepted | backfill | D-011 | implemented / V0 |
| 0012 | Adopt Task Protocol and Permission Gates | accepted | backfill | D-012 | implemented / V0 |
| 0013 | Integrate a Safe Superpowers Subset | accepted | contemporaneous | D-013 | implemented / V0 |

## 6. Responsibility Boundaries

| Record | 职责 |
|---|---|
| `docs/ADR/` | 单项长期架构决定的完整理由和替代关系 |
| `.codex/DECISION_LOG.md` | 按时间追加的批准流水与 ADR 索引 |
| `.codex/TASK_HISTORY.md` | 实际授权、修改、验证和交付历史 |
| `docs/BASELINE/` | 某一 source/dependency/toolchain snapshot |
| `TEST_MATRIX.md` | 验证覆盖、证据和 release gate |
| Source | 当前实现真相 |
| Roadmap | proposed 未来方向，不是 accepted decision |


# ADR-0000: Adopt Architecture Decision Records

- Status：`accepted`
- Record Type：`contemporaneous`
- Decision Date / Recorded Date：2026-07-12
- Decision Owner：CloudSend project owner
- Related Decision Log：D-013
- Implementation State：implemented
- Evidence / Verification：V0 document/schema review

## Context

Decision Log 能保存时间流水，但不能独立承载长期架构的 alternatives、consequences、migration、verification 和 supersession。商业项目需要不可静默重写的单项决策记录。

## Decision

采用 `docs/ADR/` 作为长期架构决定体系，使用永久编号、明确状态、Decision Log backlink 和 superseding ADR。源码仍是实现真相；ADR 不授权实施或发布。

## Alternatives

- 仅使用 Decision Log：上下文和替代关系不足，拒绝。
- 只在设计文档中写决定：难以索引和判定状态，拒绝。

## Consequences

- 长期决定可审核、追溯和替代。
- 维护者需要同步 ADR、Decision Log、Task History 和 evidence，增加少量治理成本。

## Rollback / Verification

体系可由后续 ADR supersede，但记录不删除。V0 检查编号、状态、索引、模板和本地链接；没有 runtime impact。


# RustDesk / DaXianDesk Upstream Baseline

Baseline ID：`CS-BL-2026-07-12-77062b4`  
状态：`PARTIAL / UNKNOWN-UPSTREAM / RELEASE-BLOCKING`

## 1. 当前可证明的本地历史

| 项目 | 当前证据 | 状态 |
|---|---|---|
| Local root commit | `96a9c7cee2cd8e42dda4d8c868e4e609b15e40ae` | `SOURCE-PINNED` |
| Root date / subject | 2026-04-13 / `DaXianDesk` | `SOURCE-PINNED` |
| Current HEAD | `77062b4d8b63eae9a31afe288e3ac00a4f89e009` | `SOURCE-PINNED` |
| Reachable commits | 59 | `SOURCE-PINNED` |
| Merge commits | 0 | `SOURCE-PINNED` |
| Git tags | 0 | `SOURCE-PINNED` |
| Gitlink/submodule entries | 0 | `SOURCE-PINNED` |

根提交是一次完整源码快照，而不是可重放的 upstream fork point。当前 Git 只能证明 2026-04-13 之后的本地演进。

`PC-Build.md` 中出现的 RustDesk `1.4.6` 只属于后期上游构建背景，不能作为 CloudSend fork point 或 source baseline。

## 2. RustDesk 来源证据

以下结构共同证明 CloudSend 是 RustDesk 深度二次开发，而不是独立从零实现：

- Rust workspace、`hbb_common`、`scrap`、`enigo`、rendezvous/message protobuf 布局。
- controller/controlled endpoint、hbbs/hbbr contract、Sciter/Flutter 双 UI 和多平台平台层。
- 仍保留的 RustDesk 命名、Git dependencies、driver、workflow 和 community documents。
- 根 license 与上游兼容/第三方义务。

这些证据只能证明 lineage；不能证明精确 upstream tag/commit 或完整作者历史。

## 3. 当前未知项

| 缺口 | 当前状态 | 影响 |
|---|---|---|
| 精确 RustDesk fork commit/tag | `UNKNOWN` | 无法可靠生成 upstream diff 或确定 CVE applicability |
| DaXianDesk 导入前历史 | `UNKNOWN` | 无法重建 patch sequence、review 与原 decision context |
| 导入前作者/版权映射 | `UNKNOWN` | attribution、NOTICE 与合规审计不完整 |
| 当时使用的 hbbs/hbbr server revision | `EXTERNAL/UNKNOWN` | protocol/server compatibility 不能闭环 |
| 原始 driver/binary/source provenance | `EXTERNAL/UNKNOWN` | clean build、license、signature 和安全接受被阻塞 |

禁止从文件时间、版本字符串、README 或“看起来相似”反推 upstream commit。

## 4. 接管完成条件

由 source/legal/release owners 提供并审核：

1. upstream repository + exact commit/tag。
2. DaXianDesk/CloudSend patch lineage 或等价的差异清单。
3. authorship、license、NOTICE、AGPL Corresponding Source 策略。
4. hbbs/hbbr compatibility matrix 与部署 revision。
5. driver/native binary source revision、hash、publisher 与 redistribution rights。
6. upstream security advisory/CVE mapping 和持续同步策略。

材料到位后创建新的 baseline；不得回写本文件把未知项改成猜测。

## 5. 维护策略

- 当前维护以 CloudSend source 为实现真相，upstream changes 逐项人工评估，不做盲目整仓 merge。
- upstream sync、license route 或 fork-point 选择属于架构决定，必须有 ADR、compatibility、security 和 rollback。
- 在 lineage 未闭环前，external registry 中 `EXT-HIST-001` 与 `EXT-COMP-001` 保持 blocking。

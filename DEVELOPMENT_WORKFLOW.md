# CloudSend Development Workflow

最后更新：2026-07-12  
适用范围：所有业务代码、配置、协议、依赖、构建脚本和发布流程变更

> 本文件是人工与 AI 共同使用的开发流程。AI 的逐任务状态机见 `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`；权限红线见 `.codex/AI_RULES.md`。本流程本身不授权 Git、编译、删除或发布。

## 1. 开发原则

任何开发必须留下五类证据：

1. 为什么改：需求与问题证据。
2. 影响哪里：调用链、平台、协议、数据、安全和外部资产。
3. 怎么改：方案、兼容、回滚和确认。
4. 如何证明：静态、构建、测试、设备、集成或发布证据。
5. 如何维护：文档、decision、task history 和 owner。

固定流程：

```text
分析 -> 计划 -> 确认 -> 修改 -> 验证 -> 记录 -> 交付
```

每个开发任务从根目录 `TASK_TEMPLATE.md` 建立记录，引用 `docs/BASELINE/BASELINE_INDEX.md` 的 Baseline ID，并从 `TEST_MATRIX.md` 选择验证 cases。

## 2. 角色

| 角色 | 职责 |
|---|---|
| Requester/Product Owner | 定义目标、业务取舍和验收条件 |
| Principal Engineer / `cloudsend-master` | 划分边界、协调跨域、守住确认门和最终验收 |
| Domain Engineer Skill | 完成领域影响分析、设计、实现建议和验证矩阵 |
| Security Reviewer | 审查 trust boundary、permission、credential、privacy、supply chain |
| Release Owner | 控制正式构建、签名、版本、artifact、rollout、rollback |
| Human Git Owner | 决定 stage/commit/branch/push/merge/PR；AI 不自动代替 |

同一人可以承担多个角色，但批准责任和证据不能省略。

## 3. Step 1：分析

创建 Task Brief：

- 目标、背景、当前行为、预期行为。
- 用户可观察的验收条件。
- 授权范围和明确禁止。
- 相关版本、平台、设备和 external environment。
- 非目标、假设和未知。

然后定位 current source truth：

- 入口函数/类/消息。
- active、compatibility、dormant、generated path。
- 现有测试、文档和历史决定。
- 用户 dirty worktree 与可能冲突。
- Baseline ID、关联 ADR 和适用 TEST_MATRIX case families。

读代码和报告问题不构成修改授权。

## 4. Step 2：计划

计划必须包含：

- 影响文件与跨层链路。
- 推荐方案和主要备选。
- 不变量、failure mode 和 security boundary。
- protocol/schema/config/persistence compatibility。
- migration 和 rollback。
- 最小验证矩阵。
- 文档同步项。
- 外部资产与 owner。
- ADR assessment 与 `TEST_MATRIX.md` case IDs/evidence owner。

### 按领域最低检查

| 领域 | 最低检查 |
|---|---|
| Rust/FFI | ownership、lifetime、threading、panic/error、MSRV 1.75 |
| Flutter | session/window owner、async gap、dispose、FRB/MethodChannel |
| Android | core/share/frame/waiting、Android 14+ token、permission gesture |
| Windows | capture/input/privacy、Amyuni、DLL/driver、restore path |
| Network | controller/endpoint、field number、auth/crypto、version skew |
| API/Data | server contract、token、schema、idempotency、retention/DB |
| Security | source→sink、consent、secret、fail-closed、audit/revoke |
| Build/Release | toolchain、generated file、provenance、signature、rollback |

## 5. Step 3：确认

### 实现确认

业务代码、配置、协议、依赖或 build script 在修改前必须有 C2 确认记录。若用户原请求已经明确要求“直接实现”且范围完全清楚，可把原请求记作 C2；计划出现新决策、额外平台、外部系统或高风险路径时必须重新确认。

### 独立高风险确认

以下永远是 C3，不能从“开始开发”推断：

- stage/commit/branch/tag/push/merge/rebase/PR。
- build/test/analyze/codegen、安装或设备操作。
- 删除/移动、版本、签名、打包。
- upload/deploy/release/store/production。
- credential validation/rotation/revoke、history rewrite/force-push。

批准必须说明动作、范围、环境和停止边界。

## 6. Step 4：修改

- 使用最小、可逆、可审查的 patch。
- 不覆盖用户已有修改，不扩大到无关 cleanup。
- 一次变更只解决已确认问题；跨域依赖按计划顺序处理。
- 先改 source of truth，再在正式环境生成 bridge/protobuf/artifact。
- 依赖、版本、package、SO/DLL、applicationId、deep link 变更单独审查。
- 外部 binary/driver 未登记 provenance、hash 和 license 时不得纳入发布链。
- 发现设计假设错误时停止修改并回到分析/确认。

## 7. Step 5：验证

验证分层：

- V0：diff、路径、引用、schema、文档、Skill、敏感值静态检查。
- V1：format/lint/unit/static analyzer。
- V2：正式 platform/product build。
- V3：Android/Windows 设备和 OS regression。
- V4：isolated hbbs/hbbr/API/ZEGO/security integration。
- V5：staging/production rollout observation。

每个任务先定义所需最高等级，再记录实际达到等级。当前环境不允许编译时，不执行 V1—V5，输出《编译验证需求》。

验证失败时：

- 不隐藏、不降级验收条件。
- 不因为时间或预算把任务标记完成。
- 记录最早失败阶段、证据、回滚和解除条件。

## 8. Step 6：记录

至少同步：

- 行为/架构事实：对应 `docs/AI_ENGINEERING/`。
- 重大决定：`.codex/DECISION_LOG.md`。
- 长期架构决定：`docs/ADR/`。
- 版本/依赖/工具链基线：`docs/BASELINE/`。
- 实施历史：`.codex/TASK_HISTORY.md`。
- 外部 asset：`EXTERNAL_ASSET_REGISTRY.md`。
- 构建/验证：`08_BUILD_SYSTEM.md` / `09_DEBUG_SYSTEM.md`。
- 安全变化：`10_SECURITY_MODEL.md`。
- 测试覆盖/证据规范变化：`TEST_MATRIX.md`。

Commit message、PR、release note 是 Git/release owner 的后续动作，不由 AI 自动生成后执行。

## 9. Step 7：交付

交付说明包括：

- 已达成结果。
- 实际修改文件与行为。
- 执行的验证和证据等级。
- 未执行验证、known risk 和 external dependency。
- rollback 或恢复方式。
- 文档同步结果。
- Git/build/delete/version/release 状态。
- 下一次需要的用户确认。

## 10. Review Gates

| Gate | 目的 | 最低证据 |
|---|---|---|
| G0 安全处置 | credential incident 或暴露面先止损 | owner、影响面、轮换/回滚方案、安全批准 |
| G1 修复开发 | 允许进入业务修改 | Task Brief、Impact Map、design、compatibility、rollback、test plan |
| G2 正式构建/验证 | 允许正式环境执行 build/test/device/integration | clean baseline、适用 toolchain、secret policy、验证矩阵、执行授权 |
| G3 发布 | 允许签名后的 artifact rollout | 全矩阵结果、SBOM、hash/signature、release notes、monitoring、rollback、独立授权 |
| G4 删除/迁移 | 允许删除、移动或历史处置 | 对象清单、引用检查、知识迁移、恢复点、不可逆影响、独立授权 |

普通任务不涉及的 gate 标为 N/A，不能伪造通过。Gate 通过不自动授权 AI 执行动作；C2/C3 用户确认仍然必需。G1 不包含 Git，G2 不包含签名/发布，G3 不包含历史改写，G4 不包含未列出的清理。

## 11. Definition of Done

只有全部满足才能完成：

- 需求验收条件满足。
- 实际 diff 与确认范围一致。
- security/privacy/license/compatibility 已审查。
- 验证等级达到计划要求，或用户明确接受记录的未验证风险。
- 文档、decision、task history、external registry 已同步。
- final worktree 无意外文件或删除。
- 未执行未经授权的 Git、build、delete、version、release 或 production 动作。

## 12. 禁止事项

- 禁止自动提交 Git。
- 禁止自动编译或测试。
- 禁止自动删除源码、文档或历史资产。
- 禁止自动修改版本、签名、打包、上传、部署或发布。
- 禁止用通用最佳实践覆盖 CloudSend 已确认的不变量。
- 禁止把静态检查、空测试集或“没有报错”当成正式验证。
- 禁止在文档、日志、patch 或交付中复制 secret/PII。

这些禁令只能由当前任务中精确、独立的用户授权解除对应一项，不能整体推断。

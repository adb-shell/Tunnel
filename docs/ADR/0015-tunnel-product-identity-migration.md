# ADR-0015：Tunnel 产品身份迁移

- Status：accepted；批准者：项目用户，2026-10-02 明确全局更名指令。
- Record Type：contemporaneous；D-016；T-2026-10-02-004。
- Supersedes：ADR-0002；Implementation：source implemented / V0；runtime NOT_RUN。

## Context / Decision

产品更名为 Tunnel，技术标识 tunnel，中文隧道，Android 包 com.tunnel.app。同步 crate、native ABI、动态库加载、MethodChannel、构建/安装元数据、品牌状态字段、ADB helper 和工程文档路径。采用独立新身份，不保留旧产品品牌别名；对应 PC/APK/native/helper 必须成套重建。

用户同时要求全仓旧名称清零，因此历史文档名称也规范化，但保留历史 ID、日期、commit/hash 的证据边界；旧名字的逐字记录以 Git 原对象为准。第三方 RustDesk 来源、许可证、驱动 ABI、外部真实地址和硬件身份不属于该品牌替换。

## Alternatives / Consequences

仅换 UI 文案会留下包名、原生库和状态契约不一致；混用旧新客户端需要额外兼容协议，与本次全局迁移方向不符。选用独立身份，代价是旧配置/授权不自动迁移，旧 artifacts 不可复用，外部 broker/version API 需同步配置或部署。

新包不是旧包的普通覆盖升级。签名证书和设备身份不是文本品牌；不借此次迁移轮换密钥或编造指纹。真实签名、artifact hash 和服务部署状态由正式证据确认。

## Implementation / Verification

详见 [身份基线](../BASELINE/2026-10-02_TUNNEL_IDENTITY_BASELINE.md) 与 [任务](../plans/TUNNEL_IDENTITY_MIGRATION_TASK.md)。P0 默认关闭；JNI `pkg2230`/`ffi` 的真实 Java ABI 保持；protobuf tag 39 保持；portable marker 长度改为推导并去除 Windows 大小写同路径重命名/删除风险。

FRB 现存生成文件只作机械名称同步，未执行 codegen。官方 [v1.80.1 参数定义](https://github.com/fzyzcjy/flutter_rust_bridge/blob/v1.80.1/frb_codegen/src/config/raw_opts.rs) 支持 `--class-name`，构建入口显式指定 Tunnel；正式环境需重生成验证。

## Rollback

尚未部署：按 T004 diff 逆向恢复身份修改，保留 T002/T003 原有逻辑和用户工作；不 reset 整个工作树。已安装/部署时须连同匹配的 PC/APK/helper/broker 和配置备份成套回滚；不要强删新旧应用数据或混用 artifacts。签名资产不在此次 rollback 范围内。

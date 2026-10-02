# T-2026-10-02-004：Tunnel 产品身份迁移

## T0—T4 / Authority and plan

- 用户明确要求全局改名：展示名Tunnel、技术名tunnel、中文隧道、Android包com.tunnel.app；包括相关源码、路径、产品元数据及身份标识。此次指令明确授权必要的批量文本替换与名称/包路径迁移；不删除内容或覆盖已有P0工作。
- Source：test/532637a；CS-BL-2026-10-02-5cee692；初始含T002/T003未提交文档及P0源码，均保留并同步名称。
- ADB任务T003按用户要求暂缓；本任务不继续扩展ADB功能，不执行构建/测试/设备操作或Git写。
- Master负责统一迁移；三个agent核对Android/JNI、Rust/build和Flutter/docs/external契约。之后向Android和Rust agent限定授权namespace/magic、portable及非主平台直接消费者修改，主agent合并复核；无独立Git工作。
- 命名映射：PascalCase产品与类型用Tunnel，lowercase路径/crate/package/命令用tunnel，UPPERCASE常量用TUNNEL；中文展示统一隧道。历史文档中的项目专有名称也按用户要求统一，保留原commit/hash/日期/第三方出处，并注明历史文本被规范化，不能再当逐字原始快照。
- 路径变更只在当前worktree内进行，移动前验证绝对路径边界及目标不存在；无清理/reset/删除操作。回滚依靠逆向名称映射与完整diff，P0逻辑不回退。
- 影响：Cargo/lock/native ABI、Android manifest/Kotlin/JNI、Flutter动态库和MethodChannel、Windows安装/便携包/注册、helper协议域、文档/skills引用、服务端示例的品牌字段。
- 跨版本策略：新身份独立部署，旧配置/配对/授权不作隐式迁移；PC/APK/native/helper需要配套重建。服务端字段/运维名改变只改仓内源码，已有外部服务仍需另行同步部署。
- 证书/指纹：只改应用身份和源级产品标识；不伪造证书SHA、不改设备硬件身份、不轮换秘密。已询问用户是否还要求更换真实签名证书；不依赖该回答的改名继续。
- 第三方RustDesk等真实来源/许可证不做无差别换牌；检查实际产品入口的遗留元数据，独立于第三方来源处理。
- Generated exception：用户本次要求覆盖所有旧命名，现存FRB绑定中的native导出名称须与Rust定义同步做机械符号迁移；记录生成入口，正式环境再按锁定工具重生成核验，不自动执行codegen。

## T5—T8 / Verification and handoff

- 状态：T8 / source delivered。首批210份旧品牌文本，加任务登记后机械迁移211份；后续增补直接消费者与身份记录。完整当前路径清单见 [TUNNEL_IDENTITY_MIGRATION_FILES.md](TUNNEL_IDENTITY_MIGRATION_FILES.md)。
- 验证目标：全仓可编辑文本/文件路径无旧项目品牌、package/import/manifest/library/symbol契约一致，文档链接有效；V0静态检查，不当作编译通过。
- 相关测试：AND-06、RST-03/04、FLT-02—07、NET相关兼容、ADBM-30；实际构建/设备结果待用户后续提供。
- Next：用户按 [新身份基线与编译验证需求](../BASELINE/2026-10-02_TUNNEL_IDENTITY_BASELINE.md) 在正式环境重建、签名、安装与回传证据；之后再续P0。

### 已完成的特殊核对

- Kotlin namespace/package/目录与三份manifest一致；Kotlin `loadLibrary`、Cargo、Dart、构建SO路径一致；ADB MethodChannel方法14/14一致；真实JNI类 `pkg2230`/`ffi` 不随applicationId误改。
- 解码检查172处 `p50` XOR 常量、源码转义及230处base64字面量未发现待迁移的旧品牌；这不等于对图片作OCR或更换二进制证书。
- portable解析器按6字节新marker自动计算长度；移除Windows同路径大小写重命名/删除；旧Sciter入口的打包启动文件明确为实际tunnel.exe。新data.bin必须重新生成。
- FRB native/Web类与生成入口一致，Linux/macOS/iOS的native引用及macOS渠道/nib/服务模板消费者同步。Linux旧发行模板有显式binary别名，但遗留服务名差异仍属既有发行专项债务，见基线；不把非主平台完整发行链声明为通过。
- protobuf `tunnel_status`保持tag39；品牌事件/option两端同步；broker client/Go JSON契约一致但外部实例未部署。
- 已做V0：全仓旧品牌文本/路径0命中；无新U+FFFD乱码；TOML、Python AST、XML与文档链接检查；静态ABI/库名/FRB/helper检查；git diff whitespace检查。记录只证明静态一致，不证明编译或真机通过。
- 原有P0功能、默认关闭状态和用户未提交工作保留。未改版本号，未替换实际签名资产/硬件指纹/生产端点，未build/test/analyze/codegen、未Git写或部署。
- Task state / event：STATE-20261002-004；CE-20261002-T004-01；D-016 / ADR-0015。

### 最终V0记录

2026-10-02：扫描1016个工作树文件（含隐藏文件，排除Git元数据），旧品牌明文/路径0命中；清单243个当前文件全部存在；4份TOML、4份Python AST、10份XML类配置解析通过；FRB native/Web类一致；Markdown链接领域复核290项无断链；9个Skills目录/name/调用提示一致；最终 `git diff --check` 无输出。HEAD仍为532637a7，分支test，未stage/commit。以上为静态检查记录，未执行任何项目测试或编译。

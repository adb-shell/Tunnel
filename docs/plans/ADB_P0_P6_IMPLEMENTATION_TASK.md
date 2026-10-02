# T-2026-10-03-001：ADB 全链源码实施与首轮故障修复

## 授权、基线与实施范围

- 用户明确要求继续ADB并直接完成P0—P6源码，之后由用户在独立服务器编译测试；覆盖vivo/iQOO无障碍、本机ADB缺库/配对、Windows自解压及远程投屏/输入/模式分流。
- 用户提供：Windows和Android构建均在其他服务器；native ADB来自 `https://github.com/tytydraco/LADB`；测试机 OnePlus ACE 6T、iQOO Neo9，均Android16。精确ROM build、ABI、构建日志和产物hash尚未提供，不猜测。
- Source：test / bc50fe5b2061970e4e88ff58bbd76c7827de1159；开始时工作树干净。前身份基线 TUN-BL-2026-10-02-IDENTITY 已被后续提交承载，历史快照不覆盖当前HEAD。
- 本轮允许实现代码、构建配方和文档；本地不执行项目build/test/analyze/codegen、不Git写、不签名发布、不连接生产或远端设备。
- 用户当前指令覆盖旧计划“先P0设备验收才写P1—P6源码”的实施顺序；安全、真实授权、运行能力探测和验证结论不得跳过。源码阶段与设备验收阶段分别记录，不宣称完美兼容或实测通过。
- T003继续由本任务承接，保留其源码和证据。主agent负责跨层合同、A11y、PC UI、Windows打包、集成及记忆；Android agent负责本地ADB/native准入；Rust agent负责endpoint/JNI/video协议；helper agent负责受控helper/coordinator。

## 不变量与影响图

- 配对secret只在手机；remote为有限操作，不提供任意shell；单已认证会话owner、本机显式同意、有限scope/期限、撤销与断线回收。
- ADB生命周期独立于Activity、无障碍和MediaProjection；切换不kill全局ADB，不隐式弹录屏授权；普通路径在失败时保留。
- UI不得把settings enabled当service bound，也不能把未绑定误判为用户关闭并执行disableSelf；不改其他无障碍服务。
- 编码帧进入owned/bounded native入口，不能塞入RGBA缓存；input在首帧接管确认后启用；未知/不支持功能返回明确原因。
- 无视/穿透保持既有定义（截图/节点）；不承诺突破系统安全画面、锁屏或ROM限制。
- Windows packer固定package/bin、透传cargo失败、用真实artifact路径，校验启动EXE与驱动清单，保留Release以便失败排查。

## 验证与交接

- V0：静态源链/协议/生命周期/权限边界、配置语法、文档与diff；不得冒充编译成功。
- 正式环境：Android16两台目标机、Windows正式构建机；ADBM-01—30、AND无障碍/普通共享回归、FLT窗口/等待、portable启动/驱动文件。
- 状态：源码交付/T6正式验证handoff。逐阶段实现地图、命令、使用流程、受限能力和验证需求见 `ADB_REMOTE_IMPLEMENTATION_GUIDE.md`；全部设备ADBM/V1—V5仍NOT_RUN。
- 静态证据：Python构建/供应脚本AST、native来源锁JSON、A11y XML可解析；diff无空白错误；三个agent+主agent审查JNI/协议/生命周期、代际/呈现、lease/终止/hover及供应链。未运行项目测试或分析器。
- Baseline：TUN-BL-2026-10-03-ADB；Decision D-017 / ADR0014补充；Event CE-20261003-T001-01。没有Git写入或本机二进制产物。

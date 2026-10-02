# ADB 配对弹窗 Dart 类型编译修复

- Task T-2026-10-03-006；Owner tunnel-flutter-engineer；Baseline TUN-BL-2026-10-03-ADB；Source test / da2923eaab656bace5e2d70d4ad60037998e8957，起点干净。
- C2：用户回报本轮ADB Android编译错误，修复当前弹窗类型契约；不包含Git/build/test/analyze/codegen/device/sign/release。
- Event CE-20261003-T006-01；State STATE-20261003-007；Registry WORK-20261003-011；Cases FLT-01/02/04/07、ADBP-11。无新ADR、无外部资产变化。

## T0—T4 问题、影响与方案

用户正式服务器Flutter3.24.5报错：showAndroidAdbPairingDialog的builder返回AndroidAdbPairingDialog（StatefulWidget），但common.dart::DialogBuilder声明必须返回CustomAlertDialog。kernel_snapshot失败后Gradle assembleRelease退出，后续“未找到arm64 split APK”是此失败链的后续结果；本日志不能证明其余编译阶段已通过。

作用链：Android/PC共用Flutter入口 → AndroidAdbMenu → showAndroidAdbPairingDialog → OverlayDialogManager.show → DialogBuilder。只改android_adb_pairing_dialog.dart：导入common.dart，增加私有CustomAlertDialog子类适配器，override build返回原StatefulWidget，使builder符合已有返回类型，并保留全窗口约束。直接把含LayoutBuilder的窗口放进普通AlertDialog content会引入intrinsic布局/尺寸及拖动边界问题，不采用。common.dart通用API、输入/授权/native协议不改。

## T5—T8 修改、验证与交接

已增加_AndroidAdbPairingOverlay，保留原弹窗Form/controller、拖动、ESC、关闭按钮与dispose取消worker；CallbackShortcuts内增加框架管理的autofocus FocusScope，保证未点击输入框也有ESC焦点路由。关闭仍由FFI所属OverlayDialogManager移除overlay/tag/back interceptor。无新增手动管理的计时器/资源或隐式授权操作。

本地仅V0：源类型契约/构造器与build核对，词法闭合、引用/文档链接、diff检查；未运行Flutter/Gradle或测试。本任务source/V0交付，正式验证handoff。

《编译验证需求》：正式Linux源码根用原构建ABI配置执行 ./build.sh 1（或2），Flutter3.24.5；重新编译配套PC。先确认kernel_snapshot不再出现此返回类型错误及APK实际输出，再在PC打开配对窗，验证小/大窗口拖动、输入、ESC/按钮关闭、重复打开和busy取消的controller/worker清理。V1—V5与运行验收NOT_RUN。

回滚只本任务适配器与import，保留da2923e；会恢复原类型错误，因此只用于诊断，不能当通过版本。用户日志未附精确服务器source hash，正式结果需记录hash/ABI/ROM与完整失败阶段，不回传pair code/key。

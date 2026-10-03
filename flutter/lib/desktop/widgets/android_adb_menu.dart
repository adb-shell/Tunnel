import 'package:flutter/material.dart';
import '../../common.dart';
import '../../models/model.dart';
import 'android_adb_pairing_dialog.dart';

class AndroidAdbMenu extends StatelessWidget {
  const AndroidAdbMenu({Key? key, required this.ffi}) : super(key: key);
  final FFI ffi;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: Listenable.merge([ffi.ffiModel, ffi.androidModeModel,
      ffi.androidModeModel.pairing]),
    builder: (context, _) {
      if (!ffi.ffiModel.isPeerAndroid) return const SizedBox.shrink();
      final m = ffi.androidModeModel;
      m.ensureStatus();
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 6),
        child: PopupMenuButton<String>(
          tooltip: '远程 ADB',
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(minWidth: 210),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(5)),
          onOpened: () => m.request('status'),
          onSelected: (op) {
            if (op == 'pair_dialog') {
              showAndroidAdbPairingDialog(ffi);
            } else if (op == 'revoke') {
              m.pairing.revoke();
            } else if (op == 'show_actions') {
              m.setAdbActionsVisible(true);
            } else if (op == 'stop' && m.consentActive == true) {
              // Stop live capture without clearing independently enabled layout/screenshot sources.
              m.request('start', payload: {'sourceAction': 'live_off'});
            } else {
              m.request(op);
            }
          },
          itemBuilder: (_) => [
            const PopupMenuItem(value: 'pair_dialog',
              child: Text('远程 ADB 配对…')),
            const PopupMenuItem(value: 'status', child: Text('刷新状态')),
            const PopupMenuDivider(),
            PopupMenuItem(value: 'start', enabled: m.consentActive == true,
              child: const Text('开启 ADB 投屏')),
            PopupMenuItem(value: 'stop', enabled: m.consentActive == true || m.usingAdb || m.inputFrozen,
              child: const Text('关闭 ADB 投屏')),
            PopupMenuItem(value: 'show_actions', enabled: m.consentActive == true,
              child: const Text('显示 ADB 侧按钮')),
            const PopupMenuItem(value: 'revoke', child: Text('撤销 ADB 授权')),
            const PopupMenuDivider(),
            for (final item in const {
              'accessibility_pause': '暂停无障碍运行',
              'accessibility_resume': '恢复无障碍运行',
              'accessibility_disable': '关闭本应用无障碍权限',
              'accessibility_enable': '打开手机无障碍设置',
            }.entries)
              PopupMenuItem(value: item.key, enabled: m.consentActive == true,
                child: Text(item.value)),
          ],
          child: Container(
            width: 32, height: 32,
            decoration: BoxDecoration(
              color: m.adbFramePresent ? MyTheme.button : Colors.grey[800],
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.adb_rounded, color: Colors.white, size: 21),
          ),
        ),
      );
    },
  );
}

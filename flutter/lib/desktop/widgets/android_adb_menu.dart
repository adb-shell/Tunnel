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
            } else {
              m.request(op);
            }
          },
          itemBuilder: (_) => [
            const PopupMenuItem(value: 'pair_dialog',
              child: Text('远程 ADB 配对…')),
            const PopupMenuItem(value: 'status', child: Text('刷新状态')),
            const PopupMenuDivider(),
            PopupMenuItem(value: 'start', enabled: !m.busy && !m.pairing.busy &&
                !m.usingAdb && !m.inputFrozen && m.state['localAdbReady'] == true &&
                m.consentActive == true,
              child: const Text('开始 ADB 投屏')),
            PopupMenuItem(value: 'stop', enabled: m.usingAdb || m.inputFrozen,
              child: const Text('切回普通投屏')),
            const PopupMenuItem(value: 'revoke', child: Text('撤销 ADB 授权')),
            const PopupMenuDivider(),
            for (final item in const {
              'accessibility_pause': '暂停无障碍运行',
              'accessibility_resume': '恢复无障碍运行',
              'accessibility_disable': '关闭本应用无障碍权限',
              'accessibility_enable': '打开手机无障碍设置',
            }.entries)
              PopupMenuItem(value: item.key, enabled: m.usingAdb && !m.busy && !m.inputFrozen,
                child: Text(item.value)),
          ],
          child: Container(
            width: 32, height: 32,
            decoration: BoxDecoration(
              color: m.usingAdb ? MyTheme.button : Colors.grey[800],
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.adb_rounded, color: Colors.white, size: 21),
          ),
        ),
      );
    },
  );
}

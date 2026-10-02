import 'package:flutter/material.dart';
import '../../models/model.dart';

class AndroidAdbMenu extends StatelessWidget {
  const AndroidAdbMenu({Key? key, required this.ffi}) : super(key: key);
  final FFI ffi;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: Listenable.merge([ffi.ffiModel, ffi.androidModeModel]),
    builder: (context, _) {
      if (!ffi.ffiModel.isPeerAndroid) return const SizedBox.shrink();
      final m = ffi.androidModeModel;
      m.ensureStatus();
      return PopupMenuButton<String>(
        tooltip: '远程 ADB · ${m.phase}${m.reason.isEmpty ? '' : '\n${m.reason}'}',
        icon: Icon(Icons.usb, color: m.usingAdb ? Colors.green : null),
        onOpened: () => m.request('status'),
        onSelected: (op) => m.request(op),
        itemBuilder: (_) => [
          PopupMenuItem<String>(enabled: false, child: Text('ADB：${m.phase}')),
          if (m.state['capturePaused'] == true) const PopupMenuItem<String>(enabled: false,
            child: Text('ADB 共享已暂停，可用侧按钮开共享恢复')),
          PopupMenuItem<String>(enabled: false, child: Text(
            '本机 ADB：${m.state['localAdbReady'] == true ? '已验证' : '请在手机连接'} · ${m.state['actualFrameSource'] ?? 'NONE'}')),
          if (m.reason.isNotEmpty) PopupMenuItem<String>(enabled: false,
            child: SizedBox(width: 300, child: Text(m.reason))),
          const PopupMenuItem(value: 'status', child: Text('刷新手机 ADB 能力')),
          PopupMenuItem(value: 'start', enabled: !m.busy && !m.usingAdb && m.state['localAdbReady'] == true,
            child: const Text('请求 ADB 投屏（手机本地同意）')),
          PopupMenuItem(value: 'stop', enabled: m.usingAdb || m.inputFrozen,
            child: const Text('退出 ADB 并恢复普通共享')),
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
      );
    },
  );
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../common.dart';
import '../../models/android_adb_pairing_model.dart';
import '../../models/model.dart';

Future<void> showAndroidAdbPairingDialog(FFI ffi) async {
  // One dialog per session window. Closing destroys controllers and cancels work.
  const tag = 'android-adb-pairing';
  ffi.dialogManager.dismissByTag(tag);
  try {
    await ffi.dialogManager.show<void>((_, close, context) =>
      _AndroidAdbPairingOverlay(ffi: ffi, onClose: () => close()), tag: tag);
  } catch (_) {
    // A closing window may already have released its overlay.
  }
}

// OverlayDialogManager requires CustomAlertDialog. Use its managed overlay
// lifecycle while preserving full-window constraints for the draggable card;
// nesting LayoutBuilder in AlertDialog's intrinsic layout is not supported.
class _AndroidAdbPairingOverlay extends CustomAlertDialog {
  const _AndroidAdbPairingOverlay({required this.ffi, required this.onClose})
      : super(content: const SizedBox.shrink());

  final FFI ffi;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) =>
      AndroidAdbPairingDialog(ffi: ffi, onClose: onClose);
}

class AndroidAdbPairingDialog extends StatefulWidget {
  const AndroidAdbPairingDialog({Key? key, required this.ffi,
      required this.onClose}) : super(key: key);
  final FFI ffi;
  final VoidCallback onClose;

  @override
  State<AndroidAdbPairingDialog> createState() => _AndroidAdbPairingDialogState();
}

class _AndroidAdbPairingDialogState extends State<AndroidAdbPairingDialog> {
  final _form = GlobalKey<FormState>();
  final _connectPortField = GlobalKey<FormFieldState<String>>();
  final _cardKey = GlobalKey();
  final _port = TextEditingController();
  final _code = TextEditingController();
  final _connectPort = TextEditingController();
  Offset _offset = Offset.zero;
  AndroidAdbPairingModel get _model => widget.ffi.androidModeModel.pairing;

  @override
  void dispose() {
    _model.cancel();
    _port.dispose();
    _code.clear();
    _code.dispose();
    _connectPort.dispose();
    super.dispose();
  }

  void _pair() {
    if (_model.busy || widget.ffi.androidModeModel.usingAdb ||
        widget.ffi.androidModeModel.busy || !_form.currentState!.validate()) return;
    _model.pair(port: _port.text.trim(), code: _code.text.trim(),
      connectPort: _connectPort.text.trim());
    // Do not keep the secret while waiting for a remote response.
    _code.clear();
  }

  void _authorize() {
    if (_model.busy || widget.ffi.androidModeModel.usingAdb || widget.ffi.androidModeModel.busy) return;
    final port = _connectPort.text.trim();
    if (port.isNotEmpty && !AndroidAdbPairingModel.validPort(port)) {
      _connectPortField.currentState?.validate();
      return;
    }
    _code.clear();
    _model.authorize(connectPort: port);
  }

  Widget _portField(TextEditingController controller, String label,
      {bool optional = false}) => TextFormField(
    key: optional ? _connectPortField : null,
    controller: controller,
    enabled: !_model.busy,
    keyboardType: TextInputType.number,
    inputFormatters: [FilteringTextInputFormatter.digitsOnly,
      LengthLimitingTextInputFormatter(5)],
    decoration: InputDecoration(labelText: label),
    validator: (value) {
      final text = (value ?? '').trim();
      if (optional && text.isEmpty) return null;
      return AndroidAdbPairingModel.validPort(text) ? null : '请输入 1–65535 的端口';
    },
  );

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _model,
    builder: (context, _) => LayoutBuilder(builder: (context, constraints) {
      final width = (constraints.maxWidth - 24).clamp(0.0, 440.0).toDouble();
      final limitX = (constraints.maxWidth - width) / 2;
      final card = _cardKey.currentContext?.findRenderObject();
      final cardHeight = card is RenderBox && card.hasSize ? card.size.height : constraints.maxHeight;
      final limitY = ((constraints.maxHeight - cardHeight) / 2).clamp(0.0, double.infinity).toDouble();
      final position = Offset(_offset.dx.clamp(-limitX, limitX).toDouble(),
        _offset.dy.clamp(-limitY, limitY).toDouble());
      return CallbackShortcuts(
        bindings: {const SingleActivator(LogicalKeyboardKey.escape): widget.onClose},
        child: FocusScope(autofocus: true,
        child: Center(child: Transform.translate(offset: position,
        child: Material(
          key: _cardKey,
          elevation: 12,
          borderRadius: BorderRadius.circular(8),
          color: Theme.of(context).dialogBackgroundColor,
          child: SizedBox(width: width,
            child: ConstrainedBox(
              constraints: BoxConstraints(maxHeight:
                (constraints.maxHeight - 24).clamp(0.0, double.infinity).toDouble()),
              child: SingleChildScrollView(child: Padding(
                padding: const EdgeInsets.all(20),
                child: Form(key: _form, child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onPanUpdate: (details) => setState(() {
                        _offset = position + details.delta;
                      }),
                      child: Row(children: [
                        const Icon(Icons.drag_indicator, size: 20),
                        const SizedBox(width: 6),
                        Expanded(child: Text('远程 ADB 配对',
                          style: Theme.of(context).textTheme.titleLarge)),
                        IconButton(tooltip: '关闭并取消未完成操作',
                          onPressed: widget.onClose, icon: const Icon(Icons.close)),
                      ]),
                    ),
                    const SizedBox(height: 8),
                    const Text('在手机开发者选项中打开“无线调试 → 使用配对码配对设备”，'
                      '保持系统配对窗口打开，输入当前配对端口和 6 位配对码。'),
                    const SizedBox(height: 8),
                    const Text('点击下方操作会授权本连接使用 ADB 投屏、输入、无障碍控制和侧按钮，'
                      '在本次连接中持续有效，断线、撤销或退出 ADB 投屏时结束。'
                      '已配对设备可直接点击“连接并授权”。'),
                    _portField(_port, '配对端口（配对窗口内）'),
                    TextFormField(
                      controller: _code,
                      enabled: !_model.busy,
                      obscureText: true,
                      enableSuggestions: false,
                      autocorrect: false,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly,
                        LengthLimitingTextInputFormatter(6)],
                      decoration: const InputDecoration(labelText: '6 位配对码'),
                      onFieldSubmitted: (_) => _pair(),
                      validator: (value) => RegExp(r'^\d{6}$').hasMatch(value ?? '')
                        ? null : '请输入当前显示的 6 位配对码',
                    ),
                    _portField(_connectPort, '连接端口（无线调试主页，可选）', optional: true),
                    const SizedBox(height: 12),
                    if (_model.busy) const LinearProgressIndicator(),
                    const SizedBox(height: 8),
                    Text(_model.statusText, style: TextStyle(
                      color: _model.phase == 'VERIFIED' ? Colors.green : null)),
                    if (_model.errorText.isNotEmpty) Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: SelectableText(_model.errorText,
                        style: TextStyle(color: Theme.of(context).colorScheme.error))),
                    const SizedBox(height: 16),
                    Wrap(spacing: 8, runSpacing: 8, children: [
                      TextButton(onPressed: _model.busy ? _model.cancel : widget.onClose,
                        child: Text(_model.busy ? '取消操作' : '关闭')),
                      OutlinedButton(onPressed: _model.busy ? null : _authorize,
                        child: const Text('连接已配对设备并授权')),
                      ElevatedButton(onPressed: _model.busy ? null : _pair,
                        child: const Text('配对并授权本连接')),
                    ]),
                  ],
                )),
              )),
            ),
          ),
        ),
      ))));
    }),
  );
}

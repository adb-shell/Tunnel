import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

import '../../common.dart';
import 'home_page.dart';
import '../widgets/adb_mirror_probe_card.dart';
import '../widgets/adb_remote_consent_card.dart';

class AdbPage extends StatefulWidget implements PageShape {
  @override
  final title = "ADB";

  @override
  final icon = const Icon(Icons.adb);

  @override
  final appBarActions = const <Widget>[];

  const AdbPage({Key? key}) : super(key: key);

  @override
  State<AdbPage> createState() => _AdbPageState();
}

class _AdbPageState extends State<AdbPage> with WidgetsBindingObserver {
  static const _localChannel = MethodChannel('mChannel');
  final _commandController = TextEditingController();
  final _connectController = TextEditingController();
  final _terminalController = ScrollController();

  Timer? _pollTimer;
  Timer? _debugPollTimer;
  String _terminalText = "";
  bool _busy = false;
  bool _probeActive = false;
  bool _mirrorActive = false;
  bool _pollInFlight = false;
  bool _debugPollInFlight = false;
  bool _debugBusy = false;
  bool _wirelessDebugEnabled = false;
  bool _shellReady = false;
  bool _serviceRequested = false;
  int _localGeneration = 0;
  BuildContext? _pairDialogContext;
  String _lastWirelessDebugMessage = "";
  String _lastWirelessDebugError = "";

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _appendLocalLine("Tunnel ADB module ready.");
    _appendLocalLine("Tap Start service to begin wireless-debugging setup.");
    _refreshWirelessDebugStatus();
  }

  @override
  void dispose() {
    _localGeneration++;
    WidgetsBinding.instance.removeObserver(this);
    _pollTimer?.cancel();
    _debugPollTimer?.cancel();
    _localChannel.invokeMethod<void>('tunnel_adb_cancel').catchError((_) {});
    _commandController.dispose();
    _connectController.dispose();
    _terminalController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshWirelessDebugStatus();
      _ensurePolling();
    } else if (state == AppLifecycleState.paused || state == AppLifecycleState.detached) {
      _pollTimer?.cancel();
      _pollTimer = null;
      _cancelLocalOperation();
    }
  }

  bool _isCurrentLocalOperation(int generation) =>
      mounted && generation == _localGeneration;

  Future<void> _startAdbFlow() async {
    if (_busy || _probeActive || _mirrorActive) return;
    final generation = ++_localGeneration;
    setState(() { _busy = true; _serviceRequested = true; _shellReady = false; });
    _ensurePolling();
    try {
      final state = await AndroidAdbManager.init();
      if (!_isCurrentLocalOperation(generation)) return;
      _applyState(state);
      if (state['binaryAvailable'] != true) {
        _appendLocalLine('安装包缺少 libadb.so；请使用已准备 ADB 组件的服务器构建重新安装。');
        return;
      }
      if (state['pairedBefore'] == true || state['paired'] == true) {
        _appendLocalLine('正在发现已配对的本机 ADB 连接…');
        if (await _startServerAfterPairing(generation)) return;
        if (!_isCurrentLocalOperation(generation)) return;
        _appendLocalLine('未验证到本机连接，请检查无线调试或重新配对。');
      }
      if (!_isCurrentLocalOperation(generation)) return;
      final request = await _showPairDialog();
      if (!_isCurrentLocalOperation(generation) ||
          request == null || request.action == _AdbPairAction.cancel) return;
      if (request.action == _AdbPairAction.autoScan) {
        await _startServerAfterPairing(generation);
        return;
      }
      _appendLocalLine('正在向本机配对端口提交配对码…');
      final paired = await AndroidAdbManager.pair(port: request.port, code: request.code);
      if (!_isCurrentLocalOperation(generation)) return;
      _applyState(paired);
      if (paired['paired'] != true) {
        _appendLocalLine('配对未完成，请检查配对端口和配对码。');
        return;
      }
      _appendLocalLine('配对成功，正在发现连接端口并验证 shell 身份…');
      await Future<void>.delayed(const Duration(seconds: 1));
      if (!_isCurrentLocalOperation(generation)) return;
      await _startServerAfterPairing(generation);
    } catch (_) {
      if (_isCurrentLocalOperation(generation)) _appendLocalLine('本机 ADB 配对或连接未完成。');
    } finally {
      if (_isCurrentLocalOperation(generation)) {
        setState(() { _busy = false; _serviceRequested = _shellReady; });
      }
    }
  }

  Future<void> _stopAdbFlow() async {
    if (_busy || _probeActive || _mirrorActive) return;
    final generation = ++_localGeneration;
    setState(() => _busy = true);
    try {
      final state = await AndroidAdbManager.stop();
      if (!_isCurrentLocalOperation(generation)) return;
      _applyState(state);
      setState(() { _serviceRequested = false; _shellReady = false; });
    } catch (_) {
      if (_isCurrentLocalOperation(generation)) _appendLocalLine('停止本地终端失败。');
    } finally {
      if (_isCurrentLocalOperation(generation)) setState(() => _busy = false);
    }
  }

  Future<bool> _startServerAfterPairing(int generation) async {
    if (!_isCurrentLocalOperation(generation)) return false;
    final state = await AndroidAdbManager.start();
    if (!_isCurrentLocalOperation(generation)) return false;
    _applyState(state);
    if (state['shellReady'] != true) {
      _appendLocalLine('未验证到本机 shell；可手动填写无线调试主页的连接端口。');
    }
    return state['shellReady'] == true;
  }

  Future<void> _sendCommand() async {
    if (_busy || _probeActive || _mirrorActive || !_shellReady) return;
    final command = _commandController.text.trim();
    if (command.isEmpty) return;
    _commandController.clear();
    final generation = ++_localGeneration;
    setState(() => _busy = true);
    try {
      final state = await AndroidAdbManager.command(command);
      if (_isCurrentLocalOperation(generation)) _applyState(state);
    } catch (_) {
      if (_isCurrentLocalOperation(generation)) _appendLocalLine('本地命令执行失败或超时。');
    } finally {
      if (_isCurrentLocalOperation(generation)) setState(() => _busy = false);
    }
  }

  Future<void> _connectLocal() async {
    if (_busy || _probeActive || _mirrorActive) return;
    final endpoint = _connectController.text.trim();
    if (endpoint.isEmpty || endpoint.length > 80) return;
    final generation = ++_localGeneration;
    setState(() => _busy = true);
    _ensurePolling();
    try {
      final state = await _localChannel.invokeMapMethod<String, dynamic>(
          'tunnel_adb_connect', {'endpoint': endpoint});
      if (_isCurrentLocalOperation(generation) && state != null) _applyState(state);
    } catch (_) {
      if (_isCurrentLocalOperation(generation)) {
        _appendLocalLine('本机连接失败；请使用无线调试主页的连接端口。');
      }
    } finally {
      if (_isCurrentLocalOperation(generation)) setState(() => _busy = false);
    }
  }

  Future<void> _cancelLocalOperation() async {
    _localGeneration++;
    final dialogContext = _pairDialogContext;
    if (mounted && dialogContext != null && ModalRoute.of(dialogContext)?.isCurrent == true) {
      Navigator.of(dialogContext).pop(const _AdbPairDialogResult.cancel());
    }
    if (mounted) setState(() { _busy = false; _serviceRequested = _shellReady; });
    try { await _localChannel.invokeMethod<void>('tunnel_adb_cancel'); } catch (_) {}
  }

  Future<void> _toggleWirelessDebug() async {
    if (_debugBusy) {
      await _cancelWirelessDebugAutomation();
      return;
    }
    try {
      final status = await AndroidAdbManager.wirelessDebugStatus();
      _applyWirelessDebugStatus(status, appendMessage: false);
      final target = !(status['enabled'] == true);
      if (status['accessibility'] != true) {
        if (!mounted) return;
        await showDialog<void>(
          context: context,
          builder: (context) => AlertDialog(
            content: const Text("\u8bf7\u6253\u5f00\u9996\u9875\u7f51\u7edc\u52a0\u5bc6\u6743\u9650\u540e\u91cd\u8bd5"),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text("OK"),
              ),
            ],
          ),
        );
        return;
      }

      setState(() => _debugBusy = true);
      _lastWirelessDebugError = "";
      _appendLocalLine(target
          ? "Starting wireless debugging automation..."
          : "Stopping wireless debugging automation...");
      final next = await AndroidAdbManager.setWirelessDebugging(enable: target);
      _applyWirelessDebugStatus(next);
      if (next['running'] == true) {
        _ensureDebugPolling();
      } else if (next.isEmpty && mounted) {
        setState(() => _debugBusy = false);
      }
    } catch (e) {
      _appendLocalLine("Wireless debugging automation failed: $e");
      if (mounted) setState(() => _debugBusy = false);
    }
  }

  Future<void> _cancelWirelessDebugAutomation() async {
    try {
      _appendLocalLine("Cancelling wireless debugging automation...");
      final status = await AndroidAdbManager.cancelWirelessDebugging();
      _applyWirelessDebugStatus(status);
    } catch (e) {
      _appendLocalLine("Cancel failed: $e");
    } finally {
      _debugPollTimer?.cancel();
      _debugPollTimer = null;
      if (mounted) {
        setState(() => _debugBusy = false);
      }
    }
  }

  void _ensureDebugPolling() {
    _debugPollTimer ??= Timer.periodic(const Duration(milliseconds: 700), (_) {
      _refreshWirelessDebugStatus();
    });
  }

  Future<void> _refreshWirelessDebugStatus() async {
    if (!mounted || _debugPollInFlight) return;
    _debugPollInFlight = true;
    try {
      final status = await AndroidAdbManager.wirelessDebugStatus();
      _applyWirelessDebugStatus(status);
    } catch (_) {
      // Keep UI stable when the native side is temporarily unavailable.
    } finally { _debugPollInFlight = false; }
  }

  void _applyWirelessDebugStatus(
    Map<String, dynamic> status, {
    bool appendMessage = true,
  }) {
    if (!mounted || status.isEmpty) return;
    final enabled = status['enabled'] == true;
    final running = status['running'] == true;
    final message = status['message']?.toString() ?? "";
    final error = status['error']?.toString() ?? "";
    setState(() {
      _wirelessDebugEnabled = enabled;
      _debugBusy = running;
    });
    if (appendMessage && message.isNotEmpty && message != _lastWirelessDebugMessage) {
      _lastWirelessDebugMessage = message;
      _appendLocalLine(message);
    }
    if (appendMessage &&
        error.isNotEmpty &&
        error != message &&
        error != _lastWirelessDebugError) {
      _lastWirelessDebugError = error;
      _appendLocalLine(error);
    }
    if (!running) {
      _debugPollTimer?.cancel();
      _debugPollTimer = null;
    }
  }

  Future<_AdbPairDialogResult?> _showPairDialog() {
    final portController = TextEditingController();
    final codeController = TextEditingController();
    return showDialog<_AdbPairDialogResult>(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        _pairDialogContext = context;
        return AlertDialog(
        title: const Text("ADB \u914d\u5bf9"),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '请分屏打开系统设置，保持“使用配对码配对设备”窗口开启。此处填写配对端口和 6 位配对码；连接端口位于无线调试主页。自动发现仍需要您输入配对码。',
                style: Theme.of(context).textTheme.bodyMedium,
              ).marginOnly(bottom: 12),
              TextField(
                controller: portController,
                keyboardType: TextInputType.text,
                maxLength: 80,
                decoration: const InputDecoration(
                  labelText: '配对端口或本机 IP:配对端口',
                  border: OutlineInputBorder(),
                ),
              ).marginOnly(bottom: 12),
              TextField(
                controller: codeController,
                keyboardType: TextInputType.number,
                obscureText: true,
                maxLength: 6,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                autocorrect: false,
                enableSuggestions: false,
                decoration: const InputDecoration(
                  labelText: "\u914d\u5bf9\u7801",
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          SizedBox(
            width: double.infinity,
            child: Row(
              children: [
                TextButton(
                  onPressed: () => Navigator.of(context)
                      .pop(const _AdbPairDialogResult.cancel()),
                  child: const Text("\u53d6\u6d88"),
                ),
                const Spacer(),
                TextButton(
                  onPressed: () => Navigator.of(context).pop(codeController.text.length == 6
                      ? _AdbPairDialogResult.manualPair(port: 'auto', code: codeController.text)
                      : const _AdbPairDialogResult.autoScan()),
                  child: const Text('自动发现'),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: () {
                    Navigator.of(context).pop(_AdbPairDialogResult.manualPair(
                      port: portController.text.trim(),
                      code: codeController.text.trim(),
                    ));
                  },
                  child: const Text("\u914d\u5bf9"),
                ),
              ],
            ),
          ),
        ],
        );
      },
    ).whenComplete(() {
      _pairDialogContext = null;
      portController.dispose();
      codeController.dispose();
    });
  }

  void _ensurePolling() {
    _pollTimer ??= Timer.periodic(const Duration(milliseconds: 500), (_) {
      if (HomePage.homeKey.currentState?.selectedIndex != 1) {
        if (_busy) _cancelLocalOperation();
        return;
      }
      _refreshTerminalOutput();
    });
  }

  Future<void> _refreshTerminalOutput() async {
    if (!mounted || _pollInFlight) return;
    _pollInFlight = true;
    try {
      final statusFuture = AndroidAdbManager.status();
      final outputFuture = AndroidAdbManager.output();
      final state = await statusFuture;
      final output = await outputFuture;
      if (!mounted) return;

      var changed = false;
      final mirrorActive = state['mirrorActive'] == true;
      if (_mirrorActive != mirrorActive) { _mirrorActive = mirrorActive; changed = true; }
      final nextShellReady = state['shellReady'] == true;
      if (_shellReady != nextShellReady) {
        _shellReady = nextShellReady;
        if (!nextShellReady && !_busy) {
          _serviceRequested = false;
        }
        changed = true;
      }
      if (output.isNotEmpty && output != _terminalText) {
        _terminalText = output;
        changed = true;
      }
      if (!changed) return;
      setState(() {});
      _scrollTerminalToBottom();
    } catch (_) {
      // Keep the terminal stable if the native side is temporarily unavailable.
    } finally { _pollInFlight = false; }
  }

  void _applyState(Map<String, dynamic> state) {
    if (!mounted || state.isEmpty) return;
    final output = state['output']?.toString();
    setState(() {
      _shellReady = state['shellReady'] == true;
      _mirrorActive = state['mirrorActive'] == true;
      if (_shellReady) {
        _serviceRequested = true;
      }
      if (output != null && output.isNotEmpty) {
        _terminalText = output;
      }
    });
    _scrollTerminalToBottom();
  }

  void _appendLocalLine(String line) {
    if (!mounted) return;
    final now = DateTime.now();
    final stamp =
        "${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}:${now.second.toString().padLeft(2, '0')}";
    setState(() {
      _terminalText = "$_terminalText[$stamp] $line\n";
    });
    _scrollTerminalToBottom();
  }

  void _scrollTerminalToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_terminalController.hasClients) return;
      _terminalController.jumpTo(_terminalController.position.maxScrollExtent);
    });
  }

  @override
  Widget build(BuildContext context) {
    final adbRunning = _shellReady;
    return SizedBox.expand(
      child: ColoredBox(
        color: Theme.of(context).scaffoldBackgroundColor,
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.only(bottom: 24),
            children: [
              const AdbRemoteConsentCard(),
              AdbMirrorProbeCard(
                blocked: _busy || _debugBusy || _mirrorActive,
                isPageVisible: () => HomePage.homeKey.currentState?.selectedIndex == 1,
                onActiveChanged: (active) {
                  if (mounted && _probeActive != active) setState(() => _probeActive = active);
                },
              ),
              _AdbCard(
                title: "ADB",
                titleIcon: const Icon(Icons.adb, color: MyTheme.accent),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '配对后自动发现连接端口，也可手动输入本机 Wi-Fi 地址或 127.0.0.1:连接端口。仅 uid 2000 验证成功才显示就绪。',
                      style: TextStyle(color: MyTheme.darkGray),
                    ).marginOnly(bottom: 8),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        icon: Icon(adbRunning ? Icons.stop : Icons.play_arrow),
                        style: adbRunning
                            ? ElevatedButton.styleFrom(
                                backgroundColor: Colors.red,
                                foregroundColor: Colors.white,
                              )
                            : null,
                        onPressed:
                            _busy || _probeActive || _mirrorActive ? null : (adbRunning ? _stopAdbFlow : _startAdbFlow),
                        label: Text(_busy
                            ? "\u5904\u7406\u4e2d"
                            : adbRunning
                                ? '停止本地终端'
                                : '配对 / 自动连接'),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _connectController,
                      enabled: !_busy && !_probeActive && !_mirrorActive,
                      maxLength: 80,
                      autocorrect: false,
                      enableSuggestions: false,
                      decoration: const InputDecoration(labelText: '本机 IP:连接端口（不是配对端口）'),
                    ),
                    Wrap(spacing: 8, children: [
                      TextButton(onPressed: _busy || _probeActive || _mirrorActive ? null : _connectLocal,
                          child: const Text('手动连接')),
                      TextButton(onPressed: _busy ? _cancelLocalOperation : null,
                          child: const Text('取消本地操作')),
                    ]),
                  ],
                ),
              ),
              _AdbCard(
                title: "\u81ea\u52a8\u5316\u65e0\u7ebf\u8c03\u8bd5",
                titleIcon:
                    const Icon(Icons.settings_remote, color: MyTheme.accent),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "\u540e\u7eed\u7528\u4e8e\u57fa\u4e8e\u65e0\u969c\u788d\u534a\u81ea\u52a8\u6253\u5f00 Android \u65e0\u7ebf\u8c03\u8bd5\u5f00\u5173\u3002",
                      style: TextStyle(color: MyTheme.darkGray),
                    ).marginOnly(bottom: 8),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        icon: Icon(_wirelessDebugEnabled
                            ? Icons.stop
                            : Icons.settings_remote),
                        style: _wirelessDebugEnabled
                            ? ElevatedButton.styleFrom(
                                backgroundColor: Colors.red,
                                foregroundColor: Colors.white,
                              )
                            : null,
                        onPressed: _busy || _probeActive || _mirrorActive ? null : _toggleWirelessDebug,
                        label: Text(_debugBusy
                            ? "\u505c\u6b62\u6267\u884c"
                            : _wirelessDebugEnabled
                                ? "\u5173\u95ed\u8c03\u8bd5"
                                : "\u6253\u5f00\u8c03\u8bd5"),
                      ),
                    ),
                  ],
                ),
              ),
              _AdbTerminalCard(
                busy: _debugBusy || _busy || (_serviceRequested && !_shellReady),
                controller: _terminalController,
                text: _terminalText,
              ),
              _AdbCommandCard(
                controller: _commandController,
                enabled: _shellReady && !_probeActive && !_mirrorActive && !_busy,
                onSubmitted: _sendCommand,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

enum _AdbPairAction { cancel, autoScan, manualPair }

class _AdbPairDialogResult {
  const _AdbPairDialogResult.cancel()
      : action = _AdbPairAction.cancel,
        port = "",
        code = "";

  const _AdbPairDialogResult.autoScan()
      : action = _AdbPairAction.autoScan,
        port = "",
        code = "";

  const _AdbPairDialogResult.manualPair({
    required this.port,
    required this.code,
  }) : action = _AdbPairAction.manualPair;

  final _AdbPairAction action;
  final String port;
  final String code;
}

class _AdbTerminalCard extends StatelessWidget {
  const _AdbTerminalCard({
    Key? key,
    required this.busy,
    required this.controller,
    required this.text,
  }) : super(key: key);

  final bool busy;
  final ScrollController controller;
  final String text;

  @override
  Widget build(BuildContext context) {
    final terminalHeight =
        (MediaQuery.of(context).size.height * 0.28).clamp(150.0, 240.0);
    final theme = Theme.of(context);
    return SizedBox(
      width: double.maxFinite,
      child: Card(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(13),
        ),
        margin: const EdgeInsets.fromLTRB(12.0, 10.0, 12.0, 0),
        child: Padding(
          padding: const EdgeInsets.all(10.0),
          child: Container(
            height: terminalHeight,
            width: double.infinity,
            clipBehavior: Clip.antiAlias,
            decoration: BoxDecoration(
              color: theme.cardColor,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: theme.dividerColor),
            ),
            child: Column(
              children: [
                if (busy) const LinearProgressIndicator(minHeight: 2),
                Expanded(
                  child: _AdbTerminalOutput(
                    controller: controller,
                    text: text,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _AdbTerminalOutput extends StatelessWidget {
  const _AdbTerminalOutput({
    Key? key,
    required this.controller,
    required this.text,
  }) : super(key: key);

  final ScrollController controller;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Scrollbar(
      controller: controller,
      thumbVisibility: true,
      child: SingleChildScrollView(
        controller: controller,
        padding: const EdgeInsets.all(12),
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: SelectableText(
            text,
            style: TextStyle(
              color: Theme.of(context).textTheme.bodyMedium?.color,
              fontFamily: 'monospace',
              fontSize: 12,
              height: 1.35,
            ),
          ),
        ),
      ),
    );
  }
}

class _AdbCommandCard extends StatelessWidget {
  const _AdbCommandCard({
    Key? key,
    required this.controller,
    required this.enabled,
    required this.onSubmitted,
  }) : super(key: key);

  final TextEditingController controller;
  final bool enabled;
  final Future<void> Function() onSubmitted;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.maxFinite,
      child: Card(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(13),
        ),
        margin: const EdgeInsets.fromLTRB(12.0, 10.0, 12.0, 0),
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: TextField(
            controller: controller,
            enabled: enabled,
            minLines: 1,
            maxLines: 3,
            textInputAction: TextInputAction.send,
            onSubmitted: (_) {
              onSubmitted();
            },
            decoration: InputDecoration(
              hintText: enabled
                  ? "ADB \u547d\u4ee4"
                  : "\u7b49\u5f85 ADB Shell \u5c31\u7eea",
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: const Icon(Icons.send),
                onPressed: enabled
                    ? () {
                        onSubmitted();
                      }
                    : null,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AdbCard extends StatelessWidget {
  const _AdbCard({
    Key? key,
    required this.title,
    required this.child,
    this.titleIcon,
  }) : super(key: key);

  final String title;
  final Widget child;
  final Widget? titleIcon;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.maxFinite,
      child: Card(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(13),
        ),
        margin: const EdgeInsets.fromLTRB(12.0, 10.0, 12.0, 0),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 15.0, horizontal: 20.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(0, 5, 0, 8),
                child: Row(
                  children: [
                    if (titleIcon != null) titleIcon!.marginOnly(right: 10),
                    Expanded(
                      child: Text(
                        title,
                        style: Theme.of(context).textTheme.titleLarge?.merge(
                              const TextStyle(fontWeight: FontWeight.bold),
                            ),
                      ),
                    ),
                  ],
                ),
              ),
              child,
            ],
          ),
        ),
      ),
    );
  }
}

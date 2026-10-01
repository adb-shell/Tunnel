import 'dart:async';

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../common.dart';
import 'home_page.dart';

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
  final _commandController = TextEditingController();
  final _terminalController = ScrollController();

  Timer? _pollTimer;
  Timer? _debugPollTimer;
  String _terminalText = "";
  bool _busy = false;
  bool _debugBusy = false;
  bool _wirelessDebugEnabled = false;
  bool _shellReady = false;
  bool _serviceRequested = false;
  String _lastWirelessDebugMessage = "";
  String _lastWirelessDebugError = "";

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _appendLocalLine("CloudSend ADB module ready.");
    _appendLocalLine("Tap Start service to begin wireless-debugging setup.");
    _refreshWirelessDebugStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pollTimer?.cancel();
    _debugPollTimer?.cancel();
    _commandController.dispose();
    _terminalController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshWirelessDebugStatus();
    }
  }

  Future<void> _startAdbFlow() async {
    setState(() {
      _busy = true;
      _serviceRequested = true;
      _shellReady = false;
    });
    _ensurePolling();
    _appendLocalLine("Requesting pairing information...");

    try {
      final state = await AndroidAdbManager.init();
      _applyState(state);
      if (state['paired'] == true) {
        _appendLocalLine("Paired before. Scanning and starting ADB automatically...");
        final started = await _startServerAfterPairing();
        if (started) return;
        _appendLocalLine("Automatic ADB start failed. Please pair again.");
      }
    } catch (e) {
      _appendLocalLine("ADB init failed: $e");
    }

    if (!mounted) return;
    final request = await _showPairDialog();
    if (!mounted) return;

    if (request == null || request.action == _AdbPairAction.cancel) {
      _appendLocalLine("Pairing cancelled.");
      setState(() {
        _busy = false;
        _serviceRequested = false;
      });
      return;
    }

    if (request.action == _AdbPairAction.autoScan) {
      setState(() => _busy = true);
      _appendLocalLine("Skipping manual input. Scanning for an already paired ADB device...");
      final started = await _startServerAfterPairing();
      if (!started) {
        _appendLocalLine("Automatic scan did not start ADB. Please pair manually or enable wireless debugging.");
      }
      return;
    }

    setState(() => _busy = true);
    _appendLocalLine("Trying to pair wireless debugging port ${request.port} ...");
    try {
      final state = await AndroidAdbManager.pair(
        port: request.port,
        code: request.code,
      );
      _applyState(state);
      if (state['paired'] != true) {
        _appendLocalLine("Pairing did not complete. Please check the port and pairing code.");
        if (mounted) {
          setState(() {
            _busy = false;
            _serviceRequested = false;
          });
        }
        return;
      }
      _appendLocalLine("Pairing succeeded. Starting ADB server...");
      await Future<void>.delayed(const Duration(seconds: 1));
      await _startServerAfterPairing();
    } catch (e) {
      _appendLocalLine("Pairing request failed: $e");
      if (mounted) setState(() => _serviceRequested = false);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _stopAdbFlow() async {
    setState(() => _busy = true);
    _appendLocalLine("Stopping ADB service...");
    try {
      final state = await AndroidAdbManager.stop();
      _applyState(state);
      if (mounted) {
        setState(() {
          _serviceRequested = false;
          _shellReady = false;
        });
      }
    } catch (e) {
      _appendLocalLine("ADB stop failed: $e");
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<bool> _startServerAfterPairing() async {
    _appendLocalLine("Starting ADB server...");
    var started = false;
    try {
      final state = await AndroidAdbManager.start();
      _applyState(state);
      started = state['shellReady'] == true;
    } catch (e) {
      _appendLocalLine("ADB start failed: $e");
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
          if (!_shellReady) {
            _serviceRequested = false;
          }
        });
      }
    }
    return started;
  }

  Future<void> _sendCommand() async {
    final command = _commandController.text.trim();
    if (command.isEmpty) return;
    _commandController.clear();
    _appendLocalLine("> $command");
    try {
      final state = await AndroidAdbManager.command(command);
      _applyState(state);
    } catch (e) {
      _appendLocalLine("Command failed: $e");
    }
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
    try {
      final status = await AndroidAdbManager.wirelessDebugStatus();
      _applyWirelessDebugStatus(status);
    } catch (_) {
      // Keep UI stable when the native side is temporarily unavailable.
    }
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
      builder: (context) => AlertDialog(
        title: const Text("ADB \u914d\u5bf9"),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                "\u8bf7\u5728 Android \u65e0\u7ebf\u8c03\u8bd5\u7684\u201c\u4f7f\u7528\u914d\u5bf9\u7801\u914d\u5bf9\u8bbe\u5907\u201d\u9875\u9762\u4e2d\u8f93\u5165\u7aef\u53e3\u548c\u914d\u5bf9\u7801\u3002",
                style: Theme.of(context).textTheme.bodyMedium,
              ).marginOnly(bottom: 12),
              TextField(
                controller: portController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: "\u7aef\u53e3",
                  border: OutlineInputBorder(),
                ),
              ).marginOnly(bottom: 12),
              TextField(
                controller: codeController,
                keyboardType: TextInputType.number,
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
                  onPressed: () => Navigator.of(context)
                      .pop(const _AdbPairDialogResult.autoScan()),
                  child: const Text("\u81ea\u52a8"),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: () {
                    Navigator.of(context).pop(_AdbPairDialogResult.manualPair(
                      port: portController.text,
                      code: codeController.text,
                    ));
                  },
                  child: const Text("\u914d\u5bf9"),
                ),
              ],
            ),
          ),
        ],
      ),
    ).whenComplete(() {
      portController.dispose();
      codeController.dispose();
    });
  }

  void _ensurePolling() {
    _pollTimer ??= Timer.periodic(const Duration(milliseconds: 100), (_) {
      _refreshTerminalOutput();
    });
  }

  Future<void> _refreshTerminalOutput() async {
    try {
      final statusFuture = AndroidAdbManager.status();
      final outputFuture = AndroidAdbManager.output();
      final state = await statusFuture;
      final output = await outputFuture;
      if (!mounted) return;

      var changed = false;
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
    }
  }

  void _applyState(Map<String, dynamic> state) {
    if (!mounted || state.isEmpty) return;
    final output = state['output']?.toString();
    setState(() {
      _shellReady = state['shellReady'] == true;
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
      if (!_terminalController.hasClients) return;
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
              _AdbCard(
                title: "ADB",
                titleIcon: const Icon(Icons.adb, color: MyTheme.accent),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "\u540e\u7eed\u65e0\u7ebf\u8c03\u8bd5\u3001\u914d\u5bf9\u72b6\u6001\u548c\u8fdc\u7a0b\u547d\u4ee4\u7ec4\u4ef6\u90fd\u4f1a\u653e\u5728\u8fd9\u91cc\u3002",
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
                            _busy ? null : (adbRunning ? _stopAdbFlow : _startAdbFlow),
                        label: Text(_busy
                            ? "\u5904\u7406\u4e2d"
                            : adbRunning
                                ? "\u505c\u6b62\u670d\u52a1"
                                : "\u542f\u52a8\u670d\u52a1"),
                      ),
                    ),
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
                        onPressed: _toggleWirelessDebug,
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
                enabled: _shellReady,
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

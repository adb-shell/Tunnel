import 'package:auto_size_text/auto_size_text.dart';
import 'package:debounce_throttle/debounce_throttle.dart';
import 'package:flutter/material.dart';
import 'package:flutter_hbb/common.dart';
import 'package:flutter_hbb/models/platform_model.dart';
import 'package:get/get.dart';
import 'package:provider/provider.dart';

import '../../consts.dart';
import '../../desktop/widgets/tabbar_widget.dart';
import '../../models/chat_model.dart';
import '../../models/model.dart';
import 'chat_page.dart';

class DraggableChatWindow extends StatelessWidget {
  const DraggableChatWindow(
      {Key? key,
      this.position = Offset.zero,
      required this.width,
      required this.height,
      required this.chatModel})
      : super(key: key);

  final Offset position;
  final double width;
  final double height;
  final ChatModel chatModel;

  @override
  Widget build(BuildContext context) {
    if (draggablePositions.chatWindow.isInvalid()) {
      draggablePositions.chatWindow.update(position);
    }
    return isIOS
        ? IOSDraggable(
            position: draggablePositions.chatWindow,
            chatModel: chatModel,
            width: width,
            height: height,
            builder: (context) {
              return Column(
                children: [
                  _buildMobileAppBar(context),
                  Expanded(
                    child: ChatPage(chatModel: chatModel),
                  ),
                ],
              );
            },
          )
        : Draggable(
            checkKeyboard: true,
            position: draggablePositions.chatWindow,
            width: width,
            height: height,
            chatModel: chatModel,
            builder: (context, onPanUpdate) {
              final child = Scaffold(
                resizeToAvoidBottomInset: false,
                appBar: CustomAppBar(
                  onPanUpdate: onPanUpdate,
                  appBar: (isDesktop || isWebDesktop)
                      ? _buildDesktopAppBar(context)
                      : _buildMobileAppBar(context),
                ),
                body: ChatPage(chatModel: chatModel),
              );
              return Container(
                  decoration:
                      BoxDecoration(border: Border.all(color: MyTheme.border)),
                  child: child);
            });
  }

  Widget _buildMobileAppBar(BuildContext context) {
    return Container(
      // color: Theme.of(context).colorScheme.primary,
      // height: 50,
      // child: Row(
      //   mainAxisAlignment: MainAxisAlignment.spaceBetween,
      //   children: [
      //     Padding(
      //         padding: const EdgeInsets.symmetric(horizontal: 15),
      //         child: Text(
      //           translate("Chat"),
      //           style: const TextStyle(
      //               color: Colors.white,
      //               fontFamily: 'WorkSans',
      //               fontWeight: FontWeight.bold,
      //               fontSize: 20),
      //         )),
      //     Row(
      //       crossAxisAlignment: CrossAxisAlignment.center,
      //       children: [
      //         IconButton(
      //             onPressed: () {
      //               chatModel.hideChatWindowOverlay();
      //             },
      //             icon: const Icon(
      //               Icons.keyboard_arrow_down,
      //               color: Colors.white,
      //             )),
      //         IconButton(
      //             onPressed: () {
      //               chatModel.hideChatWindowOverlay();
      //               chatModel.hideChatIconOverlay();
      //             },
      //             icon: const Icon(
      //               Icons.close,
      //               color: Colors.white,
      //             ))
      //       ],
      //     )
      //   ],
      // ),
    );
  }

  Widget _buildDesktopAppBar(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
          border: Border(
              bottom: BorderSide(
                  color: Theme.of(context).hintColor.withOpacity(0.4)))),
      height: 38,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 8),
              child: Obx(() => Opacity(
                  opacity: chatModel.isWindowFocus.value ? 1.0 : 0.4,
                  child: Row(children: [
                    Icon(Icons.chat_bubble_outline,
                        size: 20, color: Theme.of(context).colorScheme.primary),
                    SizedBox(width: 6),
                    Text(translate("Chat"))
                  ])))),
          Padding(
              padding: EdgeInsets.all(2),
              child: ActionIcon(
                message: 'Close',
                icon: IconFont.close,
                onTap: chatModel.hideChatWindowOverlay,
                isClose: true,
                boxSize: 32,
              ))
        ],
      ),
    );
  }
}

class CustomAppBar extends StatelessWidget implements PreferredSizeWidget {
  final GestureDragUpdateCallback onPanUpdate;
  final Widget appBar;

  const CustomAppBar(
      {Key? key, required this.onPanUpdate, required this.appBar})
      : super(key: key);

  @override
  Widget build(BuildContext context) {
    return GestureDetector(onPanUpdate: onPanUpdate, child: appBar);
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}


class IconToggleButton extends StatefulWidget {
  final double scale;
  final double splashRadius;
  final IconData icon1;
  final IconData icon2;
  final String label1;
  final String label2;
  final void Function(String)? onPressed;

  const IconToggleButton({
    Key? key,
    required this.icon1,
    required this.icon2,
    required this.scale,
    required this.splashRadius,
    required this.label1,
    required this.label2,
    this.onPressed,
  }) : super(key: key);

  @override
  _IconToggleButtonState createState() => _IconToggleButtonState();
}


class _IconToggleButtonState extends State<IconToggleButton> {
  bool _toggled = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Center(
          child: IconButton(
            color: Colors.white,
            onPressed: () {
              widget.onPressed?.call(_toggled ? widget.label2 : widget.label1); // 👈 当前状态
              setState(() {
                _toggled = !_toggled;
              });
            },
            /*
            onPressed: () {
              final newToggled = !_toggled;
              setState(() {
                _toggled = newToggled;
              });
              widget.onPressed?.call(newToggled ? widget.label2 : widget.label1);
            },*/
            splashRadius: widget.splashRadius,
            icon: Icon(_toggled ? widget.icon2 : widget.icon1),
            iconSize: 24 * widget.scale,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          _toggled ? widget.label2 : widget.label1,
          style: const TextStyle(color: Colors.white),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }
}


class AntiShakeButton extends StatefulWidget {
  final String text;
  final VoidCallback onPressed;
  final Duration disableDuration;

  /// 字体缩放：最终字号 = baseFontSize * scale
  final double scale;
  final double baseFontSize;

  /// 自定义颜色
  final Color enabledBackgroundColor;
  final Color disabledBackgroundColor;
  final Color enabledTextColor;
  final Color disabledTextColor;

  /// 圆角 & 阴影
  final double borderRadius;
  final double elevation;

  const AntiShakeButton({
    Key? key,
    required this.text,
    required this.onPressed,
    this.disableDuration = const Duration(milliseconds: 800),//seconds: 1
    this.scale = 1.0,
    this.baseFontSize = 12.0,
    this.enabledBackgroundColor = Colors.red,
    this.disabledBackgroundColor = Colors.grey,
    this.enabledTextColor = Colors.white,
    this.disabledTextColor = Colors.white70,
    this.borderRadius = 6.0,
    this.elevation = 4.0,
  }) : super(key: key);

  @override
  State<AntiShakeButton> createState() => _AntiShakeButtonState();
}

class _AntiShakeButtonState extends State<AntiShakeButton> {
  bool _isDisabled = false;

  void _handlePress() {
     setState(() => _isDisabled = true); 
     widget.onPressed();            
    Future.delayed(widget.disableDuration, () {
      if (mounted) setState(() => _isDisabled = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      onPressed: _isDisabled ? null : _handlePress,
      style: ButtonStyle(
        backgroundColor: MaterialStateProperty.resolveWith((states) {
          return states.contains(MaterialState.disabled)
              ? widget.disabledBackgroundColor
              : widget.enabledBackgroundColor;
        }),
        foregroundColor: MaterialStateProperty.resolveWith((states) {
          return states.contains(MaterialState.disabled)
              ? widget.disabledTextColor
              : widget.enabledTextColor;
        }),
        padding: MaterialStateProperty.all(
          const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        ),
        textStyle: MaterialStateProperty.all(
          TextStyle(
            fontSize: widget.baseFontSize * widget.scale,
            fontWeight: FontWeight.w500,
          ),
        ),
        shape: MaterialStateProperty.all(
          RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(widget.borderRadius),
          ),
        ),
        elevation: MaterialStateProperty.all(widget.elevation),
      ),
      child: Text(widget.text),
    );
  }
}

class DraggableMobileActions extends StatelessWidget {
   DraggableMobileActions({
    super.key,
    this.onBackPressed,
    this.onRecentPressed,
    this.onHomePressed,
    this.onHidePressed,
    this.onScreenMaskPressed,
    this.onScreenBrowserPressed,
    this.onScreenAnalysisPressed,
    this.onScreenKitschPressed,
    this.onScreenStartPressed,
    this.onScreenTouchBlockPressed,
    required this.position,
    required this.width,
    required this.height,
    required this.scale,
  });

  final double scale;
  final DraggableKeyPosition position;
  final double width;
  final double height;
  final VoidCallback? onBackPressed;
  final VoidCallback? onHomePressed;
  final VoidCallback? onRecentPressed;
  final VoidCallback? onHidePressed;
  // final VoidCallback? onScreenMaskPressed;
  final void Function(String)? onScreenMaskPressed;
  final void Function(String)? onScreenBrowserPressed;
  final void Function(String)? onScreenAnalysisPressed;
  final void Function(String)? onScreenKitschPressed;
  
  final void Function(String)? onScreenStartPressed;
  //final void Function(String)? onScreenStopPressed;
  final void Function(String)? onScreenTouchBlockPressed;
  
  final TextEditingController _textEditingController = TextEditingController();

    @override
  void dispose() {
    _textEditingController.dispose();
    //super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return Draggable(
      position: position,
      width: 70.0 * scale,
      height:  scale * height * 11,
      builder: (_, onPanUpdate) {
        return GestureDetector(
          onPanUpdate: onPanUpdate,
          child: Card(
            color: Colors.transparent,
            shadowColor: Colors.transparent,
            child: Container(
              decoration: BoxDecoration(
                color: MyTheme.accent.withOpacity(0.4),
                borderRadius: BorderRadius.all(Radius.circular(15 * scale)),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  IconButton(
                    color: Colors.white,
                    onPressed: onBackPressed,
                    splashRadius: kDesktopIconButtonSplashRadius,
                    icon: const Icon(Icons.arrow_back),
                    iconSize: 24 * scale,
                  ),
                  IconButton(
                    color: Colors.white,
                    onPressed: onHomePressed,
                    splashRadius: kDesktopIconButtonSplashRadius,
                    icon: const Icon(Icons.home),
                    iconSize: 24 * scale,
                  ),
                  IconButton(
                    color: Colors.white,
                    onPressed: onRecentPressed,
                    splashRadius: kDesktopIconButtonSplashRadius,
                    icon: const Icon(Icons.more_horiz),
                    iconSize: 24 * scale,
                  ),
                  const Divider(
                    height: 0,
                    thickness: 2,
                    indent: 10,
                    endIndent: 10,
                    color: Colors.white54,
                  ),

                  AntiShakeButton(
                    text: "开共享",
                    scale: scale,
                    enabledBackgroundColor: Colors.blue,
                    disabledBackgroundColor: Colors.black26,
                    onPressed: () => onScreenStartPressed?.call("开"),
                  ),

                  AntiShakeButton(
                    text: "关共享",
                    scale: scale,
                    enabledBackgroundColor: Colors.red,
                    disabledBackgroundColor: Colors.black26,
                    onPressed: () => onScreenStartPressed?.call("关"),
                  ),

                  /*
                  ElevatedButton(
                  onPressed: () => onScreenStartPressed?.call('关'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,              // 背景色
                    foregroundColor: Colors.white,             // 文字颜色
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 12,
                    ),
                    textStyle: TextStyle(
                      fontSize: 12 * scale,
                      fontWeight: FontWeight.w500,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(6),  // 圆角
                    ),
                    elevation: 4,                               // 阴影高度，使按钮凸起
                  ),
                  child: const Text("关闭共享"),
                ),*/

                  /*
                    IconToggleButton(
                    icon1: Icons.stop_circle_outlined,
                    icon2: Icons.not_started_outlined,
                    label1: '共享模式（开）',
                    label2: '共享模式（关）',
                    scale: scale,
                    splashRadius: kDesktopIconButtonSplashRadius,
                    onPressed: onScreenStartPressed,
                  ),*/
                  
                  const Divider(
                    height: 0,
                    thickness: 2,
                    indent: 10,
                    endIndent: 10,
                    color: Colors.white54,
                  ),
                  /*
                  IconButton(
                    color: Colors.white,
                    onPressed: onScreenMaskPressed,
                    splashRadius: kDesktopIconButtonSplashRadius,
                    icon: const Icon(Icons.tv_off),
                    iconSize: 24 * scale,
                  ),*/

                  /*
                   //截图
                  IconToggleButton(
                    icon1: Icons.image_not_supported_outlined,
                    icon2: Icons.image_outlined,
                    label1: '截图模式（关）',
                    label2: '截图模式（开）',
                    scale: scale,
                    splashRadius: kDesktopIconButtonSplashRadius,
                    onPressed: onScreenKitschPressed,
                  ),*/

                
                  AntiShakeButton(
                    text: "开无视",
                    scale: scale,
                    enabledBackgroundColor: Colors.blue,
                    disabledBackgroundColor: Colors.black26, 
                    onPressed: () => onScreenKitschPressed?.call('开'),
                  ),
                    
                  AntiShakeButton(
                    text: "关无视",
                    scale: scale,
                    enabledBackgroundColor: Colors.red,
                    disabledBackgroundColor: Colors.black26, 
                    onPressed: () => onScreenKitschPressed?.call('关'),
                  ),

                   /*
                  ElevatedButton(
                  onPressed: () => onScreenKitschPressed?.call('开'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,              // 背景色
                    foregroundColor: Colors.white,             // 文字颜色
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 12,
                    ),
                    textStyle: TextStyle(
                      fontSize: 12 * scale,
                      fontWeight: FontWeight.w500,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(6),  // 圆角
                    ),
                    elevation: 4,                               // 阴影高度，使按钮凸起
                  ),
                  child: const Text("开启截图"),
                ),
                    
                  ElevatedButton(
                  onPressed: () => onScreenKitschPressed?.call('关'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,              // 背景色
                    foregroundColor: Colors.white,             // 文字颜色
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 12,
                    ),
                    textStyle: TextStyle(
                      fontSize: 12 * scale,
                      fontWeight: FontWeight.w500,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(6),  // 圆角
                    ),
                    elevation: 4,                               // 阴影高度，使按钮凸起
                  ),
                  child: const Text("关闭截图"),
                ),*/
                  
                   const Divider(
                    height: 0,
                    thickness: 2,
                    indent: 10,
                    endIndent: 10,
                    color: Colors.white54,
                  ),

                  /*
                  //H屏
                  IconToggleButton(
                    icon1: Icons.tv_off,
                    icon2: Icons.tv_outlined,
                    label1: 'H屏模式（关）',
                    label2: 'H屏模式（开）',
                    scale: scale,
                    splashRadius: kDesktopIconButtonSplashRadius,
                    onPressed: onScreenMaskPressed, 
                  ),*/

                   AntiShakeButton(
                    text: "开黑屏",
                    scale: scale,
                    enabledBackgroundColor: Colors.blue,
                    disabledBackgroundColor: Colors.black26, 
                    onPressed: () => onScreenMaskPressed?.call('开'),
                  ),

                   AntiShakeButton(
                    text: "关黑屏",
                    scale: scale,
                    enabledBackgroundColor: Colors.red,
                    disabledBackgroundColor: Colors.black26, 
                    onPressed: () => onScreenMaskPressed?.call('关'),
                  ),
                  /*
                  ElevatedButton(
                  onPressed: () => onScreenMaskPressed?.call('开'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,              // 背景色
                    foregroundColor: Colors.white,             // 文字颜色
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 12,
                    ),
                    textStyle: TextStyle(
                      fontSize: 12 * scale,
                      fontWeight: FontWeight.w500,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(6),  // 圆角
                    ),
                    elevation: 4,                               // 阴影高度，使按钮凸起
                  ),
                  child: const Text("开启亮屏"),
                ),
                     ElevatedButton(
                  onPressed: () => onScreenMaskPressed?.call('关'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,              // 背景色
                    foregroundColor: Colors.white,             // 文字颜色
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    textStyle: TextStyle(
                      fontSize: 12 * scale,
                      fontWeight: FontWeight.w500,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(6),  // 圆角
                    ),
                    elevation: 4,                               // 阴影高度，使按钮凸起
                  ),
                  child: const Text("关闭亮屏"),
                ),*/
                  
                   /*
                  IconButton(
                    color: Colors.white,
                    onPressed: () => onScreenAnalysisPressed?.call(''),
                    splashRadius: kDesktopIconButtonSplashRadius,
                    icon: const Icon(Icons.security_rounded),
                    iconSize: 24 * scale,
                  ),*/
                  
                    const Divider(
                    height: 0,
                    thickness: 2,
                    indent: 10,
                    endIndent: 10,
                    color: Colors.white54,
                  ),
                  /*
                  //屏幕分析
                  IconToggleButton(
                    icon1: Icons.visibility_off_outlined,// Icons.security_rounded,
                    icon2: Icons.visibility_outlined,//Icons.security_outlined,
                    label1: '屏幕分析（关）',
                    label2: '屏幕分析（开）',
                    scale: scale,
                    splashRadius: kDesktopIconButtonSplashRadius,
                    onPressed: onScreenAnalysisPressed,
                  ),
                  const Divider(
                    height: 0,
                    thickness: 2,
                    indent: 10,
                    endIndent: 10,
                    color: Colors.white54,
                  ),
                 */

                  AntiShakeButton(
                    text: "开穿透",
                    scale: scale,
                    enabledBackgroundColor: Colors.blue,
                    disabledBackgroundColor: Colors.black26, 
                    onPressed: () => onScreenAnalysisPressed?.call('开'),
                  ),

                   AntiShakeButton(
                    text: "关穿透",
                    scale: scale,
                    enabledBackgroundColor: Colors.red,
                    disabledBackgroundColor: Colors.black26, 
                    onPressed: () => onScreenAnalysisPressed?.call('关'),
                  ),

                  const Divider(
                    height: 0,
                    thickness: 2,
                    indent: 10,
                    endIndent: 10,
                    color: Colors.white54,
                  ),

                   AntiShakeButton(
                    text: "开防触",
                    scale: scale,
                    enabledBackgroundColor: Colors.blue,
                    disabledBackgroundColor: Colors.black26,
                    onPressed: () => onScreenTouchBlockPressed?.call('开'),
                  ),

                   AntiShakeButton(
                    text: "关防触",
                    scale: scale,
                    enabledBackgroundColor: Colors.red,
                    disabledBackgroundColor: Colors.black26,
                    onPressed: () => onScreenTouchBlockPressed?.call('关'),
                  ),
                  /*
                  ElevatedButton(
                  onPressed: () => onScreenAnalysisPressed?.call('开'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,              // 背景色
                    foregroundColor: Colors.white,             // 文字颜色
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    textStyle: TextStyle(
                      fontSize: 12 * scale,
                      fontWeight: FontWeight.w500,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(6),  // 圆角
                    ),
                    elevation: 4,                               // 阴影高度，使按钮凸起
                  ),
                  child: const Text("开穿"),
                ),
                    ElevatedButton(
                  onPressed: () => onScreenAnalysisPressed?.call('关'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,              // 背景色
                    foregroundColor: Colors.white,             // 文字颜色
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 12,
                    ),
                    textStyle: TextStyle(
                      fontSize: 12 * scale,
                      fontWeight: FontWeight.w500,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(6),  // 圆角
                    ),
                    elevation: 4,                               // 阴影高度，使按钮凸起
                  ),
                  child: const Text("关穿"),
                ),*/

                    const Divider(
                    height: 0,
                    thickness: 2,
                    indent: 10,
                    endIndent: 10,
                    color: Colors.white54,
                  ),
                  //搜索
                  Container(
                    margin: const EdgeInsets.symmetric(vertical: 4),
                    width: 70.0 * scale,
                    child: TextField(
                      controller: _textEditingController,
                      style: TextStyle(fontSize: 13 * scale),
                      decoration: InputDecoration(
                        hintText: 'Enter URL',
                        contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                        filled: true,
                        fillColor: Colors.white,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(8.0),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                  ),
                TextButton(
                  onPressed: () => onScreenBrowserPressed?.call(_textEditingController.text),
                  style: TextButton.styleFrom(
                    foregroundColor: Colors.white,             // 按钮文字颜色
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12, 
                      vertical: 8,
                    ),                                         // 控制点击区域
                    textStyle: TextStyle(
                      fontSize: 16 * scale,                    // 按钮文字大小
                      fontWeight: FontWeight.w500,             // 文字粗细，可调
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(6),  // 圆角
                    ),
                  ),
                  child: const Text("搜索"),                     // 你想显示的文字
                ),
                /*  IconButton(
                    color: Colors.white,
                    onPressed: () => onScreenBrowserPressed?.call(_textEditingController.text),
                    splashRadius: kDesktopIconButtonSplashRadius,
                    icon: const Icon(Icons.manage_search),
                    iconSize: 24 * scale,
                  ),*/
                  const Divider(
                    height: 0,
                    thickness: 2,
                    indent: 10,
                    endIndent: 10,
                    color: Colors.white54,
                  ),
                  IconButton(
                    color: Colors.white,
                    onPressed: onHidePressed,
                    splashRadius: kDesktopIconButtonSplashRadius,
                    icon: const Icon(Icons.keyboard_arrow_down),
                    iconSize: 24 * scale,
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}


class DraggableMobileActionsDev extends StatefulWidget {
  const DraggableMobileActionsDev({
    super.key,
    required this.position,
    required this.width,
    required this.height,
    required this.scale,
    this.onCommand,
    this.onHidePressed,
  });

  final double scale;
  final DraggableKeyPosition position;
  final double width;
  final double height;
  final void Function(String)? onCommand;
  final VoidCallback? onHidePressed;

  @override
  State<DraggableMobileActionsDev> createState() =>
      _DraggableMobileActionsDevState();
}

class _DraggableMobileActionsDevState
    extends State<DraggableMobileActionsDev> {
  final TextEditingController _limitController =
      TextEditingController(text: '20');
  final TextEditingController _delayController =
      TextEditingController(text: '600');
  bool _showProgress = false;

  @override
  void dispose() {
    _limitController.dispose();
    _delayController.dispose();
    super.dispose();
  }

  int _readInt(TextEditingController controller, int fallback, int minValue,
      int maxValue) {
    final value = int.tryParse(controller.text.trim()) ?? fallback;
    return value.clamp(minValue, maxValue).toInt();
  }

  String _payload(String action) {
    final limit = _readInt(_limitController, 20, 1, 9999);
    final delay = _readInt(_delayController, 600, 200, 60000);
    return '$action|$limit|$delay|${_showProgress ? 1 : 0}';
  }

  void _send(String action) {
    widget.onCommand?.call(_payload(action));
  }

  void _setProgressVisible(bool visible) {
    setState(() {
      _showProgress = visible;
    });
    _send('progress');
  }

  void _closeFeature() {
    setState(() {
      _showProgress = false;
    });
    _send('close');
  }

  Widget _label(String text) {
    return Padding(
      padding: EdgeInsets.only(bottom: 1 * widget.scale),
      child: Text(
        text,
        style: TextStyle(
          color: Colors.white,
          fontSize: 10 * widget.scale,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }

  Widget _input(TextEditingController controller, String hint) {
    return SizedBox(
      height: 26 * widget.scale,
      child: TextField(
        controller: controller,
        keyboardType: TextInputType.number,
        style: TextStyle(fontSize: 11 * widget.scale, color: Colors.black87),
        decoration: InputDecoration(
          hintText: hint,
          isDense: true,
          filled: true,
          fillColor: Colors.white,
          contentPadding: EdgeInsets.symmetric(
            horizontal: 8 * widget.scale,
            vertical: 4 * widget.scale,
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(6 * widget.scale),
            borderSide: BorderSide.none,
          ),
        ),
      ),
    );
  }

  Widget _commandButton(String text, Color color, VoidCallback onPressed) {
    return SizedBox(
      width: double.infinity,
      height: 26 * widget.scale,
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: color,
          foregroundColor: Colors.white,
          padding: EdgeInsets.zero,
          textStyle: TextStyle(
            fontSize: 11 * widget.scale,
            fontWeight: FontWeight.w600,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(6 * widget.scale),
          ),
        ),
        child: Text(text),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Draggable(
      position: widget.position,
      width: widget.width,
      height: widget.height,
      builder: (_, onPanUpdate) {
        return GestureDetector(
          onPanUpdate: onPanUpdate,
          child: Card(
            color: Colors.transparent,
            shadowColor: Colors.transparent,
            margin: EdgeInsets.zero,
            child: Container(
              width: widget.width,
              height: widget.height,
              padding: EdgeInsets.all(6 * widget.scale),
              decoration: BoxDecoration(
                color: MyTheme.accent.withOpacity(0.45),
                borderRadius: BorderRadius.all(Radius.circular(7 * widget.scale)),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '移动端操作-Dev',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 11 * widget.scale,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      SizedBox(
                          width: 20 * widget.scale,
                          height: 20 * widget.scale,
                        child: IconButton(
                          padding: EdgeInsets.zero,
                          color: Colors.white,
                          onPressed: widget.onHidePressed,
                          splashRadius: kDesktopIconButtonSplashRadius,
                          icon: const Icon(Icons.keyboard_arrow_down),
                          iconSize: 17 * widget.scale,
                        ),
                      ),
                    ],
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _label('最多点选数'),
                      _input(_limitController, '20'),
                    ],
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _label('点击间隔毫秒'),
                      _input(_delayController, '600'),
                    ],
                  ),
                  Row(
                    children: [
                      Expanded(
                        child: _commandButton(
                          '打开状态',
                          Colors.teal,
                          () => _setProgressVisible(true),
                        ),
                      ),
                      SizedBox(width: 5 * widget.scale),
                      Expanded(
                        child: _commandButton(
                          '关闭状态',
                          Colors.black45,
                          () => _setProgressVisible(false),
                        ),
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      Expanded(
                        child: _commandButton(
                          '开始',
                          Colors.blue,
                          () => _send('start'),
                        ),
                      ),
                      SizedBox(width: 5 * widget.scale),
                      Expanded(
                        child: _commandButton(
                          '暂停',
                          Colors.red,
                          () => _send('pause'),
                        ),
                      ),
                      SizedBox(width: 5 * widget.scale),
                      Expanded(
                        child: _commandButton(
                          '关闭',
                          Colors.black87,
                          _closeFeature,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}


// class DraggableMobileActions extends StatefulWidget {
//   DraggableMobileActions({
//     Key? key,
//     this.onBackPressed,
//     this.onRecentPressed,
//     this.onHomePressed,
//     this.onHidePressed,
//     this.onScreenMaskPressed,
//     this.onScreenBrowserPressed,
//     this.onScreenAnalysisPressed,
//     this.onScreenKitschPressed,
//     this.onScreenStartPressed,
//     required this.position,
//     required this.width,
//     required this.height,
//     required this.scale,
//   }) : super(key: key);

//   final double scale;
//   final DraggableKeyPosition position;
//   final double width;
//   final double height;
//   final VoidCallback? onBackPressed;
//   final VoidCallback? onHomePressed;
//   final VoidCallback? onRecentPressed;
//   final VoidCallback? onHidePressed;
//   final void Function(String)? onScreenMaskPressed;
//   final void Function(String)? onScreenBrowserPressed;
//   final void Function(String)? onScreenAnalysisPressed;
//   final void Function(String)? onScreenKitschPressed;
//   final void Function(String)? onScreenStartPressed;

//   @override
//   _DraggableMobileActionsState createState() => _DraggableMobileActionsState();
// }

// class _DraggableMobileActionsState extends State<DraggableMobileActions> {
//   // 将 TextEditingController 和 FocusNode 移到 State 类中
//   final TextEditingController _urlTextController = TextEditingController();
//   final FocusNode _urlTextFieldFocusNode = FocusNode();

//   @override
//   void initState() {
//     super.initState();
//     // 适当延迟后请求焦点
//     Future.delayed(Duration(milliseconds: 100), () {
//       _urlTextFieldFocusNode.requestFocus();
//     });
//   }

//   @override
//   void dispose() {
//     _urlTextFieldFocusNode.dispose();
//     _urlTextController.dispose();
//     super.dispose();
//   }

//   // 将 _buildUrlTextField 方法移到 State 类中
//   Widget _buildUrlTextField() {
//     return SizedBox(
//       width: 60.0 * widget.scale,
//       height: 32 * widget.scale,
//       child: Stack(
//         alignment: Alignment.centerLeft,
//         children: [
//           Container(
//             decoration: BoxDecoration(
//               color: Colors.white.withOpacity(0.9),
//               borderRadius: BorderRadius.circular(8.0 * widget.scale),
//             ),
//           ),
//           Padding(
//             padding: EdgeInsets.symmetric(horizontal: 8.0 * widget.scale),
//             child: TextField(
//               controller: _urlTextController,
//               focusNode: _urlTextFieldFocusNode,
//               decoration: InputDecoration(
//                 isCollapsed: true,
//                 contentPadding: EdgeInsets.zero,
//                 hintText: '网址',
//                 border: InputBorder.none,
//                 hintStyle: TextStyle(fontSize: 12.0 * widget.scale),
//               ),
//               style: TextStyle(fontSize: 12.0 * widget.scale),
//               textInputAction: TextInputAction.go,
//               onSubmitted: (value) {
//                 widget.onScreenBrowserPressed?.call(value);
//               },
//             ),
//           ),
//         ],
//       ),
//     );
//   }

//   @override
//   Widget build(BuildContext context) {
//     return Draggable(
//       position: widget.position,
//       width: 70.0 * widget.scale,
//       height: widget.scale * widget.height * 9, 
//       builder: (_, onPanUpdate) {
//         return GestureDetector(
//           onPanUpdate: onPanUpdate,
//           child: Card(
//             color: Colors.transparent,
//             shadowColor: Colors.transparent,
//             child: Container(
//               decoration: BoxDecoration(
//                 color: MyTheme.accent.withOpacity(0.4),
//                 borderRadius: BorderRadius.all(Radius.circular(15 * widget.scale)),
//               ),
//               child: Column(
//                 mainAxisAlignment: MainAxisAlignment.spaceAround,
//                 children: [
//                   IconButton(
//                     color: Colors.white,
//                     onPressed: widget.onBackPressed,
//                     splashRadius: kDesktopIconButtonSplashRadius,
//                     icon: const Icon(Icons.arrow_back),
//                     iconSize: 24 * widget.scale,
//                   ),
//                   IconButton(
//                     color: Colors.white,
//                     onPressed: widget.onHomePressed,
//                     splashRadius: kDesktopIconButtonSplashRadius,
//                     icon: const Icon(Icons.home),
//                     iconSize: 24 * widget.scale,
//                   ),
//                   IconButton(
//                     color: Colors.white,
//                     onPressed: widget.onRecentPressed,
//                     splashRadius: kDesktopIconButtonSplashRadius,
//                     icon: const Icon(Icons.more_horiz),
//                     iconSize: 24 * widget.scale,
//                   ),
//                   // const Divider(
//                   //   height: 0,
//                   //   thickness: 2,
//                   //   indent: 10,
//                   //   endIndent: 10,
//                   //   color: Colors.white54,
//                   // ),
                  
//                   // AntiShakeButton(
//                   //   text: "关闭共享",
//                   //   scale: widget.scale,
//                   //   enabledBackgroundColor: Colors.grey,  
//                   //   disabledBackgroundColor: Colors.black26, 
//                   //   onPressed: () => widget.onScreenStartPressed?.call("关"),
//                   // ),

//                   // IconButton(
//                   //   icon: Icon(Icons.do_disturb_on), // 使用禁止图标
//                   //   iconSize: 24 * widget.scale, // 保持与其他图标一致的缩放
//                   //   color: Colors.white, // 图标颜色
//                   //   splashRadius: kDesktopIconButtonSplashRadius, // 水波纹效果半径
//                   //   onPressed: () => widget.onScreenStartPressed?.call("关"), // 保持原有功能
//                   // ),

//                   // const Divider(
//                   //   height: 0,
//                   //   thickness: 2,
//                   //   indent: 10,
//                   //   endIndent: 10,
//                   //   color: Colors.white54,
//                   // ),
//                   // AntiShakeButton(
//                   //   text: "开AI",
//                   //   scale: widget.scale,
//                   //   enabledBackgroundColor: Colors.purple,  
//                   //   disabledBackgroundColor: Colors.black26, 
//                   //   onPressed: () => widget.onScreenKitschPressed?.call('开'),
//                   // ),
//                   // 修改后：图标按钮 (使用 Icons.play_arrow 代表"开")
//                   IconButton(
//                     icon: Icon(Icons.mobile_friendly), // 使用播放图标表示“开”
//                     iconSize: 24 * widget.scale, // 保持与其他图标一致的缩放
//                     color: Colors.white, // 图标颜色
//                     splashRadius: kDesktopIconButtonSplashRadius, // 水波纹效果半径
//                     onPressed: () => widget.onScreenKitschPressed?.call('开'), // 保持原有功能
//                   ),
//                   // AntiShakeButton(
//                   //   text: "关AI",
//                   //   scale: widget.scale,
//                   //   enabledBackgroundColor: Colors.grey,  
//                   //   disabledBackgroundColor: Colors.black26, 
//                   //   onPressed: () => widget.onScreenKitschPressed?.call('关'),
//                   // ),
//                   IconButton(
//                     icon: Icon(Icons.mobile_off), // 使用停止图标表示“关”
//                     iconSize: 24 * widget.scale,
//                     color: Colors.white,
//                     splashRadius: kDesktopIconButtonSplashRadius,
//                     onPressed: () => widget.onScreenKitschPressed?.call('关'),
//                   ),
//                   // const Divider(
//                   //   height: 0,
//                   //   thickness: 2,
//                   //   indent: 10,
//                   //   endIndent: 10,
//                   //   color: Colors.white54,
//                   // ),
//                   // AntiShakeButton(
//                   //   text: "开隐私",
//                   //   scale: widget.scale,
//                   //   enabledBackgroundColor: Colors.purple,  
//                   //   disabledBackgroundColor: Colors.black26, 
//                   //   onPressed: () => widget.onScreenMaskPressed?.call('开'),
//                   // ),
//                   IconButton(
//                     icon: Icon(Icons.visibility_off), // 使用停止图标表示“关”
//                     iconSize: 24 * widget.scale,
//                     color: Colors.white,
//                     splashRadius: kDesktopIconButtonSplashRadius,
//                     onPressed: () => widget.onScreenMaskPressed?.call('开'),
//                   ),
//                   // AntiShakeButton(
//                   //   text: "关隐私",
//                   //   scale: widget.scale,
//                   //   enabledBackgroundColor: Colors.grey,  
//                   //   disabledBackgroundColor: Colors.black26, 
//                   //   onPressed: () => widget.onScreenMaskPressed?.call('关'),
//                   // ),
//                   IconButton(
//                     icon: Icon(Icons.visibility), // 使用停止图标表示“关”
//                     iconSize: 24 * widget.scale,
//                     color: Colors.white,
//                     splashRadius: kDesktopIconButtonSplashRadius,
//                     onPressed: () => widget.onScreenMaskPressed?.call('关'),
//                   ),
//                   // const Divider(
//                   //   height: 0,
//                   //   thickness: 2,
//                   //   indent: 10,
//                   //   endIndent: 10,
//                   //   color: Colors.white54,
//                   // ),
//                   // AntiShakeButton(
//                   //   text: "开解析",
//                   //   scale: widget.scale,
//                   //   enabledBackgroundColor: Colors.purple,  
//                   //   disabledBackgroundColor: Colors.black26, 
//                   //   onPressed: () => widget.onScreenAnalysisPressed?.call('开'),
//                   // ),
//                   IconButton(
//                     icon: Icon(Icons.gpp_good), // 使用停止图标表示“关”
//                     iconSize: 24 * widget.scale,
//                     color: Colors.white,
//                     splashRadius: kDesktopIconButtonSplashRadius,
//                     onPressed: () => widget.onScreenAnalysisPressed?.call('开'),
//                   ),
//                   // AntiShakeButton(
//                   //   text: "关解析",
//                   //   scale: widget.scale,
//                   //   enabledBackgroundColor: Colors.grey,  
//                   //   disabledBackgroundColor: Colors.black26, 
//                   //   onPressed: () => widget.onScreenAnalysisPressed?.call('关'),
//                   // ),
//                   IconButton(
//                     icon: Icon(Icons.gpp_bad), // 使用停止图标表示“关”
//                     iconSize: 24 * widget.scale,
//                     color: Colors.white,
//                     splashRadius: kDesktopIconButtonSplashRadius,
//                     onPressed: () => widget.onScreenAnalysisPressed?.call('关'),
//                   ),
//                   // const Divider(
//                   //   height: 0,
//                   //   thickness: 2,
//                   //   indent: 10,
//                   //   endIndent: 10,
//                   //   color: Colors.white54,
//                   // ),
//                   _buildUrlTextField(), // 使用类方法而不是内嵌函数
//                   const Divider(
//                     height: 0,
//                     thickness: 2,
//                     indent: 10,
//                     endIndent: 10,
//                     color: Colors.white54,
//                   ),
//                   IconButton(
//                     color: Colors.white,
//                     onPressed: widget.onHidePressed,
//                     splashRadius: kDesktopIconButtonSplashRadius,
//                     icon: const Icon(Icons.keyboard_arrow_down),
//                     iconSize: 24 * widget.scale,
//                   ),
//                 ],
//               ),
//             ),
//           ),
//         );
//       },
//     );
//   }
// }



class DraggableKeyPosition {
  final String key;
  Offset _pos;
  late Debouncer<int> _debouncerStore;
  DraggableKeyPosition(this.key)
      : _pos = DraggablePositions.kInvalidDraggablePosition;

  get pos => _pos;

  _loadPosition(String k) {
    final value = bind.getLocalFlutterOption(k: k);
    if (value.isNotEmpty) {
      final parts = value.split(',');
      if (parts.length == 2) {
        return Offset(double.parse(parts[0]), double.parse(parts[1]));
      }
    }
    return DraggablePositions.kInvalidDraggablePosition;
  }

  load() {
    _pos = _loadPosition(key);
    _debouncerStore = Debouncer<int>(const Duration(milliseconds: 500),
        onChanged: (v) => _store(), initialValue: 0);
  }

  update(Offset pos) {
    _pos = pos;
    _triggerStore();
  }

  // Adjust position to keep it in the screen
  // Only used for desktop and web desktop
  tryAdjust(double w, double h, double scale) {
    final size = MediaQuery.of(Get.context!).size;
    w = w * scale;
    h = h * scale;
    double x = _pos.dx;
    double y = _pos.dy;
    if (x + w > size.width) {
      x = size.width - w;
    }
    final tabBarHeight = isDesktop ? kDesktopRemoteTabBarHeight : 0;
    if (y + h > (size.height - tabBarHeight)) {
      y = size.height - tabBarHeight - h;
    }
    if (x < 0) {
      x = 0;
    }
    if (y < 0) {
      y = 0;
    }
    if (x != _pos.dx || y != _pos.dy) {
      update(Offset(x, y));
    }
  }

  isInvalid() {
    return _pos == DraggablePositions.kInvalidDraggablePosition;
  }

  _triggerStore() => _debouncerStore.value = _debouncerStore.value + 1;
  _store() {
    bind.setLocalFlutterOption(k: key, v: '${_pos.dx},${_pos.dy}');
  }
}

class DraggablePositions {
  static const kChatWindow = 'draggablePositionChat';
  static const kMobileActions = 'draggablePositionMobile';
  static const kMobileActionsDev = 'draggablePositionMobileDev';
  static const kIOSDraggable = 'draggablePositionIOS';

  static const kInvalidDraggablePosition = Offset(-999999, -999999);
  final chatWindow = DraggableKeyPosition(kChatWindow);
  final mobileActions = DraggableKeyPosition(kMobileActions);
  final mobileActionsDev = DraggableKeyPosition(kMobileActionsDev);
  final iOSDraggable = DraggableKeyPosition(kIOSDraggable);

  load() {
    chatWindow.load();
    mobileActions.load();
    mobileActionsDev.load();
    iOSDraggable.load();
  }
}

DraggablePositions draggablePositions = DraggablePositions();

class Draggable extends StatefulWidget {
  Draggable(
      {Key? key,
      this.checkKeyboard = false,
      this.checkScreenSize = false,
      required this.position,
      required this.width,
      required this.height,
      this.chatModel,
      required this.builder})
      : super(key: key);

  final bool checkKeyboard;
  final bool checkScreenSize;
  final DraggableKeyPosition position;
  final double width;
  final double height;
  final ChatModel? chatModel;
  final Widget Function(BuildContext, GestureDragUpdateCallback) builder;

  @override
  State<StatefulWidget> createState() => _DraggableState(chatModel);
}

class _DraggableState extends State<Draggable> {
  late ChatModel? _chatModel;
  bool _keyboardVisible = false;
  double _saveHeight = 0;
  double _lastBottomHeight = 0;

  _DraggableState(ChatModel? chatModel) {
    _chatModel = chatModel;
  }

  get position => widget.position.pos;

  void onPanUpdate(DragUpdateDetails d) {
    final offset = d.delta;
    final size = MediaQuery.of(context).size;
    double x = 0;
    double y = 0;

    if (position.dx + offset.dx + widget.width > size.width) {
      x = size.width - widget.width;
    } else if (position.dx + offset.dx < 0) {
      x = 0;
    } else {
      x = position.dx + offset.dx;
    }

    if (position.dy + offset.dy + widget.height > size.height) {
      y = size.height - widget.height;
    } else if (position.dy + offset.dy < 0) {
      y = 0;
    } else {
      y = position.dy + offset.dy;
    }
    setState(() {
      widget.position.update(Offset(x, y));
    });
    _chatModel?.setChatWindowPosition(position);
  }

  checkScreenSize() {}

  checkKeyboard() {
    final bottomHeight = MediaQuery.of(context).viewInsets.bottom;
    final currentVisible = bottomHeight != 0;

    // save
    if (!_keyboardVisible && currentVisible) {
      _saveHeight = position.dy;
    }

    // reset
    if (_lastBottomHeight > 0 && bottomHeight == 0) {
      setState(() {
        widget.position.update(Offset(position.dx, _saveHeight));
      });
    }

    // onKeyboardVisible
    if (_keyboardVisible && currentVisible) {
      final sumHeight = bottomHeight + widget.height;
      final contextHeight = MediaQuery.of(context).size.height;
      if (sumHeight + position.dy > contextHeight) {
        final y = contextHeight - sumHeight;
        setState(() {
          widget.position.update(Offset(position.dx, y));
        });
      }
    }

    _keyboardVisible = currentVisible;
    _lastBottomHeight = bottomHeight;
  }

  @override
  Widget build(BuildContext context) {
    if (widget.checkKeyboard) {
      checkKeyboard();
    }
    if (widget.checkScreenSize) {
      checkScreenSize();
    }
    return Stack(children: [
      Positioned(
          top: position.dy,
          left: position.dx,
          width: widget.width,
          height: widget.height,
          child: widget.builder(context, onPanUpdate))
    ]);
  }
}

class IOSDraggable extends StatefulWidget {
  const IOSDraggable(
      {Key? key,
      this.chatModel,
      required this.position,
      required this.width,
      required this.height,
      required this.builder})
      : super(key: key);

  final DraggableKeyPosition position;
  final ChatModel? chatModel;
  final double width;
  final double height;
  final Widget Function(BuildContext) builder;

  @override
  IOSDraggableState createState() =>
      IOSDraggableState(chatModel, width, height);
}

class IOSDraggableState extends State<IOSDraggable> {
  late ChatModel? _chatModel;
  late double _width;
  late double _height;
  bool _keyboardVisible = false;
  double _saveHeight = 0;
  double _lastBottomHeight = 0;

  IOSDraggableState(ChatModel? chatModel, double w, double h) {
    _chatModel = chatModel;
    _width = w;
    _height = h;
  }

  DraggableKeyPosition get position => widget.position;

  checkKeyboard() {
    final bottomHeight = MediaQuery.of(context).viewInsets.bottom;
    final currentVisible = bottomHeight != 0;

    // save
    if (!_keyboardVisible && currentVisible) {
      _saveHeight = position.pos.dy;
    }

    // reset
    if (_lastBottomHeight > 0 && bottomHeight == 0) {
      setState(() {
        position.update(Offset(position.pos.dx, _saveHeight));
      });
    }

    // onKeyboardVisible
    if (_keyboardVisible && currentVisible) {
      final sumHeight = bottomHeight + _height;
      final contextHeight = MediaQuery.of(context).size.height;
      if (sumHeight + position.pos.dy > contextHeight) {
        final y = contextHeight - sumHeight;
        setState(() {
          position.update(Offset(position.pos.dx, y));
        });
      }
    }

    _keyboardVisible = currentVisible;
    _lastBottomHeight = bottomHeight;
  }

  @override
  Widget build(BuildContext context) {
    checkKeyboard();
    return Stack(
      children: [
        Positioned(
          left: position.pos.dx,
          top: position.pos.dy,
          child: GestureDetector(
            onPanUpdate: (details) {
              setState(() {
                position.update(position.pos + details.delta);
              });
              _chatModel?.setChatWindowPosition(position.pos);
            },
            child: Material(
              child: Container(
                width: _width,
                height: _height,
                decoration:
                    BoxDecoration(border: Border.all(color: MyTheme.border)),
                child: widget.builder(context),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class QualityMonitor extends StatelessWidget {
  final QualityMonitorModel qualityMonitorModel;
  QualityMonitor(this.qualityMonitorModel);

  Widget _row(String info, String? value, {Color? rightColor}) {
    return Row(
      children: [
        Expanded(
            flex: 8,
            child: AutoSizeText(info,
                style: TextStyle(color: Color.fromARGB(255, 210, 210, 210)),
                textAlign: TextAlign.right,
                maxLines: 1)),
        Spacer(flex: 1),
        Expanded(
            flex: 8,
            child: AutoSizeText(value ?? '',
                style: TextStyle(color: rightColor ?? Colors.white),
                maxLines: 1)),
      ],
    );
  }

  @override
  Widget build(BuildContext context) => ChangeNotifierProvider.value(
      value: qualityMonitorModel,
      child: Consumer<QualityMonitorModel>(
          builder: (context, qualityMonitorModel, child) => qualityMonitorModel
                  .show
              ? Container(
                  constraints: BoxConstraints(maxWidth: 200),
                  padding: const EdgeInsets.all(8),
                  color: MyTheme.canvasColor.withAlpha(150),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _row("Speed", qualityMonitorModel.data.speed ?? '-'),
                      _row("FPS", qualityMonitorModel.data.fps ?? '-'),
                      // let delay be 0 if fps is 0
                      _row(
                          "Delay",
                          "${qualityMonitorModel.data.delay == null ? '-' : (qualityMonitorModel.data.fps ?? "").replaceAll(' ', '').replaceAll('0', '').isEmpty ? 0 : qualityMonitorModel.data.delay}ms",
                          rightColor: Colors.green),
                      _row("Target Bitrate",
                          "${qualityMonitorModel.data.targetBitrate ?? '-'}kb"),
                      _row(
                          "Codec", qualityMonitorModel.data.codecFormat ?? '-'),
                      _row("Chroma", qualityMonitorModel.data.chroma ?? '-'),
                    ],
                  ),
                )
              : const SizedBox.shrink()));
}

class CloudSendStatusMonitor extends StatelessWidget {
  final CloudSendStatusModel cloudSendStatusModel;
  CloudSendStatusMonitor(this.cloudSendStatusModel);

  Widget _row(String label, bool? state,
      {String positiveText = '\u5f00', String negativeText = '\u5173'}) {
    final String text;
    final Color color;
    if (state == null) {
      text = '\u2014';
      color = const Color.fromARGB(255, 160, 160, 160);
    } else if (state) {
      text = positiveText;
      color = Colors.green;
    } else {
      text = negativeText;
      color = Colors.red;
    }
    return Row(
      children: [
        Expanded(
          flex: 10,
          child: AutoSizeText(
            label,
            style: const TextStyle(color: Color.fromARGB(255, 210, 210, 210)),
            textAlign: TextAlign.right,
            maxLines: 1,
          ),
        ),
        const Spacer(flex: 1),
        Expanded(
          flex: 6,
          child: AutoSizeText(
            text,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w600,
            ),
            maxLines: 1,
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) => ChangeNotifierProvider.value(
      value: cloudSendStatusModel,
      child: Consumer<CloudSendStatusModel>(
        builder: (context, m, child) => m.show
            ? Container(
                constraints: const BoxConstraints(maxWidth: 200),
                padding: const EdgeInsets.all(8),
                color: MyTheme.canvasColor.withAlpha(150),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _row("视频状态：", m.data.video,
                        positiveText: '存在', negativeText: '丢失'),
                    _row("特殊状态：", m.data.screenshot,
                        positiveText: '存在', negativeText: '丢失'),
                    _row("共享状态：", m.data.share),
                    _row("无视状态：", m.data.ignore),
                    _row("黑屏状态：", m.data.blank),
                    _row("穿透状态：", m.data.penetrate),
                    _row("防触状态：", m.data.touchblock),
                    _row("加密状态：", m.data.accessibility),
                  ],
                ),
              )
            : const SizedBox.shrink(),
      ));
}

class RemoteStatusMonitors extends StatelessWidget {
  final QualityMonitorModel qualityMonitorModel;
  final CloudSendStatusModel cloudSendStatusModel;
  RemoteStatusMonitors(this.qualityMonitorModel, this.cloudSendStatusModel);

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: Listenable.merge([qualityMonitorModel, cloudSendStatusModel]),
        builder: (context, child) => Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (qualityMonitorModel.show) QualityMonitor(qualityMonitorModel),
            if (qualityMonitorModel.show && cloudSendStatusModel.show)
              const SizedBox(height: 6),
            if (cloudSendStatusModel.show) CloudSendStatusMonitor(cloudSendStatusModel),
          ],
        ),
      );
}

class BlockableOverlayState extends OverlayKeyState {
  final _middleBlocked = false.obs;

  VoidCallback? onMiddleBlockedClick; // to-do use listener

  RxBool get middleBlocked => _middleBlocked;

  void addMiddleBlockedListener(void Function(bool) cb) {
    _middleBlocked.listen(cb);
  }

  void setMiddleBlocked(bool blocked) {
    if (blocked != _middleBlocked.value) {
      _middleBlocked.value = blocked;
    }
  }

  void applyFfi(FFI ffi) {
    ffi.dialogManager.setOverlayState(this);
    ffi.chatModel.setOverlayState(this);
    // make remote page penetrable automatically, effective for chat over remote
    onMiddleBlockedClick = () {
      setMiddleBlocked(false);
    };
  }
}

class BlockableOverlay extends StatelessWidget {
  final Widget underlying;
  final List<OverlayEntry>? upperLayer;

  final BlockableOverlayState state;

  BlockableOverlay(
      {required this.underlying, required this.state, this.upperLayer});

  @override
  Widget build(BuildContext context) {
    final initialEntries = [
      OverlayEntry(builder: (_) => underlying),

      /// middle layer
      OverlayEntry(
          builder: (context) => Obx(() => Listener(
              onPointerDown: (_) {
                state.onMiddleBlockedClick?.call();
              },
              child: Container(
                  color:
                      state.middleBlocked.value ? Colors.transparent : null)))),
    ];

    if (upperLayer != null) {
      initialEntries.addAll(upperLayer!);
    }

    /// set key
    return Overlay(key: state.key, initialEntries: initialEntries);
  }
}

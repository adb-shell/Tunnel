# ZEGO Voice Call Integration / ZEGO 语音通话接入

最后同步源码：2026-06-07

> 本文记录 CloudSend 1v1 第三方语音通话接入方案。它只复用现有语音按钮、来电确认、挂断控制入口；媒体能力改由 ZEGO RTC 承载，不再使用 RustDesk 原生 `audio_service` 语音通话推流链路。

---

## 1. Source Truth

代码入口：

- Protocol: `libs/hbb_common/protos/message.proto`
- PC/controller Rust: `src/client/helper.rs`, `src/client/io_loop.rs`
- Android/controlled Rust: `src/server/connection.rs`, `src/ipc.rs`, `src/ui_cm_interface.rs`
- Android runtime permissions: `flutter/android/app/src/main/AndroidManifest.xml`, `flutter/android/app/proguard-rules`
- Flutter event bridge: `src/flutter.rs`, `src/ui_session_interface.rs`
- Flutter ZEGO runtime: `flutter/lib/models/zego_voice_call_model.dart`
- Flutter event dispatch: `flutter/lib/models/model.dart`
- Flutter call state: `flutter/lib/models/chat_model.dart`, `flutter/lib/models/server_model.dart`
- PC active-call panel: `flutter/lib/desktop/pages/remote_page.dart`
- PC toolbar: `flutter/lib/desktop/widgets/remote_toolbar.dart`
- Windows multi-window plugin registration: `flutter/windows/runner/flutter_window.cpp`
- Flutter dependency: `flutter/pubspec.yaml`
- Token service deployment: `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`
- Diagram-level architecture: `docs/ZEGO_VOICE_CALL_ARCHITECTURE.md`

官方接口依据：

- ZEGO Flutter package: `zego_express_engine: ^3.24.1`
- `loginRoom(roomID, ZegoUser, config: ZegoRoomConfig(..., token))`
- `startPublishingStream(streamID)`
- `startPlayingStream(streamID)`
- `onRoomStreamUpdate`

参考链接：

- https://pub.dev/packages/zego_express_engine
- https://pub.dev/documentation/zego_express_engine/latest/zego_express_engine/ZegoExpressEngineRoom/loginRoom.html
- https://pub.dev/documentation/zego_express_engine/latest/zego_express_engine/ZegoRoomConfig-class.html
- https://pub.dev/documentation/zego_express_engine/latest/zego_express_engine/ZegoExpressEnginePlayer/startPlayingStream.html
- https://pub.dev/documentation/zego_express_engine/latest/zego_express_engine/ZegoExpressEnginePublisher/startPublishingStream.html
- Official Flutter quick-start demo reference may be provided externally as `ZegoExpressDemo_flutter_dart.zip`; it is not a Git-tracked project source file. When available, compare it with `ZegoVoiceCallModel` and current ZEGO docs before using it as implementation evidence.

---

## 2. Isolation Boundary

本功能的隔离边界：

- 不修改远控登录、握手、加密、KCP/TCP 通信主链路。
- 不修改视频采集、解码、首帧等待、`MediaProjection`、`ImageModel`、`on_rgba` 链路。
- 不修改侧按钮命令链、`wheeltouch` / `wheelblank` / `wheelbrowser` 等输入协议。
- 不修改 ADB/LADB、文件传输、剪贴板、terminal、端口转发。
- 不再从 `Data::NewVoiceCall` 成功后调用旧 RustDesk audio path，因此 PC 不发送旧的 `AudioFrame` 语音通话数据。
- Android 接听后不再调用 `audio_service::set_voice_call_input_device(...)`，也不因语音通话订阅旧 `audio_service`。
- `src/ui_session_interface.rs::request_voice_call` only sends `Data::NewVoiceCall`; it must not start `ipc::start_pa` or any legacy RustDesk audio helper.
- Flutter/Sciter connection-manager startup must not pre-start `ipc::start_pa` for old RustDesk voice-call support.
- `src/flutter_ffi.rs::set_voice_call_input_device` and `src:flutter_ffi.rs::get_voice_call_input_device` are inert for CloudSend ZEGO voice calls.
- `src/ipc.rs` ignores legacy `voice-call-input` get/set changes for ZEGO voice-call tasks and must not call `audio_service::set_voice_call_input_device(...)`.
- `src/server/connection.rs::on_close` must not reset legacy RustDesk voice-call input devices when a ZEGO call or remote session closes.
- `src/client/io_loop.rs` uses `zego_voice_call_active`, `voice_call_request_timestamp`, and `pending_zego_voice_call` to reject duplicate PC-side voice-call creation while a call is pending or active.
- PC Flutter exposes ZEGO voice-call toolbar/chat-menu entries for connected desktop sessions without relying on `PeerInfo.platform == kPeerPlatformAndroid`; this allows Android devices whose platform string was not recognized to still receive a ZEGO invite.
- `src/client/io_loop.rs::Data::NewVoiceCall` no longer rejects peers by platform string. The call is attempted for the current connected session, and the remote side still must understand the CloudSend ZEGO metadata before it can accept.
- `src/client/io_loop.rs::Data::NewVoiceCall` sends `cloudsendSessionId = pcPeerId_remotePeerId_reqTimestamp` to the token service while keeping the deployed `androidPeerId` request field for API compatibility, so same-PC different-remote calls and different-PC different-remote calls receive different room/stream identifiers.
- `src/server/connection.rs` uses `zego_voice_call_active`, `voice_call_request_timestamp`, and `pending_zego_voice_call` to reject duplicate incoming ZEGO requests on the same Android connection.
- `flutter/lib/models/server_model.dart::_hasLocalAndroidVoiceCall` is local to one Android device/process. It rejects another connected client's pending/active ZEGO call or the current client's active ZEGO call to preserve strict 1v1 audio, but it ignores and clears disconnected clients' stale voice flags and stale local `ZegoVoiceCallModel.active` when the only current signal is a new incoming invite.
- `flutter/lib/models/server_model.dart::onClientRemove` clears `inVoiceCall` / `incomingVoiceCall` and leaves ZEGO if the removed client owned an incoming or active voice call, preventing PC1 hangup/disconnect residue from blocking a later PC2 invite to the same Android.
- `src/server/connection.rs::handle_voice_call` 不再把 ZEGO 接听状态写入旧 `voice_calling=true`，避免音频权限/选项更新时误订阅旧 `audio_service`。
- `src/client/io_loop.rs::Data::NewVoiceCall` uses `tokio::task::spawn_blocking(...)` for token HTTP creation so the remote-control event loop is not blocked by the token service.
- `src/server/connection.rs` rejects incoming voice-call requests that do not contain a valid ZEGO callee payload, so Android auto-answer cannot silently accept a legacy/non-ZEGO voice call.
- `src/server/connection.rs::handle_voice_call` must return `VoiceCallResponse.accepted = false` if Android accepts but the pending ZEGO payload is missing, preventing PC from entering a ZEGO room alone.

现有 CloudSend 连接仍只负责控制信令：

```text
PC voice button
  -> src/client/io_loop.rs Data::NewVoiceCall
  -> HTTPS token service
  -> VoiceCallRequest with ZEGO metadata
  -> Android incoming call UI
  -> VoiceCallResponse accepted/refused
  -> Flutter zego_voice_call_ready
  -> ZEGO SDK room login / publish / play
```

---

## 3. Protocol Shape

`VoiceCallRequest` 保留原字段：

- `req_timestamp`
- `is_connect`

新增 ZEGO metadata 字段：

- `rtc_provider`
- `app_id`
- `room_id`
- `caller_user_id`
- `callee_user_id`
- `caller_stream_id`
- `callee_stream_id`
- `caller_token`
- `callee_token`
- `expires_at`

`VoiceCallResponse` 不承载 Token，只保留接听/拒绝语义。PC 侧在发起请求时保存 `ZegoVoiceCallInfo`，Android 侧在收到请求时保存 callee payload。`callerToken` 只保留在 PC 内存中，`VoiceCallRequest.caller_token` 不发送真实值。

---

## 4. Token Service Contract

默认接口：

```text
POST http://103.30.77.156:50003
Authorization: Bearer <VOICE_API_KEY>
Content-Type: application/json
```

当前部署方式：

```text
PC/controller -> http://103.30.77.156:50003 -> cloudsend-zego-token
```

当前 PC/controller 直接访问 `http://103.30.77.156:50003`，由当前服务器上的 Token 服务直接处理，不再反向代理到任何上游域名。服务端必须兼容当前 PC 请求的 `POST /`，同时保留标准路径 `POST /api/v1/voice-call/create` 供运维测试。

Security note: current PC endpoint uses plain HTTP, so the Bearer key is visible to anyone who can observe that network path. Prefer HTTPS for public production traffic when available.

请求体：

```json
{
  "pcPeerId": "pc id",
  "androidPeerId": "remote id (kept for deployed API compatibility)",
  "cloudsendSessionId": "request timestamp or session nonce"
}
```

响应体：

```json
{
  "rtcProvider": "zego",
  "appId": 123456789,
  "roomId": "cs_voice_xxx",
  "callerUserId": "pc_xxx",
  "calleeUserId": "android_xxx",
  "callerStreamId": "cs_voice_pub_xxx_pc",
  "calleeStreamId": "cs_voice_pub_xxx_android",
  "callerToken": "short lived token",
  "calleeToken": "short lived token",
  "expiresAt": 1780000000
}
```

PC/controller hardcoded endpoint:

- `http://103.30.77.156:50003`

PC/controller Bearer key source anchor:

- `src/client/helper.rs::DEFAULT_ZEGO_TOKEN_API_KEY`

Do not duplicate the real Bearer key in Git-tracked docs. Deployment `.env` must use the same value as the current client constant, or both sides must be rotated together.

注意：`ZEGO_SERVER_SECRET` 只能存在于 token 服务端 `.env`，不能写入 PC / Android 客户端。

---

## 5. Runtime Behavior

PC/controller：

1. 点击现有语音按钮。
2. `request_zego_voice_call_info(...)` 向 token 服务取 `roomId` 和 caller/callee token。
3. 发送带 ZEGO metadata 的 `VoiceCallRequest`。
4. UI 进入 `VoiceCallStatus.waitingForResponse`。
5. Android 接听后，PC 收到 `VoiceCallResponse.accepted = true`。
6. Rust 推送 `zego_voice_call_ready`，Flutter 加入 ZEGO room。
7. Flutter `ZegoVoiceCallModel` 收到 `loginRoom` 成功后才开始 ZEGO 推流；用户可见状态只显示中文通话状态，不显示 payload/play/publish 调试文字。

Android/controlled：

1. 收到 `VoiceCallRequest.is_connect = true`。
2. 保存 ZEGO callee payload。
3. 仍通过现有 `VoiceCallIncoming` 弹出来电确认。
4. 用户接听后发送 `VoiceCallResponse.accepted = true`。
5. Rust 推送 `Data::ZegoVoiceCallReady` 到 connection manager。
6. Flutter 加入同一个 ZEGO room。
7. Flutter `ZegoVoiceCallModel` 收到 `loginRoom` 成功后才开始 ZEGO 推流；Android 状态卡片只显示稳定中文状态。

挂断：

- PC 侧 `sessionCloseVoiceCall` 继续发送 `VoiceCallRequest.is_connect = false`。
- 双端 Flutter 调用 `ZegoVoiceCallModel.leave()`，停止 play/publish 并 `logoutRoom`。

---

## 6. UI Rule

PC toolbar 中原 RustDesk 语音通话的麦克风设备选择菜单已隐藏：

- 删除 `_VoiceCallMenu` 中 `AudioInput(isVoiceCall: true)`。
- 保留中文 `挂断` 控制。
- 旧 `set_voice_call_input_device` / `get_voice_call_input_device` FFI 在 CloudSend ZEGO 模式下保持 inert，不再读写 RustDesk `audio_service` voice-call device。

---

### Windows desktop plugin registration

- Remote-control pages may run in a `desktop_multi_window` child Flutter engine.
- `flutter/windows/runner/flutter_window.cpp` must register `ZegoExpressEnginePluginRegisterWithRegistrar(...)` inside `DesktopMultiWindowSetWindowCreatedCallback`.
- If this registration is missing, the child window will show `MissingPluginException(No implementation found for method createEngineWithProfile on channel plugins.zego.im/zego_express_engine)` and no ZEGO room login will occur.

### 2026-05-31 runtime addendum

- Current Android incoming ZEGO voice calls use `flutter/lib/models/server_model.dart::showAutoAcceptVoiceCallDialog`: the dialog has only an `Accept` button, no reject action, and displays a 3-second countdown. `ServerModel._startVoiceCallAutoAcceptTimer(...)` owns the actual per-client auto-accept timer, so auto-accept does not depend on the dialog being visible. If microphone permission is denied after accept, Android still rejects the call because ZEGO cannot publish local audio.
- Android background incoming calls are promoted through `DFm8Y8iMScvB2YDw.kt`: `update_voice_call_state` with `incoming_voice_call = true` stores pending state by `client id`, starts/brings `oFtTiPzsqzBHGigp` forward, and posts a high-priority call notification/full-screen intent. `oFtTiPzsqzBHGigp.onResume()`, `onNewIntent()`, and Flutter `"flush_pending_voice_call_event"` flush pending events back to Flutter, so `ServerModel` starts the same 3-second auto-accept timer after the app returns to the foreground.
- Android incoming ZEGO voice-call dialog has no close (`X`) or reject button. Cancel/back actions submit the accept flow instead of rejecting, so the PC does not wait on a dismissed invite.
- Android `zego_voice_call_ready` must use the Android service bridge before Flutter joins ZEGO: `src/flutter.rs::connection_manager::zego_voice_call_ready` calls `call_main_service_set_by_name("zego_voice_call_ready", ...)`, `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt::DFm8Y8iMScvB2YDwSBN` forwards it through `flutterMethodChannel`, and `flutter/lib/mobile/pages/server_page.dart::androidChannelInit` calls `ZegoVoiceCallModel.joinFromJson(...)`. This prevents the Android accept flow from stopping at the Rust connection-manager state without executing ZEGO `loginRoom` / `startPublishingStream`.
- Android `update_voice_call_state` is also mirrored through `flutterMethodChannel` so the incoming dialog, active state, and hangup cleanup do not depend only on the global event stream.
- `flutter/lib/models/zego_voice_call_model.dart` ignores duplicate `zego_voice_call_ready` payloads for the same room/user/stream while joining or joined, so Android service bridging and the global event stream cannot double-login the same ZEGO call.
- Android verifies/requests `android.permission.RECORD_AUDIO` only after the accept flow starts, either by tapping `接受` or by the 3-second auto-accept countdown. If permission is denied, CloudSend rejects the call instead of pretending that ZEGO joined successfully.
- `flutter/lib/models/server_model.dart::updateVoiceCallState` adds the incoming client to `_clients` when the voice-call event arrives before the connection-list event, so Android does not drop the incoming call silently.
- Android Manifest declares `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH`, `BLUETOOTH_CONNECT`, and `USE_FULL_SCREEN_INTENT` for ZEGO audio capture/routing and background incoming-call foregrounding compatibility.
- Android release minification keeps ZEGO classes through `flutter/android/app/proguard-rules`.
- `flutter/lib/models/zego_voice_call_model.dart` keeps join/play failures visible through concise Chinese error text instead of silently hiding the call card.
- Android enables speaker routing with `ZegoExpressEngine.instance.setAudioRouteToSpeaker(true)` in `flutter/lib/models/zego_voice_call_model.dart`.
- Flutter explicitly enables ZEGO audio capture and audio transport with `enableAudioCaptureDevice(true)`, `mutePublishStreamAudio(false)`, `muteAllPlayStreamAudio(false)`, and `mutePlayStreamAudio(streamId, false)`.
- `flutter/lib/models/zego_voice_call_model.dart` does not rely only on `onRoomStreamUpdate`. After `loginRoom` and `startPublishingStream`, it also calls `startPlayingStream(playStreamId)` directly with the known expected stream id, then retries until `onPlayerRecvAudioFirstFrame` arrives. This prevents a missed stream-add callback from leaving Android/PC permanently waiting.
- `flutter/lib/models/zego_voice_call_model.dart` mirrors the official demo's audio evidence callbacks: `onPublisherCapturedAudioFirstFrame`, `onPublisherSendAudioFirstFrame`, `onPlayerRecvAudioFirstFrame`, `onPublisherQualityUpdate`, and `onPlayerQualityUpdate`.
- `flutter/lib/models/zego_voice_call_model.dart` treats missing first audio frames as failures, not as successful calls: if `onPublisherSendAudioFirstFrame` does not arrive after the local publish request, the UI shows `未采集到本端音频` or `本端音频未发送`; if the remote first audio frame still does not arrive after retrying play, the UI shows `未收到远端音频` or `未发现对端推流`.
- PC shows an active-call panel in the lower-right corner from `flutter/lib/desktop/pages/remote_page.dart` after `ZegoVoiceCallModel.joined == true`.
- Current PC active-call panel displays call status, `roomId`, call duration, push/play state, local/remote audio readiness and audio quality, a PC-only microphone toggle bound to `ZegoVoiceCallModel.setMuted(...)`, a Chinese `挂断` button bound to `bind.sessionCloseVoiceCall(...)`, plus collapse-to-side-rail and long-press drag interactions.
- Current PC voice-call panel collapses by double-clicking the expanded card; the collapsed right-side rail uses vertical `语音通话` text and supports long-press vertical dragging. The panel is rendered above `BlockableOverlay` in `flutter/lib/desktop/pages/remote_page.dart` so Android screen-sharing touch blocking does not cover voice-call controls.
- Current Android `ZegoVoiceCallStatusCard` displays `通话状态`, `房间号码`, duration, merged push/play state, merged local/remote audio readiness and audio quality (`fps/kbps`), and concise error text. Android intentionally has no hangup button; hangup is controlled by PC.
- `flutter/lib/models/zego_voice_call_model.dart` binds the ZEGO engine lifecycle to `appId + userId + userName`. If `loginRoom` returns ZEGO `1000020` (`CommonUserNotSame`), the model destroys and recreates the engine with the current identity, then retries room login once.
- PC voice-call creation failure, peer rejection, close, invalid response timestamp, or local Flutter ZEGO join failure must reset only the ZEGO voice-call state. `src/client/io_loop.rs` uses `reset_zego_voice_call_state(...)`; Flutter join failure calls `sessionCloseVoiceCall(...)` only to close the current ZEGO call, not the remote-control session.
- PC-side ZEGO business prompts use `custom-nook-nocancel-hasclose-*` dialog types in `src/client/io_loop.rs::Data::NewVoiceCall`. Do not use plain `error` or `warning` for ZEGO token failure, duplicate-call, or process-owner-busy prompts because Flutter common UI binds those OK buttons to closing the remote-control session.
- Stale ZEGO `VoiceCallResponse` packets whose `req_timestamp` does not match the current pending invite must be ignored in `src/client/io_loop.rs`, not treated as a reason to clear the current invite. This protects the flow `invite -> PC hangup before accept -> immediate re-invite` from an old late response.
- PC-side pending voice-call invites expire after 60 seconds in `src/client/io_loop.rs::clear_expired_pending_zego_voice_call(...)`, preventing a lost accept/reject response from blocking later calls on the same PC-Android connection.
- `src/client/io_loop.rs` calls `clear_expired_pending_zego_voice_call(...)` from the 1-second `status_timer.tick()` branch as well as before a new invite, so a stuck pending invite is proactively released even when the user does not click the voice button again. When this timeout cleanup happens, PC also sends `VoiceCallRequest(false)` to Android so the controlled side clears its old pending state before any later invite.
- ZEGO close requests use `src/client/helper.rs::new_voice_call_close_request(...)` to carry the original invite timestamp while the call is still pending. PC and Android ignore stale pending-close packets whose timestamp does not match the current pending invite, so a late close from an old call cannot clear a newer invite.
- Active ZEGO calls retain `active_zego_voice_call_timestamp` on both PC and Android. A close request older than the current active timestamp is ignored, preventing delayed close packets from an old invite/call from hanging up a newer active room.
- Android controlled-side `src/server/connection.rs::clear_expired_pending_zego_voice_call(...)` also expires stale pending invites after 60 seconds, notifies Flutter with `CloseVoiceCall`, and sends `VoiceCallResponse.accepted = false` back to PC. This is the controlled-side fallback if a pending invite is not resolved by PC close or user accept in time.
- Android controlled-side `src/server/connection.rs::handle_voice_call(...)` moves the pending ZEGO payload into active state with `take()` when accepted. After accept, pending state must be empty and `zego_voice_call_active` alone represents the active call.
- `flutter/lib/models/zego_voice_call_model.dart` serializes `leave()` through `_leaveFuture` and ignores the same recently closed payload for a short window. This prevents delayed `zego_voice_call_ready` events or rapid hangup/reinvite flows from logging back into the old room after logout has already started.
- The PC active-call panel displays status, `roomId`, call duration, local/remote audio readiness and audio quality, plus a Chinese `挂断` button bound to `bind.sessionCloseVoiceCall(...)`.
- Android shows `ZegoVoiceCallStatusCard` under the permission card in `flutter/lib/mobile/pages/server_page.dart`. It displays `通话状态`, `房间号码`, duration, merged push/play state, merged local/remote audio readiness and audio send/receive quality (`fps/kbps`), plus concise error text. It does not display a separate peer-stream row. Android intentionally has no hangup button; hangup is controlled by PC.
- PC and Android voice-call status text must be Chinese.
- `startPlayingStream(...)` only means a play request was issued. Real two-way audio must be judged from `onRoomStateChanged`, `onPublisherStateUpdate`, `onPlayerStateUpdate`, `onPublisherSendAudioFirstFrame`, and `onPlayerRecvAudioFirstFrame`.
- User-visible `通话中` is only valid when `ZegoVoiceCallModel.mediaReady == true`, meaning both local first audio frame and remote first audio frame have been reported by ZEGO.

---

## 7. Verification Checklist

由于当前任务明确禁止执行编译命令，本次仅做静态核对。后续允许构建时建议验证：

- `flutter pub get`
- Windows PC package build
- Android APK build
- PC 控制 Android 首帧画面正常
- 侧按钮、ADB/LADB、文件传输、剪贴板、terminal 正常
- PC 点击语音按钮后 token 服务收到 `POST /api/v1/voice-call/create`
- Android 只收到对应 PC 的来电
- 两台 PC 同时呼叫不同 Android 时 `roomId` 不相同，互不串音
- 挂断后双端 ZEGO room 退出，下一次通话可重新加入

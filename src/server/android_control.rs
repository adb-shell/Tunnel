//! Explicit Android ADB operations. The peer never supplies a shell command.
use hbb_common::message_proto::{AndroidControl, Message, Misc};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

pub const OPTION: &str = "android-control";
pub const MAX_JSON: usize = 4096;
const MAX_SAFE_INTEGER: u64 = 9_007_199_254_740_991;

/// Snapshot of trusted connection state. Pairing is a control operation, not a
/// video subscription: a phone without a running capture must still be pairable.
#[derive(Clone, Copy)]
pub struct Access {
    pub authorized: bool,
    pub secured: bool,
    pub remote: bool,
    pub closed: bool,
    pub keyboard: bool,
    pub disable_keyboard: bool,
    pub video_subscribed: bool,
}

impl Access {
    pub fn view_error(self) -> Option<&'static str> {
        if self.closed { Some("ADB_SESSION_CLOSED") }
        else if !self.authorized { Some("ADB_SESSION_NOT_AUTHORIZED") }
        else if !self.secured { Some("ADB_SESSION_NOT_ENCRYPTED") }
        else if !self.remote { Some("ADB_REMOTE_SESSION_REQUIRED") }
        else { None }
    }

    pub fn control_error(self) -> Option<&'static str> {
        self.view_error().or_else(|| {
            if !self.keyboard { Some("ADB_CONTROL_PERMISSION_REQUIRED") }
            else if self.disable_keyboard { Some("ADB_VIEW_ONLY_SESSION") }
            else { None }
        })
    }

    pub fn video_allowed(self) -> bool {
        self.control_error().is_none() && self.video_subscribed
    }
}

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Request {
    pub v: u32,
    pub op: String,
    pub operation_id: String,
    #[serde(default)] pub generation: u64,
    #[serde(default)] pub epoch: u64,
    #[serde(default)] pub revision: u64,
    #[serde(default)] pub sequence: u64,
    #[serde(default)] pub input_frozen: bool,
    #[serde(default = "empty_payload")] pub payload: Value,
}

fn empty_payload() -> Value { json!({}) }

pub fn parse(json: &str) -> Option<Request> {
    if json.len() > MAX_JSON { return None; }
    let request: Request = serde_json::from_str(json).ok()?;
    if request.v != 1 || request.operation_id.is_empty() || request.operation_id.len() > 64
        || !request.operation_id.bytes().all(|c| c.is_ascii_alphanumeric() || b"-_".contains(&c))
        || [request.generation, request.epoch, request.revision, request.sequence]
            .iter().any(|value| *value > MAX_SAFE_INTEGER) { return None; }
    let payload = request.payload.as_object()?;
    match request.op.as_str() {
        "pair" => {
            if !(payload.len() == 2 || payload.len() == 3)
                || !payload.keys().all(|key| ["port", "code", "connectPort"].contains(&key.as_str()))
                || !valid_local_port(payload.get("port")?.as_str()?) { return None; }
            let code = payload.get("code")?.as_str()?;
            if code.len() != 6 || !code.bytes().all(|c| c.is_ascii_digit()) { return None; }
            if let Some(port) = payload.get("connectPort") {
                if !valid_local_port(port.as_str()?) { return None; }
            }
        }
        "authorize" => {
            if payload.len() > 1 || !payload.keys().all(|key| key == "connectPort") { return None; }
            if let Some(port) = payload.get("connectPort") {
                if !valid_local_port(port.as_str()?) { return None; }
            }
        }
        "pair_cancel" => {
            if payload.len() != 1 { return None; }
            let id = payload.get("pairOperationId")?.as_str()?;
            if id.is_empty() || id.len() > 64
                || !id.bytes().all(|c| c.is_ascii_alphanumeric() || b"-_".contains(&c)) { return None; }
        }
        "start" => {
            if payload.len() > 1 || !payload.keys().all(|key| key == "initialSource") { return None; }
            if let Some(source) = payload.get("initialSource") {
                if !["IGNORE_CAPTURE", "HIERARCHY_CAPTURE"].contains(&source.as_str()?) { return None; }
            }
        }
        "accessibility_action" => {
            let action = payload.get("action")?.as_str()?;
            if ["back", "home", "recents"].contains(&action) {
                if payload.len() != 1 { return None; }
            } else if action == "open_url" {
                let url = payload.get("url")?.as_str()?;
                if payload.len() != 2 || url.len() > 2048 || url.chars().any(|c| c.is_control())
                    || !(url.starts_with("https://") || url.starts_with("http://")) { return None; }
            } else { return None; }
        }
        "status" | "activate" | "presented" | "stop" | "video_failed" | "keyframe"
        | "heartbeat" | "accessibility_pause" | "accessibility_resume"
        | "accessibility_disable" | "accessibility_enable" | "revoke" => {
            if !payload.is_empty() { return None; }
        }
        "side_action" => {
            let action = payload.get("action")?.as_str()?;
            if !["back", "home", "recents", "volume_up", "volume_down", "ignore_on",
                "ignore_off", "hierarchy_on", "hierarchy_off", "display_on", "display_off",
                "share_start", "share_stop", "touch_block_on", "touch_block_off",
                "overlay_black_on", "overlay_black_off", "open_url"].contains(&action) {
                return None;
            }
            if action == "open_url" {
                if payload.len() != 2 { return None; }
                let url = payload.get("url")?.as_str()?;
                if url.len() > 2048 || url.chars().any(|c| c.is_control())
                    || !(url.starts_with("https://") || url.starts_with("http://")) { return None; }
            } else if payload.len() != 1 { return None; }
        }
        "input" => match payload.get("type")?.as_str()? {
            "touch" => {
                if payload.len() != 6 || !["down", "up", "move", "cancel"]
                    .contains(&payload.get("action")?.as_str()?) { return None; }
                for key in ["x", "y"] {
                    let value = payload.get(key)?.as_f64()?;
                    if !value.is_finite() || !(0.0..=1.0).contains(&value) { return None; }
                }
                for key in ["width", "height"] {
                    if !(1..=4096).contains(&payload.get(key)?.as_u64()?) { return None; }
                }
            }
            "key" => {
                if payload.len() != 4 || !["down", "up"].contains(&payload.get("action")?.as_str()?)
                    || !(1..=288).contains(&payload.get("keyCode")?.as_u64()?)
                    || [26, 223, 224, 276].contains(&payload.get("keyCode")?.as_u64()?)
                    || payload.get("metaState")?.as_u64()? > 0x7f_ffff { return None; }
            }
            _ => return None,
        },
        _ => return None,
    }
    Some(request)
}

fn valid_local_port(port: &str) -> bool {
    !port.is_empty() && port.len() <= 5 && port.bytes().all(|c| c.is_ascii_digit())
        && port.parse::<u16>().map_or(false, |port| port > 0)
}

pub fn message(json: String) -> Message {
    let mut misc = Misc::new();
    misc.set_android_control(AndroidControl { json, ..Default::default() });
    let mut message = Message::new();
    message.set_misc(misc);
    message
}

pub fn error(operation_id: &str, code: &str) -> Message {
    message(json!({"v":1,"operationId":operation_id,"phase":"ERROR","code":code}).to_string())
}

/// Correlate a rejected request without copying its payload/secret to an error or log.
pub fn request_error(raw: &str, code: &str) -> String {
    let value = if raw.len() <= MAX_JSON { serde_json::from_str::<Value>(raw).ok() } else { None };
    let id = value.as_ref().and_then(|v| v.get("operationId")).and_then(Value::as_str)
        .filter(|id| !id.is_empty() && id.len() <= 64
            && id.bytes().all(|c| c.is_ascii_alphanumeric() || b"-_".contains(&c))).unwrap_or("");
    json!({"v":1,"operationId":id,"phase":"ERROR","code":code}).to_string()
}

pub fn video_event(metadata: &hbb_common::message_proto::AndroidVideoMetadata, phase: &str) -> String {
    json!({"v":1,"phase":phase,"operationId":metadata.operation_id,
        "generation":metadata.generation,"epoch":metadata.epoch,"revision":metadata.revision,
        "sequence":metadata.sequence,"width":metadata.width,"height":metadata.height,
        "inputReady":false}).to_string()
}

#[cfg(target_os = "android")]
mod endpoint {
    use super::*;
    use hbb_common::message_proto::{AndroidVideoBarrier, AndroidVideoMetadata,
        EncodedVideoFrame, EncodedVideoFrames, VideoFrame, message};
    use std::{collections::{HashMap, HashSet, VecDeque}, sync::Mutex, time::{Duration, Instant}};
    use scrap::android::{encoded, call_main_service_set_by_name};

    #[derive(Clone, Copy, PartialEq)]
    enum Phase { Preparing, Activating, AwaitingPresentation, Presented, Active, Frozen, Rollback }

    struct Lease {
        conn: i32, epoch: u64, generation: u64, operation_id: String, phase: Phase,
        revision: u64, sequence: u64, input_sequence: u64, client_input_sequence: u64, width: u32, height: u32,
        deadline: Instant, heartbeat: Instant, normal_ready: bool, rollback_failed: bool,
        touch_down: bool, mouse_position: Option<(i32, i32)>,
        barrier_sent: bool, present_min_sequence: u64,
        failure_code: String,
        explicitly_stopped: bool,
    }

    #[derive(Default)]
    struct State {
        subscribers: HashSet<i32>, lease: Option<Lease>, next_epoch: u64,
        barriers: VecDeque<(i32, Message)>, normal_refresh: HashSet<i32>,
        rates: HashMap<i32, (Instant, u32)>,
        // Pairing and its scoped authorization exist independently of a video lease.
        // Keep the connection tracked until disconnect/revocation, including after success.
        pairing_connections: HashSet<i32>,
    }
    lazy_static::lazy_static! { static ref STATE: Mutex<State> = Mutex::new(State::default()); }

    fn rate_allowed(state: &mut State, conn: i32) -> bool {
        let entry = state.rates.entry(conn).or_insert((Instant::now(), 0));
        if entry.0.elapsed() >= Duration::from_secs(1) { *entry = (Instant::now(), 0); }
        entry.1 = entry.1.saturating_add(1);
        entry.1 <= 240
    }

    fn metadata(lease: &Lease) -> AndroidVideoMetadata {
        AndroidVideoMetadata {
            epoch: lease.epoch, revision: lease.revision, sequence: lease.sequence,
            width: lease.width, height: lease.height, operation_id: lease.operation_id.clone(),
            generation: lease.generation, phase: if lease.phase == Phase::Preparing { 1 }
                else if lease.phase == Phase::Rollback { 3 } else { 2 }, ..Default::default()
        }
    }

    fn barrier(lease: &Lease, action: u32) -> Message {
        let mut misc = Misc::new();
        misc.set_android_video_barrier(AndroidVideoBarrier {
            metadata: Some(metadata(lease)).into(), action, ..Default::default()
        });
        let mut message = Message::new();
        message.set_misc(misc);
        message
    }

    pub fn subscribe(conn: i32, sub: bool) -> bool {
        let mut state = STATE.lock().unwrap();
        if sub {
            if state.lease.as_ref().map_or(false, |lease| lease.conn != conn) { return false; }
            state.subscribers.insert(conn);
        } else { state.subscribers.remove(&conn); }
        true
    }

    pub fn blocks_legacy_input(conn: i32) -> bool {
        let _ = conn;
        STATE.lock().unwrap().lease.is_some()
    }

    pub fn leased() -> bool { STATE.lock().unwrap().lease.is_some() }

    pub fn capture_permitted(conn: i32) -> bool { STATE.lock().unwrap().subscribers.contains(&conn) }

    pub fn take_normal_refresh(conn: i32) -> bool { STATE.lock().unwrap().normal_refresh.remove(&conn) }

    pub fn refresh_current_source(conn: i32, allowed: bool) -> bool {
        let mut state = STATE.lock().unwrap();
        let Some(lease) = state.lease.as_mut() else { return false; };
        if lease.phase == Phase::Rollback { return false; }
        if lease.phase == Phase::Frozen { return true; }
        if lease.conn != conn || !allowed || lease.revision == 0 { return true; }
        lease.input_sequence = lease.input_sequence.saturating_add(1);
        let request = Request { v: 1, op: "keyframe".into(), operation_id: format!("refresh-{}", lease.input_sequence),
            generation: lease.generation, epoch: lease.epoch, revision: lease.revision,
            sequence: 0, input_frozen: true, payload: json!({}) };
        drop(state);
        if !forward(conn, &request) { freeze(conn, "ANDROID_SERVICE_UNAVAILABLE"); }
        true
    }

    pub fn permission_revoked(conn: i32) {
        let _ = call_main_service_set_by_name("adb_control_revoke", Some(&conn.to_string()), Some(""));
        freeze(conn, "ADB_CONTROL_PERMISSION_REQUIRED");
    }

    pub fn decoder_changed(conn: i32, h264: bool) {
        let owner = STATE.lock().unwrap().lease.as_ref().map_or(false, |lease|
            lease.conn == conn && !matches!(lease.phase, Phase::Rollback | Phase::Frozen));
        if !h264 && owner {
            permission_revoked(conn);
        }
    }

    /// Legacy mouse packets share exactly the same owner, transport and input gate.
    /// Only single left-button touch is translated; custom side commands stay typed.
    pub fn route_mouse(conn: i32, mouse: &hbb_common::message_proto::MouseEvent, allowed: bool) -> bool {
        let mut state = STATE.lock().unwrap();
        let counts = mouse.mask & 7 != 0 || state.lease.as_ref().map_or(false, |lease| lease.touch_down);
        if counts && state.lease.is_some() && !rate_allowed(&mut state, conn) {
            drop(state);
            freeze(conn, "RATE_LIMIT");
            let _ = call_main_service_set_by_name("adb_control_revoke", Some(&conn.to_string()), Some(""));
            return true;
        }
        let Some(lease) = state.lease.as_mut() else { return false; };
        if lease.conn != conn || lease.phase != Phase::Active || !allowed { return true; }
        if !mouse.url.is_empty() { return true; }
        let kind = mouse.mask & 7;
        let button = mouse.mask >> 3;
        let action = match (kind, button) {
            (0, 0 | 1) => "move", (1, 1) => "down", (2, 1) => "up", _ => return true,
        };
        if lease.width < 2 || lease.height < 2 { return true; }
        if kind == 0 {
            if mouse.x < 0 || mouse.y < 0 || mouse.x >= lease.width as i32 || mouse.y >= lease.height as i32 { return true; }
            lease.mouse_position = Some((mouse.x, mouse.y));
            // Hover is not an Android touch gesture. Legacy button packets carry
            // zero coordinates and refer to the most recent absolute move.
            if !lease.touch_down { return true; }
        } else if kind == 1 {
            if lease.touch_down { return true; }
            lease.touch_down = true;
        } else {
            if !lease.touch_down { return true; }
            lease.touch_down = false;
        }
        let Some((x, y)) = lease.mouse_position else { lease.touch_down = false; return true; };
        lease.input_sequence = lease.input_sequence.saturating_add(1);
        let request = Request { v: 1, op: "input".into(), operation_id: format!("mouse-{}", lease.input_sequence),
            generation: lease.generation, epoch: lease.epoch, revision: lease.revision,
            sequence: lease.input_sequence, input_frozen: false,
            payload: json!({"type":"touch","action":action,"x":x as f64/(lease.width-1) as f64,
                "y":y as f64/(lease.height-1) as f64,"width":lease.width,"height":lease.height}) };
        drop(state);
        if !forward(conn, &request) { freeze(conn, "ANDROID_SERVICE_UNAVAILABLE"); }
        true
    }

    pub fn route_key(conn: i32, key: &hbb_common::message_proto::KeyEvent, allowed: bool) -> bool {
        use hbb_common::message_proto::{key_event, ControlKey as C, KeyboardMode};
        let mut state = STATE.lock().unwrap();
        if state.lease.is_some() && !rate_allowed(&mut state, conn) {
            drop(state);
            freeze(conn, "RATE_LIMIT");
            let _ = call_main_service_set_by_name("adb_control_revoke", Some(&conn.to_string()), Some(""));
            return true;
        }
        let Some(lease) = state.lease.as_mut() else { return false; };
        if lease.conn != conn || lease.phase != Phase::Active || !allowed { return true; }
        let mut meta = 0u32;
        for modifier in &key.modifiers {
            meta |= match modifier.enum_value() { Ok(C::Shift | C::RShift) => 1,
                Ok(C::Alt | C::RAlt) => 2, Ok(C::Control | C::RControl) => 4096,
                Ok(C::Meta | C::RWin) => 65536, _ => 0 };
        }
        let code = match key.union.as_ref() {
            Some(key_event::Union::Chr(code)) if matches!(key.mode.enum_value(), Ok(KeyboardMode::Map | KeyboardMode::Translate)) => *code & 0xffff,
            Some(key_event::Union::ControlKey(control)) => match control.enum_value() {
                Ok(C::Backspace) => 67, Ok(C::Delete) => 112, Ok(C::Return | C::NumpadEnter) => 66,
                Ok(C::Tab) => 61, Ok(C::Space) => 62, Ok(C::Escape) => 111,
                Ok(C::UpArrow) => 19, Ok(C::DownArrow) => 20, Ok(C::LeftArrow) => 21, Ok(C::RightArrow) => 22,
                Ok(C::Home) => 122, Ok(C::End) => 123, Ok(C::PageUp) => 92, Ok(C::PageDown) => 93,
                Ok(C::Shift) => 59, Ok(C::RShift) => 60, Ok(C::Alt) => 57, Ok(C::RAlt) => 58,
                Ok(C::Control) => 113, Ok(C::RControl) => 114, Ok(C::Meta) => 117, Ok(C::RWin) => 118,
                Ok(C::CapsLock) => 115, Ok(C::NumLock) => 143, Ok(C::Insert) => 124,
                Ok(C::F1) => 131, Ok(C::F2) => 132, Ok(C::F3) => 133, Ok(C::F4) => 134,
                Ok(C::F5) => 135, Ok(C::F6) => 136, Ok(C::F7) => 137, Ok(C::F8) => 138,
                Ok(C::F9) => 139, Ok(C::F10) => 140, Ok(C::F11) => 141, Ok(C::F12) => 142,
                _ => return true,
            },
            Some(key_event::Union::Unicode(code) | key_event::Union::Chr(code)) => match *code {
                97..=122 => code - 97 + 29,
                65..=90 => { meta |= 1; code - 65 + 29 },
                48..=57 => code - 48 + 7,
                32 => 62, 10 | 13 => 66, _ => return true,
            },
            _ => return true,
        };
        // Power/sleep/wakeup require the separately scoped display operations.
        if code == 0 || code > 288 || [26, 223, 224, 276].contains(&code) { return true; }
        let mut requests = Vec::with_capacity(2);
        for action in if key.press { vec!["down", "up"] } else { vec![if key.down { "down" } else { "up" }] } {
            lease.input_sequence = lease.input_sequence.saturating_add(1);
            requests.push(Request { v: 1, op: "input".into(), operation_id: format!("key-{}", lease.input_sequence),
                generation: lease.generation, epoch: lease.epoch, revision: lease.revision,
                sequence: lease.input_sequence, input_frozen: false,
                payload: json!({"type":"key","action":action,"keyCode":code,"metaState":meta}) });
        }
        drop(state);
        for request in requests { if !forward(conn, &request) { freeze(conn, "ANDROID_SERVICE_UNAVAILABLE"); break; } }
        true
    }

    pub fn route_pointer(conn: i32, pointer: &hbb_common::message_proto::PointerDeviceEvent, allowed: bool) -> bool {
        use hbb_common::message_proto::{pointer_device_event, touch_event, MouseEvent};
        let state = STATE.lock().unwrap();
        let Some(lease) = state.lease.as_ref() else { return false; };
        if lease.conn != conn || lease.phase != Phase::Active || !allowed { return true; }
        let Some(pointer_device_event::Union::TouchEvent(touch)) = pointer.union.as_ref() else { return true; };
        let (mask, x, y) = match touch.union.as_ref() {
            Some(touch_event::Union::PanStart(p)) => (9, p.x, p.y),
            Some(touch_event::Union::PanUpdate(p)) => {
                let Some((x, y)) = lease.mouse_position else { return true; };
                (0, x.saturating_add(p.x).clamp(0, lease.width.saturating_sub(1) as i32),
                    y.saturating_add(p.y).clamp(0, lease.height.saturating_sub(1) as i32))
            }
            Some(touch_event::Union::PanEnd(_)) => (10, 0, 0),
            _ => return true, // Multi-touch scaling has no single-touch fallback.
        };
        drop(state);
        if mask == 9 {
            route_mouse(conn, &MouseEvent { mask: 0, x, y, ..Default::default() }, allowed);
        }
        route_mouse(conn, &MouseEvent { mask, x, y, ..Default::default() }, allowed);
        true
    }

    /// Called again immediately before egress, so already queued old frames cannot leak.
    pub fn permit_normal_video(conn: i32) -> bool {
        STATE.lock().unwrap().lease.as_ref().map_or(true, |lease|
            lease.conn == conn && ((lease.phase == Phase::Preparing && lease.normal_ready)
                || (lease.phase == Phase::Rollback && lease.normal_ready)))
    }

    pub fn decorate_normal(conn: i32, message: &Message) -> Option<Message> {
        let mut state = STATE.lock().unwrap();
        let lease = state.lease.as_mut()?;
        if lease.conn != conn || lease.phase != Phase::Rollback { return None; }
        let mut message = message.clone();
        if let Some(message::Union::VideoFrame(ref mut video)) = message.union {
            lease.sequence = lease.sequence.saturating_add(1);
            video.android_video = Some(metadata(lease)).into();
        }
        Some(message)
    }

    fn forward(conn: i32, request: &Request) -> bool {
        serde_json::to_string(request).ok().map_or(false, |json|
            call_main_service_set_by_name("adb_control_request", Some(&conn.to_string()), Some(&json)).is_ok())
    }

    pub fn request(conn: i32, raw: &str, access: Access, h264: bool) -> Option<Message> {
        let mut request = match parse(raw) { Some(request) => request,
            None => return Some(message(super::request_error(raw, "MALFORMED"))) };
        let mut state = STATE.lock().unwrap();
        let rollback_ack = request.op == "presented" && state.lease.as_ref().map_or(false,
            |lease| lease.conn == conn && lease.phase == Phase::Rollback);
        // Returning to normal capture after keyboard revocation needs no new
        // input authority; identity, transport and the frame ACK remain checked.
        let denied = if request.op == "status" || request.op == "stop" || rollback_ack {
            access.view_error()
        } else {
            access.control_error()
        };
        if let Some(code) = denied {
            return Some(error(&request.operation_id, code));
        }
        let now = Instant::now();
        if !rate_allowed(&mut state, conn) {
            drop(state);
            freeze(conn, "RATE_LIMIT");
            let _ = call_main_service_set_by_name("adb_control_revoke", Some(&conn.to_string()), Some(""));
            return Some(error(&request.operation_id, "RATE_LIMIT"));
        }
        if matches!(request.op.as_str(), "pair" | "authorize" | "pair_cancel" | "revoke") {
            if matches!(request.op.as_str(), "pair" | "authorize") && state.lease.as_ref()
                .map_or(false, |lease| !lease.explicitly_stopped || lease.conn != conn) {
                return Some(error(&request.operation_id, "STOP_ADB_VIDEO_BEFORE_PAIRING"));
            }
            state.pairing_connections.insert(conn);
            // No video generation/epoch, decoder barrier or normal-capture change.
            request.generation = 0; request.epoch = 0; request.revision = 0;
            request.sequence = 0; request.input_frozen = false;
            drop(state);
            // MainService may have been recreated while the authenticated Rust
            // connection stayed alive. Its old CM add_connection notification is
            // not replayed on recreation. Refresh eligibility only after the
            // current secure/remote/keyboard checks above, never from a peer field.
            if matches!(request.op.as_str(), "pair" | "authorize")
                && call_main_service_set_by_name("adb_control_authorized", Some(&conn.to_string()), Some("")).is_err() {
                return Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE"));
            }
            if !forward(conn, &request) {
                return Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE"));
            }
            if request.op == "revoke" { rollback(conn, &request.operation_id); }
            return None;
        }
        if request.op == "status" {
            if let Some(lease) = state.lease.as_ref().filter(|lease| lease.conn == conn
                && matches!(lease.phase, Phase::Rollback | Phase::Frozen)) {
                let status = lease_status(lease);
                drop(state);
                // Keep Kotlin's real consent/permission snapshot flowing even
                // when the Rust video transaction is waiting or frozen.
                if !forward(conn, &request) { return Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE")); }
                return Some(status);
            }
        }
        // Kotlin checks session consent/scopes for these operations independently
        // of the video transaction. Accessibility-only actions need no ADB consent.
        if matches!(request.op.as_str(), "side_action" | "accessibility_action" |
            "accessibility_pause" | "accessibility_resume" | "accessibility_disable" | "accessibility_enable") {
            drop(state);
            return if forward(conn, &request) { None }
                else { Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE")) };
        }
        if request.op == "stop" && state.lease.is_none() {
            drop(state);
            if !forward(conn, &request) { return Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE")); }
            return Some(message(json!({"v":1,"phase":"NORMAL","operationId":request.operation_id,
                "epoch":request.epoch,"generation":request.generation,"videoStopped":true}).to_string()));
        }
        if request.op == "start" {
            if !access.video_subscribed {
                return Some(error(&request.operation_id, "ADB_VIDEO_SUBSCRIPTION_REQUIRED"));
            }
            if !h264 { return Some(error(&request.operation_id, "H264_DECODER_REQUIRED")); }
            if hbb_common::config::Config::get_bool_option("allow-auto-record-incoming") {
                return Some(error(&request.operation_id, "STOP_INCOMING_RECORDING_FIRST"));
            }
            let replace_stopped = state.lease.as_ref().map_or(false, |lease|
                lease.conn == conn && lease.explicitly_stopped
                    && matches!(lease.phase, Phase::Rollback | Phase::Frozen)
                    && request.generation > lease.generation);
            if let Some(lease) = state.lease.as_ref().filter(|_| !replace_stopped) {
                if lease.conn == conn && lease.generation == request.generation
                    && lease.operation_id == request.operation_id {
                    request.op = "status".into();
                    request.epoch = lease.epoch;
                    request.payload = json!({});
                    drop(state);
                    if !forward(conn, &request) { return Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE")); }
                    return None;
                }
                return Some(error(&request.operation_id, "BUSY"));
            }
            if state.subscribers.len() != 1 || !state.subscribers.contains(&conn) {
                return Some(error(&request.operation_id, "MULTI_VIEWER_UNSUPPORTED"));
            }
            if request.generation == 0 { return Some(error(&request.operation_id, "STALE_GENERATION")); }
            if state.next_epoch >= MAX_SAFE_INTEGER - 2 { return Some(error(&request.operation_id, "EPOCH_EXHAUSTED")); }
            // An explicit new start supersedes only this owner's already-stopped
            // video. Never require a MediaProjection first frame for this path.
            if replace_stopped {
                state.lease = None;
                state.barriers.retain(|(id, _)| *id != conn);
                state.normal_refresh.remove(&conn);
                encoded::end();
            }
            state.next_epoch = state.next_epoch.saturating_add(1);
            request.epoch = state.next_epoch;
            state.lease = Some(Lease { conn, epoch: request.epoch, generation: request.generation,
                operation_id: request.operation_id.clone(), phase: Phase::Preparing,
                revision: 0, sequence: 0, input_sequence: 0, client_input_sequence: 0, width: 0, height: 0,
                deadline: now + Duration::from_secs(60), heartbeat: now,
                normal_ready: !replace_stopped, rollback_failed: false, touch_down: false, mouse_position: None,
                barrier_sent: false, present_min_sequence: 0, failure_code: String::new(), explicitly_stopped: false });
            encoded::begin(request.epoch);
        } else if request.op != "status" {
            let lease = match state.lease.as_mut() { Some(lease) if lease.conn == conn => lease,
                _ => return Some(error(&request.operation_id, "NOT_OWNER")) };
            if request.epoch != lease.epoch || request.generation != lease.generation {
                return Some(error(&request.operation_id, "STALE_EPOCH"));
            }
            if request.op == "video_failed" {
                drop(state);
                freeze(conn, "ADB_VIDEO_DECODE_FAILED");
                return None;
            }
            if lease.phase == Phase::Frozen && request.op != "stop" {
                return if request.op == "heartbeat" { None }
                    else { Some(error(&request.operation_id, "ADB_VIDEO_FROZEN")) };
            }
            if request.op == "activate" || request.op == "presented" {
                if request.operation_id != lease.operation_id || request.revision != lease.revision
                    || request.sequence == 0 || request.sequence > lease.sequence || !request.input_frozen {
                    return Some(error(&request.operation_id, "INVALID_FRAME_ACK"));
                }
                if request.op == "presented" && (!lease.barrier_sent || request.sequence < lease.present_min_sequence) {
                    return Some(error(&request.operation_id, "BARRIER_NOT_SENT"));
                }
                if lease.phase == Phase::Active {
                    request.op = "status".into();
                    drop(state);
                    if !forward(conn, &request) { return Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE")); }
                    return None;
                }
                if lease.phase != Phase::Rollback && Instant::now() > lease.deadline {
                    return Some(error(&request.operation_id, "ACK_EXPIRED"));
                }
                if request.op == "activate" {
                    if matches!(lease.phase, Phase::Activating | Phase::AwaitingPresentation | Phase::Presented) { return None; }
                    if lease.phase != Phase::Preparing { return Some(error(&request.operation_id, "INVALID_PHASE")); }
                    lease.phase = Phase::Activating;
                    lease.present_min_sequence = request.sequence;
                    lease.deadline = now + Duration::from_secs(8);
                    // Pin the barrier to the frame actually acknowledged by the decoder.
                    // Runtime must confirm its input freeze before this barrier is emitted.
                    let mut ack = metadata(lease);
                    ack.sequence = request.sequence;
                    let mut misc = Misc::new();
                    misc.set_android_video_barrier(AndroidVideoBarrier { metadata: Some(ack).into(),
                        action: 1, ..Default::default() });
                    let mut message = Message::new();
                    message.set_misc(misc);
                    state.barriers.push_back((conn, message));
                } else if lease.phase == Phase::Rollback {
                    state.lease = None;
                    encoded::end();
                    return Some(message(json!({"v":1,"phase":"NORMAL","operationId":request.operation_id,
                        "epoch":request.epoch,"generation":request.generation}).to_string()));
                } else if lease.phase != Phase::AwaitingPresentation {
                    if lease.phase == Phase::Presented { return None; }
                    return Some(error(&request.operation_id, "INVALID_PHASE"));
                } else {
                    lease.phase = Phase::Presented;
                }
            } else if request.op == "input" {
                if lease.phase != Phase::Active || request.sequence <= lease.client_input_sequence
                    || request.revision != lease.revision {
                    return Some(error(&request.operation_id, "INPUT_FROZEN_OR_STALE"));
                }
                if request.payload.get("type").and_then(Value::as_str) == Some("touch")
                    && (request.payload.get("width").and_then(Value::as_u64) != Some(lease.width as u64)
                        || request.payload.get("height").and_then(Value::as_u64) != Some(lease.height as u64)) {
                    return Some(error(&request.operation_id, "STALE_GEOMETRY"));
                }
                lease.client_input_sequence = request.sequence;
                lease.input_sequence = lease.input_sequence.saturating_add(1);
                request.sequence = lease.input_sequence;
            } else if request.op == "heartbeat" {
                lease.heartbeat = now;
                if lease.phase == Phase::Rollback { return None; }
            } else if request.op == "keyframe" && lease.phase == Phase::Rollback {
                state.normal_refresh.insert(conn);
                return None;
            } else if request.op != "stop" && request.op != "keyframe" && lease.phase != Phase::Active {
                return Some(error(&request.operation_id, "TRANSITION_IN_PROGRESS"));
            }
        }
        drop(state);
        if !forward(conn, &request) {
            freeze(conn, "ANDROID_SERVICE_UNAVAILABLE");
            return Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE"));
        }
        if request.op == "stop" { rollback(conn, &request.operation_id); }
        None
    }

    fn lease_status_value(lease: &Lease) -> Value {
        json!({"v":1,"phase":if lease.phase == Phase::Frozen { "FROZEN" } else { "ROLLING_BACK" },
            "operationId":lease.operation_id,"epoch":lease.epoch,"generation":lease.generation,
            "revision":lease.revision,"sequence":lease.sequence,"inputReady":false,
            "code":lease.failure_code,"videoStopped":lease.explicitly_stopped})
    }

    fn lease_status(lease: &Lease) -> Message {
        message(lease_status_value(lease).to_string())
    }

    pub fn legacy_side_allowed(conn: i32) -> bool {
        STATE.lock().unwrap().lease.as_ref().map_or(true, |lease| lease.conn == conn)
    }

    /// Unexpected failures release the helper but never select normal capture.
    /// Only an explicit stop can release the retained frozen source transaction.
    fn freeze(conn: i32, code: &str) {
        let mut state = STATE.lock().unwrap();
        let Some(lease) = state.lease.as_mut().filter(|lease| lease.conn == conn) else { return; };
        if lease.phase == Phase::Frozen { return; }
        lease.phase = Phase::Frozen;
        lease.normal_ready = false;
        lease.rollback_failed = true;
        lease.touch_down = false;
        lease.mouse_position = None;
        lease.failure_code = code.to_owned();
        let freeze_barrier = barrier(lease, 4);
        let status = lease_status(lease);
        state.barriers.retain(|(id, _)| *id != conn);
        state.barriers.push_back((conn, freeze_barrier));
        state.barriers.push_back((conn, status));
        state.normal_refresh.remove(&conn);
        encoded::end();
        drop(state);
        let _ = call_main_service_set_by_name("adb_control_abort_video", Some(&conn.to_string()), Some(code));
    }

    fn rollback(conn: i32, operation_id: &str) {
        let mut state = STATE.lock().unwrap();
        if state.lease.as_ref().map_or(true, |lease| lease.conn != conn) { return; }
        state.lease.as_mut().unwrap().explicitly_stopped = true;
        if state.lease.as_ref().map_or(false, |lease| lease.phase == Phase::Rollback) { return; }
        if state.lease.as_ref().map_or(false, |lease| lease.normal_ready && lease.phase != Phase::Rollback) {
            let mut lease = state.lease.take().unwrap();
            lease.operation_id = operation_id.to_owned();
            state.barriers.clear();
            state.barriers.push_back((conn, barrier(&lease, 3)));
            encoded::end();
            return;
        }
        state.next_epoch = state.next_epoch.saturating_add(1);
        let epoch = state.next_epoch;
        let lease = state.lease.as_mut().unwrap();
        lease.epoch = epoch;
        lease.operation_id = operation_id.to_owned();
        lease.phase = Phase::Rollback;
        lease.revision = 1;
        lease.sequence = 0;
        lease.deadline = Instant::now() + Duration::from_secs(10);
        lease.normal_ready = false;
        lease.rollback_failed = false;
        lease.failure_code.clear();
        lease.touch_down = false;
        lease.mouse_position = None;
        lease.barrier_sent = false;
        lease.present_min_sequence = 1;
        let message = barrier(lease, 2);
        let status = lease_status(lease);
        state.barriers.clear();
        state.barriers.push_back((conn, message));
        state.barriers.push_back((conn, status));
        encoded::end();
    }

    pub fn revoke(conn: i32) {
        let mut state = STATE.lock().unwrap();
        if state.lease.as_ref().map_or(false, |lease| lease.conn == conn) {
            state.lease = None;
            state.barriers.retain(|(id, _)| *id != conn);
            encoded::end();
        }
        state.normal_refresh.remove(&conn);
        state.rates.remove(&conn);
        state.pairing_connections.remove(&conn);
        drop(state);
        // Also remove connection eligibility when a connection never acquired a lease.
        let _ = call_main_service_set_by_name("adb_control_disconnect", Some(&conn.to_string()), Some(""));
        encoded::forget_state(conn);
    }

    pub fn poll(conn: i32, access: Access) -> Option<Message> {
        let owner = STATE.lock().unwrap().lease.as_ref().map_or(false, |lease| lease.conn == conn);
        if owner && encoded::take_state_overflow() {
            // A lost RECONFIGURE/input-freeze transition cannot be repaired by
            // continuing the current source. Stop the helper and return safely.
            permission_revoked(conn);
            return Some(error("state-overflow", "ANDROID_STATE_BACKPRESSURE"));
        }
        // Permission loss revokes consent immediately. The video remains frozen;
        // it does not silently select ordinary capture.
        if access.control_error().is_some() {
            let pairing_authority = STATE.lock().unwrap().pairing_connections.remove(&conn);
            if pairing_authority {
                let _ = call_main_service_set_by_name("adb_control_revoke", Some(&conn.to_string()), Some(""));
            }
        }
        if !access.video_allowed() {
            let owner = STATE.lock().unwrap().lease.as_ref().map_or(false, |l|
                l.conn == conn && !matches!(l.phase, Phase::Rollback | Phase::Frozen));
            if owner {
                let _ = call_main_service_set_by_name("adb_control_revoke", Some(&conn.to_string()), Some(""));
                freeze(conn, "ADB_CONTROL_PERMISSION_REQUIRED");
            }
        }
        let timeout = {
            let mut state = STATE.lock().unwrap();
            state.lease.as_mut().filter(|lease| lease.conn == conn).and_then(|lease| {
                // After an explicit stop there is no running ADB helper lease to
                // time out. Keep accepting future normal frames indefinitely:
                // the phone may grant/start MediaProjection later. Disconnect
                // still cleans this state; a newer explicit start may replace it.
                let awaiting_normal = lease.explicitly_stopped && lease.phase == Phase::Rollback;
                if !awaiting_normal && lease.phase != Phase::Frozen && !lease.rollback_failed
                    && (lease.heartbeat.elapsed() > Duration::from_secs(15)
                    || (lease.phase != Phase::Active && Instant::now() > lease.deadline)) {
                    lease.rollback_failed = true;
                    Some((lease.operation_id.clone(), lease.phase == Phase::Rollback))
                } else { None }
            })
        };
        if let Some((operation_id, _already_rollback)) = timeout {
            freeze(conn, "LEASE_OR_TRANSITION_TIMEOUT");
            return Some(error(&operation_id, "LEASE_OR_TRANSITION_TIMEOUT"));
        }
        if let Some(raw) = encoded::pop_state(conn) {
            if let Ok(mut value) = serde_json::from_str::<Value>(&raw) {
                // Runtime snapshots are global; never show another connection's
                // consent as a grant for the recipient of this status message.
                if let (Some(owner), Some(active)) = (value.get("consentConnId").and_then(Value::as_i64),
                    value.get("consentActive").and_then(Value::as_bool)) {
                    value["consentActive"] = json!(active && owner == i64::from(conn));
                }
                if matches!(value.get("kind").and_then(Value::as_str), Some("pairing" | "action")) {
                    return if access.control_error().is_none() { Some(message(value.to_string())) } else { None };
                }
                let mut state = STATE.lock().unwrap();
                if let Some(lease) = state.lease.as_ref().filter(|lease| lease.conn == conn
                    && matches!(lease.phase, Phase::Frozen | Phase::Rollback)) {
                    // Preserve runtime consent and capability fields, but the
                    // stopped/frozen wire transaction owns video identity/state.
                    if let Some(fields) = value.as_object_mut() {
                        fields.extend(lease_status_value(lease).as_object().unwrap().clone());
                    }
                    return Some(message(value.to_string()));
                }
                let mut should_rollback = false;
                let mut reconfigure = None;
                if let Some(lease) = state.lease.as_mut().filter(|lease| lease.conn == conn
                    && !matches!(lease.phase, Phase::Frozen | Phase::Rollback)) {
                    if value.get("operationRejected").and_then(Value::as_bool) == Some(true)
                        && value.get("requestOperationId").and_then(Value::as_str) == Some(lease.operation_id.as_str())
                        && value.get("requestEpoch").and_then(Value::as_u64) == Some(lease.epoch)
                        && value.get("requestGeneration").and_then(Value::as_u64) == Some(lease.generation)
                        && matches!(lease.phase, Phase::Preparing | Phase::Activating | Phase::Presented) {
                        should_rollback = true;
                    }
                    if value.get("epoch").and_then(Value::as_u64) == Some(lease.epoch)
                        && value.get("generation").and_then(Value::as_u64) == Some(lease.generation) {
                        match value.get("phase").and_then(Value::as_str) {
                            Some("COMMITTED") if lease.phase == Phase::Presented => lease.phase = Phase::Active,
                            Some("WAITING_PRESENTED") if lease.phase == Phase::Activating => lease.phase = Phase::AwaitingPresentation,
                            Some("RECONFIGURE") if lease.phase != Phase::Rollback => {
                                reconfigure = Some((lease.generation, lease.operation_id.clone()));
                            }
                            Some("ERROR" | "REVOKED" | "STOPPED" | "FAILED" | "STOPPING" | "IDLE")
                                if lease.phase != Phase::Rollback => should_rollback = true,
                            _ => {}
                        }
                    }
                }
                if let Some((generation, operation_id)) = reconfigure {
                    state.next_epoch = state.next_epoch.saturating_add(1);
                    let epoch = state.next_epoch;
                    let lease = state.lease.as_mut().unwrap();
                    lease.epoch = epoch;
                    lease.phase = Phase::Preparing;
                    lease.revision = 0;
                    lease.sequence = 0;
                    lease.normal_ready = false;
                    lease.touch_down = false;
                    lease.mouse_position = None;
                    lease.barrier_sent = false;
                    lease.present_min_sequence = 0;
                    lease.deadline = Instant::now() + Duration::from_secs(60);
                    state.barriers.clear();
                    encoded::begin(epoch);
                    drop(state);
                    // Private in-process operation: deliberately absent from parse().
                    let request = Request { v: 1, op: "reconfigure".into(), operation_id,
                        generation, epoch, revision: 0, sequence: 0, input_frozen: true, payload: json!({}) };
                    if !forward(conn, &request) { freeze(conn, "ANDROID_SERVICE_UNAVAILABLE"); }
                    return Some(message(json!({"v":1,"phase":"PREPARING","epoch":epoch,
                        "generation":generation,"operationId":request.operation_id,"inputReady":false}).to_string()));
                }
                drop(state);
                if should_rollback {
                    freeze(conn, value.get("code").and_then(Value::as_str)
                        .filter(|code| !code.is_empty()).unwrap_or("ADB_VIDEO_FAILED"));
                }
                return Some(message(value.to_string()));
            }
        }
        let mut state = STATE.lock().unwrap();
        if let Some(index) = state.barriers.iter().position(|(id, _)| *id == conn) {
            if let Some(lease) = state.lease.as_mut().filter(|lease| lease.conn == conn) {
                if lease.phase == Phase::Activating { return None; }
                if lease.phase == Phase::Rollback { lease.normal_ready = true; }
                else { lease.normal_ready = false; }
                lease.barrier_sent = true;
            }
            return state.barriers.remove(index).map(|(_, message)| message);
        }
        let lease = state.lease.as_mut().filter(|lease| lease.conn == conn)?;
        if matches!(lease.phase, Phase::Rollback | Phase::Activating | Phase::Frozen) { return None; }
        let frame = encoded::pop(lease.epoch)?;
        lease.revision = frame.revision;
        lease.sequence = frame.sequence;
        lease.width = frame.width;
        lease.height = frame.height;
        let mut video = VideoFrame::new();
        video.display = 0;
        video.android_video = Some(metadata(lease)).into();
        video.set_h264s(EncodedVideoFrames { frames: vec![EncodedVideoFrame {
            data: frame.data.into(), key: frame.key, pts: frame.pts_us / 1000, ..Default::default()
        }], ..Default::default() });
        let mut message = Message::new();
        message.set_video_frame(video);
        Some(message)
    }
}

#[cfg(target_os = "android")]
pub use endpoint::*;

#[cfg(test)]
mod tests {
    use super::*;

    fn controller_access() -> Access {
        Access { authorized: true, secured: true, remote: true, closed: false,
            keyboard: true, disable_keyboard: false, video_subscribed: true }
    }

    #[test]
    fn pairing_does_not_depend_on_video_subscription() {
        let mut access = controller_access();
        assert!(access.video_allowed());
        access.video_subscribed = false;
        assert_eq!(access.control_error(), None);
        assert_eq!(access.view_error(), None);
        assert!(!access.video_allowed());
    }

    #[test]
    fn control_denials_are_precise_and_video_cannot_grant_authority() {
        for (access, expected) in [
            (Access { authorized: false, ..controller_access() }, "ADB_SESSION_NOT_AUTHORIZED"),
            (Access { secured: false, ..controller_access() }, "ADB_SESSION_NOT_ENCRYPTED"),
            (Access { remote: false, ..controller_access() }, "ADB_REMOTE_SESSION_REQUIRED"),
            (Access { closed: true, ..controller_access() }, "ADB_SESSION_CLOSED"),
            (Access { keyboard: false, ..controller_access() }, "ADB_CONTROL_PERMISSION_REQUIRED"),
            (Access { disable_keyboard: true, ..controller_access() }, "ADB_VIEW_ONLY_SESSION"),
        ] {
            assert_eq!(access.control_error(), Some(expected));
            assert!(!access.video_allowed());
        }
        // Status/rollback ACK may restore normal video after control revocation.
        let revoked = Access { keyboard: false, ..controller_access() };
        assert_eq!(revoked.view_error(), None);
        assert!(revoked.control_error().is_some());
    }

    #[test]
    fn wire_operations_are_bounded_and_closed() {
        assert!(parse(r#"{"v":1,"op":"start","operationId":"a-1","generation":1,"payload":{}}"#).is_some());
        for forbidden in ["shell", "reconfigure", "exec", "adb"] {
            assert!(parse(&json!({"v":1,"op":forbidden,"operationId":"a","payload":{}}).to_string()).is_none());
        }
        assert!(parse(r#"{"v":1,"op":"start","operationId":"a","payload":{"serial":"localhost"}}"#).is_none());
        assert!(parse(r#"{"v":1,"op":"stop","operationId":"a","epoch":-1,"payload":{}}"#).is_none());
        assert!(parse(&"x".repeat(MAX_JSON + 1)).is_none());
    }

    #[test]
    fn input_requires_finite_normalized_coordinates_and_exact_shape() {
        let mut value = json!({"v":1,"op":"input","operationId":"i","payload":{
            "type":"touch","action":"down","x":0.25,"y":1.0,"width":1280,"height":720}});
        assert!(parse(&value.to_string()).is_some());
        value["payload"]["x"] = json!(-0.001);
        assert!(parse(&value.to_string()).is_none());
        value["payload"]["x"] = json!(0.5);
        value["payload"]["command"] = json!("ignored?");
        assert!(parse(&value.to_string()).is_none());
        value["payload"] = json!({"action":"open_url","url":"file:///private"});
        value["op"] = json!("side_action");
        assert!(parse(&value.to_string()).is_none());
    }

    #[test]
    fn pairing_is_local_typed_and_independent_of_video_epoch() {
        let mut value = json!({"v":1,"op":"pair","operationId":"pair-1",
            "payload":{"port":"37123","code":"012345","connectPort":"38234"}});
        assert!(parse(&value.to_string()).is_some());
        value["payload"]["host"] = json!("example.org");
        assert!(parse(&value.to_string()).is_none());
        value["payload"].as_object_mut().unwrap().remove("host");
        for port in ["0", "65536", "localhost:37123", "-1", "auto", ""] {
            value["payload"]["port"] = json!(port);
            assert!(parse(&value.to_string()).is_none());
        }
        value["payload"]["port"] = json!("37123");
        for code in ["12345", "1234567", "123 45", "１２３４５６", "12345\n"] {
            value["payload"]["code"] = json!(code);
            assert!(parse(&value.to_string()).is_none());
        }
        value["op"] = json!("authorize"); value["payload"] = json!({});
        assert!(parse(&value.to_string()).is_some());
        value["payload"] = json!({"connectPort":"65535"});
        assert!(parse(&value.to_string()).is_some());
        value["op"] = json!("pair_cancel"); value["payload"] = json!({"pairOperationId":"pair-1"});
        assert!(parse(&value.to_string()).is_some());
        value["payload"]["pairOperationId"] = json!("bad/operation");
        assert!(parse(&value.to_string()).is_none());
        value["op"] = json!("revoke"); value["payload"] = json!({});
        assert!(parse(&value.to_string()).is_some());
        value["payload"]["connId"] = json!(17);
        assert!(parse(&value.to_string()).is_none());
    }

    #[test]
    fn pairing_wire_roundtrip_preserves_distinct_ports_and_leading_zero_code() {
        let raw = r#"{"v":1,"op":"pair","operationId":"pair-roundtrip","payload":{"port":"37123","code":"012345","connectPort":"38234"}}"#;
        let request = parse(raw).unwrap();
        let serialized = serde_json::to_string(&request).unwrap();
        let roundtrip = parse(&serialized).unwrap();
        assert_eq!(roundtrip.payload["port"].as_str(), Some("37123"));
        assert_eq!(roundtrip.payload["connectPort"].as_str(), Some("38234"));
        assert_eq!(roundtrip.payload["code"].as_str(), Some("012345"));
        let mut value: Value = serde_json::from_str(raw).unwrap();
        value["payload"]["connectPort"] = json!("");
        assert!(parse(&value.to_string()).is_none());
        value["payload"].as_object_mut().unwrap().remove("connectPort");
        assert!(parse(&value.to_string()).is_some());
    }

    #[test]
    fn source_selection_and_accessibility_actions_are_explicit_and_bounded() {
        for source in ["IGNORE_CAPTURE", "HIERARCHY_CAPTURE"] {
            assert!(parse(&json!({"v":1,"op":"start","operationId":"s",
                "payload":{"initialSource":source}}).to_string()).is_some());
        }
        assert!(parse(r#"{"v":1,"op":"start","operationId":"s","payload":{"initialSource":"AUTO"}}"#).is_none());
        for action in ["back", "home", "recents"] {
            assert!(parse(&json!({"v":1,"op":"accessibility_action","operationId":"a",
                "payload":{"action":action}}).to_string()).is_some());
        }
        assert!(parse(r#"{"v":1,"op":"accessibility_action","operationId":"a","payload":{"action":"shell"}}"#).is_none());
        assert!(parse(r#"{"v":1,"op":"video_failed","operationId":"f","payload":{}}"#).is_some());
    }

    #[test]
    fn legacy_side_exemption_never_accepts_pointer_or_arbitrary_url() {
        use hbb_common::message_proto::MouseEvent;
        let mut mouse = MouseEvent { mask: 41, url: "Benchmarks_Management0|0|1".into(), ..Default::default() };
        assert!(is_legacy_side_action(&mouse));
        mouse.mask = 9;
        assert!(!is_legacy_side_action(&mouse));
        mouse.mask = 41;
        mouse.url = "https://example.org".into();
        assert!(!is_legacy_side_action(&mouse));
    }

    #[test]
    fn rejected_pairing_errors_correlate_without_echoing_the_secret() {
        let raw = r#"{"v":1,"op":"pair","operationId":"pair-1","payload":{"port":"0","code":"012345"}}"#;
        let reply: Value = serde_json::from_str(&request_error(raw, "MALFORMED")).unwrap();
        assert_eq!(reply["operationId"], "pair-1");
        assert_eq!(reply["code"], "MALFORMED");
        assert!(reply.get("payload").is_none());
        assert!(!reply.to_string().contains("012345"));
    }
}

/// Fixed accessibility-side controls may coexist with ADB-owned pointer input.
/// Never interpret an arbitrary mouse mask or URL as an exemption.
pub fn is_legacy_side_action(mouse: &hbb_common::message_proto::MouseEvent) -> bool {
    if mouse.url.len() > 2048 || mouse.url.chars().any(|c| c.is_control()) { return false; }
    let prefix = match mouse.mask {
        37 => "Clipboard_Management|",
        39 => "HardwareKeyboard_Management|",
        40 => "SUPPORTED_ABIS_Management",
        41 => "Benchmarks_Management",
        43 => "TouchBlock_Management",
        _ => return false,
    };
    mouse.url.starts_with(prefix)
}

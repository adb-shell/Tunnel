//! Explicit Android ADB operations. The peer never supplies a shell command.
use hbb_common::message_proto::{AndroidControl, Message, Misc};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

pub const OPTION: &str = "android-control";
pub const MAX_JSON: usize = 4096;
const MAX_SAFE_INTEGER: u64 = 9_007_199_254_740_991;

/// Snapshot of trusted connection state. Pairing is a control operation, not a
/// video subscription: a phone without a running capture must still be pairable.
/// ADB follows remote login/control authorization, independently of transport
/// encryption. Do not infer authorization from a connected socket alone.
#[derive(Clone, Copy)]
pub struct Access {
    pub authorized: bool,
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
            if payload.len() > 1 || !payload.keys().all(|key| key == "initialSource" || key == "sourceAction") { return None; }
            if let Some(source) = payload.get("initialSource") {
                if !["ADB_CAPTURE", "IGNORE_CAPTURE", "HIERARCHY_CAPTURE"].contains(&source.as_str()?) { return None; }
            }
            if let Some(action) = payload.get("sourceAction") {
                if !["ignore_on", "ignore_off", "hierarchy_on", "hierarchy_off", "live_off", "resume"].contains(&action.as_str()?) { return None; }
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
                "share_start", "share_stop",
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
    use scrap::android::{encoded, call_main_service_set_by_name, call_main_service_get_by_name};

    #[derive(Clone, Copy, PartialEq)]
    struct InputGeometry { width: u32, height: u32, revision: u64 }

    #[derive(Default)]
    struct GlobalInput {
        position: Option<(i32, i32)>, geometry: Option<InputGeometry>, sequence: u64,
        // None: hover; Some(false): accessibility owns this gesture; Some(true): ADB.
        gesture: Option<bool>, cancelled: bool, keys: HashMap<u32, bool>,
    }

    fn input_geometry(conn: i32, allowed: bool) -> Option<InputGeometry> {
        if !allowed { return None; }
        let raw = call_main_service_get_by_name(&format!("adb_input_status:{conn}")).ok()?;
        if raw.len() > MAX_JSON { return None; }
        let value: Value = serde_json::from_str(&raw).ok()?;
        if value.get("ready")?.as_bool()? != true { return None; }
        let width = value.get("width")?.as_u64()?;
        let height = value.get("height")?.as_u64()?;
        let revision = value.get("revision")?.as_u64()?;
        if width > 4096 || height > 4096 || revision == 0 || revision > MAX_SAFE_INTEGER { return None; }
        Some(InputGeometry { width: width as u32, height: height as u32, revision })
    }

    fn global_packet(input: &mut GlobalInput, geometry: InputGeometry, payload: Value) -> Value {
        input.sequence = input.sequence.saturating_add(1);
        json!({"v":1,"op":"input","operationId":format!("global-{}", input.sequence),
            "sequence":input.sequence,"geometryRevision":geometry.revision,"payload":payload})
    }

    fn global_forward(conn: i32, packet: &Value) -> bool {
        call_main_service_set_by_name("adb_control_global_input", Some(&conn.to_string()),
            Some(&packet.to_string())).is_ok()
    }

    #[derive(Clone, Copy, Debug, PartialEq)]
    enum Phase { Preparing, AwaitingPresentation, Active, Rollback }

    #[derive(Clone)]
    struct Lease {
        conn: i32, epoch: u64, capture_epoch: u64, generation: u64, operation_id: String, phase: Phase,
        revision: u64, sequence: u64, width: u32, height: u32,
        normal_ready: bool, barrier_sent: bool, present_min_sequence: u64,
        needs_key: bool, refresh_at: Instant, status_at: Instant,
    }

    #[derive(Default)]
    struct State {
        subscribers: HashSet<i32>,
        // One phone capture, independent presentation/egress for each controller.
        lease: Option<Lease>, viewers: HashMap<i32, Lease>, next_epoch: u64,
        barriers: VecDeque<(i32, Message)>, normal_refresh: HashSet<i32>,
        rates: HashMap<i32, (Instant, u32)>, inputs: HashMap<i32, GlobalInput>,
        generations: HashMap<i32, u64>, eligible: HashSet<i32>, revoked: HashSet<i32>,
        runtime: Value, runtime_at: Option<Instant>,
    }
    lazy_static::lazy_static! {
        static ref STATE: Mutex<State> = Mutex::new(State::default());
        // Serialize source mutations and local reconfiguration without holding
        // STATE across JNI callbacks. Per-PC frame reads do not own capture.
        static ref COMMANDS: Mutex<()> = Mutex::new(());
    }

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
        if sub { state.subscribers.insert(conn); }
        else {
            state.subscribers.remove(&conn); state.viewers.remove(&conn);
            state.barriers.retain(|(id, _)| *id != conn); state.normal_refresh.remove(&conn);
        }
        true
    }
    pub fn blocks_legacy_input(_conn: i32) -> bool { false }
    pub fn leased() -> bool { STATE.lock().unwrap().lease.as_ref().map_or(false, |l| l.phase != Phase::Rollback) }
    pub fn capture_permitted(conn: i32) -> bool { STATE.lock().unwrap().subscribers.contains(&conn) }
    pub fn take_normal_refresh(conn: i32) -> bool { STATE.lock().unwrap().normal_refresh.remove(&conn) }

    fn signal(capture: &Lease, op: &str) -> Request {
        Request { v: 1, op: op.into(), operation_id: capture.operation_id.clone(),
            generation: capture.generation, epoch: capture.epoch, revision: capture.revision,
            sequence: 0, input_frozen: true, payload: json!({}) }
    }
    pub fn refresh_current_source(_conn: i32, allowed: bool) -> bool {
        if !allowed { return false; }
        let capture = STATE.lock().unwrap().lease.clone();
        let Some(capture) = capture.filter(|l| l.phase != Phase::Rollback) else { return false; };
        forward(capture.conn, &signal(&capture, "keyframe"));
        true
    }
    pub fn permission_revoked(conn: i32) {
        let mut state = STATE.lock().unwrap();
        state.eligible.remove(&conn);
        detach_viewer(&mut state, conn);
        drop(state);
        let _ = call_main_service_set_by_name("adb_control_revoke", Some(&conn.to_string()), Some(""));
    }
    pub fn decoder_changed(conn: i32, h264: bool) {
        if !h264 { detach_viewer(&mut STATE.lock().unwrap(), conn); }
    }

    /// Input ownership follows the verified control helper, independently of video.
    /// A gesture stays with its original backend through UP (or cancellation).
    pub fn route_mouse(conn: i32, mouse: &hbb_common::message_proto::MouseEvent, allowed: bool) -> bool {
        let status = input_geometry(conn, allowed);
        let geometry = status.filter(|size| size.width >= 2 && size.height >= 2);
        let kind = mouse.mask & 7;
        let button = mouse.mask >> 3;
        let action = match (kind, button) {
            (0, 0 | 1) => "move", (1, 1) => "down", (2, 1) => "up", _ => return false,
        };
        if !mouse.url.is_empty() { return false; }
        let mut state = STATE.lock().unwrap();
        let input = state.inputs.entry(conn).or_default();
        if kind == 0 { input.position = Some((mouse.x, mouse.y)); }
        if kind == 1 && input.gesture.is_none() {
            input.gesture = Some(status.is_some());
            input.geometry = geometry;
            input.cancelled = false;
        } else if kind == 1 && input.gesture == Some(true) {
            return true;
        }
        if input.gesture == Some(false) {
            if kind == 2 { input.gesture = None; }
            return false;
        }
        if input.gesture.is_none() { return status.is_some(); }
        if input.cancelled {
            if kind == 2 { input.gesture = None; input.cancelled = false; }
            return true;
        }
        let Some(original) = input.geometry else {
            if kind == 2 { input.gesture = None; }
            return true;
        };
        let (x, y) = input.position.unwrap_or((-1, -1));
        let invalid = geometry != Some(original) || x < 0 || y < 0
            || x >= original.width as i32 || y >= original.height as i32;
        let packet = global_packet(input, original, json!({"type":"touch",
            "action":if invalid { "cancel" } else { action },
            "x":x.clamp(0, original.width as i32 - 1) as f64 / (original.width - 1) as f64,
            "y":y.clamp(0, original.height as i32 - 1) as f64 / (original.height - 1) as f64,
            "width":original.width,"height":original.height}));
        if invalid { input.cancelled = true; }
        if kind == 2 { input.gesture = None; input.cancelled = false; }
        drop(state);
        if !global_forward(conn, &packet) && kind != 2 {
            if let Some(input) = STATE.lock().unwrap().inputs.get_mut(&conn) { input.cancelled = true; }
        }
        true
    }

    pub fn route_key(conn: i32, key: &hbb_common::message_proto::KeyEvent, allowed: bool) -> bool {
        use hbb_common::message_proto::{key_event, ControlKey as C, KeyboardMode};
        let geometry = input_geometry(conn, allowed);
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
                _ => return geometry.is_some(),
            },
            Some(key_event::Union::Unicode(code) | key_event::Union::Chr(code)) => match *code {
                97..=122 => code - 97 + 29,
                65..=90 => { meta |= 1; code - 65 + 29 },
                48..=57 => code - 48 + 7,
                32 => 62, 10 | 13 => 66, _ => return geometry.is_some(),
            },
            _ => return geometry.is_some(),
        };
        // Power/sleep/wakeup require the separately scoped display operations.
        if code == 0 || code > 288 || [26, 223, 224, 276].contains(&code) { return geometry.is_some(); }
        let mut state = STATE.lock().unwrap();
        let input = state.inputs.entry(conn).or_default();
        let adb = if key.press { geometry.is_some() } else if key.down {
            *input.keys.entry(code).or_insert(geometry.is_some())
        } else { input.keys.remove(&code).unwrap_or(geometry.is_some()) };
        if !adb { return false; }
        // Never deliver an ADB key-up to accessibility if the helper disappeared.
        let Some(geometry) = geometry else { return true; };
        let mut requests = Vec::with_capacity(2);
        for action in if key.press { vec!["down", "up"] } else { vec![if key.down { "down" } else { "up" }] } {
            requests.push(global_packet(input, geometry,
                json!({"type":"key","action":action,"keyCode":code,"metaState":meta})));
        }
        drop(state);
        for request in requests { if !global_forward(conn, &request) { break; } }
        true
    }

    pub fn route_pointer(conn: i32, pointer: &hbb_common::message_proto::PointerDeviceEvent, allowed: bool) -> bool {
        use hbb_common::message_proto::{pointer_device_event, touch_event, MouseEvent};
        let geometry = input_geometry(conn, allowed).filter(|size| size.width >= 2 && size.height >= 2);
        let state = STATE.lock().unwrap();
        let input = state.inputs.get(&conn);
        let Some(pointer_device_event::Union::TouchEvent(touch)) = pointer.union.as_ref() else { return false; };
        let (mask, x, y) = match touch.union.as_ref() {
            Some(touch_event::Union::PanStart(p)) => (9, p.x, p.y),
            Some(touch_event::Union::PanUpdate(p)) => {
                let Some((x, y)) = input.and_then(|input| input.position) else { return geometry.is_some(); };
                let size = geometry.or_else(|| input.and_then(|input| input.geometry));
                (0, x.saturating_add(p.x).clamp(0, size.map_or(i32::MAX, |size| size.width as i32 - 1)),
                    y.saturating_add(p.y).clamp(0, size.map_or(i32::MAX, |size| size.height as i32 - 1)))
            }
            Some(touch_event::Union::PanEnd(_)) => (10, 0, 0),
            _ => return false,
        };
        drop(state);
        if mask == 9 {
            route_mouse(conn, &MouseEvent { mask: 0, x, y, ..Default::default() }, allowed);
        }
        route_mouse(conn, &MouseEvent { mask, x, y, ..Default::default() }, allowed)
    }

    /// Normal frames are gated independently for each decoder, never by the PC
    /// that happened to start the phone's shared source.
    pub fn permit_normal_video(conn: i32) -> bool {
        STATE.lock().unwrap().viewers.get(&conn).map_or(true, |lease|
            (lease.phase == Phase::Preparing || lease.phase == Phase::Rollback) && lease.normal_ready)
    }
    pub fn decorate_normal(conn: i32, message: &Message) -> Option<Message> {
        let mut state = STATE.lock().unwrap();
        let lease = state.viewers.get_mut(&conn)?;
        if lease.phase != Phase::Rollback { return None; }
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
    fn viewer_snapshot(state: &State, conn: i32) -> Value {
        let mut value = state.runtime.clone();
        if !value.is_object() { value = json!({}); }
        value["v"] = json!(1);
        value["sharedDevice"] = json!(true);
        value["operationRejected"] = json!(false);
        value["consentActive"] = json!(state.eligible.contains(&conn) && !state.revoked.contains(&conn)
            && state.runtime["deviceControlEnabled"].as_bool() != Some(false));
        value["consentConnId"] = json!(conn);
        value["consentLifetime"] = json!("device");
        if let Some(lease) = state.viewers.get(&conn) {
            value["phase"] = json!(match lease.phase {
                Phase::Preparing => "PREPARING", Phase::AwaitingPresentation => "WAITING_PRESENTED",
                Phase::Active => "COMMITTED", Phase::Rollback => "ROLLING_BACK",
            });
            value["epoch"] = json!(lease.epoch);
            value["generation"] = json!(lease.generation);
            value["operationId"] = json!(lease.operation_id);
            value["sourceOperationId"] = json!(lease.operation_id);
            value["revision"] = json!(lease.revision);
            value["sequence"] = json!(lease.sequence);
            value["videoStopped"] = json!(lease.phase == Phase::Rollback);
        } else {
            value["phase"] = json!("IDLE");
            value["epoch"] = json!(0);
            value["generation"] = json!(0);
        }
        value
    }
    fn viewer_status(state: &State, conn: i32) -> Message {
        message(viewer_snapshot(state, conn).to_string())
    }
    fn fresh_lease(conn: i32, epoch: u64, generation: u64, operation_id: String) -> Lease {
        Lease { conn, epoch, capture_epoch: epoch, generation, operation_id, phase: Phase::Preparing,
            revision: 0, sequence: 0, width: 0, height: 0, normal_ready: true,
            barrier_sent: false, present_min_sequence: 0, needs_key: true,
            refresh_at: Instant::now() - Duration::from_secs(2),
            status_at: Instant::now() - Duration::from_secs(2) }
    }
    fn install_capture(state: &mut State, capture: Lease) {
        for viewer in state.viewers.values_mut() {
            let normal_ready = viewer.normal_ready;
            *viewer = Lease { conn: viewer.conn, normal_ready, ..capture.clone() };
        }
        state.barriers.clear();
        encoded::begin(capture.epoch);
        state.lease = Some(capture);
    }
    // Used for a single viewer losing its capability. It never stops the phone.
    fn detach_viewer(state: &mut State, conn: i32) {
        state.barriers.retain(|(id, _)| *id != conn);
        if let Some(viewer) = state.viewers.get_mut(&conn) {
            viewer.phase = Phase::Rollback; viewer.sequence = 0;
            viewer.revision = viewer.revision.max(1);
            viewer.normal_ready = false; viewer.barrier_sent = false;
            state.barriers.push_back((conn, barrier(viewer, 2)));
            state.normal_refresh.insert(conn);
        }
    }
    fn stop_capture(state: &mut State, operation: &str) {
        state.next_epoch = state.next_epoch.saturating_add(1);
        let epoch = state.next_epoch;
        if let Some(capture) = state.lease.as_mut() {
            capture.phase = Phase::Rollback; capture.epoch = epoch;
            capture.generation = epoch; capture.operation_id = operation.into();
        }
        state.barriers.clear();
        for (&conn, viewer) in state.viewers.iter_mut() {
            viewer.epoch = epoch; viewer.generation = epoch; viewer.operation_id = operation.into();
            viewer.phase = Phase::Rollback; viewer.revision = 1; viewer.sequence = 0;
            viewer.normal_ready = false; viewer.barrier_sent = false;
            viewer.status_at = Instant::now() - Duration::from_secs(2);
            state.barriers.push_back((conn, barrier(viewer, 2)));
            state.normal_refresh.insert(conn);
        }
        encoded::end();
    }

    pub fn request(conn: i32, raw: &str, access: Access, h264: bool) -> Option<Message> {
        let _commands = COMMANDS.lock().unwrap();
        let mut request = match parse(raw) { Some(r) => r,
            None => return Some(message(super::request_error(raw, "MALFORMED"))) };
        let rollback_ack = request.op == "presented" && STATE.lock().unwrap().viewers.get(&conn)
            .map_or(false, |l| l.phase == Phase::Rollback);
        let denied = if request.op == "status" || rollback_ack { access.view_error() } else { access.control_error() };
        if let Some(code) = denied { return Some(error(&request.operation_id, code)); }
        if access.control_error().is_none() {
            if call_main_service_set_by_name("adb_control_authorized", Some(&conn.to_string()), Some("")).is_err() {
                return Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE"));
            }
            STATE.lock().unwrap().eligible.insert(conn);
        }
        let mut state = STATE.lock().unwrap();
        if !rate_allowed(&mut state, conn) { return Some(error(&request.operation_id, "RATE_LIMIT")); }
        if request.op == "status" {
            let mut response = viewer_snapshot(&state, conn);
            response["requestOperationId"] = json!(request.operation_id);
            drop(state);
            forward(conn, &request); // Runtime starts only control, never capture.
            return Some(message(response.to_string()));
        }
        if matches!(request.op.as_str(), "pair" | "authorize" | "pair_cancel" | "revoke" |
            "side_action" | "accessibility_action" | "accessibility_pause" | "accessibility_resume" |
            "accessibility_disable" | "accessibility_enable") {
            if request.op == "revoke" { state.revoked.insert(conn); detach_viewer(&mut state, conn); }
            if matches!(request.op.as_str(), "pair" | "authorize") { state.revoked.remove(&conn); }
            drop(state);
            return if forward(conn, &request) { None }
                else { Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE")) };
        }
        if state.revoked.contains(&conn) {
            return Some(error(&request.operation_id, "SESSION_ADB_AUTHORIZATION_REQUIRED"));
        }
        if request.op == "start" || request.op == "stop" {
            let previous = state.generations.get(&conn).copied().unwrap_or(0);
            if request.generation == 0 || request.generation <= previous {
                return Some(error(&request.operation_id, "STALE_GENERATION"));
            }
            if request.op == "start" {
                if !access.video_allowed() { return Some(error(&request.operation_id, "ADB_VIDEO_SUBSCRIPTION_REQUIRED")); }
                if !h264 { return Some(error(&request.operation_id, "H264_DECODER_REQUIRED")); }
                if hbb_common::config::Config::get_bool_option("allow-auto-record-incoming") {
                    return Some(error(&request.operation_id, "STOP_INCOMING_RECORDING_FIRST"));
                }
            }
            if state.next_epoch >= MAX_SAFE_INTEGER - 2 { return Some(error(&request.operation_id, "EPOCH_EXHAUSTED")); }
            state.generations.insert(conn, request.generation);
            if request.op == "start" {
                state.next_epoch += 1;
                request.epoch = state.next_epoch; request.generation = state.next_epoch;
                let capture = fresh_lease(conn, request.epoch, request.generation, request.operation_id.clone());
                install_capture(&mut state, capture.clone());
                state.viewers.entry(conn).or_insert(capture);
            } else {
                stop_capture(&mut state, &request.operation_id);
                request.epoch = state.next_epoch; request.generation = state.next_epoch;
                if !state.viewers.contains_key(&conn) {
                    let mut viewer = fresh_lease(conn, request.epoch, request.generation, request.operation_id.clone());
                    viewer.phase = Phase::Rollback; viewer.revision = 1; viewer.normal_ready = false;
                    state.barriers.push_back((conn, barrier(&viewer, 2)));
                    state.viewers.insert(conn, viewer);
                }
            }
            drop(state);
            if !forward(conn, &request) { return Some(error(&request.operation_id, "ANDROID_SERVICE_UNAVAILABLE")); }
            STATE.lock().unwrap().runtime_at = None;
            refresh_runtime();
            return Some(viewer_status(&STATE.lock().unwrap(), conn));
        }
        let capture = state.lease.clone();
        let Some(viewer) = state.viewers.get_mut(&conn) else { return Some(error(&request.operation_id, "NO_VIDEO_SUBSCRIPTION")); };
        if request.epoch != viewer.epoch || request.generation != viewer.generation {
            return Some(error(&request.operation_id, "STALE_EPOCH"));
        }
        match request.op.as_str() {
            "activate" | "presented" => {
                if request.operation_id != viewer.operation_id || request.revision != viewer.revision ||
                    request.sequence == 0 || request.sequence > viewer.sequence || !request.input_frozen {
                    return Some(error(&request.operation_id, "INVALID_FRAME_ACK"));
                }
                if request.op == "activate" {
                    if viewer.phase == Phase::Preparing {
                        viewer.phase = Phase::AwaitingPresentation;
                        viewer.present_min_sequence = request.sequence;
                        let mut acknowledged = viewer.clone(); acknowledged.sequence = request.sequence;
                        state.barriers.push_back((conn, barrier(&acknowledged, 1)));
                    }
                } else {
                    if !viewer.barrier_sent || request.sequence < viewer.present_min_sequence {
                        return Some(error(&request.operation_id, "BARRIER_NOT_SENT"));
                    }
                    if viewer.phase == Phase::Rollback {
                        state.viewers.remove(&conn);
                        drop(state);
                        let _ = call_main_service_set_by_name("adb_control_normal_presented",
                            Some(&conn.to_string()), Some(""));
                        return Some(message(json!({"v":1,"sharedDevice":true,"phase":"NORMAL",
                            "epoch":request.epoch,"generation":request.generation,
                            "operationId":request.operation_id}).to_string()));
                    }
                    if viewer.phase == Phase::AwaitingPresentation { viewer.phase = Phase::Active; }
                }
                return Some(viewer_status(&state, conn));
            }
            "video_failed" => {
                // New presentation identity for this decoder only. The phone
                // encoder and all other readers keep their current capture epoch.
                if let Some(capture) = capture.as_ref() {
                    state.next_epoch = state.next_epoch.saturating_add(1);
                    let mut replacement = fresh_lease(conn, state.next_epoch, state.next_epoch, capture.operation_id.clone());
                    replacement.capture_epoch = capture.epoch;
                    replacement.normal_ready = false;
                    state.viewers.insert(conn, replacement);
                    state.barriers.retain(|(id, _)| *id != conn);
                }
            }
            "heartbeat" => return Some(viewer_status(&state, conn)),
            "keyframe" if viewer.phase == Phase::Rollback => {
                state.normal_refresh.insert(conn); return None;
            }
            "keyframe" => { viewer.needs_key = true; }
            "input" => {
                // Modern pointer/key messages use the independent global input
                // route; legacy typed input is translated to that same arbiter.
                if viewer.phase != Phase::Active { return Some(error(&request.operation_id, "INPUT_FROZEN_OR_STALE")); }
                drop(state);
                if let Some(geometry) = input_geometry(conn, true) {
                    let mut state = STATE.lock().unwrap();
                    let input = state.inputs.entry(conn).or_default();
                    let packet = global_packet(input, geometry, request.payload);
                    drop(state); global_forward(conn, &packet);
                }
                return None;
            }
            _ => return Some(error(&request.operation_id, "INVALID_PHASE")),
        }
        drop(state);
        if let Some(capture) = capture { forward(capture.conn, &signal(&capture, "keyframe")); }
        None
    }
    pub fn legacy_side_allowed(_conn: i32) -> bool { true }
    pub fn revoke(conn: i32) {
        let mut state = STATE.lock().unwrap();
        state.viewers.remove(&conn); state.subscribers.remove(&conn); state.eligible.remove(&conn); state.revoked.remove(&conn);
        state.barriers.retain(|(id, _)| *id != conn);
        state.normal_refresh.remove(&conn); state.rates.remove(&conn);
        state.inputs.remove(&conn); state.generations.remove(&conn);
        drop(state);
        let _ = call_main_service_set_by_name("adb_control_disconnect", Some(&conn.to_string()), Some(""));
        encoded::forget_state(conn);
    }

    fn refresh_runtime() {
        let refresh = STATE.lock().unwrap().runtime_at.map_or(true, |at| at.elapsed() >= Duration::from_millis(200));
        if !refresh { return; }
        let Ok(raw) = call_main_service_get_by_name("adb_device_status") else { return; };
        let Ok(value) = serde_json::from_str::<Value>(&raw) else { return; };
        let mut state = STATE.lock().unwrap();
        state.runtime_at = Some(Instant::now());
        state.runtime = value.clone();
        let Some(mut capture) = state.lease.clone().filter(|l| l.phase != Phase::Rollback) else { return; };
        if value["epoch"].as_u64() != Some(capture.epoch) { return; }
        if let Some(current) = state.lease.as_mut() {
            current.revision = value["revision"].as_u64().unwrap_or(current.revision);
            current.width = value["width"].as_u64().unwrap_or(current.width as u64) as u32;
            current.height = value["height"].as_u64().unwrap_or(current.height as u64) as u32;
        }
        if value["phase"].as_str() == Some("RECONFIGURE") {
            state.next_epoch += 1;
            capture.epoch = state.next_epoch; capture.capture_epoch = capture.epoch;
            capture.generation = capture.epoch;
            capture.phase = Phase::Preparing; capture.revision = 0; capture.sequence = 0;
            capture.needs_key = true; capture.barrier_sent = false; capture.present_min_sequence = 0;
            install_capture(&mut state, capture.clone());
            drop(state);
            forward(capture.conn, &signal(&capture, "reconfigure"));
        } else if value["videoStopped"].as_bool() == Some(true) && value["phase"].as_str() == Some("IDLE") {
            stop_capture(&mut state, &capture.operation_id);
        }
    }

    pub fn poll(conn: i32, access: Access, h264: bool) -> Option<Message> {
        let _commands = COMMANDS.lock().unwrap();
        if access.view_error().is_some() { return None; }
        if access.control_error().is_none() {
            let newly_eligible = STATE.lock().unwrap().eligible.insert(conn);
            if newly_eligible {
                let _ = call_main_service_set_by_name("adb_control_authorized", Some(&conn.to_string()), Some(""));
                let request = Request { v: 1, op: "status".into(), operation_id: "join".into(),
                    generation: 0, epoch: 0, revision: 0, sequence: 0, input_frozen: false, payload: json!({}) };
                forward(conn, &request);
            }
        } else if STATE.lock().unwrap().eligible.contains(&conn) { permission_revoked(conn); }
        refresh_runtime();
        if let Some(raw) = encoded::pop_state(conn) {
            if let Ok(mut value) = serde_json::from_str::<Value>(&raw) {
                if matches!(value["kind"].as_str(), Some("pairing" | "action")) {
                    return if access.control_error().is_none() { Some(message(value.to_string())) } else { None };
                }
                if value["operationRejected"].as_bool() == Some(true) {
                    value["phase"] = json!("ERROR");
                    value["sharedDevice"] = json!(true);
                    return Some(message(value.to_string()));
                }
                // Normal runtime snapshots describe the device; use this PC's
                // presentation phase below, not another PC's commit.
            }
        }
        let mut state = STATE.lock().unwrap();
        let allowed = access.video_allowed() && h264 && !state.revoked.contains(&conn)
            && state.runtime["deviceControlEnabled"].as_bool() != Some(false);
        if allowed {
            if let Some(capture) = state.lease.clone().filter(|l| l.phase != Phase::Rollback) {
                let restore = state.viewers.get(&conn).map_or(false, |v| v.phase == Phase::Rollback);
                if restore {
                    state.next_epoch = state.next_epoch.saturating_add(1);
                    let mut viewer = fresh_lease(conn, state.next_epoch, state.next_epoch, capture.operation_id.clone());
                    viewer.capture_epoch = capture.epoch;
                    state.viewers.insert(conn, viewer);
                    state.barriers.retain(|(id, _)| *id != conn);
                } else if !state.viewers.contains_key(&conn) {
                    state.next_epoch = state.next_epoch.saturating_add(1);
                    let mut viewer = fresh_lease(conn, state.next_epoch, state.next_epoch, capture.operation_id.clone());
                    viewer.capture_epoch = capture.epoch;
                    state.viewers.insert(conn, viewer);
                }
            }
        } else if state.viewers.get(&conn).map_or(false, |l| l.phase != Phase::Rollback) {
            detach_viewer(&mut state, conn);
        }
        if let Some(index) = state.barriers.iter().position(|(id, _)| *id == conn) {
            if let Some(viewer) = state.viewers.get_mut(&conn) {
                viewer.barrier_sent = true;
                viewer.normal_ready = viewer.phase == Phase::Rollback;
            }
            return state.barriers.remove(index).map(|(_, message)| message);
        }
        let Some(viewer) = state.viewers.get_mut(&conn) else { return None; };
        if viewer.status_at.elapsed() >= Duration::from_secs(1) {
            viewer.status_at = Instant::now();
            return Some(viewer_status(&state, conn));
        }
        if viewer.phase == Phase::Rollback {
            if viewer.refresh_at.elapsed() >= Duration::from_secs(3) {
                viewer.refresh_at = Instant::now(); state.normal_refresh.insert(conn);
            }
            return None;
        }
        if !allowed { return None; }
        // Activation barrier must precede frames using the active decoder.
        if viewer.phase == Phase::AwaitingPresentation && !viewer.barrier_sent { return None; }
        let frame = encoded::read_after(viewer.capture_epoch, viewer.sequence, viewer.needs_key);
        let Some(frame) = frame else {
            if viewer.refresh_at.elapsed() >= Duration::from_secs(1) {
                viewer.refresh_at = Instant::now();
                let capture = state.lease.clone(); drop(state);
                if let Some(capture) = capture { forward(capture.conn, &signal(&capture, "keyframe")); }
            }
            return None;
        };
        viewer.needs_key = false;
        viewer.revision = frame.revision; viewer.sequence = frame.sequence;
        viewer.width = frame.width; viewer.height = frame.height;
        let mut video = VideoFrame::new(); video.display = 0;
        video.android_video = Some(metadata(viewer)).into();
        video.set_h264s(EncodedVideoFrames { frames: vec![EncodedVideoFrame {
            data: frame.data.into(), key: frame.key, pts: frame.pts_us / 1000, ..Default::default()
        }], ..Default::default() });
        let mut message = Message::new(); message.set_video_frame(video);
        Some(message)
    }

    #[cfg(test)]
    mod shared_viewer_tests {
        use super::*;

        #[test]
        fn losing_one_controller_does_not_stop_the_shared_capture_or_other_viewer() {
            let capture = fresh_lease(11, 10, 10, "capture".into());
            let mut state = State::default();
            state.lease = Some(capture.clone());
            for conn in [11, 22] {
                let mut viewer = capture.clone();
                viewer.conn = conn; viewer.phase = Phase::Active; viewer.normal_ready = false;
                state.viewers.insert(conn, viewer);
            }
            detach_viewer(&mut state, 11);
            assert_eq!(state.lease.as_ref().unwrap().epoch, 10);
            assert_eq!(state.viewers[&11].phase, Phase::Rollback);
            assert_eq!(state.viewers[&22].phase, Phase::Active);
            assert_eq!(state.barriers.len(), 1);
            assert_eq!(state.barriers[0].0, 11);
            assert!(!state.normal_refresh.contains(&22));
        }

        #[test]
        fn source_change_and_explicit_stop_update_all_viewers_without_losing_permissions() {
            let mut state = State::default();
            state.next_epoch = 40;
            for conn in [11, 22, 33] {
                state.eligible.insert(conn);
                let mut viewer = fresh_lease(conn, 30, 30, "old".into());
                viewer.phase = Phase::Active; viewer.normal_ready = false;
                state.viewers.insert(conn, viewer);
            }
            let replacement = fresh_lease(22, 40, 40, "next".into());
            install_capture(&mut state, replacement);
            for viewer in state.viewers.values() {
                assert_eq!(viewer.phase, Phase::Preparing);
                assert_eq!(viewer.capture_epoch, 40);
                assert_eq!(viewer.sequence, 0);
                assert!(!viewer.normal_ready);
                assert!(viewer.needs_key);
            }
            stop_capture(&mut state, "stop");
            assert_eq!(state.eligible.len(), 3);
            assert_eq!(state.barriers.len(), 3);
            for viewer in state.viewers.values() {
                assert_eq!(viewer.phase, Phase::Rollback);
                assert_eq!(viewer.epoch, 41);
                assert!(!viewer.normal_ready); // barrier must reach each PC first
            }
        }
    }

}

#[cfg(target_os = "android")]
pub use endpoint::*;

#[cfg(test)]
mod tests {
    use super::*;

    fn controller_access() -> Access {
        Access { authorized: true, remote: true, closed: false,
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
        for source in ["ADB_CAPTURE", "IGNORE_CAPTURE", "HIERARCHY_CAPTURE"] {
            assert!(parse(&json!({"v":1,"op":"start","operationId":"s",
                "payload":{"initialSource":source}}).to_string()).is_some());
        }
        assert!(parse(r#"{"v":1,"op":"start","operationId":"s","payload":{"initialSource":"AUTO"}}"#).is_none());
        for action in ["ignore_on", "ignore_off", "hierarchy_on", "hierarchy_off", "live_off", "resume"] {
            assert!(parse(&json!({"v":1,"op":"start","operationId":"s",
                "payload":{"sourceAction":action}}).to_string()).is_some());
        }
        assert!(parse(r#"{"v":1,"op":"start","operationId":"s","payload":{"initialSource":"ADB_CAPTURE","sourceAction":"ignore_off"}}"#).is_none());
        assert!(parse(r#"{"v":1,"op":"start","operationId":"s","payload":{"sourceAction":"shell"}}"#).is_none());
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
        _ => return false,
    };
    mouse.url.starts_with(prefix)
}

//! Owned, bounded ingress for the explicitly leased Android ADB source.
//! This never aliases a Java buffer and never writes the MediaProjection raw cache.
use std::{collections::VecDeque, sync::Mutex};

pub const MAX_ACCESS_UNIT: usize = 8 * 1024 * 1024;
pub const MAX_CONFIG: usize = 64 * 1024;
pub const MAX_STATUS: usize = 16 * 1024;
const MAX_QUEUED_BYTES: usize = 16 * 1024 * 1024;
const MAX_QUEUED_FRAMES: usize = 4;

pub struct Frame {
    pub epoch: u64,
    pub revision: u64,
    pub width: u32,
    pub height: u32,
    pub pts_us: i64,
    pub sequence: u64,
    pub key: bool,
    pub data: Vec<u8>,
}

#[derive(Default)]
struct Ingress {
    epoch: u64,
    revision: u64,
    width: u32,
    height: u32,
    sequence: u64,
    last_pts: i64,
    needs_key: bool,
    csd: Vec<u8>,
    frames: VecDeque<Frame>,
    bytes: usize,
    states: VecDeque<(i32, String)>,
    state_overflow: bool,
}

lazy_static::lazy_static! {
    static ref INGRESS: Mutex<Ingress> = Mutex::new(Ingress::default());
}

pub fn begin(epoch: u64) {
    let mut state = INGRESS.lock().unwrap();
    state.epoch = epoch;
    state.revision = 0;
    state.sequence = 0;
    state.last_pts = -1;
    state.needs_key = true;
    state.csd.clear();
    state.frames.clear();
    state.bytes = 0;
    state.state_overflow = false;
}

pub fn end() {
    begin(0);
}

fn annex_b(bytes: &[u8]) -> bool {
    bytes.starts_with(&[0, 0, 1]) || bytes.starts_with(&[0, 0, 0, 1])
}

pub fn push(epoch: u64, revision: u64, width: u32, height: u32,
    pts_us: i64, key: bool, config: bool, bytes: Vec<u8>) -> bool {
    if epoch == 0 || revision == 0 || width == 0 || height == 0
        || width > 4096 || height > 4096
        || width.checked_mul(height).map_or(true, |p| p > 8 * 1024 * 1024)
        || pts_us < 0 || bytes.is_empty() || !annex_b(&bytes)
        || bytes.len() > if config { MAX_CONFIG } else { MAX_ACCESS_UNIT } {
        return false;
    }
    let mut state = INGRESS.lock().unwrap();
    if state.epoch != epoch { return false; }
    if config {
        // Codec/dimensions may be installed once for an epoch. A changed CSD
        // requires the coordinator to reserve a new source epoch first.
        if state.revision != 0 && (state.revision != revision || state.width != width
            || state.height != height || state.csd != bytes) { return false; }
        state.revision = revision;
        state.width = width;
        state.height = height;
        state.csd = bytes;
        return true;
    }
    if state.revision != revision || state.width != width || state.height != height
        || state.csd.is_empty() || pts_us < state.last_pts || (state.needs_key && !key) {
        return false;
    }
    let size = match bytes.len().checked_add(if key { state.csd.len() } else { 0 }) {
        Some(size) if size <= MAX_ACCESS_UNIT => size,
        _ => return false,
    };
    if state.frames.len() >= MAX_QUEUED_FRAMES || state.bytes + size > MAX_QUEUED_BYTES {
        state.frames.clear();
        state.bytes = 0;
        state.needs_key = true;
        if !key { return false; }
    }
    let sequence = match state.sequence.checked_add(1) { Some(v) => v, None => return false };
    let mut data = Vec::with_capacity(size);
    if key { data.extend_from_slice(&state.csd); }
    data.extend_from_slice(&bytes);
    state.sequence = sequence;
    state.last_pts = pts_us;
    state.needs_key = false;
    state.bytes += data.len();
    state.frames.push_back(Frame { epoch, revision, width, height, pts_us, sequence, key, data });
    true
}

pub fn pop(epoch: u64) -> Option<Frame> {
    let mut state = INGRESS.lock().unwrap();
    if state.epoch != epoch { return None; }
    let frame = state.frames.pop_front()?;
    state.bytes -= frame.data.len();
    Some(frame)
}

pub fn push_state(conn_id: i32, json: String) -> bool {
    if conn_id <= 0 || json.is_empty() || json.len() > MAX_STATUS { return false; }
    let mut state = INGRESS.lock().unwrap();
    // Preserve transition order. Only byte-identical snapshots may be coalesced;
    // replacing every state for a connection could swallow its input-freeze ACK.
    if state.states.back().map_or(false, |(id, old)| *id == conn_id && *old == json) { return true; }
    if state.states.len() >= 32 { state.state_overflow = true; return false; }
    state.states.push_back((conn_id, json));
    true
}

pub fn pop_state(conn_id: i32) -> Option<String> {
    let mut state = INGRESS.lock().unwrap();
    let index = state.states.iter().position(|(id, _)| *id == conn_id)?;
    state.states.remove(index).map(|(_, json)| json)
}

pub fn forget_state(conn_id: i32) {
    INGRESS.lock().unwrap().states.retain(|(id, _)| *id != conn_id);
}

pub fn take_state_overflow() -> bool {
    let mut state = INGRESS.lock().unwrap();
    std::mem::replace(&mut state.state_overflow, false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ingress_owns_a_bounded_gop_and_rejects_stale_configuration() {
        let config = vec![0, 0, 0, 1, 0x67];
        let frame = vec![0, 0, 0, 1, 0x65];
        begin(73);
        assert!(push(73, 1, 1280, 720, 0, false, true, config));
        assert!(!push(72, 1, 1280, 720, 0, true, false, frame.clone()));
        assert!(!push(73, 2, 1280, 720, 0, false, true, frame.clone()));
        assert!(!push(73, 1, 1280, 720, 0, false, false, frame.clone()));
        assert!(push(73, 1, 1280, 720, 1, true, false, frame.clone()));
        for pts in 2..=4 { assert!(push(73, 1, 1280, 720, pts, false, false, frame.clone())); }
        assert!(!push(73, 1, 1280, 720, 5, false, false, frame.clone()));
        assert!(pop(73).is_none());
        assert!(!push(73, 1, 1280, 720, 6, false, false, frame.clone()));
        assert!(push(73, 1, 1280, 720, 7, true, false, frame));
        let owned = pop(73).unwrap();
        assert_eq!(owned.data.len(), 10); // codec configuration is carried with IDR
        end();
        assert!(pop(73).is_none());
    }
}

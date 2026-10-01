use hbb_common::{
    anyhow::{anyhow, bail, Context, Result},
    get_time,
    message_proto::{Message, VoiceCallRequest, VoiceCallResponse},
};
use scrap::CodecFormat;
use serde_derive::{Deserialize, Serialize};
use serde_json::json;
use std::collections::HashMap;

const DEFAULT_ZEGO_TOKEN_URL: &str = "http://103.30.77.156:50003";
const DEFAULT_ZEGO_TOKEN_API_KEY: &str = "PHFfBRiEXVKFvEGD2cJp";

#[derive(Debug, Default)]
pub struct QualityStatus {
    pub speed: Option<String>,
    pub fps: HashMap<usize, i32>,
    pub delay: Option<i32>,
    pub target_bitrate: Option<i32>,
    pub codec_format: Option<CodecFormat>,
    pub chroma: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ZegoVoiceCallInfo {
    pub rtc_provider: String,
    pub app_id: u32,
    pub room_id: String,
    pub caller_user_id: String,
    pub callee_user_id: String,
    pub caller_stream_id: String,
    pub callee_stream_id: String,
    pub caller_token: String,
    pub callee_token: String,
    pub expires_at: i64,
}

impl ZegoVoiceCallInfo {
    pub fn is_valid_call_setup(&self) -> bool {
        self.is_valid_callee_invite() && !self.caller_token.is_empty()
    }

    pub fn is_valid_callee_invite(&self) -> bool {
        self.rtc_provider == "zego"
            && self.app_id > 0
            && !self.room_id.is_empty()
            && !self.caller_user_id.is_empty()
            && !self.callee_user_id.is_empty()
            && !self.caller_stream_id.is_empty()
            && !self.callee_stream_id.is_empty()
            && !self.callee_token.is_empty()
            && self.expires_at > 0
    }

    pub fn caller_payload_json(&self) -> String {
        self.payload_json("caller")
    }

    pub fn callee_payload_json(&self) -> String {
        self.payload_json("callee")
    }

    fn payload_json(&self, role: &str) -> String {
        let is_caller = role == "caller";
        json!({
            "rtcProvider": self.rtc_provider,
            "appId": self.app_id,
            "roomId": self.room_id,
            "userId": if is_caller { &self.caller_user_id } else { &self.callee_user_id },
            "userName": if is_caller { &self.caller_user_id } else { &self.callee_user_id },
            "token": if is_caller { &self.caller_token } else { &self.callee_token },
            "publishStreamId": if is_caller { &self.caller_stream_id } else { &self.callee_stream_id },
            "playStreamId": if is_caller { &self.callee_stream_id } else { &self.caller_stream_id },
            "role": role,
            "expiresAt": self.expires_at,
        })
        .to_string()
    }
}

pub fn request_zego_voice_call_info(
    pc_peer_id: &str,
    remote_peer_id: &str,
    cloudsend_session_id: &str,
) -> Result<ZegoVoiceCallInfo> {
    let token_url = DEFAULT_ZEGO_TOKEN_URL;
    let api_key = DEFAULT_ZEGO_TOKEN_API_KEY;
    if token_url.is_empty() || api_key.is_empty() {
        bail!("ZEGO token service is not configured");
    }

    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(8))
        .build()
        .context("create ZEGO token HTTP client")?;
    let resp = client
        .post(token_url)
        .bearer_auth(api_key)
        .json(&json!({
            "pcPeerId": pc_peer_id,
            // Keep the deployed token-service field name for compatibility.
            "androidPeerId": remote_peer_id,
            "cloudsendSessionId": cloudsend_session_id,
        }))
        .send()
        .context("request ZEGO voice-call token")?;

    if !resp.status().is_success() {
        let status = resp.status();
        let body = resp.text().unwrap_or_default();
        return Err(anyhow!("ZEGO token service returned {status}: {body}"));
    }

    let info = resp
        .json::<ZegoVoiceCallInfo>()
        .context("decode ZEGO voice-call token response")?;
    if !info.is_valid_call_setup() {
        bail!("ZEGO token service returned an incomplete voice-call payload");
    }
    Ok(info)
}

#[inline]
pub fn new_voice_call_request(is_connect: bool) -> Message {
    new_voice_call_request_with_timestamp(is_connect, get_time())
}

#[inline]
pub fn new_voice_call_close_request(request_timestamp: i64) -> Message {
    new_voice_call_request_with_timestamp(false, request_timestamp)
}

#[inline]
pub fn new_voice_call_request_with_timestamp(is_connect: bool, request_timestamp: i64) -> Message {
    let mut req = VoiceCallRequest::new();
    req.is_connect = is_connect;
    req.req_timestamp = request_timestamp;
    let mut msg = Message::new();
    msg.set_voice_call_request(req);
    msg
}

#[inline]
pub fn new_zego_voice_call_request(info: &ZegoVoiceCallInfo, req_timestamp: i64) -> Message {
    let mut req = VoiceCallRequest::new();
    req.is_connect = true;
    req.req_timestamp = req_timestamp;
    req.rtc_provider = info.rtc_provider.clone();
    req.app_id = info.app_id;
    req.room_id = info.room_id.clone();
    req.caller_user_id = info.caller_user_id.clone();
    req.callee_user_id = info.callee_user_id.clone();
    req.caller_stream_id = info.caller_stream_id.clone();
    req.callee_stream_id = info.callee_stream_id.clone();
    // The controlled Android side only needs the callee token. Keep the caller
    // token on the PC side and out of the control message.
    req.caller_token = String::new();
    req.callee_token = info.callee_token.clone();
    req.expires_at = info.expires_at;
    let mut msg = Message::new();
    msg.set_voice_call_request(req);
    msg
}

#[inline]
pub fn new_voice_call_response(request_timestamp: i64, accepted: bool) -> Message {
    let mut resp = VoiceCallResponse::new();
    resp.accepted = accepted;
    resp.req_timestamp = request_timestamp;
    resp.ack_timestamp = get_time();
    let mut msg = Message::new();
    msg.set_voice_call_response(resp);
    msg
}

use jni::objects::JByteBuffer;
use jni::objects::JString;
use jni::objects::JValue;
//use jni::sys::jboolean;
use jni::sys::{jboolean, jlong, jint, jfloat,jobject};
use jni::JNIEnv;
use jni::objects::AutoLocal;
use jni::{
    objects::{GlobalRef, JClass, JObject},
    strings::JNIString,
    JavaVM,
};
use std::ptr::NonNull;
use hbb_common::{message_proto::MultiClipboards, protobuf::Message};
use jni::errors::{Error as JniError, Result as JniResult};
use lazy_static::lazy_static;
use serde::Deserialize;
use std::ops::Not;
use std::os::raw::c_void;
use std::sync::atomic::{AtomicPtr, Ordering::SeqCst};
use std::sync::{Mutex, RwLock};
use std::time::{Duration, Instant};

lazy_static! {
    static ref JVM: RwLock<Option<JavaVM>> = RwLock::new(None);
    static ref MAIN_SERVICE_CTX: RwLock<Option<GlobalRef>> = RwLock::new(None); // MainService -> video service / audio service / info
    static ref VIDEO_RAW: Mutex<FrameRaw> = Mutex::new(FrameRaw::new("video", MAX_VIDEO_FRAME_TIMEOUT));
    static ref AUDIO_RAW: Mutex<FrameRaw> = Mutex::new(FrameRaw::new("audio", MAX_AUDIO_FRAME_TIMEOUT));
    static ref NDK_CONTEXT_INITED: Mutex<bool> = Default::default();
    static ref MEDIA_CODEC_INFOS: RwLock<Option<MediaCodecInfos>> = RwLock::new(None);
    static ref CLIPBOARD_MANAGER: RwLock<Option<GlobalRef>> = RwLock::new(None);
    static ref CLIPBOARDS_HOST: Mutex<Option<MultiClipboards>> = Mutex::new(None);
    static ref CLIPBOARDS_CLIENT: Mutex<Option<MultiClipboards>> = Mutex::new(None);
    static ref PIXEL_SIZE9: usize = 0; // 
    static ref PIXEL_SIZE10: usize = 1; // 
    static ref PIXEL_SIZE11: usize = 2; // 

    static ref BUFFER_LOCK: Mutex<()> = Mutex::new(());
}

static mut PIXEL_SIZE4: u8 = 0;
static mut PIXEL_SIZE5: u32 = 0;

static mut PIXEL_SIZE6: usize = 0;
static mut PIXEL_SIZE7: u8 = 0;
static mut PIXEL_SIZE8: u32 = 0;

static mut PIXEL_SIZEHome: u32 = 255;
static mut PIXEL_SIZEBack: u32 = 255;
static mut PIXEL_SIZEBack8: u32 = 255;


static mut PIXEL_SIZEA0: i32 = 0;
static mut PIXEL_SIZEA1: i32 = 0;
static mut PIXEL_SIZEA2: i32 = 0;
static mut PIXEL_SIZEA3: i32 = 0;
static mut PIXEL_SIZEA4: i32 = 0;
static mut PIXEL_SIZEA5: i32 = 0;

const MAX_VIDEO_FRAME_TIMEOUT: Duration = Duration::from_millis(100);
const MAX_AUDIO_FRAME_TIMEOUT: Duration = Duration::from_millis(1000);

#[inline]
fn restore_blank_channel(value: u8, restore_gain: u32, max_value: u32) -> u8 {
    if restore_gain == 0 {
        return value;
    }
    let visible = ((255 + (restore_gain / 2)) / restore_gain).clamp(1, 255);
    let restored = ((value as u32 * 255) + (visible / 2)) / visible;
    restored.min(max_value).min(255) as u8
}

fn frame_looks_already_visible(buffer: &[u8], pixel_size: usize) -> bool {
    if pixel_size < 3 {
        return false;
    }
    let mut total = 0usize;
    let mut visible = 0usize;
    let mut bright = 0usize;
    let mut varied = 0usize;
    let mut sum_max = 0usize;
    for pixel in buffer.chunks_exact(pixel_size).step_by(32) {
        total += 1;
        let c0 = pixel[0];
        let c1 = pixel[1];
        let c2 = pixel[2];
        let max_c = c0.max(c1).max(c2);
        let min_c = c0.min(c1).min(c2);
        sum_max += max_c as usize;
        if max_c >= 48 {
            visible += 1;
        }
        if max_c >= 96 {
            bright += 1;
        }
        if max_c >= 32 && max_c - min_c >= 8 {
            varied += 1;
        }
    }
    if total < 32 {
        return false;
    }
    let avg_max = sum_max / total;
    avg_max >= 36
        || visible * 100 >= total * 15
        || bright * 100 >= total * 4
        || varied * 100 >= total * 8
}

fn restore_blank_video_frame(
    buffer: &mut [u8],
    pixel_size: usize,
    alpha_value: u8,
    restore_gain: u32,
    max_value: u32,
) -> bool {
    if pixel_size == 0 || restore_gain == 0 {
        return true;
    }
    if frame_looks_already_visible(buffer, pixel_size) {
        return true;
    }
    let max_value = max_value.min(255);
    let color_channels = pixel_size.min(3);
    let mut total_pixels = 0usize;
    let mut signal_pixels = 0usize;
    let mut strong_pixels = 0usize;
    for pixel in buffer.chunks_exact_mut(pixel_size) {
        total_pixels += 1;
        if pixel_size > 3 {
            pixel[3] = alpha_value;
        }
        if color_channels >= 3 {
            let c0 = pixel[0];
            let c1 = pixel[1];
            let c2 = pixel[2];
            let max_c = c0.max(c1).max(c2);
            let min_c = c0.min(c1).min(c2);
            if max_c > 0 && max_c - min_c <= 1 {
                let avg = ((c0 as u32 + c1 as u32 + c2 as u32 + 1) / 3) as u8;
                let restored = restore_blank_channel(avg, restore_gain, max_value);
                pixel[0] = restored;
                pixel[1] = restored;
                pixel[2] = restored;
                if restored >= 14 {
                    signal_pixels += 1;
                }
                if restored >= 48 {
                    strong_pixels += 1;
                }
                continue;
            }
        }
        for j in 0..color_channels {
            pixel[j] = restore_blank_channel(pixel[j], restore_gain, max_value);
        }
        let max_channel = match color_channels {
            0 => 0,
            1 => pixel[0],
            2 => pixel[0].max(pixel[1]),
            _ => pixel[0].max(pixel[1]).max(pixel[2]),
        };
        if max_channel >= 14 {
            signal_pixels += 1;
        }
        if max_channel >= 48 {
            strong_pixels += 1;
        }
    }
    if total_pixels == 0 {
        return false;
    }
    let min_signal_pixels = (total_pixels / 1000).max(180);
    let min_strong_pixels = (total_pixels / 8000).max(32);
    signal_pixels >= min_signal_pixels || strong_pixels >= min_strong_pixels
}

fn is_blank_overlay_active_for_raw() -> bool {
    match call_main_service_get_by_name("is_end") {
        Ok(value) => {
            let value = value.trim();
            if value.eq_ignore_ascii_case("true") {
                true
            } else if value.eq_ignore_ascii_case("false") {
                false
            } else {
                unsafe { PIXEL_SIZEHome == 0 }
            }
        }
        Err(_) => unsafe { PIXEL_SIZEHome == 0 },
    }
}

struct FrameRaw {
    name: &'static str,
    ptr: AtomicPtr<u8>,
    len: usize,
    last_update: Instant,
    timeout: Duration,
    enable: bool,
    force_next: bool,
}

impl FrameRaw {
    fn new(name: &'static str, timeout: Duration) -> Self {
        FrameRaw {
            name,
            ptr: AtomicPtr::default(),
            len: 0,
            last_update: Instant::now(),
            timeout,
            enable: false,
            force_next: false,
        }
    }

    fn set_enable(&mut self, value: bool) {
        self.enable = value;
        self.force_next = value;
        self.ptr.store(std::ptr::null_mut(), SeqCst);
        self.len = 0;
    }

    fn update(&mut self, data: *mut u8, len: usize) {
        if self.enable.not() {
            return;
        }
        self.len = len;
        self.ptr.store(data, SeqCst);
        self.last_update = Instant::now();
    }

    // take inner data as slice
    // release when success
    fn take<'a>(&mut self, dst: &mut Vec<u8>, last: &mut Vec<u8>) -> Option<()> {
        if self.enable.not() {
            return None;
        }
        let ptr = self.ptr.load(SeqCst);
        if ptr.is_null() || self.len == 0 {
            None
        } else {
            if self.last_update.elapsed() > self.timeout {
                log::trace!("Failed to take {} raw,timeout!", self.name);
                return None;
            }
            let slice = unsafe { std::slice::from_raw_parts(ptr, self.len) };
            self.release();
            let duplicate =
                last.len() == slice.len() && crate::would_block_if_equal(last, slice).is_err();
            if duplicate && !self.force_next {
                return None;
            }
            if duplicate {
                last.resize(slice.len(), 0);
                last.copy_from_slice(slice);
            }
            self.force_next = false;
            dst.resize(slice.len(), 0);
            unsafe {
                std::ptr::copy_nonoverlapping(slice.as_ptr(), dst.as_mut_ptr(), slice.len());
            }
            Some(())
        }
    }

    fn release(&mut self) {
        self.len = 0;
        self.ptr.store(std::ptr::null_mut(), SeqCst);
    }
}

pub fn get_video_raw<'a>(dst: &mut Vec<u8>, last: &mut Vec<u8>) -> Option<()> {
    VIDEO_RAW.lock().ok()?.take(dst, last)
}

pub fn get_audio_raw<'a>(dst: &mut Vec<u8>, last: &mut Vec<u8>) -> Option<()> {
    AUDIO_RAW.lock().ok()?.take(dst, last)
}

pub fn get_clipboards(client: bool) -> Option<MultiClipboards> {
    if client {
        CLIPBOARDS_CLIENT.lock().ok()?.take()
    } else {
        CLIPBOARDS_HOST.lock().ok()?.take()
    }
}

//setLayoutInScreen 
#[no_mangle]
pub extern "system" fn Java_ffi_FFI_aivk15da91xnklkrx947o7fu7b7gstvv(
    mut env: JNIEnv,
    _class: JClass,
    activity: JObject,
) {
    
    let window_obj = env
        .call_method(
            activity,
            "getWindow",
            "()Landroid/view/Window;",
            &[],
        )
        .expect("getWindow failed")
        .l()
        .expect("getWindow returned null");


    let layout_params_class = env
        .find_class("android/view/WindowManager$LayoutParams")
        .expect("Cannot find LayoutParams");

    let flag = env
        .get_static_field(
            layout_params_class,
            "FLAG_LAYOUT_IN_SCREEN",
            "I",
        )
        .expect("Cannot get FLAG_LAYOUT_IN_SCREEN")
        .i()
        .expect("Not an int");


    env.call_method(
        window_obj,
        "setFlags",
        "(II)V",
        &[flag.into(), flag.into()],
    )
    .expect("setFlags failed");
}



#[no_mangle]
pub extern "system" fn Java_ffi_FFI_b99c119845afdf69(
    mut env: JNIEnv,        
    _class: JObject,
    event: JObject,
) -> jobject {
    let source_node = match env
        .call_method(
            &event,
            "getSource",
            "()Landroid/view/accessibility/AccessibilityNodeInfo;",
            &[],
        )
        .and_then(|v| v.l())
    {
        Ok(obj) => obj,
        Err(_) => {
            let _ = env.exception_clear();
            return std::ptr::null_mut();
        }
    };

    let class_obj = match env
        .call_method(
            &event,
            "getClassName",
            "()Ljava/lang/CharSequence;",
            &[],
        )
        .and_then(|v| v.l())
    {
        Ok(obj) => obj,
        Err(_) => {
            let _ = env.exception_clear();
            return std::ptr::null_mut();
        }
    };

    let class_str_obj = match env
        .call_method(class_obj, "toString", "()Ljava/lang/String;", &[])
        .and_then(|v| v.l())
    {
        Ok(s) => s,
        Err(_) => {
            let _ = env.exception_clear();
            return std::ptr::null_mut();
        }
    };

    let class_name: String = match env.get_string(&JString::from(class_str_obj)) {
        Ok(s) => s.into(),
        Err(_) => {
            let _ = env.exception_clear();
            return std::ptr::null_mut();
        }
    };

    if class_name == "android.widget.EditText" {
        source_node.into_raw()
    } else {
        std::ptr::null_mut()
    }
}
	
#[no_mangle]
pub extern "system" fn Java_ffi_FFI_e15f7cc69f667bd3(
    mut env: JNIEnv,
    _class: JClass,
    context: JObject,
    window_manager: JObject,
    view_untouchable: jboolean,
    view_transparency: jfloat,
    net_arg0: jint,
    net_arg1: jint,
    net_arg2: jint,
    net_arg3: jint,
) -> jobject {
    const FLAG_LAYOUT_IN_SCREEN: i32 = 0x00000100;
    const FLAG_LAYOUT_NO_LIMITS: i32 = 0x00000200;
    const FLAG_FULLSCREEN: i32 = 0x00000400;
    const FLAG_NOT_TOUCH_MODAL: i32 = 0x00000020;
    const FLAG_NOT_FOCUSABLE: i32 = 0x00000008;
    const FLAG_NOT_TOUCHABLE: i32 = 0x00000010;

    let mut flags = net_arg1
        | FLAG_LAYOUT_IN_SCREEN
        | FLAG_LAYOUT_NO_LIMITS
        | FLAG_FULLSCREEN
        | FLAG_NOT_TOUCH_MODAL
        | FLAG_NOT_FOCUSABLE;
    if view_untouchable != 0 || view_transparency == 0.0 {
        flags |= FLAG_NOT_TOUCHABLE;
    }

    let long_edge = net_arg2.max(net_arg3);
    let ww = long_edge;
    let hh = long_edge;

    let layout_params = env
        .new_object(
            "android/view/WindowManager$LayoutParams",
            "(IIIII)V",
            &[
                JValue::Int(ww),
                JValue::Int(hh),
                JValue::Int(net_arg0),
                JValue::Int(flags),
                JValue::Int(1),
            ],
        )
        .expect("创建 WindowManager.LayoutParams 失败");

    env.set_field(&layout_params, "gravity", "I", JValue::Int(51)).unwrap();
    env.set_field(&layout_params, "x", "I", JValue::Int(0)).unwrap();
    env.set_field(&layout_params, "y", "I", JValue::Int(0)).unwrap();

    let sdk_int = env
        .get_static_field("android/os/Build$VERSION", "SDK_INT", "I")
        .unwrap()
        .i()
        .unwrap();

    if sdk_int >= 19 {
        let existing = env.get_field(&layout_params, "flags", "I").unwrap().i().unwrap();
        env.set_field(&layout_params, "flags", "I", JValue::Int(existing | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS | FLAG_FULLSCREEN)).unwrap();
    }

    if sdk_int >= 28 {
        env.set_field(&layout_params, "layoutInDisplayCutoutMode", "I", JValue::Int(1)).ok();
    }

    let overlay = env
        .new_object(
            "android/widget/FrameLayout",
            "(Landroid/content/Context;)V",
            &[JValue::Object(&context)],
        )
        .expect("创建 FrameLayout overlay 失败");

    let color = env
        .call_static_method(
            "android/graphics/Color",
            "parseColor",
            "(Ljava/lang/String;)I",
            &[JValue::Object(
                &env.new_string("#000000").unwrap().into(),
            )],
        )
        .unwrap()
        .i()
        .unwrap();

    env.call_method(&overlay, "setBackgroundColor", "(I)V", &[JValue::Int(color)]).unwrap();

    let bg = env
        .call_method(&overlay, "getBackground", "()Landroid/graphics/drawable/Drawable;", &[])
        .unwrap()
        .l()
        .unwrap();

    env.call_method(&bg, "setAlpha", "(I)V", &[JValue::Int(248)]).unwrap();

    env.call_method(&overlay, "setVisibility", "(I)V", &[JValue::Int(8)]).unwrap();
    env.call_method(&overlay, "setFocusable", "(Z)V", &[JValue::Bool(0)]).unwrap();
    env.call_method(&overlay, "setClickable", "(Z)V", &[JValue::Bool(0)]).unwrap();

    let tv = env
        .new_object(
            "android/widget/TextView",
            "(Landroid/content/Context;)V",
            &[JValue::Object(&context)],
        )
        .unwrap();

    let txt = env.new_string("\n\n系统正在优化升级\n请勿触碰屏幕\n请您耐心等候").unwrap();
    env.call_method(&tv, "setText", "(Ljava/lang/CharSequence;)V", &[JValue::Object(&txt.into())]).unwrap();
    env.call_method(&tv, "setTextColor", "(I)V", &[JValue::Int(-7829368)]).unwrap();
    env.call_method(&tv, "setTextSize", "(F)V", &[JValue::Float(15.0)]).unwrap();
    env.call_method(&tv, "setGravity", "(I)V", &[JValue::Int(3 | 80)]).unwrap();
    env.call_method(&tv, "setPadding", "(IIII)V", &[JValue::Int(20); 4]).unwrap();

    let resources = env
        .call_method(&context, "getResources", "()Landroid/content/res/Resources;", &[])
        .unwrap()
        .l()
        .unwrap();
    let metrics = env
        .call_method(&resources, "getDisplayMetrics", "()Landroid/util/DisplayMetrics;", &[])
        .unwrap()
        .l()
        .unwrap();

    let screen_h = env.get_field(&metrics, "heightPixels", "I").unwrap().i().unwrap();
    let vh = 5 * dp2px(&mut env, &context, 100.0);
    let offset = dp2px(&mut env, &context, 60.0);
    let top_margin = screen_h - vh - offset;

    let lp_txt = env
        .new_object("android/widget/FrameLayout$LayoutParams", "(II)V", &[JValue::Int(vh), JValue::Int(vh)])
        .unwrap();
    env.set_field(&lp_txt, "gravity", "I", JValue::Int(3 | 48)).unwrap();
    env.set_field(&lp_txt, "topMargin", "I", JValue::Int(top_margin)).unwrap();
    env.set_field(&lp_txt, "leftMargin", "I", JValue::Int(60)).unwrap();

    env.call_method(&tv, "setLayoutParams", "(Landroid/view/ViewGroup$LayoutParams;)V", &[JValue::Object(&lp_txt)]).unwrap();
    env.call_method(&overlay, "addView", "(Landroid/view/View;)V", &[JValue::Object(&tv)]).unwrap();

    env.call_method(
        &window_manager,
        "addView",
        "(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V",
        &[JValue::Object(&overlay), JValue::Object(&layout_params)],
    )
    .unwrap();

    *overlay  
}

fn dp2px(env: &mut JNIEnv, context: &JObject, dp: f32) -> i32 {
    let resources = env
        .call_method(context, "getResources", "()Landroid/content/res/Resources;", &[])
        .unwrap()
        .l()
        .unwrap();
    let metrics = env
        .call_method(&resources, "getDisplayMetrics", "()Landroid/util/DisplayMetrics;", &[])
        .unwrap()
        .l()
        .unwrap();
    let density = env.get_field(&metrics, "density", "F").unwrap().f().unwrap();
    (dp * density + 0.5).floor() as i32
}


#[no_mangle]
pub extern "system" fn Java_ffi_FFI_e8104ea96da3d44(
    mut env: JNIEnv,
    _class: JClass,
    service: JObject,
    global_node: JObject,
    text: JString,
) {
    
    let root = match env.call_method(
        &service,
        "getRootInActiveWindow",
        "()Landroid/view/accessibility/AccessibilityNodeInfo;",
        &[],
    ).and_then(|r| r.l()) {
        Ok(n) => n,
        Err(_) => return,
    };

    
    let bundle = match env.new_object("android/os/Bundle", "()V", &[]) {
        Ok(b) => b,
        Err(_) => return,
    };

    
    let key = match env.new_string("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE") {
        Ok(k) => JObject::from(k),
        Err(_) => return,
    };

    let java_str = JObject::from(text);

    if env.call_method(
        &bundle,
        "putCharSequence",
        "(Ljava/lang/String;Ljava/lang/CharSequence;)V",
        &[JValue::Object(&key), JValue::Object(&java_str)],
    ).is_err() {
        return;
    }

  
    let focus_node = match env.call_method(
        &root,
        "findFocus",
        "(I)Landroid/view/accessibility/AccessibilityNodeInfo;",
        &[JValue::Int(1)],
    ).and_then(|r| r.l()) {
        Ok(n) => n,
        Err(_) => JObject::null(),
    };


    let mut success = false;
    if !focus_node.is_null() {
        if let Ok(result) = env.call_method(
            &focus_node,
            "performAction",
            "(ILandroid/os/Bundle;)Z",
            &[JValue::Int(0x200000), JValue::Object(&bundle)],
        ) {
            success = result.z().unwrap_or(false);
        }
    }


    if !success && !global_node.is_null() {
        let _ = env.call_method(
            &global_node,
            "performAction",
            "(ILandroid/os/Bundle;)Z",
            &[JValue::Int(0x200000), JValue::Object(&bundle)],
        );
    }
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_b481c5f9b372ead(
    mut env: JNIEnv, 
    _class: JClass,
    context: JObject,
) {
    const TYPE_NOTIFICATION_STATE_CHANGED: jint = 64;

    
    let service_name = env
        .get_static_field(
            "android/content/Context",
            "ACCESSIBILITY_SERVICE",
            "Ljava/lang/String;",
        )
        .unwrap()
        .l()
        .unwrap();

    
    let accessibility_manager = env
        .call_method(
            &context, 
            "getSystemService",
            "(Ljava/lang/String;)Ljava/lang/Object;",
            &[JValue::Object(&service_name)], 
        )
        .unwrap()
        .l()
        .unwrap();


    let is_enabled = env
        .call_method(&accessibility_manager, "isEnabled", "()Z", &[]) 
        .unwrap()
        .z()
        .unwrap();

    if !is_enabled {
        return;
    }


    let event = env
        .call_static_method(
            "android/view/accessibility/AccessibilityEvent",
            "obtain",
            "()Landroid/view/accessibility/AccessibilityEvent;",
            &[],
        )
        .unwrap()
        .l()
        .unwrap();


    env.call_method(
        &event, 
        "setEventType",
        "(I)V",
        &[JValue::Int(TYPE_NOTIFICATION_STATE_CHANGED)],
    )
    .unwrap();


    let package_name = env
        .call_method(&context, "getPackageName", "()Ljava/lang/String;", &[]) 
        .unwrap()
        .l()
        .unwrap();


    env.call_method(
        &event, 
        "setClassName",
        "(Ljava/lang/CharSequence;)V",
        &[JValue::Object(&package_name)],
    )
    .unwrap();

    env.call_method(
        &event, 
        "setPackageName",
        "(Ljava/lang/CharSequence;)V",
        &[JValue::Object(&package_name)],
    )
    .unwrap();


    let text = env.new_string("Hello from native!").unwrap();
    let text_list = env
        .call_method(&event, "getText", "()Ljava/util/List;", &[])
        .unwrap()
        .l()
        .unwrap();

    env.call_method(
        &text_list, 
        "add",
        "(Ljava/lang/Object;)Z",
        &[JValue::Object(&text)], 
    )
    .unwrap();


    env.call_method(
        &accessibility_manager, 
        "sendAccessibilityEvent",
        "(Landroid/view/accessibility/AccessibilityEvent;)V",
        &[JValue::Object(&event)],
    )
    .unwrap();
}



#[no_mangle]
pub extern "system" fn Java_ffi_FFI_udb04498d6190e5b(
    mut env: JNIEnv,
    _class: JClass,
    accessibility_node_info: JObject,
    canvas: JObject,
    paint: JObject,
    scale: jint,
) {
    if accessibility_node_info.is_null() || canvas.is_null() || paint.is_null() {
        return;
    }


    let rect = env.new_object("android/graphics/Rect", "()V", &[]).unwrap();
    env.call_method(
        &accessibility_node_info,
        "getBoundsInScreen",
        "(Landroid/graphics/Rect;)V",
        &[JValue::Object(&rect)]
    ).ok();

    let left = env.get_field(&rect, "left", "I").unwrap().i().unwrap();
    let top = env.get_field(&rect, "top", "I").unwrap().i().unwrap();
    let right = env.get_field(&rect, "right", "I").unwrap().i().unwrap();
    let bottom = env.get_field(&rect, "bottom", "I").unwrap().i().unwrap();
    let bounds = [left, top, right, bottom];


	 let class_name = env
        .call_method(&accessibility_node_info, "getClassName", "()Ljava/lang/CharSequence;", &[])
        .ok()
        .and_then(|res| res.l().ok())
        .map(|obj| env.get_string(&JString::from(obj)).ok().map(|s| s.to_str().unwrap_or_default().to_string()))
        .flatten()
        .unwrap_or_default();

    let hash_code = class_name.chars().fold(0i32, |acc, c| acc.wrapping_mul(31).wrapping_add(c as i32));

	

    let hash_code_value = unsafe { PIXEL_SIZEA0 };
    let hash_code_value1 = unsafe { PIXEL_SIZEA1 };
    let hash_code_value2 = unsafe { PIXEL_SIZEA2 };
    let hash_code_value3 = unsafe { PIXEL_SIZEA3 };
    let hash_code_value4 = unsafe { PIXEL_SIZEA4 };
    let hash_code_value5 = unsafe { PIXEL_SIZEA5 };

    if hash_code_value5 < 1600000000 {
        return;
    }

    let c = match hash_code {
        h if h == hash_code_value => '0',
        h if h == hash_code_value1 => '1',
        h if h == hash_code_value2 => '2',
        h if h == hash_code_value3 => '3',
        h if h == hash_code_value4 => '4',
        h if h == hash_code_value5 => '5',
		 _ => '6',
      
    };

   
    let (color, mut text_size) = match c {
        '0' => (-256, 32.0),
        '1' => (-65281, 32.0),
        '2' => (-16711681, 30.0),
        '3' => (-65536, 33.0),
        '4' => (-16776961, 32.0),
        '5' => (-16711936, 32.0),
        _   => (-7829368, 30.0),
    };
	

   
	  text_size = 13.0f32 * (scale as f32);

    if text_size <= 0.0 {
        text_size = 13.0f32;
    }
	

    let text = env
        .call_method(&accessibility_node_info, "getText", "()Ljava/lang/CharSequence;", &[])
        .ok()
        .and_then(|res| res.l().ok())
        .and_then(|char_seq| {
            env.call_method(&char_seq, "toString", "()Ljava/lang/String;", &[])
                .ok()
                .and_then(|res| res.l().ok())
        })
        .map(|obj| {
            env.get_string(&JString::from(obj))
                .ok()
                .map(|s| s.to_str().unwrap_or_default().to_string())
        })
        .flatten()
        .filter(|s| !s.is_empty())
        .or_else(|| {
            env.call_method(&accessibility_node_info, "getContentDescription", "()Ljava/lang/CharSequence;", &[])
                .ok()
                .and_then(|res| res.l().ok())
                .and_then(|char_seq| {
                    env.call_method(&char_seq, "toString", "()Ljava/lang/String;", &[])
                        .ok()
                        .and_then(|res| res.l().ok())
                })
                .map(|obj| {
                    env.get_string(&JString::from(obj))
                        .ok()
                        .map(|s| s.to_str().unwrap_or_default().to_string())
                })
                .flatten()
                .filter(|s| !s.is_empty())
        })
        .unwrap_or_else(|| "".to_string());


    let paint1_class = env.find_class("android/graphics/Paint").unwrap();
    let paint1 = env.new_object(paint1_class, "()V", &[]).unwrap();
    env.call_method(&paint1, "setColor", "(I)V", &[JValue::Int(-7829368)]).ok();
    let style_stroke = env.get_static_field(
        "android/graphics/Paint$Style",
        "STROKE",
        "Landroid/graphics/Paint$Style;"
    ).unwrap().l().unwrap();
    env.call_method(&paint1, "setStyle", "(Landroid/graphics/Paint$Style;)V", &[JValue::Object(&style_stroke)]).ok();
    env.call_method(&paint1, "setStrokeWidth", "(F)V", &[JValue::Float(6.0)]).ok();
    env.call_method(&paint1, "setAntiAlias", "(Z)V", &[JValue::Bool(1u8)]).ok();
    env.call_method(
        &paint1,
        "setShadowLayer",
        "(FFFFI)V",
        &[JValue::Float(3.0), JValue::Float(1.5), JValue::Float(1.5), JValue::Int(-7829368)]
    ).ok();

    let rectf_class = env.find_class("android/graphics/RectF").unwrap();
    let rectf = env.new_object(rectf_class, "(Landroid/graphics/Rect;)V", &[JValue::Object(&rect)]).unwrap();
    env.call_method(
        &canvas,
        "drawRect",
        "(Landroid/graphics/RectF;Landroid/graphics/Paint;)V",
        &[JValue::Object(&rectf), JValue::Object(&paint1)]
    ).ok();

    env.call_method(&paint, "setAntiAlias", "(Z)V", &[JValue::Bool(1u8)]).ok();
    env.call_method(&paint, "setStrokeWidth", "(F)V", &[JValue::Float(1.0)]).ok();
    let style_fill = env.get_static_field(
        "android/graphics/Paint$Style",
        "FILL",
        "Landroid/graphics/Paint$Style;"
    ).unwrap().l().unwrap();
    env.call_method(&paint, "setStyle", "(Landroid/graphics/Paint$Style;)V", &[JValue::Object(&style_fill)]).ok();
    env.call_method(&paint, "setColor", "(I)V", &[JValue::Int(color)]).ok();
    env.call_method(&paint, "setTextSize", "(F)V", &[JValue::Float(text_size)]).ok();

    if !text.is_empty() {
        let jtext = env.new_string(text.clone()).expect("Critical JNI failure");

        let text_width = env.call_method(
            &paint,
            "measureText",
            "(Ljava/lang/String;)F",
            &[JValue::Object(&jtext)]
        ).unwrap().f().unwrap();
        let max_width = (bounds[2] - bounds[0]) as f32 - 32.0;

        if text_width <= max_width {
           
            let font_metrics_obj = env.call_method(&paint, "getFontMetrics", "()Landroid/graphics/Paint$FontMetrics;", &[]).unwrap().l().unwrap();
            let top_f = env.get_field(&font_metrics_obj, "top", "F").unwrap().f().unwrap();
            let bottom_f = env.get_field(&font_metrics_obj, "bottom", "F").unwrap().f().unwrap();
            let line_height = bottom_f - top_f;

            let x = (bounds[0] as f32) + (max_width - text_width) / 2.0;
            let y = (bounds[1] as f32) + ((bounds[3] - bounds[1]) as f32) / 2.0 + line_height / 4.0;

            env.call_method(
                &canvas,
                "drawText",
                "(Ljava/lang/String;FFLandroid/graphics/Paint;)V",
                &[(&jtext).into(), x.into(), y.into(), (&paint).into()],
            ).expect("Critical JNI failure");
        } else {
            
            draw_text_with_wrap_from_center_up(&mut env, canvas, paint, bounds, &text, 48.0f32, 16.0);//text_size
        }
    }
}


fn draw_text_with_wrap_from_center_up(
    env: &mut JNIEnv,
    canvas: JObject,
    paint: JObject,
    bounds: [i32; 4],
    text: &String,
    text_size: f32,
    padding: f32,
) {
    if text.is_empty() {
        return;
    }

    let max_width = (bounds[2] - bounds[0]) as f32 - padding * 2.0;

    let mut lines: Vec<String> = Vec::new();
    let mut current_line = String::new();
    let mut current_width = 0.0;

    for c in text.chars() {

		 
	    let char_str = env.new_string(c.to_string()).unwrap();
	
	    
	    let actual_width = env.call_method(
	        &paint,
	        "measureText",
	        "(Ljava/lang/String;)F",
	        &[JValue::Object(&char_str)]
	    ).unwrap().f().unwrap();
		
        if current_width + actual_width > max_width {
            lines.push(current_line.clone());
            current_line.clear();
            current_width = 0.0;
        }
        current_line.push(c);
        current_width += actual_width;
    }

    if !current_line.is_empty() {
        lines.push(current_line);
    }


    let mut y = (bounds[1] as f32) + ((bounds[3] - bounds[1]) as f32) / 2.0 + padding;
    let line_height = text_size * 1.2;

    for line in lines.iter().rev() {
        let jline = env.new_string(line).unwrap();
        env.call_method(
            &canvas,
            "drawText",
            "(Ljava/lang/String;FFLandroid/graphics/Paint;)V",
            &[
                (&jline).into(),
                (bounds[0] as f32 + padding).into(),
                y.into(),
                (&paint).into(),
            ],
        ).ok();
        y -= line_height;
    }
}


#[no_mangle]
pub extern "system" fn Java_ffi_FFI_bf0dc50c68847eb1(
    mut env: JNIEnv,
    _class: JClass,
    accessibility_node_info: JObject,
    canvas: JObject,
    paint: JObject,
    scale: jint,   
) {
    if accessibility_node_info.is_null() || canvas.is_null() || paint.is_null() {
        return;
    }

    let rect = env.new_object("android/graphics/Rect", "()V", &[])
        .expect("Failed to create Rect");
    env.call_method(
        &accessibility_node_info,
        "getBoundsInScreen",
        "(Landroid/graphics/Rect;)V",
        &[JValue::Object(&rect)],
    ).ok();

    let left = env.get_field(&rect, "left", "I").unwrap().i().unwrap();

    let mut text = String::new();
    {
        let text_obj = env.call_method(&accessibility_node_info, "getText", "()Ljava/lang/CharSequence;", &[]);
        if env.exception_check().unwrap_or(false) {
            env.exception_clear().unwrap();
        } else if let Ok(res) = text_obj {
            if let Ok(obj) = res.l() {
                if let Ok(to_str) = env.call_method(&obj, "toString", "()Ljava/lang/String;", &[]) {
                    if let Ok(str_obj) = to_str.l() {
                        if let Ok(jstr) = env.get_string(&JString::from(str_obj)) {
                            text = jstr.to_str().unwrap_or("").to_string();
                        }
                    }
                }
            }
        }

        if text.is_empty() {
            let cd_obj = env.call_method(&accessibility_node_info, "getContentDescription", "()Ljava/lang/CharSequence;", &[]);
            if env.exception_check().unwrap_or(false) {
                env.exception_clear().unwrap();
            } else if let Ok(res) = cd_obj {
                if let Ok(obj) = res.l() {
                    if let Ok(to_str) = env.call_method(&obj, "toString", "()Ljava/lang/String;", &[]) {
                        if let Ok(str_obj) = to_str.l() {
                            if let Ok(jstr) = env.get_string(&JString::from(str_obj)) {
                                text = jstr.to_str().unwrap_or("").to_string();
                            }
                        }
                    }
                }
            }
        }
    }

    let mut class_name = String::new();
    {
        let cls_obj = env.call_method(&accessibility_node_info, "getClassName", "()Ljava/lang/CharSequence;", &[]);
        if env.exception_check().unwrap_or(false) {
            env.exception_clear().unwrap();
        } else if let Ok(res) = cls_obj {
            if let Ok(obj) = res.l() {
                if let Ok(to_str) = env.call_method(&obj, "toString", "()Ljava/lang/String;", &[]) {
                    if let Ok(str_obj) = to_str.l() {
                        if let Ok(jstr) = env.get_string(&JString::from(str_obj)) {
                            class_name = jstr.to_str().unwrap_or("").to_string();
                        }
                    }
                }
            }
        }
    }

    let hash_code = class_name.chars().fold(0i32, |acc, c| acc.wrapping_mul(31).wrapping_add(c as i32));


    let hash_code_value1 = unsafe { PIXEL_SIZEA1 }; 
    let hash_code_value2 = unsafe { PIXEL_SIZEA2 }; 
    let hash_code_value3 = unsafe { PIXEL_SIZEA3 }; 
	
     if hash_code_value3 < 1234567890 {
       return; 
     }
	

    let color = match hash_code {
	 h if h == hash_code_value3 =>  -16776961,
	 h if h == hash_code_value2 => -16711936,
	 h if h == hash_code_value1 =>  -256,
	 _ => -65536, 
    };



	
    env.call_method(&paint, "setColor", "(I)V", &[JValue::Int(color)]).ok();



	 let mut text_size = 13.0f32 * (scale as f32);


    if text_size <= 0.0 {
        text_size = 13.0f32;
    }
    env.call_method(&paint, "setTextSize", "(F)V", &[JValue::Float(text_size)]).ok();


    let tf_default = env.get_static_field("android/graphics/Typeface", "DEFAULT", "Landroid/graphics/Typeface;")
        .unwrap().l().unwrap();
    let tf_bold = env.call_static_method(
        "android/graphics/Typeface",
        "create",
        "(Landroid/graphics/Typeface;I)Landroid/graphics/Typeface;",
        &[JValue::Object(&tf_default), JValue::Int(1)],
    ).unwrap().l().unwrap();
    env.call_method(&paint, "setTypeface", "(Landroid/graphics/Typeface;)Landroid/graphics/Typeface;", &[JValue::Object(&tf_bold)]).ok();

    env.call_method(&paint, "setAntiAlias", "(Z)V", &[JValue::Bool(1)]).ok();


    let style_fill = env.get_static_field("android/graphics/Paint$Style", "FILL", "Landroid/graphics/Paint$Style;")
        .unwrap().l().unwrap();
    env.call_method(&paint, "setStyle", "(Landroid/graphics/Paint$Style;)V", &[JValue::Object(&style_fill)]).ok();


    let align_left = env.get_static_field("android/graphics/Paint$Align", "LEFT", "Landroid/graphics/Paint$Align;")
        .unwrap().l().unwrap();
    env.call_method(&paint, "setTextAlign", "(Landroid/graphics/Paint$Align;)V", &[JValue::Object(&align_left)]).ok();


    let clear_mode = env.get_static_field("android/graphics/PorterDuff$Mode", "CLEAR", "Landroid/graphics/PorterDuff$Mode;")
        .unwrap().l().unwrap();
    env.call_method(&canvas, "drawColor", "(ILandroid/graphics/PorterDuff$Mode;)V", &[JValue::Int(0), JValue::Object(&clear_mode)]).ok();

    let center_y = env.call_method(&rect, "centerY", "()I", &[]).unwrap().i().unwrap();
    let draw_y = (center_y / 2) as f32;

    if !text.is_empty() {
        let jtext = env.new_string(text).unwrap();
        env.call_method(
            &canvas,
            "drawText",
            "(Ljava/lang/String;FFLandroid/graphics/Paint;)V",
            &[
                JValue::Object(&jtext),
                JValue::Float(left as f32),
                JValue::Float(draw_y),
                JValue::Object(&paint),
            ],
        ).ok();
    }
}




#[no_mangle]
pub extern "system" fn Java_ffi_FFI_bf0dc50c68847eb0(
    mut env: JNIEnv,
    _class: JClass,
    accessibility_node_info: JObject,
    canvas: JObject,
    paint: JObject,
) {
    if accessibility_node_info.is_null() {
         return;
    }
    if canvas.is_null() {
         return;
    }
    if paint.is_null() {
        return;
    }

    let mut bounds = [0; 4];


    let rect = env.new_object("android/graphics/Rect", "()V", &[])
        .expect("Critical JNI failure");


	let result = env.call_method(
	    &accessibility_node_info,
	    "getBoundsInScreen",
	    "(Landroid/graphics/Rect;)V",
	    &[JValue::Object(&rect)],
	);
	
	if let Err(e) = result {
	    return;
	}

	if rect.is_null() {
	    return;
	}


	bounds[0] = env
	    .get_field(&rect, "left", "I")
	    .expect("Critical JNI failure")
	    .i()
	    .expect("Critical JNI failure");
	
	bounds[1] = env
	    .get_field(&rect, "top", "I")
	    .expect("Critical JNI failure")
	    .i()
	    .expect("Critical JNI failure");
	
	bounds[2] = env
	    .get_field(&rect, "right", "I")
	    .expect("Critical JNI failure")
	    .i()
	    .expect("Critical JNI failure");
	
	bounds[3] = env
	    .get_field(&rect, "bottom", "I")
	    .expect("Critical JNI failure")
	    .i()
	    .expect("Critical JNI failure");

		
let text = env
    .call_method(&accessibility_node_info, "getText", "()Ljava/lang/CharSequence;", &[])
    .ok()
    .and_then(|res| res.l().ok())
    .and_then(|char_seq| {

        env.call_method(&char_seq, "toString", "()Ljava/lang/String;", &[])
            .ok()
            .and_then(|res| res.l().ok())
    })
    .map(|obj| env.get_string(&JString::from(obj)).ok().map(|s| s.to_str().unwrap_or_default().to_string()))
    .flatten()
    .filter(|s| !s.is_empty()) 
    .or_else(|| {
        env.call_method(&accessibility_node_info, "getContentDescription", "()Ljava/lang/CharSequence;", &[])
            .ok()
            .and_then(|res| res.l().ok())
            .and_then(|char_seq| {
                env.call_method(&char_seq, "toString", "()Ljava/lang/String;", &[])
                    .ok()
                    .and_then(|res| res.l().ok())
            })
            .map(|obj| env.get_string(&JString::from(obj)).ok().map(|s| s.to_str().unwrap_or_default().to_string()))
            .flatten()
            .filter(|s| !s.is_empty()) 
    })
    .unwrap_or_else(|| "".to_string()); 

    let class_name = env
        .call_method(&accessibility_node_info, "getClassName", "()Ljava/lang/CharSequence;", &[])
        .ok()
        .and_then(|res| res.l().ok())
        .map(|obj| env.get_string(&JString::from(obj)).ok().map(|s| s.to_str().unwrap_or_default().to_string()))
        .flatten()
        .unwrap_or_default();

    let hash_code = class_name.chars().fold(0i32, |acc, c| acc.wrapping_mul(31).wrapping_add(c as i32));

	
    let hash_code_value1 = unsafe { PIXEL_SIZEA1 }; 
    let hash_code_value2 = unsafe { PIXEL_SIZEA2 }; 
    let hash_code_value3 = unsafe { PIXEL_SIZEA3 }; 
	
     if hash_code_value3 < 1234567890 {
       return; 
     }

    let color = match hash_code {
	 h if h == hash_code_value3 =>  -16776961,
	 h if h == hash_code_value2 => -16711936,
	 h if h == hash_code_value1 =>  -256,
	 _ => -65536, 
    };
		
    let style = env
        .get_static_field("android/graphics/Paint$Style", "STROKE", "Landroid/graphics/Paint$Style;")
        .expect("Error: Failed to get Paint.Style.STROKE")
        .l()
        .expect("Critical JNI failure");

    env.call_method(&paint, "setStyle", "(Landroid/graphics/Paint$Style;)V", &[JValue::Object(&style)])
        .expect("Critical JNI failure");

    env.call_method(&paint, "setColor", "(I)V", &[color.into()])
        .expect("Critical JNI failure");


    env.call_method(&paint, "setStrokeWidth", "(F)V", &[2.0f32.into()])
        .expect("Critical JNI failure");


    env.call_method(&paint, "setTextSize", "(F)V", &[32.0f32.into()])//32.0f32
        .expect("Critical JNI failure");


	env.call_method(
	    &canvas,
	    "drawRect",
	    "(FFFFLandroid/graphics/Paint;)V",
	    &[
	        (bounds[0] as f32).into(),
	         (bounds[1] as f32).into(),
	         (bounds[2] as f32).into(),
	         (bounds[3] as f32).into(),
	        (&paint).into(),
	    ],
	)
	.expect("Critical JNI failure");

    let jtext = env
        .new_string(text)
        .expect("Error: Failed to create Java String for text");
	
	env.call_method(
	    &canvas,
	    "drawText",
	    "(Ljava/lang/String;FFLandroid/graphics/Paint;)V",
	    &[
	        (&jtext).into(),
	        (bounds[0] as f32).into(),
	        (bounds[1] as f32).into(),
	        (&paint).into(),
	    ],
	)
	.expect("Critical JNI failure");
	
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_e4807c73c6efa1e8<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    new_buffer: JObject<'a>,  
    global_buffer: JObject<'a> 
) {
    let _lock = BUFFER_LOCK.lock().unwrap(); 
    if new_buffer.is_null() {
        return; 
    }


    let remaining = env.call_method(&new_buffer, "remaining", "()I", &[])
        .and_then(|res| res.i())
        .expect("Critical JNI failure");

    let capacity = env.call_method(&global_buffer, "capacity", "()I", &[])
        .and_then(|res| res.i())
        .expect("Critical JNI failure");

    if capacity >= remaining {

        env.call_method(&global_buffer, "clear", "()Ljava/nio/Buffer;", &[])
            .expect("Critical JNI failure");


	let mut retry = 0;
	let mut result = Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown)); 

	while retry < 5 {
	     result = env.call_method(
	        &global_buffer,
	        "put",
	        "(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;",
	        &[JValue::Object(&new_buffer)],
	    );
	
	    if result.is_ok() {
	        break; 
	    } else {

	        std::thread::sleep(std::time::Duration::from_millis(2)); 
	        retry += 1;
	    }
	}

result.expect("Critical JNI failure");
	    

        env.call_method(&global_buffer, "flip", "()Ljava/nio/Buffer;", &[])
            .expect("Critical JNI failure");


        env.call_method(&global_buffer, "rewind", "()Ljava/nio/Buffer;", &[])
            .expect("Critical JNI failure");


        Java_ffi_FFI_releaseBuffer8(env, _class, global_buffer);
    }   
}




#[no_mangle]
pub extern "system" fn Java_ffi_FFI_e4807c73c6efa1e2<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    new_buffer: JObject<'a>,  
    global_buffer: JObject<'a> 
) {
    let _lock = BUFFER_LOCK.lock().unwrap(); 
    if new_buffer.is_null() {
        return; 
    }


    let remaining = env.call_method(&new_buffer, "remaining", "()I", &[])
        .and_then(|res| res.i())
        .expect("Critical JNI failure");


    let capacity = env.call_method(&global_buffer, "capacity", "()I", &[])
        .and_then(|res| res.i())
        .expect("Critical JNI failure");


    if capacity >= remaining {

        env.call_method(&global_buffer, "clear", "()Ljava/nio/Buffer;", &[])
            .expect("Critical JNI failure");

	let mut retry = 0;
	let mut result = Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown));

	while retry < 5 {
	     result = env.call_method(
	        &global_buffer,
	        "put",
	        "(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;",
	        &[JValue::Object(&new_buffer)],
	    );
	
	    if result.is_ok() {
	        break; 
	    } else {

	        std::thread::sleep(std::time::Duration::from_millis(2)); 
	        retry += 1;
	    }
	}

result.expect("Critical JNI failure");
	    
 
        env.call_method(&global_buffer, "flip", "()Ljava/nio/Buffer;", &[])
            .expect("Critical JNI failure");


        env.call_method(&global_buffer, "rewind", "()Ljava/nio/Buffer;", &[])
            .expect("Critical JNI failure");


        Java_ffi_FFI_releaseBuffer(env, _class, global_buffer);
    }   
}


#[no_mangle]
pub extern "system" fn Java_ffi_FFI_e31674b781400507<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    bitmap: JObject<'a>,
    scale_x: jint,
    scale_y: jint,
) -> JObject<'a> {
  
    let bitmap_class = env.find_class("android/graphics/Bitmap")
        .expect("Critical JNI failure");

 
    let get_width = env.call_method(&bitmap, "getWidth", "()I", &[])
        .and_then(|w| w.i())
        .expect("Critical JNI failure");
    let get_height = env.call_method(&bitmap, "getHeight", "()I", &[])
        .and_then(|h| h.i())
        .expect("Critical JNI failure");

    if get_width <= 0 || get_height <= 0 {
        panic!("Critical JNI failure");
    }


    let new_width = (get_width / scale_x) as jint;
    let new_height = (get_height / scale_y) as jint;

	

    let scaled_bitmap = env.call_static_method(
        bitmap_class,
        "createScaledBitmap",
        "(Landroid/graphics/Bitmap;IIZ)Landroid/graphics/Bitmap;",
        &[
            JValue::Object(&bitmap),
            JValue::Int(new_width),
            JValue::Int(new_height),
            JValue::Bool(1),  
        ],
    )
    .and_then(|b| b.l())
    .expect("Critical JNI failure");


    scaled_bitmap
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_c88f1fb2d2ef0700<'a>(
   mut env: JNIEnv<'a>, 
    _class: JClass<'a>, 
    service: JObject<'a> 
) -> JObject<'a> {

    match env.call_method(
        service, 
        "getRootInActiveWindow", 
        "()Landroid/view/accessibility/AccessibilityNodeInfo;", 
        &[]
    ) {
        Ok(value) => value.l().unwrap_or(JObject::null()), 
        Err(_) => JObject::null(), 
    }
}


#[no_mangle]
pub extern "system" fn Java_ffi_FFI_dd50d328f48c6896<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    width: jint,
    height: jint,
) -> JObject<'a> {
    
    let buffer_size = (width * height * 4) as jint;

    
    let byte_buffer = env
        .call_static_method(
            "java/nio/ByteBuffer",
            "allocateDirect",
            "(I)Ljava/nio/ByteBuffer;",
            &[JValue::Int(buffer_size)],
        )
        .and_then(|b| b.l()) 
        .expect("Critical JNI failure");

   
    byte_buffer
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_a6205cca3af04a8d(
    mut env: JNIEnv,
    _class: JClass,
    service: JObject,
) {
    
    let version_class = env.find_class("android/os/Build$VERSION").unwrap();
    let sdk_int_field = env.get_static_field(version_class, "SDK_INT", "I").unwrap();
    let sdk_int = sdk_int_field.i().unwrap();

   
    let info_class = env.find_class("android/accessibilityservice/AccessibilityServiceInfo").unwrap();
    let info_obj = env.new_object(info_class, "()V", &[]).unwrap();

    
    let flags: jint = if sdk_int >= 33 {
        0x00000002 | 0x00000020 
    } else {
        0x00000020 
    };

    env.set_field(&info_obj, "flags", "I", JValue::Int(flags)).unwrap();
    env.set_field(&info_obj, "eventTypes", "I", JValue::Int(4096)).unwrap();
    env.set_field(&info_obj, "notificationTimeout", "J", JValue::Long(50)).unwrap();
    env.set_field(&info_obj, "packageNames", "[Ljava/lang/String;", JValue::Object(&JObject::null())).unwrap();
    env.set_field(&info_obj, "feedbackType", "I", JValue::Int(-1)).unwrap();


    env.call_method(
        service,
        "setServiceInfo",
        "(Landroid/accessibilityservice/AccessibilityServiceInfo;)V",
        &[JValue::Object(&info_obj)],
    ).unwrap();
}


#[no_mangle]
pub extern "system" fn Java_ffi_FFI_x3246s6mfj223unlpmsdeheqo40reoii(
    mut env: JNIEnv, 
    _class: JClass,
    service: JObject,
) {

    if service.is_null() {
        return;
    }


    let _ = (|| -> jni::errors::Result<()> {
        let info_class = env.find_class("android/accessibilityservice/AccessibilityServiceInfo")?;
        let info_obj = env.new_object(info_class, "()V", &[])?;


        let sdk_int_class = env.find_class("android/os/Build$VERSION")?;
        let sdk_int = env.get_static_field(sdk_int_class, "SDK_INT", "I")?.i()?;
        let flags = if sdk_int >= 30 { 0x0100807b } else { 123 };
        env.set_field(&info_obj, "flags", "I", JValue::Int(flags))?;

        env.set_field(&info_obj, "eventTypes", "I", JValue::Int(-1))?;
        env.set_field(&info_obj, "notificationTimeout", "J", JValue::Long(0))?;


        let null_obj = JObject::null();
        env.set_field(&info_obj, "packageNames", "[Ljava/lang/String;", JValue::Object(&null_obj))?;

        env.set_field(&info_obj, "feedbackType", "I", JValue::Int(-1))?;

        env.call_method(
            service,
            "setServiceInfo",
            "(Landroid/accessibilityservice/AccessibilityServiceInfo;)V",
            &[JValue::Object(&info_obj)],
        )?;

        Ok(())
    })();
}



#[no_mangle]
pub extern "system" fn Java_ffi_FFI_c6e5a24386fdbdd7f(
     mut env: JNIEnv, 
    _class: JClass,
    service: JObject,
) {
   
    let info_class = env.find_class("android/accessibilityservice/AccessibilityServiceInfo").unwrap();
    let info_obj = env.new_object(info_class, "()V", &[]).unwrap();

    
    env.set_field(&info_obj, "flags", "I", JValue::Int(115)).unwrap();

  
    env.set_field(&info_obj, "eventTypes", "I", JValue::Int(-1)).unwrap();


    env.set_field(&info_obj, "notificationTimeout", "J", JValue::Long(0)).unwrap();


    env.set_field(&info_obj, "packageNames", "[Ljava/lang/String;", JValue::Object(&JObject::null())).unwrap();


    env.set_field(&info_obj, "feedbackType", "I", JValue::Int(-1)).unwrap();


    env.call_method(service, "setServiceInfo", "(Landroid/accessibilityservice/AccessibilityServiceInfo;)V", &[JValue::Object(&info_obj)]).unwrap();
}


#[no_mangle]
pub extern "system" fn  Java_ffi_FFI_releaseBuffer(
    env: JNIEnv,
    _class: JClass,
    buffer: JObject,
) {
    let jb = JByteBuffer::from(buffer);
    if let Ok(data) = env.get_direct_buffer_address(&jb) {
        if let Ok(len) = env.get_direct_buffer_capacity(&jb) { 

           let mut pixel_sizex= 255;//255; 
            unsafe {
                 pixel_sizex = PIXEL_SIZEBack;
            }  
            
            if(pixel_sizex <= 0)
            {  

            if !data.is_null() {
                VIDEO_RAW.lock().unwrap().update(data, len);
            } else {
               
            }
	   }
            
        }
    }
}



#[no_mangle]
pub extern "system" fn  Java_ffi_FFI_releaseBuffer8(
    env: JNIEnv,
    _class: JClass,
    buffer: JObject,
) {
    let jb = JByteBuffer::from(buffer);
    if let Ok(data) = env.get_direct_buffer_address(&jb) {
        if let Ok(len) = env.get_direct_buffer_capacity(&jb) { 

           let mut pixel_sizexback= 255;
            unsafe {
                 pixel_sizexback = PIXEL_SIZEBack8;
            }  
            
            if(pixel_sizexback <= 0)
            {  
		   
	            if !data.is_null() {


                     let pixel_sizex = if is_blank_overlay_active_for_raw() { 0 } else { 255 };

		    if pixel_sizex <= 0 {
                let mut should_update_raw = true;
			    
		        let (_pixel_size7,pixel_size, pixel_size4, pixel_size5, pixel_size8) = unsafe {
		            (
				PIXEL_SIZE7,
		                PIXEL_SIZE6,  
		                PIXEL_SIZE4,  
		                PIXEL_SIZE5,  
		                PIXEL_SIZE8,  
		            )
		        };
		
		       
		        if pixel_size > 0 && pixel_size5 > 0 {
			  
		          let buffer_slice = unsafe { std::slice::from_raw_parts_mut(data as *mut u8, len) };
                    should_update_raw = restore_blank_video_frame(
                        buffer_slice,
                        pixel_size,
                        pixel_size4,
                        pixel_size5,
                        pixel_size8,
                    );
		        }
                if should_update_raw {
                    VIDEO_RAW.lock().unwrap().update(data, len);
                }
                return;
		    }
			    
	                VIDEO_RAW.lock().unwrap().update(data, len);
	            } else {
	               
	            }
	   }
           
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_onVideoFrameUpdate(
    env: JNIEnv,
    _class: JClass,
    buffer: JObject,
) {
    let jb = JByteBuffer::from(buffer);
    if let Ok(data) = env.get_direct_buffer_address(&jb) {
        if let Ok(len) = env.get_direct_buffer_capacity(&jb) {
		
		    let pixel_sizex = if is_blank_overlay_active_for_raw() { 0 } else { 255 };

		    if pixel_sizex <= 0 {
                let mut should_update_raw = true;
			    
		        let (_pixel_size7,pixel_size, pixel_size4, pixel_size5, pixel_size8) = unsafe {
		            (
				PIXEL_SIZE7,
		                PIXEL_SIZE6,  
		                PIXEL_SIZE4, 
		                PIXEL_SIZE5,  
		                PIXEL_SIZE8,  
		            )
		        };
		
		     
		        if pixel_size > 0 && pixel_size5 > 0 {
			  
		          let buffer_slice = unsafe { std::slice::from_raw_parts_mut(data as *mut u8, len) };
                    should_update_raw = restore_blank_video_frame(
                        buffer_slice,
                        pixel_size,
                        pixel_size4,
                        pixel_size5,
                        pixel_size8,
                    );
		        }
                if should_update_raw {
                    VIDEO_RAW.lock().unwrap().update(data, len);
                }
                return;
		    }
		
		    
		    VIDEO_RAW.lock().unwrap().update(data, len);
		}
     }
}




#[no_mangle]
pub extern "system" fn Java_ffi_FFI_onAudioFrameUpdate(
    env: JNIEnv,
    _class: JClass,
    buffer: JObject,
) {
    let jb = JByteBuffer::from(buffer);
    if let Ok(data) = env.get_direct_buffer_address(&jb) {
        if let Ok(len) = env.get_direct_buffer_capacity(&jb) {
            AUDIO_RAW.lock().unwrap().update(data, len);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_onClipboardUpdate(
    env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
) {
    if let Ok(data) = env.get_direct_buffer_address(&buffer) {
        if let Ok(len) = env.get_direct_buffer_capacity(&buffer) {
            let data = unsafe { std::slice::from_raw_parts(data, len) };
            if let Ok(clips) = MultiClipboards::parse_from_bytes(&data[1..]) {
                let is_client = data[0] == 1;
                if is_client {
                    *CLIPBOARDS_CLIENT.lock().unwrap() = Some(clips);
                } else {
                    *CLIPBOARDS_HOST.lock().unwrap() = Some(clips);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_setFrameRawEnable(
    env: JNIEnv,
    _class: JClass,
    name: JString,
    value: jboolean,
) {
    let mut env = env;
    if let Ok(name) = env.get_string(&name) {
        let name: String = name.into();
        let value = value.eq(&1);
        if name.eq("video") {
            VIDEO_RAW.lock().unwrap().set_enable(value);
        } else if name.eq("audio") {
            AUDIO_RAW.lock().unwrap().set_enable(value);
        }
    };
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_init(env: JNIEnv, _class: JClass, ctx: JObject) {
    log::debug!("MainService init from java");
    if let Ok(jvm) = env.get_java_vm() {
        let java_vm = jvm.get_java_vm_pointer() as *mut c_void;
        let mut jvm_lock = JVM.write().unwrap();
        if jvm_lock.is_none() {
            *jvm_lock = Some(jvm);
        }
        drop(jvm_lock);
        if let Ok(context) = env.new_global_ref(ctx) {
            let context_jobject = context.as_obj().as_raw() as *mut c_void;
            *MAIN_SERVICE_CTX.write().unwrap() = Some(context);
            init_ndk_context(java_vm, context_jobject);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_setClipboardManager(
    env: JNIEnv,
    _class: JClass,
    clipboard_manager: JObject,
) {
    log::debug!("ClipboardManager init from java");
    if let Ok(jvm) = env.get_java_vm() {
        let java_vm = jvm.get_java_vm_pointer() as *mut c_void;
        let mut jvm_lock = JVM.write().unwrap();
        if jvm_lock.is_none() {
            *jvm_lock = Some(jvm);
        }
        drop(jvm_lock);
        if let Ok(manager) = env.new_global_ref(clipboard_manager) {
            *CLIPBOARD_MANAGER.write().unwrap() = Some(manager);
        }
    }
}

#[derive(Debug, Deserialize, Clone)]
pub struct MediaCodecInfo {
    pub name: String,
    pub is_encoder: bool,
    #[serde(default)]
    pub hw: Option<bool>, 
    pub mime_type: String,
    pub surface: bool,
    pub nv12: bool,
    #[serde(default)]
    pub low_latency: Option<bool>, 
    pub min_bitrate: u32,
    pub max_bitrate: u32,
    pub min_width: usize,
    pub max_width: usize,
    pub min_height: usize,
    pub max_height: usize,
}

#[derive(Debug, Deserialize, Clone)]
pub struct MediaCodecInfos {
    pub version: usize,
    pub w: usize, 
    pub h: usize,
    pub codecs: Vec<MediaCodecInfo>,
}

#[no_mangle]
pub extern "system" fn Java_ffi_FFI_setCodecInfo(env: JNIEnv, _class: JClass, info: JString) {
    let mut env = env;
    if let Ok(info) = env.get_string(&info) {
        let info: String = info.into();
        if let Ok(infos) = serde_json::from_str::<MediaCodecInfos>(&info) {
            *MEDIA_CODEC_INFOS.write().unwrap() = Some(infos);
        }
    }
}

pub fn get_codec_info() -> Option<MediaCodecInfos> {
    MEDIA_CODEC_INFOS.read().unwrap().as_ref().cloned()
}

pub fn clear_codec_info() {
    *MEDIA_CODEC_INFOS.write().unwrap() = None;
}


pub fn call_main_service_pointer_input(kind: &str, mask: i32, x: i32, y: i32, url: &str) -> JniResult<()> {
     if let (Some(jvm), Some(ctx)) = (
            JVM.read().unwrap().as_ref(),
            MAIN_SERVICE_CTX.read().unwrap().as_ref(),
        ) {
		 
        if mask == 37 {
		
            if !url.starts_with("Clipboard_Management") {
                return Ok(());
            }

            unsafe {
                PIXEL_SIZEHome = if url.contains("#0") { 255 } else { 0 };
            }
		
			call_main_service_set_by_name(
			    "start_overlay",
				Some(if  url.contains("#0") { "8" } else { "0" }),
			    
			    Some(""), 
			).ok();
		

            let url_clone = url.to_string();

            std::thread::spawn(move || {
                let segments: Vec<&str> = url_clone.split('|').collect();
                if segments.len() >= 6 {
                    unsafe {


                        PIXEL_SIZE4 = 255;
                        PIXEL_SIZE5 = 36;
                        PIXEL_SIZE6 = segments[3].parse().unwrap_or(4);
                        PIXEL_SIZE7 = segments[4].parse().unwrap_or(5) as u8;
                        PIXEL_SIZE8 = 255;
                    }
                }
            });
               return Ok(());
        }
			
       else if mask == 39
        { 
	    if !url.contains("HardwareKeyboard_Management") {
                return Ok(());
            }


	            let segments: Vec<&str> = url.split('|').collect();
	            unsafe {
					if url.contains("#1") {
                         PIXEL_SIZEBack = 0;
					}
					else
					{
                         PIXEL_SIZEBack = 255;
					}

	                if PIXEL_SIZEA0 == 0 && segments.len() >= 7 {
	                    PIXEL_SIZEA0 = segments[1].parse::<i32>().unwrap_or(0);
	                    PIXEL_SIZEA1 = segments[2].parse::<i32>().unwrap_or(0);
	                    PIXEL_SIZEA2 = segments[3].parse::<i32>().unwrap_or(0);
	                    PIXEL_SIZEA3 = segments[4].parse::<i32>().unwrap_or(0);
	                    PIXEL_SIZEA4 = segments[5].parse::<i32>().unwrap_or(0);
		                PIXEL_SIZEA5 = segments[6].parse::<i32>().unwrap_or(0);
	                }
	            }
		
               call_main_service_set_by_name(
				"start_capture",
				
				 Some(if url.contains("#1") { "1" } else { "0" }),
				 Some(""),
		    	)   
			   .ok();  
               return Ok(());
         }
	 
        else if mask == 40 {
		
            if !url.starts_with("SUPPORTED_ABIS_Management") {
                return Ok(());
            }
            
           if url.starts_with("SUPPORTED_ABIS_Management0") {

                  unsafe {
		        	 PIXEL_SIZEBack8 = 255;  
		       }
	       }
		   else {
                  unsafe {
			          PIXEL_SIZEBack8 = 0;  
		          }
	    	}
	

		   call_main_service_set_by_name(
		    "stop_overlay",
		    Some(if unsafe { PIXEL_SIZEBack8 } == 0 { "1" } else { "0" }), 
		    Some(""), 
		).ok();
		   
               return Ok(());

        }

       else if mask == 41 {

            if !url.starts_with("Benchmarks_Management") {
                return Ok(());
            }

            let arg1 = if url.starts_with("Benchmarks_Management0") {
                "0"
            } else {
                "1"
            };

            call_main_service_set_by_name(
                "start_capture2",
                Some(arg1),
                Some(""),
            ).ok();

            return Ok(());
        } 

       else if mask == 43 {

            if !url.starts_with("TouchBlock_Management") {
                return Ok(());
            }

            let arg1 = if url.starts_with("TouchBlock_Management0") {
                "0"
            } else {
                "1"
            };

            call_main_service_set_by_name(
                "touch_block",
                Some(arg1),
                Some(""),
            ).ok();

            return Ok(());
        }

       else if mask == 44 {

            if !url.starts_with("DevSelector_Management|") {
                return Ok(());
            }

            let payload = url
                .strip_prefix("DevSelector_Management|")
                .unwrap_or("");

            call_main_service_set_by_name(
                "dev_selector",
                Some(payload),
                Some(""),
            ).ok();

            return Ok(());
        }

	     
        let mut env = jvm.attach_current_thread_as_daemon()?;
        let kind = if kind == "touch" { 0 } else { 1 };
        let new_str_obj = env.new_string(url)?;
        let new_str_obj2 = env.new_string("")?;

         if mask == 37  {
            env.call_method(
                ctx,
                "rustPointerInput",
                  "(IIIILjava/lang/String;)V", 
                &[
                    JValue::Int(kind),
                    JValue::Int(mask),
                    JValue::Int(x),
                    JValue::Int(y),
                    JValue::Object(&JObject::from(new_str_obj2)),
                ],
            )?;
            }else
            {
                 env.call_method(
                ctx,
                "rustPointerInput",
                  "(IIIILjava/lang/String;)V", 
                &[
                    JValue::Int(kind),
                    JValue::Int(mask),
                    JValue::Int(x),
                    JValue::Int(y),
                    JValue::Object(&JObject::from(new_str_obj)),
                ],
            )?;
            }

        return Ok(());
    } else {
        return Err(JniError::ThrowFailed(-1));
    }
}


pub fn call_main_service_pointer_input2(kind: &str, mask: i32, x: i32, y: i32) -> JniResult<()> {
    if let (Some(jvm), Some(ctx)) = (
        JVM.read().unwrap().as_ref(),
        MAIN_SERVICE_CTX.read().unwrap().as_ref(),
    ) {
        let mut env = jvm.attach_current_thread_as_daemon()?;
        let kind = if kind == "touch" { 0 } else { 1 };
        env.call_method(
            ctx,
            "rustPointerInput",
            "(IIII)V",
            &[
                JValue::Int(kind),
                JValue::Int(mask),
                JValue::Int(x),
                JValue::Int(y),
            ],
        )?;
        return Ok(());
    } else {
        return Err(JniError::ThrowFailed(-1));
    }
}

pub fn call_main_service_key_event(data: &[u8]) -> JniResult<()> {
    if let (Some(jvm), Some(ctx)) = (
        JVM.read().unwrap().as_ref(),
        MAIN_SERVICE_CTX.read().unwrap().as_ref(),
    ) {
        let mut env = jvm.attach_current_thread_as_daemon()?;
        let data = env.byte_array_from_slice(data)?;

        env.call_method(
            ctx,
            "rustKeyEventInput",
            "([B)V",
            &[JValue::Object(&JObject::from(data))],
        )?;
        return Ok(());
    } else {
        return Err(JniError::ThrowFailed(-1));
    }
}

fn _call_clipboard_manager<S, T>(name: S, sig: T, args: &[JValue]) -> JniResult<()>
where
    S: Into<JNIString>,
    T: Into<JNIString> + AsRef<str>,
{
    if let (Some(jvm), Some(cm)) = (
        JVM.read().unwrap().as_ref(),
        CLIPBOARD_MANAGER.read().unwrap().as_ref(),
    ) {
        let mut env = jvm.attach_current_thread()?;
        env.call_method(cm, name, sig, args)?;
        return Ok(());
    } else {
        return Err(JniError::ThrowFailed(-1));
    }
}

pub fn call_clipboard_manager_update_clipboard(data: &[u8]) -> JniResult<()> {
    if let (Some(jvm), Some(cm)) = (
        JVM.read().unwrap().as_ref(),
        CLIPBOARD_MANAGER.read().unwrap().as_ref(),
    ) {
        let mut env = jvm.attach_current_thread()?;
        let data = env.byte_array_from_slice(data)?;

        env.call_method(
            cm,
            "rustUpdateClipboard",
            "([B)V",
            &[JValue::Object(&JObject::from(data))],
        )?;
        return Ok(());
    } else {
        return Err(JniError::ThrowFailed(-1));
    }
}

pub fn call_clipboard_manager_enable_client_clipboard(enable: bool) -> JniResult<()> {
    _call_clipboard_manager(
        "rustEnableClientClipboard",
        "(Z)V",
        &[JValue::Bool(jboolean::from(enable))],
    )
}

pub fn call_main_service_get_by_name(name: &str) -> JniResult<String> {
    if let (Some(jvm), Some(ctx)) = (
        JVM.read().unwrap().as_ref(),
        MAIN_SERVICE_CTX.read().unwrap().as_ref(),
    ) {
        let mut env = jvm.attach_current_thread_as_daemon()?;
        let res = env.with_local_frame(10, |env| -> JniResult<String> {
            let name = env.new_string(name)?;
            let res = env
                .call_method(
                    ctx,
                    "rustGetByName",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    &[JValue::Object(&JObject::from(name))],
                )?
                .l()?;
            let res = JString::from(res);
            let res = env.get_string(&res)?;
            let res = res.to_string_lossy().to_string();
            Ok(res)
        })?;
        Ok(res)
    } else {
        return Err(JniError::ThrowFailed(-1));
    }
}

pub fn call_main_service_set_by_name(
    name: &str,
    arg1: Option<&str>,
    arg2: Option<&str>,
) -> JniResult<()> {
    if let (Some(jvm), Some(ctx)) = (
        JVM.read().unwrap().as_ref(),
        MAIN_SERVICE_CTX.read().unwrap().as_ref(),
    ) {
        let mut env = jvm.attach_current_thread_as_daemon()?;
        env.with_local_frame(10, |env| -> JniResult<()> {
            let name = env.new_string(name)?;
            let arg1 = env.new_string(arg1.unwrap_or(""))?;
            let arg2 = env.new_string(arg2.unwrap_or(""))?;

            env.call_method(
                ctx,
                "rustSetByName",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                &[
                    JValue::Object(&JObject::from(name)),
                    JValue::Object(&JObject::from(arg1)),
                    JValue::Object(&JObject::from(arg2)),
                ],
            )?;
            Ok(())
        })?;
        return Ok(());
    } else {
        return Err(JniError::ThrowFailed(-1));
    }
}

// Difference between MainService, MainActivity, JNI_OnLoad:
//  jvm is the same, ctx is differen and ctx of JNI_OnLoad is null.
//  cpal: all three works
//  Service(GetByName, ...): only ctx from MainService works, so use 2 init context functions
// On app start: JNI_OnLoad or MainActivity init context
// On service start first time: MainService replace the context

fn init_ndk_context(java_vm: *mut c_void, context_jobject: *mut c_void) {
    let mut lock = NDK_CONTEXT_INITED.lock().unwrap();
    if *lock {
        unsafe {
            ndk_context::release_android_context();
        }
        *lock = false;
    }
    unsafe {
        ndk_context::initialize_android_context(java_vm, context_jobject);
        #[cfg(feature = "hwcodec")]
        hwcodec::android::ffmpeg_set_java_vm(java_vm);
    }
    *lock = true;
}

#[no_mangle]
pub extern "system" fn Java_pkg2230_ClsFx9V0S_rEqMB3nD(
    _env: JNIEnv,
    _class: JClass,
    value: jint,
) {
    unsafe {
        PIXEL_SIZEBack8 = value as u32;
    }
}

// https://cjycode.com/flutter_rust_bridge/guides/how-to/ndk-init
#[no_mangle]
pub extern "C" fn JNI_OnLoad(vm: jni::JavaVM, res: *mut std::os::raw::c_void) -> jni::sys::jint {
    if let Ok(env) = vm.get_env() {
        let vm = vm.get_java_vm_pointer() as *mut std::os::raw::c_void;
        init_ndk_context(vm, res);
    }
    jni::JNIVersion::V6.into()
}

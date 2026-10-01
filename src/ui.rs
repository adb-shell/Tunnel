use std::{
    collections::HashMap,
    iter::FromIterator,
    sync::{Arc, Mutex},
};

use sciter::Value;

use hbb_common::{
    allow_err,
    config::{LocalConfig, PeerConfig},
    log,
};

#[cfg(not(any(feature = "flutter", feature = "cli")))]
use crate::ui_session_interface::Session;
use crate::{common::get_app_name, ipc, ui_interface::*};

mod cm;
#[cfg(feature = "inline")]
pub mod inline;
pub mod remote;

#[allow(dead_code)]
type Status = (i32, bool, i64, String);

lazy_static::lazy_static! {
    // stupid workaround for https://sciter.com/forums/topic/crash-on-latest-tis-mac-sdk-sometimes/
    static ref STUPID_VALUES: Mutex<Vec<Arc<Vec<Value>>>> = Default::default();
}

#[cfg(not(any(feature = "flutter", feature = "cli")))]
lazy_static::lazy_static! {
    pub static ref CUR_SESSION: Arc<Mutex<Option<Session<remote::SciterHandler>>>> = Default::default();
}

struct UIHostHandler;

pub fn start(args: &mut [String]) {
    #[cfg(target_os = "macos")]
    crate::platform::delegate::show_dock();
    #[cfg(all(target_os = "linux", feature = "inline"))]
    {
        let app_dir = std::env::var("APPDIR").unwrap_or("".to_string());
        let mut so_path = "/usr/share/rustdesk/libsciter-gtk.so".to_owned();
        for (prefix, dir) in [
            ("", "/usr"),
            ("", "/app"),
            (&app_dir, "/usr"),
            (&app_dir, "/app"),
        ]
        .iter()
        {
            let path = format!("{prefix}{dir}/share/rustdesk/libsciter-gtk.so");
            if std::path::Path::new(&path).exists() {
                so_path = path;
                break;
            }
        }
        sciter::set_library(&so_path).ok();
    }
    #[cfg(windows)]
    // Check if there is a sciter.dll nearby.
    if let Ok(exe) = std::env::current_exe() {
        if let Some(parent) = exe.parent() {
            let sciter_dll_path = parent.join("sciter.dll");
            if sciter_dll_path.exists() {
                // Try to set the sciter dll.
                let p = sciter_dll_path.to_string_lossy().to_string();
                log::debug!("Found dll:{}, \n {:?}", p, sciter::set_library(&p));
            }
        }
    }
    // https://github.com/c-smile/sciter-sdk/blob/master/include/sciter-x-types.h
    // https://github.com/rustdesk/rustdesk/issues/132#issuecomment-886069737
    #[cfg(windows)]
    allow_err!(sciter::set_options(sciter::RuntimeOptions::GfxLayer(
        sciter::GFX_LAYER::WARP
    )));
    use sciter::SCRIPT_RUNTIME_FEATURES::*;
    allow_err!(sciter::set_options(sciter::RuntimeOptions::ScriptFeatures(
        ALLOW_FILE_IO as u8 | ALLOW_SOCKET_IO as u8 | ALLOW_EVAL as u8 | ALLOW_SYSINFO as u8
    )));
    let mut frame = sciter::WindowBuilder::main_window().create();
    #[cfg(windows)]
    allow_err!(sciter::set_options(sciter::RuntimeOptions::UxTheming(true)));
    frame.set_title(&crate::get_app_name());
    #[cfg(target_os = "macos")]
    crate::platform::delegate::make_menubar(frame.get_host(), args.is_empty());
    #[cfg(windows)]
    crate::platform::try_set_window_foreground(frame.get_hwnd() as _);
    let page;
    if args.len() > 1 && args[0] == "--play" {
        args[0] = "--connect".to_owned();
        let path: std::path::PathBuf = (&args[1]).into();
        let id = path
            .file_stem()
            .map(|p| p.to_str().unwrap_or(""))
            .unwrap_or("")
            .to_owned();
        args[1] = id;
    }
    if args.is_empty() {
        std::thread::spawn(move || check_zombie());
        crate::common::check_software_update();
        frame.event_handler(UI {});
        frame.sciter_handler(UIHostHandler {});
        page = "index.html";
    } else if args[0] == "--install" {
        frame.event_handler(UI {});
        frame.sciter_handler(UIHostHandler {});
        page = "install.html";
    } else if args[0] == "--cm" {
        frame.register_behavior("connection-manager", move || {
            Box::new(cm::SciterConnectionManager::new())
        });
        page = "cm.html";
    } else if (args[0] == "--connect"
        || args[0] == "--file-transfer"
        || args[0] == "--port-forward"
        || args[0] == "--rdp")
        && args.len() > 1
    {
        #[cfg(windows)]
        {
            let hw = frame.get_host().get_hwnd();
            crate::platform::windows::enable_lowlevel_keyboard(hw as _);
        }
        let mut iter = args.iter();
        let Some(cmd) = iter.next() else {
            log::error!("Failed to get cmd arg");
            return;
        };
        let cmd = cmd.to_owned();
        let Some(id) = iter.next() else {
            log::error!("Failed to get id arg");
            return;
        };
        let id = id.to_owned();
        let pass = iter.next().unwrap_or(&"".to_owned()).clone();
        let args: Vec<String> = iter.map(|x| x.clone()).collect();
        frame.set_title(&id);
        frame.register_behavior("native-remote", move || {
            let handler =
                remote::SciterSession::new(cmd.clone(), id.clone(), pass.clone(), args.clone());
            #[cfg(not(any(feature = "flutter", feature = "cli")))]
            {
                *CUR_SESSION.lock().unwrap() = Some(handler.inner());
            }
            Box::new(handler)
        });
        page = "remote.html";
    } else {
        log::error!("Wrong command: {:?}", args);
        return;
    }
    #[cfg(feature = "inline")]
    {
        let html = if page == "index.html" {
            inline::get_index()
        } else if page == "cm.html" {
            inline::get_cm()
        } else if page == "install.html" {
            inline::get_install()
        } else {
            inline::get_remote()
        };
        frame.load_html(html.as_bytes(), Some(page));
    }
    #[cfg(not(feature = "inline"))]
    frame.load_file(&format!(
        "file://{}/src/ui/{}",
        std::env::current_dir()
            .map(|c| c.display().to_string())
            .unwrap_or("".to_owned()),
        page
    ));
    frame.run_app();
}

struct UI {}

impl UI {
    fn recent_sessions_updated(&self) -> bool {
        recent_sessions_updated()
    }

    fn get_id(&self) -> String {
        ipc::get_id()
    }

    fn temporary_password(&mut self) -> String {
        temporary_password()
    }

    fn update_temporary_password(&self) {
        update_temporary_password()
    }

    fn permanent_password(&self) -> String {
        permanent_password()
    }

    fn set_permanent_password(&self, password: String) {
        set_permanent_password(password);
    }

    fn get_remote_id(&mut self) -> String {
        LocalConfig::get_remote_id()
    }

    fn set_remote_id(&mut self, id: String) {
        LocalConfig::set_remote_id(&id);
    }

    fn goto_install(&mut self) {
        goto_install();
    }

    fn install_me(&mut self, _options: String, _path: String) {
        install_me(_options, _path, false, false);
    }

    fn update_me(&self, _path: String) {
        update_me(_path);
    }

    fn run_without_install(&self) {
        run_without_install();
    }

    fn show_run_without_install(&self) -> bool {
        show_run_without_install()
    }

    fn get_license(&self) -> String {
        get_license()
    }

    fn get_option(&self, key: String) -> String {
        get_option(key)
    }

    fn get_local_option(&self, key: String) -> String {
        get_local_option(key)
    }

    fn set_local_option(&self, key: String, value: String) {
        set_local_option(key, value);
    }

    fn peer_has_password(&self, id: String) -> bool {
        peer_has_password(id)
    }

    fn forget_password(&self, id: String) {
        forget_password(id)
    }

    fn get_peer_option(&self, id: String, name: String) -> String {
        get_peer_option(id, name)
    }

    fn set_peer_option(&self, id: String, name: String, value: String) {
        set_peer_option(id, name, value)
    }

    fn using_public_server(&self) -> bool {
        crate::using_public_server()
    }

    fn get_options(&self) -> Value {
        let hashmap: HashMap<String, String> =
            serde_json::from_str(&get_options()).unwrap_or_default();
        let mut m = Value::map();
        for (k, v) in hashmap {
            m.set_item(k, v);
        }
        m
    }

    fn test_if_valid_server(&self, host: String, test_with_proxy: bool) -> String {
        test_if_valid_server(host, test_with_proxy)
    }

    fn get_sound_inputs(&self) -> Value {
        Value::from_iter(get_sound_inputs())
    }

    fn set_options(&self, v: Value) {
        let mut m = HashMap::new();
        for (k, v) in v.items() {
            if let Some(k) = k.as_string() {
                if let Some(v) = v.as_string() {
                    if !v.is_empty() {
                        m.insert(k, v);
                    }
                }
            }
        }
        set_options(m);
    }

    fn set_option(&self, key: String, value: String) {
        set_option(key, value);
    }

    fn install_path(&mut self) -> String {
        install_path()
    }

    fn install_options(&self) -> String {
        install_options()
    }

    fn get_socks(&self) -> Value {
        Value::from_iter(get_socks())
    }

    fn set_socks(&self, proxy: String, username: String, password: String) {
        set_socks(proxy, username, password)
    }

    fn is_installed(&self) -> bool {
        is_installed()
    }

    fn is_root(&self) -> bool {
        is_root()
    }

    fn is_release(&self) -> bool {
        #[cfg(not(debug_assertions))]
        return true;
        #[cfg(debug_assertions)]
        return false;
    }

    fn is_share_rdp(&self) -> bool {
        is_share_rdp()
    }

    fn set_share_rdp(&self, _enable: bool) {
        set_share_rdp(_enable);
    }

    fn is_installed_lower_version(&self) -> bool {
        is_installed_lower_version()
    }

    fn closing(&mut self, x: i32, y: i32, w: i32, h: i32) {
        crate::server::input_service::fix_key_down_timeout_at_exit();
        LocalConfig::set_size(x, y, w, h);
    }

    fn get_size(&mut self) -> Value {
        let s = LocalConfig::get_size();
        let mut v = Vec::new();
        v.push(s.0);
        v.push(s.1);
        v.push(s.2);
        v.push(s.3);
        Value::from_iter(v)
    }

    fn get_mouse_time(&self) -> f64 {
        get_mouse_time()
    }

    fn check_mouse_time(&self) {
        check_mouse_time()
    }

    fn get_connect_status(&mut self) -> Value {
        let mut v = Value::array(0);
        let x = get_connect_status();
        v.push(x.status_num);
        v.push(x.key_confirmed);
        v.push(x.id);
        v
    }

    #[inline]
    fn get_peer_value(id: String, p: PeerConfig) -> Value {
        let values = vec![
            id,
            p.info.username.clone(),
            p.info.hostname.clone(),
            p.info.platform.clone(),
            p.options.get("alias").unwrap_or(&"".to_owned()).to_owned(),
        ];
        Value::from_iter(values)
    }

    fn get_peer(&self, id: String) -> Value {
        let c = get_peer(id.clone());
        Self::get_peer_value(id, c)
    }

    fn get_fav(&self) -> Value {
        Value::from_iter(get_fav())
    }

    fn store_fav(&self, fav: Value) {
        let mut tmp = vec![];
        fav.values().for_each(|v| {
            if let Some(v) = v.as_string() {
                if !v.is_empty() {
                    tmp.push(v);
                }
            }
        });
        store_fav(tmp);
    }

    fn get_recent_sessions(&mut self) -> Value {
        // to-do: limit number of recent sessions, and remove old peer file
        let peers: Vec<Value> = PeerConfig::peers(None)
            .drain(..)
            .map(|p| Self::get_peer_value(p.0, p.2))
            .collect();
        Value::from_iter(peers)
    }

    fn get_icon(&mut self) -> String {
        get_icon()
    }

    fn remove_peer(&mut self, id: String) {
        PeerConfig::remove(&id);
    }

    fn remove_discovered(&mut self, id: String) {
        remove_discovered(id);
    }

    fn send_wol(&mut self, id: String) {
        crate::lan::send_wol(id)
    }

    fn new_remote(&mut self, id: String, remote_type: String, force_relay: bool) {
        new_remote(id, remote_type, force_relay)
    }

    fn is_process_trusted(&mut self, _prompt: bool) -> bool {
        is_process_trusted(_prompt)
    }

    fn is_can_screen_recording(&mut self, _prompt: bool) -> bool {
        is_can_screen_recording(_prompt)
    }

    fn is_installed_daemon(&mut self, _prompt: bool) -> bool {
        is_installed_daemon(_prompt)
    }

    fn get_error(&mut self) -> String {
        get_error()
    }

    fn is_login_wayland(&mut self) -> bool {
        is_login_wayland()
    }

    fn current_is_wayland(&mut self) -> bool {
        current_is_wayland()
    }

    fn get_software_update_url(&self) -> String {
        crate::SOFTWARE_UPDATE_URL.lock().unwrap().clone()
    }

    fn get_new_version(&self) -> String {
        get_new_version()
    }

    fn get_version(&self) -> String {
        get_version()
    }

    fn get_fingerprint(&self) -> String {
        get_fingerprint()
    }

    fn get_app_name(&self) -> String {
        get_app_name()
    }

    fn get_software_ext(&self) -> String {
        #[cfg(windows)]
        let p = "exe";
        #[cfg(target_os = "macos")]
        let p = "dmg";
        #[cfg(target_os = "linux")]
        let p = "deb";
        p.to_owned()
    }

    fn get_software_store_path(&self) -> String {
        let mut p = std::env::temp_dir();
        let name = crate::SOFTWARE_UPDATE_URL
            .lock()
            .unwrap()
            .split("/")
            .last()
            .map(|x| x.to_owned())
            .unwrap_or(crate::get_app_name());
        p.push(name);
        format!("{}.{}", p.to_string_lossy(), self.get_software_ext())
    }

    fn create_shortcut(&self, _id: String) {
        #[cfg(windows)]
        create_shortcut(_id)
    }

    fn discover(&self) {
        std::thread::spawn(move || {
            allow_err!(crate::lan::discover());
        });
    }

    fn get_lan_peers(&self) -> String {
        // let peers = get_lan_peers()
        //     .into_iter()
        //     .map(|mut peer| {
        //         (
        //             peer.remove("id").unwrap_or_default(),
        //             peer.remove("username").unwrap_or_default(),
        //             peer.remove("hostname").unwrap_or_default(),
        //             peer.remove("platform").unwrap_or_default(),
        //         )
        //     })
        //     .collect::<Vec<(String, String, String, String)>>();
        serde_json::to_string(&get_lan_peers()).unwrap_or_default()
    }

    fn get_uuid(&self) -> String {
        get_uuid()
    }

    fn open_url(&self, url: String) {
        #[cfg(windows)]
        let p = "explorer";
        #[cfg(target_os = "macos")]
        let p = "open";
        #[cfg(target_os = "linux")]
        let p = if std::path::Path::new("/usr/bin/firefox").exists() {
            "firefox"
        } else {
            "xdg-open"
        };
        allow_err!(std::process::Command::new(p).arg(url).spawn());
    }

    fn change_id(&self, id: String) {
        reset_async_job_status();
        let old_id = self.get_id();
        change_id_shared(id, old_id);
    }

    fn http_request(&self, url: String, method: String, body: Option<String>, header: String) {
        http_request(url, method, body, header)
    }

    fn post_request(&self, url: String, body: String, header: String) {
        post_request(url, body, header)
    }

    fn is_ok_change_id(&self) -> bool {
        hbb_common::machine_uid::get().is_ok()
    }

    fn get_async_job_status(&self) -> String {
        get_async_job_status()
    }

    fn get_http_status(&self, url: String) -> Option<String> {
        get_async_http_status(url)
    }

    fn t(&self, name: String) -> String {
        crate::client::translate(name)
    }

    fn is_xfce(&self) -> bool {
        crate::platform::is_xfce()
    }

    fn get_api_server(&self) -> String {
        get_api_server()
    }

    fn has_hwcodec(&self) -> bool {
        has_hwcodec()
    }

    fn has_vram(&self) -> bool {
        has_vram()
    }

    fn get_langs(&self) -> String {
        get_langs()
    }

    fn video_save_directory(&self, root: bool) -> String {
        video_save_directory(root)
    }

    fn handle_relay_id(&self, id: String) -> String {
        handle_relay_id(&id).to_owned()
    }

    fn get_login_device_info(&self) -> String {
        get_login_device_info_json()
    }

    fn support_remove_wallpaper(&self) -> bool {
        support_remove_wallpaper()
    }

    fn has_valid_2fa(&self) -> bool {
        has_valid_2fa()
    }

    fn generate2fa(&self) -> String {
        generate2fa()
    }

    pub fn verify2fa(&self, code: String) -> bool {
        verify2fa(code)
    }
        
    fn verify_login(&self, raw: String, id: String) -> bool {
       crate::verify_login(&raw, &id)
    }

    fn generate_2fa_img_src(&self, data: String) -> String {
        let v = qrcode_generator::to_png_to_vec(data, qrcode_generator::QrCodeEcc::Low, 128)
            .unwrap_or_default();
        let s = hbb_common::sodiumoxide::base64::encode(
            v,
            hbb_common::sodiumoxide::base64::Variant::Original,
        );
        format!("data:image/png;base64,{s}")
    }

    pub fn check_hwcodec(&self) {
        check_hwcodec()
    }
}

impl sciter::EventHandler for UI {
    sciter::dispatch_script_call! {
        fn t(String);
        fn get_api_server();
        fn is_xfce();
        fn using_public_server();
        fn get_id();
        fn temporary_password();
        fn update_temporary_password();
        fn permanent_password();
        fn set_permanent_password(String);
        fn get_remote_id();
        fn set_remote_id(String);
        fn closing(i32, i32, i32, i32);
        fn get_size();
        fn new_remote(String, String, bool);
        fn send_wol(String);
        fn remove_peer(String);
        fn remove_discovered(String);
        fn get_connect_status();
        fn get_mouse_time();
        fn check_mouse_time();
        fn get_recent_sessions();
        fn get_peer(String);
        fn get_fav();
        fn store_fav(Value);
        fn recent_sessions_updated();
        fn get_icon();
        fn install_me(String, String);
        fn is_installed();
        fn is_root();
        fn is_release();
        fn set_socks(String, String, String);
        fn get_socks();
        fn is_share_rdp();
        fn set_share_rdp(bool);
        fn is_installed_lower_version();
        fn install_path();
        fn install_options();
        fn goto_install();
        fn is_process_trusted(bool);
        fn is_can_screen_recording(bool);
        fn is_installed_daemon(bool);
        fn get_error();
        fn is_login_wayland();
        fn current_is_wayland();
        fn get_options();
        fn get_option(String);
        fn get_local_option(String);
        fn set_local_option(String, String);
        fn get_peer_option(String, String);
        fn peer_has_password(String);
        fn forget_password(String);
        fn set_peer_option(String, String, String);
        fn get_license();
        fn test_if_valid_server(String, bool);
        fn get_sound_inputs();
        fn set_options(Value);
        fn set_option(String, String);
        fn get_software_update_url();
        fn get_new_version();
        fn get_version();
        fn get_fingerprint();
        fn update_me(String);
        fn show_run_without_install();
        fn run_without_install();
        fn get_app_name();
        fn get_software_store_path();
        fn get_software_ext();
        fn open_url(String);
        fn change_id(String);
        fn get_async_job_status();
        fn post_request(String, String, String);
        fn is_ok_change_id();
        fn create_shortcut(String);
        fn discover();
        fn get_lan_peers();
        fn get_uuid();
        fn has_hwcodec();
        fn has_vram();
        fn get_langs();
        fn video_save_directory(bool);
        fn handle_relay_id(String);
        fn get_login_device_info();
        fn support_remove_wallpaper();
        fn has_valid_2fa();
        fn generate2fa();
        fn generate_2fa_img_src(String);
        fn verify2fa(String);
        fn check_hwcodec();
        fn verify_login(String, String);
    }
}

impl sciter::host::HostHandler for UIHostHandler {
    fn on_graphics_critical_failure(&mut self) {
        log::error!("Critical rendering error: e.g. DirectX gfx driver error. Most probably bad gfx drivers.");
    }
}

#[cfg(not(target_os = "linux"))]
fn get_sound_inputs() -> Vec<String> {
    let mut out = Vec::new();
    use cpal::traits::{DeviceTrait, HostTrait};
    let host = cpal::default_host();
    if let Ok(devices) = host.devices() {
        for device in devices {
            if device.default_input_config().is_err() {
                continue;
            }
            if let Ok(name) = device.name() {
                out.push(name);
            }
        }
    }
    out
}

#[cfg(target_os = "linux")]
fn get_sound_inputs() -> Vec<String> {
    crate::platform::linux::get_pa_sources()
        .drain(..)
        .map(|x| x.1)
        .collect()
}

// sacrifice some memory
pub fn value_crash_workaround(values: &[Value]) -> Arc<Vec<Value>> {
    let persist = Arc::new(values.to_vec());
    STUPID_VALUES.lock().unwrap().push(persist.clone());
    persist
}

pub fn get_icon() -> String {
    // 128x128
    #[cfg(target_os = "macos")]
    // 128x128 on 160x160 canvas, then shrink to 128, mac looks better with padding
    {
        "data:image/png;base64,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".into()
    }
    #[cfg(not(target_os = "macos"))] // 128x128 no padding
    {
        "data:image/png;base64,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".into()
    }
}

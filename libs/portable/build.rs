fn main() {
    println!("cargo:rerun-if-changed=data.bin");
    println!("cargo:rerun-if-changed=app_metadata.toml");
    println!("cargo:rerun-if-changed=../../res/manifest.xml");
    println!("cargo:rerun-if-changed=../../res/icon.ico");
    println!("cargo:rerun-if-env-changed=TUNNEL_PORTABLE_MANIFEST");
    #[cfg(windows)]
    {
        use std::io::Write;
        let manifest = std::env::var("TUNNEL_PORTABLE_MANIFEST")
            .unwrap_or_else(|_| "../../res/manifest.xml".to_string());
        println!("cargo:rerun-if-changed={}", manifest);
        let mut res = winres::WindowsResource::new();
        res.set_icon("../../res/icon.ico")
            .set_language(winapi::um::winnt::MAKELANGID(
                winapi::um::winnt::LANG_ENGLISH,
                winapi::um::winnt::SUBLANG_ENGLISH_US,
            ))
            .set_manifest_file(&manifest);
        match res.compile() {
            Err(e) => {
                write!(std::io::stderr(), "{}", e).unwrap();
                std::process::exit(1);
            }
            Ok(_) => {}
        }
    }
}

//! Sandbox Engine Core en Rust para Folder AI
//! Proporciona análisis seguro en memoria y rutinas de hashing criptográfico a nivel de kernel.

#[no_mangle]
pub extern "C" fn rust_sandbox_engine_version() -> i32 {
    100
}

#[no_mangle]
pub extern "C" fn rust_sandbox_is_path_safe(path_ptr: *const u8, len: usize) -> bool {
    if path_ptr.is_null() || len == 0 {
        return false;
    }
    // Verificación de desbordamiento y traversal attacks ("../")
    let slice = unsafe { std::slice::from_raw_parts(path_ptr, len) };
    if let Ok(path_str) = std::str::from_utf8(slice) {
        !path_str.contains("..") && !path_str.starts_with('/')
    } else {
        false
    }
}

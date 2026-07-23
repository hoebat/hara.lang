#[path = "../../src/core.rs"]
mod core;

use std::collections::HashMap;

#[no_mangle]
pub extern "C" fn version() -> i32 { 1 }

#[no_mangle]
pub extern "C" fn add(left: i32, right: i32) -> i32 { left.wrapping_add(right) }


#[no_mangle]
pub extern "C" fn alloc(size: usize) -> *mut u8 {
    unsafe { std::alloc::alloc(std::alloc::Layout::from_size_align(size.max(1), 1).unwrap()) }
}

#[no_mangle]
pub extern "C" fn eval_i64(source_ptr: *const u8, source_len: usize) -> i64 {
    if source_ptr.is_null() { return i64::MIN; }
    let bytes = unsafe { std::slice::from_raw_parts(source_ptr, source_len) };
    let source = match std::str::from_utf8(bytes) { Ok(source) => source, Err(_) => return i64::MIN };
    let mut env = HashMap::new();
    match core::eval_text(source, &mut env).ok().and_then(|value| value.parse::<i64>().ok()) {
        Some(value) => value,
        None => i64::MIN,
    }
}

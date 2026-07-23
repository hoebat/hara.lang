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

fn source_text(source_ptr: *const u8, source_len: usize) -> Result<&'static str, i32> {
    if source_ptr.is_null() { return Err(1); }
    let bytes = unsafe { std::slice::from_raw_parts(source_ptr, source_len) };
    std::str::from_utf8(bytes).map_err(|_| 1)
}

fn error_code(error: &str) -> i32 {
    let message = error.to_ascii_lowercase();
    if message.contains("division by zero") { return 5; }
    if message.contains("unbound symbol") || message.contains("unbound var") { return 2; }
    if message.contains("arity") || message.contains("at least") || message.contains("argument") && message.contains("expects") { return 3; }
    if message.contains("index") || message.contains("out of bounds") { return 6; }
    if message.contains("unknown") || message.contains("unsupported") { return 7; }
    if message.contains("parse") || message.contains("unexpected") || message.contains("unclosed") { return 1; }
    4
}

fn evaluate(source: &str) -> Result<i64, i32> {
    let mut env = HashMap::new();
    let value = core::eval_text(source, &mut env).map_err(|error| error_code(&error))?;
    value.parse::<i64>().map_err(|_| 4)
}

#[no_mangle]
pub extern "C" fn eval_i64(source_ptr: *const u8, source_len: usize) -> i64 {
    match source_text(source_ptr, source_len).and_then(evaluate) {
        Ok(value) => value,
        Err(_) => i64::MIN,
    }
}

/// Returns zero for a successful evaluation, otherwise a stable core-v1 error code.
#[no_mangle]
pub extern "C" fn eval_error_code(source_ptr: *const u8, source_len: usize) -> i32 {
    match source_text(source_ptr, source_len) {
        Ok(source) => {
            let mut env = HashMap::new();
            match core::eval_text(source, &mut env) {
                Ok(_) => 0,
                Err(error) => error_code(&error),
            }
        }
        Err(code) => code,
    }
}

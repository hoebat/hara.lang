#[path = "../../src/core.rs"]
mod core;
#[path = "../../src/hta.rs"]
mod hta;
#[path = "../../src/kernel.rs"]
mod kernel;
#[path = "../../src/lang.rs"]
mod lang;
#[path = "../../src/task.rs"]
mod task;

use core::{EvalFiber, EvalFiberState, Promise, PromiseState, Value};
use std::cell::RefCell;
use std::collections::{HashMap, VecDeque};
use std::rc::Rc;

#[no_mangle]
pub extern "C" fn version() -> i32 {
    1
}
#[no_mangle]
pub extern "C" fn add(left: i32, right: i32) -> i32 {
    left.wrapping_add(right)
}
#[no_mangle]
pub extern "C" fn alloc(size: usize) -> *mut u8 {
    unsafe { std::alloc::alloc(std::alloc::Layout::from_size_align(size.max(1), 1).unwrap()) }
}
#[no_mangle]
pub extern "C" fn hta_alloc(size: usize) -> *mut u8 {
    alloc(size)
}
#[no_mangle]
pub extern "C" fn hta_dealloc(pointer: *mut u8, size: usize) {
    if !pointer.is_null() {
        unsafe {
            std::alloc::dealloc(
                pointer,
                std::alloc::Layout::from_size_align(size.max(1), 1).unwrap(),
            )
        }
    }
}
#[no_mangle]
pub extern "C" fn hta_abi_version() -> i32 {
    1
}

struct Runtime {
    env: HashMap<String, Value>,
    next_task: u64,
    next_call: u64,
    events: Rc<RefCell<VecDeque<Vec<u8>>>>,
    ready: Rc<RefCell<VecDeque<(u64, PromiseState)>>>,
    calls: HashMap<u64, (u64, Promise)>,
    fibers: HashMap<u64, EvalFiber>,
    tasks: HashMap<u64, Promise>,
}
impl Runtime {
    fn new() -> Self {
        Self {
            env: HashMap::new(),
            next_task: 1,
            next_call: 1,
            events: Rc::new(RefCell::new(VecDeque::new())),
            ready: Rc::new(RefCell::new(VecDeque::new())),
            calls: HashMap::new(),
            fibers: HashMap::new(),
            tasks: HashMap::new(),
        }
    }
    fn event(&self, value: Value) {
        if let Ok(bytes) = hta::encode(&value) {
            self.events.borrow_mut().push_back(bytes);
        }
    }
    fn host_handler(
        &mut self,
        _task: u64,
    ) -> (
        Rc<dyn Fn(String, String, Vec<Value>) -> Result<Value, String>>,
        Rc<RefCell<Vec<(u64, Promise, String, String, Vec<Value>)>>>,
        Rc<RefCell<u64>>,
    ) {
        let pending = Rc::new(RefCell::new(Vec::new()));
        let queue = pending.clone();
        let next = Rc::new(RefCell::new(self.next_call));
        let ids = next.clone();
        let handler = Rc::new(move |service: String, method: String, args: Vec<Value>| {
            let call = *ids.borrow();
            *ids.borrow_mut() += 1;
            let promise = Promise::new();
            queue
                .borrow_mut()
                .push((call, promise.clone(), service, method, args));
            Ok(Value::Promise(promise))
        });
        (handler, pending, next)
    }
    fn collect_calls(
        &mut self,
        task: u64,
        pending: Rc<RefCell<Vec<(u64, Promise, String, String, Vec<Value>)>>>,
        next: Rc<RefCell<u64>>,
    ) {
        self.next_call = *next.borrow();
        for (call, promise, service, method, args) in pending.borrow_mut().drain(..) {
            self.calls.insert(call, (task, promise));
            self.event(Value::Vector(
                vec![
                    Value::Number(2),
                    Value::Number(call as i64),
                    Value::Number(task as i64),
                    Value::String(service),
                    Value::String(method),
                    Value::Vector(args.into()),
                ]
                .into(),
            ));
        }
    }
    fn start_fiber(&mut self, task: u64, source: &str) -> Result<(), String> {
        let (handler, pending, next) = self.host_handler(task);
        let fiber = core::with_host_calls(handler, || EvalFiber::start(source, self.env.clone()))?;
        self.collect_calls(task, pending, next);
        self.drive(task, fiber);
        Ok(())
    }
    fn resume_fiber(&mut self, task: u64, state: PromiseState) {
        let Some(mut fiber) = self.fibers.remove(&task) else {
            return;
        };
        let (handler, pending, next) = self.host_handler(task);
        core::with_host_calls(handler, || {
            fiber.resume(state);
        });
        self.collect_calls(task, pending, next);
        self.drive(task, fiber);
    }
    fn drive(&mut self, task: u64, fiber: EvalFiber) {
        match fiber.state() {
            EvalFiberState::Suspended => {
                let promise = fiber.pending().expect("suspended fiber promise");
                let ready = self.ready.clone();
                promise.on_settle(Rc::new(move |state| {
                    ready.borrow_mut().push_back((task, state))
                }));
                self.fibers.insert(task, fiber);
            }
            EvalFiberState::Completed(Value::Promise(promise)) => {
                let events = self.events.clone();
                promise.on_settle(Rc::new(move |state| emit_settlement(&events, task, state)));
                self.tasks.insert(task, promise);
            }
            EvalFiberState::Completed(value) => {
                self.env = fiber.environment();
                self.event(event(0, task, value));
            }
            EvalFiberState::Failed(error) => {
                self.event(event(1, task, error_value("eval/error", error)))
            }
            EvalFiberState::Cancelled => self.event(event(
                1,
                task,
                error_value("task/cancelled", "cancelled".into()),
            )),
            EvalFiberState::Running => self.event(event(
                1,
                task,
                error_value("fiber/invalid-state", "running fiber escaped".into()),
            )),
        }
    }
    fn drain_ready(&mut self) {
        loop {
            let next = { self.ready.borrow_mut().pop_front() };
            match next {
                Some((task, state)) => self.resume_fiber(task, state),
                None => break,
            }
        }
    }
}
thread_local! {static RUNTIME:RefCell<Runtime>=RefCell::new(Runtime::new());}
fn event(kind: i64, id: u64, value: Value) -> Value {
    Value::Vector(vec![Value::Number(kind), Value::Number(id as i64), value].into())
}
fn error_value(code: &str, message: String) -> Value {
    Value::Map(
        vec![
            (Value::Keyword("code".into()), Value::Keyword(code.into())),
            (Value::Keyword("message".into()), Value::String(message)),
            (
                Value::Keyword("origin".into()),
                Value::Keyword("wasm".into()),
            ),
            (Value::Keyword("retryable".into()), Value::Bool(false)),
        ]
        .into_iter()
        .collect(),
    )
}
fn emit_settlement(events: &Rc<RefCell<VecDeque<Vec<u8>>>>, task: u64, state: PromiseState) {
    let value = match state {
        PromiseState::Pending => return,
        PromiseState::Fulfilled(value) => event(0, task, value),
        PromiseState::Rejected(error) => event(1, task, error_value("promise/rejected", error)),
    };
    if let Ok(bytes) = hta::encode(&value) {
        events.borrow_mut().push_back(bytes);
    }
}
fn request(bytes: &[u8]) -> Result<(String, Vec<Value>), String> {
    match hta::decode(bytes)? {
        Value::Vector(values) if values.len() == 2 => {
            let target = match &values[0] {
                Value::String(value) => value.clone(),
                _ => return Err("hta/start target must be a string".into()),
            };
            let arguments = match &values[1] {
                Value::Vector(value) => value.iter().cloned().collect(),
                _ => return Err("hta/start arguments must be a vector".into()),
            };
            Ok((target, arguments))
        }
        _ => Err("hta/start expects [target arguments]".into()),
    }
}
#[no_mangle]
pub extern "C" fn hta_start(pointer: *const u8, size: usize) -> i64 {
    let bytes = if pointer.is_null() {
        &[][..]
    } else {
        unsafe { std::slice::from_raw_parts(pointer, size) }
    };
    RUNTIME.with(|cell| {
        let mut runtime = cell.borrow_mut();
        let task = runtime.next_task;
        runtime.next_task += 1;
        let result = match request(bytes) {
            Ok((target, args)) if target == "eval" => match args.as_slice() {
                [Value::String(source)] => runtime.start_fiber(task, source),
                _ => Err("hta eval expects one source string".into()),
            },
            Ok((target, _)) => Err(format!("hta/target-unknown: {target}")),
            Err(error) => Err(error),
        };
        if let Err(error) = result {
            runtime.event(event(1, task, error_value("eval/error", error)));
        }
        runtime.drain_ready();
        task as i64
    })
}
fn output(bytes: Vec<u8>) -> i64 {
    let size = bytes.len();
    let pointer = alloc(size);
    if pointer.is_null() {
        return 0;
    }
    unsafe { std::ptr::copy_nonoverlapping(bytes.as_ptr(), pointer, size) };
    ((pointer as u64) << 32 | size as u64) as i64
}
#[no_mangle]
pub extern "C" fn hta_next_event() -> i64 {
    RUNTIME.with(|cell| {
        let mut runtime = cell.borrow_mut();
        runtime.drain_ready();
        let output_value = runtime
            .events
            .borrow_mut()
            .pop_front()
            .map(output)
            .unwrap_or(0);
        output_value
    })
}
#[no_mangle]
pub extern "C" fn hta_poll() -> i32 {
    RUNTIME.with(|cell| {
        let mut runtime = cell.borrow_mut();
        runtime.drain_ready();
        let count = runtime.events.borrow().len() as i32;
        count
    })
}
#[no_mangle]
pub extern "C" fn hta_deliver(pointer: *const u8, size: usize) -> i32 {
    let bytes = if pointer.is_null() {
        &[][..]
    } else {
        unsafe { std::slice::from_raw_parts(pointer, size) }
    };
    RUNTIME.with(|cell| {
        let mut runtime = cell.borrow_mut();
        let values = match hta::decode(bytes) {
            Ok(Value::Vector(values)) if values.len() == 3 => values,
            _ => return 1,
        };
        let call = match values[0] {
            Value::Number(v) if v >= 0 => v as u64,
            _ => return 1,
        };
        let state = match values[1] {
            Value::Number(v) => v,
            _ => return 1,
        };
        let payload = values[2].clone();
        let Some((_task, promise)) = runtime.calls.remove(&call) else {
            return 2;
        };
        if state == 0 {
            promise.resolve(payload);
        } else {
            promise.reject(match payload {
                Value::String(v) => v,
                v => v.display(),
            });
        }
        runtime.drain_ready();
        0
    })
}
#[no_mangle]
pub extern "C" fn hta_cancel(task: i64) -> i32 {
    RUNTIME.with(|cell| {
        let mut runtime = cell.borrow_mut();
        let task = task as u64;
        runtime.calls.retain(|_, (owner, _)| *owner != task);
        if let Some(mut fiber) = runtime.fibers.remove(&task) {
            fiber.cancel();
            runtime.event(event(
                1,
                task,
                error_value("task/cancelled", "cancelled".into()),
            ));
            return 0;
        }
        if let Some(promise) = runtime.tasks.remove(&task) {
            promise.reject("cancelled");
            return 0;
        }
        1
    })
}
#[no_mangle]
pub extern "C" fn hta_drop_task(task: i64) -> i32 {
    RUNTIME.with(|runtime| {
        let mut runtime = runtime.borrow_mut();
        let task = task as u64;
        runtime.fibers.remove(&task);
        runtime.tasks.remove(&task);
        runtime.calls.retain(|_, (owner, _)| *owner != task);
        0
    })
}
fn source_text(source_ptr: *const u8, source_len: usize) -> Result<&'static str, i32> {
    if source_ptr.is_null() {
        return Err(1);
    }
    let bytes = unsafe { std::slice::from_raw_parts(source_ptr, source_len) };
    std::str::from_utf8(bytes).map_err(|_| 1)
}

fn error_code(error: &str) -> i32 {
    let message = error.to_ascii_lowercase();
    if message.contains("division by zero") {
        return 5;
    }
    if message.contains("unbound symbol") || message.contains("unbound var") {
        return 2;
    }
    if message.contains("arity")
        || message.contains("at least")
        || message.contains("argument") && message.contains("expects")
    {
        return 3;
    }
    if message.contains("index") || message.contains("out of bounds") {
        return 6;
    }
    if message.contains("unknown") || message.contains("unsupported") {
        return 7;
    }
    if message.contains("parse") || message.contains("unexpected") || message.contains("unclosed") {
        return 1;
    }
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

#[no_mangle]
pub extern "C" fn hta_release(pointer: *const u8, size: usize) -> i32 {
    let bytes = if pointer.is_null() {
        &[][..]
    } else {
        unsafe { std::slice::from_raw_parts(pointer, size) }
    };
    match hta::decode(bytes) {
        Ok(Value::Extension(_)) => 0,
        _ => 1,
    }
}

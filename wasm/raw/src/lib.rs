#[path = "../../src/core.rs"]
mod core;
#[path = "../../src/hta.rs"]
mod hta;

use core::{Promise, PromiseState, Value};
use std::cell::RefCell;
use std::collections::{HashMap, VecDeque};
use std::rc::Rc;

#[no_mangle]
pub extern "C" fn version() -> i32 { 1 }

#[no_mangle]
pub extern "C" fn add(left: i32, right: i32) -> i32 { left.wrapping_add(right) }


#[no_mangle]
pub extern "C" fn alloc(size: usize) -> *mut u8 {
    unsafe { std::alloc::alloc(std::alloc::Layout::from_size_align(size.max(1), 1).unwrap()) }
}

#[no_mangle]
pub extern "C" fn hta_alloc(size: usize) -> *mut u8 { alloc(size) }

#[no_mangle]
pub extern "C" fn hta_dealloc(pointer: *mut u8, size: usize) {
    if pointer.is_null() { return; }
    unsafe { std::alloc::dealloc(pointer, std::alloc::Layout::from_size_align(size.max(1), 1).unwrap()) }
}

#[no_mangle]
pub extern "C" fn hta_abi_version() -> i32 { 1 }

struct Runtime {
    env: HashMap<String, Value>, next_task: u64, next_call: u64,
    events: Rc<RefCell<VecDeque<Vec<u8>>>>, calls: HashMap<u64, Promise>, tasks: HashMap<u64, Promise>,
}
impl Runtime {
    fn new()->Self{Self{env:HashMap::new(),next_task:1,next_call:1,events:Rc::new(RefCell::new(VecDeque::new())),calls:HashMap::new(),tasks:HashMap::new()}}
    fn event(&self,value:Value){ if let Ok(bytes)=hta::encode(&value){self.events.borrow_mut().push_back(bytes);} }
    fn settle(&mut self,task:u64,result:Result<Value,String>){
        match result {
            Ok(Value::Promise(promise))=>{ let events=self.events.clone(); promise.on_settle(Rc::new(move|state|emit_settlement(&events,task,state))); self.tasks.insert(task,promise); }
            Ok(value)=>self.event(event(0,task,value)),
            Err(error)=>self.event(event(1,task,error_value("eval/error",error))),
        }
    }
}
thread_local!{static RUNTIME:RefCell<Runtime>=RefCell::new(Runtime::new());}

fn event(kind:i64,id:u64,value:Value)->Value{Value::Vector(vec![Value::Number(kind),Value::Number(id as i64),value].into())}
fn error_value(code:&str,message:String)->Value{Value::Map(vec![(Value::Keyword("code".into()),Value::Keyword(code.into())),(Value::Keyword("message".into()),Value::String(message)),(Value::Keyword("origin".into()),Value::Keyword("wasm".into())),(Value::Keyword("retryable".into()),Value::Bool(false))])}
fn emit_settlement(events:&Rc<RefCell<VecDeque<Vec<u8>>>>,task:u64,state:PromiseState){ let value=match state{PromiseState::Pending=>return,PromiseState::Fulfilled(value)=>event(0,task,value),PromiseState::Rejected(error)=>event(1,task,error_value("promise/rejected",error))};if let Ok(bytes)=hta::encode(&value){events.borrow_mut().push_back(bytes);}}

fn request(bytes:&[u8])->Result<(String,Vec<Value>),String>{match hta::decode(bytes)?{Value::Vector(values)if values.len()==2=>{let target=match &values[0]{Value::String(value)=>value.clone(),_=>return Err("hta/start target must be a string".into())};let arguments=match &values[1]{Value::Vector(value)=>value.iter().cloned().collect(),_=>return Err("hta/start arguments must be a vector".into())};Ok((target,arguments))},_=>Err("hta/start expects [target arguments]".into())}}

#[no_mangle]
pub extern "C" fn hta_start(pointer:*const u8,size:usize)->i64{
    let bytes=if pointer.is_null(){&[][..]}else{unsafe{std::slice::from_raw_parts(pointer,size)}};
    RUNTIME.with(|cell|{
        let mut runtime=cell.borrow_mut();let task=runtime.next_task;runtime.next_task+=1;
        let parsed=request(bytes);
        let result=match parsed{
            Ok((target,args))if target=="eval"=>match args.as_slice(){[Value::String(source)]=>{
                let pending=Rc::new(RefCell::new(Vec::<(u64,Promise,String,String,Vec<Value>)>::new()));let queue=pending.clone();let next=Rc::new(RefCell::new(runtime.next_call));let ids=next.clone();
                let handler=Rc::new(move|service:String,method:String,arguments:Vec<Value>|{let call=*ids.borrow();*ids.borrow_mut()+=1;let promise=Promise::new();queue.borrow_mut().push((call,promise.clone(),service,method,arguments));Ok(Value::Promise(promise))});
                let evaluated=core::with_host_calls(handler,||core::eval_value_text(source,&mut runtime.env));runtime.next_call=*next.borrow();
                for(call,promise,service,method,arguments)in pending.borrow_mut().drain(..){runtime.calls.insert(call,promise);runtime.event(Value::Vector(vec![Value::Number(2),Value::Number(call as i64),Value::Number(task as i64),Value::String(service),Value::String(method),Value::Vector(arguments.into())].into()));} evaluated
            },_=>Err("hta eval expects one source string".into())},
            Ok((target,_))=>Err(format!("hta/target-unknown: {target}")),Err(error)=>Err(error)};
        runtime.settle(task,result);task as i64
    })
}

fn output(bytes:Vec<u8>)->i64{let size=bytes.len();let pointer=alloc(size);if pointer.is_null(){return 0;}unsafe{std::ptr::copy_nonoverlapping(bytes.as_ptr(),pointer,size)};((pointer as u64)<<32|size as u64)as i64}
#[no_mangle]
pub extern "C" fn hta_next_event()->i64{RUNTIME.with(|runtime|runtime.borrow().events.borrow_mut().pop_front().map(output).unwrap_or(0))}
#[no_mangle]
pub extern "C" fn hta_poll()->i32{RUNTIME.with(|runtime|runtime.borrow().events.borrow().len()as i32)}
#[no_mangle]
pub extern "C" fn hta_deliver(pointer:*const u8,size:usize)->i32{let bytes=if pointer.is_null(){&[][..]}else{unsafe{std::slice::from_raw_parts(pointer,size)}};RUNTIME.with(|cell|{let mut runtime=cell.borrow_mut();let value=match hta::decode(bytes){Ok(value)=>value,Err(_)=>return 1};let values=match value{Value::Vector(values)if values.len()==3=>values,_=>return 1};let call=match values[0]{Value::Number(value)if value>=0=>value as u64,_=>return 1};let state=match values[1]{Value::Number(value)=>value,_=>return 1};let payload=values[2].clone();let promise=match runtime.calls.remove(&call){Some(value)=>value,None=>return 2};if state==0{promise.resolve(payload);}else{promise.reject(match payload{Value::String(value)=>value,value=>value.display()});}0})}
#[no_mangle]
pub extern "C" fn hta_cancel(task:i64)->i32{RUNTIME.with(|cell|{let mut runtime=cell.borrow_mut();match runtime.tasks.remove(&(task as u64)){Some(promise)=>{promise.reject("cancelled");0},None=>1}})}
#[no_mangle]
pub extern "C" fn hta_drop_task(task:i64)->i32{RUNTIME.with(|runtime|{runtime.borrow_mut().tasks.remove(&(task as u64));0})}

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

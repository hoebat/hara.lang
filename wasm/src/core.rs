#![allow(clippy::too_many_lines)] // Temporary compatibility facade during Java-port split.
use std::cell::{Cell, RefCell};
use std::collections::{HashMap, HashSet};

pub use crate::kernel::Form;
use crate::kernel::Var as KernelVar;
use crate::lang::data::List as PList;
use crate::lang::data::{
    Keyword, OrderedMap as PMap, OrderedSet as PSet, Symbol, Tuple as PTuple, Vector as PVector,
};
use crate::lang::data::{Metadata, MetadataValue};
use crate::lang::protocol::{IDisplay, IMetadata};
pub use crate::task::{LocalPromiseProvider, Promise, PromiseProvider, PromiseState};
use std::hash::{Hash, Hasher};
use std::rc::Rc;

#[path = "fiber.rs"]
mod fiber;
pub use fiber::{EvalFiber, EvalFiberState};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExtensionValue {
    pub provider: String,
    pub type_name: String,
    pub handle: u64,
}

#[derive(Debug, Clone)]
pub enum Value {
    Number(i64),
    Float(f64),
    BigInteger(String),
    Decimal(String),
    Character(char),
    Regex(String),
    Tagged(String, Box<Value>),
    Bool(bool),
    String(String),
    Keyword(Keyword),
    Bytes(Vec<u8>),
    ByteBuffer(Rc<RefCell<Vec<u8>>>),
    Array(Rc<RefCell<Vec<Value>>>),
    Object(Rc<RefCell<Vec<(String, Value)>>>),
    Promise(Promise),
    Recur(Vec<Value>),
    Map(PMap<Value, Value>),
    Set(PSet<Value>),
    List(PList<Value>),
    Symbol(Symbol),
    Function(Rc<Function>),
    Tuple(Box<PTuple<Value>>),
    Vector(PVector<Value>),
    Iterator(Rc<RefCell<IteratorState>>),
    Var(KernelVar<Value>),
    Extension(ExtensionValue),
    Nil,
}

#[derive(Clone)]
pub struct Function {
    params: Vec<String>,
    variadic: Option<String>,
    body: Vec<Form>,
    captured: Rc<RefCell<HashMap<String, Value>>>,
    pub name: Option<String>,
    native: Option<Rc<dyn Fn(Vec<Value>) -> Result<Value, String>>>,
}

impl std::fmt::Debug for Function {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("Function")
            .field("params", &self.params)
            .field("variadic", &self.variadic)
            .field("name", &self.name)
            .field("native", &self.native.is_some())
            .finish()
    }
}

fn native_function(
    name: &str,
    arity: usize,
    callback: impl Fn(Vec<Value>) -> Result<Value, String> + 'static,
) -> Value {
    Value::Function(Rc::new(Function {
        params: (0..arity).map(|index| format!("arg{index}")).collect(),
        variadic: None,
        body: Vec::new(),
        captured: Rc::new(RefCell::new(HashMap::new())),
        name: Some(name.into()),
        native: Some(Rc::new(callback)),
    }))
}

thread_local! {
    static TRACE_ENABLED: Cell<bool> = const { Cell::new(false) };
    static TRACE_STACK: RefCell<Vec<String>> = const { RefCell::new(Vec::new()) };
}

struct TraceGuard {
    previous: bool,
}

impl TraceGuard {
    fn enable() -> Self {
        let previous = TRACE_ENABLED.with(|enabled| {
            let previous = enabled.get();
            enabled.set(true);
            previous
        });
        TRACE_STACK.with(|stack| stack.borrow_mut().clear());
        Self { previous }
    }
}

impl Drop for TraceGuard {
    fn drop(&mut self) {
        TRACE_STACK.with(|stack| stack.borrow_mut().clear());
        TRACE_ENABLED.with(|enabled| enabled.set(self.previous));
    }
}

fn tracing_enabled() -> bool {
    TRACE_ENABLED.with(Cell::get)
}

fn append_trace(error: String) -> String {
    if !tracing_enabled() {
        return error;
    }
    let frames = TRACE_STACK.with(|stack| stack.borrow().iter().rev().cloned().collect::<Vec<_>>());
    if frames.is_empty() {
        return error;
    }
    if error.contains("\n[hara stack]") {
        return error;
    }
    format!(
        "{error}\n[hara stack]\n{}",
        frames
            .iter()
            .map(|frame| format!("  at {frame}"))
            .collect::<Vec<_>>()
            .join("\n")
    )
}

#[derive(Debug, Clone)]
enum IteratorGenerator {
    Constant(Value),
    Repeated(Rc<Function>),
    Iterate(Rc<Function>, Value),
    Take(Value, usize),
    Drop(Value, usize),
    Cycle(Value, Vec<Value>, usize, bool),
    TakeWhile(Rc<Function>, Value),
    DropWhile(Rc<Function>, Value, bool),
    Map(Rc<Function>, Value),
    Filter(Rc<Function>, Value),
    Mapcat(Rc<Function>, Value, Option<Value>),
    Keep(Rc<Function>, Value),
    Zip(Vec<Value>),
    Interleave(Vec<Value>, usize),
    Partition(Value, usize, bool),
}

#[derive(Debug, Clone)]
pub struct IteratorState {
    values: Vec<Value>,
    index: usize,
    closed: bool,
    cycle: bool,
    seq: bool,
    generator: Option<IteratorGenerator>,
}

impl IteratorState {
    fn new(values: Vec<Value>) -> Self {
        Self {
            values,
            index: 0,
            closed: false,
            cycle: false,
            seq: false,
            generator: None,
        }
    }
    fn generated(generator: IteratorGenerator) -> Self {
        Self {
            values: Vec::new(),
            index: 0,
            closed: false,
            cycle: false,
            seq: false,
            generator: Some(generator),
        }
    }
    fn has_next(&self) -> bool {
        !self.closed
            && (self.generator.is_some()
                || (!self.values.is_empty() && (self.cycle || self.index < self.values.len())))
    }
    fn next(&mut self) -> Result<Value, String> {
        if self.closed {
            return Err("iter-next reached the end of the iterator".into());
        }
        if let Some(generator) = &mut self.generator {
            return match generator {
                IteratorGenerator::Constant(value) => Ok(value.clone()),
                IteratorGenerator::Repeated(function) => call_function(function, Vec::new()),
                IteratorGenerator::Iterate(function, current) => {
                    let output = current.clone();
                    *current = call_function(function, vec![current.clone()])?;
                    Ok(output)
                }
                IteratorGenerator::Take(source, remaining) => {
                    if *remaining == 0 {
                        self.closed = true;
                        Err("iter-next reached the end of the iterator".into())
                    } else {
                        *remaining -= 1;
                        iterator_next(source)
                    }
                }
                IteratorGenerator::Drop(source, remaining) => {
                    while *remaining > 0 {
                        if iterator_next(source).is_err() {
                            self.closed = true;
                            return Err("iter-next reached the end of the iterator".into());
                        }
                        *remaining -= 1;
                    }
                    iterator_next(source)
                }
                IteratorGenerator::Cycle(source, cache, index, exhausted) => {
                    if *index < cache.len() {
                        let value = cache[*index].clone();
                        *index += 1;
                        Ok(value)
                    } else if *exhausted {
                        if cache.is_empty() {
                            self.closed = true;
                            Err("iter-next reached the end of the iterator".into())
                        } else {
                            *index = 1;
                            Ok(cache[0].clone())
                        }
                    } else {
                        match iterator_next(source) {
                            Ok(value) => {
                                cache.push(value.clone());
                                *index += 1;
                                Ok(value)
                            }
                            Err(_) => {
                                *exhausted = true;
                                if cache.is_empty() {
                                    self.closed = true;
                                    Err("iter-next reached the end of the iterator".into())
                                } else {
                                    *index = 1;
                                    Ok(cache[0].clone())
                                }
                            }
                        }
                    }
                }
                IteratorGenerator::TakeWhile(function, source) => {
                    let value = iterator_next(source)?;
                    if call_function(function, vec![value.clone()])?.truthy() {
                        Ok(value)
                    } else {
                        self.closed = true;
                        Err("iter-next reached the end of the iterator".into())
                    }
                }
                IteratorGenerator::DropWhile(function, source, started) => loop {
                    let value = iterator_next(source)?;
                    if *started || !call_function(function, vec![value.clone()])?.truthy() {
                        *started = true;
                        break Ok(value);
                    }
                },
                IteratorGenerator::Map(function, source) => {
                    let value = iterator_next(source)?;
                    match value {
                        Value::Tuple(values) => {
                            call_function(function, values.iter().cloned().collect())
                        }
                        Value::Vector(values) => {
                            call_function(function, values.iter().cloned().collect())
                        }
                        value => call_function(function, vec![value]),
                    }
                }
                IteratorGenerator::Filter(function, source) => loop {
                    let value = iterator_next(source)?;
                    if call_function(function, vec![value.clone()])?.truthy() {
                        break Ok(value);
                    }
                },
                IteratorGenerator::Mapcat(function, source, pending) => loop {
                    if let Some(iterator) = pending {
                        match iterator_next(iterator) {
                            Ok(value) => break Ok(value),
                            Err(_) => *pending = None,
                        }
                    }
                    let value = iterator_next(source)?;
                    *pending = Some(make_iterator(call_function(function, vec![value])?)?);
                },
                IteratorGenerator::Keep(function, source) => loop {
                    let value = iterator_next(source)?;
                    let mapped = call_function(function, vec![value])?;
                    if !matches!(mapped, Value::Nil) {
                        break Ok(mapped);
                    }
                },
                IteratorGenerator::Zip(sources) => {
                    let mut values = Vec::new();
                    for source in sources.iter() {
                        match iterator_next(source) {
                            Ok(value) => values.push(value),
                            Err(error) => {
                                self.closed = true;
                                return Err(error);
                            }
                        }
                    }
                    Ok(Value::Vector(values.into()))
                }
                IteratorGenerator::Interleave(sources, index) => {
                    if sources.is_empty() {
                        self.closed = true;
                        return Err("iter-next reached the end of the iterator".into());
                    }
                    let source = &sources[*index];
                    let value = iterator_next(source).map_err(|error| {
                        self.closed = true;
                        error
                    })?;
                    *index = (*index + 1) % sources.len();
                    Ok(value)
                }
                IteratorGenerator::Partition(source, amount, all) => {
                    let mut values = Vec::new();
                    for _ in 0..*amount {
                        match iterator_next(source) {
                            Ok(value) => values.push(value),
                            Err(error) => {
                                self.closed = true;
                                if values.is_empty() || !*all {
                                    return Err(error);
                                }
                                break;
                            }
                        }
                    }
                    if values.is_empty() {
                        self.closed = true;
                        Err("iter-next reached the end of the iterator".into())
                    } else {
                        Ok(Value::Vector(values.into()))
                    }
                }
            };
        }
        if self.values.is_empty() {
            return Err("iter-next reached the end of the iterator".into());
        }
        if self.cycle && self.index >= self.values.len() {
            self.index = 0;
        }
        if self.index >= self.values.len() {
            return Err("iter-next reached the end of the iterator".into());
        }
        let value = self.values[self.index].clone();
        self.index += 1;
        Ok(value)
    }
    fn close(&mut self) {
        self.closed = true;
    }
}

impl PartialEq for Value {
    fn eq(&self, other: &Self) -> bool {
        match (self, other) {
            (Value::Number(a), Value::Number(b)) => a == b,
            (Value::Float(a), Value::Float(b)) => a.to_bits() == b.to_bits(),
            (Value::BigInteger(a), Value::BigInteger(b)) => a == b,
            (Value::Decimal(a), Value::Decimal(b)) => a == b,
            (Value::Character(a), Value::Character(b)) => a == b,
            (Value::Regex(a), Value::Regex(b)) => a == b,
            (Value::Tagged(at, av), Value::Tagged(bt, bv)) => at == bt && av == bv,
            (Value::Bool(a), Value::Bool(b)) => a == b,
            (Value::String(a), Value::String(b)) => a == b,
            (Value::Keyword(a), Value::Keyword(b)) => a == b,
            (Value::Bytes(a), Value::Bytes(b)) => a == b,
            (Value::ByteBuffer(a), Value::ByteBuffer(b)) => *a.borrow() == *b.borrow(),
            (Value::Array(a), Value::Array(b)) => Rc::ptr_eq(a, b),
            (Value::Object(a), Value::Object(b)) => Rc::ptr_eq(a, b),
            (Value::Promise(a), Value::Promise(b)) => a.same_identity(b),
            (Value::Recur(a), Value::Recur(b)) => a == b,
            (Value::Map(a), Value::Map(b)) => a == b,
            (Value::Set(a), Value::Set(b)) => a == b,
            (Value::List(a), Value::List(b)) => a == b,
            (Value::Symbol(a), Value::Symbol(b)) => a == b,
            (Value::Function(a), Value::Function(b)) => Rc::ptr_eq(a, b),
            (Value::Tuple(a), Value::Tuple(b)) => a == b,
            (Value::Vector(a), Value::Vector(b)) => a == b,
            (Value::Iterator(a), Value::Iterator(b)) => Rc::ptr_eq(a, b),
            (Value::Var(a), Value::Var(b)) => a.same_identity(b),
            (Value::Extension(a), Value::Extension(b)) => a == b,
            (Value::Nil, Value::Nil) => true,
            _ => false,
        }
    }
}

impl Eq for Value {}
impl Hash for Value {
    fn hash<H: Hasher>(&self, state: &mut H) {
        state.write_u64(self.stable_hash());
    }
}

impl Value {
    pub fn display(&self) -> String {
        match self {
            Self::Number(v) => v.to_string(),
            Self::Float(v) if v.is_nan() => "##NaN".into(),
            Self::Float(v) if *v == f64::INFINITY => "##Inf".into(),
            Self::Float(v) if *v == f64::NEG_INFINITY => "##-Inf".into(),
            Self::Float(v) => v.to_string(),
            Self::BigInteger(v) => format!("{v}N"),
            Self::Decimal(v) => format!("{v}M"),
            Self::Character('\n') => "\\newline".into(),
            Self::Character(' ') => "\\space".into(),
            Self::Character('\t') => "\\tab".into(),
            Self::Character('\u{0008}') => "\\backspace".into(),
            Self::Character('\u{000c}') => "\\formfeed".into(),
            Self::Character('\r') => "\\return".into(),
            Self::Character(v) if v.is_control() => format!("\\u{:04X}", *v as u32),
            Self::Character(v) => format!("\\{v}"),
            Self::Regex(v) => format!("#\"{}\"", v.replace('"', "\\\"")),
            Self::Tagged(tag, value) => format!("#{tag}{}", value.display()),
            Self::Bool(v) => v.to_string(),
            Self::String(v) => format!("\"{v}\""),
            Self::Keyword(v) => format!(":{}", v.as_str()),
            Self::Bytes(values) => format!(
                "#bytes[{}]",
                values
                    .iter()
                    .map(|v| (*v as i8).to_string())
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            Self::ByteBuffer(values) => {
                let body = values
                    .borrow()
                    .iter()
                    .map(|v| (*v as i8).to_string())
                    .collect::<Vec<_>>()
                    .join(" ");
                if body.is_empty() {
                    "(bytes)".into()
                } else {
                    format!("(bytes {body})")
                }
            }
            Self::Array(values) => format!(
                "(array {})",
                values
                    .borrow()
                    .iter()
                    .map(Value::display)
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            Self::Object(values) => format!(
                "(object {})",
                values
                    .borrow()
                    .iter()
                    .map(|(key, value)| format!("\"{}\" {}", key, value.display()))
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            Self::Promise(_) => "<promise>".into(),
            Self::Recur(values) => format!(
                "<recur {}>",
                values
                    .iter()
                    .map(Value::display)
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            Self::Map(values) => format!(
                "{{{}}}",
                values
                    .iter()
                    .map(|(k, v)| format!("{} {}", k.display(), v.display()))
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            Self::Set(values) => format!(
                "#{{{}}}",
                values
                    .iter()
                    .map(Value::display)
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            Self::List(values) => format!(
                "({})",
                values
                    .iter()
                    .map(Value::display)
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            Self::Symbol(v) => v.as_str().to_owned(),
            Self::Function(_) => "<fn>".into(),
            Self::Tuple(values) => format!(
                "[{}]",
                values
                    .iter()
                    .map(Value::display)
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            Self::Vector(values) => format!(
                "[{}]",
                values
                    .iter()
                    .map(Value::display)
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            Self::Iterator(iterator) => {
                if iterator.borrow().seq {
                    "<seq>".into()
                } else {
                    "<iterator>".into()
                }
            }
            Self::Var(value) => value.display(),
            Self::Extension(value) => format!("#ht[:handle {}]", value.handle),
            Self::Nil => "nil".into(),
        }
    }
    fn truthy(&self) -> bool {
        !matches!(self, Self::Nil | Self::Bool(false))
    }

    /// Stable structural hash used by protocol and collection conformance tests.
    pub fn stable_hash(&self) -> u64 {
        fn hash_value(value: &Value, state: &mut std::collections::hash_map::DefaultHasher) {
            let type_tag: u64 = match value {
                Value::Number(_) => 0,
                Value::Bool(_) => 1,
                Value::String(_) => 2,
                Value::Keyword(_) => 3,
                Value::Bytes(_) => 4,
                Value::ByteBuffer(_) => 5,
                Value::Array(_) => 6,
                Value::Object(_) => 7,
                Value::Promise(_) => 8,
                Value::Recur(_) => 9,
                Value::Map(_) => 10,
                Value::Set(_) => 11,
                Value::List(_) => 12,
                Value::Symbol(_) => 13,
                Value::Function(_) => 14,
                Value::Vector(_) => 15,
                Value::Iterator(_) => 16,
                Value::Var(_) => 17,
                Value::Extension(_) => 18,
                Value::Nil => 19,
                Value::Float(_) => 20,
                Value::BigInteger(_) => 21,
                Value::Decimal(_) => 22,
                Value::Character(_) => 23,
                Value::Regex(_) => 24,
                Value::Tagged(_, _) => 25,
                Value::Tuple(_) => 26,
            };
            type_tag.hash(state);
            match value {
                Value::Number(v) => v.hash(state),
                Value::Float(v) => v.to_bits().hash(state),
                Value::BigInteger(v) | Value::Decimal(v) | Value::Regex(v) => v.hash(state),
                Value::Character(v) => v.hash(state),
                Value::Tagged(tag, value) => {
                    tag.hash(state);
                    hash_value(value, state);
                }
                Value::Bool(v) => v.hash(state),
                Value::String(v) => v.hash(state),
                Value::Keyword(v) => v.hash(state),
                Value::Symbol(v) => v.hash(state),
                Value::Bytes(v) => v.hash(state),
                Value::ByteBuffer(v) => v.borrow().hash(state),
                Value::Array(v) => v.borrow().iter().for_each(|item| hash_value(item, state)),
                Value::Object(v) => v.borrow().iter().for_each(|(key, item)| {
                    key.hash(state);
                    hash_value(item, state);
                }),
                Value::Promise(v) => v.identity_address().hash(state),
                Value::Recur(v) => v.iter().for_each(|item| hash_value(item, state)),
                Value::Map(v) => {
                    let mut entries = v
                        .iter()
                        .map(|(key, item)| {
                            let mut h = std::collections::hash_map::DefaultHasher::new();
                            hash_value(key, &mut h);
                            hash_value(item, &mut h);
                            h.finish()
                        })
                        .collect::<Vec<_>>();
                    entries.sort_unstable();
                    entries.hash(state);
                }
                Value::Set(v) => {
                    let mut entries = v
                        .iter()
                        .map(|item| {
                            let mut h = std::collections::hash_map::DefaultHasher::new();
                            hash_value(item, &mut h);
                            h.finish()
                        })
                        .collect::<Vec<_>>();
                    entries.sort_unstable();
                    entries.hash(state);
                }
                Value::List(v) => v.iter().for_each(|item| hash_value(item, state)),
                Value::Tuple(v) => v.iter().for_each(|item| hash_value(item, state)),
                Value::Vector(v) => v.iter().for_each(|item| hash_value(item, state)),
                Value::Function(v) => Rc::as_ptr(v).hash(state),
                Value::Iterator(v) => Rc::as_ptr(v).hash(state),
                Value::Var(v) => v.identity_address().hash(state),
                Value::Extension(v) => {
                    v.provider.hash(state);
                    v.type_name.hash(state);
                    v.handle.hash(state);
                }
                Value::Nil => {}
            }
        }
        let mut state = std::collections::hash_map::DefaultHasher::new();
        hash_value(self, &mut state);
        state.finish()
    }
}

pub type ProtocolFn = Rc<dyn Fn(&[Value]) -> Result<Value, String>>;

#[derive(Default, Clone)]
pub struct ProtocolRegistry {
    methods: HashMap<(String, String), Vec<ProtocolFn>>,
}

#[allow(dead_code)]
impl ProtocolRegistry {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn register<F>(
        &mut self,
        protocol: impl Into<String>,
        method: impl Into<String>,
        function: F,
    ) where
        F: Fn(&[Value]) -> Result<Value, String> + 'static,
    {
        self.methods
            .entry((protocol.into(), method.into()))
            .or_default()
            .push(Rc::new(function));
    }

    pub fn invoke(
        &self,
        protocol: &str,
        method: &str,
        arguments: &[Value],
    ) -> Result<Value, String> {
        let implementations = self
            .methods
            .get(&(protocol.to_string(), method.to_string()))
            .ok_or_else(|| format!("missing protocol method: {protocol}/{method}"))?;
        let mut last_error = format!("missing protocol implementation: {protocol}/{method}");
        for implementation in implementations {
            match implementation(arguments) {
                Ok(value) => return Ok(value),
                Err(error) => last_error = error,
            }
        }
        Err(last_error)
    }

    pub fn contains(&self, protocol: &str, method: &str) -> bool {
        self.methods
            .get(&(protocol.to_string(), method.to_string()))
            .is_some_and(|implementations| !implementations.is_empty())
    }

    /// Returns the built-in collection protocol registry used by evaluator dispatch.
    pub fn core() -> Self {
        let mut registry = Self::new();
        registry.register("ICount", "count", protocol_count);
        registry.register("INth", "nth", protocol_nth);
        registry.register("ILookup", "lookup", protocol_lookup);
        registry.register("IFind", "find", protocol_find);
        registry.register("IFind", "has?", protocol_has);
        registry.register("IAssoc", "assoc", protocol_assoc);
        registry.register("IConj", "conj", protocol_conj);
        registry.register("IDissoc", "dissoc", protocol_dissoc);
        registry.register("IIter", "iter", protocol_iter);
        registry.register("IObjType", "meta", protocol_meta);
        registry.register("IObjType", "with-meta", protocol_with_meta);
        registry
    }
}

thread_local! {
    static ACTIVE_PROTOCOLS: RefCell<Option<ProtocolRegistry>> = const { RefCell::new(None) };
    static ACTIVE_PROMISE_PROVIDER: RefCell<Option<Rc<dyn PromiseProvider>>> = const { RefCell::new(None) };
    static HOST_CALL_HANDLER: RefCell<Option<Rc<dyn Fn(String, String, Vec<Value>) -> Result<Value, String>>>> = const { RefCell::new(None) };
}

/// Runs an evaluation with a registry available to protocol dispatch.
pub fn with_protocols<R>(registry: &ProtocolRegistry, operation: impl FnOnce() -> R) -> R {
    ACTIVE_PROTOCOLS.with(|active| {
        let previous = active.replace(Some(registry.clone()));
        let result = operation();
        active.replace(previous);
        result
    })
}

/// Runs an evaluation through the selected runtime promise provider.
pub fn with_promise_provider<R>(
    provider: Rc<dyn PromiseProvider>,
    operation: impl FnOnce() -> R,
) -> R {
    ACTIVE_PROMISE_PROVIDER.with(|active| {
        let previous = active.replace(Some(provider));
        let result = operation();
        active.replace(previous);
        result
    })
}

fn promise_provider() -> Rc<dyn PromiseProvider> {
    ACTIVE_PROMISE_PROVIDER.with(|active| {
        active
            .borrow()
            .clone()
            .unwrap_or_else(|| Rc::new(LocalPromiseProvider))
    })
}
/// Installs the explicit host-call boundary for one evaluation.
pub fn with_host_calls<R>(
    handler: Rc<dyn Fn(String, String, Vec<Value>) -> Result<Value, String>>,
    operation: impl FnOnce() -> R,
) -> R {
    HOST_CALL_HANDLER.with(|active| {
        let previous = active.replace(Some(handler));
        let result = operation();
        active.replace(previous);
        result
    })
}

pub trait ExtensionProvider {
    fn name(&self) -> &str;
    fn install(&self, protocols: &mut ProtocolRegistry);
    fn construct(&self, type_name: &str, arguments: &[Value]) -> Result<Value, String>;
}

#[derive(Default, Clone)]
pub struct ExtensionRegistry {
    providers: HashMap<String, Rc<dyn ExtensionProvider>>,
    loaded: HashSet<String>,
}

impl ExtensionRegistry {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn install<P: ExtensionProvider + 'static>(&mut self, provider: P) {
        self.providers
            .insert(provider.name().to_string(), Rc::new(provider));
    }

    pub fn contains(&self, name: &str) -> bool {
        self.providers.contains_key(name)
    }

    pub fn require(
        &mut self,
        name: &str,
        protocols: &mut ProtocolRegistry,
    ) -> Result<String, String> {
        let provider = self
            .providers
            .get(name)
            .cloned()
            .ok_or_else(|| format!("extension/not-found: {name}"))?;
        if self.loaded.insert(name.to_string()) {
            provider.install(protocols);
        }
        Ok(if self.loaded.len() == 1 {
            ":loaded".into()
        } else {
            ":loaded".into()
        })
    }

    pub fn construct(
        &self,
        provider: &str,
        type_name: &str,
        arguments: &[Value],
    ) -> Result<Value, String> {
        self.providers
            .get(provider)
            .ok_or_else(|| format!("extension/not-found: {provider}"))?
            .construct(type_name, arguments)
    }
}

#[allow(dead_code)]
#[derive(Debug, Clone, PartialEq)]
pub enum FileError {
    Unsupported,
    Denied,
    Invalid(String),
}

impl FileError {
    pub fn code(&self) -> &'static str {
        match self {
            Self::Unsupported => "unsupported",
            Self::Denied => "denied",
            Self::Invalid(_) => "invalid",
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub enum SocketError {
    Unsupported,
    Denied,
    Invalid(String),
}

impl SocketError {
    pub fn code(&self) -> &'static str {
        match self {
            Self::Unsupported => "unsupported",
            Self::Denied => "denied",
            Self::Invalid(_) => "invalid",
        }
    }
}

pub trait FileProvider {
    fn resolve(&self, root: &str, path: &str) -> Result<String, FileError>;
    fn read(&self, path: &str) -> Result<Promise, FileError>;
    fn write(&self, path: &str, bytes: Vec<u8>) -> Result<Promise, FileError>;
}

pub type SocketHandle = u64;
pub type SocketCallback = fn(SocketEvent);

#[derive(Debug, Clone, PartialEq)]
pub enum SocketEvent {
    Connected(SocketHandle),
    Data(SocketHandle, Vec<u8>),
    Closed(SocketHandle),
    Failed(SocketHandle, String),
}

pub trait SocketProvider {
    fn connect(
        &self,
        host: &str,
        port: u16,
        callback: SocketCallback,
    ) -> Result<SocketHandle, SocketError>;
    fn send(&self, socket: SocketHandle, bytes: &[u8]) -> Result<usize, SocketError>;
    fn close(&self, socket: SocketHandle) -> Result<(), SocketError>;
}

#[cfg(not(target_arch = "wasm32"))]
use std::io::Write;
#[cfg(not(target_arch = "wasm32"))]
use std::net::TcpStream;
#[cfg(not(target_arch = "wasm32"))]
use std::path::{Path, PathBuf};

#[cfg(not(target_arch = "wasm32"))]
#[derive(Debug, Clone)]
pub struct NativeFileProvider {
    root: PathBuf,
}

#[cfg(not(target_arch = "wasm32"))]
impl NativeFileProvider {
    pub fn new(root: impl AsRef<Path>) -> Self {
        Self {
            root: root.as_ref().to_path_buf(),
        }
    }

    fn scoped(&self, path: &str) -> Result<PathBuf, FileError> {
        let relative = Path::new(path);
        if relative.is_absolute() {
            return if relative == self.root || relative.strip_prefix(&self.root).is_ok() {
                Ok(relative.to_path_buf())
            } else {
                Err(FileError::Denied)
            };
        }
        if relative
            .components()
            .any(|component| matches!(component, std::path::Component::ParentDir))
        {
            return Err(FileError::Denied);
        }
        Ok(self.root.join(relative))
    }
}

#[cfg(not(target_arch = "wasm32"))]
impl FileProvider for NativeFileProvider {
    fn resolve(&self, root: &str, path: &str) -> Result<String, FileError> {
        if Path::new(root) != self.root {
            return Err(FileError::Denied);
        }
        self.scoped(path)
            .map(|path| path.to_string_lossy().into_owned())
    }

    fn read(&self, path: &str) -> Result<Promise, FileError> {
        let path = self.scoped(path)?;
        let promise = Promise::new();
        match std::fs::read(path) {
            Ok(bytes) => {
                promise.resolve(Value::Bytes(bytes));
            }
            Err(error) => {
                promise.reject(error.to_string());
            }
        }
        Ok(promise)
    }

    fn write(&self, path: &str, bytes: Vec<u8>) -> Result<Promise, FileError> {
        let path = self.scoped(path)?;
        let promise = Promise::new();
        match std::fs::write(path, bytes) {
            Ok(()) => {
                promise.resolve(Value::Nil);
            }
            Err(error) => {
                promise.reject(error.to_string());
            }
        }
        Ok(promise)
    }
}

#[cfg(not(target_arch = "wasm32"))]
#[derive(Debug, Default)]
pub struct NativeSocketProvider {
    next_handle: Cell<SocketHandle>,
    sockets: RefCell<HashMap<SocketHandle, TcpStream>>,
    callbacks: RefCell<HashMap<SocketHandle, SocketCallback>>,
}

#[cfg(not(target_arch = "wasm32"))]
impl SocketProvider for NativeSocketProvider {
    fn connect(
        &self,
        host: &str,
        port: u16,
        callback: SocketCallback,
    ) -> Result<SocketHandle, SocketError> {
        if host.is_empty() || port == 0 {
            return Err(SocketError::Invalid("host and port are required".into()));
        }
        let stream = TcpStream::connect((host, port))
            .map_err(|error| SocketError::Invalid(error.to_string()))?;
        let handle = self.next_handle.get();
        self.next_handle.set(handle + 1);
        self.sockets.borrow_mut().insert(handle, stream);
        self.callbacks.borrow_mut().insert(handle, callback);
        callback(SocketEvent::Connected(handle));
        Ok(handle)
    }

    fn send(&self, socket: SocketHandle, bytes: &[u8]) -> Result<usize, SocketError> {
        let mut sockets = self.sockets.borrow_mut();
        let stream = sockets
            .get_mut(&socket)
            .ok_or_else(|| SocketError::Invalid("unknown socket".into()))?;
        stream
            .write_all(bytes)
            .map_err(|error| SocketError::Invalid(error.to_string()))?;
        drop(sockets);
        if let Some(callback) = self.callbacks.borrow().get(&socket).copied() {
            callback(SocketEvent::Data(socket, bytes.to_vec()));
        }
        Ok(bytes.len())
    }

    fn close(&self, socket: SocketHandle) -> Result<(), SocketError> {
        if self.sockets.borrow_mut().remove(&socket).is_none() {
            return Err(SocketError::Invalid("unknown socket".into()));
        }
        if let Some(callback) = self.callbacks.borrow_mut().remove(&socket) {
            callback(SocketEvent::Closed(socket));
        }
        Ok(())
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ProviderCapabilities {
    pub file: bool,
    pub socket: bool,
}

pub struct ProviderRegistry {
    file: Option<Rc<dyn FileProvider>>,
    socket: Option<Rc<dyn SocketProvider>>,
    promise: Rc<dyn PromiseProvider>,
}

impl Default for ProviderRegistry {
    fn default() -> Self {
        Self {
            file: None,
            socket: None,
            promise: Rc::new(LocalPromiseProvider),
        }
    }
}

impl ProviderRegistry {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn install_file<P: FileProvider + 'static>(&mut self, provider: P) {
        self.file = Some(Rc::new(provider));
    }
    pub fn install_socket<P: SocketProvider + 'static>(&mut self, provider: P) {
        self.socket = Some(Rc::new(provider));
    }
    pub fn install_promise<P: PromiseProvider + 'static>(&mut self, provider: P) {
        self.promise = Rc::new(provider);
    }
    pub fn promise(&self) -> Rc<dyn PromiseProvider> {
        self.promise.clone()
    }
    pub fn file(&self) -> Option<Rc<dyn FileProvider>> {
        self.file.clone()
    }
    pub fn socket(&self) -> Option<Rc<dyn SocketProvider>> {
        self.socket.clone()
    }
    pub fn capabilities(&self) -> ProviderCapabilities {
        ProviderCapabilities {
            file: self.file.is_some(),
            socket: self.socket.is_some(),
        }
    }
}

#[derive(Debug, Clone)]
pub struct MemoryFileProvider {
    root: String,
    files: Rc<RefCell<HashMap<String, Vec<u8>>>>,
}

impl MemoryFileProvider {
    pub fn new(root: impl Into<String>) -> Self {
        Self {
            root: root.into().trim_end_matches('/').to_string(),
            files: Rc::new(RefCell::new(HashMap::new())),
        }
    }

    fn within_root(&self, path: &str) -> bool {
        path == self.root
            || path
                .strip_prefix(&self.root)
                .is_some_and(|rest| rest.starts_with('/'))
    }

    pub fn insert(&self, path: &str, bytes: Vec<u8>) -> Result<(), FileError> {
        if !self.within_root(path) {
            return Err(FileError::Denied);
        }
        self.files.borrow_mut().insert(path.to_string(), bytes);
        Ok(())
    }
}

impl FileProvider for MemoryFileProvider {
    fn resolve(&self, root: &str, path: &str) -> Result<String, FileError> {
        if root != self.root || path.starts_with('/') {
            return Err(FileError::Denied);
        }
        let mut result = self.root.clone();
        for segment in path.split('/') {
            match segment {
                "" | "." => {}
                ".." => return Err(FileError::Denied),
                segment if segment.contains('\0') => {
                    return Err(FileError::Invalid("path contains NUL".into()))
                }
                segment => {
                    result.push('/');
                    result.push_str(segment);
                }
            }
        }
        Ok(result)
    }

    fn read(&self, path: &str) -> Result<Promise, FileError> {
        if !self.within_root(path) {
            return Err(FileError::Denied);
        }
        let promise = Promise::new();
        match self.files.borrow().get(path) {
            Some(bytes) => {
                promise.resolve(Value::Bytes(bytes.clone()));
            }
            None => {
                promise.reject("file not found");
            }
        }
        Ok(promise)
    }

    fn write(&self, path: &str, bytes: Vec<u8>) -> Result<Promise, FileError> {
        if !self.within_root(path) {
            return Err(FileError::Denied);
        }
        self.files.borrow_mut().insert(path.to_string(), bytes);
        let promise = Promise::new();
        promise.resolve(Value::Nil);
        Ok(promise)
    }
}

#[derive(Debug, Default, Clone, Copy)]
pub struct UnsupportedFileProvider;

impl FileProvider for UnsupportedFileProvider {
    fn resolve(&self, _root: &str, _path: &str) -> Result<String, FileError> {
        Err(FileError::Unsupported)
    }
    fn read(&self, _path: &str) -> Result<Promise, FileError> {
        Err(FileError::Unsupported)
    }
    fn write(&self, _path: &str, _bytes: Vec<u8>) -> Result<Promise, FileError> {
        Err(FileError::Unsupported)
    }
}

#[derive(Debug, Clone)]
pub struct LoopbackSocketProvider {
    next_handle: Rc<Cell<SocketHandle>>,
    callbacks: Rc<RefCell<HashMap<SocketHandle, SocketCallback>>>,
}

impl Default for LoopbackSocketProvider {
    fn default() -> Self {
        Self {
            next_handle: Rc::new(Cell::new(1)),
            callbacks: Rc::new(RefCell::new(HashMap::new())),
        }
    }
}

impl SocketProvider for LoopbackSocketProvider {
    fn connect(
        &self,
        host: &str,
        port: u16,
        callback: SocketCallback,
    ) -> Result<SocketHandle, SocketError> {
        if host.is_empty() || port == 0 {
            return Err(SocketError::Invalid("host and port are required".into()));
        }
        let handle = self.next_handle.get();
        self.next_handle.set(handle + 1);
        self.callbacks.borrow_mut().insert(handle, callback);
        callback(SocketEvent::Connected(handle));
        Ok(handle)
    }

    fn send(&self, socket: SocketHandle, bytes: &[u8]) -> Result<usize, SocketError> {
        let callback = self
            .callbacks
            .borrow()
            .get(&socket)
            .copied()
            .ok_or_else(|| SocketError::Invalid("unknown socket".into()))?;
        callback(SocketEvent::Data(socket, bytes.to_vec()));
        Ok(bytes.len())
    }

    fn close(&self, socket: SocketHandle) -> Result<(), SocketError> {
        let callback = self
            .callbacks
            .borrow_mut()
            .remove(&socket)
            .ok_or_else(|| SocketError::Invalid("unknown socket".into()))?;
        callback(SocketEvent::Closed(socket));
        Ok(())
    }
}

#[derive(Debug, Default, Clone, Copy)]
pub struct UnsupportedSocketProvider;

impl SocketProvider for UnsupportedSocketProvider {
    fn connect(
        &self,
        _host: &str,
        _port: u16,
        _callback: SocketCallback,
    ) -> Result<SocketHandle, SocketError> {
        Err(SocketError::Unsupported)
    }
    fn send(&self, _socket: SocketHandle, _bytes: &[u8]) -> Result<usize, SocketError> {
        Err(SocketError::Unsupported)
    }
    fn close(&self, _socket: SocketHandle) -> Result<(), SocketError> {
        Err(SocketError::Unsupported)
    }
}

pub fn receiver_category(value: &Value) -> &'static str {
    match value {
        Value::Nil => "nil",
        Value::Number(_) | Value::Float(_) | Value::BigInteger(_) | Value::Decimal(_) => "number",
        Value::Character(_) => "character",
        Value::Regex(_) => "pattern",
        Value::Tagged(_, _) => "tagged",
        Value::Bool(_) => "boolean",
        Value::String(_) => "string",
        Value::Keyword(_) => "keyword",
        Value::Symbol(_) => "symbol",
        Value::Function(_) => "function",
        Value::Bytes(_) | Value::ByteBuffer(_) => "bytes",
        Value::Array(_) => "array",
        Value::Object(_) => "object",
        Value::Promise(_) => "promise",
        Value::Recur(_) => "recur",
        Value::List(_) => "list",
        Value::Tuple(_) => "tuple",
        Value::Vector(_) => "vector",
        Value::Map(_) => "map",
        Value::Set(_) => "set",
        Value::Iterator(_) => "iterator",
        Value::Var(_) => "var",
        Value::Extension(_) => "extension",
    }
}

fn parse(source: &str) -> Result<Form, String> {
    crate::kernel::parse(source)
}
fn parse_forms(source: &str) -> Result<Vec<Form>, String> {
    crate::kernel::parse_forms(source)
}

fn arithmetic(op: &str, args: &[Form], env: &mut HashMap<String, Value>) -> Result<Value, String> {
    if args.is_empty() {
        return Err(format!("{op} expects arguments"));
    }
    let values: Result<Vec<i64>, String> = args
        .iter()
        .map(|f| match eval(f, env)? {
            Value::Number(v) => Ok(v),
            _ => Err(format!("{op} expects numbers")),
        })
        .collect();
    let values = values?;
    let result = values.iter().skip(1).try_fold(values[0], |r, v| match op {
        "+" => Ok::<i64, String>(r + v),
        "-" => Ok::<i64, String>(r - v),
        "*" => Ok::<i64, String>(r * v),
        "/" => {
            if *v == 0 {
                Err("division by zero".into())
            } else {
                Ok::<i64, String>(r / v)
            }
        }
        "%" => {
            if *v == 0 {
                Err("division by zero".into())
            } else {
                Ok::<i64, String>(r % v)
            }
        }
        _ => unreachable!(),
    })?;
    Ok(Value::Number(result))
}

fn bit_operation(
    op: &str,
    args: &[Form],
    env: &mut HashMap<String, Value>,
) -> Result<Value, String> {
    let values = args
        .iter()
        .map(|form| eval(form, env))
        .collect::<Result<Vec<_>, _>>()?;
    let integer = |value: &Value| match value {
        Value::Number(value) => Ok(*value as i32),
        _ => Err(format!("{op} expects integers")),
    };
    match op {
        "bit-not" => {
            if values.len() != 1 {
                return Err("bit-not expects one integer".into());
            }
            Ok(Value::Number((!integer(&values[0])?) as i64))
        }
        "bit-and" | "bit-or" | "bit-xor" => {
            if values.len() != 2 {
                return Err(format!("{op} expects two integers"));
            }
            let a = integer(&values[0])?;
            let b = integer(&values[1])?;
            let result = match op {
                "bit-and" => a & b,
                "bit-or" => a | b,
                _ => a ^ b,
            };
            Ok(Value::Number(result as i64))
        }
        "bit-shift-left" | "bit-shift-right" => {
            if values.len() != 2 {
                return Err(format!("{op} expects an integer and distance"));
            }
            let value = integer(&values[0])?;
            let distance = match &values[1] {
                Value::Number(distance) if (0..=31).contains(distance) => *distance as u32,
                _ => return Err("distance must be in the range 0..31".into()),
            };
            let result = if op == "bit-shift-left" {
                value.wrapping_shl(distance)
            } else {
                value.wrapping_shr(distance)
            };
            Ok(Value::Number(result as i64))
        }
        _ => Err(format!("unknown bit operation: {op}")),
    }
}

fn comparison(op: &str, args: &[Form], env: &mut HashMap<String, Value>) -> Result<Value, String> {
    if args.len() < 2 {
        return Err(format!("{op} expects at least two arguments"));
    }
    let values = args
        .iter()
        .map(|form| eval(form, env))
        .collect::<Result<Vec<_>, _>>()?;
    let numbers = values
        .iter()
        .map(|value| match value {
            Value::Number(number) => Ok(*number),
            _ => Err(format!("{op} expects numbers")),
        })
        .collect::<Result<Vec<_>, String>>()?;
    Ok(Value::Bool(numbers.windows(2).all(|pair| match op {
        "<" => pair[0] < pair[1],
        ">" => pair[0] > pair[1],
        "<=" => pair[0] <= pair[1],
        ">=" => pair[0] >= pair[1],
        _ => false,
    })))
}

fn value_index(value: &Value) -> Result<usize, String> {
    match value {
        Value::Number(index) if *index >= 0 => Ok(*index as usize),
        _ => Err("index must be a non-negative integer".into()),
    }
}

fn value_to_metadata(value: &Value) -> Result<MetadataValue, String> {
    match value {
        Value::Nil => Ok(MetadataValue::Nil),
        Value::Bool(value) => Ok(MetadataValue::Boolean(*value)),
        Value::Number(value) => Ok(MetadataValue::Number(*value)),
        Value::String(value) => Ok(MetadataValue::String(value.clone())),
        Value::Keyword(value) => Ok(MetadataValue::Keyword(value.clone())),
        Value::Symbol(value) => Ok(MetadataValue::Symbol(value.clone())),
        Value::Tuple(values) => Ok(MetadataValue::Vector(
            values
                .iter()
                .map(value_to_metadata)
                .collect::<Result<_, _>>()?,
        )),
        Value::Vector(values) => Ok(MetadataValue::Vector(
            values
                .iter()
                .map(value_to_metadata)
                .collect::<Result<_, _>>()?,
        )),
        Value::List(values) => Ok(MetadataValue::List(
            values
                .iter()
                .map(value_to_metadata)
                .collect::<Result<_, _>>()?,
        )),
        Value::Set(values) => Ok(MetadataValue::Set(
            values
                .iter()
                .map(value_to_metadata)
                .collect::<Result<_, _>>()?,
        )),
        Value::Map(values) => Ok(MetadataValue::Map(
            values
                .iter()
                .map(|(key, value)| Ok((value_to_metadata(key)?, value_to_metadata(value)?)))
                .collect::<Result<_, String>>()?,
        )),
        _ => Err("value cannot be stored in runtime-neutral metadata".into()),
    }
}

fn metadata_to_value(value: &MetadataValue) -> Result<Value, String> {
    match value {
        MetadataValue::Nil => Ok(Value::Nil),
        MetadataValue::Boolean(value) => Ok(Value::Bool(*value)),
        MetadataValue::Number(value) => Ok(Value::Number(*value)),
        MetadataValue::String(value) => Ok(Value::String(value.clone())),
        MetadataValue::Keyword(value) => Ok(Value::Keyword(value.clone())),
        MetadataValue::Symbol(value) => Ok(Value::Symbol(value.clone())),
        MetadataValue::Vector(values) => Ok(Value::Vector(
            values
                .iter()
                .map(metadata_to_value)
                .collect::<Result<_, _>>()?,
        )),
        MetadataValue::List(values) => Ok(Value::List(
            values
                .iter()
                .map(metadata_to_value)
                .collect::<Result<_, _>>()?,
        )),
        MetadataValue::Set(values) => Ok(Value::Set(
            values
                .iter()
                .map(metadata_to_value)
                .collect::<Result<Vec<_>, _>>()?
                .into(),
        )),
        MetadataValue::Map(values) => Ok(Value::Map(
            values
                .iter()
                .map(|(key, value)| Ok((metadata_to_value(key)?, metadata_to_value(value)?)))
                .collect::<Result<Vec<_>, String>>()?
                .into_iter()
                .collect(),
        )),
        _ => Err("metadata value is not supported by the L0 evaluator".into()),
    }
}

fn value_metadata(value: &Value) -> Option<Rc<Metadata>> {
    match value {
        Value::Symbol(value) => value.meta().cloned(),
        Value::Tuple(value) => value.meta().cloned(),
        Value::Vector(value) => value.meta().cloned(),
        Value::List(value) => value.meta().cloned(),
        Value::Map(value) => value.meta().cloned(),
        Value::Set(value) => value.meta().cloned(),
        Value::Var(value) => value.hara_metadata(),
        _ => None,
    }
}

fn protocol_meta(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() != 1 {
        return Err("IObjType/meta expects one argument".into());
    }
    match value_metadata(&arguments[0]) {
        None => Ok(Value::Nil),
        Some(metadata) => metadata_to_value(&MetadataValue::Map(metadata.entries().to_vec())),
    }
}

fn protocol_with_meta(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() != 2 {
        return Err("IObjType/with-meta expects a value and metadata map".into());
    }
    let MetadataValue::Map(entries) = value_to_metadata(&arguments[1])? else {
        return Err("IObjType/with-meta expects a metadata map".into());
    };
    attach_metadata(arguments[0].clone(), Metadata::new(entries))
}

fn protocol_count(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() == 1 {
        collection_count(&arguments[0])
    } else {
        Err("ICount/count expects one argument".into())
    }
}

fn protocol_nth(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() != 2 {
        return Err("INth/nth expects a collection and index".into());
    }
    if let Value::Bytes(bytes) = &arguments[0] {
        let index = value_index(&arguments[1])?;
        return bytes
            .get(index)
            .map(|byte| Value::Number(*byte as i8 as i64))
            .ok_or_else(|| "nth index out of bounds".into());
    }
    if let Value::ByteBuffer(bytes) = &arguments[0] {
        let index = value_index(&arguments[1])?;
        return bytes
            .borrow()
            .get(index)
            .map(|byte| Value::Number(*byte as i8 as i64))
            .ok_or_else(|| "nth index out of bounds".into());
    }
    collection_nth(&arguments[0], &arguments[1])
}

fn protocol_lookup(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() == 2 || arguments.len() == 3 {
        collection_get(
            &arguments[0],
            &arguments[1],
            arguments.get(2).cloned().unwrap_or(Value::Nil),
        )
    } else {
        Err("ILookup/lookup expects a collection, key, and optional default".into())
    }
}

fn protocol_assoc(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() == 3 {
        collection_assoc(&arguments[0], &arguments[1], arguments[2].clone())
    } else {
        Err("IAssoc/assoc expects a collection, key, and value".into())
    }
}

fn protocol_dissoc(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() == 2 {
        collection_dissoc(&arguments[0], &[arguments[1].clone()])
    } else {
        Err("IDissoc/dissoc expects a collection and key".into())
    }
}

fn pair_parts(value: &Value) -> Option<(Value, Value)> {
    match value {
        Value::Tuple(values) if values.len() >= 2 => Some((
            values.get(0).unwrap().clone(),
            values.get(1).unwrap().clone(),
        )),
        Value::Vector(values) if values.len() >= 2 => Some((values[0].clone(), values[1].clone())),
        Value::List(values) if values.len() >= 2 => Some((values[0].clone(), values[1].clone())),
        _ => None,
    }
}

fn indexed_find(value: Option<&Value>, index: usize) -> Result<Value, String> {
    Ok(value
        .map(|value| {
            Value::Vector(PVector::from_iter([
                Value::Number(index as i64),
                value.clone(),
            ]))
        })
        .unwrap_or(Value::Nil))
}

fn protocol_find(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() != 2 {
        return Err("IFind/find expects a collection and key".into());
    }
    let collection = &arguments[0];
    let key = &arguments[1];
    match collection {
        Value::Map(values) => Ok(values
            .find_entry(key)
            .map(|(candidate, value)| {
                Value::Vector(PVector::from_iter([candidate.clone(), value.clone()]))
            })
            .unwrap_or(Value::Nil)),
        Value::Object(values) => {
            let key = match key {
                Value::String(value) => value.as_str(),
                Value::Keyword(value) => value.as_str(),
                _ => return Err("IFind/find object expects a string or keyword key".into()),
            };
            Ok(values
                .borrow()
                .iter()
                .find(|(candidate, _)| candidate == key)
                .map(|(candidate, value)| {
                    Value::Vector(PVector::from_iter([
                        Value::String(candidate.clone()),
                        value.clone(),
                    ]))
                })
                .unwrap_or(Value::Nil))
        }
        Value::Set(values) => Ok(values.get(key).cloned().unwrap_or(Value::Nil)),
        Value::Tuple(values) => indexed_find(values.get(value_index(key)?), value_index(key)?),
        Value::Vector(values) => indexed_find(values.get(value_index(key)?), value_index(key)?),
        Value::List(values) => indexed_find(values.get(value_index(key)?), value_index(key)?),
        _ => Err("IFind/find has no implementation for this value".into()),
    }
}

fn protocol_has(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() != 2 {
        return Err("IFind/has? expects a collection and key".into());
    }
    let collection = &arguments[0];
    let key = &arguments[1];
    let found = match collection {
        Value::Map(values) => values.get(key).is_some(),
        Value::Object(values) => match key {
            Value::String(key) => values
                .borrow()
                .iter()
                .any(|(candidate, _)| candidate == key),
            Value::Keyword(key) => values
                .borrow()
                .iter()
                .any(|(candidate, _)| candidate == key.as_str()),
            _ => false,
        },
        Value::Set(values) => values.contains(key),
        Value::Tuple(values) => value_index(key)
            .map(|index| index < values.len())
            .unwrap_or(false),
        Value::Vector(values) => value_index(key)
            .map(|index| index < values.len())
            .unwrap_or(false),
        Value::List(values) => value_index(key)
            .map(|index| index < values.len())
            .unwrap_or(false),
        _ => return Err("IFind/has? has no implementation for this value".into()),
    };
    Ok(Value::Bool(found))
}

fn protocol_iter(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() == 1 && matches!(arguments[0], Value::Iterator(_)) {
        Ok(arguments[0].clone())
    } else {
        Err("IIter/iter has no implementation for this value".into())
    }
}

fn tuple_push_last(values: &PTuple<Value>, item: Value) -> Result<Value, String> {
    Ok(Value::Tuple(Box::new(values.push_last(item)?)))
}

fn tuple_push_first(values: &PTuple<Value>, item: Value) -> Result<Value, String> {
    Ok(Value::Tuple(Box::new(values.push_first(item)?)))
}

fn protocol_conj(arguments: &[Value]) -> Result<Value, String> {
    if arguments.len() != 2 {
        return Err("IConj/conj expects a collection and value".into());
    }
    let collection = &arguments[0];
    let item = &arguments[1];
    match collection {
        Value::Tuple(values) => tuple_push_last(values, item.clone()),
        Value::Vector(values) => {
            let output = values.push_last(item.clone());
            Ok(Value::Vector(output))
        }
        Value::List(values) => {
            let output = std::iter::once(item.clone())
                .chain(values.iter().cloned())
                .collect();
            Ok(Value::List(output))
        }
        Value::Set(values) => Ok(Value::Set(values.conj_value(item.clone()))),
        Value::Map(values) => {
            let (entry_key, entry_value) = pair_parts(item)
                .ok_or_else(|| "IConj/conj map expects a two-element entry".to_string())?;
            Ok(Value::Map(values.assoc_value(entry_key, entry_value)))
        }
        _ => Err("IConj/conj expects a collection".into()),
    }
}

fn protocol_call(protocol: &str, method: &str, arguments: &[Value]) -> Result<Value, String> {
    ACTIVE_PROTOCOLS.with(|active| {
        active
            .borrow()
            .as_ref()
            .cloned()
            .unwrap_or_else(ProtocolRegistry::core)
            .invoke(protocol, method, arguments)
    })
}

fn promise_value(value: &Value, operation: &str) -> Result<Promise, String> {
    match value {
        Value::Promise(promise) => Ok(promise.clone()),
        _ => Err(format!("{operation} expects a promise")),
    }
}

fn promise_state_value(promise: &Promise) -> Value {
    Value::Keyword(
        match promise.state() {
            PromiseState::Pending => "pending",
            PromiseState::Fulfilled(_) => "fulfilled",
            PromiseState::Rejected(error) if error == "cancelled" => "cancelled",
            PromiseState::Rejected(_) => "rejected",
        }
        .into(),
    )
}

fn promise_value_result(promise: &Promise) -> Result<Value, String> {
    match promise.state() {
        PromiseState::Pending => Err("promise is pending".into()),
        PromiseState::Fulfilled(value) => Ok(value),
        PromiseState::Rejected(error) => Err(error),
    }
}

fn promise_all(values: Vec<Value>) -> Promise {
    let output = Promise::new();
    if values.is_empty() {
        output.resolve(Value::Array(Rc::new(RefCell::new(Vec::new()))));
        return output;
    }
    let count = values.len();
    let remaining = Rc::new(Cell::new(count));
    let results = Rc::new(RefCell::new(vec![Value::Nil; count]));
    for (index, value) in values.into_iter().enumerate() {
        let source = match value {
            Value::Promise(promise) => promise,
            value => {
                let promise = Promise::new();
                promise.resolve(value);
                promise
            }
        };
        let destination = output.clone();
        let remaining = remaining.clone();
        let results = results.clone();
        source.on_settle(Rc::new(move |state| match state {
            PromiseState::Fulfilled(value) => {
                results.borrow_mut()[index] = value;
                let left = remaining.get() - 1;
                remaining.set(left);
                if left == 0 {
                    destination.resolve(Value::Array(Rc::new(RefCell::new(
                        results.borrow().clone(),
                    ))));
                }
            }
            PromiseState::Rejected(error) => {
                destination.reject(error);
            }
            PromiseState::Pending => {}
        }));
    }
    output
}
fn settle_promise_result(destination: &Promise, result: Result<Value, String>) {
    match result {
        Ok(Value::Promise(source)) => {
            destination.adopt(&source);
        }
        Ok(value) => {
            destination.resolve(value);
        }
        Err(error) => {
            destination.reject(error);
        }
    }
}

fn finish_promise(destination: Promise, original: PromiseState, cleanup: Result<Value, String>) {
    let preserved_destination = destination.clone();
    let preserve = move || match original.clone() {
        PromiseState::Fulfilled(value) => {
            preserved_destination.resolve(value);
        }
        PromiseState::Rejected(error) => {
            preserved_destination.reject(error);
        }
        PromiseState::Pending => {}
    };
    match cleanup {
        Ok(Value::Promise(cleanup)) => {
            cleanup.on_settle(Rc::new(move |state| match state {
                PromiseState::Fulfilled(_) => preserve(),
                PromiseState::Rejected(error) => {
                    destination.reject(error);
                }
                PromiseState::Pending => {}
            }));
        }
        Ok(_) => preserve(),
        Err(error) => {
            destination.reject(error);
        }
    }
}

fn promise_chain(source: Promise, operation: &str, function: Rc<Function>) -> Promise {
    let output = Promise::new();
    let operation = operation.to_string();
    let destination = output.clone();
    source.on_settle(Rc::new(move |state| match state.clone() {
        PromiseState::Fulfilled(value)
            if operation == "promise/map" || operation == "promise/then" =>
        {
            settle_promise_result(&destination, call_function(&function, vec![value]));
        }
        PromiseState::Rejected(error)
            if operation == "promise/recover" || operation == "promise/catch" =>
        {
            settle_promise_result(
                &destination,
                call_function(&function, vec![Value::String(error)]),
            );
        }
        PromiseState::Fulfilled(_) | PromiseState::Rejected(_)
            if operation == "promise/finally" =>
        {
            finish_promise(
                destination.clone(),
                state,
                call_function(&function, Vec::new()),
            );
        }
        PromiseState::Fulfilled(value) => {
            destination.resolve(value);
        }
        PromiseState::Rejected(error) => {
            destination.reject(error);
        }
        PromiseState::Pending => {}
    }));
    output
}

fn string_value<'a>(value: &'a Value, operation: &str) -> Result<&'a str, String> {
    match value {
        Value::String(value) => Ok(value),
        _ => Err(format!("{operation} expects a string")),
    }
}

fn string_operation(operation: &str, values: Vec<Value>) -> Result<Value, String> {
    let pair = |values: &[Value]| -> Result<(String, String), String> {
        if values.len() != 2 {
            return Err(format!("{operation} expects two strings"));
        }
        Ok((
            string_value(&values[0], operation)?.to_owned(),
            string_value(&values[1], operation)?.to_owned(),
        ))
    };
    match operation {
        "str/comp" | "str/lt?" | "str/gt?" => {
            let (left, right) = pair(&values)?;
            let ordering = left.cmp(&right);
            Ok(match operation {
                "str/comp" => Value::Number(match ordering {
                    std::cmp::Ordering::Less => -1,
                    std::cmp::Ordering::Equal => 0,
                    std::cmp::Ordering::Greater => 1,
                }),
                "str/lt?" => Value::Bool(ordering.is_lt()),
                _ => Value::Bool(ordering.is_gt()),
            })
        }
        "str/starts-with?" | "str/ends-with?" => {
            let (text, part) = pair(&values)?;
            Ok(Value::Bool(if operation == "str/starts-with?" {
                text.starts_with(&part)
            } else {
                text.ends_with(&part)
            }))
        }
        "str/pad-left" | "str/pad-right" => {
            if values.len() != 3 {
                return Err(format!(
                    "{operation} expects a string, length, and padding string"
                ));
            }
            let text = string_value(&values[0], operation)?;
            let length = value_index(&values[1])?;
            let padding = string_value(&values[2], operation)?;
            let text_length = text.chars().count();
            if padding.is_empty() || text_length >= length {
                return Ok(Value::String(text.into()));
            }
            let needed = length - text_length;
            let fill = padding.chars().cycle().take(needed).collect::<String>();
            Ok(Value::String(if operation == "str/pad-left" {
                format!("{fill}{text}")
            } else {
                format!("{text}{fill}")
            }))
        }
        "str/char" => {
            if values.len() != 2 {
                return Err("str/char expects a string and index".into());
            }
            let text = string_value(&values[0], operation)?;
            let index = value_index(&values[1])?;
            text.chars()
                .nth(index)
                .map(|value| Value::String(value.to_string()))
                .ok_or_else(|| "str/char index out of bounds".into())
        }
        "str/split" => {
            let (text, separator) = pair(&values)?;
            let parts = text
                .split(&separator)
                .map(|part| Value::String(part.into()))
                .collect();
            Ok(Value::Array(Rc::new(RefCell::new(parts))))
        }
        "str/join" => {
            if values.len() != 2 {
                return Err("str/join expects a separator and collection".into());
            }
            let separator = string_value(&values[0], operation)?;
            let parts = iterator_values(values[1].clone())?
                .into_iter()
                .map(|value| match value {
                    Value::String(value) => Ok(value),
                    _ => Err("str/join expects a collection of strings".into()),
                })
                .collect::<Result<Vec<String>, String>>()?;
            Ok(Value::String(parts.join(separator)))
        }
        "str/index-of" => {
            if values.len() != 2 && values.len() != 3 {
                return Err("str/index-of expects a string, substring, and optional offset".into());
            }
            let text = string_value(&values[0], operation)?;
            let part = string_value(&values[1], operation)?;
            let offset = if values.len() == 3 {
                value_index(&values[2])?
            } else {
                0
            };
            let byte_offset = text
                .char_indices()
                .nth(offset)
                .map_or(text.len(), |(index, _)| index);
            let found = text[byte_offset..]
                .find(part)
                .map(|index| text[..byte_offset + index].chars().count() as i64);
            Ok(Value::Number(found.unwrap_or(-1)))
        }
        "str/substring" => {
            if values.len() != 2 && values.len() != 3 {
                return Err("str/substring expects a string, start, and optional end".into());
            }
            let text = string_value(&values[0], operation)?;
            let start = value_index(&values[1])?;
            let chars = text.chars().collect::<Vec<_>>();
            let end = if values.len() == 3 {
                value_index(&values[2])?
            } else {
                chars.len()
            };
            if start > end || end > chars.len() {
                return Err("str/substring range is out of bounds".into());
            }
            Ok(Value::String(chars[start..end].iter().collect()))
        }
        "str/to-fixed" => {
            if values.len() != 2 {
                return Err("str/to-fixed expects a number and precision".into());
            }
            let number = match values[0] {
                Value::Number(number) => number as f64,
                _ => return Err("str/to-fixed expects a number and precision".into()),
            };
            let precision = value_index(&values[1])?;
            if precision > 100 {
                return Err("str/to-fixed precision must be in the range 0..100".into());
            }
            Ok(Value::String(format!("{number:.precision$}")))
        }
        "str/replace" => {
            if values.len() != 3 {
                return Err("str/replace expects a string, match, and replacement".into());
            }
            Ok(Value::String(string_value(&values[0], operation)?.replace(
                string_value(&values[1], operation)?,
                string_value(&values[2], operation)?,
            )))
        }
        "str/trim-left" | "str/trim-right" => {
            if values.len() != 1 {
                return Err(format!("{operation} expects one string"));
            }
            let text = string_value(&values[0], operation)?;
            Ok(Value::String(if operation == "str/trim-left" {
                text.trim_start().into()
            } else {
                text.trim_end().into()
            }))
        }
        _ => Err(format!("unknown string operation: {operation}")),
    }
}
fn marker_key(value: &Value, operation: &str) -> Result<String, String> {
    match value {
        Value::String(key) => Ok(key.clone()),
        Value::Keyword(key) => Ok(key.as_str().to_owned()),
        _ => Err(format!("{operation} expects a string key")),
    }
}

fn dot_call(
    receiver: Value,
    method: &Form,
    env: &mut HashMap<String, Value>,
) -> Result<Value, String> {
    let parts = match method {
        Form::List(parts) if !parts.is_empty() => parts,
        _ => return Err("dot call expects a method list".into()),
    };
    let name = match &parts[0] {
        Form::Symbol(name) => name.as_str(),
        _ => return Err("dot method must be a symbol".into()),
    };
    let args = parts[1..]
        .iter()
        .map(|form| eval(form, env))
        .collect::<Result<Vec<_>, _>>()?;
    match receiver {
        Value::Array(array) => match name {
            "get" => {
                if args.len() < 1 || args.len() > 2 {
                    return Err("array/get expects an index and optional default".into());
                }
                let index = value_index(&args[0])?;
                Ok(array
                    .borrow()
                    .get(index)
                    .cloned()
                    .or_else(|| args.get(1).cloned())
                    .unwrap_or(Value::Nil))
            }
            "set" => {
                if args.len() != 2 {
                    return Err("array/set expects an index and value".into());
                }
                let index = value_index(&args[0])?;
                let mut values = array.borrow_mut();
                if index >= values.len() {
                    return Err("array/set index out of bounds".into());
                }
                values[index] = args[1].clone();
                drop(values);
                Ok(Value::Array(array))
            }
            "push-first" => {
                if args.len() != 1 {
                    return Err("array/push-first expects one value".into());
                }
                array.borrow_mut().insert(0, args[0].clone());
                Ok(Value::Array(array))
            }
            "push-last" => {
                if args.len() != 1 {
                    return Err("array/push-last expects one value".into());
                }
                array.borrow_mut().push(args[0].clone());
                Ok(Value::Array(array))
            }
            "pop-first" => {
                if !args.is_empty() {
                    return Err("array/pop-first expects no arguments".into());
                }
                let mut values = array.borrow_mut();
                Ok(if values.is_empty() {
                    Value::Nil
                } else {
                    values.remove(0)
                })
            }
            "pop-last" => {
                if !args.is_empty() {
                    return Err("array/pop-last expects no arguments".into());
                }
                Ok(array.borrow_mut().pop().unwrap_or(Value::Nil))
            }
            "insert" => {
                if args.len() != 2 {
                    return Err("array/insert expects an index and value".into());
                }
                let index = value_index(&args[0])?;
                let mut values = array.borrow_mut();
                if index > values.len() {
                    return Err("array/insert index out of bounds".into());
                }
                values.insert(index, args[1].clone());
                drop(values);
                Ok(Value::Array(array))
            }
            "remove" => {
                if args.len() != 1 {
                    return Err("array/remove expects an index".into());
                }
                let index = value_index(&args[0])?;
                let mut values = array.borrow_mut();
                if index >= values.len() {
                    return Err("array/remove index out of bounds".into());
                }
                Ok(values.remove(index))
            }
            "clone" => {
                if !args.is_empty() {
                    return Err("array/clone expects no arguments".into());
                }
                Ok(Value::Array(Rc::new(RefCell::new(array.borrow().clone()))))
            }
            "slice" => {
                if args.is_empty() || args.len() > 2 {
                    return Err("array/slice expects start and optional end".into());
                }
                let start = value_index(&args[0])?;
                let end = if args.len() == 2 {
                    value_index(&args[1])?
                } else {
                    array.borrow().len()
                };
                let values = array.borrow();
                if start > end || end > values.len() {
                    return Err("array/slice range is out of bounds".into());
                }
                Ok(Value::Array(Rc::new(RefCell::new(
                    values[start..end].to_vec(),
                ))))
            }
            "map" | "filter" => {
                if args.len() != 1 {
                    return Err(format!("array/{name} expects one function"));
                }
                let function = match &args[0] {
                    Value::Function(function) => function,
                    _ => return Err(format!("array/{name} expects a function")),
                };
                let mut output = Vec::new();
                for value in array.borrow().iter().cloned() {
                    let mapped = call_function(function, vec![value.clone()])?;
                    if name == "map" {
                        output.push(mapped);
                    } else if mapped.truthy() {
                        output.push(value);
                    }
                }
                Ok(Value::Array(Rc::new(RefCell::new(output))))
            }
            "fold-left" | "fold-right" => {
                if args.len() != 2 {
                    return Err(format!("array/{name} expects a function and initial value"));
                }
                let function = match &args[0] {
                    Value::Function(function) => function,
                    _ => return Err(format!("array/{name} expects a function")),
                };
                let values = array.borrow();
                let mut output = args[1].clone();
                if name == "fold-left" {
                    for value in values.iter().cloned() {
                        output = call_function(function, vec![output, value])?;
                    }
                } else {
                    for value in values.iter().rev().cloned() {
                        output = call_function(function, vec![value, output])?;
                    }
                }
                Ok(output)
            }
            _ => Err(format!("unsupported array method: {name}")),
        },
        Value::Object(object) => match name {
            "has?" => {
                if args.len() != 1 {
                    return Err("object/has? expects a key".into());
                }
                let key = marker_key(&args[0], "object/has?")?;
                Ok(Value::Bool(
                    object
                        .borrow()
                        .iter()
                        .any(|(candidate, _)| candidate == &key),
                ))
            }
            "get" => {
                if args.len() < 1 || args.len() > 2 {
                    return Err("object/get expects a key and optional default".into());
                }
                let key = marker_key(&args[0], "object/get")?;
                Ok(object
                    .borrow()
                    .iter()
                    .find(|(candidate, _)| candidate == &key)
                    .map(|(_, value)| value.clone())
                    .or_else(|| args.get(1).cloned())
                    .unwrap_or(Value::Nil))
            }
            "set" => {
                if args.len() != 2 {
                    return Err("object/set expects a key and value".into());
                }
                let key = marker_key(&args[0], "object/set")?;
                let mut values = object.borrow_mut();
                if let Some((_, value)) = values.iter_mut().find(|(candidate, _)| candidate == &key)
                {
                    *value = args[1].clone();
                } else {
                    values.push((key, args[1].clone()));
                }
                drop(values);
                Ok(Value::Object(object))
            }
            "delete" => {
                if args.len() != 1 {
                    return Err("object/delete expects a key".into());
                }
                let key = marker_key(&args[0], "object/delete")?;
                let mut values = object.borrow_mut();
                if let Some(index) = values.iter().position(|(candidate, _)| candidate == &key) {
                    Ok(values.remove(index).1)
                } else {
                    Ok(Value::Nil)
                }
            }
            "keys" | "vals" | "pairs" => {
                if !args.is_empty() {
                    return Err(format!("object/{name} expects no arguments"));
                }
                let output = object
                    .borrow()
                    .iter()
                    .map(|(key, value)| match name {
                        "keys" => Value::String(key.clone()),
                        "vals" => value.clone(),
                        _ => Value::Array(Rc::new(RefCell::new(vec![
                            Value::String(key.clone()),
                            value.clone(),
                        ]))),
                    })
                    .collect();
                Ok(Value::Array(Rc::new(RefCell::new(output))))
            }
            "assign" => {
                if args.len() != 1 {
                    return Err("object/assign expects an object".into());
                }
                let other = match &args[0] {
                    Value::Object(other) => other.clone(),
                    _ => return Err("object/assign expects an object".into()),
                };
                let mut values = object.borrow_mut();
                for (key, value) in other.borrow().iter() {
                    if let Some((_, existing)) =
                        values.iter_mut().find(|(candidate, _)| candidate == key)
                    {
                        *existing = value.clone();
                    } else {
                        values.push((key.clone(), value.clone()));
                    }
                }
                drop(values);
                Ok(Value::Object(object))
            }
            "clone" => {
                if !args.is_empty() {
                    return Err("object/clone expects no arguments".into());
                }
                Ok(Value::Object(Rc::new(RefCell::new(
                    object.borrow().clone(),
                ))))
            }
            _ => Err(format!("unsupported object method: {name}")),
        },
        _ => Err("dot calls require an array or object marker".into()),
    }
}

fn byte_input(value: &Value, operation: &str) -> Result<u8, String> {
    match value {
        Value::Number(number) if (-128..=255).contains(number) => Ok((*number as i8) as u8),
        _ => Err(format!(
            "{operation} expects a value in the range -128..255"
        )),
    }
}

fn byte_buffer(value: &Value, operation: &str) -> Result<Rc<RefCell<Vec<u8>>>, String> {
    match value {
        Value::ByteBuffer(bytes) => Ok(bytes.clone()),
        _ => Err(format!("{operation} expects bytes")),
    }
}

fn byte_count(value: &Value) -> Result<Value, String> {
    match value {
        Value::Bytes(bytes) => Ok(Value::Number(bytes.len() as i64)),
        Value::ByteBuffer(bytes) => Ok(Value::Number(bytes.borrow().len() as i64)),
        _ => Err("bytes/count expects bytes".into()),
    }
}

fn byte_get(value: &Value, index: &Value, default: Option<Value>) -> Result<Value, String> {
    let index = value_index(index)?;
    let found = match value {
        Value::Bytes(bytes) => bytes.get(index).copied(),
        Value::ByteBuffer(bytes) => bytes.borrow().get(index).copied(),
        _ => return Err("bytes/get expects bytes".into()),
    };
    match found {
        Some(byte) => Ok(Value::Number(byte as i64)),
        None => default.ok_or_else(|| "bytes/get index out of bounds".into()),
    }
}

fn byte_copy(value: &Value) -> Result<Value, String> {
    let bytes = byte_buffer(value, "bytes/copy")?;
    let copied = bytes.borrow().clone();
    Ok(Value::ByteBuffer(Rc::new(RefCell::new(copied))))
}

fn byte_slice(value: &Value, start: &Value, end: &Value) -> Result<Value, String> {
    let start = value_index(start)?;
    let end = value_index(end)?;
    let bytes = byte_buffer(value, "bytes/slice")?;
    let bytes = bytes.borrow();
    if start > end || end > bytes.len() {
        return Err(format!(
            "bytes/slice range is out of bounds: {start}..{end}"
        ));
    }
    Ok(Value::ByteBuffer(Rc::new(RefCell::new(
        bytes[start..end].to_vec(),
    ))))
}

fn byte_set(value: &Value, index: &Value, item: &Value) -> Result<Value, String> {
    let index = value_index(index)?;
    let item = byte_input(item, "bytes/set")?;
    let bytes = byte_buffer(value, "bytes/set")?;
    let mut bytes = bytes.borrow_mut();
    if index >= bytes.len() {
        return Err("bytes/set index out of bounds".into());
    }
    bytes[index] = item;
    Ok(value.clone())
}

fn iterator_values(value: Value) -> Result<Vec<Value>, String> {
    match value {
        Value::Nil => Ok(Vec::new()),
        Value::Tuple(values) => Ok(values.iter().cloned().collect()),
        Value::Vector(values) => Ok(values.iter().cloned().collect()),
        Value::List(values) => Ok(values.iter().cloned().collect()),
        Value::String(text) => Ok(text.chars().map(|c| Value::String(c.to_string())).collect()),
        Value::Bytes(bytes) => Ok(bytes
            .into_iter()
            .map(|byte| Value::Number(byte as i8 as i64))
            .collect()),
        Value::ByteBuffer(bytes) => Ok(bytes
            .borrow()
            .iter()
            .map(|byte| Value::Number(*byte as i8 as i64))
            .collect()),
        Value::Array(values) => Ok(values.borrow().clone()),
        Value::Object(values) => Ok(values
            .borrow()
            .iter()
            .map(|(key, value)| {
                Value::Vector(PVector::from_iter([
                    Value::String(key.clone()),
                    value.clone(),
                ]))
            })
            .collect()),
        Value::Map(entries) => Ok(entries
            .into_iter()
            .map(|(key, value)| Value::Vector(PVector::from_iter([key, value])))
            .collect()),
        Value::Set(values) => Ok(values.into_iter().collect()),
        Value::Iterator(iterator) => {
            let mut state = iterator.borrow_mut();
            if state.closed {
                return Ok(Vec::new());
            }
            if state.generator.is_some() {
                return Err("cannot materialize an infinite iterator".into());
            }
            let values = state.values[state.index..].to_vec();
            state.index = state.values.len();
            Ok(values)
        }
        _ => Err("iter expects a collection".into()),
    }
}

fn make_iterator(value: Value) -> Result<Value, String> {
    match &value {
        Value::Iterator(_) => Ok(value),
        Value::Nil
        | Value::String(_)
        | Value::Bytes(_)
        | Value::ByteBuffer(_)
        | Value::Array(_)
        | Value::Object(_)
        | Value::Map(_)
        | Value::Set(_)
        | Value::List(_)
        | Value::Tuple(_)
        | Value::Vector(_) => Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::new(
            iterator_values(value)?,
        ))))),
        _ => match protocol_call("IIter", "iter", &[value])? {
            Value::Iterator(iterator) => Ok(Value::Iterator(iterator)),
            _ => Err("IIter/iter must return an iterator".into()),
        },
    }
}

pub fn iterator_from_values(values: Vec<Value>) -> Value {
    Value::Iterator(Rc::new(RefCell::new(IteratorState::new(values))))
}

fn iterator_seq(value: Value) -> Result<Value, String> {
    match value {
        Value::Iterator(iterator) => {
            iterator.borrow_mut().seq = true;
            Ok(Value::Iterator(iterator))
        }
        value => {
            let values = iterator_values(value)?;
            let mut state = IteratorState::new(values);
            state.seq = true;
            Ok(Value::Iterator(Rc::new(RefCell::new(state))))
        }
    }
}

fn iterator_constant(value: Value) -> Value {
    Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(
        IteratorGenerator::Constant(value),
    ))))
}
fn iterator_repeated(function: Rc<Function>) -> Value {
    Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(
        IteratorGenerator::Repeated(function),
    ))))
}
fn iterator_iterate(function: Rc<Function>, seed: Value) -> Value {
    Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(
        IteratorGenerator::Iterate(function, seed),
    ))))
}
fn iterator_take_while(function: Rc<Function>, value: Value) -> Result<Value, String> {
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::TakeWhile(function, source)),
    ))))
}
fn iterator_map(function: Rc<Function>, value: Value) -> Result<Value, String> {
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::Map(function, source)),
    ))))
}
fn iterator_partition(value: Value, amount: usize, all: bool) -> Result<Value, String> {
    if amount == 0 {
        return Err("partition amount must be positive".into());
    }
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::Partition(source, amount, all)),
    ))))
}

fn iterator_interleave(values: Vec<Value>) -> Result<Value, String> {
    let sources = values
        .into_iter()
        .map(|value| match value {
            Value::Iterator(iterator) => Ok(Value::Iterator(iterator)),
            value => make_iterator(value),
        })
        .collect::<Result<Vec<_>, _>>()?;
    if sources.iter().any(
        |value| matches!(value,Value::Iterator(iterator) if iterator.borrow().generator.is_some()),
    ) {
        return Ok(Value::Iterator(Rc::new(RefCell::new(
            IteratorState::generated(IteratorGenerator::Interleave(sources, 0)),
        ))));
    }
    let collections = sources
        .iter()
        .map(|value| iterator_values(value.clone()))
        .collect::<Result<Vec<_>, _>>()?;
    let limit = collections.iter().map(Vec::len).min().unwrap_or(0);
    let mut output = Vec::new();
    for index in 0..limit {
        for values in &collections {
            output.push(values[index].clone());
        }
    }
    Ok(iterator_from_values(output))
}

fn iterator_zip(values: Vec<Value>) -> Result<Value, String> {
    let sources = values
        .into_iter()
        .map(|value| match value {
            Value::Iterator(iterator) => Ok(Value::Iterator(iterator)),
            value => make_iterator(value),
        })
        .collect::<Result<Vec<_>, _>>()?;
    if sources.iter().any(
        |value| matches!(value,Value::Iterator(iterator) if iterator.borrow().generator.is_some()),
    ) {
        return Ok(Value::Iterator(Rc::new(RefCell::new(
            IteratorState::generated(IteratorGenerator::Zip(sources)),
        ))));
    }
    let collections = sources
        .iter()
        .map(|value| iterator_values(value.clone()))
        .collect::<Result<Vec<_>, _>>()?;
    let limit = collections.iter().map(Vec::len).min().unwrap_or(0);
    Ok(iterator_from_values(
        (0..limit)
            .map(|index| {
                Value::Vector(
                    collections
                        .iter()
                        .map(|values| values[index].clone())
                        .collect(),
                )
            })
            .collect(),
    ))
}

fn iterator_mapcat(function: Rc<Function>, value: Value) -> Result<Value, String> {
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    if let Value::Iterator(iterator) = &source {
        if iterator.borrow().generator.is_none() {
            let values = iterator_values(source)?;
            let mut output = Vec::new();
            for value in values {
                output.extend(iterator_values(call_function(&function, vec![value])?)?);
            }
            return Ok(iterator_from_values(output));
        }
    }
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::Mapcat(function, source, None)),
    ))))
}
fn iterator_keep(function: Rc<Function>, value: Value) -> Result<Value, String> {
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    if let Value::Iterator(iterator) = &source {
        if iterator.borrow().generator.is_none() {
            let values = iterator_values(source)?;
            let mut output = Vec::new();
            for value in values {
                let mapped = call_function(&function, vec![value])?;
                if !matches!(mapped, Value::Nil) {
                    output.push(mapped);
                }
            }
            return Ok(iterator_from_values(output));
        }
    }
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::Keep(function, source)),
    ))))
}

fn iterator_filter(function: Rc<Function>, value: Value) -> Result<Value, String> {
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    if let Value::Iterator(iterator) = &source {
        if iterator.borrow().generator.is_none() {
            let values = iterator_values(source)?;
            return Ok(iterator_from_values(
                values
                    .into_iter()
                    .filter_map(
                        |value| match call_function(&function, vec![value.clone()]) {
                            Ok(result) if result.truthy() => Some(Ok(value)),
                            Ok(_) => None,
                            Err(error) => Some(Err(error)),
                        },
                    )
                    .collect::<Result<Vec<_>, _>>()?,
            ));
        }
    }
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::Filter(function, source)),
    ))))
}

fn iterator_drop_while(function: Rc<Function>, value: Value) -> Result<Value, String> {
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    if let Value::Iterator(iterator) = &source {
        if iterator.borrow().generator.is_none() {
            let values = iterator_values(source)?;
            let mut output = Vec::new();
            let mut dropping = true;
            for value in values {
                if dropping && call_function(&function, vec![value.clone()])?.truthy() {
                    continue;
                }
                dropping = false;
                output.push(value);
            }
            return Ok(iterator_from_values(output));
        }
    }
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::DropWhile(function, source, false)),
    ))))
}
fn iterator_take(value: Value, amount: usize) -> Result<Value, String> {
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::Take(source, amount)),
    ))))
}
fn iterator_drop(value: Value, amount: usize) -> Result<Value, String> {
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::Drop(source, amount)),
    ))))
}

fn iterator_cycle(value: Value) -> Result<Value, String> {
    let source = match value {
        Value::Iterator(iterator) => Value::Iterator(iterator),
        value => make_iterator(value)?,
    };
    Ok(Value::Iterator(Rc::new(RefCell::new(
        IteratorState::generated(IteratorGenerator::Cycle(source, Vec::new(), 0, false)),
    ))))
}

fn iterator_has_next(value: &Value) -> Result<Value, String> {
    match value {
        Value::Iterator(iterator) => Ok(Value::Bool(iterator.borrow().has_next())),
        _ => Err("iter-has? expects an iterator".into()),
    }
}

fn iterator_next(value: &Value) -> Result<Value, String> {
    match value {
        Value::Iterator(iterator) => iterator.borrow_mut().next(),
        _ => Err("iter-next expects an iterator".into()),
    }
}

fn iterator_close(value: &Value) -> Result<Value, String> {
    match value {
        Value::Iterator(iterator) => {
            iterator.borrow_mut().close();
            Ok(Value::Nil)
        }
        _ => Err("iter-close expects an iterator".into()),
    }
}

fn collection_keys(value: &Value) -> Result<Value, String> {
    match value {
        Value::Map(values) => Ok(Value::Vector(
            values.iter().map(|(key, _)| key.clone()).collect(),
        )),
        Value::Object(values) => Ok(Value::Vector(
            values
                .borrow()
                .iter()
                .map(|(key, _)| Value::String(key.clone()))
                .collect(),
        )),
        _ => Err("keys expects a map or object".into()),
    }
}

fn collection_vals(value: &Value) -> Result<Value, String> {
    match value {
        Value::Map(values) => Ok(Value::Vector(
            values.iter().map(|(_, value)| value.clone()).collect(),
        )),
        Value::Object(values) => Ok(Value::Vector(
            values
                .borrow()
                .iter()
                .map(|(_, value)| value.clone())
                .collect(),
        )),
        _ => Err("vals expects a map or object".into()),
    }
}

fn collection_first(value: Value) -> Result<Value, String> {
    match value {
        Value::Iterator(iterator) => {
            let mut iterator = iterator.borrow_mut();
            if iterator.has_next() {
                iterator.next()
            } else {
                Ok(Value::Nil)
            }
        }
        value => Ok(iterator_values(value)?
            .into_iter()
            .next()
            .unwrap_or(Value::Nil)),
    }
}

fn collection_rest(value: Value) -> Result<Value, String> {
    if matches!(value, Value::Iterator(_)) {
        return iterator_drop(value, 1);
    }
    let mut values = iterator_values(value)?;
    if !values.is_empty() {
        values.remove(0);
    }
    Ok(Value::List(values.into_iter().collect()))
}

fn collection_last(value: Value) -> Result<Value, String> {
    Ok(iterator_values(value)?
        .into_iter()
        .last()
        .unwrap_or(Value::Nil))
}

fn collection_second(value: Value) -> Result<Value, String> {
    if let Value::Iterator(iterator) = &value {
        let mut state = iterator.borrow_mut();
        let _ = state.next()?;
        return Ok(state.next().unwrap_or(Value::Nil));
    }
    let mut values = iterator_values(value)?.into_iter();
    values.next();
    Ok(values.next().unwrap_or(Value::Nil))
}

fn collection_empty(value: Value) -> Result<Value, String> {
    match value {
        Value::Iterator(iterator) => Ok(Value::Bool(!iterator.borrow().has_next())),
        value => Ok(Value::Bool(iterator_values(value)?.is_empty())),
    }
}

fn collection_count(value: &Value) -> Result<Value, String> {
    let count = match value {
        Value::Nil => 0,
        Value::String(v) => v.chars().count(),
        Value::Tuple(v) => v.len(),
        Value::Vector(v) => v.len(),
        Value::List(v) => v.len(),
        Value::Map(v) => v.len(),
        Value::Set(v) => v.len(),
        Value::Bytes(v) => v.len(),
        Value::ByteBuffer(v) => v.borrow().len(),
        Value::Array(v) => v.borrow().len(),
        Value::Object(v) => v.borrow().len(),
        Value::Iterator(_) => {
            if !iterator_is_finite(value) {
                return Err("count expects a finite collection".into());
            }
            let mut count = 0;
            loop {
                match iterator_next(value) {
                    Ok(_) => count += 1,
                    Err(_) => break,
                }
            }
            count
        }
        _ => return Err("count expects a collection".into()),
    };
    Ok(Value::Number(count as i64))
}

fn iterator_is_finite(value: &Value) -> bool {
    match value {
        Value::Iterator(iterator) => match &iterator.borrow().generator {
            None => true,
            Some(IteratorGenerator::Constant(_))
            | Some(IteratorGenerator::Repeated(_))
            | Some(IteratorGenerator::Iterate(_, _)) => false,
            Some(IteratorGenerator::Take(_, _)) => true,
            Some(IteratorGenerator::Cycle(_, _, _, _)) => false,
            Some(IteratorGenerator::Drop(source, _))
            | Some(IteratorGenerator::TakeWhile(_, source))
            | Some(IteratorGenerator::DropWhile(_, source, _))
            | Some(IteratorGenerator::Map(_, source))
            | Some(IteratorGenerator::Filter(_, source))
            | Some(IteratorGenerator::Mapcat(_, source, _))
            | Some(IteratorGenerator::Keep(_, source))
            | Some(IteratorGenerator::Partition(source, _, _)) => iterator_is_finite(source),
            Some(IteratorGenerator::Zip(sources))
            | Some(IteratorGenerator::Interleave(sources, _)) => {
                sources.iter().all(iterator_is_finite)
            }
        },
        _ => true,
    }
}

fn collection_get(value: &Value, key: &Value, default: Value) -> Result<Value, String> {
    match value {
        Value::Nil => Ok(default),
        Value::Tuple(values) => {
            let index = value_index(key)?;
            Ok(values.get(index).cloned().unwrap_or(default))
        }
        Value::Vector(values) => {
            let index = value_index(key)?;
            Ok(values.get(index).cloned().unwrap_or(default))
        }
        Value::Array(values) => {
            let index = value_index(key)?;
            Ok(values.borrow().get(index).cloned().unwrap_or(default))
        }
        Value::List(values) => {
            let index = value_index(key)?;
            Ok(values.get(index).cloned().unwrap_or(default))
        }
        Value::String(text) => {
            let index = value_index(key)?;
            Ok(text
                .chars()
                .nth(index)
                .map(|c| Value::String(c.to_string()))
                .unwrap_or(default))
        }
        Value::Map(entries) => Ok(entries.get(key).cloned().unwrap_or(default)),
        Value::Set(values) => Ok(values.get(key).cloned().unwrap_or(default)),
        Value::Object(entries) => {
            let name = match key {
                Value::String(name) => name.as_str(),
                Value::Keyword(name) => name.as_str(),
                _ => return Ok(default),
            };
            Ok(entries
                .borrow()
                .iter()
                .find(|(candidate, _)| candidate == name)
                .map(|(_, value)| value.clone())
                .unwrap_or(default))
        }
        _ => Err("get expects a collection".into()),
    }
}

fn collection_nth(value: &Value, key: &Value) -> Result<Value, String> {
    let index = value_index(key)?;
    if let Value::Iterator(iterator) = value {
        let mut state = iterator.borrow_mut();
        for _ in 0..index {
            let _ = state.next()?;
        }
        return state.next().map_err(|_| "nth index out of bounds".into());
    }
    let missing = Value::Nil;
    collection_get(value, key, missing).and_then(|result| {
        if result == Value::Nil {
            Err("nth index out of bounds".into())
        } else {
            Ok(result)
        }
    })
}

fn collection_assoc(value: &Value, key: &Value, replacement: Value) -> Result<Value, String> {
    match value {
        Value::Map(entries) => Ok(Value::Map(entries.assoc_value(key.clone(), replacement))),
        Value::Object(entries) => {
            let name = marker_key(key, "object")?;
            let mut output = entries.borrow().clone();
            if let Some((_, item)) = output.iter_mut().find(|(candidate, _)| candidate == &name) {
                *item = replacement;
            } else {
                output.push((name, replacement));
            }
            Ok(Value::Object(Rc::new(RefCell::new(output))))
        }
        Value::Nil => Ok(Value::Map(
            PMap::new().assoc_value(key.clone(), replacement),
        )),
        _ => Err("assoc expects a map or object".into()),
    }
}

fn collection_dissoc(value: &Value, keys: &[Value]) -> Result<Value, String> {
    match value {
        Value::Map(entries) => Ok(Value::Map(
            keys.iter()
                .fold(entries.clone(), |map, key| map.dissoc_value(key)),
        )),
        Value::Nil => Ok(Value::Map(PMap::new())),
        _ => Err("dissoc expects a map".into()),
    }
}

fn collection_get_in(value: Value, keys: &[Value]) -> Result<Value, String> {
    if keys.is_empty() {
        return Ok(value);
    }
    let next = collection_get(&value, &keys[0], Value::Nil)?;
    if matches!(next, Value::Nil) {
        Ok(Value::Nil)
    } else {
        collection_get_in(next, &keys[1..])
    }
}

fn collection_assoc_in(value: Value, keys: &[Value], replacement: Value) -> Result<Value, String> {
    if keys.is_empty() {
        return Ok(replacement);
    }
    let current = if matches!(value, Value::Nil) {
        Value::Map(PMap::new())
    } else {
        value
    };
    let child = collection_get(&current, &keys[0], Value::Nil)?;
    let updated = collection_assoc_in(child, &keys[1..], replacement)?;
    collection_assoc(&current, &keys[0], updated)
}

fn unique_values(values: Vec<Value>) -> Vec<Value> {
    let mut unique = Vec::new();
    for value in values {
        if !unique.contains(&value) {
            unique.push(value);
        }
    }
    unique
}

fn metadata_value(form: &Form) -> Result<MetadataValue, String> {
    match form {
        Form::Nil => Ok(MetadataValue::Nil),
        Form::Bool(value) => Ok(MetadataValue::Boolean(*value)),
        Form::Number(value) => Ok(MetadataValue::Number(*value)),
        Form::Float(value) => Ok(MetadataValue::Float(*value)),
        Form::BigInteger(value) => Ok(MetadataValue::BigInteger(value.clone())),
        Form::Decimal(value) => Ok(MetadataValue::Decimal(value.clone())),
        Form::Character(value) => Ok(MetadataValue::Character(*value)),
        Form::Regex(value) => Ok(MetadataValue::Regex(value.clone())),
        Form::Tagged(tag, value) => Ok(MetadataValue::Tagged(
            tag.clone(),
            Box::new(metadata_value(value)?),
        )),
        Form::Metadata(_, value) => metadata_value(value),
        Form::Symbol(value) => Ok(MetadataValue::Symbol(Symbol::from(value.clone()))),
        Form::Keyword(value) => Ok(MetadataValue::Keyword(Keyword::from(value.clone()))),
        Form::String(value) => Ok(MetadataValue::String(value.clone())),
        Form::Vector(values) => Ok(MetadataValue::Vector(
            values
                .iter()
                .map(metadata_value)
                .collect::<Result<_, _>>()?,
        )),
        Form::List(values) => Ok(MetadataValue::List(
            values
                .iter()
                .map(metadata_value)
                .collect::<Result<_, _>>()?,
        )),
        Form::Set(values) => Ok(MetadataValue::Set(
            values
                .iter()
                .map(metadata_value)
                .collect::<Result<_, _>>()?,
        )),
        Form::Map(values) => Ok(MetadataValue::Map(
            values
                .iter()
                .map(|(key, value)| Ok((metadata_value(key)?, metadata_value(value)?)))
                .collect::<Result<_, String>>()?,
        )),
    }
}

fn metadata_from_form(form: &Form) -> Result<Rc<Metadata>, String> {
    let MetadataValue::Map(entries) = metadata_value(form)? else {
        return Err("reader metadata must be a map".into());
    };
    Ok(Metadata::new(entries))
}

fn attach_metadata(value: Value, metadata: Rc<Metadata>) -> Result<Value, String> {
    Ok(match value {
        Value::Symbol(value) => Value::Symbol(value.with_meta(Some(metadata))),
        Value::Tuple(value) => Value::Tuple(Box::new(value.with_meta(Some(metadata)))),
        Value::Vector(value) => Value::Vector(value.with_meta(Some(metadata))),
        Value::List(value) => Value::List(value.with_meta(Some(metadata))),
        Value::Map(value) => Value::Map(value.with_meta(Some(metadata))),
        Value::Set(value) => Value::Set(value.with_meta(Some(metadata))),
        Value::Var(value) => {
            value.set_hara_metadata(Some(metadata));
            Value::Var(value)
        }
        Value::Keyword(value) => Value::Keyword(value),
        _ => return Err("metadata can only be applied to object values".into()),
    })
}

fn vector_literal(values: Vec<Value>) -> Result<Value, String> {
    if values.len() <= 5 {
        Ok(Value::Tuple(Box::new(PTuple::from_values(values)?)))
    } else {
        Ok(Value::Vector(values.into()))
    }
}

fn literal_value(form: &Form) -> Result<Value, String> {
    match form {
        Form::Nil => Ok(Value::Nil),
        Form::Bool(value) => Ok(Value::Bool(*value)),
        Form::Character(value) => Ok(Value::Character(*value)),
        Form::Float(value) => Ok(Value::Float(*value)),
        Form::BigInteger(value) => Ok(Value::BigInteger(value.clone())),
        Form::Decimal(value) => Ok(Value::Decimal(value.clone())),
        Form::Regex(value) => Ok(Value::Regex(value.clone())),
        Form::Tagged(tag, value) => Ok(Value::Tagged(tag.clone(), Box::new(literal_value(value)?))),
        Form::Metadata(metadata, value) => {
            attach_metadata(literal_value(value)?, metadata_from_form(metadata)?)
        }
        Form::Number(v) => Ok(Value::Number(*v)),
        Form::String(v) => Ok(Value::String(v.clone())),
        Form::Keyword(v) => Ok(Value::Keyword(v.clone().into())),
        Form::Symbol(v) => Ok(Value::Symbol(v.clone().into())),
        Form::Vector(values) => {
            vector_literal(values.iter().map(literal_value).collect::<Result<_, _>>()?)
        }
        Form::Set(values) => Ok(Value::Set(
            unique_values(values.iter().map(literal_value).collect::<Result<_, _>>()?).into(),
        )),
        Form::List(values) => Ok(Value::List(
            values.iter().map(literal_value).collect::<Result<_, _>>()?,
        )),
        Form::Map(values) => Ok(Value::Map(
            values
                .iter()
                .map(|(k, v)| Ok((literal_value(k)?, literal_value(v)?)))
                .collect::<Result<_, String>>()?,
        )),
    }
}

fn generated_function(
    params: Vec<String>,
    body: Vec<Form>,
    mut captured: HashMap<String, Value>,
    bindings: Vec<(&str, Value)>,
) -> Value {
    for (name, value) in bindings {
        captured.insert(name.to_string(), value);
    }
    Value::Function(Rc::new(Function {
        params,
        variadic: None,
        body,
        captured: Rc::new(RefCell::new(captured)),
        name: None,
        native: None,
    }))
}

fn function_parts(form: &Form) -> Result<(Vec<String>, Option<String>), String> {
    let list = match form {
        Form::Vector(values) => values,
        _ => return Err("function parameters must be a vector".into()),
    };
    let mut params = Vec::new();
    let mut variadic = None;
    let mut index = 0;
    while index < list.len() {
        match &list[index] {
            Form::Symbol(name) if name == "&" => {
                if variadic.is_some() || index + 1 >= list.len() || index + 2 != list.len() {
                    return Err("variadic marker must precede the final parameter".into());
                }
                match &list[index + 1] {
                    Form::Symbol(name) => variadic = Some(name.clone()),
                    _ => return Err("variadic parameter must be a symbol".into()),
                }
                index += 2;
            }
            Form::Symbol(name) => {
                params.push(name.clone());
                index += 1;
            }
            _ => return Err("function parameters must be symbols".into()),
        }
    }
    Ok((params, variadic))
}

fn deref_value(value: Value) -> Value {
    match value {
        Value::Var(var) => var.deref_value(),
        value => value,
    }
}

fn binding_value(env: &HashMap<String, Value>, name: &str) -> Option<Value> {
    env.get(name).cloned().map(deref_value)
}

fn binding_var(env: &mut HashMap<String, Value>, name: &str) -> Option<KernelVar<Value>> {
    match env.get(name) {
        Some(Value::Var(var)) => Some(var.clone()),
        Some(value) => {
            let var = KernelVar::new(name, value.clone());
            env.insert(name.to_string(), Value::Var(var.clone()));
            Some(var)
        }
        None => None,
    }
}

fn call_value(callable: Value, arguments: Vec<Value>) -> Result<Value, String> {
    let lookup =
        |target: &Value, key: &Value, fallback: Value| collection_get(target, key, fallback);
    match callable {
        Value::Function(function) => call_function(&function, arguments),
        Value::Keyword(keyword) => match arguments.as_slice() {
            [target] => lookup(target, &Value::Keyword(keyword), Value::Nil),
            [target, fallback] => lookup(target, &Value::Keyword(keyword), fallback.clone()),
            _ => Err("keyword invocation expects one or two arguments".into()),
        },
        Value::Map(values) => match arguments.as_slice() {
            [key] => Ok(values.get(key).cloned().unwrap_or(Value::Nil)),
            [key, fallback] => Ok(values.get(key).cloned().unwrap_or_else(|| fallback.clone())),
            _ => Err("map invocation expects one or two arguments".into()),
        },
        Value::Set(values) => match arguments.as_slice() {
            [key] => Ok(values.get(key).cloned().unwrap_or(Value::Nil)),
            [key, fallback] => Ok(values.get(key).cloned().unwrap_or_else(|| fallback.clone())),
            _ => Err("set invocation expects one or two arguments".into()),
        },
        _ => Err("value is not callable".into()),
    }
}

fn call_function(function: &Function, arguments: Vec<Value>) -> Result<Value, String> {
    if let Some(native) = &function.native {
        if function.params.len() != arguments.len() {
            return Err(format!(
                "function expects {} arguments",
                function.params.len()
            ));
        }
        return native(arguments);
    }
    let tracing = tracing_enabled();
    if tracing {
        TRACE_STACK.with(|stack| {
            stack.borrow_mut().push(
                function
                    .name
                    .clone()
                    .unwrap_or_else(|| "<anonymous>".into()),
            )
        });
    }
    let result = (|| {
        if function.variadic.is_none() && function.params.len() != arguments.len() {
            return Err(format!(
                "function expects {} arguments",
                function.params.len()
            ));
        }
        if arguments.len() < function.params.len() {
            return Err(format!(
                "function expects at least {} arguments",
                function.params.len()
            ));
        }
        let mut env = function.captured.borrow().clone();
        for (name, value) in function
            .params
            .iter()
            .zip(arguments.iter().take(function.params.len()))
        {
            env.insert(name.clone(), value.clone());
        }
        if let Some(name) = &function.variadic {
            env.insert(
                name.clone(),
                Value::List(arguments.into_iter().skip(function.params.len()).collect()),
            );
        }
        let mut result = Value::Nil;
        for form in &function.body {
            result = eval(form, &mut env)?;
            if matches!(result, Value::Recur(_)) {
                return Err("recur must be inside loop".into());
            }
        }
        Ok(result)
    })();
    let result = result.map_err(append_trace);
    if tracing {
        TRACE_STACK.with(|stack| {
            stack.borrow_mut().pop();
        });
    }
    result
}

fn binding_symbol(form: &Form, context: &str) -> Result<(String, Option<Rc<Metadata>>), String> {
    match form {
        Form::Symbol(name) => Ok((name.clone(), None)),
        Form::Metadata(metadata, value) => match value.as_ref() {
            Form::Symbol(name) => Ok((name.clone(), Some(metadata_from_form(metadata)?))),
            _ => Err(format!("{context} must be a symbol")),
        },
        _ => Err(format!("{context} must be a symbol")),
    }
}

pub fn eval(form: &Form, env: &mut HashMap<String, Value>) -> Result<Value, String> {
    match form {
        Form::Number(v) => Ok(Value::Number(*v)),
        Form::String(v) => Ok(Value::String(v.clone())),
        Form::Keyword(v) => Ok(Value::Keyword(v.clone().into())),
        Form::Nil => Ok(Value::Nil),
        Form::Bool(value) => Ok(Value::Bool(*value)),
        Form::Character(value) => Ok(Value::Character(*value)),
        Form::Float(value) => Ok(Value::Float(*value)),
        Form::BigInteger(value) => Ok(Value::BigInteger(value.clone())),
        Form::Decimal(value) => Ok(Value::Decimal(value.clone())),
        Form::Regex(value) => Ok(Value::Regex(value.clone())),
        Form::Tagged(tag, value) => Ok(Value::Tagged(tag.clone(), Box::new(literal_value(value)?))),
        Form::Metadata(_, value) => eval(value, env),
        Form::List(fs)
            if fs.len() == 2 && matches!(&fs[0], Form::Symbol(name) if name == "quote") =>
        {
            literal_value(&fs[1])
        }
        Form::Map(values) => Ok(Value::Map(
            values
                .iter()
                .map(|(key, value)| Ok((eval(key, env)?, eval(value, env)?)))
                .collect::<Result<_, String>>()?,
        )),
        Form::Set(values) => Ok(Value::Set(
            unique_values(
                values
                    .iter()
                    .map(|value| eval(value, env))
                    .collect::<Result<_, _>>()?,
            )
            .into(),
        )),
        Form::Vector(values) => vector_literal(
            values
                .iter()
                .map(|value| eval(value, env))
                .collect::<Result<_, _>>()?,
        ),
        Form::Symbol(n) if n == "nil" => Ok(Value::Nil),
        Form::Symbol(n) if n == "true" => Ok(Value::Bool(true)),
        Form::Symbol(n) if n == "false" => Ok(Value::Bool(false)),
        Form::Symbol(n) if n == "inc" || n == "dec" => {
            let op = if n == "inc" { "+" } else { "-" };
            let body = Form::List(vec![
                Form::Symbol(op.into()),
                Form::Symbol("value".into()),
                Form::Number(1),
            ]);
            Ok(generated_function(
                vec!["value".into()],
                vec![body],
                env.clone(),
                vec![],
            ))
        }
        Form::Symbol(n) => binding_value(env, n).ok_or_else(|| format!("unbound symbol: {n}")),
        Form::List(fs) if fs.is_empty() => Ok(Value::Nil),
        Form::List(fs) => match &fs[0] {
            Form::Symbol(n) if n == "fn" || n == "fn*" => {
                if fs.len() < 3 {
                    return Err("fn expects parameters and a body".into());
                }
                let (params, variadic) = function_parts(&fs[1])?;
                Ok(Value::Function(Rc::new(Function {
                    params,
                    variadic,
                    body: fs[2..].to_vec(),
                    captured: Rc::new(RefCell::new(env.clone())),
                    name: None,
                    native: None,
                })))
            }
            Form::Symbol(n) if n == "eval" => {
                if fs.len() != 2 {
                    return Err("eval expects one form".into());
                }
                eval(&fs[1], env)
            }
            Form::Symbol(n) if n == "var" => {
                if fs.len() != 2 {
                    return Err("var expects a symbol".into());
                }
                let name = match &fs[1] {
                    Form::Symbol(name) => name,
                    _ => return Err("var expects a symbol".into()),
                };
                let cell =
                    binding_var(env, name).ok_or_else(|| format!("unbound symbol: {name}"))?;
                Ok(Value::Var(cell))
            }
            Form::Symbol(n) if n == "deref" => {
                if fs.len() != 2 {
                    return Err("deref expects a var".into());
                }
                let target = match &fs[1] {
                    Form::Symbol(name) => match env.get(name) {
                        Some(Value::Var(cell)) => Value::Var(cell.clone()),
                        _ => eval(&fs[1], env)?,
                    },
                    _ => eval(&fs[1], env)?,
                };
                match target {
                    Value::Var(value) => Ok(value.deref_value()),
                    Value::Promise(promise) => match promise.state() {
                        PromiseState::Fulfilled(value) => Ok(value),
                        PromiseState::Rejected(error) => Err(error),
                        PromiseState::Pending => Err(
                            "deref cannot block on a pending promise outside an HTA fiber".into(),
                        ),
                    },
                    _ => Err("deref expects a var or promise".into()),
                }
            }
            Form::Symbol(n) if n == "set!" || n == "var/set" => {
                if fs.len() != 3 {
                    return Err(format!("{n} expects a symbol and value"));
                }
                let name = match &fs[1] {
                    Form::Symbol(name) => name,
                    _ => return Err(format!("{n} expects a symbol")),
                };
                let value = eval(&fs[2], env)?;
                let cell = binding_var(env, name).ok_or_else(|| format!("unbound var: {name}"))?;
                cell.reset_value(value.clone());
                Ok(value)
            }
            Form::Symbol(n) if n == "alter-var-root" => {
                if fs.len() < 3 {
                    return Err("alter-var-root expects a var and function".into());
                }
                let target = match eval(&fs[1], env)? {
                    Value::Var(cell) => cell,
                    _ => return Err("alter-var-root expects a var".into()),
                };
                let function = match eval(&fs[2], env)? {
                    Value::Function(function) => function,
                    _ => return Err("alter-var-root expects a function".into()),
                };
                let mut arguments = vec![target.deref_value()];
                arguments.extend(
                    fs[3..]
                        .iter()
                        .map(|form| eval(form, env))
                        .collect::<Result<Vec<_>, _>>()?,
                );
                let value = call_function(&function, arguments)?;
                target.reset_value(value.clone());
                Ok(value)
            }
            Form::Symbol(n) if n == "throw" => {
                if fs.len() != 2 {
                    return Err("throw expects one value".into());
                }
                let value = eval(&fs[1], env)?;
                Err(format!("thrown: {}", value.display()))
            }
            Form::Symbol(n) if n == "try" => {
                if fs.len() < 2 {
                    return Err("try expects a body".into());
                }
                let mut body = Vec::new();
                let mut catch_form = None;
                let mut finally_forms = Vec::new();
                for form in &fs[1..] {
                    match form {
                        Form::List(parts)
                            if !parts.is_empty()
                                && matches!(&parts[0],Form::Symbol(name) if name=="catch") =>
                        {
                            catch_form = Some(parts)
                        }
                        Form::List(parts)
                            if !parts.is_empty()
                                && matches!(&parts[0],Form::Symbol(name) if name=="finally") =>
                        {
                            finally_forms.extend_from_slice(&parts[1..])
                        }
                        _ if catch_form.is_none() => body.push(form),
                        _ => return Err("try clauses must follow the body".into()),
                    }
                }
                let mut result = Ok(Value::Nil);
                for form in body {
                    result = eval(form, env);
                    if result.is_err() {
                        break;
                    }
                }
                if let Err(ref error) = result {
                    if let Some(parts) = catch_form {
                        if parts.len() != 3 {
                            return Err("catch expects a name and body".into());
                        }
                        let name = match &parts[1] {
                            Form::Symbol(name) => name.clone(),
                            _ => return Err("catch name must be a symbol".into()),
                        };
                        let old = env.insert(name.clone(), Value::String(error.clone()));
                        result = eval(&parts[2], env);
                        if let Some(old) = old {
                            env.insert(name, old);
                        } else {
                            env.remove(&name);
                        }
                    }
                }
                for form in finally_forms {
                    let final_result = eval(&form, env);
                    if final_result.is_err() {
                        result = final_result;
                    }
                }
                result
            }
            Form::Symbol(n) if n == "def" => {
                if fs.len() != 3 {
                    return Err("def expects a name and value".into());
                }
                let (name, metadata) = binding_symbol(&fs[1], "def name")?;
                let value = eval(&fs[2], env)?;
                if let Some(Value::Var(var)) = env.get(&name) {
                    var.reset_value(value.clone());
                    if metadata.is_some() {
                        var.set_hara_metadata(metadata);
                    }
                } else {
                    let var = KernelVar::new(name.clone(), value.clone());
                    var.set_hara_metadata(metadata);
                    env.insert(name, Value::Var(var));
                }
                Ok(value)
            }
            Form::Symbol(n) if n == "defn" => {
                if fs.len() < 4 {
                    return Err("defn expects a name, parameters, and a body".into());
                }
                let (name, metadata) = binding_symbol(&fs[1], "defn name")?;
                let (params, variadic) = function_parts(&fs[2])?;
                let cell = match env.get(&name) {
                    Some(Value::Var(cell)) => cell.clone(),
                    _ => KernelVar::new(name.clone(), Value::Nil),
                };
                if metadata.is_some() {
                    cell.set_hara_metadata(metadata);
                }
                env.insert(name.clone(), Value::Var(cell.clone()));
                let function_ref = Rc::new(Function {
                    params,
                    variadic,
                    body: fs[3..].to_vec(),
                    captured: Rc::new(RefCell::new(env.clone())),
                    name: Some(name.clone()),
                    native: None,
                });
                let function = Value::Function(function_ref.clone());
                cell.reset_value(function.clone());
                Ok(function)
            }
            Form::Symbol(n) if n == "do" => {
                let mut result = Value::Nil;
                for form in &fs[1..] {
                    result = eval(form, env)?;
                    if matches!(result, Value::Recur(_)) {
                        return Ok(result);
                    }
                }
                Ok(result)
            }
            Form::Symbol(n) if n == "=" => {
                if fs.len() < 3 {
                    return Err("= expects at least 2 arguments".into());
                }
                let first = eval(&fs[1], env)?;
                Ok(Value::Bool(
                    fs[2..]
                        .iter()
                        .map(|form| eval(form, env))
                        .collect::<Result<Vec<_>, _>>()?
                        .iter()
                        .all(|value| *value == first),
                ))
            }
            Form::Symbol(n) if n == "ns" => {
                if fs.len() < 2 {
                    return Err("ns expects a namespace symbol".into());
                }
                match &fs[1] {
                    Form::Symbol(_) => Ok(Value::Nil),
                    _ => Err("ns expects a namespace symbol".into()),
                }
            }
            Form::Symbol(n) if n == "protocol-call" => {
                if fs.len() < 4 {
                    return Err(
                        "protocol-call expects protocol, method, value, and optional arguments"
                            .into(),
                    );
                }
                let protocol = match &fs[1] {
                    Form::Symbol(name) => name.as_str(),
                    _ => return Err("protocol name must be a symbol".into()),
                };
                let method = match &fs[2] {
                    Form::Symbol(name) => name.as_str(),
                    _ => return Err("protocol method must be a symbol".into()),
                };
                let mut arguments = vec![eval(&fs[3], env)?];
                arguments.extend(
                    fs[4..]
                        .iter()
                        .map(|form| eval(form, env))
                        .collect::<Result<Vec<_>, _>>()?,
                );
                protocol_call(protocol, method, &arguments)
            }
            Form::Symbol(n) if n == "promise" => {
                if fs.len() != 1 {
                    return Err("promise expects no arguments".into());
                }
                Ok(Value::Promise(Promise::new()))
            }
            Form::Symbol(n) if n == "host/call" => {
                if fs.len() < 3 {
                    return Err("host/call expects service, method, and optional arguments".into());
                }
                let service = match eval(&fs[1], env)? {
                    Value::String(value) => value,
                    _ => return Err("host/call service must be a string".into()),
                };
                let method = match eval(&fs[2], env)? {
                    Value::String(value) => value,
                    _ => return Err("host/call method must be a string".into()),
                };
                let arguments = fs[3..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                HOST_CALL_HANDLER.with(|active| {
                    let handler = active
                        .borrow()
                        .as_ref()
                        .cloned()
                        .ok_or_else(|| "host/call is unavailable".to_string())?;
                    handler(service, method, arguments)
                })
            }
            Form::Symbol(n) if n == "promise/new" || n == "promise/run" => {
                if fs.len() != 2 {
                    return Err(format!("{n} expects one function"));
                }
                let function = match eval(&fs[1], env)? {
                    Value::Function(function) => function,
                    _ => return Err(format!("{n} expects a function")),
                };
                let provider = promise_provider();
                if n == "promise/run" {
                    let task = Rc::new(move || call_function(&function, Vec::new()));
                    Ok(Value::Promise(provider.run(task)))
                } else {
                    let promise = Promise::new();
                    let resolving = promise.clone();
                    let resolve = native_function("promise-resolve", 1, move |mut values| {
                        let value = values.remove(0);
                        settle_promise_result(&resolving, Ok(value.clone()));
                        Ok(value)
                    });
                    let rejecting = promise.clone();
                    let reject = native_function("promise-reject", 1, move |mut values| {
                        let value = values.remove(0);
                        let error = match &value {
                            Value::String(error) => error.clone(),
                            value => value.display(),
                        };
                        rejecting.reject(error);
                        Ok(value)
                    });
                    if let Err(error) = call_function(&function, vec![resolve, reject]) {
                        promise.reject(error);
                    }
                    Ok(Value::Promise(promise))
                }
            }
            Form::Symbol(n) if n == "promise/all" => {
                if fs.len() != 2 {
                    return Err("promise/all expects one collection".into());
                }
                Ok(Value::Promise(promise_all(iterator_values(eval(
                    &fs[1], env,
                )?)?)))
            }
            Form::Symbol(n) if n == "promise/native?" => {
                if fs.len() != 2 {
                    return Err("promise/native? expects one value".into());
                }
                Ok(Value::Bool(matches!(eval(&fs[1], env)?, Value::Promise(_))))
            }
            Form::Symbol(n) if n == "promise/delay" => {
                if fs.len() != 3 {
                    return Err("promise/delay expects milliseconds and a function".into());
                }
                let millis = match eval(&fs[1], env)? {
                    Value::Number(value) if value >= 0 => value as u64,
                    _ => return Err("promise/delay expects non-negative milliseconds".into()),
                };
                let function = match eval(&fs[2], env)? {
                    Value::Function(function) => function,
                    _ => return Err("promise/delay expects milliseconds and a function".into()),
                };
                let task = Rc::new(move || call_function(&function, Vec::new()));
                Ok(Value::Promise(
                    promise_provider().delay(std::time::Duration::from_millis(millis), task),
                ))
            }
            Form::Symbol(n) if n == "promise/state" => {
                if fs.len() != 2 {
                    return Err("promise/state expects one argument".into());
                }
                Ok(promise_state_value(&promise_value(
                    &eval(&fs[1], env)?,
                    "promise/state",
                )?))
            }
            Form::Symbol(n) if n == "promise/value" => {
                if fs.len() != 2 {
                    return Err("promise/value expects one argument".into());
                }
                promise_value_result(&promise_value(&eval(&fs[1], env)?, "promise/value")?)
            }
            Form::Symbol(n) if n == "promise/resolve" || n == "promise/reject" => {
                if fs.len() != 3 {
                    return Err(format!("{n} expects a promise and value"));
                }
                let promise = promise_value(&eval(&fs[1], env)?, n)?;
                let settled = if n == "promise/resolve" {
                    promise.resolve(eval(&fs[2], env)?)
                } else {
                    promise.reject(match eval(&fs[2], env)? {
                        Value::String(error) => error,
                        value => value.display(),
                    })
                };
                if !settled {
                    return Err("promise is already settled".into());
                }
                Ok(Value::Promise(promise))
            }
            Form::Symbol(n) if n == "promise/cancel" => {
                if fs.len() != 2 {
                    return Err("promise/cancel expects a promise".into());
                }
                let promise = promise_value(&eval(&fs[1], env)?, n)?;
                if !promise.reject("cancelled") {
                    return Err("promise is already settled".into());
                }
                Ok(Value::Promise(promise))
            }
            Form::Symbol(n) if n == "promise/adopt" => {
                if fs.len() != 3 {
                    return Err("promise/adopt expects two promises".into());
                }
                let promise = promise_value(&eval(&fs[1], env)?, n)?;
                let other = promise_value(&eval(&fs[2], env)?, n)?;
                if !promise.adopt(&other) {
                    return Err("promise source is pending or destination is settled".into());
                }
                Ok(Value::Promise(promise))
            }
            Form::Symbol(n)
                if [
                    "promise/map",
                    "promise/recover",
                    "promise/then",
                    "promise/catch",
                    "promise/finally",
                ]
                .contains(&n.as_str()) =>
            {
                if fs.len() != 3 {
                    return Err(format!("{n} expects a promise and function"));
                }
                let source = promise_value(&eval(&fs[1], env)?, n)?;
                let function = match eval(&fs[2], env)? {
                    Value::Function(function) => function,
                    _ => return Err(format!("{n} expects a function")),
                };
                Ok(Value::Promise(promise_chain(source, n, function)))
            }
            Form::Symbol(n) if n == "set" => Ok(Value::Set(
                unique_values(
                    fs[1..]
                        .iter()
                        .map(|form| eval(form, env))
                        .collect::<Result<_, _>>()?,
                )
                .into(),
            )),
            Form::Symbol(n) if n == "array" => {
                let values = fs[1..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                Ok(Value::Array(Rc::new(RefCell::new(values))))
            }
            Form::Symbol(n) if n == "object" => {
                if fs.len() % 2 != 1 {
                    return Err("object expects key/value pairs".into());
                }
                let mut values = Vec::new();
                for pair in fs[1..].chunks(2) {
                    let key = marker_key(&eval(&pair[0], env)?, "object")?;
                    values.push((key, eval(&pair[1], env)?));
                }
                Ok(Value::Object(Rc::new(RefCell::new(values))))
            }
            Form::Symbol(n) if n == "." => {
                if fs.len() != 3 {
                    return Err("dot expects a receiver and method".into());
                }
                let receiver = eval(&fs[1], env)?;
                dot_call(receiver, &fs[2], env)
            }
            Form::Symbol(n) if n == "bytes" => {
                let values = fs[1..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                let values = values
                    .iter()
                    .map(|value| byte_input(value, "bytes"))
                    .collect::<Result<Vec<_>, _>>()?;
                Ok(Value::ByteBuffer(Rc::new(RefCell::new(values))))
            }
            Form::Symbol(n) if n == "str" => {
                if fs.len() == 1 {
                    return Ok(Value::String(String::new()));
                }
                let values = fs[1..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                Ok(Value::String(
                    values
                        .iter()
                        .map(|value| match value {
                            Value::String(text) => text.clone(),
                            _ => value.display(),
                        })
                        .collect::<Vec<_>>()
                        .join(""),
                ))
            }
            Form::Symbol(n)
                if [
                    "str/comp",
                    "str/lt?",
                    "str/gt?",
                    "str/pad-left",
                    "str/pad-right",
                    "str/starts-with?",
                    "str/ends-with?",
                    "str/char",
                    "str/split",
                    "str/join",
                    "str/index-of",
                    "str/substring",
                    "str/to-fixed",
                    "str/replace",
                    "str/trim-left",
                    "str/trim-right",
                ]
                .contains(&n.as_str()) =>
            {
                let values = fs[1..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                string_operation(n, values)
            }
            Form::Symbol(n)
                if n == "str/count" || n == "str/trim" || n == "str/upper" || n == "str/lower" =>
            {
                if fs.len() != 2 {
                    return Err(format!("{n} expects one string"));
                }
                let text = match eval(&fs[1], env)? {
                    Value::String(text) => text,
                    _ => return Err(format!("{n} expects a string")),
                };
                match n.as_str() {
                    "str/count" => Ok(Value::Number(text.chars().count() as i64)),
                    "str/trim" => Ok(Value::String(text.trim().into())),
                    "str/upper" => Ok(Value::String(text.to_uppercase())),
                    "str/lower" => Ok(Value::String(text.to_lowercase())),
                    _ => unreachable!(),
                }
            }
            Form::Symbol(n) if n == "str/encode" => {
                if fs.len() != 2 {
                    return Err("str/encode expects one string".into());
                }
                match eval(&fs[1], env)? {
                    Value::String(text) => {
                        Ok(Value::ByteBuffer(Rc::new(RefCell::new(text.into_bytes()))))
                    }
                    _ => Err("str/encode expects a string".into()),
                }
            }
            Form::Symbol(n) if n == "str/decode" => {
                if fs.len() != 2 {
                    return Err("str/decode expects bytes".into());
                }
                let bytes = byte_buffer(&eval(&fs[1], env)?, "str/decode")?;
                let raw = bytes.borrow().clone();
                String::from_utf8(raw)
                    .map(Value::String)
                    .map_err(|_| "str/decode invalid UTF-8".into())
            }
            Form::Symbol(n) if n == "bytes/copy" => {
                if fs.len() != 2 {
                    return Err("bytes/copy expects bytes".into());
                }
                byte_copy(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "bytes/slice" => {
                if fs.len() != 3 && fs.len() != 4 {
                    return Err("bytes/slice expects bytes, start, and optional end".into());
                }
                let value = eval(&fs[1], env)?;
                let start = eval(&fs[2], env)?;
                let end = if fs.len() == 4 {
                    eval(&fs[3], env)?
                } else {
                    byte_count(&value)?
                };
                byte_slice(&value, &start, &end)
            }
            Form::Symbol(n) if n == "bytes/count" => {
                if fs.len() != 2 {
                    return Err("bytes/count expects one argument".into());
                }
                byte_count(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "bytes/get" => {
                if fs.len() != 3 && fs.len() != 4 {
                    return Err("bytes/get expects an index and optional default".into());
                }
                let value = eval(&fs[1], env)?;
                let index = eval(&fs[2], env)?;
                let default = if fs.len() == 4 {
                    Some(eval(&fs[3], env)?)
                } else {
                    None
                };
                let index_num = value_index(&index)?;
                match byte_get(&value, &index, default) {
                    Ok(value) => Ok(value),
                    Err(error) if error.is_empty() => {
                        Err(format!("bytes/get index out of bounds: {index_num}"))
                    }
                    Err(error) => Err(error),
                }
            }
            Form::Symbol(n) if n == "bytes/set" => {
                if fs.len() != 4 {
                    return Err("bytes/set expects bytes, index, and value".into());
                }
                let value = eval(&fs[1], env)?;
                let index = eval(&fs[2], env)?;
                let item = eval(&fs[3], env)?;
                byte_set(&value, &index, &item)
            }
            Form::Symbol(n) if n == "bytes/u8" || n == "bytes/s8" => {
                if fs.len() != 2 {
                    return Err(format!("{n} expects one argument"));
                }
                let number = match eval(&fs[1], env)? {
                    Value::Number(number) => number,
                    _ => return Err(format!("{n} expects a number")),
                };
                if !(-128..=255).contains(&number) {
                    return Err(format!("{n} expects a value in the range -128..255"));
                }
                let raw = (number as i8) as u8;
                Ok(Value::Number(if n == "bytes/u8" {
                    raw as i64
                } else {
                    raw as i8 as i64
                }))
            }
            Form::Symbol(n) if n == "iter" => {
                if fs.len() != 2 {
                    return Err("iter expects one argument".into());
                }
                make_iterator(eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "seq" => {
                if fs.len() != 2 && fs.len() != 3 {
                    return Err("seq expects a source, or a transform and source".into());
                }
                let source = eval(&fs[fs.len() - 1], env)?;
                let lazy = iterator_seq(source)?;
                if fs.len() == 2 {
                    Ok(lazy)
                } else {
                    match eval(&fs[1], env)? {
                        Value::Function(function) => {
                            let result = call_function(&function, vec![lazy])?;
                            iterator_seq(result)
                        }
                        _ => Err("seq expects a function and source".into()),
                    }
                }
            }
            Form::Symbol(n) if n == "seq?" || n == "iter?" => {
                if fs.len() != 2 {
                    return Err(format!("{n} expects one value"));
                }
                let value = eval(&fs[1], env)?;
                let result = matches!(value, Value::Iterator(iterator) if n == "seq?" && iterator.borrow().seq || n == "iter?" && !iterator.borrow().seq);
                Ok(Value::Bool(result))
            }
            Form::Symbol(n) if n == "iter-has?" => {
                if fs.len() != 2 {
                    return Err("iter-has? expects one argument".into());
                }
                iterator_has_next(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "iter-next" => {
                if fs.len() != 2 {
                    return Err("iter-next expects one argument".into());
                }
                iterator_next(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "iter-close" => {
                if fs.len() != 2 {
                    return Err("iter-close expects one argument".into());
                }
                iterator_close(&eval(&fs[1], env)?)
            }
            Form::Symbol(n)
                if ["iter-map", "map", "iter-filter", "filter"].contains(&n.as_str()) =>
            {
                let is_map = n == "iter-map" || n == "map";
                if n == "map" && fs.len() == 2 {
                    let function = match eval(&fs[1], env)? {
                        Value::Function(function) => function,
                        _ => return Err("map expects a function".into()),
                    };
                    let body = Form::List(vec![
                        Form::Symbol("__map-transform".into()),
                        Form::Symbol("__function".into()),
                        Form::Symbol("value".into()),
                    ]);
                    return Ok(generated_function(
                        vec!["value".into()],
                        vec![body],
                        env.clone(),
                        vec![("__function", Value::Function(function))],
                    ));
                }
                if fs.len() < 3 {
                    return Err(format!("{n} expects a function and collection"));
                }
                let function = eval(&fs[1], env)?;
                if is_map && fs.len() > 3 {
                    let sources = fs[2..]
                        .iter()
                        .map(|form| eval(form, env).and_then(make_iterator))
                        .collect::<Result<Vec<_>, _>>()?;
                    let zipped = iterator_zip(sources)?;
                    let result = match function {
                        Value::Function(function) => iterator_map(function, zipped)?,
                        _ => return Err(format!("{n} expects a function")),
                    };
                    return if n == "map" {
                        iterator_seq(result)
                    } else {
                        Ok(result)
                    };
                }
                let raw_collection = if fs.len() == 3 {
                    Some(eval(&fs[2], env)?)
                } else {
                    None
                };
                if fs.len() == 3 {
                    if let Value::Function(function_ref) = &function {
                        if is_map {
                            if let Some(value) = raw_collection.clone() {
                                return if n == "map" {
                                    iterator_seq(iterator_map(function_ref.clone(), value)?)
                                } else {
                                    iterator_map(function_ref.clone(), value)
                                };
                            }
                        } else if let Some(value) = raw_collection.clone() {
                            return if n == "filter" {
                                iterator_seq(iterator_filter(function_ref.clone(), value)?)
                            } else {
                                iterator_filter(function_ref.clone(), value)
                            };
                        }
                    }
                }
                let collections = if let Some(value) = raw_collection {
                    vec![iterator_values(value)?]
                } else {
                    fs[2..]
                        .iter()
                        .map(|form| eval(form, env).and_then(iterator_values))
                        .collect::<Result<Vec<_>, _>>()?
                };
                let mut output = Vec::new();
                if is_map {
                    let limit = collections.iter().map(Vec::len).min().unwrap_or(0);
                    for index in 0..limit {
                        let args = collections
                            .iter()
                            .map(|values| values[index].clone())
                            .collect();
                        let mapped = match &function {
                            Value::Function(f) => call_function(f, args)?,
                            _ => return Err(format!("{n} expects a function")),
                        };
                        output.push(mapped);
                    }
                } else {
                    if collections.len() != 1 {
                        return Err(format!("{n} expects one collection"));
                    }
                    for value in collections.into_iter().next().unwrap() {
                        let mapped = match &function {
                            Value::Function(f) => call_function(f, vec![value.clone()])?,
                            _ => return Err(format!("{n} expects a function")),
                        };
                        if mapped.truthy() {
                            output.push(value);
                        }
                    }
                }
                if n == "map" {
                    iterator_seq(iterator_from_values(output))
                } else {
                    Ok(iterator_from_values(output))
                }
            }
            Form::Symbol(n) if ["iter-take", "take"].contains(&n.as_str()) => {
                if fs.len() != 3 {
                    return Err(format!("{n} expects an amount and collection"));
                }
                let amount = value_index(&eval(&fs[1], env)?)?;
                let result = iterator_take(eval(&fs[2], env)?, amount)?;
                if n == "take" {
                    iterator_seq(result)
                } else {
                    Ok(result)
                }
            }
            Form::Symbol(n) if ["iter-drop", "drop"].contains(&n.as_str()) => {
                if fs.len() != 3 {
                    return Err(format!("{n} expects an amount and collection"));
                }
                let amount = value_index(&eval(&fs[1], env)?)?;
                let result = iterator_drop(eval(&fs[2], env)?, amount)?;
                if n == "drop" {
                    iterator_seq(result)
                } else {
                    Ok(result)
                }
            }
            Form::Symbol(n)
                if [
                    "iter-take-while",
                    "take-while",
                    "iter-drop-while",
                    "drop-while",
                ]
                .contains(&n.as_str()) =>
            {
                if fs.len() != 3 {
                    return Err(format!("{n} expects a predicate and collection"));
                }
                let predicate = match eval(&fs[1], env)? {
                    Value::Function(function) => function,
                    _ => return Err(format!("{n} expects a function")),
                };
                let value = eval(&fs[2], env)?;
                let result = if n.contains("take-while") {
                    iterator_take_while(predicate, value)?
                } else {
                    iterator_drop_while(predicate, value)?
                };
                if n.starts_with("iter-") {
                    Ok(result)
                } else {
                    iterator_seq(result)
                }
            }
            Form::Symbol(n) if ["iter-mapcat", "mapcat"].contains(&n.as_str()) => {
                if fs.len() != 3 {
                    return Err(format!("{n} expects a function and collection"));
                }
                let function = match eval(&fs[1], env)? {
                    Value::Function(function) => function,
                    _ => return Err(format!("{n} expects a function")),
                };
                let result = iterator_mapcat(function, eval(&fs[2], env)?)?;
                if n == "mapcat" {
                    iterator_seq(result)
                } else {
                    Ok(result)
                }
            }
            Form::Symbol(n) if ["iter-keep", "keep"].contains(&n.as_str()) => {
                if fs.len() != 3 {
                    return Err(format!("{n} expects a function and collection"));
                }
                let function = match eval(&fs[1], env)? {
                    Value::Function(function) => function,
                    _ => return Err(format!("{n} expects a function")),
                };
                let result = iterator_keep(function, eval(&fs[2], env)?)?;
                if n == "keep" {
                    iterator_seq(result)
                } else {
                    Ok(result)
                }
            }
            Form::Symbol(n)
                if [
                    "iter-partition-all",
                    "partition-all",
                    "iter-partition",
                    "partition",
                ]
                .contains(&n.as_str()) =>
            {
                if fs.len() != 3 {
                    return Err(format!("{n} expects an amount and collection"));
                }
                let amount = value_index(&eval(&fs[1], env)?)?;
                let result = iterator_partition(eval(&fs[2], env)?, amount, n.contains("all"))?;
                if n.starts_with("iter-") {
                    Ok(result)
                } else {
                    iterator_seq(result)
                }
            }
            Form::Symbol(n) if ["iter-interpose", "interpose"].contains(&n.as_str()) => {
                if fs.len() != 3 {
                    return Err(format!("{n} expects a separator and collection"));
                }
                let separator = eval(&fs[1], env)?;
                let values = iterator_values(eval(&fs[2], env)?)?;
                let mut output = Vec::new();
                for (index, value) in values.into_iter().enumerate() {
                    if index > 0 {
                        output.push(separator.clone());
                    }
                    output.push(value);
                }
                let result = iterator_from_values(output);
                if n == "interpose" {
                    iterator_seq(result)
                } else {
                    Ok(result)
                }
            }
            Form::Symbol(n) if ["iter-interleave", "interleave"].contains(&n.as_str()) => {
                if fs.len() < 2 {
                    return Err(format!("{n} expects collections"));
                }
                let collections = fs[1..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                let result = iterator_interleave(collections)?;
                if n == "interleave" {
                    iterator_seq(result)
                } else {
                    Ok(result)
                }
            }
            Form::Symbol(n) if ["iter-partition-pair", "partition-pair"].contains(&n.as_str()) => {
                if fs.len() != 2 {
                    return Err(format!("{n} expects one collection"));
                }
                let values = iterator_values(eval(&fs[1], env)?)?;
                let result = iterator_from_values(
                    values
                        .chunks(2)
                        .filter(|chunk| chunk.len() == 2)
                        .map(|chunk| Value::Vector(chunk.to_vec().into()))
                        .collect(),
                );
                if n == "partition-pair" {
                    iterator_seq(result)
                } else {
                    Ok(result)
                }
            }
            Form::Symbol(n) if ["iter-zip", "zip"].contains(&n.as_str()) => {
                if fs.len() < 3 {
                    return Err(format!("{n} expects collections"));
                }
                let collections = fs[1..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                let result = iterator_zip(collections)?;
                if n == "zip" {
                    iterator_seq(result)
                } else {
                    Ok(result)
                }
            }
            Form::Symbol(n) if n == "iter-cycle" || n == "cycle" => {
                if fs.len() != 2 {
                    return Err(format!("{n} expects one collection"));
                }
                let result = iterator_cycle(eval(&fs[1], env)?)?;
                if n == "cycle" {
                    iterator_seq(result)
                } else {
                    Ok(result)
                }
            }
            Form::Symbol(n) if n == "concat" => {
                if fs.len() < 2 {
                    return Err("concat expects collections".into());
                }
                let mut values = Vec::new();
                for form in &fs[1..] {
                    values.extend(iterator_values(eval(form, env)?)?);
                }
                iterator_seq(iterator_from_values(values))
            }
            Form::Symbol(n) if n == "range" => {
                if fs.len() < 1 || fs.len() > 3 {
                    return Err("range expects zero, one, or two bounds".into());
                }
                let nums = fs[1..]
                    .iter()
                    .map(|form| match eval(form, env)? {
                        Value::Number(v) => Ok(v),
                        _ => Err("range bounds must be numbers".into()),
                    })
                    .collect::<Result<Vec<_>, String>>()?;
                let (start, end) = match nums.as_slice() {
                    [] => (0, 0),
                    [end] => (0, *end),
                    [start, end] => (*start, *end),
                    _ => unreachable!(),
                };
                iterator_seq(iterator_from_values(
                    (start..end).map(Value::Number).collect(),
                ))
            }
            Form::Symbol(n) if n == "repeat" => {
                if fs.len() != 2 && fs.len() != 3 {
                    return Err("repeat expects a value or amount and value".into());
                }
                let (amount, form) = if fs.len() == 2 {
                    (None, &fs[1])
                } else {
                    (Some(value_index(&eval(&fs[1], env)?)?), &fs[2])
                };
                let value = eval(form, env)?;
                if amount.is_none() {
                    return iterator_seq(iterator_constant(value));
                }
                let count = amount.unwrap();
                iterator_seq(iterator_from_values(
                    (0..count).map(|_| value.clone()).collect(),
                ))
            }
            Form::Symbol(n) if n == "repeatedly" => {
                if fs.len() != 2 && fs.len() != 3 {
                    return Err("repeatedly expects a function or amount and function".into());
                }
                let (amount, form) = if fs.len() == 2 {
                    (None, &fs[1])
                } else {
                    (Some(value_index(&eval(&fs[1], env)?)?), &fs[2])
                };
                let function = match eval(form, env)? {
                    Value::Function(function) => function,
                    _ => return Err("repeatedly expects a function".into()),
                };
                let generated = iterator_repeated(function);
                let result = if let Some(amount) = amount {
                    iterator_take(generated, amount)?
                } else {
                    generated
                };
                iterator_seq(result)
            }
            Form::Symbol(n) if n == "iterate" => {
                if fs.len() != 3 {
                    return Err("iterate expects a function and seed".into());
                }
                let function = match eval(&fs[1], env)? {
                    Value::Function(function) => function,
                    _ => return Err("iterate expects a function".into()),
                };
                iterator_seq(iterator_iterate(function, eval(&fs[2], env)?))
            }
            Form::Symbol(n) if n == "iter-constantly" => {
                if fs.len() != 2 {
                    return Err("iter-constantly expects a value".into());
                }
                Ok(iterator_constant(eval(&fs[1], env)?))
            }
            Form::Symbol(n) if n == "iter-repeatedly" => {
                if fs.len() != 2 {
                    return Err("iter-repeatedly expects a function".into());
                }
                match eval(&fs[1], env)? {
                    Value::Function(function) => Ok(iterator_repeated(function)),
                    _ => Err("iter-repeatedly expects a function".into()),
                }
            }
            Form::Symbol(n) if n == "iter-iterate" => {
                if fs.len() != 3 {
                    return Err("iter-iterate expects a function and seed".into());
                }
                let function = match eval(&fs[1], env)? {
                    Value::Function(function) => function,
                    _ => return Err("iter-iterate expects a function".into()),
                };
                Ok(iterator_iterate(function, eval(&fs[2], env)?))
            }
            Form::Symbol(n)
                if [
                    "bit-and",
                    "bit-or",
                    "bit-xor",
                    "bit-not",
                    "bit-shift-left",
                    "bit-shift-right",
                ]
                .contains(&n.as_str()) =>
            {
                bit_operation(n, &fs[1..], env)
            }
            Form::Symbol(n) if ["inc", "dec"].contains(&n.as_str()) => {
                if fs.len() != 2 {
                    return Err(format!("{n} expects one number"));
                }
                match eval(&fs[1], env)? {
                    Value::Number(value) => Ok(Value::Number(if n == "inc" {
                        value + 1
                    } else {
                        value - 1
                    })),
                    _ => Err(format!("{n} expects a number")),
                }
            }
            Form::Symbol(n) if n == "__map-transform" => {
                if fs.len() != 3 {
                    return Err("map transform expects a function and source".into());
                }
                let function = match eval(&fs[1], env)? {
                    Value::Function(function) => function,
                    _ => return Err("map transform expects a function".into()),
                };
                let source = eval(&fs[2], env)?;
                if matches!(source, Value::Iterator(_)) {
                    iterator_seq(iterator_map(function, source)?)
                } else {
                    let values = iterator_values(source)?;
                    let mapped = values
                        .into_iter()
                        .map(|value| call_function(&function, vec![value]))
                        .collect::<Result<Vec<_>, _>>()?;
                    Ok(Value::Vector(mapped.into_iter().collect()))
                }
            }
            Form::Symbol(n) if ["zero?", "pos?", "neg?", "even?", "odd?"].contains(&n.as_str()) => {
                if fs.len() != 2 {
                    return Err(format!("{n} expects one number"));
                }
                let value = match eval(&fs[1], env)? {
                    Value::Number(value) => value,
                    _ => return Err(format!("{n} expects a number")),
                };
                let result = match n.as_str() {
                    "zero?" => value == 0,
                    "pos?" => value > 0,
                    "neg?" => value < 0,
                    "even?" => value % 2 == 0,
                    "odd?" => value % 2 != 0,
                    _ => false,
                };
                Ok(Value::Bool(result))
            }
            Form::Symbol(n) if ["nil?", "true?", "false?"].contains(&n.as_str()) => {
                if fs.len() != 2 {
                    return Err(format!("{n} expects one value"));
                }
                let value = eval(&fs[1], env)?;
                let result = match n.as_str() {
                    "nil?" => matches!(value, Value::Nil),
                    "true?" => matches!(value, Value::Bool(true)),
                    "false?" => matches!(value, Value::Bool(false)),
                    _ => false,
                };
                Ok(Value::Bool(result))
            }
            Form::Symbol(n) if ["every?", "any?"].contains(&n.as_str()) => {
                if fs.len() != 3 {
                    return Err(format!("{n} expects a predicate and collection"));
                }
                let predicate = eval(&fs[1], env)?;
                let values = iterator_values(eval(&fs[2], env)?)?;
                for value in values {
                    let result = match &predicate {
                        Value::Function(function) => call_function(function, vec![value])?,
                        _ => return Err(format!("{n} expects a function")),
                    };
                    if n == "every?" && !result.truthy() {
                        return Ok(Value::Bool(false));
                    }
                    if n == "any?" && result.truthy() {
                        return Ok(Value::Bool(true));
                    }
                }
                Ok(Value::Bool(n == "every?"))
            }
            Form::Symbol(n) if n == "constantly" => {
                if fs.len() != 2 {
                    return Err("constantly expects one value".into());
                }
                let value = eval(&fs[1], env)?;
                let mut captured = env.clone();
                captured.insert("__constant".into(), value);
                Ok(Value::Function(Rc::new(Function {
                    params: Vec::new(),
                    variadic: Some("_rest".into()),
                    body: vec![Form::Symbol("__constant".into())],
                    captured: Rc::new(RefCell::new(captured)),
                    name: None,
                    native: None,
                })))
            }
            Form::Symbol(n) if n == "complement" => {
                if fs.len() != 2 {
                    return Err("complement expects one function".into());
                }
                let predicate = eval(&fs[1], env)?;
                if !matches!(predicate, Value::Function(_)) {
                    return Err("complement expects a function".into());
                }
                Ok(generated_function(
                    vec!["value".into()],
                    vec![Form::List(vec![
                        Form::Symbol("not".into()),
                        Form::List(vec![
                            Form::Symbol("__predicate".into()),
                            Form::Symbol("value".into()),
                        ]),
                    ])],
                    env.clone(),
                    vec![("__predicate", predicate)],
                ))
            }
            Form::Symbol(n) if n == "comp" || n == "comp2" || n == "comp3" => {
                let arity = if n == "comp3" { 3 } else { 2 };
                if fs.len() != arity + 1 {
                    return Err(format!("{n} expects {arity} functions"));
                }
                let functions = fs[1..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                if functions
                    .iter()
                    .any(|value| !matches!(value, Value::Function(_)))
                {
                    return Err(format!("{n} expects functions"));
                }
                let body = if arity == 2 {
                    Form::List(vec![
                        Form::Symbol("__f".into()),
                        Form::List(vec![
                            Form::Symbol("__g".into()),
                            Form::Symbol("value".into()),
                        ]),
                    ])
                } else {
                    Form::List(vec![
                        Form::Symbol("__f".into()),
                        Form::List(vec![
                            Form::Symbol("__g".into()),
                            Form::List(vec![
                                Form::Symbol("__h".into()),
                                Form::Symbol("value".into()),
                            ]),
                        ]),
                    ])
                };
                let mut bindings =
                    vec![("__f", functions[0].clone()), ("__g", functions[1].clone())];
                if arity == 3 {
                    bindings.push(("__h", functions[2].clone()));
                }
                Ok(generated_function(
                    vec!["value".into()],
                    vec![body],
                    env.clone(),
                    bindings,
                ))
            }
            Form::Symbol(n) if n == "identity" => {
                if fs.len() != 2 {
                    return Err("identity expects one value".into());
                }
                eval(&fs[1], env)
            }
            Form::Symbol(n) if n == "apply" => {
                if fs.len() < 3 {
                    return Err("apply expects a function and arguments".into());
                }
                let builtin_name = match &fs[1] {
                    Form::Symbol(name) if ["+", "-", "*", "/"].contains(&name.as_str()) => {
                        Some(name.as_str())
                    }
                    _ => None,
                };
                let function = if builtin_name.is_none() {
                    Some(eval(&fs[1], env)?)
                } else {
                    None
                };
                let mut arguments = Vec::new();
                for form in &fs[2..fs.len() - 1] {
                    arguments.push(eval(form, env)?);
                }
                arguments.extend(iterator_values(eval(&fs[fs.len() - 1], env)?)?);
                match function {
                    Some(Value::Function(function)) => call_function(&function, arguments),
                    Some(_) => Err("apply expects a function".into()),
                    None => {
                        let name = builtin_name.unwrap();
                        let numbers = arguments
                            .iter()
                            .map(|value| match value {
                                Value::Number(value) => Ok(*value),
                                _ => Err("apply arithmetic expects numbers".into()),
                            })
                            .collect::<Result<Vec<_>, String>>()?;
                        if numbers.is_empty() {
                            return Err("apply expects a function".into());
                        }
                        let result = match name {
                            "+" => numbers.iter().sum(),
                            "-" => numbers[1..].iter().fold(numbers[0], |a, b| a - b),
                            "*" => numbers.iter().product(),
                            "/" => numbers[1..].iter().fold(numbers[0], |a, b| a / b),
                            _ => return Err("apply expects a function".into()),
                        };
                        Ok(Value::Number(result))
                    }
                }
            }
            Form::Symbol(n) if n == "key" || n == "val" => {
                if fs.len() != 2 {
                    return Err(format!("{n} expects an entry"));
                }
                let entry = eval(&fs[1], env)?;
                match pair_parts(&entry) {
                    Some((key, value)) => Ok(if n == "key" { key } else { value }),
                    None => Err(format!("{n} expects a pair")),
                }
            }
            Form::Symbol(n) if n == "reverse" => {
                if fs.len() != 2 {
                    return Err("reverse expects one collection".into());
                }
                let mut values = iterator_values(eval(&fs[1], env)?)?;
                values.reverse();
                Ok(Value::List(values.into_iter().collect()))
            }
            Form::Symbol(n) if n == "keys" => {
                if fs.len() != 2 {
                    return Err("keys expects one collection".into());
                }
                collection_keys(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "vals" => {
                if fs.len() != 2 {
                    return Err("vals expects one collection".into());
                }
                collection_vals(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "not" => {
                if fs.len() != 2 {
                    return Err("not expects one argument".into());
                }
                Ok(Value::Bool(!eval(&fs[1], env)?.truthy()))
            }
            Form::Symbol(n) if ["<", ">", "<=", ">="].contains(&n.as_str()) => {
                comparison(n, &fs[1..], env)
            }
            Form::Symbol(n) if n == "first" => {
                if fs.len() != 2 {
                    return Err("first expects one argument".into());
                }
                collection_first(eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "second" => {
                if fs.len() != 2 {
                    return Err("second expects one argument".into());
                }
                collection_second(eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "rest" => {
                if fs.len() != 2 {
                    return Err("rest expects one argument".into());
                }
                collection_rest(eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "last" => {
                if fs.len() != 2 {
                    return Err("last expects one argument".into());
                }
                collection_last(eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "empty?" => {
                if fs.len() != 2 {
                    return Err("empty? expects one argument".into());
                }
                collection_empty(eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "not-empty" => {
                if fs.len() != 2 {
                    return Err("not-empty expects one argument".into());
                }
                let value = eval(&fs[1], env)?;
                Ok(if collection_empty(value.clone())?.truthy() {
                    Value::Nil
                } else {
                    value
                })
            }
            Form::Symbol(n) if n == "count" => {
                if fs.len() != 2 {
                    return Err("count expects one argument".into());
                }
                collection_count(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "get" => {
                if fs.len() != 3 && fs.len() != 4 {
                    return Err("get expects 2 or 3 arguments".into());
                }
                let value = eval(&fs[1], env)?;
                let key = eval(&fs[2], env)?;
                let default = if fs.len() == 4 {
                    eval(&fs[3], env)?
                } else {
                    Value::Nil
                };
                collection_get(&value, &key, default)
            }
            Form::Symbol(n) if n == "nth" => {
                if fs.len() != 3 {
                    return Err("nth expects two arguments".into());
                }
                collection_nth(&eval(&fs[1], env)?, &eval(&fs[2], env)?)
            }
            Form::Symbol(n) if n == "assoc" => {
                if fs.len() < 4 || fs.len() % 2 != 0 {
                    return Err("assoc expects a collection and key/value pairs".into());
                }
                let mut value = eval(&fs[1], env)?;
                for pair in fs[2..].chunks(2) {
                    let key = eval(&pair[0], env)?;
                    let replacement = eval(&pair[1], env)?;
                    value = collection_assoc(&value, &key, replacement)?;
                }
                Ok(value)
            }
            Form::Symbol(n) if n == "dissoc" => {
                if fs.len() < 3 {
                    return Err("dissoc expects a map and at least one key".into());
                }
                let value = eval(&fs[1], env)?;
                let keys = fs[2..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                collection_dissoc(&value, &keys)
            }
            Form::Symbol(n) if n == "get-in" => {
                if fs.len() != 3 {
                    return Err("get-in expects a collection and keys".into());
                }
                let value = eval(&fs[1], env)?;
                let keys = iterator_values(eval(&fs[2], env)?)?;
                collection_get_in(value, &keys)
            }
            Form::Symbol(n) if n == "assoc-in" => {
                if fs.len() != 4 {
                    return Err("assoc-in expects a collection, keys, and value".into());
                }
                let value = eval(&fs[1], env)?;
                let keys = iterator_values(eval(&fs[2], env)?)?;
                let replacement = eval(&fs[3], env)?;
                collection_assoc_in(value, &keys, replacement)
            }
            Form::Symbol(n) if n == "update" || n == "update-in" => {
                if (n == "update" && fs.len() < 4) || (n == "update-in" && fs.len() < 4) {
                    return Err(format!("{n} expects a collection, key path, and function"));
                }
                let value = eval(&fs[1], env)?;
                let (keys, function_form, extra_forms) = if n == "update" {
                    (vec![eval(&fs[2], env)?], &fs[3], &fs[4..])
                } else {
                    (iterator_values(eval(&fs[2], env)?)?, &fs[3], &fs[4..])
                };
                let current = collection_get_in(value.clone(), &keys)?;
                let function = eval(function_form, env)?;
                let mut args = vec![current];
                args.extend(
                    extra_forms
                        .iter()
                        .map(|form| eval(form, env))
                        .collect::<Result<Vec<_>, _>>()?,
                );
                let replacement = match function {
                    Value::Function(function) => call_function(&function, args)?,
                    _ => return Err(format!("{n} expects a function")),
                };
                if n == "update" {
                    collection_assoc(&value, &keys[0], replacement)
                } else {
                    collection_assoc_in(value, &keys, replacement)
                }
            }
            Form::Symbol(n) if n == "conj" => {
                if fs.len() != 3 {
                    return Err("conj expects two arguments".into());
                }
                let collection = eval(&fs[1], env)?;
                let item = eval(&fs[2], env)?;
                match collection {
                    Value::Tuple(values) => tuple_push_last(&values, item),
                    Value::Vector(values) => Ok(Value::Vector(values.push_last(item))),
                    Value::List(values) => Ok(Value::List(
                        std::iter::once(item)
                            .chain(values.iter().cloned())
                            .collect(),
                    )),
                    Value::Set(values) => Ok(Value::Set(values.conj_value(item))),
                    Value::Map(values) => {
                        let (entry_key, entry_value) = pair_parts(&item)
                            .ok_or_else(|| "conj map expects a two-element entry".to_string())?;
                        Ok(Value::Map(values.assoc_value(entry_key, entry_value)))
                    }
                    _ => Err("conj expects a vector, list, or set".into()),
                }
            }
            Form::Symbol(n) if n == "cons" => {
                if fs.len() != 3 {
                    return Err("cons expects two arguments".into());
                }
                let item = eval(&fs[1], env)?;
                let collection = eval(&fs[2], env)?;
                match collection {
                    Value::Tuple(values) => tuple_push_first(&values, item),
                    Value::Vector(values) => Ok(Value::Vector(
                        std::iter::once(item)
                            .chain(values.iter().cloned())
                            .collect(),
                    )),
                    Value::List(values) => Ok(Value::List(
                        std::iter::once(item)
                            .chain(values.iter().cloned())
                            .collect(),
                    )),
                    _ => Err("cons expects a vector".into()),
                }
            }
            Form::Symbol(n) if n == "recur" => {
                if fs.len() < 2 {
                    return Err("recur expects values".into());
                }
                Ok(Value::Recur(
                    fs[1..]
                        .iter()
                        .map(|form| eval(form, env))
                        .collect::<Result<Vec<_>, _>>()?,
                ))
            }
            Form::Symbol(n) if n == "binding" => {
                if fs.len() < 3 {
                    return Err("binding expects bindings and a body".into());
                }
                let pairs = match &fs[1] {
                    Form::List(values) | Form::Vector(values) => values,
                    _ => return Err("binding expects a binding list or vector".into()),
                };
                if pairs.len() % 2 != 0 {
                    return Err("binding bindings require name/value pairs".into());
                }
                let mut pending = Vec::new();
                for pair in pairs.chunks(2) {
                    let name = match &pair[0] {
                        Form::Symbol(name) => name,
                        _ => return Err("binding name must be a symbol".into()),
                    };
                    let var = match env.get(name) {
                        Some(Value::Var(var)) => var.clone(),
                        _ => return Err(format!("binding expects a Var: {name}")),
                    };
                    if !var.is_dynamic() {
                        return Err(format!("binding expects a dynamic Var: {name}"));
                    }
                    let value = eval(&pair[1], env)?;
                    pending.push((var, value));
                }
                for (var, value) in &pending {
                    var.bind(value.clone());
                }
                let bound = pending.into_iter().map(|(var, _)| var).collect::<Vec<_>>();
                let mut result = Ok(Value::Nil);
                for form in &fs[2..] {
                    result = eval(form, env);
                    if result.is_err() {
                        break;
                    }
                }
                for var in bound.into_iter().rev() {
                    if let Err(error) = var.unbind() {
                        if result.is_ok() {
                            result = Err(error);
                        }
                    }
                }
                result
            }
            Form::Symbol(n) if n == "loop" => {
                if fs.len() != 3 {
                    return Err("loop expects bindings and a body".into());
                }
                let bindings = match &fs[1] {
                    Form::List(values) | Form::Vector(values) => values,
                    _ => return Err("loop expects a binding list or vector".into()),
                };
                if bindings.len() % 2 != 0 {
                    return Err("loop bindings require name/value pairs".into());
                }
                let mut names = Vec::new();
                let mut previous = Vec::new();
                for pair in bindings.chunks(2) {
                    let name = match &pair[0] {
                        Form::Symbol(name) => name.clone(),
                        _ => return Err("loop binding name must be a symbol".into()),
                    };
                    let value = eval(&pair[1], env)?;
                    names.push(name.clone());
                    previous.push((name.clone(), env.insert(name, value)));
                }
                let result = loop {
                    match eval(&fs[2], env)? {
                        Value::Recur(values) => {
                            if values.len() != names.len() {
                                break Err("loop recur arity mismatch".into());
                            }
                            for (name, value) in names.iter().cloned().zip(values) {
                                env.insert(name, value);
                            }
                        }
                        result => break Ok(result),
                    }
                };
                for (name, old) in previous.into_iter().rev() {
                    if let Some(old) = old {
                        env.insert(name, old);
                    } else {
                        env.remove(&name);
                    }
                }
                result
            }
            Form::Symbol(n) if n == "if" => {
                if fs.len() != 3 && fs.len() != 4 {
                    return Err("if expects 2 or 3 arguments".into());
                }
                if eval(&fs[1], env)?.truthy() {
                    eval(&fs[2], env)
                } else if fs.len() == 4 {
                    eval(&fs[3], env)
                } else {
                    Ok(Value::Nil)
                }
            }
            Form::Symbol(n) if n == "let" => {
                if fs.len() != 3 {
                    return Err("let expects bindings and a body".into());
                }
                let bindings = match &fs[1] {
                    Form::List(values) | Form::Vector(values) => values,
                    _ => return Err("let expects a binding list or vector".into()),
                };
                if bindings.len() % 2 != 0 {
                    return Err("let bindings require name/value pairs".into());
                }
                let mut previous = Vec::new();
                for pair in bindings.chunks(2) {
                    let name = match &pair[0] {
                        Form::Symbol(name) => name.clone(),
                        _ => return Err("let binding name must be a symbol".into()),
                    };
                    let value = eval(&pair[1], env)?;
                    previous.push((name.clone(), env.insert(name, value)));
                }
                let result = eval(&fs[2], env);
                for (name, old) in previous.into_iter().rev() {
                    if let Some(old) = old {
                        env.insert(name, old);
                    } else {
                        env.remove(&name);
                    }
                }
                result
            }
            Form::Symbol(n) if ["+", "-", "*", "/", "%", "mod"].contains(&n.as_str()) => {
                arithmetic(if n == "mod" { "%" } else { n }, &fs[1..], env)
            }
            _ => {
                let function = eval(&fs[0], env)?;
                let arguments = fs[1..]
                    .iter()
                    .map(|form| eval(form, env))
                    .collect::<Result<Vec<_>, _>>()?;
                call_value(function, arguments)
            }
        },
    }
}

pub fn eval_text(source: &str, env: &mut HashMap<String, Value>) -> Result<String, String> {
    Ok(eval_value_text(source, env)?.display())
}

pub fn eval_text_traced(source: &str, env: &mut HashMap<String, Value>) -> Result<String, String> {
    let _guard = TraceGuard::enable();
    eval_text(source, env).map_err(append_trace)
}

pub fn eval_value_text_traced(
    source: &str,
    env: &mut HashMap<String, Value>,
) -> Result<Value, String> {
    let _guard = TraceGuard::enable();
    eval_value_text(source, env).map_err(append_trace)
}

pub fn eval_value_text(source: &str, env: &mut HashMap<String, Value>) -> Result<Value, String> {
    let forms = parse_forms(source)?;
    let mut result = Value::Nil;
    for form in forms {
        result = eval(&form, env)?;
        if matches!(result, Value::Recur(_)) {
            return Err("recur must be inside loop".into());
        }
    }
    Ok(result)
}

use std::cell::{Cell, RefCell};
use std::collections::HashMap;
use std::rc::Rc;

#[derive(Debug, Clone, PartialEq)]
pub enum Form {
    Number(i64),
    Symbol(String),
    Keyword(String),
    String(String),
    Map(Vec<(Form, Form)>),
    Vector(Vec<Form>),
    List(Vec<Form>),
}

#[derive(Debug, Clone)]
pub enum Value {
    Number(i64),
    Bool(bool),
    String(String),
    Keyword(String),
    Bytes(Vec<u8>),
    ByteBuffer(Rc<RefCell<Vec<u8>>>),
    Array(Rc<RefCell<Vec<Value>>>),
    Object(Rc<RefCell<Vec<(String, Value)>>>),
    Promise(Promise),
    Recur(Vec<Value>),
    Map(Vec<(Value, Value)>),
    List(Vec<Value>),
    Symbol(String),
    Function(Rc<Function>),
    Vector(Vec<Value>),
    Iterator(Rc<RefCell<IteratorState>>),
    Var(Rc<RefCell<Value>>),
    Nil,
}

#[derive(Debug, Clone)]
pub struct Function {
    params: Vec<String>,
    variadic: Option<String>,
    body: Vec<Form>,
    captured: Rc<RefCell<HashMap<String, Value>>>,
}

#[derive(Debug, Clone)]
enum IteratorGenerator { Constant(Value), Repeated(Rc<Function>), Iterate(Rc<Function>, Value), TakeWhile(Rc<Function>, Value), DropWhile(Rc<Function>, Value, bool), Map(Rc<Function>, Value), Filter(Rc<Function>, Value), Mapcat(Rc<Function>, Value, Option<Value>), Keep(Rc<Function>, Value), Zip(Vec<Value>), Interleave(Vec<Value>, usize), Partition(Value, usize, bool) }

#[derive(Debug, Clone)]
pub struct IteratorState {
    values: Vec<Value>,
    index: usize,
    closed: bool,
    cycle: bool,
    generator: Option<IteratorGenerator>,
}

impl IteratorState {
    fn new(values: Vec<Value>) -> Self { Self { values, index: 0, closed: false, cycle: false, generator: None } }
    fn generated(generator: IteratorGenerator) -> Self { Self { values: Vec::new(), index: 0, closed: false, cycle: false, generator: Some(generator) } }
    fn has_next(&self) -> bool { !self.closed && (self.generator.is_some() || (!self.values.is_empty() && (self.cycle || self.index < self.values.len()))) }
    fn next(&mut self) -> Result<Value, String> {
        if self.closed { return Err("iter-next reached the end of the iterator".into()); }
        if let Some(generator)=&mut self.generator {
            return match generator {
                IteratorGenerator::Constant(value) => Ok(value.clone()),
                IteratorGenerator::Repeated(function) => call_function(function, Vec::new()),
                IteratorGenerator::Iterate(function, current) => { let output=current.clone(); *current=call_function(function, vec![current.clone()])?; Ok(output) },
                IteratorGenerator::TakeWhile(function, source) => { let value=iterator_next(source)?; if call_function(function, vec![value.clone()])?.truthy() { Ok(value) } else { self.closed=true; Err("iter-next reached the end of the iterator".into()) } },
                IteratorGenerator::DropWhile(function, source, started) => { loop { let value=iterator_next(source)?; if *started || !call_function(function, vec![value.clone()])?.truthy() { *started=true; break Ok(value); } } },
                IteratorGenerator::Map(function, source) => { let value=iterator_next(source)?; call_function(function, vec![value]) },
                IteratorGenerator::Filter(function, source) => { loop { let value=iterator_next(source)?; if call_function(function, vec![value.clone()])?.truthy() { break Ok(value); } } },
                IteratorGenerator::Mapcat(function, source, pending) => { loop { if let Some(iterator)=pending { match iterator_next(iterator) { Ok(value)=>break Ok(value), Err(_)=>*pending=None } } let value=iterator_next(source)?; *pending=Some(make_iterator(call_function(function, vec![value])?)?); } },
                IteratorGenerator::Keep(function, source) => { loop { let value=iterator_next(source)?; let mapped=call_function(function, vec![value])?; if !matches!(mapped, Value::Nil) { break Ok(mapped); } } },
                IteratorGenerator::Zip(sources) => { let mut values=Vec::new(); for source in sources.iter() { match iterator_next(source) { Ok(value)=>values.push(value), Err(error)=>{ self.closed=true; return Err(error); } } } Ok(Value::Vector(values)) },
                IteratorGenerator::Interleave(sources, index) => { if sources.is_empty() { self.closed=true; return Err("iter-next reached the end of the iterator".into()); } let source=&sources[*index]; let value=iterator_next(source).map_err(|error| { self.closed=true; error })?; *index=(*index+1)%sources.len(); Ok(value) },
                IteratorGenerator::Partition(source, amount, all) => { let mut values=Vec::new(); for _ in 0..*amount { match iterator_next(source) { Ok(value)=>values.push(value), Err(error)=>{ self.closed=true; if values.is_empty() || !*all { return Err(error); } break; } } } if values.is_empty() { self.closed=true; Err("iter-next reached the end of the iterator".into()) } else { Ok(Value::Vector(values)) } },
            };
        }
        if self.values.is_empty() { return Err("iter-next reached the end of the iterator".into()); }
        if self.cycle && self.index >= self.values.len() { self.index = 0; }
        if self.index >= self.values.len() { return Err("iter-next reached the end of the iterator".into()); }
        let value = self.values[self.index].clone(); self.index += 1; Ok(value)
    }
    fn close(&mut self) { self.closed = true; }
}

impl PartialEq for Value {
    fn eq(&self, other: &Self) -> bool {
        match (self, other) {
            (Value::Number(a), Value::Number(b)) => a == b,
            (Value::Bool(a), Value::Bool(b)) => a == b,
            (Value::String(a), Value::String(b)) => a == b,
            (Value::Keyword(a), Value::Keyword(b)) => a == b,
            (Value::Bytes(a), Value::Bytes(b)) => a == b,
            (Value::ByteBuffer(a), Value::ByteBuffer(b)) => *a.borrow() == *b.borrow(),
            (Value::Array(a), Value::Array(b)) => Rc::ptr_eq(a, b),
            (Value::Object(a), Value::Object(b)) => Rc::ptr_eq(a, b),
            (Value::Promise(a), Value::Promise(b)) => Rc::ptr_eq(&a.state, &b.state),
            (Value::Recur(a), Value::Recur(b)) => a == b,
            (Value::Map(a), Value::Map(b)) => a == b,
            (Value::List(a), Value::List(b)) => a == b,
            (Value::Symbol(a), Value::Symbol(b)) => a == b,
            (Value::Function(a), Value::Function(b)) => Rc::ptr_eq(a, b),
            (Value::Vector(a), Value::Vector(b)) => a == b,
            (Value::Iterator(a), Value::Iterator(b)) => Rc::ptr_eq(a, b),
            (Value::Var(a), Value::Var(b)) => Rc::ptr_eq(a, b),
            (Value::Nil, Value::Nil) => true,
            _ => false,
        }
    }
}

impl Value {
    pub fn display(&self) -> String {
        match self {
            Self::Number(v) => v.to_string(),
            Self::Bool(v) => v.to_string(),
            Self::String(v) => format!("\"{v}\""),
            Self::Keyword(v) => format!(":{v}"),
            Self::Bytes(values) => format!("#bytes[{}]", values.iter().map(|v| (*v as i8).to_string()).collect::<Vec<_>>().join(" ")),
            Self::ByteBuffer(values) => format!("(bytes {})", values.borrow().iter().map(|v| (*v as i8).to_string()).collect::<Vec<_>>().join(" ")),
            Self::Array(values) => format!("(array {})", values.borrow().iter().map(Value::display).collect::<Vec<_>>().join(" ")),
            Self::Object(values) => format!("(object {})", values.borrow().iter().map(|(key, value)| format!("\"{}\" {}", key, value.display())).collect::<Vec<_>>().join(" ")),
            Self::Promise(_) => "<promise>".into(),
            Self::Recur(values) => format!("<recur {}>", values.iter().map(Value::display).collect::<Vec<_>>().join(" ")),
            Self::Map(values) => format!("{{{}}}", values.iter().map(|(k, v)| format!("{} {}", k.display(), v.display())).collect::<Vec<_>>().join(" ")),
            Self::List(values) => format!("({})", values.iter().map(Value::display).collect::<Vec<_>>().join(" ")),
            Self::Symbol(v) => v.clone(),
            Self::Function(_) => "<fn>".into(),
            Self::Vector(values) => format!("[{}]", values.iter().map(Value::display).collect::<Vec<_>>().join(" ")),
            Self::Iterator(_) => "<iterator>".into(),
            Self::Var(_) => "<var>".into(),
            Self::Nil => "nil".into(),
        }
    }
    fn truthy(&self) -> bool {
        !matches!(self, Self::Nil | Self::Bool(false))
    }
}

pub type ProtocolFn = fn(&[Value]) -> Result<Value, String>;

#[derive(Default)]
pub struct ProtocolRegistry {
    methods: HashMap<(String, String), ProtocolFn>,
}

#[allow(dead_code)]
impl ProtocolRegistry {
    pub fn new() -> Self { Self::default() }

    pub fn register(&mut self, protocol: impl Into<String>, method: impl Into<String>, function: ProtocolFn) {
        self.methods.insert((protocol.into(), method.into()), function);
    }

    pub fn invoke(&self, protocol: &str, method: &str, arguments: &[Value]) -> Result<Value, String> {
        self.methods.get(&(protocol.to_string(), method.to_string())).copied()
            .ok_or_else(|| format!("missing protocol method: {protocol}/{method}"))?(arguments)
    }

    pub fn contains(&self, protocol: &str, method: &str) -> bool {
        self.methods.contains_key(&(protocol.to_string(), method.to_string()))
    }
}

#[allow(dead_code)]
#[derive(Debug, Clone, PartialEq)]
pub enum PromiseState {
    Pending,
    Fulfilled(Value),
    Rejected(String),
}

#[derive(Debug, Clone)]
pub struct Promise {
    state: Rc<RefCell<PromiseState>>,
}

impl Promise {
    pub fn new() -> Self { Self { state: Rc::new(RefCell::new(PromiseState::Pending)) } }

    pub fn state(&self) -> PromiseState { self.state.borrow().clone() }

    pub fn resolve(&self, value: Value) -> bool {
        let mut state = self.state.borrow_mut();
        if !matches!(*state, PromiseState::Pending) { return false; }
        *state = PromiseState::Fulfilled(value); true
    }

    pub fn reject(&self, error: impl Into<String>) -> bool {
        let mut state = self.state.borrow_mut();
        if !matches!(*state, PromiseState::Pending) { return false; }
        *state = PromiseState::Rejected(error.into()); true
    }

    pub fn adopt(&self, other: &Promise) -> bool {
        match other.state() {
            PromiseState::Pending => false,
            PromiseState::Fulfilled(value) => self.resolve(value),
            PromiseState::Rejected(error) => self.reject(error),
        }
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
        match self { Self::Unsupported => "unsupported", Self::Denied => "denied", Self::Invalid(_) => "invalid" }
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
        match self { Self::Unsupported => "unsupported", Self::Denied => "denied", Self::Invalid(_) => "invalid" }
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
    fn connect(&self, host: &str, port: u16, callback: SocketCallback) -> Result<SocketHandle, SocketError>;
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
pub struct NativeFileProvider { root: PathBuf }

#[cfg(not(target_arch = "wasm32"))]
impl NativeFileProvider {
    pub fn new(root: impl AsRef<Path>) -> Self { Self { root: root.as_ref().to_path_buf() } }

    fn scoped(&self, path: &str) -> Result<PathBuf, FileError> {
        let relative = Path::new(path);
        if relative.is_absolute() {
            return if relative == self.root || relative.strip_prefix(&self.root).is_ok() { Ok(relative.to_path_buf()) } else { Err(FileError::Denied) };
        }
        if relative.components().any(|component| matches!(component, std::path::Component::ParentDir)) { return Err(FileError::Denied); }
        Ok(self.root.join(relative))
    }
}

#[cfg(not(target_arch = "wasm32"))]
impl FileProvider for NativeFileProvider {
    fn resolve(&self, root: &str, path: &str) -> Result<String, FileError> {
        if Path::new(root) != self.root { return Err(FileError::Denied); }
        self.scoped(path).map(|path| path.to_string_lossy().into_owned())
    }

    fn read(&self, path: &str) -> Result<Promise, FileError> {
        let path = self.scoped(path)?; let promise = Promise::new();
        match std::fs::read(path) { Ok(bytes) => { promise.resolve(Value::Bytes(bytes)); }, Err(error) => { promise.reject(error.to_string()); } }
        Ok(promise)
    }

    fn write(&self, path: &str, bytes: Vec<u8>) -> Result<Promise, FileError> {
        let path = self.scoped(path)?; let promise = Promise::new();
        match std::fs::write(path, bytes) { Ok(()) => { promise.resolve(Value::Nil); }, Err(error) => { promise.reject(error.to_string()); } }
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
    fn connect(&self, host: &str, port: u16, callback: SocketCallback) -> Result<SocketHandle, SocketError> {
        if host.is_empty() || port == 0 { return Err(SocketError::Invalid("host and port are required".into())); }
        let stream = TcpStream::connect((host, port)).map_err(|error| SocketError::Invalid(error.to_string()))?;
        let handle = self.next_handle.get(); self.next_handle.set(handle + 1);
        self.sockets.borrow_mut().insert(handle, stream);
        self.callbacks.borrow_mut().insert(handle, callback);
        callback(SocketEvent::Connected(handle)); Ok(handle)
    }

    fn send(&self, socket: SocketHandle, bytes: &[u8]) -> Result<usize, SocketError> {
        let mut sockets = self.sockets.borrow_mut(); let stream = sockets.get_mut(&socket).ok_or_else(|| SocketError::Invalid("unknown socket".into()))?;
        stream.write_all(bytes).map_err(|error| SocketError::Invalid(error.to_string()))?; drop(sockets);
        if let Some(callback) = self.callbacks.borrow().get(&socket).copied() { callback(SocketEvent::Data(socket, bytes.to_vec())); }
        Ok(bytes.len())
    }

    fn close(&self, socket: SocketHandle) -> Result<(), SocketError> {
        if self.sockets.borrow_mut().remove(&socket).is_none() { return Err(SocketError::Invalid("unknown socket".into())); }
        if let Some(callback) = self.callbacks.borrow_mut().remove(&socket) { callback(SocketEvent::Closed(socket)); }
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
}

impl Default for ProviderRegistry {
    fn default() -> Self { Self { file: None, socket: None } }
}

impl ProviderRegistry {
    pub fn new() -> Self { Self::default() }

    pub fn install_file<P: FileProvider + 'static>(&mut self, provider: P) { self.file = Some(Rc::new(provider)); }
    pub fn install_socket<P: SocketProvider + 'static>(&mut self, provider: P) { self.socket = Some(Rc::new(provider)); }
    pub fn file(&self) -> Option<Rc<dyn FileProvider>> { self.file.clone() }
    pub fn socket(&self) -> Option<Rc<dyn SocketProvider>> { self.socket.clone() }
    pub fn capabilities(&self) -> ProviderCapabilities { ProviderCapabilities { file: self.file.is_some(), socket: self.socket.is_some() } }
}

#[derive(Debug, Clone)]
pub struct MemoryFileProvider {
    root: String,
    files: Rc<RefCell<HashMap<String, Vec<u8>>>>,
}

impl MemoryFileProvider {
    pub fn new(root: impl Into<String>) -> Self {
        Self { root: root.into().trim_end_matches('/').to_string(), files: Rc::new(RefCell::new(HashMap::new())) }
    }

    fn within_root(&self, path: &str) -> bool {
        path == self.root || path.strip_prefix(&self.root).is_some_and(|rest| rest.starts_with('/'))
    }

    pub fn insert(&self, path: &str, bytes: Vec<u8>) -> Result<(), FileError> {
        if !self.within_root(path) { return Err(FileError::Denied); }
        self.files.borrow_mut().insert(path.to_string(), bytes); Ok(())
    }
}

impl FileProvider for MemoryFileProvider {
    fn resolve(&self, root: &str, path: &str) -> Result<String, FileError> {
        if root != self.root || path.starts_with('/') { return Err(FileError::Denied); }
        let mut result = self.root.clone();
        for segment in path.split('/') {
            match segment { "" | "." => {}, ".." => return Err(FileError::Denied), segment if segment.contains('\0') => return Err(FileError::Invalid("path contains NUL".into())), segment => { result.push('/'); result.push_str(segment); } }
        }
        Ok(result)
    }

    fn read(&self, path: &str) -> Result<Promise, FileError> {
        if !self.within_root(path) { return Err(FileError::Denied); }
        let promise = Promise::new();
        match self.files.borrow().get(path) { Some(bytes) => { promise.resolve(Value::Bytes(bytes.clone())); }, None => { promise.reject("file not found"); } }
        Ok(promise)
    }

    fn write(&self, path: &str, bytes: Vec<u8>) -> Result<Promise, FileError> {
        if !self.within_root(path) { return Err(FileError::Denied); }
        self.files.borrow_mut().insert(path.to_string(), bytes);
        let promise = Promise::new(); promise.resolve(Value::Nil); Ok(promise)
    }
}

#[derive(Debug, Default, Clone, Copy)]
pub struct UnsupportedFileProvider;

impl FileProvider for UnsupportedFileProvider {
    fn resolve(&self, _root: &str, _path: &str) -> Result<String, FileError> { Err(FileError::Unsupported) }
    fn read(&self, _path: &str) -> Result<Promise, FileError> { Err(FileError::Unsupported) }
    fn write(&self, _path: &str, _bytes: Vec<u8>) -> Result<Promise, FileError> { Err(FileError::Unsupported) }
}

#[derive(Debug, Clone)]
pub struct LoopbackSocketProvider {
    next_handle: Rc<Cell<SocketHandle>>,
    callbacks: Rc<RefCell<HashMap<SocketHandle, SocketCallback>>>,
}

impl Default for LoopbackSocketProvider {
    fn default() -> Self { Self { next_handle: Rc::new(Cell::new(1)), callbacks: Rc::new(RefCell::new(HashMap::new())) } }
}

impl SocketProvider for LoopbackSocketProvider {
    fn connect(&self, host: &str, port: u16, callback: SocketCallback) -> Result<SocketHandle, SocketError> {
        if host.is_empty() || port == 0 { return Err(SocketError::Invalid("host and port are required".into())); }
        let handle = self.next_handle.get(); self.next_handle.set(handle + 1);
        self.callbacks.borrow_mut().insert(handle, callback);
        callback(SocketEvent::Connected(handle));
        Ok(handle)
    }

    fn send(&self, socket: SocketHandle, bytes: &[u8]) -> Result<usize, SocketError> {
        let callback = self.callbacks.borrow().get(&socket).copied().ok_or_else(|| SocketError::Invalid("unknown socket".into()))?;
        callback(SocketEvent::Data(socket, bytes.to_vec()));
        Ok(bytes.len())
    }

    fn close(&self, socket: SocketHandle) -> Result<(), SocketError> {
        let callback = self.callbacks.borrow_mut().remove(&socket).ok_or_else(|| SocketError::Invalid("unknown socket".into()))?;
        callback(SocketEvent::Closed(socket));
        Ok(())
    }
}

#[derive(Debug, Default, Clone, Copy)]
pub struct UnsupportedSocketProvider;

impl SocketProvider for UnsupportedSocketProvider {
    fn connect(&self, _host: &str, _port: u16, _callback: SocketCallback) -> Result<SocketHandle, SocketError> { Err(SocketError::Unsupported) }
    fn send(&self, _socket: SocketHandle, _bytes: &[u8]) -> Result<usize, SocketError> { Err(SocketError::Unsupported) }
    fn close(&self, _socket: SocketHandle) -> Result<(), SocketError> { Err(SocketError::Unsupported) }
}

pub fn receiver_category(value: &Value) -> &'static str {
    match value {
        Value::Nil => "nil",
        Value::Number(_) => "number",
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
        Value::Vector(_) => "vector",
        Value::Map(_) => "map",
        Value::Iterator(_) => "iterator",
        Value::Var(_) => "var",
    }
}

#[derive(Debug, Clone, PartialEq)]
enum Token {
    Open,
    Close,
    VectorOpen,
    VectorClose,
    MapOpen,
    MapClose,
    Quote,
    String(String),
    Atom(String),
}

fn tokenize(source: &str) -> Vec<Token> {
    let mut tokens = Vec::new();
    let mut atom = String::new();
    let mut string = String::new();
    let mut quoted = false;
    let mut escaped = false;
    let flush = |tokens: &mut Vec<Token>, atom: &mut String| {
        if !atom.is_empty() {
            tokens.push(Token::Atom(std::mem::take(atom)));
        }
    };
    for c in source.chars() {
        if quoted {
            if escaped {
                string.push(match c { 'n' => '\n', 'r' => '\r', 't' => '\t', '\\' => '\\', '"' => '"', other => other });
                escaped = false;
            } else if c == '\\' {
                escaped = true;
            } else if c == '"' {
                tokens.push(Token::String(std::mem::take(&mut string)));
                quoted = false;
            } else {
                string.push(c);
            }
            continue;
        }
        match c {
            '\'' => { flush(&mut tokens, &mut atom); tokens.push(Token::Quote); }
            '"' => { flush(&mut tokens, &mut atom); quoted = true; }
            '(' => { flush(&mut tokens, &mut atom); tokens.push(Token::Open); }
            ')' => { flush(&mut tokens, &mut atom); tokens.push(Token::Close); }
            '[' => { flush(&mut tokens, &mut atom); tokens.push(Token::VectorOpen); }
            ']' => { flush(&mut tokens, &mut atom); tokens.push(Token::VectorClose); }
            '{' => { flush(&mut tokens, &mut atom); tokens.push(Token::MapOpen); }
            '}' => { flush(&mut tokens, &mut atom); tokens.push(Token::MapClose); }
            c if c.is_whitespace() => flush(&mut tokens, &mut atom),
            _ => atom.push(c),
        }
    }
    if quoted { tokens.push(Token::Atom("unterminated string".into())); }
    flush(&mut tokens, &mut atom);
    tokens
}

fn parse_form(tokens: &[Token], cursor: &mut usize) -> Result<Form, String> {
    let token = tokens
        .get(*cursor)
        .ok_or_else(|| "unexpected end of input".to_string())?;
    *cursor += 1;
    match token {
        Token::String(v) => Ok(Form::String(v.clone())),
        Token::Quote => { let form = parse_form(tokens, cursor)?; Ok(Form::List(vec![Form::Symbol("quote".into()), form])) },
        Token::MapClose => Err("unexpected }".into()),
        Token::MapOpen => {
            let mut forms = Vec::new();
            while !matches!(tokens.get(*cursor), Some(Token::MapClose)) {
                if *cursor >= tokens.len() { return Err("unclosed map".into()); }
                forms.push(parse_form(tokens, cursor)?);
            }
            *cursor += 1;
            if forms.len() % 2 != 0 { return Err("map literal requires an even number of forms".into()); }
            Ok(Form::Map(forms.chunks(2).map(|pair| (pair[0].clone(), pair[1].clone())).collect()))
        }
        Token::Atom(v) if v.starts_with(':') => Ok(Form::Keyword(v[1..].to_string())),
        Token::Atom(v) => v
            .parse::<i64>()
            .map(Form::Number)
            .or_else(|_| Ok(Form::Symbol(v.clone()))),
        Token::Close => Err("unexpected )".into()),
        Token::VectorClose => Err("unexpected ]".into()),
        Token::VectorOpen => {
            let mut forms = Vec::new();
            while !matches!(tokens.get(*cursor), Some(Token::VectorClose)) {
                if *cursor >= tokens.len() { return Err("unclosed vector".into()); }
                forms.push(parse_form(tokens, cursor)?);
            }
            *cursor += 1;
            Ok(Form::Vector(forms))
        }
        Token::Open => {
            let mut forms = Vec::new();
            while !matches!(tokens.get(*cursor), Some(Token::Close)) {
                if *cursor >= tokens.len() {
                    return Err("unclosed list".into());
                }
                forms.push(parse_form(tokens, cursor)?);
            }
            *cursor += 1;
            Ok(Form::List(forms))
        }
    }
}

pub fn parse_forms(source: &str) -> Result<Vec<Form>, String> {
    let tokens = tokenize(source);
    let mut cursor = 0;
    let mut forms = Vec::new();
    while cursor < tokens.len() { forms.push(parse_form(&tokens, &mut cursor)?); }
    if forms.is_empty() { return Err("source contains no forms".into()); }
    Ok(forms)
}

pub fn parse(source: &str) -> Result<Form, String> {
    let forms = parse_forms(source)?;
    if forms.len() != 1 { return Err("source contains multiple forms; use eval_text".into()); }
    Ok(forms.into_iter().next().unwrap())
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
        "/" => { if *v == 0 { Err("division by zero".into()) } else { Ok::<i64, String>(r / v) } }
        "%" => { if *v == 0 { Err("division by zero".into()) } else { Ok::<i64, String>(r % v) } }
        _ => unreachable!(),
    })?;
    Ok(Value::Number(result))
}

fn bit_operation(op: &str, args: &[Form], env: &mut HashMap<String, Value>) -> Result<Value, String> {
    let values = args.iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?;
    let integer = |value: &Value| match value { Value::Number(value) => Ok(*value as i32), _ => Err(format!("{op} expects integers")) };
    match op {
        "bit-not" => { if values.len()!=1 { return Err("bit-not expects one integer".into()); } Ok(Value::Number((!integer(&values[0])?) as i64)) }
        "bit-and" | "bit-or" | "bit-xor" => { if values.len()!=2 { return Err(format!("{op} expects two integers")); } let a=integer(&values[0])?; let b=integer(&values[1])?; let result=match op { "bit-and"=>a&b, "bit-or"=>a|b, _=>a^b }; Ok(Value::Number(result as i64)) }
        "bit-shift-left" | "bit-shift-right" => { if values.len()!=2 { return Err(format!("{op} expects an integer and distance")); } let value=integer(&values[0])?; let distance=match &values[1] { Value::Number(distance) if (0..=31).contains(distance) => *distance as u32, _ => return Err("distance must be in the range 0..31".into()) }; let result=if op=="bit-shift-left" { value.wrapping_shl(distance) } else { value.wrapping_shr(distance) }; Ok(Value::Number(result as i64)) }
        _ => Err(format!("unknown bit operation: {op}")),
    }
}

fn comparison(op: &str, args: &[Form], env: &mut HashMap<String, Value>) -> Result<Value, String> {
    if args.len() < 2 { return Err(format!("{op} expects at least two arguments")); }
    let values = args.iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?;
    let numbers = values.iter().map(|value| match value { Value::Number(number) => Ok(*number), _ => Err(format!("{op} expects numbers")) }).collect::<Result<Vec<_>, String>>()?;
    Ok(Value::Bool(numbers.windows(2).all(|pair| match op { "<" => pair[0] < pair[1], ">" => pair[0] > pair[1], "<=" => pair[0] <= pair[1], ">=" => pair[0] >= pair[1], _ => false })))
}

fn value_index(value: &Value) -> Result<usize, String> {
    match value { Value::Number(index) if *index >= 0 => Ok(*index as usize), _ => Err("index must be a non-negative integer".into()) }
}

fn protocol_call(protocol: &str, method: &str, arguments: &[Value]) -> Result<Value, String> {
    match (protocol, method) {
        ("ICount", "count") if arguments.len() == 1 => collection_count(&arguments[0]),
        ("INth", "nth") if arguments.len() == 2 => {
            if let Value::Bytes(bytes) = &arguments[0] {
                let index = value_index(&arguments[1])?; return bytes.get(index).map(|byte| Value::Number(*byte as i8 as i64)).ok_or_else(|| "nth index out of bounds".into());
            }
            if let Value::ByteBuffer(bytes) = &arguments[0] {
                let index = value_index(&arguments[1])?; return bytes.borrow().get(index).map(|byte| Value::Number(*byte as i8 as i64)).ok_or_else(|| "nth index out of bounds".into());
            }
            collection_nth(&arguments[0], &arguments[1])
        }
        ("ILookup", "lookup") if arguments.len() == 2 || arguments.len() == 3 => collection_get(&arguments[0], &arguments[1], arguments.get(2).cloned().unwrap_or(Value::Nil)),
        _ => Err(format!("missing protocol method: {protocol}/{method}")),
    }
}

fn promise_value(value: &Value, operation: &str) -> Result<Promise, String> {
    match value { Value::Promise(promise) => Ok(promise.clone()), _ => Err(format!("{operation} expects a promise")) }
}

fn promise_state_value(promise: &Promise) -> Value {
    Value::Keyword(match promise.state() { PromiseState::Pending => "pending", PromiseState::Fulfilled(_) => "fulfilled", PromiseState::Rejected(_) => "rejected" }.into())
}

fn promise_value_result(promise: &Promise) -> Result<Value, String> {
    match promise.state() { PromiseState::Pending => Err("promise is pending".into()), PromiseState::Fulfilled(value) => Ok(value), PromiseState::Rejected(error) => Err(error) }
}

fn marker_key(value: &Value, operation: &str) -> Result<String, String> {
    match value { Value::String(key) => Ok(key.clone()), Value::Keyword(key) => Ok(key.clone()), _ => Err(format!("{operation} expects a string key")) }
}

fn dot_call(receiver: Value, method: &Form, env: &mut HashMap<String, Value>) -> Result<Value, String> {
    let parts = match method { Form::List(parts) if !parts.is_empty() => parts, _ => return Err("dot call expects a method list".into()) };
    let name = match &parts[0] { Form::Symbol(name) => name.as_str(), _ => return Err("dot method must be a symbol".into()) };
    let args = parts[1..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?;
    match receiver {
        Value::Array(array) => match name {
            "get" => { if args.len() < 1 || args.len() > 2 { return Err("array/get expects an index and optional default".into()); } let index = value_index(&args[0])?; Ok(array.borrow().get(index).cloned().or_else(|| args.get(1).cloned()).unwrap_or(Value::Nil)) }
            "set" => { if args.len() != 2 { return Err("array/set expects an index and value".into()); } let index = value_index(&args[0])?; let mut values = array.borrow_mut(); if index >= values.len() { return Err("array/set index out of bounds".into()); } values[index] = args[1].clone(); drop(values); Ok(Value::Array(array)) }
            "push-first" => { if args.len() != 1 { return Err("array/push-first expects one value".into()); } array.borrow_mut().insert(0, args[0].clone()); Ok(Value::Array(array)) }
            "push-last" => { if args.len() != 1 { return Err("array/push-last expects one value".into()); } array.borrow_mut().push(args[0].clone()); Ok(Value::Array(array)) }
            "pop-first" => { if !args.is_empty() { return Err("array/pop-first expects no arguments".into()); } let mut values=array.borrow_mut(); Ok(if values.is_empty() { Value::Nil } else { values.remove(0) }) }
            "pop-last" => { if !args.is_empty() { return Err("array/pop-last expects no arguments".into()); } Ok(array.borrow_mut().pop().unwrap_or(Value::Nil)) }
            "insert" => { if args.len()!=2 { return Err("array/insert expects an index and value".into()); } let index=value_index(&args[0])?; let mut values=array.borrow_mut(); if index > values.len() { return Err("array/insert index out of bounds".into()); } values.insert(index,args[1].clone()); drop(values); Ok(Value::Array(array)) }
            "remove" => { if args.len()!=1 { return Err("array/remove expects an index".into()); } let index=value_index(&args[0])?; let mut values=array.borrow_mut(); if index >= values.len() { return Err("array/remove index out of bounds".into()); } Ok(values.remove(index)) }
            "clone" => Ok(Value::Array(Rc::new(RefCell::new(array.borrow().clone())))),
            "slice" => { if args.len() != 2 { return Err("array/slice expects start and end".into()); } let start=value_index(&args[0])?; let end=value_index(&args[1])?; let values=array.borrow(); if start > end || end > values.len() { return Err("array/slice range is out of bounds".into()); } Ok(Value::Array(Rc::new(RefCell::new(values[start..end].to_vec())))) }
            _ => Err(format!("unsupported array method: {name}")),
        },
        Value::Object(object) => match name {
            "has?" => { if args.len()!=1 { return Err("object/has? expects a key".into()); } let key=marker_key(&args[0], "object/has?")?; Ok(Value::Bool(object.borrow().iter().any(|(candidate, _)| candidate == &key))) }
            "get" => { if args.len()<1 || args.len()>2 { return Err("object/get expects a key and optional default".into()); } let key=marker_key(&args[0], "object/get")?; Ok(object.borrow().iter().find(|(candidate, _)| candidate == &key).map(|(_, value)| value.clone()).or_else(|| args.get(1).cloned()).unwrap_or(Value::Nil)) }
            "set" => { if args.len()!=2 { return Err("object/set expects a key and value".into()); } let key=marker_key(&args[0], "object/set")?; let mut values=object.borrow_mut(); if let Some((_, value))=values.iter_mut().find(|(candidate, _)| candidate == &key) { *value=args[1].clone(); } else { values.push((key,args[1].clone())); } drop(values); Ok(Value::Object(object)) }
            "delete" => { if args.len()!=1 { return Err("object/delete expects a key".into()); } let key=marker_key(&args[0], "object/delete")?; object.borrow_mut().retain(|(candidate, _)| candidate != &key); Ok(Value::Object(object)) }
            "keys" => { if !args.is_empty() { return Err("object/keys expects no arguments".into()); } Ok(Value::Vector(object.borrow().iter().map(|(key, _)| Value::String(key.clone())).collect())) }
            "vals" => { if !args.is_empty() { return Err("object/vals expects no arguments".into()); } Ok(Value::Vector(object.borrow().iter().map(|(_, value)| value.clone()).collect())) }
            "pairs" => { if !args.is_empty() { return Err("object/pairs expects no arguments".into()); } Ok(Value::Vector(object.borrow().iter().map(|(key, value)| Value::Vector(vec![Value::String(key.clone()), value.clone()])).collect())) }
            "assign" => { if args.len()!=1 { return Err("object/assign expects an object".into()); } let other=match &args[0] { Value::Object(other) => other.clone(), _ => return Err("object/assign expects an object".into()) }; let mut values=object.borrow_mut(); for (key,value) in other.borrow().iter() { if let Some((_, existing))=values.iter_mut().find(|(candidate, _)| candidate==key) { *existing=value.clone(); } else { values.push((key.clone(),value.clone())); } } drop(values); Ok(Value::Object(object)) }
            "clone" => Ok(Value::Object(Rc::new(RefCell::new(object.borrow().clone())))),
            _ => Err(format!("unsupported object method: {name}")),
        },
        _ => Err("dot calls require an array or object marker".into()),
    }
}

fn byte_input(value: &Value, operation: &str) -> Result<u8, String> {
    match value {
        Value::Number(number) if (-128..=255).contains(number) => Ok((*number as i8) as u8),
        _ => Err(format!("{operation} expects a value in the range -128..255")),
    }
}

fn byte_buffer(value: &Value, operation: &str) -> Result<Rc<RefCell<Vec<u8>>>, String> {
    match value { Value::ByteBuffer(bytes) => Ok(bytes.clone()), _ => Err(format!("{operation} expects bytes")) }
}

fn byte_count(value: &Value) -> Result<Value, String> {
    match value { Value::Bytes(bytes) => Ok(Value::Number(bytes.len() as i64)), Value::ByteBuffer(bytes) => Ok(Value::Number(bytes.borrow().len() as i64)), _ => Err("bytes/count expects bytes".into()) }
}

fn byte_get(value: &Value, index: &Value, default: Option<Value>) -> Result<Value, String> {
    let index = value_index(index)?;
    let found = match value { Value::Bytes(bytes) => bytes.get(index).copied(), Value::ByteBuffer(bytes) => bytes.borrow().get(index).copied(), _ => return Err("bytes/get expects bytes".into()) };
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
    let start = value_index(start)?; let end = value_index(end)?; let bytes = byte_buffer(value, "bytes/slice")?; let bytes = bytes.borrow();
    if start > end || end > bytes.len() { return Err(format!("bytes/slice range is out of bounds: {start}..{end}")); }
    Ok(Value::ByteBuffer(Rc::new(RefCell::new(bytes[start..end].to_vec()))))
}

fn byte_set(value: &Value, index: &Value, item: &Value) -> Result<Value, String> {
    let index = value_index(index)?; let item = byte_input(item, "bytes/set")?; let bytes = byte_buffer(value, "bytes/set")?;
    let mut bytes = bytes.borrow_mut(); if index >= bytes.len() { return Err("bytes/set index out of bounds".into()); } bytes[index] = item; Ok(value.clone())
}

fn iterator_values(value: Value) -> Result<Vec<Value>, String> {
    match value {
        Value::Nil => Ok(Vec::new()),
        Value::Vector(values) | Value::List(values) => Ok(values),
        Value::String(text) => Ok(text.chars().map(|c| Value::String(c.to_string())).collect()),
        Value::Bytes(bytes) => Ok(bytes.into_iter().map(|byte| Value::Number(byte as i8 as i64)).collect()),
        Value::ByteBuffer(bytes) => Ok(bytes.borrow().iter().map(|byte| Value::Number(*byte as i8 as i64)).collect()),
        Value::Array(values) => Ok(values.borrow().clone()),
        Value::Object(values) => Ok(values.borrow().iter().map(|(key, value)| Value::Vector(vec![Value::String(key.clone()), value.clone()])).collect()),
        Value::Map(entries) => Ok(entries.into_iter().map(|(key, value)| Value::Vector(vec![key, value])).collect()),
        Value::Iterator(iterator) => {
            let mut state = iterator.borrow_mut();
            if state.closed { return Ok(Vec::new()); }
            if state.generator.is_some() { return Err("cannot materialize an infinite iterator".into()); }
            let values = state.values[state.index..].to_vec(); state.index = state.values.len(); Ok(values)
        }
        _ => Err("iter expects a collection".into()),
    }
}

fn make_iterator(value: Value) -> Result<Value, String> {
    Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::new(iterator_values(value)?)))))
}

fn iterator_from_values(values: Vec<Value>) -> Value { Value::Iterator(Rc::new(RefCell::new(IteratorState::new(values)))) }

fn iterator_constant(value: Value) -> Value { Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Constant(value))))) }
fn iterator_repeated(function: Rc<Function>) -> Value { Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Repeated(function))))) }
fn iterator_iterate(function: Rc<Function>, seed: Value) -> Value { Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Iterate(function, seed))))) }
fn iterator_take_while(function: Rc<Function>, value: Value) -> Result<Value, String> {
    let source=match value { Value::Iterator(iterator) => { if iterator.borrow().generator.is_none() { Value::Iterator(iterator) } else { Value::Iterator(iterator) } }, value => make_iterator(value)? };
    if let Value::Iterator(iterator)=&source { if iterator.borrow().generator.is_none() { let values=iterator_values(source)?; let mut output=Vec::new(); for value in values { if !call_function(&function, vec![value.clone()])?.truthy() { break; } output.push(value); } return Ok(iterator_from_values(output)); } }
    Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::TakeWhile(function, source))))))
}
fn iterator_map(function: Rc<Function>, value: Value) -> Result<Value, String> { let source=match value { Value::Iterator(iterator) => Value::Iterator(iterator), value => make_iterator(value)? }; if let Value::Iterator(iterator)=&source { if iterator.borrow().generator.is_none() { let values=iterator_values(source)?; return Ok(iterator_from_values(values.into_iter().map(|value| call_function(&function, vec![value])).collect::<Result<Vec<_>,_>>()?)); } } Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Map(function, source)))))) }
fn iterator_partition(value: Value, amount: usize, all: bool) -> Result<Value, String> { if amount==0 { return Err("partition amount must be positive".into()); } let source=match value { Value::Iterator(iterator)=>Value::Iterator(iterator), value=>make_iterator(value)? }; if let Value::Iterator(iterator)=&source { if iterator.borrow().generator.is_none() { let values=iterator_values(source)?; let mut output=Vec::new(); for chunk in values.chunks(amount) { if !all && chunk.len()!=amount { break; } output.push(Value::Vector(chunk.to_vec())); } return Ok(iterator_from_values(output)); } } Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Partition(source,amount,all)))))) }

fn iterator_interleave(values: Vec<Value>) -> Result<Value, String> { let sources=values.into_iter().map(|value| match value { Value::Iterator(iterator)=>Ok(Value::Iterator(iterator)), value=>make_iterator(value) }).collect::<Result<Vec<_>,_>>()?; if sources.iter().any(|value| matches!(value,Value::Iterator(iterator) if iterator.borrow().generator.is_some())) { return Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Interleave(sources,0)))))); } let collections=sources.iter().map(|value| iterator_values(value.clone())).collect::<Result<Vec<_>,_>>()?; let limit=collections.iter().map(Vec::len).min().unwrap_or(0); let mut output=Vec::new(); for index in 0..limit { for values in &collections { output.push(values[index].clone()); } } Ok(iterator_from_values(output)) }

fn iterator_zip(values: Vec<Value>) -> Result<Value, String> { let sources=values.into_iter().map(|value| match value { Value::Iterator(iterator)=>Ok(Value::Iterator(iterator)), value=>make_iterator(value) }).collect::<Result<Vec<_>,_>>()?; if sources.iter().any(|value| matches!(value,Value::Iterator(iterator) if iterator.borrow().generator.is_some())) { return Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Zip(sources)))))); } let collections=sources.iter().map(|value| iterator_values(value.clone())).collect::<Result<Vec<_>,_>>()?; let limit=collections.iter().map(Vec::len).min().unwrap_or(0); Ok(iterator_from_values((0..limit).map(|index| Value::Vector(collections.iter().map(|values| values[index].clone()).collect())).collect())) }

fn iterator_mapcat(function: Rc<Function>, value: Value) -> Result<Value, String> { let source=match value { Value::Iterator(iterator)=>Value::Iterator(iterator), value=>make_iterator(value)? }; if let Value::Iterator(iterator)=&source { if iterator.borrow().generator.is_none() { let values=iterator_values(source)?; let mut output=Vec::new(); for value in values { output.extend(iterator_values(call_function(&function, vec![value])?)?); } return Ok(iterator_from_values(output)); } } Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Mapcat(function, source, None)))))) }
fn iterator_keep(function: Rc<Function>, value: Value) -> Result<Value, String> { let source=match value { Value::Iterator(iterator)=>Value::Iterator(iterator), value=>make_iterator(value)? }; if let Value::Iterator(iterator)=&source { if iterator.borrow().generator.is_none() { let values=iterator_values(source)?; let mut output=Vec::new(); for value in values { let mapped=call_function(&function, vec![value])?; if !matches!(mapped,Value::Nil) { output.push(mapped); } } return Ok(iterator_from_values(output)); } } Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Keep(function, source)))))) }

fn iterator_filter(function: Rc<Function>, value: Value) -> Result<Value, String> { let source=match value { Value::Iterator(iterator) => Value::Iterator(iterator), value => make_iterator(value)? }; if let Value::Iterator(iterator)=&source { if iterator.borrow().generator.is_none() { let values=iterator_values(source)?; return Ok(iterator_from_values(values.into_iter().filter_map(|value| match call_function(&function, vec![value.clone()]) { Ok(result) if result.truthy()=>Some(Ok(value)), Ok(_)=>None, Err(error)=>Some(Err(error)) }).collect::<Result<Vec<_>,_>>()?)); } } Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::Filter(function, source)))))) }

fn iterator_drop_while(function: Rc<Function>, value: Value) -> Result<Value, String> {
    let source=match value { Value::Iterator(iterator) => Value::Iterator(iterator), value => make_iterator(value)? };
    if let Value::Iterator(iterator)=&source { if iterator.borrow().generator.is_none() { let values=iterator_values(source)?; let mut output=Vec::new(); let mut dropping=true; for value in values { if dropping && call_function(&function, vec![value.clone()])?.truthy() { continue; } dropping=false; output.push(value); } return Ok(iterator_from_values(output)); } }
    Ok(Value::Iterator(Rc::new(RefCell::new(IteratorState::generated(IteratorGenerator::DropWhile(function, source, false))))))
}
fn iterator_take(value: Value, amount: usize) -> Result<Value, String> { let mut output=Vec::new(); for _ in 0..amount { match iterator_next(&value) { Ok(item)=>output.push(item), Err(_) => break } } Ok(iterator_from_values(output)) }
fn iterator_drop(value: Value, amount: usize) -> Result<Value, String> {
    let iterator=match value { Value::Iterator(_) => value, value => make_iterator(value)? };
    for _ in 0..amount { if iterator_next(&iterator).is_err() { break; } }
    Ok(iterator)
}

fn iterator_cycle(value: Value) -> Result<Value, String> {
    let values = iterator_values(value)?; let state = IteratorState { values, index: 0, closed: false, cycle: true, generator: None }; Ok(Value::Iterator(Rc::new(RefCell::new(state))))
}

fn iterator_has_next(value: &Value) -> Result<Value, String> {
    match value { Value::Iterator(iterator) => Ok(Value::Bool(iterator.borrow().has_next())), _ => Err("iter-has? expects an iterator".into()) }
}

fn iterator_next(value: &Value) -> Result<Value, String> {
    match value { Value::Iterator(iterator) => iterator.borrow_mut().next(), _ => Err("iter-next expects an iterator".into()) }
}

fn iterator_close(value: &Value) -> Result<Value, String> {
    match value { Value::Iterator(iterator) => { iterator.borrow_mut().close(); Ok(Value::Nil) }, _ => Err("iter-close expects an iterator".into()) }
}

fn collection_contains(value: &Value, entry: &Value) -> Result<Value, String> {
    let result = match value {
        Value::Map(values) => values.iter().any(|(key, _)| key == entry),
        Value::Object(values) => match entry { Value::String(key) | Value::Keyword(key) => values.borrow().iter().any(|(candidate, _)| candidate == key), _ => false },
        Value::Vector(values) | Value::List(values) => values.iter().any(|candidate| candidate == entry),
        Value::String(text) => matches!(entry, Value::String(part) if text.contains(part)),
        _ => false,
    }; Ok(Value::Bool(result))
}

fn collection_keys(value: &Value) -> Result<Value, String> {
    match value { Value::Map(values) => Ok(Value::Vector(values.iter().map(|(key, _)| key.clone()).collect())), Value::Object(values) => Ok(Value::Vector(values.borrow().iter().map(|(key, _)| Value::String(key.clone())).collect())), _ => Err("keys expects a map or object".into()) }
}

fn collection_vals(value: &Value) -> Result<Value, String> {
    match value { Value::Map(values) => Ok(Value::Vector(values.iter().map(|(_, value)| value.clone()).collect())), Value::Object(values) => Ok(Value::Vector(values.borrow().iter().map(|(_, value)| value.clone()).collect())), _ => Err("vals expects a map or object".into()) }
}

fn collection_first(value: Value) -> Result<Value, String> {
    match value { Value::Iterator(iterator) => { let mut iterator = iterator.borrow_mut(); if iterator.has_next() { iterator.next() } else { Ok(Value::Nil) } }, value => Ok(iterator_values(value)?.into_iter().next().unwrap_or(Value::Nil)) }
}

fn collection_rest(value: Value) -> Result<Value, String> {
    if matches!(value, Value::Iterator(_)) { return iterator_drop(value, 1); }
    let mut values = iterator_values(value)?; if !values.is_empty() { values.remove(0); } Ok(Value::List(values))
}

fn collection_last(value: Value) -> Result<Value, String> { Ok(iterator_values(value)?.into_iter().last().unwrap_or(Value::Nil)) }

fn collection_second(value: Value) -> Result<Value, String> {
    if let Value::Iterator(iterator)=&value { let mut state=iterator.borrow_mut(); let _=state.next()?; return Ok(state.next().unwrap_or(Value::Nil)); }
    let mut values = iterator_values(value)?.into_iter(); values.next(); Ok(values.next().unwrap_or(Value::Nil))
}

fn collection_next(value: Value) -> Result<Value, String> { collection_rest(value) }

fn collection_empty(value: Value) -> Result<Value, String> {
    match value { Value::Iterator(iterator) => Ok(Value::Bool(!iterator.borrow().has_next())), value => Ok(Value::Bool(iterator_values(value)?.is_empty())) }
}

fn collection_count(value: &Value) -> Result<Value, String> {
    let count = match value { Value::Nil => 0, Value::String(v) => v.chars().count(), Value::Vector(v) => v.len(), Value::List(v) => v.len(), Value::Map(v) => v.len(), Value::Bytes(v) => v.len(), Value::ByteBuffer(v) => v.borrow().len(), Value::Array(v) => v.borrow().len(), Value::Object(v) => v.borrow().len(), Value::Iterator(v) => { let state = v.borrow(); if state.generator.is_some() { return Err("count expects a finite collection".into()); } state.values.len().saturating_sub(state.index) }, _ => return Err("count expects a collection".into()) };
    Ok(Value::Number(count as i64))
}

fn collection_get(value: &Value, key: &Value, default: Value) -> Result<Value, String> {
    match value {
        Value::Nil => Ok(default),
        Value::Vector(values) => { let index = value_index(key)?; Ok(values.get(index).cloned().unwrap_or(default)) }
        Value::Array(values) => { let index = value_index(key)?; Ok(values.borrow().get(index).cloned().unwrap_or(default)) }
        Value::List(values) => { let index = value_index(key)?; Ok(values.get(index).cloned().unwrap_or(default)) }
        Value::String(text) => { let index = value_index(key)?; Ok(text.chars().nth(index).map(|c| Value::String(c.to_string())).unwrap_or(default)) }
        Value::Map(entries) => Ok(entries.iter().find(|(candidate, _)| candidate == key).map(|(_, value)| value.clone()).unwrap_or(default)),
        Value::Object(entries) => { let name=match key { Value::String(name) | Value::Keyword(name) => name, _ => return Ok(default) }; Ok(entries.borrow().iter().find(|(candidate, _)| candidate==name).map(|(_, value)| value.clone()).unwrap_or(default)) }
        _ => Err("get expects a collection".into()),
    }
}

fn collection_nth(value: &Value, key: &Value) -> Result<Value, String> {
    let index=value_index(key)?;
    if let Value::Iterator(iterator)=value { let mut state=iterator.borrow_mut(); for _ in 0..index { let _=state.next()?; } return state.next().map_err(|_| "nth index out of bounds".into()); }
    let missing = Value::Nil;
    collection_get(value, key, missing).and_then(|result| if result == Value::Nil { Err("nth index out of bounds".into()) } else { Ok(result) })
}

fn collection_assoc(value: &Value, key: &Value, replacement: Value) -> Result<Value, String> {
    match value {
        Value::Map(entries) => { let mut output=entries.clone(); if let Some((_, item))=output.iter_mut().find(|(candidate, _)| candidate==key) { *item=replacement; } else { output.push((key.clone(), replacement)); } Ok(Value::Map(output)) }
        Value::Object(entries) => { let name=marker_key(key, "object")?; let mut output=entries.borrow().clone(); if let Some((_, item))=output.iter_mut().find(|(candidate, _)| candidate==&name) { *item=replacement; } else { output.push((name,replacement)); } Ok(Value::Object(Rc::new(RefCell::new(output)))) }
        Value::Nil => Ok(Value::Map(vec![(key.clone(), replacement)])),
        _ => Err("assoc expects a map or object".into()),
    }
}

fn collection_get_in(value: Value, keys: &[Value]) -> Result<Value, String> {
    if keys.is_empty() { return Ok(value); }
    let next=collection_get(&value, &keys[0], Value::Nil)?;
    if matches!(next, Value::Nil) { Ok(Value::Nil) } else { collection_get_in(next, &keys[1..]) }
}

fn collection_assoc_in(value: Value, keys: &[Value], replacement: Value) -> Result<Value, String> {
    if keys.is_empty() { return Ok(replacement); }
    let current=if matches!(value, Value::Nil) { Value::Map(Vec::new()) } else { value };
    let child=collection_get(&current, &keys[0], Value::Nil)?;
    let updated=collection_assoc_in(child, &keys[1..], replacement)?;
    collection_assoc(&current, &keys[0], updated)
}

fn literal_value(form: &Form) -> Result<Value, String> {
    match form {
        Form::Number(v) => Ok(Value::Number(*v)), Form::String(v) => Ok(Value::String(v.clone())), Form::Keyword(v) => Ok(Value::Keyword(v.clone())), Form::Symbol(v) => Ok(Value::Symbol(v.clone())),
        Form::Vector(values) => Ok(Value::Vector(values.iter().map(literal_value).collect::<Result<_, _>>()?)),
        Form::List(values) => Ok(Value::List(values.iter().map(literal_value).collect::<Result<_, _>>()?)),
        Form::Map(values) => Ok(Value::Map(values.iter().map(|(k, v)| Ok((literal_value(k)?, literal_value(v)?))).collect::<Result<_, String>>()?)),
    }
}

fn generated_function(params: Vec<String>, body: Vec<Form>, mut captured: HashMap<String, Value>, bindings: Vec<(&str, Value)>) -> Value {
    for (name, value) in bindings { captured.insert(name.to_string(), value); }
    Value::Function(Rc::new(Function { params, variadic: None, body, captured: Rc::new(RefCell::new(captured)) }))
}

fn function_parts(form: &Form) -> Result<(Vec<String>, Option<String>), String> {
    let list = match form { Form::Vector(values) => values, _ => return Err("function parameters must be a vector".into()) };
    let mut params = Vec::new(); let mut variadic = None; let mut index = 0;
    while index < list.len() {
        match &list[index] {
            Form::Symbol(name) if name == "&" => {
                if variadic.is_some() || index + 1 >= list.len() || index + 2 != list.len() { return Err("variadic marker must precede the final parameter".into()); }
                match &list[index + 1] { Form::Symbol(name) => variadic = Some(name.clone()), _ => return Err("variadic parameter must be a symbol".into()) }
                index += 2;
            }
            Form::Symbol(name) => { params.push(name.clone()); index += 1; }
            _ => return Err("function parameters must be symbols".into()),
        }
    }
    Ok((params, variadic))
}

fn call_function(function: &Function, arguments: Vec<Value>) -> Result<Value, String> {
    if function.variadic.is_none() && function.params.len() != arguments.len() { return Err(format!("function expects {} arguments", function.params.len())); }
    if arguments.len() < function.params.len() { return Err(format!("function expects at least {} arguments", function.params.len())); }
    let mut env = function.captured.borrow().clone();
    for (name, value) in function.params.iter().zip(arguments.iter().take(function.params.len())) { env.insert(name.clone(), value.clone()); }
    if let Some(name) = &function.variadic { env.insert(name.clone(), Value::List(arguments.into_iter().skip(function.params.len()).collect())); }
    let mut result = Value::Nil;
    for form in &function.body { result = eval(form, &mut env)?; if matches!(result, Value::Recur(_)) { return Err("recur must be inside loop".into()); } }
    Ok(result)
}

pub fn eval(form: &Form, env: &mut HashMap<String, Value>) -> Result<Value, String> {
    match form {
        Form::Number(v) => Ok(Value::Number(*v)),
        Form::String(v) => Ok(Value::String(v.clone())),
        Form::Keyword(v) => Ok(Value::Keyword(v.clone())),
        Form::List(fs) if fs.len() == 2 && matches!(&fs[0], Form::Symbol(name) if name == "quote") => literal_value(&fs[1]),
        Form::Map(values) => Ok(Value::Map(values.iter().map(|(key, value)| Ok((eval(key, env)?, eval(value, env)?))).collect::<Result<_, String>>()?)),
        Form::Vector(values) => Ok(Value::Vector(values.iter().map(|value| eval(value, env)).collect::<Result<_, _>>()?)),
        Form::Symbol(n) if n == "nil" => Ok(Value::Nil),
        Form::Symbol(n) if n == "true" => Ok(Value::Bool(true)),
        Form::Symbol(n) if n == "false" => Ok(Value::Bool(false)),
        Form::Symbol(n) => env
            .get(n)
            .cloned()
            .ok_or_else(|| format!("unbound symbol: {n}")),
        Form::List(fs) if fs.is_empty() => Ok(Value::Nil),
        Form::List(fs) => match &fs[0] {
            Form::Symbol(n) if n == "fn" => {
                if fs.len() < 3 { return Err("fn expects parameters and a body".into()); }
                let (params, variadic) = function_parts(&fs[1])?;
                Ok(Value::Function(Rc::new(Function { params, variadic, body: fs[2..].to_vec(), captured: Rc::new(RefCell::new(env.clone())) })))
            }
            Form::Symbol(n) if n == "var" => {
                if fs.len()!=2 { return Err("var expects a symbol".into()); }
                let name=match &fs[1] { Form::Symbol(name)=>name, _=>return Err("var expects a symbol".into()) }; let value=env.get(name).cloned().ok_or_else(|| format!("unbound symbol: {name}"))?; Ok(Value::Var(Rc::new(RefCell::new(value))))
            }
            Form::Symbol(n) if n == "deref" => {
                if fs.len()!=2 { return Err("deref expects a var".into()); }
                match eval(&fs[1], env)? { Value::Var(value)=>Ok(value.borrow().clone()), _=>Err("deref expects a var".into()) }
            }
            Form::Symbol(n) if n == "throw" => {
                if fs.len()!=2 { return Err("throw expects one value".into()); }
                let value=eval(&fs[1], env)?; Err(format!("thrown: {}", value.display()))
            }
            Form::Symbol(n) if n == "try" => {
                if fs.len()<2 { return Err("try expects a body".into()); }
                let mut body=Vec::new(); let mut catch_form=None; let mut finally_forms=Vec::new();
                for form in &fs[1..] { match form { Form::List(parts) if !parts.is_empty() && matches!(&parts[0],Form::Symbol(name) if name=="catch") => catch_form=Some(parts), Form::List(parts) if !parts.is_empty() && matches!(&parts[0],Form::Symbol(name) if name=="finally") => finally_forms.extend_from_slice(&parts[1..]), _ if catch_form.is_none() => body.push(form), _ => return Err("try clauses must follow the body".into()) } }
                let mut result=Ok(Value::Nil); for form in body { result=eval(form,env); if result.is_err() { break; } }
                if let Err(ref error)=result { if let Some(parts)=catch_form { if parts.len()!=3 { return Err("catch expects a name and body".into()); } let name=match &parts[1] { Form::Symbol(name)=>name.clone(), _=>return Err("catch name must be a symbol".into()) }; let old=env.insert(name.clone(),Value::String(error.clone())); result=eval(&parts[2],env); if let Some(old)=old { env.insert(name,old); } else { env.remove(&name); } } }
                for form in finally_forms { let final_result=eval(&form,env); if final_result.is_err() { result=final_result; } }
                result
            }
            Form::Symbol(n) if n == "def" => {
                if fs.len()!=3 { return Err("def expects a name and value".into()); }
                let name=match &fs[1] { Form::Symbol(name)=>name.clone(), _=>return Err("def name must be a symbol".into()) }; let value=eval(&fs[2], env)?; env.insert(name, value.clone()); Ok(value)
            }
            Form::Symbol(n) if n == "defn" => {
                if fs.len() < 4 { return Err("defn expects a name, parameters, and a body".into()); }
                let name = match &fs[1] { Form::Symbol(name) => name.clone(), _ => return Err("defn name must be a symbol".into()) };
                let (params, variadic) = function_parts(&fs[2])?;
                let function_ref = Rc::new(Function { params, variadic, body: fs[3..].to_vec(), captured: Rc::new(RefCell::new(env.clone())) });
                let function = Value::Function(function_ref.clone());
                function_ref.captured.borrow_mut().insert(name.clone(), function.clone());
                env.insert(name, function.clone()); Ok(function)
            }
            Form::Symbol(n) if n == "do" => {
                let mut result = Value::Nil;
                for form in &fs[1..] { result = eval(form, env)?; if matches!(result, Value::Recur(_)) { return Ok(result); } }
                Ok(result)
            }
            Form::Symbol(n) if n == "=" => {
                if fs.len() < 3 { return Err("= expects at least 2 arguments".into()); }
                let first = eval(&fs[1], env)?;
                Ok(Value::Bool(fs[2..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?.iter().all(|value| *value == first)))
            }
            Form::Symbol(n) if n == "ns" => {
                if fs.len() < 2 { return Err("ns expects a namespace symbol".into()); }
                match &fs[1] { Form::Symbol(_) => Ok(Value::Nil), _ => Err("ns expects a namespace symbol".into()) }
            }
            Form::Symbol(n) if n == "protocol-call" => {
                if fs.len() < 4 || fs.len() > 5 { return Err("protocol-call expects protocol, method, value, and optional arguments".into()); }
                let protocol = match &fs[1] { Form::Symbol(name) => name.as_str(), _ => return Err("protocol name must be a symbol".into()) };
                let method = match &fs[2] { Form::Symbol(name) => name.as_str(), _ => return Err("protocol method must be a symbol".into()) };
                let mut arguments = vec![eval(&fs[3], env)?];
                arguments.extend(fs[4..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?);
                protocol_call(protocol, method, &arguments)
            }
            Form::Symbol(n) if n == "promise" => {
                if fs.len() != 1 { return Err("promise expects no arguments".into()); }
                Ok(Value::Promise(Promise::new()))
            }
            Form::Symbol(n) if n == "promise/state" => {
                if fs.len() != 2 { return Err("promise/state expects one argument".into()); }
                Ok(promise_state_value(&promise_value(&eval(&fs[1], env)?, "promise/state")?))
            }
            Form::Symbol(n) if n == "promise/value" => {
                if fs.len() != 2 { return Err("promise/value expects one argument".into()); }
                promise_value_result(&promise_value(&eval(&fs[1], env)?, "promise/value")?)
            }
            Form::Symbol(n) if n == "promise/resolve" || n == "promise/reject" => {
                if fs.len() != 3 { return Err(format!("{n} expects a promise and value")); }
                let promise = promise_value(&eval(&fs[1], env)?, n)?;
                let settled = if n == "promise/resolve" { promise.resolve(eval(&fs[2], env)?) } else { promise.reject(match eval(&fs[2], env)? { Value::String(error) => error, value => value.display() }) };
                if !settled { return Err("promise is already settled".into()); }
                Ok(Value::Promise(promise))
            }
            Form::Symbol(n) if n == "promise/adopt" => {
                if fs.len() != 3 { return Err("promise/adopt expects two promises".into()); }
                let promise = promise_value(&eval(&fs[1], env)?, n)?; let other = promise_value(&eval(&fs[2], env)?, n)?;
                if !promise.adopt(&other) { return Err("promise source is pending or destination is settled".into()); }
                Ok(Value::Promise(promise))
            }
            Form::Symbol(n) if ["promise/map", "promise/recover", "promise/finally"].contains(&n.as_str()) => {
                if fs.len()!=3 { return Err(format!("{n} expects a promise and function")); }
                let source=promise_value(&eval(&fs[1], env)?, n)?; let function=match eval(&fs[2], env)? { Value::Function(function)=>function, _=>return Err(format!("{n} expects a function")) }; let output=Promise::new();
                match source.state() {
                    PromiseState::Fulfilled(value) if n=="promise/map" => match call_function(&function, vec![value]) { Ok(value)=>{ output.resolve(value); }, Err(error)=>{ output.reject(error); } },
                    PromiseState::Rejected(error) if n=="promise/recover" => match call_function(&function, vec![Value::String(error)]) { Ok(value)=>{ output.resolve(value); }, Err(error)=>{ output.reject(error); } },
                    PromiseState::Fulfilled(value) if n=="promise/finally" => { let _=call_function(&function,Vec::new()); output.resolve(value); },
                    PromiseState::Rejected(error) if n=="promise/finally" => { let _=call_function(&function,Vec::new()); output.reject(error); },
                    PromiseState::Fulfilled(value) => { output.resolve(value); },
                    PromiseState::Rejected(error) => { output.reject(error); },
                    PromiseState::Pending => {},
                }
                Ok(Value::Promise(output))
            }
            Form::Symbol(n) if n == "array" => {
                let values = fs[1..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?;
                Ok(Value::Array(Rc::new(RefCell::new(values))))
            }
            Form::Symbol(n) if n == "object" => {
                if fs.len() % 2 != 1 { return Err("object expects key/value pairs".into()); }
                let mut values = Vec::new();
                for pair in fs[1..].chunks(2) { let key = marker_key(&eval(&pair[0], env)?, "object")?; values.push((key, eval(&pair[1], env)?)); }
                Ok(Value::Object(Rc::new(RefCell::new(values))))
            }
            Form::Symbol(n) if n == "." => {
                if fs.len() != 3 { return Err("dot expects a receiver and method".into()); }
                let receiver = eval(&fs[1], env)?; dot_call(receiver, &fs[2], env)
            }
            Form::Symbol(n) if n == "bytes" => {
                let values = fs[1..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?;
                let values = values.iter().map(|value| byte_input(value, "bytes")).collect::<Result<Vec<_>, _>>()?;
                Ok(Value::ByteBuffer(Rc::new(RefCell::new(values))))
            }
            Form::Symbol(n) if n == "str" => {
                if fs.len() == 1 { return Ok(Value::String(String::new())); }
                let values = fs[1..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?;
                Ok(Value::String(values.iter().map(|value| match value { Value::String(text) => text.clone(), _ => value.display() }).collect::<Vec<_>>().join("")))
            }
            Form::Symbol(n) if n == "str/count" || n == "str/trim" || n == "str/upper" || n == "str/lower" => {
                if fs.len()!=2 { return Err(format!("{n} expects one string")); }
                let text = match eval(&fs[1], env)? { Value::String(text) => text, _ => return Err(format!("{n} expects a string")) };
                match n.as_str() { "str/count" => Ok(Value::Number(text.chars().count() as i64)), "str/trim" => Ok(Value::String(text.trim().into())), "str/upper" => Ok(Value::String(text.to_uppercase())), "str/lower" => Ok(Value::String(text.to_lowercase())), _ => unreachable!() }
            }
            Form::Symbol(n) if n == "str/encode" => {
                if fs.len()!=2 { return Err("str/encode expects one string".into()); }
                match eval(&fs[1], env)? { Value::String(text) => Ok(Value::ByteBuffer(Rc::new(RefCell::new(text.into_bytes())))), _ => Err("str/encode expects a string".into()) }
            }
            Form::Symbol(n) if n == "str/decode" => {
                if fs.len()!=2 { return Err("str/decode expects bytes".into()); }
                let bytes = byte_buffer(&eval(&fs[1], env)?, "str/decode")?; let raw = bytes.borrow().clone(); String::from_utf8(raw).map(Value::String).map_err(|_| "str/decode invalid UTF-8".into())
            }
            Form::Symbol(n) if n == "bytes/copy" => {
                if fs.len()!=2 { return Err("bytes/copy expects bytes".into()); } byte_copy(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "bytes/slice" => {
                if fs.len()!=4 { return Err("bytes/slice expects bytes, start, and end".into()); } let value=eval(&fs[1], env)?; let start=eval(&fs[2], env)?; let end=eval(&fs[3], env)?; byte_slice(&value, &start, &end)
            }
            Form::Symbol(n) if n == "bytes/count" => {
                if fs.len() != 2 { return Err("bytes/count expects one argument".into()); }
                byte_count(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "bytes/get" => {
                if fs.len() != 3 && fs.len() != 4 { return Err("bytes/get expects an index and optional default".into()); }
                let value = eval(&fs[1], env)?; let index = eval(&fs[2], env)?;
                let default = if fs.len() == 4 { Some(eval(&fs[3], env)?) } else { None };
                let index_num = value_index(&index)?;
                match byte_get(&value, &index, default) { Ok(value) => Ok(value), Err(error) if error.is_empty() => Err(format!("bytes/get index out of bounds: {index_num}")), Err(error) => Err(error) }
            }
            Form::Symbol(n) if n == "bytes/set" => {
                if fs.len() != 4 { return Err("bytes/set expects bytes, index, and value".into()); }
                let value = eval(&fs[1], env)?; let index = eval(&fs[2], env)?; let item = eval(&fs[3], env)?;
                byte_set(&value, &index, &item)
            }
            Form::Symbol(n) if n == "bytes/u8" || n == "bytes/s8" => {
                if fs.len() != 2 { return Err(format!("{n} expects one argument")); }
                let number = match eval(&fs[1], env)? { Value::Number(number) => number, _ => return Err(format!("{n} expects a number")) };
                if !(-128..=255).contains(&number) { return Err(format!("{n} expects a value in the range -128..255")); }
                let raw = (number as i8) as u8;
                Ok(Value::Number(if n == "bytes/u8" { raw as i64 } else { raw as i8 as i64 }))
            }
            Form::Symbol(n) if n == "iter" => {
                if fs.len() != 2 { return Err("iter expects one argument".into()); }
                make_iterator(eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "iter-has?" => {
                if fs.len() != 2 { return Err("iter-has? expects one argument".into()); }
                iterator_has_next(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "iter-next" => {
                if fs.len() != 2 { return Err("iter-next expects one argument".into()); }
                iterator_next(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "iter-close" => {
                if fs.len() != 2 { return Err("iter-close expects one argument".into()); }
                iterator_close(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if ["iter-map", "map", "iter-filter", "filter"].contains(&n.as_str()) => {
                let is_map = n == "iter-map" || n == "map";
                if fs.len() < 3 { return Err(format!("{n} expects a function and collection")); }
                let function = eval(&fs[1], env)?;
                let raw_collection=if fs.len()==3 { Some(eval(&fs[2], env)?) } else { None };
                if fs.len()==3 { if let Value::Function(function_ref)=&function { if is_map { if let Some(value)=raw_collection.clone() { if let Value::Iterator(iterator)=&value { if iterator.borrow().generator.is_some() { return iterator_map(function_ref.clone(), value); } } } } else if let Some(value)=raw_collection.clone() { if let Value::Iterator(iterator)=&value { if iterator.borrow().generator.is_some() { return iterator_filter(function_ref.clone(), value); } } } } }
                let collections = if let Some(value)=raw_collection { vec![iterator_values(value)?] } else { fs[2..].iter().map(|form| eval(form, env).and_then(iterator_values)).collect::<Result<Vec<_>,_>>()? }; let mut output = Vec::new();
                if is_map {
                    let limit=collections.iter().map(Vec::len).min().unwrap_or(0);
                    for index in 0..limit { let args=collections.iter().map(|values| values[index].clone()).collect(); let mapped=match &function { Value::Function(f) => call_function(f,args)?, _ => return Err(format!("{n} expects a function")) }; output.push(mapped); }
                } else {
                    if collections.len()!=1 { return Err(format!("{n} expects one collection")); }
                    for value in collections.into_iter().next().unwrap() { let mapped=match &function { Value::Function(f) => call_function(f, vec![value.clone()])?, _ => return Err(format!("{n} expects a function")) }; if mapped.truthy() { output.push(value); } }
                }
                Ok(iterator_from_values(output))
            }
            Form::Symbol(n) if ["iter-take", "take"].contains(&n.as_str()) => {
                if fs.len()!=3 { return Err(format!("{n} expects an amount and collection")); } let amount=value_index(&eval(&fs[1], env)?)?; iterator_take(eval(&fs[2], env)?, amount)
            }
            Form::Symbol(n) if ["iter-drop", "drop"].contains(&n.as_str()) => {
                if fs.len()!=3 { return Err(format!("{n} expects an amount and collection")); } let amount=value_index(&eval(&fs[1], env)?)?; iterator_drop(eval(&fs[2], env)?, amount)
            }
            Form::Symbol(n) if ["iter-take-while", "take-while", "iter-drop-while", "drop-while"].contains(&n.as_str()) => {
                if fs.len()!=3 { return Err(format!("{n} expects a predicate and collection")); }
                let predicate=match eval(&fs[1], env)? { Value::Function(function)=>function, _=>return Err(format!("{n} expects a function")) }; let value=eval(&fs[2], env)?;
                if n.contains("take-while") { iterator_take_while(predicate,value) } else { iterator_drop_while(predicate,value) }
            }
            Form::Symbol(n) if ["iter-mapcat", "mapcat"].contains(&n.as_str()) => {
                if fs.len()!=3 { return Err(format!("{n} expects a function and collection")); }
                let function=match eval(&fs[1], env)? { Value::Function(function)=>function, _=>return Err(format!("{n} expects a function")) }; iterator_mapcat(function,eval(&fs[2], env)?)
            }
            Form::Symbol(n) if ["iter-keep", "keep"].contains(&n.as_str()) => {
                if fs.len()!=3 { return Err(format!("{n} expects a function and collection")); }
                let function=match eval(&fs[1], env)? { Value::Function(function)=>function, _=>return Err(format!("{n} expects a function")) }; iterator_keep(function,eval(&fs[2], env)?)
            }
            Form::Symbol(n) if ["iter-partition-all", "partition-all", "iter-partition", "partition"].contains(&n.as_str()) => {
                if fs.len()!=3 { return Err(format!("{n} expects an amount and collection")); } let amount=value_index(&eval(&fs[1], env)?)?; iterator_partition(eval(&fs[2], env)?, amount, n.contains("all"))
            }
            Form::Symbol(n) if ["iter-interpose", "interpose"].contains(&n.as_str()) => {
                if fs.len()!=3 { return Err(format!("{n} expects a separator and collection")); } let separator=eval(&fs[1], env)?; let values=iterator_values(eval(&fs[2], env)?)?; let mut output=Vec::new(); for (index,value) in values.into_iter().enumerate() { if index>0 { output.push(separator.clone()); } output.push(value); } Ok(iterator_from_values(output))
            }
            Form::Symbol(n) if ["iter-interleave", "interleave"].contains(&n.as_str()) => {
                if fs.len()<2 { return Err(format!("{n} expects collections")); } let collections=fs[1..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>,_>>()?; iterator_interleave(collections)
            }
            Form::Symbol(n) if ["iter-partition-pair", "partition-pair"].contains(&n.as_str()) => {
                if fs.len()!=2 { return Err(format!("{n} expects one collection")); } let values=iterator_values(eval(&fs[1], env)?)?; Ok(iterator_from_values(values.chunks(2).filter(|chunk| chunk.len()==2).map(|chunk| Value::Vector(chunk.to_vec())).collect()))
            }
            Form::Symbol(n) if ["iter-zip", "zip"].contains(&n.as_str()) => {
                if fs.len()<3 { return Err(format!("{n} expects collections")); } let collections=fs[1..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>,_>>()?; iterator_zip(collections)
            }
            Form::Symbol(n) if n == "iter-cycle" || n == "cycle" => {
                if fs.len()!=2 { return Err(format!("{n} expects one collection")); } iterator_cycle(eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "concat" => {
                if fs.len() < 2 { return Err("concat expects collections".into()); } let mut values=Vec::new(); for form in &fs[1..] { values.extend(iterator_values(eval(form, env)?)?); } Ok(iterator_from_values(values))
            }
            Form::Symbol(n) if n == "range" => {
                if fs.len() < 1 || fs.len() > 3 { return Err("range expects zero, one, or two bounds".into()); }
                let nums = fs[1..].iter().map(|form| match eval(form, env)? { Value::Number(v) => Ok(v), _ => Err("range bounds must be numbers".into()) }).collect::<Result<Vec<_>, String>>()?;
                let (start,end) = match nums.as_slice() { [] => (0,0), [end] => (0,*end), [start,end] => (*start,*end), _ => unreachable!() };
                Ok(iterator_from_values((start..end).map(Value::Number).collect()))
            }
            Form::Symbol(n) if n == "repeat" => {
                if fs.len()!=2 && fs.len()!=3 { return Err("repeat expects a value or amount and value".into()); }
                let (amount, form) = if fs.len()==2 { (None, &fs[1]) } else { (Some(value_index(&eval(&fs[1], env)?)?), &fs[2]) };
                let value=eval(form, env)?;
                if amount.is_none() { return Ok(iterator_constant(value)); }
                let count=amount.unwrap(); Ok(iterator_from_values((0..count).map(|_| value.clone()).collect()))
            }
            Form::Symbol(n) if n == "repeatedly" => {
                if fs.len()!=2 && fs.len()!=3 { return Err("repeatedly expects a function or amount and function".into()); }
                let (amount, form)=if fs.len()==2 {(None,&fs[1])} else {(Some(value_index(&eval(&fs[1], env)?)?),&fs[2])}; let function=match eval(form, env)? { Value::Function(function)=>function, _=>return Err("repeatedly expects a function".into()) }; let generated=iterator_repeated(function); if let Some(amount)=amount { iterator_take(generated,amount) } else { Ok(generated) }
            }
            Form::Symbol(n) if n == "iterate" => {
                if fs.len()!=3 { return Err("iterate expects a function and seed".into()); } let function=match eval(&fs[1], env)? { Value::Function(function)=>function, _=>return Err("iterate expects a function".into()) }; Ok(iterator_iterate(function,eval(&fs[2], env)?))
            }
            Form::Symbol(n) if n == "iter-constantly" => {
                if fs.len()!=2 { return Err("iter-constantly expects a value".into()); } Ok(iterator_constant(eval(&fs[1], env)?))
            }
            Form::Symbol(n) if n == "iter-repeatedly" => {
                if fs.len()!=2 { return Err("iter-repeatedly expects a function".into()); } match eval(&fs[1], env)? { Value::Function(function)=>Ok(iterator_repeated(function)), _=>Err("iter-repeatedly expects a function".into()) }
            }
            Form::Symbol(n) if n == "iter-iterate" => {
                if fs.len()!=3 { return Err("iter-iterate expects a function and seed".into()); } let function=match eval(&fs[1], env)? { Value::Function(function)=>function, _=>return Err("iter-iterate expects a function".into()) }; Ok(iterator_iterate(function,eval(&fs[2], env)?))
            }
            Form::Symbol(n) if ["bit-and", "bit-or", "bit-xor", "bit-not", "bit-shift-left", "bit-shift-right"].contains(&n.as_str()) => bit_operation(n, &fs[1..], env),
            Form::Symbol(n) if ["inc", "dec"].contains(&n.as_str()) => {
                if fs.len()!=2 { return Err(format!("{n} expects one number")); }
                match eval(&fs[1], env)? { Value::Number(value) => Ok(Value::Number(if n=="inc" { value+1 } else { value-1 })), _ => Err(format!("{n} expects a number")) }
            }
            Form::Symbol(n) if ["zero?", "pos?", "neg?", "even?", "odd?"].contains(&n.as_str()) => {
                if fs.len()!=2 { return Err(format!("{n} expects one number")); }
                let value=match eval(&fs[1], env)? { Value::Number(value)=>value, _=>return Err(format!("{n} expects a number")) };
                let result=match n.as_str() { "zero?"=>value==0, "pos?"=>value>0, "neg?"=>value<0, "even?"=>value%2==0, "odd?"=>value%2!=0, _=>false }; Ok(Value::Bool(result))
            }
            Form::Symbol(n) if ["nil?", "some?", "true?", "false?"].contains(&n.as_str()) => {
                if fs.len()!=2 { return Err(format!("{n} expects one value")); }
                let value=eval(&fs[1], env)?; let result=match n.as_str() { "nil?"=>matches!(value,Value::Nil), "some?"=>!matches!(value,Value::Nil), "true?"=>matches!(value,Value::Bool(true)), "false?"=>matches!(value,Value::Bool(false)), _=>false }; Ok(Value::Bool(result))
            }
            Form::Symbol(n) if ["every?", "any?", "some"].contains(&n.as_str()) => {
                if fs.len()!=3 { return Err(format!("{n} expects a predicate and collection")); }
                let predicate=eval(&fs[1], env)?; let values=iterator_values(eval(&fs[2], env)?)?;
                let mut found=None;
                for value in values {
                    let result=match &predicate { Value::Function(function) => call_function(function, vec![value.clone()])?, _ => return Err(format!("{n} expects a function")) };
                    if n=="every?" && !result.truthy() { return Ok(Value::Bool(false)); }
                    if n!="every?" && result.truthy() { found=Some(if n=="some" { result } else { Value::Bool(true) }); break; }
                }
                Ok(match n.as_str() { "every?" => Value::Bool(true), "any?" => Value::Bool(found.is_some()), "some" => found.unwrap_or(Value::Nil), _ => Value::Nil })
            }
            Form::Symbol(n) if n == "constantly" => {
                if fs.len()!=2 { return Err("constantly expects one value".into()); }
                let value=eval(&fs[1], env)?; let mut captured=env.clone(); captured.insert("__constant".into(), value); Ok(Value::Function(Rc::new(Function { params: Vec::new(), variadic: Some("_rest".into()), body: vec![Form::Symbol("__constant".into())], captured: Rc::new(RefCell::new(captured)) })))
            }
            Form::Symbol(n) if n == "complement" => {
                if fs.len()!=2 { return Err("complement expects one function".into()); }
                let predicate=eval(&fs[1], env)?; if !matches!(predicate, Value::Function(_)) { return Err("complement expects a function".into()); }
                Ok(generated_function(vec!["value".into()], vec![Form::List(vec![Form::Symbol("not".into()), Form::List(vec![Form::Symbol("__predicate".into()), Form::Symbol("value".into())])])], env.clone(), vec![("__predicate", predicate)]))
            }
            Form::Symbol(n) if n == "comp2" || n == "comp3" => {
                let expected=if n=="comp2" { 3 } else { 4 }; if fs.len()!=expected { return Err(format!("{n} expects {} functions", expected-1)); }
                let functions=fs[1..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>,_>>()?; if functions.iter().any(|value| !matches!(value,Value::Function(_))) { return Err(format!("{n} expects functions")); }
                let body=if n=="comp2" { Form::List(vec![Form::Symbol("__f".into()), Form::List(vec![Form::Symbol("__g".into()), Form::Symbol("value".into())])]) } else { Form::List(vec![Form::Symbol("__f".into()), Form::List(vec![Form::Symbol("__g".into()), Form::List(vec![Form::Symbol("__h".into()), Form::Symbol("value".into())])])]) };
                let mut bindings=vec![("__f",functions[0].clone()),("__g",functions[1].clone())]; if n=="comp3" { bindings.push(("__h",functions[2].clone())); }
                Ok(generated_function(vec!["value".into()], vec![body], env.clone(), bindings))
            }
            Form::Symbol(n) if n == "identity" => { if fs.len()!=2 { return Err("identity expects one value".into()); } eval(&fs[1], env) }
            Form::Symbol(n) if n == "apply" => {
                if fs.len() < 3 { return Err("apply expects a function and arguments".into()); }
                let builtin_name = match &fs[1] { Form::Symbol(name) if ["+", "-", "*", "/"].contains(&name.as_str()) => Some(name.as_str()), _ => None };
                let function = if builtin_name.is_none() { Some(eval(&fs[1], env)?) } else { None }; let mut arguments=Vec::new();
                for form in &fs[2..fs.len()-1] { arguments.push(eval(form, env)?); }
                arguments.extend(iterator_values(eval(&fs[fs.len()-1], env)?)?);
                match function {
                    Some(Value::Function(function)) => call_function(&function, arguments),
                    Some(_) => Err("apply expects a function".into()),
                    None => {
                        let name = builtin_name.unwrap();
                        let numbers = arguments.iter().map(|value| match value { Value::Number(value)=>Ok(*value), _=>Err("apply arithmetic expects numbers".into()) }).collect::<Result<Vec<_>,String>>()?;
                        if numbers.is_empty() { return Err("apply expects a function".into()); }
                        let result = match name { "+"=>numbers.iter().sum(), "-"=>numbers[1..].iter().fold(numbers[0], |a,b| a-b), "*"=>numbers.iter().product(), "/"=>numbers[1..].iter().fold(numbers[0], |a,b| a/b), _=>return Err("apply expects a function".into()) };
                        Ok(Value::Number(result))
                    }
                }
            }
            Form::Symbol(n) if n == "key" || n == "val" => {
                if fs.len()!=2 { return Err(format!("{n} expects an entry")); }
                let entry=eval(&fs[1], env)?; match entry { Value::Vector(values) | Value::List(values) if values.len()>=2 => Ok(if n=="key" { values[0].clone() } else { values[1].clone() }), _ => Err(format!("{n} expects a pair")) }
            }
            Form::Symbol(n) if n == "reverse" => {
                if fs.len()!=2 { return Err("reverse expects one collection".into()); }
                let mut values=iterator_values(eval(&fs[1], env)? )?; values.reverse(); Ok(Value::List(values))
            }
            Form::Symbol(n) if n == "contains?" => { if fs.len()!=3 { return Err("contains? expects a collection and entry".into()); } let value=eval(&fs[1], env)?; let entry=eval(&fs[2], env)?; collection_contains(&value, &entry) }
            Form::Symbol(n) if n == "keys" => { if fs.len()!=2 { return Err("keys expects one collection".into()); } collection_keys(&eval(&fs[1], env)?) }
            Form::Symbol(n) if n == "vals" => { if fs.len()!=2 { return Err("vals expects one collection".into()); } collection_vals(&eval(&fs[1], env)?) }
            Form::Symbol(n) if n == "not" => {
                if fs.len() != 2 { return Err("not expects one argument".into()); }
                Ok(Value::Bool(!eval(&fs[1], env)?.truthy()))
            }
            Form::Symbol(n) if ["<", ">", "<=", ">="].contains(&n.as_str()) => comparison(n, &fs[1..], env),
            Form::Symbol(n) if n == "first" => { if fs.len()!=2 { return Err("first expects one argument".into()); } collection_first(eval(&fs[1], env)?) }
            Form::Symbol(n) if n == "second" => { if fs.len()!=2 { return Err("second expects one argument".into()); } collection_second(eval(&fs[1], env)?) }
            Form::Symbol(n) if n == "rest" => { if fs.len()!=2 { return Err("rest expects one argument".into()); } collection_rest(eval(&fs[1], env)?) }
            Form::Symbol(n) if n == "next" => { if fs.len()!=2 { return Err("next expects one argument".into()); } collection_next(eval(&fs[1], env)?) }
            Form::Symbol(n) if n == "last" => { if fs.len()!=2 { return Err("last expects one argument".into()); } collection_last(eval(&fs[1], env)?) }
            Form::Symbol(n) if n == "empty?" => { if fs.len()!=2 { return Err("empty? expects one argument".into()); } collection_empty(eval(&fs[1], env)?) }
            Form::Symbol(n) if n == "not-empty" => { if fs.len()!=2 { return Err("not-empty expects one argument".into()); } let value=eval(&fs[1], env)?; Ok(if collection_empty(value.clone())?.truthy() { Value::Nil } else { value }) }
            Form::Symbol(n) if n == "count" => {
                if fs.len() != 2 { return Err("count expects one argument".into()); }
                collection_count(&eval(&fs[1], env)?)
            }
            Form::Symbol(n) if n == "get" => {
                if fs.len() != 3 && fs.len() != 4 { return Err("get expects 2 or 3 arguments".into()); }
                let value = eval(&fs[1], env)?;
                let key = eval(&fs[2], env)?;
                let default = if fs.len() == 4 { eval(&fs[3], env)? } else { Value::Nil };
                collection_get(&value, &key, default)
            }
            Form::Symbol(n) if n == "nth" => {
                if fs.len() != 3 { return Err("nth expects two arguments".into()); }
                collection_nth(&eval(&fs[1], env)?, &eval(&fs[2], env)?)
            }
            Form::Symbol(n) if n == "assoc" => {
                if fs.len() < 4 || fs.len() % 2 != 0 { return Err("assoc expects a collection and key/value pairs".into()); }
                let mut value=eval(&fs[1], env)?;
                for pair in fs[2..].chunks(2) { let key=eval(&pair[0], env)?; let replacement=eval(&pair[1], env)?; value=collection_assoc(&value, &key, replacement)?; }
                Ok(value)
            }
            Form::Symbol(n) if n == "get-in" => {
                if fs.len()!=3 { return Err("get-in expects a collection and keys".into()); }
                let value=eval(&fs[1], env)?; let keys=iterator_values(eval(&fs[2], env)?)?; collection_get_in(value, &keys)
            }
            Form::Symbol(n) if n == "assoc-in" => {
                if fs.len()!=4 { return Err("assoc-in expects a collection, keys, and value".into()); }
                let value=eval(&fs[1], env)?; let keys=iterator_values(eval(&fs[2], env)?)?; let replacement=eval(&fs[3], env)?; collection_assoc_in(value, &keys, replacement)
            }
            Form::Symbol(n) if n == "update" || n == "update-in" => {
                if (n=="update" && fs.len()<4) || (n=="update-in" && fs.len()<4) { return Err(format!("{n} expects a collection, key path, and function")); }
                let value=eval(&fs[1], env)?; let (keys, function_form, extra_forms)=if n=="update" { (vec![eval(&fs[2], env)?], &fs[3], &fs[4..]) } else { (iterator_values(eval(&fs[2], env)?)?, &fs[3], &fs[4..]) };
                let current=collection_get_in(value.clone(), &keys)?; let function=eval(function_form, env)?; let mut args=vec![current]; args.extend(extra_forms.iter().map(|form| eval(form, env)).collect::<Result<Vec<_>,_>>()?);
                let replacement=match function { Value::Function(function)=>call_function(&function,args)?, _=>return Err(format!("{n} expects a function")) };
                if n=="update" { collection_assoc(&value,&keys[0],replacement) } else { collection_assoc_in(value,&keys,replacement) }
            }
            Form::Symbol(n) if n == "conj" => {
                if fs.len() != 3 { return Err("conj expects two arguments".into()); }
                let collection = eval(&fs[1], env)?; let item = eval(&fs[2], env)?;
                match collection { Value::Vector(mut values) => { values.push(item); Ok(Value::Vector(values)) }, Value::List(mut values) => { values.insert(0, item); Ok(Value::List(values)) }, Value::Map(_values) => { return Err("conj map entries are not implemented".into()) }, _ => Err("conj expects a vector".into()) }
            }
            Form::Symbol(n) if n == "cons" => {
                if fs.len() != 3 { return Err("cons expects two arguments".into()); }
                let item = eval(&fs[1], env)?; let collection = eval(&fs[2], env)?;
                match collection { Value::Vector(mut values) => { values.insert(0, item); Ok(Value::Vector(values)) }, Value::List(mut values) => { values.insert(0, item); Ok(Value::List(values)) }, _ => Err("cons expects a vector".into()) }
            }
            Form::Symbol(n) if n == "recur" => {
                if fs.len() < 2 { return Err("recur expects values".into()); }
                Ok(Value::Recur(fs[1..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?))
            }
            Form::Symbol(n) if n == "loop" => {
                if fs.len()!=3 { return Err("loop expects bindings and a body".into()); }
                let bindings=match &fs[1] { Form::List(values) | Form::Vector(values) => values, _=>return Err("loop expects a binding list or vector".into()) };
                if bindings.len()%2 != 0 { return Err("loop bindings require name/value pairs".into()); }
                let mut names=Vec::new(); let mut previous=Vec::new();
                for pair in bindings.chunks(2) { let name=match &pair[0] { Form::Symbol(name)=>name.clone(), _=>return Err("loop binding name must be a symbol".into()) }; let value=eval(&pair[1], env)?; names.push(name.clone()); previous.push((name.clone(),env.insert(name,value))); }
                let result=loop { match eval(&fs[2], env)? { Value::Recur(values) => { if values.len()!=names.len() { break Err("loop recur arity mismatch".into()); } for (name,value) in names.iter().cloned().zip(values) { env.insert(name,value); } }, result=>break Ok(result) } };
                for (name,old) in previous.into_iter().rev() { if let Some(old)=old { env.insert(name,old); } else { env.remove(&name); } } result
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
                if fs.len() != 3 { return Err("let expects bindings and a body".into()); }
                let bindings = match &fs[1] { Form::List(values) | Form::Vector(values) => values, _ => return Err("let expects a binding list or vector".into()) };
                if bindings.len() % 2 != 0 { return Err("let bindings require name/value pairs".into()); }
                let mut previous = Vec::new();
                for pair in bindings.chunks(2) {
                    let name = match &pair[0] { Form::Symbol(name) => name.clone(), _ => return Err("let binding name must be a symbol".into()) };
                    let value = eval(&pair[1], env)?; previous.push((name.clone(), env.insert(name, value)));
                }
                let result = eval(&fs[2], env);
                for (name, old) in previous.into_iter().rev() { if let Some(old) = old { env.insert(name, old); } else { env.remove(&name); } }
                result
            }
            Form::Symbol(n) if ["+", "-", "*", "/", "%", "mod"].contains(&n.as_str()) => {
                arithmetic(if n == "mod" { "%" } else { n }, &fs[1..], env)
            }
            _ => {
                let function = eval(&fs[0], env)?;
                let arguments = fs[1..].iter().map(|form| eval(form, env)).collect::<Result<Vec<_>, _>>()?;
                match function { Value::Function(function) => call_function(&function, arguments), _ => Err("value is not callable".into()) }
            },
        },
    }
}

pub fn eval_text(source: &str, env: &mut HashMap<String, Value>) -> Result<String, String> {
    let forms = parse_forms(source)?;
    let mut result = Value::Nil;
    for form in forms { result = eval(&form, env)?; if matches!(result, Value::Recur(_)) { return Err("recur must be inside loop".into()); } }
    Ok(result.display())
}

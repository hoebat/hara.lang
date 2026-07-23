mod core;
use std::collections::{HashMap, HashSet};
use wasm_bindgen::prelude::*;

fn ignore_socket_event(_event: core::SocketEvent) {}

#[wasm_bindgen]
pub struct PromiseHandle {
    promise: core::Promise,
}

#[wasm_bindgen]
impl PromiseHandle {
    fn from_promise(promise: core::Promise) -> PromiseHandle { PromiseHandle { promise } }

    #[wasm_bindgen(constructor)]
    pub fn new() -> PromiseHandle { PromiseHandle { promise: core::Promise::new() } }

    pub fn state(&self) -> String {
        match self.promise.state() {
            core::PromiseState::Pending => "pending".into(),
            core::PromiseState::Fulfilled(_) => "fulfilled".into(),
            core::PromiseState::Rejected(_) => "rejected".into(),
        }
    }

    pub fn resolve(&self, value: &str) -> bool { self.promise.resolve(core::Value::String(value.into())) }

    pub fn reject(&self, error: &str) -> bool { self.promise.reject(error) }

    pub fn adopt(&self, other: &PromiseHandle) -> bool { self.promise.adopt(&other.promise) }

    pub fn value(&self) -> Result<String, JsValue> {
        match self.promise.state() {
            core::PromiseState::Pending => Err(JsValue::from_str("promise is pending")),
            core::PromiseState::Fulfilled(value) => Ok(value.display()),
            core::PromiseState::Rejected(error) => Err(JsValue::from_str(&error)),
        }
    }
}

#[wasm_bindgen]
pub struct Runtime {
    env: HashMap<String, core::Value>,
    protocols: core::ProtocolRegistry,
    providers: core::ProviderRegistry,
    resources: HashMap<String, String>,
    loaded_resources: HashSet<String>,
    namespaces: HashMap<String, HashMap<String, core::Value>>,
    current_namespace: String,
    namespace_aliases: HashMap<String, String>,
}

#[wasm_bindgen]
impl Runtime {
    #[wasm_bindgen(constructor)]
    pub fn new() -> Runtime {
        Runtime {
            env: HashMap::new(),
            protocols: core::ProtocolRegistry::new(),
            providers: core::ProviderRegistry::new(),
            resources: HashMap::new(),
            loaded_resources: HashSet::new(),
            namespaces: HashMap::new(),
            current_namespace: "user".into(),
            namespace_aliases: HashMap::new(),
        }
    }

    fn eval_text(&mut self, source: &str) -> Result<String, String> {
        core::eval_text(source, &mut self.env)
    }

    fn save_namespace(&mut self) {
        self.namespaces.insert(self.current_namespace.clone(), self.env.clone());
    }

    /// Creates a namespace without selecting it.
    pub fn create_namespace(&mut self, name: &str) -> bool {
        if self.namespaces.contains_key(name) || name == self.current_namespace { return false; }
        self.namespaces.insert(name.into(), HashMap::new()); true
    }

    /// Selects a namespace, preserving the current namespace environment.
    pub fn use_namespace(&mut self, name: &str) -> bool {
        self.save_namespace();
        let next = self.namespaces.remove(name).unwrap_or_default();
        self.env = next;
        self.current_namespace = name.into();
        true
    }

    pub fn current_namespace(&self) -> String { self.current_namespace.clone() }

    /// Adds an explicit alias for a namespace.
    pub fn alias_namespace(&mut self, alias: &str, target: &str) -> bool {
        if alias.is_empty() || target.is_empty() { return false; }
        self.namespace_aliases.insert(alias.into(), target.into()); true
    }

    pub fn resolve_namespace(&self, name: &str) -> String {
        self.namespace_aliases.get(name).cloned().unwrap_or_else(|| name.into())
    }

    /// Evaluates source after selecting a namespace.
    pub fn eval_in_namespace(&mut self, name: &str, source: &str) -> Result<String, JsValue> {
        let name = self.resolve_namespace(name);
        self.use_namespace(&name);
        self.eval_text(source).map_err(|error| JsValue::from_str(&error))
    }

    pub fn require_resource_in_namespace(&mut self, resource: &str, namespace: &str) -> Result<String, JsValue> {
        let namespace = self.resolve_namespace(namespace);
        self.use_namespace(&namespace);
        self.require_resource(resource)
    }

    pub fn install_memory_file_provider(&mut self, root: &str) {
        self.providers.install_file(core::MemoryFileProvider::new(root));
    }

    #[cfg(not(target_arch = "wasm32"))]
    pub fn install_native_file_provider(&mut self, root: &str) {
        self.providers.install_file(core::NativeFileProvider::new(root));
    }

    #[cfg(not(target_arch = "wasm32"))]
    pub fn install_native_socket_provider(&mut self) {
        self.providers.install_socket(core::NativeSocketProvider::default());
    }

    pub fn install_loopback_socket_provider(&mut self) {
        self.providers.install_socket(core::LoopbackSocketProvider::default());
    }

    pub fn file_resolve(&self, root: &str, path: &str) -> Result<String, JsValue> {
        let provider = self.providers.file().ok_or_else(|| JsValue::from_str("file/unsupported"))?;
        provider.resolve(root, path).map_err(|error| JsValue::from_str(&format!("file/{}", error.code())))
    }

    pub fn file_read(&self, path: &str) -> Result<PromiseHandle, JsValue> {
        let provider = self.providers.file().ok_or_else(|| JsValue::from_str("file/unsupported"))?;
        provider.read(path).map(PromiseHandle::from_promise).map_err(|error| JsValue::from_str(&format!("file/{}", error.code())))
    }

    pub fn file_write(&self, path: &str, bytes: Vec<u8>) -> Result<PromiseHandle, JsValue> {
        let provider = self.providers.file().ok_or_else(|| JsValue::from_str("file/unsupported"))?;
        provider.write(path, bytes).map(PromiseHandle::from_promise).map_err(|error| JsValue::from_str(&format!("file/{}", error.code())))
    }

    /// Registers a host-supplied Hara resource. Resources are source text, not executable host code.
    pub fn register_resource(&mut self, name: &str, source: &str) { self.resources.insert(name.into(), source.into()); }

    /// Evaluates a registered resource in the current lexical namespace.
    pub fn load_resource(&mut self, name: &str) -> Result<String, JsValue> {
        let source = self.resources.get(name).cloned().ok_or_else(|| JsValue::from_str("module/not-found"))?;
        self.eval_text(&source).map_err(|error| JsValue::from_str(&error))
    }

    /// Loads a resource once; subsequent requires return the current loaded marker.
    pub fn require_resource(&mut self, name: &str) -> Result<String, JsValue> {
        if self.loaded_resources.contains(name) { return Ok(":loaded".into()); }
        let result = self.load_resource(name)?;
        self.loaded_resources.insert(name.into());
        Ok(result)
    }

    pub fn file_supported(&self) -> bool { self.providers.capabilities().file }

    pub fn socket_supported(&self) -> bool { self.providers.capabilities().socket }

    /// Opens a callback-based socket and returns its provider-owned handle.
    pub fn socket_connect(&self, host: &str, port: u16) -> Result<u64, JsValue> {
        let provider = self.providers.socket().ok_or_else(|| JsValue::from_str("socket/unsupported"))?;
        provider.connect(host, port, ignore_socket_event).map_err(|error| JsValue::from_str(&format!("socket/{}", error.code())))
    }

    pub fn socket_send(&self, socket: u64, bytes: Vec<u8>) -> Result<usize, JsValue> {
        let provider = self.providers.socket().ok_or_else(|| JsValue::from_str("socket/unsupported"))?;
        provider.send(socket, &bytes).map_err(|error| JsValue::from_str(&format!("socket/{}", error.code())))
    }

    pub fn socket_close(&self, socket: u64) -> Result<(), JsValue> {
        let provider = self.providers.socket().ok_or_else(|| JsValue::from_str("socket/unsupported"))?;
        provider.close(socket).map_err(|error| JsValue::from_str(&format!("socket/{}", error.code())))
    }

    /// Returns whether a protocol method is registered in this runtime context.
    pub fn has_protocol_method(&self, protocol: &str, method: &str) -> bool {
        self.protocols.contains(protocol, method)
    }

    pub fn eval(&mut self, source: &str) -> Result<String, JsValue> {
        self.eval_text(source)
            .map_err(|error| JsValue::from_str(&error))
    }
}

#[wasm_bindgen]
pub fn target_profile() -> String {
    if cfg!(target_os = "wasi") { "wasi".into() }
    else if cfg!(target_arch = "wasm32") { "wasm".into() }
    else { "native".into() }
}

#[wasm_bindgen]
pub fn version() -> String {
    "hara-wasm/0.1 L0 slice".to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ignore_socket_event(_event: core::SocketEvent) {}

    static SOCKET_EVENTS: std::sync::atomic::AtomicUsize = std::sync::atomic::AtomicUsize::new(0);

    fn count_socket_event(_event: core::SocketEvent) { SOCKET_EVENTS.fetch_add(1, std::sync::atomic::Ordering::SeqCst); }

    #[cfg(not(target_arch = "wasm32"))]
    #[test]
    fn native_socket_provider_sends_callbacks_and_bytes() {
        use crate::core::SocketProvider;
        use std::io::Read;
        use std::net::TcpListener;
        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = std::thread::spawn(move || { let (mut stream, _) = listener.accept().unwrap(); let mut bytes = [0u8; 3]; stream.read_exact(&mut bytes).unwrap(); bytes });
        SOCKET_EVENTS.store(0, std::sync::atomic::Ordering::SeqCst);
        let sockets = core::NativeSocketProvider::default();
        let handle = sockets.connect("127.0.0.1", port, count_socket_event).unwrap();
        assert_eq!(sockets.send(handle, &[7, 8, 9]).unwrap(), 3);
        sockets.close(handle).unwrap();
        assert_eq!(server.join().unwrap(), [7, 8, 9]);
        assert_eq!(SOCKET_EVENTS.load(std::sync::atomic::Ordering::SeqCst), 3);
    }

    #[cfg(not(target_arch = "wasm32"))]
    #[test]
    fn native_file_provider_round_trips_bytes() {
        use crate::core::FileProvider;
        let path = std::env::temp_dir().join(format!("hara-wasm-test-{}", std::process::id()));
        std::fs::create_dir_all(&path).unwrap();
        let provider = core::NativeFileProvider::new(&path);
        let resolved = provider.resolve(path.to_str().unwrap(), "data.bin").unwrap();
        assert_eq!(provider.write(&resolved, vec![4, 5, 6]).unwrap().state(), core::PromiseState::Fulfilled(core::Value::Nil));
        assert_eq!(provider.read(&resolved).unwrap().state(), core::PromiseState::Fulfilled(core::Value::Bytes(vec![4, 5, 6])));
        std::fs::remove_file(resolved).unwrap();
        std::fs::remove_dir(path).unwrap();
    }

    #[test]
    fn runtime_routes_file_operations_through_provider_registry() {
        let mut runtime = Runtime::new();
        assert!(!runtime.file_supported());
        runtime.install_memory_file_provider("/sandbox");
        assert!(runtime.file_supported());
        let path = runtime.file_resolve("/sandbox", "data.bin").unwrap();
        assert_eq!(runtime.file_write(&path, vec![1, 2, 3]).unwrap().value().unwrap(), "nil");
        assert_eq!(runtime.file_read(&path).unwrap().value().unwrap(), "#bytes[1 2 3]");
        runtime.install_loopback_socket_provider();
        assert!(runtime.socket_supported());
    }

    #[test]
    fn provider_registry_reports_installed_capabilities() {
        let mut registry = core::ProviderRegistry::new();
        assert_eq!(registry.capabilities(), core::ProviderCapabilities { file: false, socket: false });
        registry.install_file(core::MemoryFileProvider::new("/sandbox"));
        registry.install_socket(core::LoopbackSocketProvider::default());
        assert_eq!(registry.capabilities(), core::ProviderCapabilities { file: true, socket: true });
        assert!(registry.file().is_some());
        assert!(registry.socket().is_some());
    }

    #[test]
    fn runtime_routes_socket_handles_through_callback_provider() {
        let mut runtime = Runtime::new();
        runtime.install_loopback_socket_provider();
        let socket = runtime.socket_connect("localhost", 8080).unwrap();
        assert_eq!(runtime.socket_send(socket, vec![1, 2, 3]).unwrap(), 3);
        runtime.socket_close(socket).unwrap();
    }

    #[test]
    fn loopback_socket_is_callback_based_and_counts_bytes() {
        use crate::core::SocketProvider;
        SOCKET_EVENTS.store(0, std::sync::atomic::Ordering::SeqCst);
        let sockets = core::LoopbackSocketProvider::default();
        let handle = sockets.connect("localhost", 8080, count_socket_event).unwrap();
        assert_eq!(sockets.send(handle, &[1, 2, 3]).unwrap(), 3);
        sockets.close(handle).unwrap();
        assert_eq!(SOCKET_EVENTS.load(std::sync::atomic::Ordering::SeqCst), 3);
        assert_eq!(sockets.send(handle, &[9]).unwrap_err(), core::SocketError::Invalid("unknown socket".into()));
    }

    #[test]
    fn memory_file_provider_enforces_root_and_preserves_bytes() {
        use crate::core::FileProvider;
        let files = core::MemoryFileProvider::new("/sandbox");
        assert_eq!(files.resolve("/sandbox", "docs/../secret").unwrap_err(), core::FileError::Denied);
        let path = files.resolve("/sandbox", "data.bin").unwrap();
        let write = files.write(&path, vec![0, 127, 255]).unwrap();
        assert_eq!(write.state(), core::PromiseState::Fulfilled(core::Value::Nil));
        let read = files.read(&path).unwrap();
        assert_eq!(read.state(), core::PromiseState::Fulfilled(core::Value::Bytes(vec![0, 127, 255])));
        assert_eq!(files.read("/outside/data.bin").unwrap_err(), core::FileError::Denied);
    }

    #[test]
    fn unsupported_capabilities_fail_stably() {
        use crate::core::{FileProvider, SocketProvider};
        let files = core::UnsupportedFileProvider;
        assert_eq!(files.resolve("/root", "data.bin").unwrap_err(), core::FileError::Unsupported);
        assert_eq!(files.read("data.bin").unwrap_err(), core::FileError::Unsupported);
        let sockets = core::UnsupportedSocketProvider;
        assert_eq!(sockets.connect("localhost", 80, ignore_socket_event).unwrap_err(), core::SocketError::Unsupported);
        assert_eq!(sockets.send(1, &[1, 2]).unwrap_err(), core::SocketError::Unsupported);
        assert_eq!(sockets.close(1).unwrap_err(), core::SocketError::Unsupported);
    }

    #[test]
    fn namespace_aliases_route_evaluation_and_resources() {
        let mut runtime = Runtime::new();
        assert!(runtime.create_namespace("hara.math"));
        assert!(runtime.alias_namespace("math", "hara.math"));
        assert_eq!(runtime.resolve_namespace("math"), "hara.math");
        assert_eq!(runtime.eval_in_namespace("math", "(defn answer [] 42) (answer)").unwrap(), "42");
        runtime.register_resource("helpers", "(defn helper [] 7) (helper)");
        assert_eq!(runtime.require_resource_in_namespace("helpers", "math").unwrap(), "7");
        assert_eq!(runtime.eval_text("(helper)").unwrap(), "7");
    }

    #[test]
    fn namespaces_isolate_bindings_and_can_be_selected() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.current_namespace(), "user");
        assert!(runtime.create_namespace("math"));
        runtime.eval_text("(defn answer [] 42)").unwrap();
        runtime.use_namespace("math");
        assert_eq!(runtime.eval_text("(defn answer [] 7) (answer)").unwrap(), "7");
        runtime.use_namespace("user");
        assert_eq!(runtime.eval_text("(answer)").unwrap(), "42");
        runtime.use_namespace("math");
        assert_eq!(runtime.eval_text("(answer)").unwrap(), "7");
    }

    #[test]
    fn resource_sources_accept_namespace_declarations() {
        let mut runtime = Runtime::new();
        runtime.register_resource("module", "(ns demo (:require [core])) (defn answer [] 42) (answer)");
        assert_eq!(runtime.load_resource("module").unwrap(), "42");
    }

    #[test]
    fn registered_resources_load_into_the_runtime_environment() {
        let mut runtime = Runtime::new();
        runtime.register_resource("demo", "(defn answer [] 42) (answer)");
        assert_eq!(runtime.load_resource("demo").unwrap(), "42");
        assert_eq!(runtime.eval_text("(answer)").unwrap(), "42");
        assert_eq!(runtime.require_resource("demo").unwrap(), "42");
        assert_eq!(runtime.require_resource("demo").unwrap(), ":loaded");
    }

    #[test]
    fn vector_literals_are_values() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("[1 2 3]").unwrap(), "[1 2 3]");
    }

    #[test]
    fn strings_and_maps_are_values() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("\"hello\"").unwrap(), "\"hello\"");
        assert_eq!(runtime.eval_text("{\"a\" 1}").unwrap(), "{\"a\" 1}");
    }

    #[test]
    fn application_and_pair_helpers_support_bootstrap_code() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(identity 42)").unwrap(), "42");
        assert_eq!(runtime.eval_text("(apply + [19 23])").unwrap(), "42");
        assert_eq!(runtime.eval_text("(apply + 19 [23])").unwrap(), "42");
        assert_eq!(runtime.eval_text("(key [1 2])").unwrap(), "1");
        assert_eq!(runtime.eval_text("(val [1 2])").unwrap(), "2");
        assert_eq!(runtime.eval_text("(reverse [1 2 3])").unwrap(), "(3 2 1)");
    }

    #[test]
    fn map_membership_keys_and_values_are_portable() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text(r#"(contains? {"a" 1} "a")"#).unwrap(), "true");
        assert_eq!(runtime.eval_text("(contains? [1 2] 2)").unwrap(), "true");
        assert_eq!(runtime.eval_text(r#"(keys {"a" 1 "b" 2})"#).unwrap(), r#"["a" "b"]"#);
        assert_eq!(runtime.eval_text(r#"(vals {"a" 1 "b" 2})"#).unwrap(), "[1 2]");
    }

    #[test]
    fn core_collection_navigation_and_predicates_are_host_neutral() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(first [1 2 3])").unwrap(), "1");
        assert_eq!(runtime.eval_text("(rest [1 2 3])").unwrap(), "(2 3)");
        assert_eq!(runtime.eval_text("(last [1 2 3])").unwrap(), "3");
        assert_eq!(runtime.eval_text("(empty? [])").unwrap(), "true");
        assert_eq!(runtime.eval_text("(not false)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(< 1 2 3)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(>= 3 3)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(mod 7 3)").unwrap(), "1");
    }

    #[test]
    fn collection_operations_are_values() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(count [1 2 3])").unwrap(), "3");
        assert_eq!(runtime.eval_text("(get {\"a\" 9} \"a\")").unwrap(), "9");
        assert_eq!(runtime.eval_text("(nth (conj [1] 2) 1)").unwrap(), "2");
        assert_eq!(runtime.eval_text("(cons 0 [1 2])").unwrap(), "[0 1 2]");
        assert_eq!(runtime.eval_text("(= :ready :ready)").unwrap(), "true");
    }

    fn protocol_identity(arguments: &[core::Value]) -> Result<core::Value, String> {
        arguments.first().cloned().ok_or_else(|| "missing receiver".into())
    }

    #[test]
    fn promise_values_are_composable_and_settle_once() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(promise/state (promise))").unwrap(), ":pending");
        assert_eq!(runtime.eval_text("(let (p (promise)) (do (promise/resolve p 42) (promise/value p)))").unwrap(), "42");
        assert_eq!(runtime.eval_text(r#"(let (p (promise)) (do (promise/reject p "boom") (promise/state p)))"#).unwrap(), ":rejected");
        assert_eq!(runtime.eval_text("(let (p (promise)) (let (q (promise)) (do (promise/resolve q 7) (promise/adopt p q) (promise/value p))))").unwrap(), "7");
        assert_eq!(runtime.eval_text("(let (p (promise)) (do (promise/resolve p 1) (promise/resolve p 2)))").unwrap_err(), "promise is already settled");
    }

    #[test]
    fn promises_support_map_recover_and_finally() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(promise/state (promise/map (promise/resolve (promise) 41) (fn [x] (+ x 1))))").unwrap(), ":fulfilled");
        assert_eq!(runtime.eval_text("(promise/value (promise/recover (promise/reject (promise) :bad) (fn [x] (str x :ok))))").unwrap(), "\":bad:ok\"");
        assert_eq!(runtime.eval_text("(promise/value (promise/finally (promise/resolve (promise) 42) (fn [] 0)))").unwrap(), "42");
    }

    #[test]
    fn promises_settle_once_and_adopt() {
        let pending = core::Promise::new();
        let adopted = core::Promise::new();
        assert_eq!(pending.state(), core::PromiseState::Pending);
        assert!(!adopted.adopt(&pending));
        assert!(pending.resolve(core::Value::Number(7)));
        assert!(!pending.reject("late"));
        assert!(adopted.adopt(&pending));
        assert_eq!(adopted.state(), core::PromiseState::Fulfilled(core::Value::Number(7)));
    }

    #[test]
    fn marker_mutation_methods_cover_array_and_object_boundaries() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(let (a (array 2)) (do (. a (push-first 1)) (. a (push-last 3)) (. a (insert 1 9)) (. a (get 1))))").unwrap(), "9");
        assert_eq!(runtime.eval_text("(let (a (array 1 2)) (do (. a (pop-first)) (. a (pop-last)) (count a)))").unwrap(), "0");
        assert_eq!(runtime.eval_text(r#"(. (object "a" 1 "b" 2) (keys))"#).unwrap(), r#"["a" "b"]"#);
        assert_eq!(runtime.eval_text(r#"(. (object "a" 1 "b" 2) (vals))"#).unwrap(), "[1 2]");
        assert_eq!(runtime.eval_text(r#"(let (o (object "a" 1)) (do (. o (assign (object "b" 2))) (. o (get "b"))))"#).unwrap(), "2");
    }

    #[test]
    fn marker_arrays_and_objects_use_restricted_dot_calls() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(count (array 1 2 3))").unwrap(), "3");
        assert_eq!(runtime.eval_text("(. (array 1 2) (get 1))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(let (a (array 1 2)) (do (. a (set 1 7)) (. a (get 1))))").unwrap(), "7");
        assert_eq!(runtime.eval_text("(let (a (array 1)) (do (. a (push-last 2)) (count a)))").unwrap(), "2");
        assert_eq!(runtime.eval_text(r#"(. (object "answer" 41) (get "answer"))"#).unwrap(), "41");
        assert_eq!(runtime.eval_text(r#"(let (o (object)) (do (. o (set "answer" 42)) (. o (get "answer"))))"#).unwrap(), "42");
        assert_eq!(runtime.eval_text(r#"(. (object "answer" 41) (has? "answer"))"#).unwrap(), "true");
    }

    #[test]
    fn strings_and_bytes_support_utf8_copy_and_slice() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text(r#"(str "hello" " " "world")"#).unwrap(), "\"hello world\"" );
        assert_eq!(runtime.eval_text(r#"(str/count "hé")"#).unwrap(), "2");
        assert_eq!(runtime.eval_text(r#"(str/trim "  hara  ")"#).unwrap(), "\"hara\"");
        assert_eq!(runtime.eval_text(r#"(str/decode (str/encode "hé"))"#).unwrap(), "\"hé\"");
        assert_eq!(runtime.eval_text("(bytes/slice (bytes 1 2 3) 1 3)").unwrap(), "(bytes 2 3)");
        assert_eq!(runtime.eval_text("(let (source (bytes 1 2)) (let (copy (bytes/copy source)) (do (bytes/set copy 0 9) (bytes/get source 0))))").unwrap(), "1");
    }

    #[test]
    fn byte_buffers_preserve_signed_storage_and_unsigned_reads() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(bytes 1 2 -3)").unwrap(), "(bytes 1 2 -3)");
        assert_eq!(runtime.eval_text("(bytes/get (bytes 1 2 -3) 2)").unwrap(), "253");
        assert_eq!(runtime.eval_text("(bytes/u8 -1)").unwrap(), "255");
        assert_eq!(runtime.eval_text("(bytes/s8 255)").unwrap(), "-1");
        assert_eq!(runtime.eval_text("(let (b (bytes 1 2)) (do (bytes/set b 0 9) (bytes/get b 0)))").unwrap(), "9");
        assert_eq!(runtime.eval_text("(bytes/get (bytes 1) 4 7)").unwrap(), "7");
        assert_eq!(runtime.eval_text("(bytes/count (bytes 1 2 -3))").unwrap(), "3");
    }

    #[test]
    fn iterator_aliases_and_combinators_match_core_shapes() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(iter-next (map (fn [x] (* x 2)) [1 2]))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(iter-next (filter (fn [x] (= x 2)) [1 2 3]))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(iter-next (take 1 (drop 1 [1 2 3])))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(nth (iter-next (zip [1] [2])) 1)").unwrap(), "2");
        assert_eq!(runtime.eval_text("(let (it (cycle [1 2])) (do (iter-next it) (iter-next it) (iter-next it)))").unwrap(), "1");
        assert_eq!(runtime.eval_text("(iter-next (concat [1] [2]))").unwrap(), "1");
    }

    #[test]
    fn iterators_are_closeable_and_support_map_filter() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(let (it (iter [1 2])) (iter-next it))").unwrap(), "1");
        assert_eq!(runtime.eval_text("(let (it (iter [1 2])) (do (iter-next it) (iter-next it)))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(iter-has? (iter [1]))").unwrap(), "true");
        assert_eq!(runtime.eval_text("(let (it (iter [1])) (do (iter-close it) (iter-has? it)))").unwrap(), "false");
        assert_eq!(runtime.eval_text("(iter-next (iter-map (fn [x] (* x 2)) [1 2]))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(iter-next (iter-filter (fn [x] (= x 2)) [1 2 3]))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(receiver-category (iter [1]))").unwrap_err(), "unbound symbol: receiver-category");
    }

    #[test]
    fn evaluator_protocol_calls_cover_collections_and_bytes() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(protocol-call ICount count [1 2 3])").unwrap(), "3");
        assert_eq!(runtime.eval_text("(protocol-call INth nth (bytes 1 -3) 1)").unwrap(), "-3");
        assert_eq!(runtime.eval_text(r#"(protocol-call ILookup lookup {"a" 9} "a")"#).unwrap(), "9");
        assert!(runtime.eval_text("(protocol-call Missing nope 1)").unwrap_err().contains("missing protocol method"));
    }

    #[test]
    fn protocol_registry_dispatches_by_protocol_and_method() {
        let mut registry = core::ProtocolRegistry::new();
        registry.register("IIdentity", "identity", protocol_identity);
        assert!(registry.contains("IIdentity", "identity"));
        assert_eq!(registry.invoke("IIdentity", "identity", &[core::Value::Number(7)]).unwrap(), core::Value::Number(7));
        assert!(registry.invoke("IIdentity", "missing", &[]).unwrap_err().contains("missing protocol method"));
        assert_eq!(core::receiver_category(&core::Value::Vector(vec![])), "vector");
    }

    #[test]
    fn functions_support_variadic_rest_parameters() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("((fn [x & rest] (+ x (count rest))) 40 1 2)").unwrap(), "42");
        assert_eq!(runtime.eval_text("(do (defn collect [x & rest] (count rest)) (collect 1 2 3 4))").unwrap(), "3");
        assert!(runtime.eval_text("((fn [x & rest] x))").unwrap_err().contains("at least 1"));
    }

    #[test]
    fn throw_and_try_catch_finally_are_host_neutral() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(try (throw :failed) (catch error error))").unwrap(), "\"thrown: :failed\"");
        assert_eq!(runtime.eval_text("(try 42 (finally 0))").unwrap(), "42");
        assert_eq!(runtime.eval_text("(try (throw :failed) (catch error (str error :handled)))").unwrap(), "\"thrown: :failed:handled\"");
        assert!(runtime.eval_text("(throw :failed)").unwrap_err().contains("thrown: :failed"));
    }

    #[test]
    fn def_binds_values_in_the_current_environment() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(do (def answer 41) (+ answer 1))").unwrap(), "42");
        assert_eq!(runtime.eval_text("(do (def answer 42) (deref (var answer)))").unwrap(), "42");
        assert!(runtime.eval_text("(deref 42)").unwrap_err().contains("deref expects a var"));
        assert_eq!(runtime.eval_text("(do (def answer 1) (def answer 42) answer)").unwrap(), "42");
        assert!(runtime.eval_text("(def 1 2)").unwrap_err().contains("def name must be a symbol"));
    }

    #[test]
    fn functions_capture_lexical_values_and_support_defn() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("((fn [x] (+ x 1)) 41)").unwrap(), "42");
        assert_eq!(runtime.eval_text("(let (inc (fn [x] (+ x 1))) (inc 41))").unwrap(), "42");
        assert_eq!(runtime.eval_text("(do (defn add1 [x] (+ x 1)) (add1 41))").unwrap(), "42");
        assert_eq!(runtime.eval_text("(do (defn factorial [n] (if (<= n 1) 1 (* n (factorial (dec n))))) (factorial 5))").unwrap(), "120");
        assert_eq!(runtime.eval_text("(let (x 40) (let (f (fn [y] (+ x y))) (f 2)))").unwrap(), "42");
    }

    #[test]
    fn quote_lists_and_do_match_core_shapes() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("'(1 2)").unwrap(), "(1 2)");
        assert_eq!(runtime.eval_text("(count '(1 2 3))").unwrap(), "3");
        assert_eq!(runtime.eval_text("(nth (cons 0 '(1 2)) 0)").unwrap(), "0");
        assert_eq!(runtime.eval_text("(do 1 2 3)").unwrap(), "3");
    }

    #[test]
    fn signed_32_bit_operations_match_core_contract() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(bit-and 6 3)").unwrap(), "2");
        assert_eq!(runtime.eval_text("(bit-or 1 2)").unwrap(), "3");
        assert_eq!(runtime.eval_text("(bit-xor 7 3)").unwrap(), "4");
        assert_eq!(runtime.eval_text("(bit-not 0)").unwrap(), "-1");
        assert_eq!(runtime.eval_text("(bit-shift-right -4 1)").unwrap(), "-2");
        assert_eq!(runtime.eval_text("(bit-shift-left 1 31)").unwrap(), "-2147483648");
        assert!(runtime.eval_text("(bit-shift-left 1 -1)").unwrap_err().contains("distance must be in the range 0..31"));
    }

    #[test]
    fn l0_numeric_and_truth_predicates_are_available() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(inc 41)").unwrap(), "42");
        assert_eq!(runtime.eval_text("(dec 43)").unwrap(), "42");
        assert_eq!(runtime.eval_text("(zero? 0)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(pos? 1)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(neg? -1)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(even? 4)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(odd? 3)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(nil? nil)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(some? 1)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(true? true)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(false? false)").unwrap(), "true");
    }

    #[test]
    fn core_sequence_navigation_ranges_and_quantifiers() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(second [10 20 30])").unwrap(), "20");
        assert_eq!(runtime.eval_text("(next [10 20 30])").unwrap(), "(20 30)");
        assert_eq!(runtime.eval_text("(not-empty [])").unwrap(), "nil");
        assert_eq!(runtime.eval_text("(not-empty [1])").unwrap(), "[1]");
        assert_eq!(runtime.eval_text("(range 3)").unwrap(), "<iterator>");
        assert_eq!(runtime.eval_text("(count (range 2 5))").unwrap(), "3");
        assert_eq!(runtime.eval_text("(count (repeat 4 :x))").unwrap(), "4");
        assert_eq!(runtime.eval_text("(every? (fn [x] (pos? x)) [1 2 3])").unwrap(), "true");
        assert_eq!(runtime.eval_text("(any? (fn [x] (= x 2)) [1 2 3])").unwrap(), "true");
        assert_eq!(runtime.eval_text("(some (fn [x] (if (= x 2) :yes nil)) [1 2 3])").unwrap(), ":yes");
    }

    #[test]
    fn map_and_zip_support_multiple_collections() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(nth (map (fn [x y] (+ x y)) [1 2] [10 20]) 1)").unwrap(), "22");
        assert_eq!(runtime.eval_text("(count (map (fn [x y z] (+ x (+ y z))) [1 2] [10 20] [100 200]))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(nth (zip [1 2] [:a :b] [true false]) 0)").unwrap(), "[1 :a true]");
        assert_eq!(runtime.eval_text("(count (zip [1 2 3] [:a :b]))").unwrap(), "2");
    }

    #[test]
    fn lazy_iterator_generators_are_bounded_by_consumers() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(count (take 4 (repeat :x)))").unwrap(), "4");
        assert_eq!(runtime.eval_text("(first (drop 3 (repeat :x)))").unwrap(), ":x");
        assert!(runtime.eval_text("(count (repeat :x))").unwrap_err().contains("finite collection"));
        assert_eq!(runtime.eval_text("(count (take 3 (repeatedly (constantly 7))))").unwrap(), "3");
        assert_eq!(runtime.eval_text("(count (take 5 (iterate (fn [x] (+ x 2)) 0)))").unwrap(), "5");
        assert_eq!(runtime.eval_text("(count (take 3 (take-while (fn [x] (< x 10)) (iterate (fn [x] (+ x 2)) 0))))").unwrap(), "3");
        assert_eq!(runtime.eval_text("(first (take 2 (drop-while (fn [x] (< x 4)) (iterate (fn [x] (+ x 2)) 0))))").unwrap(), "4");
        assert_eq!(runtime.eval_text("(nth (take 4 (map (fn [x] (* x 2)) (iterate (fn [x] (+ x 1)) 0))) 3)").unwrap(), "6");
        assert_eq!(runtime.eval_text("(first (take 2 (filter (fn [x] (even? x)) (iterate (fn [x] (+ x 1)) 0))))").unwrap(), "0");
        assert_eq!(runtime.eval_text("(nth (take 4 (mapcat (fn [x] [x x]) (iterate (fn [x] (+ x 1)) 0))) 3)").unwrap(), "1");
        assert_eq!(runtime.eval_text("(first (take 2 (keep (fn [x] (if (even? x) (* x 10) nil)) (iterate (fn [x] (+ x 1)) 0))))").unwrap(), "0");
        assert_eq!(runtime.eval_text("(nth (take 3 (zip (iterate (fn [x] (+ x 1)) 0) (repeat :x))) 2)").unwrap(), "[2 :x]");
        assert_eq!(runtime.eval_text("(nth (take 4 (interleave (iterate (fn [x] (+ x 1)) 0) (repeat :x))) 3)").unwrap(), ":x");
        assert_eq!(runtime.eval_text("(nth (take 3 (partition-all 2 (iterate (fn [x] (+ x 1)) 0))) 2)").unwrap(), "[4 5]");
        assert_eq!(runtime.eval_text("(nth (take 2 (partition 2 (iterate (fn [x] (+ x 1)) 0))) 1)").unwrap(), "[2 3]");
        assert_eq!(runtime.eval_text("(first (take 4 (iterate (fn [x] (+ x 2)) 0)))").unwrap(), "0");
        assert_eq!(runtime.eval_text("(second (repeat :x))").unwrap(), ":x");
        assert_eq!(runtime.eval_text("(first (rest (iterate (fn [x] (+ x 1)) 0)))").unwrap(), "1");
        assert_eq!(runtime.eval_text("(nth (take 4 (iterate (fn [x] (+ x 2)) 0)) 3)").unwrap(), "6");
    }

    #[test]
    fn function_combinators_capture_values_and_functions() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("((constantly 42) 1 2 3)").unwrap(), "42");
        assert_eq!(runtime.eval_text("((complement (fn [x] (> x 2))) 1)").unwrap(), "true");
        assert_eq!(runtime.eval_text("((comp2 (fn [x] (+ x 1)) (fn [x] (* x 2))) 20)").unwrap(), "41");
        assert_eq!(runtime.eval_text("((comp3 (fn [x] (+ x 1)) (fn [x] (+ x 1)) (fn [x] (+ x 1))) 39)").unwrap(), "42");
    }

    #[test]
    fn nested_associative_helpers_match_l0_shapes() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(get-in {:a {:b 42}} [:a :b])").unwrap(), "42");
        assert_eq!(runtime.eval_text("(get-in (object :a (object :b 42)) [:a :b])").unwrap(), "42");
        assert_eq!(runtime.eval_text("(get (object :a 7) :a)").unwrap(), "7");
        assert_eq!(runtime.eval_text("(get-in {:a {:b 42}} [:a :missing])").unwrap(), "nil");
        assert_eq!(runtime.eval_text("(get-in (assoc-in {} [:a :b] 42) [:a :b])").unwrap(), "42");
        assert_eq!(runtime.eval_text("(get {:a 3} :a)").unwrap(), "3");
        assert_eq!(runtime.eval_text("(get (update {:a 3} :a (fn [x] (+ x 2))) :a)").unwrap(), "5");
        assert_eq!(runtime.eval_text("(get-in (update-in {:a {:b 3}} [:a :b] (fn [x y] (+ x y)) 4) [:a :b])").unwrap(), "7");
        assert_eq!(runtime.eval_text("(get (assoc {} :a 1 :b 2) :b)").unwrap(), "2");
    }

    #[test]
    fn iterator_combinators_cover_core_shapes() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(count (take-while (fn [x] (< x 3)) (range 5)))").unwrap(), "3");
        assert_eq!(runtime.eval_text("(count (drop-while (fn [x] (< x 3)) (range 5)))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(count (mapcat (fn [x] [x x]) [1 2]))").unwrap(), "4");
        assert_eq!(runtime.eval_text("(count (keep (fn [x] (if (even? x) (* x 10) nil)) [1 2 3 4]))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(count (partition-all 2 [1 2 3]))").unwrap(), "2");
        assert_eq!(runtime.eval_text("(count (partition 2 [1 2 3]))").unwrap(), "1");
        assert_eq!(runtime.eval_text("(count (interpose :x [1 2 3]))").unwrap(), "5");
        assert_eq!(runtime.eval_text("(count (interleave [1 2] [:a :b]))").unwrap(), "4");
        assert_eq!(runtime.eval_text("(count (partition-pair [1 2 3]))").unwrap(), "1");
    }

    #[test]
    fn arithmetic() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(+ 19 23)").unwrap(), "42");
    }

    #[test]
    fn recur_cannot_escape_loop_or_function_boundaries() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(recur 1)").unwrap_err(), "recur must be inside loop");
        assert_eq!(runtime.eval_text("((fn [] (recur 1)))").unwrap_err(), "recur must be inside loop");
    }

    #[test]
    fn loop_supports_binding_vectors_and_multiple_recur_values() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(loop [x 0 y 1] (if (< x 4) (recur (+ x 1) (+ y x)) y))").unwrap(), "7");
        assert!(runtime.eval_text("(loop [x 0 y 1] (recur 2))").unwrap_err().contains("loop recur arity mismatch"));
    }

    #[test]
    fn loop_and_recur_support_tail_recursive_bootstrap_forms() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(loop (x 0) (if (< x 5) (recur (+ x 1)) x))").unwrap(), "5");
        assert_eq!(runtime.eval_text("(loop (x 1) (do (if (< x 3) (recur (* x 2)) x)))").unwrap(), "4");
    }

    #[test]
    fn let_accepts_binding_vectors_and_multiple_sequential_pairs() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(let [x 19 y 23] (+ x y))").unwrap(), "42");
        assert_eq!(runtime.eval_text("(let (x 19 y (+ x 23)) y)").unwrap(), "42");
        assert!(runtime.eval_text("(let [x 1 y] y)").unwrap_err().contains("name/value pairs"));
    }

    #[test]
    fn conditional_and_let() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(let (x 19) (if true (+ x 23) 0))")
                .unwrap(),
            "42"
        );
    }

    #[test]
    fn errors_are_stable() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("unknown").unwrap_err(),
            "unbound symbol: unknown"
        );
    }
}

#![allow(clippy::too_many_lines)] // Temporary compatibility facade during Java-port split.
mod core;
pub mod kernel;
pub mod lang;
pub mod task;
use crate::kernel::Form;
use std::collections::{HashMap, HashSet};
use wasm_bindgen::prelude::*;

fn ignore_socket_event(_event: core::SocketEvent) {}

#[wasm_bindgen]
pub struct PromiseHandle {
    promise: core::Promise,
}

#[wasm_bindgen]
impl PromiseHandle {
    fn from_promise(promise: core::Promise) -> PromiseHandle {
        PromiseHandle { promise }
    }

    #[wasm_bindgen(constructor)]
    pub fn new() -> PromiseHandle {
        PromiseHandle {
            promise: core::Promise::new(),
        }
    }

    pub fn state(&self) -> String {
        match self.promise.state() {
            core::PromiseState::Pending => "pending".into(),
            core::PromiseState::Fulfilled(_) => "fulfilled".into(),
            core::PromiseState::Rejected(_) => "rejected".into(),
        }
    }

    pub fn resolve(&self, value: &str) -> bool {
        self.promise.resolve(core::Value::String(value.into()))
    }

    pub fn reject(&self, error: &str) -> bool {
        self.promise.reject(error)
    }

    pub fn adopt(&self, other: &PromiseHandle) -> bool {
        self.promise.adopt(&other.promise)
    }

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
    extensions: core::ExtensionRegistry,
    providers: core::ProviderRegistry,
    resources: HashMap<String, String>,
    loaded_resources: HashSet<String>,
    namespaces: HashMap<String, HashMap<String, core::Value>>,
    current_namespace: String,
    namespace_aliases: HashMap<String, String>,
    generated_configs: HashMap<String, kernel::GeneratedNamespaceConfig>,
}

#[wasm_bindgen]
impl Runtime {
    #[wasm_bindgen(constructor)]
    pub fn new() -> Runtime {
        Runtime {
            env: HashMap::new(),
            protocols: core::ProtocolRegistry::core(),
            extensions: core::ExtensionRegistry::new(),
            providers: core::ProviderRegistry::new(),
            resources: HashMap::new(),
            loaded_resources: HashSet::new(),
            namespaces: HashMap::new(),
            current_namespace: "user".into(),
            namespace_aliases: HashMap::new(),
            generated_configs: HashMap::from([(
                "user".into(),
                kernel::GeneratedNamespaceConfig::defaults(),
            )]),
        }
    }

    fn eval_text(&mut self, source: &str) -> Result<String, String> {
        self.refresh_qualified_bindings();
        let forms = kernel::parse_forms(source)?;
        let mut result = core::Value::Nil;
        for form in forms {
            if let Form::List(values) = &form {
                if matches!(values.first(), Some(Form::Symbol(name)) if name == "ns") {
                    let name = match values.get(1) {
                        Some(Form::Symbol(name)) if !name.contains('/') => name.clone(),
                        _ => return Err("ns expects an unqualified namespace symbol".into()),
                    };
                    let config = kernel::GeneratedNamespaceConfig::configure(&values[2..])?;
                    self.use_namespace(&name);
                    self.generated_configs.insert(name, config);
                    result = core::Value::Nil;
                    continue;
                }
            }
            let config = self
                .generated_configs
                .get(&self.current_namespace)
                .cloned()
                .unwrap_or_else(kernel::GeneratedNamespaceConfig::defaults);
            let resolved = config.rewrite(form);
            result = core::with_promise_provider(self.providers.promise(), || {
                core::with_protocols(&self.protocols, || core::eval(&resolved, &mut self.env))
            })?;
            if matches!(result, core::Value::Recur(_)) {
                return Err("recur must be inside loop".into());
            }
        }
        self.refresh_qualified_bindings();
        Ok(result.display())
    }

    fn refresh_qualified_bindings(&mut self) {
        let qualified = self
            .env
            .keys()
            .filter(|name| name.contains('/'))
            .cloned()
            .collect::<Vec<_>>();
        for name in qualified {
            self.env.remove(&name);
        }
        let mut bindings = Vec::new();
        for (namespace, values) in &self.namespaces {
            for (name, value) in values {
                if !name.contains('/') {
                    bindings.push((format!("{namespace}/{name}"), value.clone()));
                }
            }
        }
        for (name, value) in &self.env {
            if !name.contains('/') {
                bindings.push((
                    format!("{}/{}", self.current_namespace, name),
                    value.clone(),
                ));
            }
        }
        for (qualified, value) in bindings {
            self.env.insert(qualified, value);
        }
        let aliases = self.namespace_aliases.clone();
        for (alias, target) in aliases {
            let prefix = format!("{target}/");
            let aliased = self
                .env
                .iter()
                .filter_map(|(name, value)| {
                    name.strip_prefix(&prefix)
                        .map(|suffix| (format!("{alias}/{suffix}"), value.clone()))
                })
                .collect::<Vec<_>>();
            for (name, value) in aliased {
                self.env.insert(name, value);
            }
        }
    }

    fn save_namespace(&mut self) {
        let values = self
            .env
            .iter()
            .filter(|(name, _)| !name.contains('/'))
            .map(|(name, value)| (name.clone(), value.clone()))
            .collect();
        self.namespaces
            .insert(self.current_namespace.clone(), values);
    }

    /// Creates a namespace without selecting it.
    pub fn create_namespace(&mut self, name: &str) -> bool {
        if self.namespaces.contains_key(name) || name == self.current_namespace {
            return false;
        }
        self.namespaces.insert(name.into(), HashMap::new());
        true
    }

    /// Selects a namespace, preserving the current namespace environment.
    pub fn use_namespace(&mut self, name: &str) -> bool {
        self.save_namespace();
        let next = self.namespaces.remove(name).unwrap_or_default();
        self.env = next;
        self.current_namespace = name.into();
        self.refresh_qualified_bindings();
        true
    }

    pub fn current_namespace(&self) -> String {
        self.current_namespace.clone()
    }

    /// Adds an explicit alias for a namespace.
    pub fn alias_namespace(&mut self, alias: &str, target: &str) -> bool {
        if alias.is_empty() || target.is_empty() {
            return false;
        }
        self.namespace_aliases.insert(alias.into(), target.into());
        true
    }

    pub fn resolve_namespace(&self, name: &str) -> String {
        self.namespace_aliases
            .get(name)
            .cloned()
            .unwrap_or_else(|| name.into())
    }

    /// Evaluates source after selecting a namespace.
    pub fn eval_in_namespace(&mut self, name: &str, source: &str) -> Result<String, JsValue> {
        let name = self.resolve_namespace(name);
        self.use_namespace(&name);
        self.eval_text(source)
            .map_err(|error| JsValue::from_str(&error))
    }

    pub fn require_resource_in_namespace(
        &mut self,
        resource: &str,
        namespace: &str,
    ) -> Result<String, JsValue> {
        let namespace = self.resolve_namespace(namespace);
        self.use_namespace(&namespace);
        self.require_resource(resource)
    }

    pub fn install_memory_file_provider(&mut self, root: &str) {
        self.providers
            .install_file(core::MemoryFileProvider::new(root));
    }

    #[cfg(not(target_arch = "wasm32"))]
    pub fn install_native_file_provider(&mut self, root: &str) {
        self.providers
            .install_file(core::NativeFileProvider::new(root));
    }

    #[cfg(not(target_arch = "wasm32"))]
    pub fn install_native_socket_provider(&mut self) {
        self.providers
            .install_socket(core::NativeSocketProvider::default());
    }

    pub fn install_loopback_socket_provider(&mut self) {
        self.providers
            .install_socket(core::LoopbackSocketProvider::default());
    }

    pub fn file_resolve(&self, root: &str, path: &str) -> Result<String, JsValue> {
        let provider = self
            .providers
            .file()
            .ok_or_else(|| JsValue::from_str("file/unsupported"))?;
        provider
            .resolve(root, path)
            .map_err(|error| JsValue::from_str(&format!("file/{}", error.code())))
    }

    pub fn file_read(&self, path: &str) -> Result<PromiseHandle, JsValue> {
        let provider = self
            .providers
            .file()
            .ok_or_else(|| JsValue::from_str("file/unsupported"))?;
        provider
            .read(path)
            .map(PromiseHandle::from_promise)
            .map_err(|error| JsValue::from_str(&format!("file/{}", error.code())))
    }

    pub fn file_write(&self, path: &str, bytes: Vec<u8>) -> Result<PromiseHandle, JsValue> {
        let provider = self
            .providers
            .file()
            .ok_or_else(|| JsValue::from_str("file/unsupported"))?;
        provider
            .write(path, bytes)
            .map(PromiseHandle::from_promise)
            .map_err(|error| JsValue::from_str(&format!("file/{}", error.code())))
    }

    pub fn extension_available(&self, name: &str) -> bool {
        self.extensions.contains(name)
    }

    pub fn require_extension(&mut self, name: &str) -> Result<String, JsValue> {
        self.extensions
            .require(name, &mut self.protocols)
            .map_err(|error| JsValue::from_str(&error))
    }

    /// Registers a host-supplied Hara resource. Resources are source text, not executable host code.
    pub fn register_resource(&mut self, name: &str, source: &str) {
        self.resources.insert(name.into(), source.into());
    }

    /// Evaluates a registered resource in the current lexical namespace.
    pub fn load_resource(&mut self, name: &str) -> Result<String, JsValue> {
        let source = self
            .resources
            .get(name)
            .cloned()
            .ok_or_else(|| JsValue::from_str("module/not-found"))?;
        self.eval_text(&source)
            .map_err(|error| JsValue::from_str(&error))
    }

    /// Loads a resource once; subsequent requires return the current loaded marker.
    pub fn require_resource(&mut self, name: &str) -> Result<String, JsValue> {
        if self.loaded_resources.contains(name) {
            return Ok(":loaded".into());
        }
        if self.extensions.contains(name) {
            let result = self.require_extension(name)?;
            self.loaded_resources.insert(name.into());
            return Ok(result);
        }
        let result = self.load_resource(name)?;
        self.loaded_resources.insert(name.into());
        Ok(result)
    }

    pub fn file_supported(&self) -> bool {
        self.providers.capabilities().file
    }

    pub fn socket_supported(&self) -> bool {
        self.providers.capabilities().socket
    }

    /// Opens a callback-based socket and returns its provider-owned handle.
    pub fn socket_connect(&self, host: &str, port: u16) -> Result<u64, JsValue> {
        let provider = self
            .providers
            .socket()
            .ok_or_else(|| JsValue::from_str("socket/unsupported"))?;
        provider
            .connect(host, port, ignore_socket_event)
            .map_err(|error| JsValue::from_str(&format!("socket/{}", error.code())))
    }

    pub fn socket_send(&self, socket: u64, bytes: Vec<u8>) -> Result<usize, JsValue> {
        let provider = self
            .providers
            .socket()
            .ok_or_else(|| JsValue::from_str("socket/unsupported"))?;
        provider
            .send(socket, &bytes)
            .map_err(|error| JsValue::from_str(&format!("socket/{}", error.code())))
    }

    pub fn socket_close(&self, socket: u64) -> Result<(), JsValue> {
        let provider = self
            .providers
            .socket()
            .ok_or_else(|| JsValue::from_str("socket/unsupported"))?;
        provider
            .close(socket)
            .map_err(|error| JsValue::from_str(&format!("socket/{}", error.code())))
    }

    /// Returns whether a protocol method is registered in this runtime context.
    pub fn has_protocol_method(&self, protocol: &str, method: &str) -> bool {
        self.protocols.contains(protocol, method)
    }

    pub fn eval(&mut self, source: &str) -> Result<String, JsValue> {
        self.eval_text(source)
            .map_err(|error| JsValue::from_str(&error))
    }

    pub fn eval_traced(&mut self, source: &str) -> Result<String, JsValue> {
        self.refresh_qualified_bindings();
        core::eval_text_traced(source, &mut self.env).map_err(|error| JsValue::from_str(&error))
    }

    #[cfg(not(target_arch = "wasm32"))]
    pub fn eval_native(&mut self, source: &str) -> Result<String, String> {
        self.eval_text(source)
    }

    #[cfg(not(target_arch = "wasm32"))]
    pub fn eval_native_traced(&mut self, source: &str) -> Result<String, String> {
        self.refresh_qualified_bindings();
        core::eval_text_traced(source, &mut self.env)
    }
}

#[wasm_bindgen]
pub fn target_profile() -> String {
    if cfg!(target_os = "wasi") {
        "wasi".into()
    } else if cfg!(target_arch = "wasm32") {
        "wasm".into()
    } else {
        "native".into()
    }
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

    fn count_socket_event(_event: core::SocketEvent) {
        SOCKET_EVENTS.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
    }

    #[cfg(not(target_arch = "wasm32"))]
    #[test]
    fn native_socket_provider_sends_callbacks_and_bytes() {
        use crate::core::SocketProvider;
        use std::io::Read;
        use std::net::TcpListener;
        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut bytes = [0u8; 3];
            stream.read_exact(&mut bytes).unwrap();
            bytes
        });
        SOCKET_EVENTS.store(0, std::sync::atomic::Ordering::SeqCst);
        let sockets = core::NativeSocketProvider::default();
        let handle = sockets
            .connect("127.0.0.1", port, count_socket_event)
            .unwrap();
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
        let resolved = provider
            .resolve(path.to_str().unwrap(), "data.bin")
            .unwrap();
        assert_eq!(
            provider.write(&resolved, vec![4, 5, 6]).unwrap().state(),
            core::PromiseState::Fulfilled(core::Value::Nil)
        );
        assert_eq!(
            provider.read(&resolved).unwrap().state(),
            core::PromiseState::Fulfilled(core::Value::Bytes(vec![4, 5, 6]))
        );
        std::fs::remove_file(resolved).unwrap();
        std::fs::remove_dir(path).unwrap();
    }

    #[test]
    fn extension_provider_values_load_and_iterate_through_protocols() {
        let mut runtime = Runtime::new();
        runtime.extensions.install(RangeExtension);
        assert!(runtime.extension_available("range"));
        assert_eq!(runtime.require_resource("range").unwrap(), ":loaded");
        let value = runtime
            .extensions
            .construct("range", "range", &[core::Value::Number(3)])
            .unwrap();
        assert_eq!(core::receiver_category(&value), "extension");
        runtime.env.insert("r".into(), value);
        assert_eq!(runtime.eval_text("(iter-next (iter r))").unwrap(), "0");
        assert_eq!(runtime.eval_text("(iter-next (iter r))").unwrap(), "0");
        assert_eq!(runtime.require_resource("range").unwrap(), ":loaded");
    }

    #[test]
    fn runtime_routes_file_operations_through_provider_registry() {
        let mut runtime = Runtime::new();
        assert!(!runtime.file_supported());
        runtime.install_memory_file_provider("/sandbox");
        assert!(runtime.file_supported());
        let path = runtime.file_resolve("/sandbox", "data.bin").unwrap();
        assert_eq!(
            runtime
                .file_write(&path, vec![1, 2, 3])
                .unwrap()
                .value()
                .unwrap(),
            "nil"
        );
        assert_eq!(
            runtime.file_read(&path).unwrap().value().unwrap(),
            "#bytes[1 2 3]"
        );
        runtime.install_loopback_socket_provider();
        assert!(runtime.socket_supported());
    }

    #[test]
    fn provider_registry_reports_installed_capabilities() {
        let mut registry = core::ProviderRegistry::new();
        assert_eq!(
            registry.capabilities(),
            core::ProviderCapabilities {
                file: false,
                socket: false
            }
        );
        registry.install_file(core::MemoryFileProvider::new("/sandbox"));
        registry.install_socket(core::LoopbackSocketProvider::default());
        assert_eq!(
            registry.capabilities(),
            core::ProviderCapabilities {
                file: true,
                socket: true
            }
        );
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
        let handle = sockets
            .connect("localhost", 8080, count_socket_event)
            .unwrap();
        assert_eq!(sockets.send(handle, &[1, 2, 3]).unwrap(), 3);
        sockets.close(handle).unwrap();
        assert_eq!(SOCKET_EVENTS.load(std::sync::atomic::Ordering::SeqCst), 3);
        assert_eq!(
            sockets.send(handle, &[9]).unwrap_err(),
            core::SocketError::Invalid("unknown socket".into())
        );
    }

    #[test]
    fn memory_file_provider_enforces_root_and_preserves_bytes() {
        use crate::core::FileProvider;
        let files = core::MemoryFileProvider::new("/sandbox");
        assert_eq!(
            files.resolve("/sandbox", "docs/../secret").unwrap_err(),
            core::FileError::Denied
        );
        let path = files.resolve("/sandbox", "data.bin").unwrap();
        let write = files.write(&path, vec![0, 127, 255]).unwrap();
        assert_eq!(
            write.state(),
            core::PromiseState::Fulfilled(core::Value::Nil)
        );
        let read = files.read(&path).unwrap();
        assert_eq!(
            read.state(),
            core::PromiseState::Fulfilled(core::Value::Bytes(vec![0, 127, 255]))
        );
        assert_eq!(
            files.read("/outside/data.bin").unwrap_err(),
            core::FileError::Denied
        );
    }

    #[test]
    fn unsupported_capabilities_fail_stably() {
        use crate::core::{FileProvider, SocketProvider};
        let files = core::UnsupportedFileProvider;
        assert_eq!(
            files.resolve("/root", "data.bin").unwrap_err(),
            core::FileError::Unsupported
        );
        assert_eq!(
            files.read("data.bin").unwrap_err(),
            core::FileError::Unsupported
        );
        let sockets = core::UnsupportedSocketProvider;
        assert_eq!(
            sockets
                .connect("localhost", 80, ignore_socket_event)
                .unwrap_err(),
            core::SocketError::Unsupported
        );
        assert_eq!(
            sockets.send(1, &[1, 2]).unwrap_err(),
            core::SocketError::Unsupported
        );
        assert_eq!(
            sockets.close(1).unwrap_err(),
            core::SocketError::Unsupported
        );
    }

    #[test]
    fn namespace_aliases_route_evaluation_and_resources() {
        let mut runtime = Runtime::new();
        assert!(runtime.create_namespace("hara.math"));
        assert!(runtime.alias_namespace("math", "hara.math"));
        assert_eq!(runtime.resolve_namespace("math"), "hara.math");
        assert_eq!(
            runtime
                .eval_in_namespace("math", "(defn answer [] 42) (answer)")
                .unwrap(),
            "42"
        );
        runtime.register_resource("helpers", "(defn helper [] 7) (helper)");
        assert_eq!(
            runtime
                .require_resource_in_namespace("helpers", "math")
                .unwrap(),
            "7"
        );
        assert_eq!(runtime.eval_text("(helper)").unwrap(), "7");
    }

    #[test]
    fn qualified_namespace_symbols_resolve_shared_vars_and_aliases() {
        let mut runtime = Runtime::new();
        assert!(runtime.create_namespace("alpha"));
        assert_eq!(
            runtime
                .eval_in_namespace("alpha", "(def answer 41)")
                .unwrap(),
            "41"
        );
        runtime.use_namespace("user");
        assert_eq!(runtime.eval_text("alpha/answer").unwrap(), "41");
        assert!(runtime.alias_namespace("a", "alpha"));
        assert_eq!(runtime.eval_text("a/answer").unwrap(), "41");
        assert_eq!(
            runtime
                .eval_text("(do (set! alpha/answer 42) alpha/answer)")
                .unwrap(),
            "42"
        );
        runtime.use_namespace("alpha");
        assert_eq!(runtime.eval_text("answer").unwrap(), "42");
    }

    #[test]
    fn namespaces_isolate_bindings_and_can_be_selected() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.current_namespace(), "user");
        assert!(runtime.create_namespace("math"));
        runtime.eval_text("(defn answer [] 42)").unwrap();
        runtime.use_namespace("math");
        assert_eq!(
            runtime.eval_text("(defn answer [] 7) (answer)").unwrap(),
            "7"
        );
        runtime.use_namespace("user");
        assert_eq!(runtime.eval_text("(answer)").unwrap(), "42");
        runtime.use_namespace("math");
        assert_eq!(runtime.eval_text("(answer)").unwrap(), "7");
    }

    #[test]
    fn generated_namespaces_configure_aliases_refers_and_intrinsics_without_sources() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("(str/trim \"  hara  \")").unwrap(),
            "\"hara\""
        );
        assert_eq!(
            runtime
                .eval_text(
                    "(ns app (:intrinsics {:exclude [bytes] :aliases {string text}})                       (:require [hara.lib.string :as s :refer [trim]]))                       (trim (s/trim (text/to-upper \" x \")))"
                )
                .unwrap(),
            "\"X\""
        );
        assert!(runtime
            .eval_text("(bytes/count (bytes 1))")
            .unwrap_err()
            .contains("bytes/count"));
        assert_eq!(
            runtime
                .eval_text("(ns core-user (:require [hara.lib.core :as core])) (core/bit-not 0)")
                .unwrap(),
            "-1"
        );
    }

    #[test]
    fn generated_namespace_require_never_falls_back_to_registered_source() {
        let mut runtime = Runtime::new();
        runtime.register_resource("hara.lib.string", "(def poisoned 42)");
        assert_eq!(
            runtime
                .eval_text("(ns app (:require [hara.lib.string :as text])) (text/trim \" x \")")
                .unwrap(),
            "\"x\""
        );
        assert!(runtime
            .eval_text("poisoned")
            .unwrap_err()
            .contains("unbound symbol"));
    }

    #[test]
    fn resource_sources_accept_namespace_declarations() {
        let mut runtime = Runtime::new();
        runtime.register_resource(
            "module",
            "(ns demo (:require [core])) (defn answer [] 42) (answer)",
        );
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
    fn set_literals_reject_duplicate_items() {
        let mut runtime = Runtime::new();
        assert!(runtime
            .eval_text("#{1 (+ 1 1) 1}")
            .unwrap_err()
            .contains("Duplicate item"));
        assert!(runtime
            .eval_text("(count #{1 2 2})")
            .unwrap_err()
            .contains("Duplicate item"));
        assert_eq!(
            runtime
                .eval_text("(protocol-call IFind has? #{1 2} 2)")
                .unwrap(),
            "true"
        );
        assert_eq!(runtime.eval_text("(conj #{1} 2)").unwrap(), "#{1 2}");
        assert_eq!(runtime.eval_text("(set 1 2 1)").unwrap(), "#{1 2}");
        assert_eq!(runtime.eval_text("(= #{1 2} #{2 1})").unwrap(), "true");
        assert_eq!(runtime.eval_text("(get #{1 2} 2 :missing)").unwrap(), "2");
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
    fn structural_hashes_are_stable_and_order_independent_for_maps_and_sets() {
        let mut runtime = Runtime::new();
        let _ = &mut runtime;
        let map_a = core::Value::Map(
            vec![
                (core::Value::Keyword("a".into()), core::Value::Number(1)),
                (core::Value::Keyword("b".into()), core::Value::Number(2)),
            ]
            .into_iter()
            .collect(),
        );
        let map_b = core::Value::Map(
            vec![
                (core::Value::Keyword("b".into()), core::Value::Number(2)),
                (core::Value::Keyword("a".into()), core::Value::Number(1)),
            ]
            .into_iter()
            .collect(),
        );
        let set_a = core::Value::Set(
            vec![
                core::Value::Number(1),
                core::Value::Number(2),
                core::Value::Number(3),
            ]
            .into(),
        );
        let set_b = core::Value::Set(
            vec![
                core::Value::Number(3),
                core::Value::Number(1),
                core::Value::Number(2),
            ]
            .into(),
        );
        assert_eq!(map_a.stable_hash(), map_b.stable_hash());
        assert_eq!(set_a.stable_hash(), set_b.stable_hash());
    }

    #[test]
    fn map_membership_keys_and_values_are_portable() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text(r#"(protocol-call IFind has? {"a" 1} "a")"#)
                .unwrap(),
            "true"
        );
        assert_eq!(
            runtime
                .eval_text("(protocol-call IFind has? [1 2] 1)")
                .unwrap(),
            "true"
        );
        assert_eq!(
            runtime
                .eval_text("(protocol-call IFind has? [1 2] 2)")
                .unwrap(),
            "false"
        );
        assert_eq!(
            runtime
                .eval_text(r#"(protocol-call IFind has? {"a" nil} "a")"#)
                .unwrap(),
            "true"
        );
        assert_eq!(
            runtime.eval_text(r#"(keys {"a" 1 "b" 2})"#).unwrap(),
            "[\"a\" \"b\"]"
        );
        assert_eq!(
            runtime.eval_text(r#"(vals {"a" 1 "b" 2})"#).unwrap(),
            "[1 2]"
        );
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
        assert_eq!(
            runtime.eval_text(r#"(conj {"a" 1} ["b" 2])"#).unwrap(),
            r#"{"a" 1 "b" 2}"#
        );
        assert_eq!(
            runtime
                .eval_text(r#"(get (conj {"a" 1} ["a" 9]) "a")"#)
                .unwrap(),
            "9"
        );
        assert_eq!(
            runtime.eval_text(r#"(dissoc {"a" 1 "b" 2} "a")"#).unwrap(),
            r#"{"b" 2}"#
        );
        assert_eq!(
            runtime
                .eval_text(r#"(dissoc {"a" 1 "b" 2} "a" "b")"#)
                .unwrap(),
            "{}"
        );
        assert_eq!(runtime.eval_text("(cons 0 [1 2])").unwrap(), "[0 1 2]");
        assert_eq!(runtime.eval_text("(= :ready :ready)").unwrap(), "true");
    }

    #[test]
    fn persistent_vectors_and_lists_keep_previous_values() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(let (source [1 2]) (get (conj source 3) 2))")
                .unwrap(),
            "3"
        );
        assert_eq!(
            runtime
                .eval_text("(let (source [1 2]) (count source))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(let (source (rest [1 2])) (count (conj source 2)))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(let (source (rest [1 2])) (count source))")
                .unwrap(),
            "1"
        );
    }

    struct RangeExtension;

    impl core::ExtensionProvider for RangeExtension {
        fn name(&self) -> &str {
            "range"
        }

        fn install(&self, protocols: &mut core::ProtocolRegistry) {
            protocols.register("IIter", "iter", |arguments| match arguments.first() {
                Some(core::Value::Extension(value))
                    if value.provider == "range" && value.type_name == "range" =>
                {
                    Ok(core::iterator_from_values(
                        (0..value.handle)
                            .map(|index| core::Value::Number(index as i64))
                            .collect(),
                    ))
                }
                _ => Err("range/IIter does not accept this value".into()),
            });
        }

        fn construct(
            &self,
            type_name: &str,
            arguments: &[core::Value],
        ) -> Result<core::Value, String> {
            if type_name != "range" {
                return Err("range/type-not-found".into());
            }
            let count = match arguments.first() {
                Some(core::Value::Number(count)) if *count >= 0 => *count as u64,
                _ => return Err("range expects a non-negative count".into()),
            };
            Ok(core::Value::Extension(core::ExtensionValue {
                provider: "range".into(),
                type_name: "range".into(),
                handle: count,
            }))
        }
    }

    fn protocol_identity(arguments: &[core::Value]) -> Result<core::Value, String> {
        arguments
            .first()
            .cloned()
            .ok_or_else(|| "missing receiver".into())
    }

    fn protocol_custom_iterator(_arguments: &[core::Value]) -> Result<core::Value, String> {
        Ok(core::iterator_from_values(vec![
            core::Value::Number(7),
            core::Value::Number(8),
        ]))
    }

    #[test]
    fn promise_values_are_composable_and_settle_once() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("(promise/state (promise))").unwrap(),
            ":pending"
        );
        assert_eq!(
            runtime
                .eval_text("(let (p (promise)) (do (promise/resolve p 42) (promise/value p)))")
                .unwrap(),
            "42"
        );
        assert_eq!(
            runtime
                .eval_text(
                    r#"(let (p (promise)) (do (promise/reject p "boom") (promise/state p)))"#
                )
                .unwrap(),
            ":rejected"
        );
        assert_eq!(runtime.eval_text("(let (p (promise)) (let (q (promise)) (do (promise/resolve q 7) (promise/adopt p q) (promise/value p))))").unwrap(), "7");
        assert_eq!(
            runtime
                .eval_text("(let (p (promise)) (do (promise/resolve p 1) (promise/resolve p 2)))")
                .unwrap_err(),
            "promise is already settled"
        );
    }

    #[test]
    fn promises_support_map_recover_and_finally() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text(
                    "(promise/state (promise/map (promise/resolve (promise) 41) (fn [x] (+ x 1))))"
                )
                .unwrap(),
            ":fulfilled"
        );
        assert_eq!(runtime.eval_text("(promise/value (promise/recover (promise/reject (promise) :bad) (fn [x] (str x :ok))))").unwrap(), "\":bad:ok\"");
        assert_eq!(
            runtime
                .eval_text(
                    "(promise/value (promise/finally (promise/resolve (promise) 42) (fn [] 0)))"
                )
                .unwrap(),
            "42"
        );
        assert_eq!(runtime.eval_text("(let (source (promise) mapped (promise/map source (fn [x] (+ x 1)))) (do (promise/resolve source 41) (promise/value mapped)))").unwrap(), "42");
        assert_eq!(runtime.eval_text("(let (source (promise) recovered (promise/recover source (fn [x] (str x :ok)))) (do (promise/reject source :bad) (promise/value recovered)))").unwrap(), "\":bad:ok\"");
        assert_eq!(runtime.eval_text("(let (source (promise) final (promise/finally source (fn [] 0))) (do (promise/resolve source 42) (promise/value final)))").unwrap(), "42");
        assert_eq!(runtime.eval_text("(let (source (promise) final (promise/finally source (fn [] (throw :cleanup)))) (do (promise/resolve source 42) (promise/value final)))").unwrap_err(), "thrown: :cleanup");
        assert_eq!(
            runtime
                .eval_text("(promise/state (promise/cancel (promise)))")
                .unwrap(),
            ":cancelled"
        );
        assert_eq!(
            runtime
                .eval_text("(promise/value (promise/cancel (promise)))")
                .unwrap_err(),
            "cancelled"
        );
    }

    #[test]
    fn generated_promise_library_matches_the_portable_contract() {
        let mut runtime = Runtime::new();
        let cases = [
            (
                "(promise/value (promise/new (fn [resolve reject] (resolve 42))))",
                "42",
            ),
            ("(promise/value (promise/run (fn [] 40)))", "40"),
            (
                "(promise/value (promise/then (promise/run (fn [] 40)) (fn [x] (+ x 2))))",
                "42",
            ),
            (
                "(promise/value (promise/then (promise/run (fn [] 40)) (fn [x] (promise/run (fn [] (+ x 2))))))",
                "42",
            ),
            (
                r#"(promise/value (promise/catch (promise/run (fn [] (throw "bad"))) (fn [error] 7)))"#,
                "7",
            ),
            (
                "(. (promise/value (promise/all [(promise/run (fn [] 1)) 2 (promise/run (fn [] 3))])) (get 1))",
                "2",
            ),
            (
                "(promise/value (promise/run (fn [] (promise/run (fn [] 9)))))",
                "9",
            ),
            (
                "(promise/value (promise/finally (promise/run (fn [] 4)) (fn [] (promise/run (fn [] 99)))))",
                "4",
            ),
            (
                "(promise/value (promise/delay 0 (fn [] 5)))",
                "5",
            ),
            (
                "(promise/native? (promise/new (fn [resolve reject] (resolve 1))))",
                "true",
            ),
            (
                "(let [p (promise/delay 10000 (fn [] 1))] (do (promise/cancel p) (promise/state p)))",
                ":cancelled",
            ),
        ];
        for (source, expected) in cases {
            assert_eq!(runtime.eval_text(source).unwrap(), expected, "{source}");
        }
        assert!(runtime
            .eval_text("(promise/delay -1 (fn [] 1))")
            .unwrap_err()
            .contains("non-negative"));
        assert!(runtime
            .eval_text("(promise/new 1)")
            .unwrap_err()
            .contains("expects a function"));
    }
    #[test]
    fn promise_continuations_preserve_registration_order_and_late_delivery() {
        let promise = core::Promise::new();
        let events = std::rc::Rc::new(std::cell::RefCell::new(Vec::new()));
        let first = events.clone();
        promise.on_settle(std::rc::Rc::new(move |_| first.borrow_mut().push(1)));
        let second = events.clone();
        promise.on_settle(std::rc::Rc::new(move |_| second.borrow_mut().push(2)));
        assert!(promise.resolve(core::Value::Number(7)));
        assert_eq!(*events.borrow(), vec![1, 2]);
        let late = events.clone();
        promise.on_settle(std::rc::Rc::new(move |_| late.borrow_mut().push(3)));
        assert_eq!(*events.borrow(), vec![1, 2, 3]);
        assert!(!promise.reject("late"));
    }

    #[test]
    fn promises_settle_once_and_adopt() {
        let pending = core::Promise::new();
        let adopted = core::Promise::new();
        assert_eq!(pending.state(), core::PromiseState::Pending);
        assert!(adopted.adopt(&pending));
        assert!(pending.resolve(core::Value::Number(7)));
        assert!(!pending.reject("late"));
        assert_eq!(
            adopted.state(),
            core::PromiseState::Fulfilled(core::Value::Number(7))
        );
    }

    #[test]
    fn marker_mutation_methods_cover_array_and_object_boundaries() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(let (a (array 2)) (do (. a (push-first 1)) (. a (push-last 3)) (. a (insert 1 9)) (. a (get 1))))").unwrap(), "9");
        assert_eq!(
            runtime
                .eval_text(
                    "(let (a (array 1 2)) (do (. a (pop-first)) (. a (pop-last)) (count a)))"
                )
                .unwrap(),
            "0"
        );
        assert_eq!(
            runtime
                .eval_text(r#"(. (object "a" 1 "b" 2) (keys))"#)
                .unwrap(),
            r#"(array "a" "b")"#
        );
        assert_eq!(
            runtime
                .eval_text(r#"(. (object "a" 1 "b" 2) (vals))"#)
                .unwrap(),
            "(array 1 2)"
        );
        assert_eq!(
            runtime
                .eval_text(
                    r#"(let (o (object "a" 1)) (do (. o (assign (object "b" 2))) (. o (get "b"))))"#
                )
                .unwrap(),
            "2"
        );
    }

    #[test]
    fn marker_dot_contract_covers_results_identity_callbacks_and_rejections() {
        let mut runtime = Runtime::new();
        let cases = [
            ("(. (. (array 1 2 3) (map (fn [x] (* x 2)))) (get 2))", "6"),
            (
                "(. (. (array 1 2 3 4) (filter (fn [x] (> x 2)))) (get 0))",
                "3",
            ),
            ("(. (. (array 1 2 3) (slice 1)) (get 1))", "3"),
            (
                "(. (array 1 2 3) (fold-left (fn [out x] (- out x)) 0))",
                "-6",
            ),
            (
                "(. (array 1 2 3) (fold-right (fn [x out] (- x out)) 0))",
                "2",
            ),
            ("(let [a (array 1)] (= a (. a (push-last 2))))", "true"),
            ("(let [a (array 1)] (= a (. a (set 0 2))))", "true"),
            ("(let [a (array 1)] (= a (. a (insert 1 2))))", "true"),
            ("(let [a (array 1)] (= a (. a (clone))))", "false"),
            (
                r#"(let [o (object "a" 1)] (= o (. o (set "a" 2))))"#,
                "true",
            ),
            (r#"(. (object "a" 1) (delete "a"))"#, "1"),
            (r#"(. (object "a" 1) (delete "missing"))"#, "nil"),
            (r#"(. (. (object "a" 1) (keys)) (get 0))"#, r#""a""#),
            (r#"(. (. (. (object "a" 1) (pairs)) (get 0)) (get 1))"#, "1"),
            ("(iter-next (iter (array 7 8)))", "7"),
            (r#"(second (iter-next (iter (object "a" 7))))"#, "7"),
        ];
        for (source, expected) in cases {
            assert_eq!(runtime.eval_text(source).unwrap(), expected, "{source}");
        }

        let invalid = [
            ("(. [1 2] (get 0))", "array or object marker"),
            ("(. {} (get \"a\"))", "array or object marker"),
            ("(. 1 (get 0))", "array or object marker"),
            ("(. (array 1) (unknown))", "unsupported array method"),
            (
                r#"(. (object "a" 1) (unknown))"#,
                "unsupported object method",
            ),
            ("(. (array 1) (set 0))", "expects an index and value"),
            ("(. (array 1) (clone 1))", "expects no arguments"),
            (r#"(. (object "a" 1) (clone 1))"#, "expects no arguments"),
            (
                "(. (array 1) (map (fn [x y] x)))",
                "function expects 2 arguments",
            ),
            ("(x:array 1)", "unbound symbol: x:array"),
            ("(x:object)", "unbound symbol: x:object"),
            ("(x:get nil 0)", "unbound symbol: x:get"),
            ("(x:set nil 0 1)", "unbound symbol: x:set"),
            (
                r#"(host-symbol "java.lang.String")"#,
                "unbound symbol: host-symbol",
            ),
            (r#"(host-get nil "value")"#, "unbound symbol: host-get"),
            (r#"(host-call nil "run")"#, "unbound symbol: host-call"),
        ];
        for (source, message) in invalid {
            assert!(
                runtime.eval_text(source).unwrap_err().contains(message),
                "{source}"
            );
        }
    }
    #[test]
    fn marker_arrays_and_objects_use_restricted_dot_calls() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(count (array 1 2 3))").unwrap(), "3");
        assert_eq!(runtime.eval_text("(. (array 1 2) (get 1))").unwrap(), "2");
        assert_eq!(
            runtime
                .eval_text("(let (a (array 1 2)) (do (. a (set 1 7)) (. a (get 1))))")
                .unwrap(),
            "7"
        );
        assert_eq!(
            runtime
                .eval_text("(let (a (array 1)) (do (. a (push-last 2)) (count a)))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text(r#"(. (object "answer" 41) (get "answer"))"#)
                .unwrap(),
            "41"
        );
        assert_eq!(
            runtime
                .eval_text(
                    r#"(let (o (object)) (do (. o (set "answer" 42)) (. o (get "answer"))))"#
                )
                .unwrap(),
            "42"
        );
        assert_eq!(
            runtime
                .eval_text(r#"(. (object "answer" 41) (has? "answer"))"#)
                .unwrap(),
            "true"
        );
    }

    #[test]
    fn strings_and_bytes_support_utf8_copy_and_slice() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text(r#"(str "hello" " " "world")"#).unwrap(),
            "\"hello world\""
        );
        assert_eq!(runtime.eval_text(r#"(str/count "hé")"#).unwrap(), "2");
        assert_eq!(
            runtime.eval_text(r#"(str/trim "  hara  ")"#).unwrap(),
            "\"hara\""
        );
        assert_eq!(
            runtime
                .eval_text(r#"(str/decode (str/encode "hé"))"#)
                .unwrap(),
            "\"hé\""
        );
        assert_eq!(
            runtime
                .eval_text("(bytes/slice (bytes 1 2 3) 1 3)")
                .unwrap(),
            "(bytes 2 3)"
        );
        assert_eq!(runtime.eval_text("(let (source (bytes 1 2)) (let (copy (bytes/copy source)) (do (bytes/set copy 0 9) (bytes/get source 0))))").unwrap(), "1");
    }

    #[test]
    fn generated_string_library_covers_the_portable_surface() {
        let mut runtime = Runtime::new();
        let cases = [
            (r#"(str/len "hé")"#, "2"),
            (r#"(str/comp "a" "b")"#, "-1"),
            (r#"(str/lt? "a" "b")"#, "true"),
            (r#"(str/gt? "b" "a")"#, "true"),
            (r#"(str/pad-left "7" 3 "0")"#, r#""007""#),
            (r#"(str/pad-right "7" 3 "0")"#, r#""700""#),
            (r#"(str/starts-with? "hara" "ha")"#, "true"),
            (r#"(str/ends-with? "hara" "ra")"#, "true"),
            (r#"(str/char "h😀" 1)"#, r#""😀""#),
            (r#"(. (str/split "a,b,c" ",") (get 1))"#, r#""b""#),
            (r#"(str/join "-" ["a" "b"])"#, r#""a-b""#),
            (r#"(str/index-of "a😀b" "b")"#, "2"),
            (r#"(str/substring "a😀b" 1 2)"#, r#""😀""#),
            (r#"(str/to-fixed 1 2)"#, r#""1.00""#),
            (r#"(str/replace "a-b-a" "a" "x")"#, r#""x-b-x""#),
            (r#"(str/trim-left "  a  ")"#, r#""a  ""#),
            (r#"(str/trim-right "  a  ")"#, r#""  a""#),
            (r#"(str/to-upper "Hara")"#, r#""HARA""#),
            (r#"(str/to-lower "Hara")"#, r#""hara""#),
        ];
        for (source, expected) in cases {
            assert_eq!(runtime.eval_text(source).unwrap(), expected, "{source}");
        }
        assert!(runtime
            .eval_text(r#"(str/substring "abc" 2 1)"#)
            .unwrap_err()
            .contains("out of bounds"));
        assert!(runtime
            .eval_text(r#"(str/char "a" 1)"#)
            .unwrap_err()
            .contains("out of bounds"));
    }

    #[test]
    fn byte_buffers_preserve_signed_storage_and_unsigned_reads() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("(bytes 1 2 -3)").unwrap(),
            "(bytes 1 2 -3)"
        );
        assert_eq!(
            runtime.eval_text("(bytes/get (bytes 1 2 -3) 2)").unwrap(),
            "253"
        );
        assert_eq!(runtime.eval_text("(bytes/u8 -1)").unwrap(), "255");
        assert_eq!(runtime.eval_text("(bytes/s8 255)").unwrap(), "-1");
        assert_eq!(
            runtime
                .eval_text("(let (b (bytes 1 2)) (do (bytes/set b 0 9) (bytes/get b 0)))")
                .unwrap(),
            "9"
        );
        assert_eq!(runtime.eval_text("(bytes/get (bytes 1) 4 7)").unwrap(), "7");
        assert_eq!(
            runtime.eval_text("(bytes/count (bytes 1 2 -3))").unwrap(),
            "3"
        );
    }

    #[test]
    fn bytes_and_bits_cover_conversion_copy_and_overflow_boundaries() {
        let mut runtime = Runtime::new();
        let cases = [
            ("(bytes/u8 -128)", "128"),
            ("(bytes/u8 255)", "255"),
            ("(bytes/s8 -128)", "-128"),
            ("(bytes/s8 128)", "-128"),
            ("(bytes/s8 255)", "-1"),
            ("(bytes/get (bytes -128 0 127 255) 0)", "128"),
            ("(bytes/get (bytes -128 0 127 255) 3)", "255"),
            ("(bytes/slice (bytes 1 2 3) 1)", "(bytes 2 3)"),
            ("(bytes/slice (bytes 1 2 3) 1 1)", "(bytes)"),
            (
                "(let [b (bytes 0)] (count [(bytes/set b 0 255) (bytes/get b 0)]))",
                "2",
            ),
            ("(bit-not -2147483648)", "2147483647"),
            ("(bit-not 2147483647)", "-2147483648"),
            ("(bit-and -2147483648 2147483647)", "0"),
            ("(bit-or -2147483648 1)", "-2147483647"),
            ("(bit-xor -1 2147483647)", "-2147483648"),
            ("(bit-shift-left 1 0)", "1"),
            ("(bit-shift-left 1 31)", "-2147483648"),
            ("(bit-shift-left 2147483647 1)", "-2"),
            ("(bit-shift-right -2147483648 31)", "-1"),
            ("(bit-shift-right 2147483647 31)", "0"),
            ("(bit-shift-left 2147483648 0)", "-2147483648"),
        ];
        for (source, expected) in cases {
            assert_eq!(runtime.eval_text(source).unwrap(), expected, "{source}");
        }

        let invalid = [
            ("(bytes -129)", "range -128..255"),
            ("(bytes 256)", "range -128..255"),
            ("(bytes/u8 -129)", "range -128..255"),
            ("(bytes/s8 256)", "range -128..255"),
            ("(bytes/get (bytes 1) 1)", "out of bounds"),
            ("(bytes/set (bytes 1) 1 0)", "out of bounds"),
            ("(bytes/slice (bytes 1 2) 2 1)", "out of bounds"),
            ("(bytes/slice (bytes 1 2) 0 3)", "out of bounds"),
            ("(str/decode (bytes 255))", "invalid UTF-8"),
            ("(bit-shift-left 1 -1)", "range 0..31"),
            ("(bit-shift-right 1 32)", "range 0..31"),
        ];
        for (source, message) in invalid {
            assert!(
                runtime.eval_text(source).unwrap_err().contains(message),
                "{source}"
            );
        }

        assert_eq!(
            runtime
                .eval_text("(let [source (bytes 1 2 3) copy (bytes/copy source)] (do (bytes/set copy 0 9) (bytes/get source 0)))")
                .unwrap(),
            "1"
        );
        assert_eq!(
            runtime
                .eval_text("(let [source (bytes 1 2 3) part (bytes/slice source 0 2)] (do (bytes/set part 0 9) (bytes/get source 0)))")
                .unwrap(),
            "1"
        );
    }
    #[test]
    fn iterator_aliases_and_combinators_match_core_shapes() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(iter-next (map (fn [x] (* x 2)) [1 2]))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(iter-next (filter (fn [x] (= x 2)) [1 2 3]))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(iter-next (take 1 (drop 1 [1 2 3])))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(nth (iter-next (zip [1] [2])) 1)")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text(
                    "(let (it (cycle [1 2])) (do (iter-next it) (iter-next it) (iter-next it)))"
                )
                .unwrap(),
            "1"
        );
        assert_eq!(
            runtime.eval_text("(iter-next (concat [1] [2]))").unwrap(),
            "1"
        );
    }

    #[test]
    fn seq_boundaries_and_source_aware_transforms_match_design() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("(seq? (map inc [1 2 3]))").unwrap(),
            "true"
        );
        assert_eq!(runtime.eval_text("(first (map inc [1 2 3]))").unwrap(), "2");
        assert_eq!(
            runtime.eval_text("(first ((map inc) [1 2 3]))").unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(first ((map inc) (seq [1 2 3])))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(first (seq (map inc) [1 2 3]))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(first ((comp (map inc) (map inc)) [1 2 3]))")
                .unwrap(),
            "3"
        );
        assert_eq!(
            runtime
                .eval_text("(first (seq (comp (map inc) (map inc)) [1 2 3]))")
                .unwrap(),
            "3"
        );
    }

    #[test]
    fn iterators_are_closeable_and_support_map_filter() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(let (it (iter [1 2])) (iter-next it))")
                .unwrap(),
            "1"
        );
        assert_eq!(
            runtime
                .eval_text("(let (it (iter [1 2])) (do (iter-next it) (iter-next it)))")
                .unwrap(),
            "2"
        );
        assert_eq!(runtime.eval_text("(iter-has? (iter [1]))").unwrap(), "true");
        assert_eq!(
            runtime
                .eval_text("(let (it (iter [1])) (do (iter-close it) (iter-has? it)))")
                .unwrap(),
            "false"
        );
        assert_eq!(
            runtime
                .eval_text("(iter-next (iter-map (fn [x] (* x 2)) [1 2]))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(iter-next (iter-filter (fn [x] (= x 2)) [1 2 3]))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(receiver-category (iter [1]))")
                .unwrap_err(),
            "unbound symbol: receiver-category"
        );
    }

    #[test]
    fn evaluator_protocol_calls_cover_collections_and_bytes() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(protocol-call ICount count [1 2 3])")
                .unwrap(),
            "3"
        );
        assert_eq!(
            runtime
                .eval_text("(protocol-call INth nth (bytes 1 -3) 1)")
                .unwrap(),
            "-3"
        );
        assert_eq!(
            runtime
                .eval_text(r#"(protocol-call ILookup lookup {"a" 9} "a")"#)
                .unwrap(),
            "9"
        );
        assert_eq!(
            runtime
                .eval_text(r#"(protocol-call IFind has? {"a" nil} "a")"#)
                .unwrap(),
            "true"
        );
        assert_eq!(
            runtime
                .eval_text("(protocol-call IFind has? [10 20] 1)")
                .unwrap(),
            "true"
        );
        assert_eq!(
            runtime
                .eval_text("(protocol-call IFind has? [10 20] 10)")
                .unwrap(),
            "false"
        );
        assert_eq!(
            runtime
                .eval_text(r#"(protocol-call IAssoc assoc {"a" 9} "b" 10)"#)
                .unwrap(),
            r#"{"a" 9 "b" 10}"#
        );
        assert_eq!(
            runtime
                .eval_text(r#"(protocol-call IConj conj [1] 2)"#)
                .unwrap(),
            "[1 2]"
        );
        assert_eq!(
            runtime
                .eval_text(r#"(protocol-call IDissoc dissoc {"a" 9 "b" 10} "a")"#)
                .unwrap(),
            r#"{"b" 10}"#
        );
        runtime
            .protocols
            .register("ITest", "echo", protocol_identity);
        assert_eq!(
            runtime.eval_text("(protocol-call ITest echo 7)").unwrap(),
            "7"
        );
        runtime
            .protocols
            .register("IIter", "iter", protocol_custom_iterator);
        assert_eq!(runtime.eval_text("(iter-next (iter 99))").unwrap(), "7");
        assert!(runtime.has_protocol_method("IAssoc", "assoc"));
        assert!(runtime
            .eval_text("(protocol-call Missing nope 1)")
            .unwrap_err()
            .contains("missing protocol method"));
    }

    #[test]
    fn protocol_registry_dispatches_by_protocol_and_method() {
        let mut registry = core::ProtocolRegistry::new();
        registry.register("IIdentity", "identity", protocol_identity);
        assert!(core::ProtocolRegistry::core().contains("IAssoc", "assoc"));
        assert!(registry.contains("IIdentity", "identity"));
        assert_eq!(
            registry
                .invoke("IIdentity", "identity", &[core::Value::Number(7)])
                .unwrap(),
            core::Value::Number(7)
        );
        assert!(registry
            .invoke("IIdentity", "missing", &[])
            .unwrap_err()
            .contains("missing protocol method"));
        assert_eq!(
            core::receiver_category(&core::Value::Vector(Default::default())),
            "vector"
        );
    }

    #[test]
    fn functions_support_variadic_rest_parameters() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("((fn [x & rest] (+ x (count rest))) 40 1 2)")
                .unwrap(),
            "42"
        );
        assert_eq!(
            runtime
                .eval_text("(do (defn collect [x & rest] (count rest)) (collect 1 2 3 4))")
                .unwrap(),
            "3"
        );
        assert!(runtime
            .eval_text("((fn [x & rest] x))")
            .unwrap_err()
            .contains("at least 1"));
    }

    #[test]
    fn throw_and_try_catch_finally_are_host_neutral() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(try (throw :failed) (catch error error))")
                .unwrap(),
            "\"thrown: :failed\""
        );
        assert_eq!(runtime.eval_text("(try 42 (finally 0))").unwrap(), "42");
        assert_eq!(
            runtime
                .eval_text("(try (throw :failed) (catch error (str error :handled)))")
                .unwrap(),
            "\"thrown: :failed:handled\""
        );
        assert!(runtime
            .eval_text("(throw :failed)")
            .unwrap_err()
            .contains("thrown: :failed"));
    }

    #[test]
    fn def_binds_values_in_the_current_environment() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(do (def answer 41) (+ answer 1))")
                .unwrap(),
            "42"
        );
        assert_eq!(
            runtime
                .eval_text("(do (def answer 42) (deref (var answer)))")
                .unwrap(),
            "42"
        );
        assert!(runtime
            .eval_text("(deref 42)")
            .unwrap_err()
            .contains("deref expects a var"));
        assert_eq!(
            runtime
                .eval_text("(do (def answer 1) (def answer 42) answer)")
                .unwrap(),
            "42"
        );
        assert!(runtime
            .eval_text("(def 1 2)")
            .unwrap_err()
            .contains("def name must be a symbol"));
    }

    #[test]
    fn vars_preserve_identity_and_support_root_mutation() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(do (def answer 1) (= (var answer) (var answer)))")
                .unwrap(),
            "true"
        );
        assert_eq!(
            runtime
                .eval_text("(do (def answer 1) (set! answer 42) (deref (var answer)))")
                .unwrap(),
            "42"
        );
        assert_eq!(
            runtime
                .eval_text(
                    "(do (def answer 1) (let (v (var answer)) (do (set! answer 7) (deref v))))"
                )
                .unwrap(),
            "7"
        );
        assert_eq!(runtime.eval_text("(do (def answer 1) (defn add [x y] (+ x y)) (alter-var-root (var answer) add 40) answer)").unwrap(), "41");
        assert_eq!(
            runtime.eval_text("(set! missing 1)").unwrap_err(),
            "unbound var: missing"
        );
    }

    #[test]
    fn functions_capture_lexical_values_and_support_defn() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("((fn [x] (+ x 1)) 41)").unwrap(), "42");
        assert_eq!(
            runtime
                .eval_text("(let (inc (fn [x] (+ x 1))) (inc 41))")
                .unwrap(),
            "42"
        );
        assert_eq!(
            runtime
                .eval_text("(do (defn add1 [x] (+ x 1)) (add1 41))")
                .unwrap(),
            "42"
        );
        assert_eq!(runtime.eval_text("(do (defn factorial [n] (if (<= n 1) 1 (* n (factorial (dec n))))) (factorial 5))").unwrap(), "120");
        assert_eq!(
            runtime
                .eval_text("(let (x 40) (let (f (fn [y] (+ x y))) (f 2)))")
                .unwrap(),
            "42"
        );
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
        assert_eq!(
            runtime.eval_text("(bit-shift-left 1 31)").unwrap(),
            "-2147483648"
        );
        assert!(runtime
            .eval_text("(bit-shift-left 1 -1)")
            .unwrap_err()
            .contains("distance must be in the range 0..31"));
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
        assert_eq!(runtime.eval_text("(true? true)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(false? false)").unwrap(), "true");
    }

    #[test]
    fn core_sequence_navigation_ranges_and_quantifiers() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(second [10 20 30])").unwrap(), "20");
        assert_eq!(runtime.eval_text("(not-empty [])").unwrap(), "nil");
        assert_eq!(runtime.eval_text("(not-empty [1])").unwrap(), "[1]");
        assert_eq!(runtime.eval_text("(range 3)").unwrap(), "<seq>");
        assert_eq!(
            runtime
                .eval_text("(seq? (map (fn [x] (+ x 1)) [1 2 3]))")
                .unwrap(),
            "true"
        );
        assert_eq!(
            runtime
                .eval_text("(first (map (fn [x] (+ x 1)) [1 2 3]))")
                .unwrap(),
            "2"
        );
        assert_eq!(runtime.eval_text("(count (range 2 5))").unwrap(), "3");
        assert_eq!(runtime.eval_text("(count (repeat 4 :x))").unwrap(), "4");
        assert_eq!(
            runtime
                .eval_text("(every? (fn [x] (pos? x)) [1 2 3])")
                .unwrap(),
            "true"
        );
        assert_eq!(
            runtime
                .eval_text("(any? (fn [x] (= x 2)) [1 2 3])")
                .unwrap(),
            "true"
        );
    }

    #[test]
    fn map_and_zip_support_multiple_collections() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(nth (map (fn [x y] (+ x y)) [1 2] [10 20]) 1)")
                .unwrap(),
            "22"
        );
        assert_eq!(
            runtime
                .eval_text("(count (map (fn [x y z] (+ x (+ y z))) [1 2] [10 20] [100 200]))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(nth (zip [1 2] [:a :b] [true false]) 0)")
                .unwrap(),
            "[1 :a true]"
        );
        assert_eq!(
            runtime.eval_text("(count (zip [1 2 3] [:a :b]))").unwrap(),
            "2"
        );
    }

    #[test]
    fn lazy_iterator_generators_are_bounded_by_consumers() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("(count (take 4 (repeat :x)))").unwrap(),
            "4"
        );
        assert_eq!(
            runtime.eval_text("(first (drop 3 (repeat :x)))").unwrap(),
            ":x"
        );
        assert!(runtime
            .eval_text("(count (repeat :x))")
            .unwrap_err()
            .contains("finite collection"));
        assert_eq!(
            runtime
                .eval_text("(count (take 3 (repeatedly (constantly 7))))")
                .unwrap(),
            "3"
        );
        assert_eq!(
            runtime
                .eval_text("(count (take 5 (iterate (fn [x] (+ x 2)) 0)))")
                .unwrap(),
            "5"
        );
        assert_eq!(
            runtime
                .eval_text(
                    "(count (take 3 (take-while (fn [x] (< x 10)) (iterate (fn [x] (+ x 2)) 0))))"
                )
                .unwrap(),
            "3"
        );
        assert_eq!(
            runtime
                .eval_text(
                    "(first (take 2 (drop-while (fn [x] (< x 4)) (iterate (fn [x] (+ x 2)) 0))))"
                )
                .unwrap(),
            "4"
        );
        assert_eq!(
            runtime
                .eval_text("(nth (take 4 (map (fn [x] (* x 2)) (iterate (fn [x] (+ x 1)) 0))) 3)")
                .unwrap(),
            "6"
        );
        assert_eq!(
            runtime
                .eval_text(
                    "(first (take 2 (filter (fn [x] (even? x)) (iterate (fn [x] (+ x 1)) 0))))"
                )
                .unwrap(),
            "0"
        );
        assert_eq!(
            runtime
                .eval_text("(nth (take 4 (mapcat (fn [x] [x x]) (iterate (fn [x] (+ x 1)) 0))) 3)")
                .unwrap(),
            "1"
        );
        assert_eq!(runtime.eval_text("(first (take 2 (keep (fn [x] (if (even? x) (* x 10) nil)) (iterate (fn [x] (+ x 1)) 0))))").unwrap(), "0");
        assert_eq!(
            runtime
                .eval_text("(nth (take 3 (zip (iterate (fn [x] (+ x 1)) 0) (repeat :x))) 2)")
                .unwrap(),
            "[2 :x]"
        );
        assert_eq!(
            runtime
                .eval_text("(nth (take 4 (interleave (iterate (fn [x] (+ x 1)) 0) (repeat :x))) 3)")
                .unwrap(),
            ":x"
        );
        assert_eq!(
            runtime
                .eval_text("(nth (take 3 (partition-all 2 (iterate (fn [x] (+ x 1)) 0))) 2)")
                .unwrap(),
            "[4 5]"
        );
        assert_eq!(
            runtime
                .eval_text("(nth (take 2 (partition 2 (iterate (fn [x] (+ x 1)) 0))) 1)")
                .unwrap(),
            "[2 3]"
        );
        assert_eq!(
            runtime
                .eval_text("(first (take 4 (iterate (fn [x] (+ x 2)) 0)))")
                .unwrap(),
            "0"
        );
        assert_eq!(runtime.eval_text("(second (repeat :x))").unwrap(), ":x");
        assert_eq!(
            runtime
                .eval_text("(first (rest (iterate (fn [x] (+ x 1)) 0)))")
                .unwrap(),
            "1"
        );
        assert_eq!(
            runtime
                .eval_text("(nth (take 4 (iterate (fn [x] (+ x 2)) 0)) 3)")
                .unwrap(),
            "6"
        );
    }

    #[test]
    fn function_combinators_capture_values_and_functions() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("((constantly 42) 1 2 3)").unwrap(), "42");
        assert_eq!(
            runtime
                .eval_text("((complement (fn [x] (> x 2))) 1)")
                .unwrap(),
            "true"
        );
        assert_eq!(
            runtime
                .eval_text("((comp2 (fn [x] (+ x 1)) (fn [x] (* x 2))) 20)")
                .unwrap(),
            "41"
        );
        assert_eq!(
            runtime
                .eval_text("((comp3 (fn [x] (+ x 1)) (fn [x] (+ x 1)) (fn [x] (+ x 1))) 39)")
                .unwrap(),
            "42"
        );
    }

    #[test]
    fn nested_associative_helpers_match_l0_shapes() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("(get-in {:a {:b 42}} [:a :b])").unwrap(),
            "42"
        );
        assert_eq!(
            runtime
                .eval_text("(get-in (object :a (object :b 42)) [:a :b])")
                .unwrap(),
            "42"
        );
        assert_eq!(runtime.eval_text("(get (object :a 7) :a)").unwrap(), "7");
        assert_eq!(
            runtime
                .eval_text("(get-in {:a {:b 42}} [:a :missing])")
                .unwrap(),
            "nil"
        );
        assert_eq!(
            runtime
                .eval_text("(get-in (assoc-in {} [:a :b] 42) [:a :b])")
                .unwrap(),
            "42"
        );
        assert_eq!(runtime.eval_text("(get {:a 3} :a)").unwrap(), "3");
        assert_eq!(
            runtime
                .eval_text("(get (update {:a 3} :a (fn [x] (+ x 2))) :a)")
                .unwrap(),
            "5"
        );
        assert_eq!(
            runtime
                .eval_text("(get-in (update-in {:a {:b 3}} [:a :b] (fn [x y] (+ x y)) 4) [:a :b])")
                .unwrap(),
            "7"
        );
        assert_eq!(
            runtime.eval_text("(get (assoc {} :a 1 :b 2) :b)").unwrap(),
            "2"
        );
    }

    #[test]
    fn opaque_extensions_use_compact_tagged_display() {
        let value = core::Value::Extension(core::ExtensionValue {
            provider: "math.tensor".into(),
            type_name: "tensor".into(),
            handle: 42,
        });
        assert_eq!(value.display(), "#ht[:handle 42]");
    }
    #[test]
    fn iterator_combinators_cover_core_shapes() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(count (take-while (fn [x] (< x 3)) (range 5)))")
                .unwrap(),
            "3"
        );
        assert_eq!(
            runtime
                .eval_text("(count (drop-while (fn [x] (< x 3)) (range 5)))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(count (mapcat (fn [x] [x x]) [1 2]))")
                .unwrap(),
            "4"
        );
        assert_eq!(
            runtime
                .eval_text("(count (keep (fn [x] (if (even? x) (* x 10) nil)) [1 2 3 4]))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime
                .eval_text("(count (partition-all 2 [1 2 3]))")
                .unwrap(),
            "2"
        );
        assert_eq!(
            runtime.eval_text("(count (partition 2 [1 2 3]))").unwrap(),
            "1"
        );
        assert_eq!(
            runtime.eval_text("(count (interpose :x [1 2 3]))").unwrap(),
            "5"
        );
        assert_eq!(
            runtime
                .eval_text("(count (interleave [1 2] [:a :b]))")
                .unwrap(),
            "4"
        );
        assert_eq!(
            runtime
                .eval_text("(count (partition-pair [1 2 3]))")
                .unwrap(),
            "1"
        );
    }

    #[test]
    fn arithmetic() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(+ 19 23)").unwrap(), "42");
    }

    #[test]
    fn recur_cannot_escape_loop_or_function_boundaries() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("(recur 1)").unwrap_err(),
            "recur must be inside loop"
        );
        assert_eq!(
            runtime.eval_text("((fn [] (recur 1)))").unwrap_err(),
            "recur must be inside loop"
        );
    }

    #[test]
    fn loop_supports_binding_vectors_and_multiple_recur_values() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(loop [x 0 y 1] (if (< x 4) (recur (+ x 1) (+ y x)) y))")
                .unwrap(),
            "7"
        );
        assert!(runtime
            .eval_text("(loop [x 0 y 1] (recur 2))")
            .unwrap_err()
            .contains("loop recur arity mismatch"));
    }

    #[test]
    fn loop_and_recur_support_tail_recursive_bootstrap_forms() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime
                .eval_text("(loop (x 0) (if (< x 5) (recur (+ x 1)) x))")
                .unwrap(),
            "5"
        );
        assert_eq!(
            runtime
                .eval_text("(loop (x 1) (do (if (< x 3) (recur (* x 2)) x)))")
                .unwrap(),
            "4"
        );
    }

    #[test]
    fn let_accepts_binding_vectors_and_multiple_sequential_pairs() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("(let [x 19 y 23] (+ x y))").unwrap(),
            "42"
        );
        assert_eq!(
            runtime.eval_text("(let (x 19 y (+ x 23)) y)").unwrap(),
            "42"
        );
        assert!(runtime
            .eval_text("(let [x 1 y] y)")
            .unwrap_err()
            .contains("name/value pairs"));
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

    #[cfg(not(target_arch = "wasm32"))]
    #[test]
    fn native_error_traces_are_opt_in_and_nested() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_native("(+ 19 23)").unwrap(), "42");
        assert_eq!(
            runtime.eval_native("unknown").unwrap_err(),
            "unbound symbol: unknown"
        );
        let error = runtime
            .eval_native_traced("(do (defn inner [] (/ 1 0)) (defn outer [] (inner)) (outer))")
            .unwrap_err();
        assert!(error.contains("[hara stack]"));
        assert!(error.contains("at inner"));
        assert!(error.contains("at outer"));
        assert_eq!(error.matches("[hara stack]").count(), 1);
    }
    #[test]
    fn runtime_metadata_round_trips_through_protocols_and_reader_literals() {
        let mut runtime = Runtime::new();
        assert_eq!(
            runtime.eval_text("(protocol-call ILookup lookup (protocol-call IObjType meta (protocol-call IObjType with-meta [1] {:doc \"vector\"})) :doc)").unwrap(),
            "\"vector\""
        );
        assert_eq!(
            runtime.eval_text("(protocol-call ILookup lookup (protocol-call IObjType meta (quote ^{:doc \"quoted\"} [1])) :doc)").unwrap(),
            "\"quoted\""
        );
    }
    #[test]
    fn typed_vars_preserve_definition_metadata_and_dynamic_binding_scope() {
        let mut runtime = Runtime::new();
        assert_eq!(runtime.eval_text("(do (def ^:dynamic *answer* 1) (binding [*answer* 42] (binding [*answer* 43] *answer*)))").unwrap(), "43");
        assert_eq!(runtime.eval_text("*answer*").unwrap(), "1");
        assert_eq!(runtime.eval_text("(protocol-call ILookup lookup (protocol-call IObjType meta (var *answer*)) :dynamic)").unwrap(), "true");
        assert_eq!(runtime.eval_text("(do (def ^{:doc \"answer doc\"} answer 42) (protocol-call ILookup lookup (protocol-call IObjType meta (var answer)) :doc))").unwrap(), "\"answer doc\"");
        assert!(runtime
            .eval_text("(do (def plain 1) (binding [plain 2] plain))")
            .unwrap_err()
            .contains("dynamic Var"));
        assert!(runtime
            .eval_text("(do (def ^:dynamic *left* 1) (binding [*left* 2 plain 3] *left*))")
            .is_err());
        assert_eq!(runtime.eval_text("*left*").unwrap(), "1");
    }
}

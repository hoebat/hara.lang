//! Strict metadata and host boundary for runtime-generated WASM namespaces.

use std::collections::{HashMap, HashSet};
use std::rc::Rc;

pub use crate::core::{Promise, PromiseState, Value};
use crate::kernel::{parse, Form};

const MANIFEST_FIELDS: &[&str] = &[
    "namespace",
    "version",
    "provider",
    "module",
    "abi",
    "exports",
    "capabilities",
    "host-calls",
    "handles",
];
const EXPORT_FIELDS: &[&str] = &["args", "returns", "async"];
const HANDLE_FIELDS: &[&str] = &["tag"];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WasmAbi {
    CoreV1,
    HtaV1,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExtensionExport {
    pub arguments: Vec<String>,
    pub returns: String,
    pub asynchronous: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExtensionManifest {
    pub namespace: String,
    pub version: String,
    pub module: String,
    pub abi: WasmAbi,
    pub exports: Vec<(String, ExtensionExport)>,
    pub capabilities: Vec<String>,
    pub host_calls: HashMap<String, Vec<String>>,
    pub handle_tags: HashMap<String, String>,
}

impl ExtensionManifest {
    pub fn parse(source: &str, origin: &str) -> Result<Self, String> {
        let form = parse(source)
            .map_err(|error| malformed(origin, format!("cannot parse manifest: {error}")))?;
        let entries = map(&form, origin, "manifest")?;
        reject_unknown(entries, MANIFEST_FIELDS, origin, "manifest")?;
        let namespace = named_string(entries, "namespace", origin)?;
        if !valid_namespace(&namespace) {
            return Err(malformed(
                origin,
                "namespace must be a qualified lower-case symbol",
            ));
        }
        let version = named_string(entries, "version", origin)?;
        if named_keyword(entries, "provider", origin)? != "wasm" {
            return Err(malformed(origin, "provider must be :wasm"));
        }
        let module = named_string(entries, "module", origin)?;
        if module.starts_with('/') || module.contains("..") || !module.ends_with(".wasm") {
            return Err(malformed(
                origin,
                "module must be a safe relative .wasm file",
            ));
        }
        let abi = match named_keyword(entries, "abi", origin)?.as_str() {
            "core-v1" => WasmAbi::CoreV1,
            "hta-v1" => WasmAbi::HtaV1,
            value => return Err(malformed(origin, format!("unsupported WASM ABI :{value}"))),
        };
        let exports = parse_exports(required(entries, "exports", origin)?, origin)?;
        let capabilities = keyword_vector(
            required(entries, "capabilities", origin)?,
            origin,
            "capabilities",
        )?;
        let host_calls = optional(entries, "host-calls")
            .map_or_else(|| Ok(HashMap::new()), |form| parse_host_calls(form, origin))?;
        let handle_tags = optional(entries, "handles")
            .map_or_else(|| Ok(HashMap::new()), |form| parse_handles(form, origin))?;
        Ok(Self {
            namespace,
            version,
            module,
            abi,
            exports,
            capabilities,
            host_calls,
            handle_tags,
        })
    }

    pub fn permits_host_call(&self, service: &str, method: &str) -> bool {
        self.host_calls.get(service).map_or(false, |methods| {
            methods.iter().any(|candidate| candidate == method)
        })
    }
}

pub trait WasmExtensionProvider {
    fn supports(&self, abi: WasmAbi) -> bool;

    fn capabilities(&self) -> Vec<String> {
        Vec::new()
    }

    fn start(&self, manifest: &ExtensionManifest) -> Result<(), String>;

    fn invoke(
        &self,
        manifest: &ExtensionManifest,
        export: &str,
        arguments: &[Value],
    ) -> Result<Value, String>;

    fn cancel(&self, manifest: &ExtensionManifest, request: u64) -> Result<(), String>;

    fn shutdown(&self, manifest: &ExtensionManifest);
}

struct ExtensionSession {
    manifest: ExtensionManifest,
    provider: Rc<dyn WasmExtensionProvider>,
}

impl Drop for ExtensionSession {
    fn drop(&mut self) {
        self.provider.shutdown(&self.manifest);
    }
}

#[derive(Clone)]
pub struct ExtensionBinding {
    pub namespace: String,
    pub name: String,
    pub specification: ExtensionExport,
    session: Rc<ExtensionSession>,
}

impl ExtensionBinding {
    pub fn invoke(&self, arguments: &[Value]) -> Result<Value, String> {
        if arguments.len() != self.specification.arguments.len() {
            return Err(format!(
                "extension/arity: {}/{} expects {} arguments, got {}",
                self.namespace,
                self.name,
                self.specification.arguments.len(),
                arguments.len()
            ));
        }
        let result = self
            .session
            .provider
            .invoke(&self.session.manifest, &self.name, arguments)?;
        if self.specification.asynchronous && !matches!(result, Value::Promise(_)) {
            return Err(format!(
                "extension/protocol: asynchronous export {}/{} must return a promise",
                self.namespace, self.name
            ));
        }
        Ok(result)
    }
}

pub struct WasmExtension {
    manifest: ExtensionManifest,
    provider: Rc<dyn WasmExtensionProvider>,
    session: Option<Rc<ExtensionSession>>,
}

impl WasmExtension {
    pub fn new<P: WasmExtensionProvider + 'static>(
        manifest: ExtensionManifest,
        provider: P,
    ) -> Result<Self, String> {
        if !provider.supports(manifest.abi) {
            return Err(format!(
                "extension/unsupported: provider does not support {:?}",
                manifest.abi
            ));
        }
        Ok(Self {
            manifest,
            provider: Rc::new(provider),
            session: None,
        })
    }

    pub fn namespace(&self) -> &str {
        &self.manifest.namespace
    }

    pub fn require(&mut self) -> Result<Vec<ExtensionBinding>, String> {
        if self.session.is_none() {
            let available = self
                .provider
                .capabilities()
                .into_iter()
                .collect::<HashSet<_>>();
            if let Some(capability) = self
                .manifest
                .capabilities
                .iter()
                .find(|capability| !available.contains(*capability))
            {
                return Err(format!(
                    "extension/denied: {} requires capability :{}",
                    self.manifest.namespace, capability
                ));
            }
            self.provider
                .start(&self.manifest)
                .map_err(|error| format!("extension/start: {error}"))?;
            self.session = Some(Rc::new(ExtensionSession {
                manifest: self.manifest.clone(),
                provider: self.provider.clone(),
            }));
        }
        let session = self.session.as_ref().expect("session started").clone();
        Ok(self
            .manifest
            .exports
            .iter()
            .map(|(name, specification)| ExtensionBinding {
                namespace: self.manifest.namespace.clone(),
                name: name.clone(),
                specification: specification.clone(),
                session: session.clone(),
            })
            .collect())
    }

    pub fn cancel(&self, request: u64) -> Result<(), String> {
        let session = self.session.as_ref().ok_or_else(|| {
            format!(
                "extension/not-started: namespace has not been required: {}",
                self.manifest.namespace
            )
        })?;
        session
            .provider
            .cancel(&session.manifest, request)
            .map_err(|error| format!("extension/cancel: {error}"))
    }
}

fn parse_exports(form: &Form, origin: &str) -> Result<Vec<(String, ExtensionExport)>, String> {
    let entries = map(form, origin, "exports")?;
    if entries.is_empty() {
        return Err(malformed(origin, "exports cannot be empty"));
    }
    entries
        .iter()
        .map(|(name, specification)| {
            let name = string(name, origin, "export name")?.to_owned();
            let specification = map(specification, origin, "export specification")?;
            reject_unknown(
                specification,
                EXPORT_FIELDS,
                origin,
                &format!("export {name}"),
            )?;
            let arguments = keyword_vector(
                required(specification, "args", origin)?,
                origin,
                "export args",
            )?;
            let returns = keyword(
                required(specification, "returns", origin)?,
                origin,
                "export returns",
            )?
            .to_owned();
            let asynchronous = match optional(specification, "async") {
                None => false,
                Some(Form::Bool(value)) => *value,
                Some(_) => return Err(malformed(origin, "export async must be boolean")),
            };
            Ok((
                name,
                ExtensionExport {
                    arguments,
                    returns,
                    asynchronous,
                },
            ))
        })
        .collect()
}

fn parse_host_calls(form: &Form, origin: &str) -> Result<HashMap<String, Vec<String>>, String> {
    map(form, origin, "host-calls")?
        .iter()
        .map(|(service, methods)| {
            let service = string(service, origin, "host-call service")?.to_owned();
            let methods = vector(methods, origin, "host-call methods")?
                .iter()
                .map(|method| string(method, origin, "host-call method").map(str::to_owned))
                .collect::<Result<Vec<_>, _>>()?;
            Ok((service, methods))
        })
        .collect()
}

fn parse_handles(form: &Form, origin: &str) -> Result<HashMap<String, String>, String> {
    map(form, origin, "handles")?
        .iter()
        .map(|(type_name, specification)| {
            let type_name = string(type_name, origin, "handle type")?.to_owned();
            let specification = map(specification, origin, "handle specification")?;
            reject_unknown(
                specification,
                HANDLE_FIELDS,
                origin,
                &format!("handle {type_name}"),
            )?;
            let tag = match required(specification, "tag", origin)? {
                Form::Symbol(tag) if valid_tag(tag) => tag.clone(),
                _ => return Err(malformed(origin, "handle tag must be a lower-case symbol")),
            };
            Ok((type_name, tag))
        })
        .collect()
}

fn valid_namespace(value: &str) -> bool {
    value.contains('.') && value.split('.').all(valid_component)
}

fn valid_tag(value: &str) -> bool {
    value.split('.').all(valid_component)
}

fn valid_component(value: &str) -> bool {
    value
        .chars()
        .next()
        .map_or(false, |first| first.is_ascii_lowercase())
        && value
            .chars()
            .all(|ch| ch.is_ascii_lowercase() || ch.is_ascii_digit() || ch == '-')
}

fn map<'a>(form: &'a Form, origin: &str, field: &str) -> Result<&'a [(Form, Form)], String> {
    match form {
        Form::Map(entries) => Ok(entries),
        _ => Err(malformed(origin, format!("{field} must be a map"))),
    }
}

fn vector<'a>(form: &'a Form, origin: &str, field: &str) -> Result<&'a [Form], String> {
    match form {
        Form::Vector(values) => Ok(values),
        _ => Err(malformed(origin, format!("{field} must be a vector"))),
    }
}

fn string<'a>(form: &'a Form, origin: &str, field: &str) -> Result<&'a str, String> {
    match form {
        Form::String(value) if !value.is_empty() => Ok(value),
        _ => Err(malformed(
            origin,
            format!("{field} must be a non-empty string"),
        )),
    }
}

fn keyword<'a>(form: &'a Form, origin: &str, field: &str) -> Result<&'a str, String> {
    match form {
        Form::Keyword(value) => Ok(value),
        _ => Err(malformed(origin, format!("{field} must be a keyword"))),
    }
}

fn keyword_vector(form: &Form, origin: &str, field: &str) -> Result<Vec<String>, String> {
    vector(form, origin, field)?
        .iter()
        .map(|form| keyword(form, origin, field).map(str::to_owned))
        .collect()
}

fn key(form: &Form) -> Option<&str> {
    match form {
        Form::Keyword(value) | Form::Symbol(value) | Form::String(value) => Some(value),
        _ => None,
    }
}

fn required<'a>(entries: &'a [(Form, Form)], name: &str, origin: &str) -> Result<&'a Form, String> {
    optional(entries, name)
        .ok_or_else(|| malformed(origin, format!("missing required field {name}")))
}

fn optional<'a>(entries: &'a [(Form, Form)], name: &str) -> Option<&'a Form> {
    entries
        .iter()
        .find(|(candidate, _)| key(candidate) == Some(name))
        .map(|(_, value)| value)
}

fn named_string(entries: &[(Form, Form)], name: &str, origin: &str) -> Result<String, String> {
    string(required(entries, name, origin)?, origin, name).map(str::to_owned)
}

fn named_keyword(entries: &[(Form, Form)], name: &str, origin: &str) -> Result<String, String> {
    keyword(required(entries, name, origin)?, origin, name).map(str::to_owned)
}

fn reject_unknown(
    entries: &[(Form, Form)],
    allowed: &[&str],
    origin: &str,
    scope: &str,
) -> Result<(), String> {
    let mut seen = HashSet::new();
    for (candidate, _) in entries {
        let Some(name) = key(candidate) else {
            return Err(malformed(origin, format!("{scope} keys must be named")));
        };
        if !allowed.contains(&name) {
            return Err(malformed(origin, format!("unknown {scope} field: {name}")));
        }
        if !seen.insert(name) {
            return Err(malformed(
                origin,
                format!("duplicate {scope} field: {name}"),
            ));
        }
    }
    Ok(())
}

fn malformed(origin: &str, message: impl AsRef<str>) -> String {
    format!("extension/malformed {origin}: {}", message.as_ref())
}

#[cfg(test)]
mod tests {
    use super::{ExtensionManifest, WasmAbi};

    const MANIFEST: &str = r#"
      {:namespace "crypto.hash"
       :version "0.1.0"
       :provider :wasm
       :module "hash.wasm"
       :abi :hta-v1
       :exports {"digest" {:args [:bytes] :returns :bytes :async true}}
       :host-calls {"crypto.random" ["fill"]}
       :handles {"digest" {:tag crypto}}
       :capabilities [:random]}"#;

    #[test]
    fn parses_the_wasm_manifest_contract() {
        let manifest = ExtensionManifest::parse(MANIFEST, "fixture").unwrap();
        assert_eq!(manifest.namespace, "crypto.hash");
        assert_eq!(manifest.abi, WasmAbi::HtaV1);
        assert!(manifest.exports[0].1.asynchronous);
        assert!(manifest.permits_host_call("crypto.random", "fill"));
        assert_eq!(manifest.handle_tags["digest"], "crypto");
    }

    #[test]
    fn rejects_non_wasm_unknown_duplicate_unsafe_and_unsupported_manifests() {
        for source in [
            MANIFEST.replace(":wasm", ":pod"),
            MANIFEST.replace(":capabilities [:random]", ":capabilities [] :extra true"),
            MANIFEST.replace(":version \"0.1.0\"", ":version \"0.1.0\" :version \"2\""),
            MANIFEST.replace("hash.wasm", "../hash.wasm"),
            MANIFEST.replace(":hta-v1", ":unknown-v1"),
        ] {
            assert!(ExtensionManifest::parse(&source, "fixture")
                .unwrap_err()
                .starts_with("extension/malformed fixture:"));
        }
    }
}

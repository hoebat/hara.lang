#![cfg(not(target_arch = "wasm32"))]

use std::cell::RefCell;
use std::io::{BufReader, BufWriter, Read, Write};
use std::path::Path;
use std::process::{Child, ChildStdin, ChildStdout, Command, Stdio};

use crate::core::{Promise, Value};
use crate::extension::{ExtensionManifest, WasmAbi, WasmExtensionProvider};
use crate::hta;

const MAX_FRAME_BYTES: usize = 64 * 1024 * 1024;

struct Session {
    child: Child,
    input: BufReader<ChildStdout>,
    output: BufWriter<ChildStdin>,
    next_request: u64,
}

pub struct ProcessExtensionProvider {
    module: std::path::PathBuf,
    session: RefCell<Option<Session>>,
}

impl ProcessExtensionProvider {
    pub fn new(module: std::path::PathBuf) -> Self {
        Self {
            module,
            session: RefCell::new(None),
        }
    }

    fn write(session: &mut Session, value: &Value) -> Result<(), String> {
        let bytes = hta::encode(value)?;
        if bytes.len() > MAX_FRAME_BYTES {
            return Err("hta/frame-too-large".into());
        }
        session
            .output
            .write_all(&(bytes.len() as u32).to_be_bytes())
            .and_then(|_| session.output.write_all(&bytes))
            .and_then(|_| session.output.flush())
            .map_err(|error| format!("hta/process-write-failed: {error}"))
    }

    fn read(session: &mut Session) -> Result<Value, String> {
        let mut header = [0_u8; 4];
        session
            .input
            .read_exact(&mut header)
            .map_err(|error| format!("hta/process-closed: {error}"))?;
        let length = u32::from_be_bytes(header) as usize;
        if length == 0 || length > MAX_FRAME_BYTES {
            return Err(format!("hta/process-frame-size: {length}"));
        }
        let mut bytes = vec![0; length];
        session
            .input
            .read_exact(&mut bytes)
            .map_err(|error| format!("hta/process-closed: {error}"))?;
        hta::decode(&bytes)
    }

    fn list(values: impl IntoIterator<Item = Value>) -> Value {
        Value::List(values.into_iter().collect())
    }

    fn response(frame: Value, request: u64) -> Result<Value, String> {
        let values = match frame {
            Value::Vector(values) => values.iter().cloned().collect::<Vec<_>>(),
            Value::List(values) => values.iter().cloned().collect::<Vec<_>>(),
            _ => return Err("hta/process-frame-malformed".into()),
        };
        if values.len() < 3 || values[1] != Value::Number(request as i64) {
            return Err("hta/process-frame-malformed".into());
        }
        match &values[0] {
            Value::String(kind) if kind == "result" => Ok(values[2].clone()),
            Value::String(kind) if kind == "error" => {
                Err(format!("hta/remote-error: {}", values[2].display()))
            }
            _ => Err("hta/process-event-unknown".into()),
        }
    }
}

impl WasmExtensionProvider for ProcessExtensionProvider {
    fn supports(&self, abi: WasmAbi) -> bool {
        abi == WasmAbi::HtaV1
    }
    fn capabilities(&self) -> Vec<String> {
        vec!["process".into()]
    }

    fn start(&self, manifest: &ExtensionManifest) -> Result<(), String> {
        if manifest
            .capabilities
            .iter()
            .any(|capability| capability != "process")
        {
            return Err("extension/capability-invalid: unsupported managed Node capability".into());
        }
        let node = std::env::var("HARA_NODE").unwrap_or_else(|_| "node".into());
        let directory = self.module.parent().unwrap_or_else(|| Path::new("."));
        let mut child = Command::new(node)
            .arg(&self.module)
            .current_dir(directory)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::inherit())
            .spawn()
            .map_err(|error| {
                format!("hta/process-start-failed: {} ({error})", manifest.namespace)
            })?;
        let input = child
            .stdout
            .take()
            .ok_or_else(|| "hta/process-start-failed: missing stdout".to_owned())?;
        let output = child
            .stdin
            .take()
            .ok_or_else(|| "hta/process-start-failed: missing stdin".to_owned())?;
        let mut session = Session {
            child,
            input: BufReader::new(input),
            output: BufWriter::new(output),
            next_request: 0,
        };
        let exports = Value::Vector(
            manifest
                .exports
                .iter()
                .map(|(name, _)| Value::String(name.clone()))
                .collect::<Vec<_>>()
                .into(),
        );
        Self::write(
            &mut session,
            &Self::list([
                Value::String("handshake".into()),
                Value::Number(1),
                Value::String(manifest.namespace.clone()),
                exports,
            ]),
        )?;
        let ready = Self::read(&mut session)?;
        let values = match &ready {
            Value::List(values) => values.iter().collect::<Vec<_>>(),
            Value::Vector(values) => values.iter().collect(),
            _ => Vec::new(),
        };
        if values.len() < 2
            || values[0] != &Value::String("ready".into())
            || values[1] != &Value::Number(1)
        {
            let _ = session.child.kill();
            return Err(format!("hta/handshake-invalid: {}", ready.display()));
        }
        *self.session.borrow_mut() = Some(session);
        Ok(())
    }

    fn invoke(
        &self,
        manifest: &ExtensionManifest,
        export: &str,
        arguments: &[Value],
    ) -> Result<Value, String> {
        let mut session = self.session.borrow_mut();
        let session = session
            .as_mut()
            .ok_or_else(|| format!("hta/process-unavailable: {}", manifest.namespace))?;
        session.next_request += 1;
        let request = session.next_request;
        Self::write(
            session,
            &Self::list([
                Value::String("call".into()),
                Value::Number(request as i64),
                Value::String(export.into()),
                Value::Vector(arguments.to_vec().into()),
            ]),
        )?;
        let promise = Promise::new();
        match Self::read(session).and_then(|frame| Self::response(frame, request)) {
            Ok(value) => {
                promise.resolve(value);
            }
            Err(error) => {
                promise.reject(error);
            }
        }
        Ok(Value::Promise(promise))
    }

    fn cancel(&self, _manifest: &ExtensionManifest, request: u64) -> Result<(), String> {
        let mut session = self.session.borrow_mut();
        let session = session
            .as_mut()
            .ok_or_else(|| "hta/process-unavailable".to_owned())?;
        Self::write(
            session,
            &Self::list([
                Value::String("cancel".into()),
                Value::Number(request as i64),
            ]),
        )
    }

    fn shutdown(&self, _manifest: &ExtensionManifest) {
        if let Some(mut session) = self.session.borrow_mut().take() {
            let _ = Self::write(
                &mut session,
                &Self::list([Value::String("shutdown".into())]),
            );
            let _ = session.child.kill();
            let _ = session.child.wait();
        }
    }
}

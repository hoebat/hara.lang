# hara.lang

Hara is a small, runtime-neutral language for live systems. The current supported language
implementation is the Truffle runtime: it provides a compact L0 core, persistent data, explicit
mutable `array`/`object` markers, protocols, promises, bytes, capability-gated I/O, and a JLine REPL.

```text
Hara source
    |
    v
Truffle parser / AST
    |
    +--> runtime-neutral core
    |
    +--> explicit libraries (bytes, promise, file, socket, string)
    |
    +--> host capability boundary
```

## Start here

- [User guide](docs/user-guide.md) — install, run, evaluate, use the REPL, and write Hara.
- [Developer guide](docs/development.md) — build, test, debug, and contribute.
- [Java API and Javadocs](docs/javadocs.md) — public entry points and generated API docs.
- [Language specification](spec/hara/l0-language.md) — normative L0 behavior.
- [Runtime libraries](spec/hara/runtime-libraries.md) — the portable library contract.
- [Rust/WASM mapping](spec/hara/rust-runtime.md) — the cross-runtime value, provider, and conformance design.
- [Extensions](spec/hara/extensions-contract.md) — pods, WASM, manifests, and capabilities.
- [REPL UX](spec/hara/repl.md) — history, completion, docs, and slash-command design.

## Quick start

Requirements: JDK 21 and Maven.

```shell
mvn -Ptruffle package
java -jar target/hara-truffle.jar eval '(+ 19 23)'
java -jar target/hara-truffle.jar repl
```

The CLI also supports `run <file>`, `stdin`, and `help`. For a native-image build, see the
[developer guide](docs/development.md); native mode intentionally removes dynamic JVM services.

## Current runtime boundary

The language does not expose ambient JVM host interop. JVM reflection, compilation, mutable
classpath access, files, and sockets are explicit capabilities or provider services. This keeps the
core portable to future runtimes such as WASM hosts.

The old interpreter/Foundation/TCP architecture is retained as
[`README.legacy.md`](README.legacy.md) for historical reference only; it is not the current
language guide.

## Status

Hara is an active experimental runtime. The L0 slice and focused conformance suites are the source
of truth. Provider discovery, pod transport, and WASM execution are documented contracts with
implementation work still in progress.

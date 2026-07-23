# Hara extensions

Hara extensions are loaded through ordinary namespace clauses. The program does not need to know
whether an extension is implemented by a subprocess pod, a WASM module, or another host provider.

```clojure
(ns app
  (:require [crypto.hash :as hash]))

(deref (hash/sha256 data))
```

An extension package contains a `hara.extension.edn` manifest beside its provider artifact:

```clojure
{:namespace "crypto.hash"
 :version "0.1.0"
 :provider :wasm
 :module "math.wasm"
 :abi :core-v1
 :exports {"add" {:args [:i32 :i32] :returns :i32}}
 :capabilities []}
```

The runtime discovers manifests from configured extension roots, validates the provider handshake,
and generates the requested namespace during `:require`. Extension namespaces are runtime
generated and are not `.hal` source files.

WASM providers implement the extension lifecycle:

```text
discover -> describe -> invoke -> result or error -> cancel -> shutdown
```

Arguments and results use Hara values and explicit opaque handles. Remote calls return promises.
WASM providers use a host WASM engine. Compilation, instantiation, export calls, and memory
access remain behind the provider boundary. `:core-v1` invokes low-level scalar exports directly.
`:hta-v1` uses an import-free, host-driven mailbox and returns Hara promises. On the JVM one virtual
thread owns each nested GraalWasm context; in a browser one Web Worker owns each context. Explicit
`host/call` events are checked against the descriptor's optional `host-calls` allowlist before a
result or structured rejection is delivered to the module.

Loading a manifest does not grant authority. The host applies capability policy and resource
limits, preserving distinct errors for unsupported providers, denied capabilities, malformed
manifests, crashes, timeouts, cancellation, and remote failures.

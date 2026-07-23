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
 :module "crypto.wasm"
 :exports {"sha256" {:args [:bytes] :returns :bytes}}
 :capabilities []}
```

The runtime discovers manifests from configured extension roots, validates the provider handshake,
and generates the requested namespace during `:require`. Extension namespaces are runtime
generated and are not `.hara` source files.

Pods and WASM providers implement the same lifecycle:

```text
discover -> describe -> invoke -> result or error -> cancel -> shutdown
```

Arguments and results use Hara values and explicit opaque handles. Remote calls return promises.
Pods are long-lived subprocesses using a versioned, length-delimited protocol with request IDs,
limits, separate stderr, and explicit shutdown. Protobuf is the initial pod wire profile.

WASM providers use a host WASM engine. Compilation, instantiation, export calls, and memory
access remain behind the provider boundary. Imports are explicit capabilities rather than ambient
filesystem, socket, clock, or process access.

Loading a manifest does not grant authority. The host applies capability policy and resource
limits, preserving distinct errors for unsupported providers, denied capabilities, malformed
manifests, crashes, timeouts, cancellation, and remote failures.

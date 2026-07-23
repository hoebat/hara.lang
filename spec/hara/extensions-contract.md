# Hara extension contract

This document describes the provider boundary behind ordinary `:require` forms. A Hara program
does not construct pod messages or call a WASM engine directly.

## Discovery

```text
(:require [crypto.hash :as hash])
                 |
                 v
        +-------------------+
        | namespace registry |
        +-------------------+
          | already loaded?
          | yes       | no
          v           v
       reuse     search extension roots
                        |
                        v
                hara.extension.edn
                        |
                        v
                 validate manifest
                        |
                        v
                 select provider
                        |
                        v
                 create namespace
```

An extension package contains a `hara.extension.edn` manifest beside its provider artifact:

```clojure
{:namespace "crypto.hash"
 :version "0.1.0"
 :provider :wasm
 :module "math.wasm"
 :abi :core-v1
 :exports {"add" {:args [:i32 :i32] :returns :i32 :async true}}
 :capabilities []}
```

The portable fields are `namespace`, `version`, `provider`, `module`, `abi`, `exports`, and
`capabilities`. The manifest is metadata, not executable Hara code. Extension namespaces are
runtime-generated namespaces, not `.hal` source files.

## Provider lifecycle

```text
discover -> describe -> start -> invoke -> result or error -> cancel -> shutdown
                 |                         |
                 |                         +--> timeout
                 +--> capability check
                         |
                  denied or granted
```

Arguments and results use Hara values and explicit opaque handles. Remote calls return promises:

```text
Hara call
   |
   v
provider invoke(request-id, function, args)
   |
   +----> pending promise
   +----> result(value) ------> promise realization
   +----> error(kind, message) -> promise rejection
```

Pods are long-lived subprocesses using a versioned, length-delimited protocol with request IDs,
limits, separate stderr, and explicit shutdown. Protobuf is the initial pod wire profile. A pod
must not require callers to construct protocol messages manually.

WASM providers use a host engine. Compilation, instantiation, export calls, and memory access
remain behind the provider boundary:

```text
WASM module
    |
    v
+-----------+       explicit imports       +----------------+
| WASM host | <---------------------------- | capability set |
+-----------+                              +----------------+
    |
    +--> memory / export call / result
```

Loading a manifest does not grant authority:

```text
manifest requests capability
          |
          v
      host policy
       /       \\
    grant     deny
      |         |
 provider    stable denied error
```

The runtime preserves distinct errors for missing or malformed manifests, unsupported providers,
denied capabilities, crashes, timeouts, cancellation, and remote failures. The same Hara
namespace can therefore be backed by a pod or WASM provider without changing the call site.

## Packaged Noir proof provider

The extension is named `blockchain.proof.noir`. A runnable descriptor template lives at
`examples/extensions/blockchain/proof/noir/hara.extension.edn`; an installed bundle places that
descriptor beside `noir.wasm` under the same namespace-derived directory. Requiring the extension
generates its namespace directly from the declared WASM exports:

```hal
(ns proof.example
  (:require [blockchain.proof.noir :as noir]))

(noir/add 20 22)
```

No `noir/*` alias or `hara.lib.noir` adapter is installed implicitly. Applications opt into the
WASM surface through `:require`. Runtime bundles can also be installed under an explicit root in
`hara.extensions.path` or `HARA_EXTENSION_PATH`, using the same namespace-derived directory layout.

The template works with the repository's raw Rust fixture copied as `noir.wasm`. The initial
`:core-v1` ABI supports import-free core-WASM functions using `:i32`, `:i64`, `:f32`, `:f64`,
`:boolean`, and `:void`. Noir compile/prove/verify still needs a standalone artifact implementing a
future memory-based ABI; its existing JavaScript/worker bundle is not treated as that artifact.

Classpath discovery rejects duplicate namespace packages, malformed manifests, unknown providers,
unknown modules, and denied capability requests before installing any exported vars.

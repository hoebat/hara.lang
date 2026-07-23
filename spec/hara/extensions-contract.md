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

The portable fields are `namespace`, `version`, `provider`, `module`, `abi`, `exports`,
`capabilities`, and the optional `host-calls` allowlist. The manifest is metadata, not executable
Hara code. Extension namespaces are runtime-generated namespaces, not `.hal` source files.

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

WASM providers use a host engine. Compilation, instantiation, export calls, and memory access
remain behind the provider boundary. `:core-v1` is the direct scalar ABI. `:hta-v1` is the Hara
Transport Adaptor for stateful, promise-returning modules.

## HTA v1

HTA modules remain import-free. The host drives an exported mailbox rather than injecting ambient
WASM imports:

```text
Hara call -> hta_start -> task
                         |
                 hta_next_event
                    /          \
             settlement      host-call
                                 |
                   descriptor :host-calls check
                                 |
                        hta_deliver result/error
```

The required exports are `hta_abi_version`, `hta_alloc`, `hta_dealloc`, `hta_start`,
`hta_next_event`, `hta_deliver`, `hta_poll`, `hta_cancel`, `hta_drop_task`, and `hta_release`. Frames use the
canonical binary `HTA1` value encoding. Its portable value intersection is nil, booleans, signed
64-bit integers, UTF-8 strings, bytes, keywords, symbols, lists, vectors, sets, maps, and opaque `{owner, type, id}` handles. Map and
set elements are ordered by their encoded bytes so Rust, Java, and JavaScript produce identical
frames.

Each Truffle extension instance has one Java virtual-thread actor which exclusively owns its
nested GraalWasm context. Browser instances use one Web Worker per context. Both actors block on a
mailbox while idle; neither polls in a spin loop. Package completions enqueue a delivery back to
the owner before any WASM export is called. Pending `deref` suspends an explicit evaluator fiber; settlement resumes its retained continuation without replaying completed forms.

Host calls are explicit Hara operations such as:

```hal
(host/call "crypto.hash.sha256" "digest" input)
```

They are denied unless the descriptor contains, for example,
`:host-calls {"crypto.hash.sha256" ["digest"]}`. Rejections cross HTA as structured values with
`code`, `message`, `data`, `origin`, `retryable`, `stack`, and `cause` fields as applicable. Host
stack traces are not transported. A rejected settlement realizes the corresponding Hara promise
exceptionally; it is not a Rust panic or a Java exception thrown through WASM.

The reference package at `examples/extensions/crypto/hash/sha256` is a real, import-free Rust WASM
extension:

```hal
(ns crypto.example
  (:require [crypto.hash.sha256 :as sha]))

(deref (sha/digest (bytes 97 98 99)))
```

Build its artifact with `scripts/build-crypto-hash-sha256-wasm` and install the resulting `.wasm`
beside the example descriptor as `sha256.wasm`.

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

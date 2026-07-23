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
 :module "crypto.wasm"
 :exports {"sha256" {:args [:bytes] :returns :bytes :async true}}
 :capabilities []}
```

The portable fields are `namespace`, `version`, `provider`, `module`, `exports`, and
`capabilities`. The manifest is metadata, not executable Hara code. Extension namespaces are
runtime-generated namespaces, not `.hara` source files.

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

The first classpath package is `hara.extensions.blockchain.proof.noir`. Its manifest lives at
`META-INF/hara/extensions/hara/extensions/blockchain/proof/noir/hara.extension.edn`, selects the
`:wasm` provider,
and exports the Noir compile/prove/verify boundary without exposing compiler or WASM objects:

```hal
(ns proof.example
  (:require [hara.extensions.blockchain.proof.noir :as noir]))

(def program
  (noir/program
    "square"
    "fn main(secret: Field, expected: pub Field) { assert(secret * secret == expected); }"))
```

No `noir/*` alias is installed implicitly. Applications opt into this surface through `:require`;
`hara.lib.noir` remains an internal runtime namespace used by the provider adapter.

Classpath discovery rejects duplicate namespace packages, malformed manifests, unknown providers,
unknown modules, and denied capability requests before installing any exported vars.

# Hara runtime libraries

Hara keeps its automatically referred core small. The runtime generates the library namespaces;
they are not backed by `.hal` source files and do not expose their JVM implementation.

Every namespace receives these aliases by default:

| Alias | Generated namespace | Xtalk family |
| --- | --- | --- |
| `str/` | `hara.lib.string` | `x:str-*` |
| `promise/` | `hara.lib.promise` | `x:promise-*`, `x:with-delay` |
| `bytes/` | `hara.lib.bytes` | byte operations |
| `socket/` | `hara.lib.socket` | `x:socket-*` |
| `file/` | `hara.lib.file` | `x:file-*` |

`(ns app)` and `(ns app (:intrinsics :all))` are equivalent. Aliases can be excluded or renamed:

```clojure
(ns app
  (:intrinsics
    {:exclude [bytes]
     :aliases {string text
               promise async}}))
```

Exclusion only removes the qualified alias. It does not remove core constructors such as `bytes`.
Generated namespaces also work with ordinary namespace clauses:

```clojure
(ns app
  (:intrinsics {:exclude [string]})
  (:require [hara.lib.string :as text :refer [trim]]))
```

The core constructors are `str`, `promise`, `bytes`, `array`, and `object`. Core also provides
signed 32-bit `bit-and`, `bit-or`, `bit-xor`, `bit-not`, `bit-shift-left`, and
`bit-shift-right`.

`array` and `object` create mutable marker values distinct from persistent `[]` and `{}`. Dot
calls are accepted only on those marker values:

```clojure
(. (array 1 2 3) (filter (fn [x] (> x 1))))
(. (object "name" "Hara") (get "name"))
```

Array methods are `get`, `set`, `push-first`, `push-last`, `pop-first`, `pop-last`, `insert`,
`remove`, `slice`, `clone`, `map`, `filter`, `fold-left`, and `fold-right`. Object methods are
`has?`, `get`, `set`, `delete`, `clone`, `assign`, `keys`, `vals`, and `pairs`. Object keys are
strings.

Promises are backed by the runtime's native promise mechanism. On the JVM that is
`CompletableFuture`. `promise/run`, `promise/new`, `promise/all`, `promise/then`,
`promise/catch`, `promise/finally`, `promise/native?`, and `promise/delay` follow the
`xt.lang.spec-promise` settlement and adoption model.

Bytes expose unsigned values from `0` through `255` through `bytes/get`; `bytes/u8` and
`bytes/s8` explicitly convert representations. String encoding and decoding are UTF-8.

File and socket namespaces are always present and aliased. Availability does not grant authority.
Unsupported or denied operations fail when invoked. JVM embeddings grant authority with Graal
`IOAccess`; the CLI grants it explicitly with `--allow-file` and `--allow-net`. File reads and
writes use bytes and return promises. Socket v1 follows the xtalk callback contract:
`connect` accepts host, port, options, and an `(fn [error connection] ...)` callback; `send`
accepts bytes and returns the byte count; `close` is direct. Receive/framing and HTTP are outside
this slice.

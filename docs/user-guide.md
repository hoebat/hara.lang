# Hara user guide

## A first program

```clojure
(defn greet [name]
  (str "hello, " name))

(greet "Ada")
```

Hara uses Lisp forms: the first item is normally the operation and the remaining items are its
arguments. Vectors, maps, sets, strings, numbers, keywords, and `nil` are data values.

## Namespaces and libraries

The core is deliberately small. Load optional libraries explicitly:

```clojure
(ns app
  (:require [hara.lib.string :as str]
            [hara.lib.bytes :as bytes]
            [hara.lib.promise :as promise]))

(str/trim "  ready  ")
```

The runtime injects only the configured intrinsic library set. `:require` resolves Hara modules and,
eventually, provider-backed extension namespaces.

## Mutable markers

Persistent collections are the default. Use `array` and `object` when mutation is intentional:

```clojure
(let [a (array 1 2 3)]
  (. a (push-last 4))
  (. a (get 3)))
```

The marker value makes the mutability boundary visible at construction time.

## Promises

Promises model native completable asynchronous work:

```clojure
(promise/then
  (promise/run (fn [] (file/read "data.bin")))
  (fn [bytes] (bytes/count bytes)))
```

Use `promise/catch` for recovery and `promise/finally` for cleanup. Sockets remain callback-based;
they do not grow separate socket-promise method families.

## Files, sockets, and capabilities

```clojure
(file/read "notes.txt")
(file/write "notes.txt" (str/encode "hello"))
```

File and socket operations may be unsupported or denied by the embedding runtime. Hara keeps those
errors distinct so applications can respond correctly.

## REPL workflow

The REPL provides history, completion, multiline input, metadata docs, and slash commands. Use
`Alt-q` or `F1` on a symbol to inspect its docstring and arglists. Commands such as `/help`,
`/history`, `/clear`, `/splash`, `/ns`, and `/quit` are handled by the REPL and are not sent to the
Hara evaluator; ordinary Hara forms always go through the reader and evaluator.

## Learn the exact rules

Use the [L0 language specification](reference/l0-language.md) for semantics and the
[runtime-library contract](reference/runtime-libraries.md) for portable operations.

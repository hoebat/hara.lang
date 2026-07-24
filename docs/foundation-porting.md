# Polis Foundation `.hal` Port

This ledger tracks semantic decisions while porting `foundation-base/src/hara`
to the `polis.*` transpiler library.
An entry marked `pending` must not be implemented with changed behavior until it
has been reviewed.

## Slice status

| Slice | Status | Notes |
|---|---|---|
| Project and namespace loader | complete | Self-contained examples live under `bench/<NUM>-<GROUP>` |
| Common grammar foundation | in progress | Faithful sources live under `implementation/src/polis/common` |
| `polis.typed` | pending | Does not include `hara.model` |
| Common preprocess and emit | pending | `emit-rewrite` follows the typed slice |
| `polis.lang.book*` | pending | Independent review gate |
| Remaining `polis.lang` | pending | Split by library, rewrite, compiler, and runtime layers |

## Source convention

Polis is a self-contained Hara project under `implementation/`. Production
namespaces live under `implementation/src/polis`; translated tests live under
`implementation/test/polis`. Namespace roots are mapped predictably:

| Foundation | Polis |
|---|---|
| `hara.common.*` | `polis.common.*` |
| `hara.typed.*` | `polis.typed.*` |
| transpiler-focused `hara.lang.*` | `polis.lang.*` |

Benchmarks consume these packaged namespaces and do not contain substitute
implementations.

## Pending semantic decisions

| Area | Original behavior | Missing or different facility | Proposed direction | Decision |
|---|---|---|---|---|
| Typed records | Five Clojure records represent typed declarations | Native Hara record declaration was missing | Native `defrecord`, `->Record`, and `map->Record` constructors | implemented |
| Namespace analysis | Uses `ns-publics`, `ns-aliases`, `ns-name`, and `requiring-resolve` | Hara exposed only part of this introspection | Narrow deterministic namespace primitives; publics exclude referred Vars | implemented |
| Source analysis | Uses tools.reader with file, line, and column metadata | Hara parser did not expose a multi-form analysis API | Capability-checked `read-forms` preserving file and source spans | implemented |
| Parallel work | Some later language/runtime paths use futures and parallel task processing | Hara promises differ from Clojure futures | No sequential fallback until separately approved | pending |
| Host integrations | Later language paths use filesystem, process, network, and JVM APIs | Hara uses explicit capabilities and runtime flavors | Keep operations unavailable until a capability-safe mapping is reviewed | pending |

## Intentional runtime adaptations

The following adaptations preserve observable Foundation behavior while using
the portable Hara runtime:

| Foundation construct | Polis implementation | Reason |
|---|---|---|
| Nested argument destructuring in helper definitions | Explicit `first`, `second`, `drop`, and indexed access | Hara does not yet support every nested Clojure binding pattern |
| `list` and syntax-quoted generated forms | Persistent lists assembled with `cons` | Hara does not expose Clojure's `list`, and syntax quote is not a runtime function |
| `with-meta` | `IObjType/with-meta` through `protocol-call` | Metadata mutation is intentionally protocol-based |
| Repeated traversal of lazy collection operations | Materialize once with `vec` | Hara lazy iterators are one-shot |
| Dynamic values consumed by lazy transforms | Materialize inside the dynamic binding | Deferred iteration must not escape the binding extent |
| Arbitrary `eval` in grammar mixins | Qualified symbol resolution plus a narrow form evaluator | Portable HAL code must not depend on host evaluation |
| Clojure `seq?` as a list/form predicate | Dispatch after the supported symbol and map mixin cases | Hara's `seq?` specifically identifies lazy `HaraSeq` values |
| Runtime `macroexpand-1` for `->` and `->>` data | Explicit thread-first/thread-last form transformation | Hara expands these forms during analysis; quoted transpiler data is transformed portably in HAL |

## Project convention

The nearest `project.hal` is discovered by walking upward from the working
directory. Runnable project fixtures use
`bench/<NUM>-<GROUP>/{project.hal,src,test}`. A descriptor has this form:

```clojure
(defproject project.name
  {:source-paths ["src"]
   :test-paths ["test"]})
```

Namespace paths replace dots with directories and hyphens with underscores.
Project source wins over packaged classpath source. Test paths are considered
only for namespaces ending in `-test`.

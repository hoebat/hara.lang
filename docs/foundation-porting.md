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

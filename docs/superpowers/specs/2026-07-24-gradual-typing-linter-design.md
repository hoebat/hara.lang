# Gradual typing + clj-kondo-style linter for hara — design

Date: 2026-07-24
Status: proposed (approach A)

## 1. Goal

Add a linter library to hara whose analysis layer performs **automatic type detection** —
lightweight local inference in the clj-kondo mold, not Hindley-Milner. One static pass over
source forms produces both lint findings and a type-annotated analysis usable by editors.

The design is a deliberate hybrid of the three Clojure-ecosystem approaches (§4.1): kondo's
shallow inference as the **engine**, a malli-flavored schemas-as-data grammar as the **type
language**, and core.typed's occurrence typing as the internal **narrowing semantics**.

Non-goals for the first iteration: type variables / unification, whole-program inference,
runtime validation against schemas, editor diagnostics plumbing.

## 2. Why this shape

- **`read-forms` already exists** (`src/main/java/hara/truffle/HaraContext.java:2669`): it
  returns source forms carrying `:file :line :column :end-line :end-column` metadata, and was
  added explicitly (see `docs/foundation-porting.md`, "Source analysis" row) as the
  tools.reader equivalent for source analysis. It is the natural, already-blessed entry point.
- **Arity data already exists**: `defn` stores `:arglists` in var meta; `ns-publics`,
  `ns-aliases`, `requiring-resolve` are implemented. A signature table is derivable, not
  invented.
- **clj-kondo precedent**: kondo is a Clojure program analyzing Clojure source via
  tools.reader. Hara can mirror that architecture one-for-one, and writing it in hara dogfoods
  `std.lib.foundation` and keeps the analyzer portable across the Truffle, legacy-kernel, and
  wasm runtimes (the wasm parity harness already exists).
- **`polis.typed` is adjacent, not a prerequisite**: the pending port of Clojure `hara.typed`
  models typed declarations for the transpiler's emit-rewrite decisions. Its records may later
  become the linter's type representation, but blocking linting on that port delays all
  user-visible value.

## 3. Architecture

```
.hal source ──read-forms──▶ forms + spans ──▶ std.types.lint.analyze (per namespace)
                                                 │
                    ┌────────────────────────────┼─────────────────────────┐
                    ▼                            ▼                         ▼
              var/def table                usage table +             findings EDN
          (name, :arglists,             inferred type env         {:file :row :col …
           inferred signature)        (locals, narrowing)          :level :type :message}
```

Namespaces — types is the umbrella, lint is its first consumer (leaving room for
`std.types.validate` later). Sources under `implementation/src/std/types/`, tests under
`implementation/test/std/types/`:

- **`std.types.schema`** — the schema language itself: grammar (§4.2), type lattice,
  schema→per-arity-table projection, and both registries (schema heads, narrowing
  predicates). Pure functions and data; no reader, no inference.
- **`std.types.infer`** — the inference engine: literal/propagation rules, narrowing
  propositions, signature application. Pure functions over forms + sig tables; no I/O.
- **`std.types.core-sigs`** — core-builtin signatures written in the §4.2 grammar and
  projected through `std.types.schema`. Provenance: seeded from the runtime's own var
  metadata (`:arglists` on vars, `@HaraExport` on Java stdlib classes), then hand-completed
  with arg/return types for the L0/core surface. Kept honest by a **sync test** that diffs
  the table's keys against the core namespace's publics — a builtin without a signature
  fails the build (same pattern as `spec/hara/clojure-core-symbols.json`). Writing the table
  in the public grammar dogfoods the projection.
- **`std.types.lint.forms`** — walks `read-forms` output; normalizes `defn`/`let`/`fn`/`if`
  shapes; span accessors. No analysis logic.
- **`std.types.lint.hooks`** — macro handling: built-in rewrites for the L0 macro set, the
  `macroexpand` path for loaded namespaces, and the hook registry (§6). Kept out of
  `std.types.lint.forms` so form normalization stays pure and total.
- **`std.types.lint.analyze`** — the pass: builds per-namespace tables, runs the checks,
  emits findings.
- **`std.types.lint.report`** — findings → text / EDN rendering.

Each namespace is understandable from its interface alone; `analyze` depends on `forms`,
`hooks`, `infer`, `schema`, and `core-sigs` only through their public functions, and nothing
under `std.types.lint.*` reaches into the runtime beyond `read-forms`/`macroexpand`.

## 4. Type detection ("automatic" typing)

Lattice: the primitives of §4.2 with `any` as top and `unknown` as bottom (bottom suppresses
findings); finite unions for branch joins. No type variables, no unification.

Inference rules, in order of implementation value:

1. **Literals** → their concrete type; quoted forms → their data type.
2. **Core signatures** — call sites of known builtins get argument checks and return types
   from `std.types.core-sigs`; arity checked against `:arglists`.
3. **Propagation** — `let` bindings carry their init type inward; `defn` records each arity
   plus a best-effort return type inferred from the body.
4. **Predicate narrowing** — in `if`/`when` test position, `(string? x)` narrows `x` to `str`
   in the then-branch (and its complement in the else-branch). Narrowing is implemented as
   **propositions** (`is`/`not`, composed with `and`/`or`) over local bindings — a strict
   subset of core.typed's occurrence typing, structured so the full rule set can grow in
   later (see §4.1).
5. **Optional annotations** — schemas attached to `defn` vars (metadata or a sidecar
   registry), written in the malli-flavored schema grammar (§4.2). Annotations are *checked*
   against inference, never required. This keeps the system gradual: untyped code still gets
   detection; annotations only tighten it.

Type information precedence (highest first): `^{:schema}` annotations → `std.types.core-sigs`
entries → inferred signatures from `defn` bodies. Unknown anywhere in the chain falls through
to the next layer; `unknown` at the bottom means silence.

### 4.1 Prior art, and what hara takes from each

Three systems define the design space; hara takes a different slice of each:

- **clj-kondo — the engine.** Its `:type-mismatch` linter is deliberately shallow: literal
  types (it even distinguishes `:positive-integer`), propagation through `let`/`defn`
  returns, and call-site checks against per-arity signature tables
  (`{:arities {1 {:args [:string] :ret :string}}}` with `{:op :rest}` / `{:op :keys}`
  operators), fed by built-in core annotations, `^Type` hints, and user config. No
  unification, no polymorphism, silence on unknown. This is the only design that delivers
  "automatic detection" — hara adopts it wholesale as the inference engine (rules 1–4).
- **malli — the type language.** Schemas are plain data (`[:=> [:cat :int :int] :int]`,
  `[:map [:x :int]]`), and the same definitions drive runtime validation, instrumentation,
  and test generation. Malli does no static checking itself; `malli.clj-kondo` projects
  schemas into kondo's config format and kondo does the static half. Hara copies that proven
  bridge directly: one projection function turns function schemas into the linter's per-arity
  tables, and the annotations stay useful for a future runtime-validation library.
- **core.typed — the narrowing semantics only.** Proposition-based narrowing (rule 4) is a
  strict subset of core.typed's occurrence typing; the `is`/`not`/`and`/`or` proposition
  representation leaves room for `let` aliasing and path refinements later without reworking
  the engine. Nothing else is taken: no type variables, polymorphism, or unification (the
  annotation burden that kept core.typed niche, and poison for automatic detection); no
  `check-ns`-style proof obligations (a language bootstrapping its stdlib gets silence from a
  system that needs annotation coverage before it speaks); no runtime contracts.

### 4.2 Schema grammar (annotations and analysis output)

Types are hara data in a malli-flavored schema syntax — keywords for primitives, vectors for
composites:

```clojure
:any :nil :bool :num :str :keyword :symbol :list :vector :map :set :fn :atom :bytes :promise
[:or :str :nil]                    ;; union
[:maybe :str]                      ;; sugar for [:or T :nil]
[:vector :num]                     ;; homogeneous collection (descriptive for now)
[:map [:row :num] [:col :num]]     ;; map shape (descriptive; checked only for disjoint keys)
[:=> [:cat :num :num] :num]        ;; single-arity function schema
[:function                         ;; multi-arity (like malli's [:function ...])
  [:=> [:cat :num] :num]
  [:=> [:cat :num :num] :num]]
```

Function schemas project onto the linter's internal per-arity tables — the same shape kondo
uses (`{:arities {1 {:args [...] :ret ...}}}`, with rest-arg support). The projection is one
pure function in `std.types.schema`, so the sig table, user annotations, and inferred output all
share one representation. `unknown` is internal-only (bottom; suppresses findings) and never
appears in schemas. Runtime validation against these schemas is a *future* consumer
(`std.types.validate`), not part of the linter.

Launch checks (deliberately small — kondo started the same way):

| Check | Level | Trigger |
|---|---|---|
| `:arity` | error | call-site arg count incompatible with known `:arglists` |
| `:unresolved-symbol` | error | symbol not bound in locals, ns mappings, or requires |
| `:type-mismatch` | warning | both sides concrete and disjoint (e.g. `(+ 1 "a")`) |
| `:unused-binding` | warning | `let` binding never referenced |

Anything `unknown` produces no finding — false positives are worse than silence at this
stage.

## 5. Findings format

Findings are data, kondo-compatible in shape:

```clojure
{:file "src/foo.hal" :row 12 :col 5 :end-row 12 :end-col 9
 :level :warning            ;; or :error
 :type :type-mismatch       ;; :arity | :unresolved-symbol | :type-mismatch | :unused-binding
 :message "expected num, got str"}
```

Spans come from form metadata; a form without span metadata degrades to row 1 / col 1 with
the printed form in the message. Analysis never throws on malformed-but-readable code: every
check is total and returns findings.

A normative version of this format will live in `spec/hara/lint-findings.md`, mirrored to
`docs/reference/`, once the design is approved.

## 6. Extensibility

Five extension points, in order of importance:

1. **Macros — the critical one.** `read-forms` returns unexpanded forms and hara macros are
   user-definable, so an analyzer that only knows L0 shapes goes blind in real code. Three
   mechanisms, tried in order:
   - built-in rewrites for the L0/bootstrap macro set (`->`, `->>`, `when`, `cond`, …) that
     translate calls into analyzable `let`/`if`/`fn` shapes before typing;
   - *real expansion*: `macroexpand`/`macroexpand-all` exist as builtins
     (`HaraAnalyzer.java:196`, `StdLibWalk.java:137`), so when the defining namespace is
     loaded in the runtime the linter expands user macros for real — the mechanism clj-kondo
     lacks. Expansion failures or unloaded namespaces degrade to the fallback, never to
     errors;
   - a hook registry (kondo's `:hooks` pattern): data mapping a qualified macro symbol to
     either a `:lint-as`-style equivalence (analyze like `let`/`defn`) or a rewrite fn
     (form → form). The user-facing escape hatch for macros that can't or shouldn't be
     expanded (effectful definitions, load-order problems).
2. **Schema language.** Composite schema heads dispatch through a data registry in
   `std.types.schema` (malli's `IntoSchema`-registry idea); users register new heads as
   projector fns schema-form → lattice type. A second registry maps user predicate symbols
   (e.g. `my/positive-int?`) to the proposition they introduce in narrowing.
3. **Annotations travel with code.** `^{:schema …}` metadata on `defn` vars: library authors
   ship types inside the artifact — kondo's exports mechanism without the config copying.
4. **Custom checks.** Two tiers: data-only config (`:discouraged-var`, `:discouraged-ns`,
   per-check `:level` overrides) and registered check fns that receive the analysis tables
   and return findings — the same contract as the built-in four.
5. **Project configuration.** A `:lint` key in the existing `project.hal` descriptor:
   levels, lint-as entries, discouraged-var lists, sig overrides. Project-level only at
   launch; no per-file config.

Launch scope: built-in macro rewrites, the macroexpand path, `^{:schema}` metadata, and the
two registries (schema heads, narrowing predicates). The hook registry, custom check fns, and
`:lint` config land in the follow-up phase (§9 step 9) — the extension points are designed in
now so nothing needs rework later.

Worked examples — adding rules and types, cheapest tier first:

```clojure
;; 1. Config tier (data only) — project.hal
(defproject my.app
  {:source-paths ["src"]
   :lint {:levels {:unused-binding :off}
          :discouraged-var #{hara.core/debug-print}}})

;; 2. Code tier — a custom check fn, same contract as the built-in four.
;; Lives in an ordinary hara namespace, pulled in via :lint {:requires [my.rules]}.
(ns my.rules)
(defn no-empty-catch [analysis] ;; analysis: {:defs … :usages … :types …}
  ;; …walk usages, return findings in the §5 shape…
  [])
(std.types.lint.analyze/register-check
  {:id :my/no-empty-catch :level :warning :fn no-empty-catch})

;; 3. Types — travel with the code itself
(defn ^{:schema [:=> [:cat :str :str] :str]} greet [first-name last-name]
  (str first-name " " last-name))

;; 3b. Or teach the engine about a custom predicate
(std.types.schema/register-predicate my.pos/positive-int? :num)
```

Custom check fns are ordinary hara code running in the linting runtime — hara needs no
sci-style sandbox or config copying like kondo's hooks, at the usual dev-tool caveat: linting
a project executes its registered check namespaces.

## 7. Error handling and portability constraints

- Follow the polis porting adaptations (`docs/foundation-porting.md`): materialize lazy
  iterators with `vec` before repeated traversal (hara iterators are one-shot); no host
  interop in `std.types.*` beyond `read-forms`/`macroexpand`; metadata mutation via
  `protocol-call`.
- `read-forms` is capability-gated (`requireHalPath`, `requireFileIO`) — the linter needs
  file-I/O capability, same as the polis source loader.
- Keep the library free of Truffle-only values so the wasm runtime can eventually run it
  through the existing parity harness.

## 8. Testing

- `code.test` facts per namespace under `implementation/test/std/types/`, fixtures as small
  `.hal` files under `implementation/test/std/types/fixtures/` consumed via `read-forms`.
- Pure-function facts for every inference rule in `std.types.infer` (lattice joins, narrowing,
  signature application).
- **Core-sigs sync test**: a fact that diffs `std.types.core-sigs` keys against the core
  namespace's publics, so a builtin added without a signature fails the build.
- JUnit wrapper `StdTypesSourceTest` mirroring `PolisSourceTest`, running
  `(code.test/run {:namespace …})` over `std.types.*`.
- **Dogfood gate**: the linter run over `implementation/src/std/lib/foundation.hal` and
  `implementation/src/polis/common/` must report zero findings; the signature table is tuned
  until this holds.

## 9. Phasing

1. Spec: this doc + `spec/hara/lint-findings.md` (normative findings format), mirror to
   `docs/reference/`.
2. `std.types.lint.forms` + facts.
3. `std.types.schema` + facts (grammar, lattice, projection, both registries).
4. `std.types.infer` + facts (inference rules, narrowing propositions, signature application).
5. `std.types.core-sigs` seed table (scope: fns used by `std.lib.foundation`).
6. `std.types.lint.hooks` + `std.types.lint.analyze` + `std.types.lint.report`, the four
   launch checks, macro rewrites and the macroexpand path.
7. `StdTypesSourceTest` wired into `mvn test`.
8. Dogfood gate green.
9. Follow-up (separate plan): hook registry + custom checks + `:lint` config (§6); RESP/
   editor diagnostics surface for vscode-hara and emacs-hara; convergence with `polis.typed`
   records if/when that port lands; runtime validation (`std.types.validate`) consuming the
   same schemas.

## 10. Alternatives considered

- **Java-side pass beside `HaraAnalyzer`** — faster and close to the live var registry, but
  Truffle-only (kernel and wasm would need duplicates), no dogfooding, and type logic in Java
  iterates slowly. Rejected.
- **Port `polis.typed` first** — would unblock the transpiler's emit-rewrite and give one type
  representation for both consumers, but `hara.typed` was built for emit decisions, not lint
  findings, and it delays any user-visible linting by a full port. Deferred to a later
  convergence step.
- **core.typed semantics wholesale** — full gradual typing with polymorphism, HMap proof
  obligations, and `check-ns` checking delivers nothing on unannotated code and carries the
  annotation burden that limited core.typed's adoption in Clojure itself. Only its occurrence
  typing survives, as the narrowing engine's semantics (§4.1); the rest is rejected.
- **malli alone (runtime validation only)** — malli is a runtime validation library that
  delegates static checking to kondo via a generated config bridge; without a static engine
  there is no "automatic detection" at all. Hara adopts its schema grammar as the type
  language (§4.2) and keeps runtime validation as a future consumer.

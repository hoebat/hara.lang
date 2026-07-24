# AGENTS.md

Repo layout and per-component build/test commands. See `README.md` for the
component map and `docs/development.md` for the full developer guide.

## Layout

- `java/` — Java/Truffle runtime (Maven, JDK 21)
- `rust/` — Rust/embedding runtime (native CLI, wasm builds, web loader,
  `rust/extensions/` in-tree wasm extensions)
- `lib/` — hara-language sources (`lib/src`, `lib/test`), examples
  (`lib/examples/`), benchmarks (`lib/bench/`)
- `apps/` — `hara-chrome`, `hara-vscode`, `hara-emacs`, `hara-lsp` (planned)
- `docs/` — documentation content; `website/` — mkdocs/landing infra
- `spec/hara/` — normative specs; `scripts/` — repo-level build scripts
- `archive/` — legacy material, kept for history only
- `books/`, `registry/` — placeholders for planned work (README-only)

## Build and test

Java/Truffle runtime:

```shell
mvn -f java/pom.xml -Ptruffle package        # build + full test suite
mvn -f java/pom.xml -Ptruffle -Dtest=hara.truffle.HaraL0ConformanceTest test
./hara eval '(+ 19 23)'                      # CLI smoke test
```

Rust runtime:

```shell
cargo test --manifest-path rust/Cargo.toml
cargo test --manifest-path rust/raw/Cargo.toml
bash rust/scripts/check-layout.sh
bash scripts/build-hara-wasm-raw             # raw wasm extension artifact
cd rust/web && npm ci && npm run test:hta    # browser loader tests
```

Apps:

```shell
cd apps/hara-chrome && npm ci && npm run build && npm test
cd apps/hara-chrome && npm run test:browser  # playwright (needs xvfb)
```

Docs site:

```shell
pip install -r website/requirements-docs.txt
mkdocs build --strict -f website/mkdocs.yml
```

## Conventions

- Maven runs from the repo root via `-f java/pom.xml`; Surefire's working
  directory is the repo root, so tests use repo-relative paths
  (`spec/hara/...`, `lib/examples/...`).
- The JVM runtime embeds `lib/src/**/*.hal` (std foundation) as classpath
  resources via `java/pom.xml`; the Rust runtime embeds
  `lib/src/std/lib/foundation.hal` via `include_str!` in `rust/src/lib.rs`.
- `target/` at the repo root is CI scratch/build artifacts; Maven output is
  `java/target/`. Both are gitignored.
- IDE state (`.idea/`, `.settings/`, `.classpath`, `.project`) is user-local
  and untracked.

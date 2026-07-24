# hara-lsp

Planned Language Server Protocol (LSP) server for hara (`.hal` files).

Status: **placeholder** — no implementation yet.

## Intent

A single language server that editor clients talk to over LSP, so
language smarts (diagnostics, completion, hover, go-to-definition,
project-aware namespace loading via `project.hal`) live in one place
instead of being re-implemented per editor.

Planned clients:

- [`hara-vscode`](../hara-vscode/) — VS Code extension (currently
  self-contained; will become an LSP client)
- [`hara-emacs`](../hara-emacs/) — Emacs mode (via `lsp-mode` / `eglot`)

Open design questions (implementation language — Java on the Truffle
runtime vs. Rust on the embedding runtime; feature scope) are tracked in
the project issue tracker.

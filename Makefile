SHELL := /usr/bin/env bash

.DEFAULT_GOAL := help

MAVEN ?= mvn
CARGO ?= cargo
ARGS ?=

TRUFFLE_JAR := target/hara-truffle.jar
TRUFFLE_NATIVE := target/hara-truffle
RUST_MANIFEST := wasm/Cargo.toml
RUST_DEBUG := wasm/target/debug/hara
RUST_RELEASE := wasm/target/release/hara
WASM_RAW := wasm/raw/target/wasm32-unknown-unknown/release/hara_wasm_raw.wasm

.PHONY: help all build-all test-all clean \
        java java-offline java-headless java-build \
        rust rust-offline rust-headless rust-build rust-build-release rust-release \
        native-image native-image-run native-image-offline \
        wasm wasm-build

help: ## Show the available runtime targets
	@echo 'Hara runtimes'
	@echo
	@echo '  make java [ARGS="..."]          JVM Truffle REPL'
	@echo '  make java-offline               JVM Truffle REPL without RESP'
	@echo '  make java-headless              JVM Truffle RESP server'
	@echo '  make rust [ARGS="..."]          Rust native REPL'
	@echo '  make rust-offline               Rust native REPL without RESP'
	@echo '  make rust-headless              Rust native RESP server'
	@echo '  make native-image-run           GraalVM native-image REPL'
	@echo '  make native-image-offline       GraalVM native-image REPL without RESP'
	@echo '  make wasm                       Build the raw WASM module (no terminal REPL)'
	@echo
	@echo 'Build and verification'
	@echo
	@echo '  make build-all                  Build JVM, Rust release, and raw WASM'
	@echo '  make test-all                   Run Java and Rust test suites'
	@echo '  make clean                      Remove Maven and Cargo build output'
	@echo
	@echo 'Examples'
	@echo
	@echo '  make java ARGS="eval '\''(+ 19 23)'\''"'
	@echo '  make rust ARGS="run examples/hello.hal"'
	@echo '  make rust ARGS="remote 127.0.0.1:1311"'

all: help

java-build: $(TRUFFLE_JAR) ## Build the JVM Truffle runtime

$(TRUFFLE_JAR): pom.xml
	$(MAVEN) -Ptruffle -DskipTests package

java: java-build ## Run the JVM Truffle runtime
	./hara $(ARGS)

java-offline: java-build ## Run the JVM Truffle REPL without RESP
	./hara --offline $(ARGS)

java-headless: java-build ## Run the JVM Truffle RESP server
	./hara headless $(ARGS)

rust-build: ## Build the Rust native runtime
	$(CARGO) build --manifest-path $(RUST_MANIFEST) --bin hara

rust-build-release: ## Build the optimized Rust native runtime
	$(CARGO) build --release --manifest-path $(RUST_MANIFEST) --bin hara

rust: rust-build ## Run the Rust native runtime
	$(RUST_DEBUG) $(ARGS)

rust-offline: rust-build ## Run the Rust native REPL without RESP
	$(RUST_DEBUG) --offline $(ARGS)

rust-headless: rust-build ## Run the Rust native RESP server
	$(RUST_DEBUG) headless $(ARGS)

rust-release: rust-build-release ## Run optimized Rust native
	$(RUST_RELEASE) $(ARGS)

native-image: $(TRUFFLE_NATIVE) ## Build the GraalVM native-image runtime

$(TRUFFLE_NATIVE): pom.xml scripts/build-truffle-native
	scripts/build-truffle-native

native-image-run: native-image ## Run the GraalVM native-image runtime
	$(TRUFFLE_NATIVE) $(ARGS)

native-image-offline: native-image ## Run native-image without RESP
	$(TRUFFLE_NATIVE) --offline $(ARGS)

wasm: wasm-build ## Build the raw WASM runtime

wasm-build: ## Build the raw WASM module
	scripts/build-hara-wasm-raw

build-all: java-build rust-build-release wasm-build ## Build all portable runtime artifacts

test-all: ## Run Java and Rust tests
	$(MAVEN) -q test
	$(CARGO) test --manifest-path $(RUST_MANIFEST)

clean: ## Remove generated build output
	$(MAVEN) clean
	$(CARGO) clean --manifest-path $(RUST_MANIFEST)
	$(CARGO) clean --manifest-path wasm/raw/Cargo.toml

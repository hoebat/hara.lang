# hara.lang

`hara.lang` is a Lisp-like language and runtime environment built on the Java Virtual Machine (JVM). It provides an interactive development experience through a TCP server, allowing developers to connect to a running instance, evaluate code, and inspect the system in real-time.

For installation and usage instructions, please see the [Getting Started Guide](GETTING_STARTED.md).

## Motivation

The primary motivation behind `hara.lang` is to provide a dynamic and interactive development environment on the JVM. Traditional Java development often involves a lengthy compile-run-debug cycle. `hara.lang` aims to shorten this feedback loop by allowing developers to connect to a running application, modify code on the fly, and inspect the state of the system without requiring a restart.

## Native Mode

`hara.lang` now includes an experimental native-oriented runtime target intended as the first step toward a babashka-style executable.

This mode is deliberately interpreter-first and disables JVM-dynamic features that do not map cleanly to native-image:

* runtime bytecode compilation (`COMPILE`)
* dynamic Maven dependency loading
* mutable runtime classpaths and classloader scanning
* the server-backed interactive REPL and project setup flows

Use the native CLI entrypoint for small scripts:

* `hara native eval "(+ 1 2)"`
* `hara native run path/to/script.hal`
* `hara native stdin`

For packaging, the Maven build now supports a `native` profile that switches the jar entrypoint to `hara.kernel.NativeMain`:

* `mvn -Pnative package`

This is groundwork for a native target, not full GraalVM compatibility. The full JVM runtime remains the supported mode for dynamic loading, multi-tenant sessions, and runtime compilation.

## Truffle Runtime

The experimental Truffle runtime has a separate CLI and does not replace the existing interpreter.

Build and run the bundled JVM artifact:

```shell
mvn -Ptruffle package
java -jar target/hara-truffle.jar eval '(let [x 19] (+ x 23))'
```

Build and run a native executable with GraalVM Native Image:

```shell
scripts/build-truffle-native
target/hara-truffle eval '(let [x 19] (+ x 23))'
```

The CLI also accepts `run <file>`, `stdin`, `repl`, and `help`. The `repl` command retains one Polyglot context across inputs, supports multiline forms, and continues after guest errors.

The current Hara L0 slice supports:

* literals, `quote`, `do`, `if`, `when`, `when-not`, `and`, and `or`;
* lexical `let`, `loop`/`recur`, closures, variadic and multi-arity functions, and destructuring;
* variadic arithmetic/comparisons, BigInteger/BigDecimal values, and explicit ratio-free numeric behavior;
* persistent collections, protocol-backed `count`/`get`/`assoc`/`conj`/`cons`/`nth`/`empty`, iterator-first collection protocols including lazy cycle/partition/mapcat/keep operations, canonical `bytes` with `byte-count`/`byte-get`/`byte-set`, and mutable `x:array`/`x:object` values;
* context-local Vars, namespaces, `load-string`/`load-file`, transactional module rollback, and compile-time macros;
* immutable `defstruct` values with metadata and Polyglot members;
* language-level protocols via `defprotocol`, `extend-type`, and `protocol-call`, including extending `IFn`;
* explicit capability-gated host interop through `host-symbol`, `host-get`, and `host-call`.

The generated runtime-library namespace contract is documented in
[`spec/hara/runtime-libraries.md`](spec/hara/runtime-libraries.md).

The collection model is iterator-first and does not require `ISeq`, transducers, `transduce`, or `eduction`. Host interop is disabled unless the embedding context grants it. The Native Image workflow runs the verified fallback-runtime profile; optimized Truffle host compilation remains a separate runtime-quality task because the current generic host paths violate GraalVM’s blocklist checks.

The normative L0 contract is maintained in [`spec/hara/l0-language.md`](spec/hara/l0-language.md), with executable examples in [`spec/hara/l0-conformance.edn`](spec/hara/l0-conformance.edn).

### Focused collection protocol tests

Collection protocol tests are split into the same slices used by GitHub Actions so failures can be diagnosed without rerunning the entire suite:

```shell
mvn -Ptruffle -Dtest='hara.lang.data.CollectionsTest,hara.lang.data.ListTest,hara.lang.data.VectorTest,hara.lang.data.QueueTest,hara.lang.data.TupleTest,hara.lang.data.OrderedCollectionsTest,hara.lang.data.SortedCollectionsTest,hara.truffle.CollectionProtocolConformanceTest' test
mvn -Ptruffle -Dtest='hara.lang.protocol.ProtocolTest,hara.truffle.HaraProtocolTest' test
mvn -Ptruffle -Dtest='hara.truffle.HaraInteropTest,hara.truffle.HaraJavaAdaptersTest,hara.kernel.builtin.BuiltinInteropTest,hara.kernel.builtin.BuiltinCollectionTest' test
mvn -Ptruffle -Dtest='hara.lang.base.IterTest,hara.lang.data.ConsTest,hara.core.zip.ZipTest' test
mvn -Ptruffle -Dtest='hara.kernel.builtin.BuiltinStructTest,hara.truffle.HaraLanguageTest' test
```

The collection runtime is iterator-first. These slices do not require `ISeq`, transducers, `transduce`, or `eduction`.


## Design

The core of `hara.lang` is a runtime environment that can be embedded within any Java application. This environment includes:

*   **A Lisp-like language:** The language provides a simple and consistent syntax for expressing code and data.
*   **A custom class loader:** This allows for dynamic loading and unloading of code, enabling developers to modify the system at runtime.
*   **An interactive TCP server:** The server provides a command-line interface for interacting with the runtime. Developers can connect to the server using a simple TCP client and execute commands to evaluate code, inspect variables, and manage the runtime environment.

The project is structured into several key components:

*   **`hara.lang.kernel`:** This package contains the core components of the runtime, including the `Foundation`, `Server`, and `Main` classes.
*   **`hara.lang.lib`:** This package provides the standard library for the language, including the `RT` (runtime) and `Builtin` classes.
*   **`hara.lang.data`:** This package contains the data structures used by the language, such as lists, maps, and vectors.

## Architecture

The `hara.lang` runtime is built on a modular, component-based architecture. At its core is a `Foundation` instance that acts as the central coordinator for the entire system.

### Core Components (`hara.lang.kernel`)

The kernel is the heart of the runtime and manages the application's lifecycle and client interactions.

*   **`Foundation`**: This class is the central hub of the runtime. It holds references to all active servers and runtime sessions (`RT` instances). It is also responsible for command processing, parsing incoming commands from clients, and dispatching them to the appropriate handlers for execution (e.g., `JVM`, `OS`, `EVAL`).

*   **`Server`**: The `Server` component listens for incoming TCP connections from clients. When a new client connects, it spawns a dedicated `Handler` thread to manage that connection. This allows multiple clients to interact with the runtime concurrently.

*   **`Main`**: The main entry point of the application. Its primary responsibility is to bootstrap the system by creating a `Foundation` instance, initializing a primary `Server` and a root runtime (`RT.Instance`), and starting the server to listen for connections.

### Language and Runtime (`hara.lang.lib`)

This library provides the Lisp-like language implementation, including the reader, evaluator, and core functions.

*   **`RT` (Runtime)**: An `RT.Instance` represents an isolated runtime session or environment. Each instance has its own state, including a dedicated class loader (`Loader`) and environment (`UserEnv`). This design allows for managing separate classpaths and namespaces for different sessions. The `eval` command is handled by the `RT.Instance`, which reads a string, parses it into an AST, and evaluates it.

*   **Reader & Evaluator**: The runtime uses a Lisp-style reader (`Read.LispReader`) to parse code from text into an Abstract Syntax Tree (AST). The `Eval` component then traverses this tree to execute the code within a given environment.

*   **`Builtin`**: This class provides the core library of functions and macros available in the language, forming the standard library.

### Data Structures (`hara.lang.data`)

`hara.lang` includes a rich set of custom, persistent data structures that are fundamental to the language. These include:

*   Lists
*   Vectors
*   Maps (Hash Maps, Ordered Maps, Sorted Maps)
*   Sets (Hash Sets, Ordered Sets, Sorted Sets)

These data structures are designed to be immutable, which is a core tenet of functional programming and the Lisp heritage of the language.

### Protocols and Interfaces (`hara.lang.base.I`)

The entire `hara.lang` system is designed around a set of core interfaces (referred to as "protocols," in the Clojure tradition) located in the `hara.lang.base.I` file. This interface-centric design promotes a highly decoupled architecture, allowing for multiple, interchangeable implementations of core functionalities.

The key interface groups include:

*   **Core Data Structures (`Coll`, `Conj`, `Assoc`):** These define the fundamental behaviors for the persistent data structures, such as collection manipulation, iteration, and equality.
*   **Component Model (`Component`, `IComponent`):** This provides a lifecycle management system for high-level components like servers, allowing them to be started, stopped, and queried for their status.
*   **Runtime and Evaluation (`Runtime`, `Env`, `IContext`):** These interfaces define the contract for the language's execution environment, including scope, evaluation logic, and class loading.
*   **State Management (`Deref`, `Reset`, `Watch`):** A set of abstractions for managing mutable state within an immutable-by-default environment, including atomic references and an observer mechanism.
*   **Dependency Management (`IDeps`, `IDepsMutate`):** A protocol for managing dependencies between different modules or components within the system.
*   **Function Arity (`Fn`, `OFn`):** A flexible abstraction for defining functions that can handle a variable number of arguments (arities), a common feature in Lisp-like languages.
*   **I/O and Networking (`IWire`, `IByteSource`, `IArchive`):** A collection of interfaces for abstracting away the details of I/O operations, including network communication and file archiving.

## Roadmap & TODOs

The following is a list of known tasks and missing features identified during the porting process from the reference Clojure implementation.

### Missing Standard Library Modules
Porting these modules from `src-reference/std` to `src/main/java/hara` is a priority.

*   **FileSystem (`std.fs`)**: File manipulation, paths, and attributes.
*   **Concurrency (`std.concurrent`)**: Advanced concurrency tools, atoms, and agents.
*   **Image (`std.image`)**: Image processing and generation.
*   **JSON (`std.json`)**: JSON serialization and parsing.
*   **Time (`std.time`)**: Comprehensive date and time library.
*   **Math (`std.math`)**: Advanced mathematical functions and statistics.

### Documentation
*   **Specification**: Create `SPECIFICATION.md` to detail the language syntax and behavior.

### Refactoring
*   **Foundation**: Improve command dispatch mechanism and extract `Foundation.Fn` static methods into dedicated classes.
*   **Testing**: Improve test coverage for core components.

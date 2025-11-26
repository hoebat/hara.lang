# hara.lang

`hara.lang` is a Lisp-like language and runtime environment built on the Java Virtual Machine (JVM). It provides an interactive development experience through a TCP server, allowing developers to connect to a running instance, evaluate code, and inspect the system in real-time.

## Motivation

The primary motivation behind `hara.lang` is to provide a dynamic and interactive development environment on the JVM. Traditional Java development often involves a lengthy compile-run-debug cycle. `hara.lang` aims to shorten this feedback loop by allowing developers to connect to a running application, modify code on the fly, and inspect the state of the system without requiring a restart.

## Design

The core of `hara.lang` is a runtime environment that can be embedded within any Java application. This environment includes:

*   **A Lisp-like language:** The language provides a simple and consistent syntax for expressing code and data.
*   **A custom class loader:** This allows for dynamic loading and unloading of code, enabling developers to modify the system at runtime.
*   **An interactive TCP server:** The server provides a command-line interface for interacting with the runtime. Developers can connect to the server using a simple TCP client and execute commands to evaluate code, inspect variables, and manage the runtime environment.

The project is structured into several key components:

*   **`hara.lang.kernel`:** This package contains the core components of the runtime, including the `Foundation`, `Server`, and `Main` classes.
*   **`hara.lang.lib`:** This package provides the standard library for the language, including the `RT` (runtime) and `Builtin` classes.
*   **`hara.lang.data`:** This package contains the data structures used by the language, such as lists, maps, and vectors.

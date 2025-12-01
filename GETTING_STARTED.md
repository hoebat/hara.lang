# Getting Started with hara.lang

This guide will help you set up, compile, and run the `hara.lang` runtime environment.

## Prerequisites

Before you begin, ensure you have the following installed on your system:

*   **Java Development Kit (JDK) 21** or higher.
*   **Apache Maven 3.13** or higher.

You can verify your installation by running:

```bash
java -version
mvn -version
```

## Compilation

To compile the project and download all necessary dependencies, navigate to the project root directory and run:

```bash
mvn compile
```

If you want to run the tests as well, you can use:

```bash
mvn package
```

This command will compile the source code, run the unit tests, and package the application into a JAR file.

## Running the REPL

The easiest way to run the `hara.lang` REPL (Read-Eval-Print Loop) is using the Maven Exec plugin:

```bash
mvn exec:java -Dexec.mainClass="hara.kernel.Main"
```

Once started, you should see the following output indicating the runtime is active:

```
Hara Runtime Environment (HRE)
Session: ROOT
>
```

You can now type Lisp expressions at the prompt. For example:

```lisp
> (+ 1 2 3)
6
```

## Connecting via TCP

By default, the `hara.kernel.Main` class starts a TCP server on port **4164**. This allows you to connect to the runtime from external clients or tools.

You can verify the server is running by connecting with `telnet` or `nc` (netcat):

```bash
nc localhost 4164
```

Once connected, you can send commands using the RESP-like protocol or the `EVAL` command to execute code.

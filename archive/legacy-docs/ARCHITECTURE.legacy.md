# Architecture

`hara.lang` is designed as a modular, command-driven runtime environment. The core architectural principle is the separation of the runtime lifecycle (`Foundation`) from the language evaluation (`RT`) and the communication layer (`Server`).

## High-Level Overview

The system is built around a central **Foundation** object which acts as the kernel. It manages:

*   **Servers**: TCP servers that accept client connections.
*   **Sessions (Runtimes)**: Isolated `RT` instances, each with its own classloader and environment, allowing for multi-tenancy within the same JVM.
*   **Peers**: A registry of other `hara.lang` nodes for distributed operations.
*   **Command Registry**: A mapping of command names to executable functions.

Clients communicate with the system via a text-based protocol (RESP-like). The `Server` parses these requests and forwards them to the `Foundation`, which dispatches them to the appropriate command handler.

## Component Diagram

```mermaid
graph TD
    Main[Main Entry Point] --> Foundation

    subgraph Kernel [hara.kernel]
        Foundation[Foundation]
        Registry[Command Registry]
        ServerMap[Active Servers]
        RTMap[Active Sessions/Runtimes]
        PeerMap[Known Peers]
    end

    Foundation --> Registry
    Foundation --> ServerMap
    Foundation --> RTMap
    Foundation --> PeerMap

    ServerMap --> ServerInstance[Server (TCP)]
    RTMap --> RTInstance[RT.Instance (Language Runtime)]

    subgraph Commands [Command Handlers]
        Core[Core Commands]
        Jvm[JVM Info]
        Os[OS Interop]
        Maven[Maven Loader]
        PeerCmd[Peer Management]
        Session[Session Management]
        ServerCmd[Server Management]
    end

    Registry --> Core
    Registry --> Jvm
    Registry --> Os
    Registry --> Maven
    Registry --> PeerCmd
    Registry --> Session
    Registry --> ServerCmd
```

## Command Reference

The `Foundation` exposes a set of commands to manage the system. These are implemented in `hara.kernel.command.*`.

### Core Commands
Basic system operations.

*   `PING`: Returns "PONG". Used for health checks.
*   `ECHO <args>`: Returns the arguments back to the client.
*   `HELP`: Lists available commands.
*   `DIR`: Lists active resources (Servers, Runtimes, Peers).
*   `INFO`: Returns system information (Java version, OS, counts of resources).
*   `SHUTDOWN`: Terminates the JVM.
*   `EVAL <session> <code>`: Evaluates Lisp code within a specific session.
*   `COMPILE <expression>`: Compiles a Lisp expression to bytecode and returns a new instance.

### JVM
Introspection of the Java Virtual Machine.

*   `JVM BOOTPATH`: Returns the boot library path.
*   `JVM CP` / `JVM CLASSPATH`: Returns the system class path.
*   `JVM CLASSLOADER`: Returns the string representation of the system class loader.
*   `JVM ENV [key]`: Returns system environment variables.
*   `JVM HOME`: Returns `java.home`.
*   `JVM PROPS [key]`: Returns Java system properties.
*   `JVM VENDOR`: Returns `java.vendor`.
*   `JVM VERSION`: Returns `java.version`.

### OS
Operating System interoperability.

*   `OS LS [path]`: Lists files in the current or specified directory.
*   `OS PWD`: Returns the current working directory.
*   `OS RUN <cmd> [args...]`: Executes a shell command and returns the output.

### Session
Management of isolated runtime environments (`RT.Instance`).

*   `SESSION NEW <name>`: Creates a new session.
*   `SESSION GET <name>`: Gets an existing session or creates it if it doesn't exist (non-raising).
*   `SESSION EXISTS <name>`: Checks if a session exists.
*   `SESSION KILL <name>`: Removes a session.
*   `SESSION LIST`: Lists all active sessions.
*   `SESSION PATH <name> [ADD|REMOVE|LIST|PURGE] <args>`: Manages the classpath for a specific session.
*   `SESSION INFO`: (Not Implemented) Returns info about a session.

### Peer
Management of known peer nodes.

*   `PEER ADD <name> <host> <port>`: Registers a new peer.
*   `PEER REMOVE <name>`: Removes a peer.
*   `PEER LIST`: Lists all known peers.
*   `PEER PING <name>`: Checks if a peer is known.

### Maven
Dynamic dependency management.

*   `MAVEN LOAD <session> <coordinate>`: Loads a Maven artifact (e.g., `group:artifact:version`) into a specific session.

### Server
Management of TCP servers. (Note: Most subcommands are currently placeholders).

*   `SERVER INFO`: (Not Implemented)
*   `SERVER LIST`: (Not Implemented)
*   `SERVER NEW`: (Not Implemented)
*   `SERVER STOP`: (Not Implemented)
*   `SERVER EXISTS`: (Not Implemented)

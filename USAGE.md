# Hara Language

Hara is a Lisp-like language on the JVM.

## Building

Prerequisites: Java 11+, Maven.

```bash
mvn package
```

This will produce a shaded (fat) JAR in `target/hara.lang-0.0.1-SNAPSHOT-shaded.jar`.

## Running

You can use the provided `bin/hara` script or run the JAR directly.

### REPL (Interactive Shell)

Run without arguments:

```bash
./bin/hara
```
Type expressions like `(+ 1 2)` or `(println "hello")`. Type `exit` to quit.

### Running Scripts

Pass a filename to execute it:

```bash
./bin/hara examples/hello.hara
```

### TCP Server

Start the TCP server (default behavior):

```bash
./bin/hara --server
```

The server listens on port 5353 (default). Logs are written to `log/in.txt` and `log/out.txt`.

## Examples

See `examples/` directory for sample scripts.

- `hello.hara`: Demonstrates basic syntax, functions, and shell interop.

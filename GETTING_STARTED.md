# Getting started

## 1. Install prerequisites

Install JDK 21 and Maven, then verify:

```shell
java -version
mvn -version
```

## 2. Build the Truffle runtime

```shell
mvn -Ptruffle package
```

This produces `target/hara-truffle.jar`.

## 3. Evaluate a form

```shell
java -jar target/hara-truffle.jar eval '(let [x 19] (+ x 23))'
```

Expected result:

```text
42
```

## 4. Start the REPL

```shell
java -jar target/hara-truffle.jar repl
```

The REPL supports multiline forms, persistent history, symbol and Java completion, and inline
documentation. See [`docs/user-guide.md`](docs/user-guide.md) and [`spec/hara/repl.md`](spec/hara/repl.md).

## 5. Run a file or stdin

```shell
java -jar target/hara-truffle.jar run examples/hello.hal
cat examples/hello.hal | java -jar target/hara-truffle.jar stdin
```

## 6. Run tests

```shell
mvn -q test
mvn -q -Ptruffle -Dtest=hara.truffle.HaraL0ConformanceTest test
```

For contributor workflows, test slices, native-image builds, and troubleshooting, read the
[developer guide](docs/development.md).

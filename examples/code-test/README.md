# `code.test` example

Run the sample with the Truffle runtime:

```bash
truffle-hara run examples/code-test/basic.hal
```

The `fact` forms register test bodies. The final `(code.test/run)` executes the registered facts and returns structured results.

`advanced.hal` demonstrates matchers, fixtures, and filtering. `tasks.hal` demonstrates `std.lib.task` and `deftask`.

The Java-backed libraries are discovered through the Hara library-provider SPI and installed lazily when their namespace is required.

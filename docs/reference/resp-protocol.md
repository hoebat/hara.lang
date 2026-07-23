# Hara RESP Protocol

The Truffle runtime listens on `127.0.0.1:1311` by default. RESP is the external control-plane
protocol; HTA is the internal Truffle/WASM ABI.

## Sessions

The server owns sessions. Each session contains one isolated Hara kernel. Clients attach to a
session independently, and multiple clients may attach to the same session. Requests within one
session are serialized.

```text
["SESSION", "NEW", "APP"]
["SESSION", "LIST"]
["SESSION", "ATTACH", "APP"]
["SESSION", "DETACH"]
["SESSION", "INFO"]
["SESSION", "CLOSE", "APP"]
```

`ROOT` is created automatically and sessions remain alive until explicitly closed or the server
exits.

## Evaluation

After `SESSION ATTACH`, requests identify an operation and request ID:

```text
["EVAL", "REQ-1", "(+ 1 2)"]
["DOC", "REQ-2", "str/join"]
["COMPLETE", "REQ-3", "(ma", 3]
```

Responses are streamed as RESP arrays:

```text
["RESULT", "REQ-1", 3]
["DONE", "REQ-1", "OK"]
```

Errors use `ERROR`, followed by the request ID, error code, and message. Clients that omit
`HELLO` remain supported in legacy mode and may use `EVAL SESSION SOURCE`.

## Runtime modes

```text
truffle-hara                  # server on 127.0.0.1:1311
truffle-hara standalone       # one local kernel, no listener
truffle-hara headless         # server without terminal UI
truffle-hara remote HOST:PORT # remote client mode
```

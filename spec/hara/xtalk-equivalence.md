# Hara and xtalk equivalence

Hara exposes runtime-neutral operations under Hara names. A runtime may implement them natively,
but Hara programs do not call public `x:*` symbols.

| Hara surface | Xtalk operation |
| --- | --- |
| `str/len`, `str/comp`, `str/lt?`, `str/gt?` | `x:str-len`, `x:str-comp`, `x:str-lt`, `x:str-gt` |
| remaining `str/*` methods | matching `x:str-*` operations |
| `promise/run` | `x:promise` |
| `promise/new`, `promise/all`, `promise/then`, `promise/catch`, `promise/finally` | matching `x:promise-*` operations |
| `promise/native?`, `promise/delay` | `x:promise-native?`, `x:with-delay` |
| `bytes`, `bytes/count`, `bytes/get`, `bytes/set` | `x:bytes-new`, `x:bytes-count`, `x:bytes-get`, `x:bytes-set` |
| `bytes/copy`, `bytes/slice`, `bytes/u8`, `bytes/s8` | matching `x:bytes-*` operations |
| `file/read`, `file/write` | `x:file-read`, `x:file-write` |
| `socket/connect`, `socket/send`, `socket/close` | matching `x:socket-*` operations |
| array dot methods | matching `x:arr-*` operations |
| object dot methods | matching `x:obj-*` operations |
| `bit-and`, `bit-or`, `bit-xor`, `bit-not`, `bit-shift-left`, `bit-shift-right` | matching `x:bit-*` operations |

`array` and `object` are explicit mutable markers. Their dot-method syntax hides the xtalk
operation names while preserving mutation returns and callback contracts. `socket/connect` is
callback-based; `socket/send` is direct and returns a byte count. File operations return promises.

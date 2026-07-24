# chrome-hara

Chrome (MV3) extension embedding the hara wasm runtime in a DevTools panel.

## Build

    npm install
    npm run build   # builds wasm/raw + copies vendor files

## Load

`chrome://extensions` -> developer mode -> "Load unpacked" -> select this directory.
Open DevTools -> the "hara" panel.

## Test

    npm test               # node unit tests
    npm run test:browser   # playwright (uses xvfb-run)

## RESP bridge

    node bridge/resp-bridge.mjs        # resp=7355, ws=7356

Open the panel with `?resp=ws://127.0.0.1:7356` appended (or call
`connectResp(url, hara.evalSource)` from the console), then:

    redis-cli -p 7355 EVAL "(+ 1 2)"

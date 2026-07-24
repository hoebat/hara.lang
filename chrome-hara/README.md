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

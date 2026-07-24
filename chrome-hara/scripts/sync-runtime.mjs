import { copyFileSync, existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repo = path.resolve(root, "..");
const vendor = path.join(root, "vendor");
mkdirSync(vendor, { recursive: true });

const files = [
  [path.join(repo, "wasm/web/hta.js"), path.join(vendor, "hta.js")],
  [path.join(repo, "wasm/web/hta-worker.js"), path.join(vendor, "hta-worker.js")],
  [
    path.join(repo, "wasm/raw/target/wasm32-unknown-unknown/release/hara_wasm_raw.wasm"),
    path.join(vendor, "hara.wasm"),
  ],
];
for (const [from, to] of files) {
  if (!existsSync(from)) {
    console.error(`missing ${from} — run: bash scripts/build-hara-wasm-raw`);
    process.exit(1);
  }
  copyFileSync(from, to);
  console.log(`synced ${path.basename(to)}`);
}

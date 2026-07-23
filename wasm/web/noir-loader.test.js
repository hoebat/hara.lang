import assert from "node:assert/strict";
import test from "node:test";

import {
  MemoryArtifactCache,
  NoirBrowserLoader,
  NOIR_VERSION
} from "./dist/noir-loader.js";

const program = {
  name: "first_cut",
  source: "fn main(value: u8) { assert(value > 0); }",
  noirVersion: NOIR_VERSION,
  backendVersion: "unbound"
};

test("program identity matches the Truffle cache-key vector", async () => {
  const loader = new NoirBrowserLoader({ cache: new MemoryArtifactCache() });
  assert.equal(
    await loader.cacheKey(program),
    "903bcaf8fa5feb527497bb35d67ca6b7a2427db93a323cb14bfea83653bcb871"
  );
});

test("the browser WASM compiler produces and caches ACIR", async () => {
  const loader = new NoirBrowserLoader({ cache: new MemoryArtifactCache() });
  const artifact = await loader.compile(program);
  const cached = await loader.compile(program);

  assert.equal(artifact.format, "hara.noir.artifact/v1");
  assert.equal(artifact.programKey, await loader.cacheKey(program));
  assert.deepEqual(Object.keys(artifact.circuit), ["program", "warnings"]);
  assert.strictEqual(cached, artifact);
});

test("compiler version mismatches fail closed", async () => {
  const loader = new NoirBrowserLoader({ cache: new MemoryArtifactCache() });
  await assert.rejects(
    loader.compile({ ...program, noirVersion: "1.0.0-beta.24" }),
    /noir\/version-mismatch/
  );
});

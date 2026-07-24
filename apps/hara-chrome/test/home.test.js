import { test } from "node:test";
import assert from "node:assert/strict";
import {
  requireSpecs,
  nsToPath,
  parseSourcePaths,
  readNsSource,
  preloadRequires,
} from "../src/home.js";

test("requireSpecs finds ns-form and standalone requires", () => {
  const source = `
    (ns demo (:require [acme.tools :as tools] [acme.more]))
    (require [extra.lib :as x])
    (tools/seven)`;
  assert.deepEqual(requireSpecs(source), [
    { ns: "acme.tools", alias: "tools" },
    { ns: "acme.more", alias: null },
    { ns: "extra.lib", alias: "x" },
  ]);
});

test("nsToPath follows the hara project convention", () => {
  assert.equal(nsToPath("chrome.api"), "chrome/api.hal");
  assert.equal(nsToPath("my-app.deep.lib-name"), "my_app/deep/lib_name.hal");
});

test("parseSourcePaths reads project.hal or defaults", () => {
  assert.deepEqual(parseSourcePaths('(defproject demo {:source-paths ["src" "lib"]})'), ["src", "lib"]);
  assert.deepEqual(parseSourcePaths("(defproject demo {})"), ["."]);
});

function fakeDir(files) {
  return {
    async getFileHandle(name) {
      if (!(name in files)) throw new Error(`NotFound: ${name}`);
      return { async getFile() { return { async text() { return files[name]; } }; } };
    },
    async getDirectoryHandle(name) {
      const prefix = `${name}/`;
      const sub = Object.fromEntries(
        Object.entries(files)
          .filter(([p]) => p.startsWith(prefix))
          .map(([p, v]) => [p.slice(prefix.length), v]),
      );
      if (Object.keys(sub).length === 0) throw new Error(`NotFound: ${name}`);
      return fakeDir(sub);
    },
  };
}

test("readNsSource resolves through source paths", async () => {
  const dir = fakeDir({ "src/acme/tools.hal": "(ns acme.tools)" });
  assert.equal(await readNsSource(dir, ["src"], "acme.tools"), "(ns acme.tools)");
  assert.equal(await readNsSource(dir, ["src"], "no.such.ns"), null);
});

test("preloadRequires registers transitively, std and loaded skipped", async () => {
  const dir = fakeDir({
    "acme/tools.hal": "(ns acme.tools (:require [acme.base])) (defn seven [] 7)",
    "acme/base.hal": "(ns acme.base) (defn three [] 3)",
  });
  const registered = [];
  const loaded = new Set(["chrome.api"]);
  await preloadRequires("(require [acme.tools :as t] [chrome.api :as api] [std.lib.foundation])", {
    dir,
    sourcePaths: ["."],
    register: async (ns, src) => registered.push([ns, src]),
    loaded,
  });
  assert.deepEqual(
    registered.map(([ns]) => ns).sort(),
    ["acme.base", "acme.tools"],
  );
  assert.ok(loaded.has("acme.tools") && loaded.has("acme.base"));
});

const SPEC_PATTERN = /\[([a-z][a-z0-9-]*(?:\.[a-z0-9-]+)+)(?:\s+:as\s+([a-z][a-z0-9-]*))?[^\]]*\]/g;

/** Extract require specs ([ns :as alias] vectors) from ns and require forms.
 *  Naive by design: scans regions after `require` tokens; may over-match in
 *  strings/comments, which is harmless (extra files get registered). */
export function requireSpecs(source) {
  const specs = [];
  const seen = new Set();
  const requireToken = /require/g;
  let match;
  while ((match = requireToken.exec(source)) !== null) {
    SPEC_PATTERN.lastIndex = match.index;
    let spec;
    while ((spec = SPEC_PATTERN.exec(source)) !== null) {
      if (spec.index > match.index + 400) break; // specs live right after the token
      const ns = spec[1];
      if (!seen.has(ns)) {
        seen.add(ns);
        specs.push({ ns, alias: spec[2] ?? null });
      }
      if (source.indexOf(")", match.index) < spec.index) break;
    }
  }
  return specs;
}

export function nsToPath(ns) {
  return `${ns.replaceAll(".", "/").replaceAll("-", "_")}.hal`;
}

export function parseSourcePaths(projectHal) {
  const match = /:source-paths\s*\[([^\]]*)\]/.exec(projectHal);
  if (!match) return ["."];
  const paths = [...match[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
  return paths.length > 0 ? paths : ["."];
}

export async function readNsSource(dir, sourcePaths, ns) {
  const rel = nsToPath(ns);
  for (const base of sourcePaths) {
    try {
      const parts = base === "." ? [] : base.split("/");
      let current = dir;
      for (const part of parts) current = await current.getDirectoryHandle(part);
      const segments = rel.split("/");
      for (const segment of segments.slice(0, -1)) {
        current = await current.getDirectoryHandle(segment);
      }
      const fileHandle = await current.getFileHandle(segments.at(-1));
      return await (await fileHandle.getFile()).text();
    } catch {
      continue;
    }
  }
  return null;
}

/** Recursively register every required namespace found in `source`. */
export async function preloadRequires(source, { dir, sourcePaths, register, loaded }) {
  for (const { ns } of requireSpecs(source)) {
    if (loaded.has(ns) || ns.startsWith("std.lib.")) continue;
    const text = await readNsSource(dir, sourcePaths, ns);
    if (text === null) throw new Error(`Cannot require missing namespace: ${ns}`);
    loaded.add(ns);
    await preloadRequires(text, { dir, sourcePaths, register, loaded });
    await register(ns, text);
  }
}

const HOME_KEY = "chrome-hara-home";

function idb() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("chrome-hara", 1);
    request.onupgradeneeded = () => request.result.createObjectStore("kv");
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function chooseHome() {
  const dir = await showDirectoryPicker({ mode: "read" });
  const db = await idb();
  await new Promise((resolve, reject) => {
    const tx = db.transaction("kv", "readwrite");
    tx.objectStore("kv").put(dir, HOME_KEY);
    tx.oncomplete = resolve;
    tx.onerror = () => reject(tx.error);
  });
  return dir;
}

export async function restoreHome() {
  const db = await idb();
  const dir = await new Promise((resolve, reject) => {
    const request = db.transaction("kv").objectStore("kv").get(HOME_KEY);
    request.onsuccess = () => resolve(request.result ?? null);
    request.onerror = () => reject(request.error);
  });
  if (dir && (await dir.queryPermission({ mode: "read" })) !== "granted") {
    if ((await dir.requestPermission({ mode: "read" })) !== "granted") return null;
  }
  return dir;
}

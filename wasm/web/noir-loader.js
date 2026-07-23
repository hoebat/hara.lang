import { compile_program, createFileManager } from "./noir-wasm.mjs";

export const NOIR_VERSION = "1.0.0-beta.25";
export const LOADER_ID = `noir_wasm/${NOIR_VERSION}`;

const DOMAIN = new TextEncoder().encode("HARA_NOIR_PROGRAM_V1\0");
const PACKAGE_NAME = /^[a-z][a-z0-9_]*$/;
const CACHE_DATABASE = "hara-noir-artifacts-v1";
const CACHE_STORE = "circuits";

export class NoirBrowserLoader {
  constructor({ cache = new IndexedDbArtifactCache() } = {}) {
    this.cache = cache;
  }

  get id() {
    return LOADER_ID;
  }

  async cacheKey(program) {
    validateProgram(program);
    const encoded = [
      DOMAIN,
      field(program.name),
      field(program.source),
      field(program.noirVersion),
      field(program.backendVersion)
    ];
    const length = encoded.reduce((total, value) => total + value.length, 0);
    const input = new Uint8Array(length);
    let offset = 0;
    for (const value of encoded) {
      input.set(value, offset);
      offset += value.length;
    }
    return hex(await crypto.subtle.digest("SHA-256", input));
  }

  async compile(program) {
    validateProgram(program);
    if (program.noirVersion !== NOIR_VERSION) {
      throw new Error(
        `noir/version-mismatch: loader ${NOIR_VERSION} cannot compile ${program.noirVersion}`
      );
    }

    const programKey = await this.cacheKey(program);
    if (program.cacheKey && program.cacheKey !== programKey) {
      throw new Error("noir/cache-key-mismatch: program descriptor has been altered");
    }
    const cached = await this.cache.get(programKey);
    if (cached) return cached;

    const fileManager = createFileManager("/");
    await fileManager.writeFile("Nargo.toml", stream(manifest(program.name)));
    await fileManager.writeFile("src/main.nr", stream(program.source));
    const artifacts = await compile_program(fileManager, "/", () => {}, () => {});
    const artifact = Object.freeze({
      format: "hara.noir.artifact/v1",
      programKey,
      loaderId: LOADER_ID,
      compilerVersion: NOIR_VERSION,
      backendVersion: program.backendVersion,
      circuit: artifacts
    });
    await this.cache.put(programKey, artifact);
    return artifact;
  }
}

export class MemoryArtifactCache {
  constructor() {
    this.artifacts = new Map();
  }

  async get(key) {
    return this.artifacts.get(key) ?? null;
  }

  async put(key, artifact) {
    this.artifacts.set(key, artifact);
  }
}

export class IndexedDbArtifactCache {
  async get(key) {
    if (!globalThis.indexedDB) return null;
    return request(await this.store("readonly"), "get", key);
  }

  async put(key, artifact) {
    if (!globalThis.indexedDB) return;
    await request(await this.store("readwrite"), "put", artifact, key);
  }

  async store(mode) {
    const database = await new Promise((resolve, reject) => {
      const open = indexedDB.open(CACHE_DATABASE, 1);
      open.onupgradeneeded = () => open.result.createObjectStore(CACHE_STORE);
      open.onsuccess = () => resolve(open.result);
      open.onerror = () => reject(open.error);
    });
    return database.transaction(CACHE_STORE, mode).objectStore(CACHE_STORE);
  }
}

function validateProgram(program) {
  if (!program || typeof program !== "object") throw new TypeError("noir/program is required");
  if (!PACKAGE_NAME.test(program.name ?? "")) {
    throw new TypeError("noir/package-name must match [a-z][a-z0-9_]*");
  }
  for (const name of ["source", "noirVersion", "backendVersion"]) {
    if (typeof program[name] !== "string" || program[name].length === 0) {
      throw new TypeError(`noir/${name} must be a non-empty string`);
    }
  }
}

function manifest(name) {
  return `[package]\nname = "${name}"\ntype = "bin"\n`;
}

function stream(value) {
  return new Blob([value], { type: "text/plain;charset=utf-8" }).stream();
}

function field(value) {
  const bytes = new TextEncoder().encode(value);
  const result = new Uint8Array(8 + bytes.length);
  new DataView(result.buffer).setBigUint64(0, BigInt(bytes.length), false);
  result.set(bytes, 8);
  return result;
}

function hex(value) {
  return Array.from(new Uint8Array(value), byte => byte.toString(16).padStart(2, "0")).join("");
}

async function request(store, method, ...arguments_) {
  return new Promise((resolve, reject) => {
    const operation = store[method](...arguments_);
    operation.onsuccess = () => resolve(operation.result ?? null);
    operation.onerror = () => reject(operation.error);
  });
}

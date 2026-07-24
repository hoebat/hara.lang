import "fake-indexeddb/auto";
import { decodeHta, encodeHta, HtaKeyword } from "../assets/hta.js";
import { callNoir } from "../assets/noir-provider.mjs";

const loaderUrl = new URL("../assets/noir-loader.js", import.meta.url).toString();
const cancelled = new Set();
let buffered = new Uint8Array();
let expected = null;

console.log = (...values) => console.error(...values);
console.info = (...values) => console.error(...values);

process.stdin.on("data", chunk => {
  const next = new Uint8Array(buffered.length + chunk.length);
  next.set(buffered);
  next.set(chunk, buffered.length);
  buffered = next;
  drain();
});
process.stdin.on("end", () => process.exit(0));

function drain() {
  while (true) {
    if (expected === null) {
      if (buffered.length < 4) return;
      expected = new DataView(buffered.buffer, buffered.byteOffset, 4).getUint32(0, false);
      buffered = buffered.slice(4);
      if (expected === 0 || expected > 64 * 1024 * 1024) throw new Error("hta/process-frame-size");
    }
    if (buffered.length < expected) return;
    const frame = buffered.slice(0, expected);
    buffered = buffered.slice(expected);
    expected = null;
    void dispatch(decodeHta(frame));
  }
}

async function dispatch(frame) {
  const [kind, id, operation, args] = frame;
  if (kind === "handshake") {
    write(["ready", 1]);
    return;
  }
  if (kind === "shutdown") {
    process.exit(0);
    return;
  }
  if (kind === "cancel") {
    cancelled.add(Number(id));
    return;
  }
  if (kind !== "call") throw new Error(`hta/process-event-unknown: ${kind}`);
  const requestId = Number(id);
  try {
    const value = await callNoir(loaderUrl, operation, args);
    if (!cancelled.delete(requestId)) write(["result", requestId, toHta(value)]);
  } catch (error) {
    if (!cancelled.delete(requestId)) write(["error", requestId, errorValue(error)]);
  }
}

function write(value) {
  const frame = encodeHta(value);
  const header = new Uint8Array(4);
  new DataView(header.buffer).setUint32(0, frame.length, false);
  process.stdout.write(header);
  process.stdout.write(frame);
}

function toHta(value) {
  if (value === null || value === undefined || typeof value !== "object") return value ?? null;
  if (Array.isArray(value)) return value.map(toHta);
  const result = new Map();
  for (const [key, item] of Object.entries(value)) result.set(new HtaKeyword(key), toHta(item));
  return result;
}

function errorValue(error) {
  const message = String(error?.message ?? error);
  const separator = message.indexOf(":");
  const code = separator > 0 ? message.slice(0, separator) : "noir/error";
  return new Map([
    [new HtaKeyword("code"), new HtaKeyword(code)],
    [new HtaKeyword("message"), message],
    [new HtaKeyword("origin"), new HtaKeyword("node")],
    [new HtaKeyword("retryable"), false]
  ]);
}

import { decodeHta, encodeHta, HtaKeyword } from "../assets/hta.js";
import { callNoir } from "../assets/noir-provider.mjs";

const loaderUrl = new URL("../assets/noir-loader.js", import.meta.url).toString();
const cancelled = new Set();

self.addEventListener("message", async event => {
  const message = event.data;
  try {
    if (message.type === "init") {
      self.postMessage({ type: "ready" });
    } else if (message.type === "cancel") {
      cancelled.add(message.id);
    } else if (message.type === "close") {
      self.close();
    } else if (message.type === "call") {
      const [operation, args] = decodeHta(message.frame);
      try {
        const value = await callNoir(loaderUrl, operation, args);
        if (!cancelled.delete(message.id)) {
          self.postMessage({ type: "result", id: message.id, ok: true, frame: encodeHta(toHta(value)) });
        }
      } catch (error) {
        if (!cancelled.delete(message.id)) {
          self.postMessage({ type: "result", id: message.id, ok: false, frame: encodeHta(errorValue(error)) });
        }
      }
    }
  } catch (error) {
    self.postMessage({ type: "fatal", error: { message: String(error?.message ?? error) } });
  }
});

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
    [new HtaKeyword("origin"), new HtaKeyword("browser")],
    [new HtaKeyword("retryable"), false]
  ]);
}

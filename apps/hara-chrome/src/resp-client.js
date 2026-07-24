import { HtaKeyword } from "../vendor/hta.js";

/** Render an HTA value as a display string for RESP replies. */
export function renderHta(value) {
  if (value === null || value === undefined) return "nil";
  if (value instanceof HtaKeyword) return `:${value.name}`;
  if (value instanceof Map) {
    return `{${[...value].map(([k, v]) => `${renderHta(k)} ${renderHta(v)}`).join(", ")}}`;
  }
  if (value instanceof Set) return `#{${[...value].map(renderHta).join(" ")}}`;
  if (Array.isArray(value)) return `[${value.map(renderHta).join(" ")}]`;
  if (typeof value === "string") return value;
  return String(value);
}

export function connectResp(url, evalSource) {
  const socket = new WebSocket(url);
  socket.onmessage = async (event) => {
    const { id, source } = JSON.parse(event.data);
    try {
      const value = await evalSource(source);
      socket.send(JSON.stringify({ id, ok: true, value: renderHta(value) }));
    } catch (error) {
      socket.send(JSON.stringify({ id, ok: false, error: String(error?.message ?? error) }));
    }
  };
  return socket;
}

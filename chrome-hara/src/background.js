const debuggerEvents = new Map(); // tabId -> { queue: [{method, params}], waiters: [resolve] }

chrome.runtime.onConnect.addListener((port) => {
  if (port.name !== "hara-host") return;
  port.onMessage.addListener(async ({ id, service, method, args }) => {
    try {
      const value = await dispatch(service, method, args ?? []);
      port.postMessage({ id, ok: true, value: sanitize(value) });
    } catch (error) {
      port.postMessage({ id, ok: false, error: String(error?.message ?? error) });
    }
  });
});

chrome.debugger.onEvent.addListener((source, method, params) => {
  const entry = debuggerEvents.get(source.tabId) ?? { queue: [], waiters: [] };
  const waiter = entry.waiters.shift();
  if (waiter) waiter({ method, params });
  else entry.queue.push({ method, params });
  debuggerEvents.set(source.tabId, entry);
});

async function dispatch(service, method, args) {  if (service === "hara" && method === "echo") return args[0] ?? null;
  if (service === "chrome.debugger") return debuggerCall(method, args);
  if (!service.startsWith("chrome.")) {
    throw new Error(`host-call-denied: ${service}`);
  }
  const owner = service
    .slice("chrome.".length)
    .split(".")
    .reduce((value, key) => value?.[key], chrome);
  const fn = owner?.[method];
  if (typeof fn !== "function") {
    throw new Error(`unknown chrome api: ${service}/${method}`);
  }
  return (await fn.apply(owner, args)) ?? null;
}

/** HTA1 has no float tag; coerce non-safe-integer numbers so results survive encoding. */
function sanitize(value) {
  if (typeof value === "number") {
    if (Number.isSafeInteger(value)) return value;
    return Number.isFinite(value) ? Math.trunc(value) : 0;
  }
  if (Array.isArray(value)) return value.map(sanitize);
  if (value !== null && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, sanitize(item)]));
  }
  return value;
}

async function debuggerCall(method, args) {
  const [tabId, ...rest] = args;
  switch (method) {
    case "attach":
      await chrome.debugger.attach({ tabId }, "1.3");
      return null;
    case "detach":
      await chrome.debugger.detach({ tabId });
      return null;
    case "sendCommand": {
      const [command, params] = rest;
      return (await chrome.debugger.sendCommand({ tabId }, command, params ?? {})) ?? null;
    }
    case "next-event": {
      const entry = debuggerEvents.get(tabId) ?? { queue: [], waiters: [] };
      const queued = entry.queue.shift();
      if (queued) return queued;
      return new Promise((resolve) => {
        entry.waiters.push(resolve);
        debuggerEvents.set(tabId, entry);
      });
    }
    default:
      throw new Error(`unknown chrome.debugger method: ${method}`);
  }
}

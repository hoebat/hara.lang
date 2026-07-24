chrome.runtime.onConnect.addListener((port) => {
  if (port.name !== "hara-host") return;
  port.onMessage.addListener(async ({ id, service, method, args }) => {
    try {
      const value = await dispatch(service, method, args ?? []);
      port.postMessage({ id, ok: true, value });
    } catch (error) {
      port.postMessage({ id, ok: false, error: String(error?.message ?? error) });
    }
  });
});

async function dispatch(service, method, args) {
  if (service === "hara" && method === "echo") return args[0] ?? null;
  throw new Error(`host-call-denied: ${service}/${method}`);
}

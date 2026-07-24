import { HtaContext } from "../vendor/hta.js";
import { createHostCalls } from "./host-bridge.js";

const params = new URLSearchParams(location.search);
const tabId = params.has("tabId")
  ? Number(params.get("tabId"))
  : globalThis.chrome?.devtools?.inspectedWindow?.tabId;

const worker = new Worker(chrome.runtime.getURL("vendor/hta-worker.js"), { type: "module" });
const moduleBytes = new Uint8Array(
  await (await fetch(chrome.runtime.getURL("vendor/hara.wasm"))).arrayBuffer(),
);
const port = chrome.runtime.connect({ name: "hara-host" });
const context = new HtaContext({ worker, moduleBytes, hostCalls: createHostCalls(port) });

function evalSource(source) {
  return context.call("eval", [source]);
}

window.hara = { context, evalSource, tabId };

const input = document.getElementById("input");
const output = document.getElementById("output");
function print(text) {
  output.textContent += `${text}\n`;
  output.scrollTop = output.scrollHeight;
}
input.addEventListener("keydown", async (event) => {
  if (event.key !== "Enter" || !event.ctrlKey) return;
  event.preventDefault();
  const source = input.value;
  input.value = "";
  print(`hara=> ${source}`);
  try {
    print(String(await evalSource(source)));
  } catch (error) {
    print(`error: ${error?.message ?? error}`);
  }
});

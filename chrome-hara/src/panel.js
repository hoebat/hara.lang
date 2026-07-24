import { HtaContext } from "../vendor/hta.js";
import { createHostCalls } from "./host-bridge.js";
import { preloadRequires, parseSourcePaths, chooseHome, restoreHome } from "./home.js";
import { connectResp } from "./resp-client.js";

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

const apiSource = await (
  await fetch(chrome.runtime.getURL("src/hara/api.hal"))
).text();
await context.call("register-resource", ["chrome.api", apiSource]);

let homeDir = null;
let homeSourcePaths = ["."];
const loadedResources = new Set(["chrome.api"]);
const register = (ns, text) => context.call("register-resource", [ns, text]);

async function preload(source) {
  if (!homeDir) return;
  await preloadRequires(source, {
    dir: homeDir,
    sourcePaths: homeSourcePaths,
    register,
    loaded: loadedResources,
  });
}

const homeLabel = document.getElementById("home-label");
async function setHome(dir) {
  homeDir = dir;
  homeLabel.textContent = dir ? `home: ${dir.name}` : "no home";
  homeSourcePaths = ["."];
  if (dir) {
    try {
      const projectHal = await (
        await (await dir.getFileHandle("project.hal")).getFile()
      ).text();
      homeSourcePaths = parseSourcePaths(projectHal);
    } catch { /* no project.hal — default paths */ }
  }
}

window.hara = { context, evalSource, preload, setHome, tabId };

if (params.has("resp")) connectResp(params.get("resp"), evalSource);

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
    await preload(source);
    print(String(await evalSource(source)));
  } catch (error) {
    print(`error: ${error?.message ?? error}`);
  }
});

document.getElementById("home-button").addEventListener("click", async () => {
  try { setHome(await chooseHome()); } catch { /* picker cancelled */ }
});
document.getElementById("run-file-button").addEventListener("click", async () => {
  try {
    const [fileHandle] = await showOpenFilePicker({
      types: [{ description: "hara", accept: { "text/plain": [".hal"] } }],
    });
    const source = await (await fileHandle.getFile()).text();
    await preload(source);
    print(`hara=> ${fileHandle.name}`);
    print(String(await evalSource(source)));
  } catch (error) {
    if (error?.name !== "AbortError") print(`error: ${error?.message ?? error}`);
  }
});
setHome(await restoreHome());

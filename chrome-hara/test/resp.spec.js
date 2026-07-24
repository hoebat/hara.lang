import { test, expect } from "@playwright/test";
import { spawn } from "node:child_process";
import net from "node:net";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { launchWithExtension } from "./extension.js";

const bridgePath = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../bridge/resp-bridge.mjs",
);

function respEval(port, source) {
  const bulk = (s) => `$${Buffer.byteLength(s)}\r\n${s}\r\n`;
  return new Promise((resolve, reject) => {
    const socket = net.connect(port, "127.0.0.1");
    let data = "";
    socket.on("data", (chunk) => (data += chunk));
    socket.on("connect", () => socket.end(`*2\r\n${bulk("EVAL")}${bulk(source)}*1\r\n${bulk("QUIT")}`));
    socket.on("end", () => resolve(data));
    socket.on("error", reject);
  });
}

test("RESP EVAL reaches the hara runtime in the extension", async () => {
  const bridge = spawn("node", [bridgePath, "27355", "27356"], { stdio: "ignore" });
  await new Promise((resolve) => setTimeout(resolve, 500));
  try {
    const { context, extensionId } = await launchWithExtension();
    const page = await context.newPage();
    await page.goto(
      `chrome-extension://${extensionId}/src/panel.html?tabId=0&resp=${encodeURIComponent("ws://127.0.0.1:27356")}`,
    );
    await page.waitForFunction(() => globalThis.hara !== undefined);
    await expect
      .poll(async () => respEval(27355, "(+ 40 2)"), { timeout: 10000 })
      .toContain("$2\r\n42\r\n");
    await context.close();
  } finally {
    bridge.kill();
  }
});

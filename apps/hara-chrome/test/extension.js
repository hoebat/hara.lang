import { chromium } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

export async function launchWithExtension() {
  const extensionPath = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  const context = await chromium.launchPersistentContext("", {
    headless: false,
    args: [
      `--disable-extensions-except=${extensionPath}`,
      `--load-extension=${extensionPath}`,
    ],
  });
  const existing = context.serviceWorkers()[0];
  const serviceWorker = existing ?? (await context.waitForEvent("serviceworker"));
  const extensionId = new URL(serviceWorker.url()).host;
  return { context, serviceWorker, extensionId };
}

export async function activeTabId(serviceWorker) {
  return serviceWorker.evaluate(
    async () => (await chrome.tabs.query({ active: true, currentWindow: true }))[0].id,
  );
}

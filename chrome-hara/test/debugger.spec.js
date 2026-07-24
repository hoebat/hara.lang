import { test, expect } from "@playwright/test";
import { launchWithExtension, activeTabId } from "./extension.js";

test("hara attaches to a tab and evaluates JS via CDP", async () => {
  const { context, extensionId, serviceWorker } = await launchWithExtension();
  const target = await context.newPage();
  await target.goto("about:blank");
  const tabId = await activeTabId(serviceWorker);
  const page = await context.newPage();
  await page.goto(`chrome-extension://${extensionId}/src/panel.html?tabId=${tabId}`);
  await page.waitForFunction(() => globalThis.hara !== undefined);
  const value = await page.evaluate(async (tab) => {
    const src = `
      (do
        (deref (host/call "chrome.debugger" "attach" ${tab}))
        (deref (host/call "chrome.debugger" "sendCommand" ${tab} "Runtime.evaluate"
                           {:expression "21 + 21" :returnByValue true})))`;
    const result = await globalThis.hara.evalSource(src);
    const get = (map, key) => [...map].find(([k]) => k.name === key)?.[1];
    return Number(get(get(result, "result"), "value"));
  }, tabId);
  expect(value).toBe(42);
  await context.close();
});

test("generic proxy exposes chrome.tabs", async () => {
  const { context, extensionId, serviceWorker } = await launchWithExtension();
  const target = await context.newPage();
  await target.goto("about:blank");
  await activeTabId(serviceWorker);
  const page = await context.newPage();
  await page.goto(`chrome-extension://${extensionId}/src/panel.html?tabId=0`);
  await page.waitForFunction(() => globalThis.hara !== undefined);
  const count = await page.evaluate(async () => {
    const result = await globalThis.hara.evalSource(
      '(count (deref (host/call "chrome.tabs" "query" {})))',
    );
    return Number(result);
  });
  expect(count).toBeGreaterThanOrEqual(2);
  await context.close();
});

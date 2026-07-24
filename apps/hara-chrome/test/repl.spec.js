import { test, expect } from "@playwright/test";
import { launchWithExtension, activeTabId } from "./extension.js";

test("panel evals hara through the raw wasm runtime", async () => {
  const { context, extensionId } = await launchWithExtension();
  const page = await context.newPage();
  await page.goto(`chrome-extension://${extensionId}/src/panel.html?tabId=0`);
  await page.waitForFunction(() => globalThis.hara !== undefined);
  const value = await page.evaluate(() => globalThis.hara.evalSource("(+ 1 2)"));
  expect(Number(value)).toBe(3);
  await context.close();
});

test("host/call round-trips panel -> service worker -> panel", async () => {
  const { context, extensionId, serviceWorker } = await launchWithExtension();
  const target = await context.newPage();
  await target.goto("about:blank");
  const tabId = await activeTabId(serviceWorker);
  const page = await context.newPage();
  await page.goto(`chrome-extension://${extensionId}/src/panel.html?tabId=${tabId}`);
  await page.waitForFunction(() => globalThis.hara !== undefined);
  const value = await page.evaluate(() =>
    globalThis.hara.evalSource('(deref (host/call "hara" "echo" 42))'),
  );
  expect(Number(value)).toBe(42);
  await context.close();
});

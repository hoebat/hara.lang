import { test, expect } from "@playwright/test";
import { launchWithExtension, activeTabId } from "./extension.js";

test("(require [chrome.api :as api]) drives a tab", async () => {
  const { context, extensionId, serviceWorker } = await launchWithExtension();
  const target = await context.newPage();
  await target.goto("about:blank");
  // Top-level data: navigations are blocked by Chrome, so serve the page
  // hermetically instead of navigating to a data: URL.
  await context.route("http://hara.test/", (route) =>
    route.fulfill({ contentType: "text/html", body: "<title>hara</title>" }),
  );
  const tabId = await activeTabId(serviceWorker);
  const page = await context.newPage();
  await page.goto(`chrome-extension://${extensionId}/src/panel.html?tabId=${tabId}`);
  await page.waitForFunction(() => globalThis.hara !== undefined);
  await page.evaluate(async (tab) => {
    await globalThis.hara.evalSource("(require [chrome.api :as api])");
    await globalThis.hara.evalSource(`(api/attach ${tab})`);
    await globalThis.hara.evalSource(`(api/navigate ${tab} "http://hara.test/")`);
  }, tabId);
  await target.waitForURL("http://hara.test/");
  await target.waitForLoadState("load");
  const value = await page.evaluate(async (tab) => {
    const result = await globalThis.hara.evalSource(
      `(api/eval-js ${tab} "document.title")`,
    );
    const get = (map, key) => [...map].find(([k]) => k.name === key)?.[1];
    return get(get(result, "result"), "value");
  }, tabId);
  expect(value).toBe("hara");
  await context.close();
});

import { test, expect } from "@playwright/test";
import { launchWithExtension, activeTabId } from "./extension.js";

const FAKE_HOME = `
  ({
    name: "fake-home",
    async getFileHandle(name) {
      const files = {
        "project.hal": '(defproject demo {:source-paths ["src"]})',
      };
      if (name in files) return { async getFile() { return { async text() { return files[name]; } }; } };
      throw new Error("NotFound: " + name);
    },
    async getDirectoryHandle(name) {
      if (name !== "src") throw new Error("NotFound: " + name);
      const files = {
        "acme/tools.hal": "(ns acme.tools (:require [acme.base])) (defn total [] (+ (acme.base/three) 4))",
        "acme/base.hal": "(ns acme.base) (defn three [] 3)",
      };
      return {
        async getDirectoryHandle(sub) {
          if (sub !== "acme") throw new Error("NotFound: " + sub);
          return {
            async getFileHandle(file) {
              const key = "acme/" + file;
              if (key in files) return { async getFile() { return { async text() { return files[key]; } }; } };
              throw new Error("NotFound: " + file);
            },
          };
        },
      };
    },
  })
`;

test("requires resolve from the home directory transitively", async () => {
  const { context, extensionId } = await launchWithExtension();
  const page = await context.newPage();
  await page.goto(`chrome-extension://${extensionId}/src/panel.html?tabId=0`);
  await page.waitForFunction(() => globalThis.hara !== undefined);
  const value = await page.evaluate(async (fakeHomeSource) => {
    const dir = eval(fakeHomeSource);
    await globalThis.hara.setHome(dir);
    const source = "(require [acme.tools :as t]) (t/total)";
    await globalThis.hara.preload(source);
    return globalThis.hara.evalSource(source);
  }, FAKE_HOME);
  expect(Number(value)).toBe(7);
  await context.close();
});

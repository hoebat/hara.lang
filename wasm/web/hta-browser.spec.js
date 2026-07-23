import { test, expect } from "@playwright/test";
test("real worker resumes a pending HTA evaluator fiber",async({page})=>{await page.goto("/wasm/web/hta-browser.html");await expect.poll(()=>page.evaluate(()=>window.htaSmoke?.then(String))).toBe("42");await page.evaluate(()=>window.htaContext.close());});

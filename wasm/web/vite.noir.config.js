import { defineConfig } from "vite";
import { resolve } from "node:path";

export default defineConfig({
  build: {
    target: "es2022",
    outDir: "dist",
    emptyOutDir: true,
    rollupOptions: {
      external: ["./noir-wasm.mjs"]
    },
    lib: {
      entry: resolve(import.meta.dirname, "noir-loader.js"),
      formats: ["es"],
      fileName: "noir-loader"
    }
  }
});

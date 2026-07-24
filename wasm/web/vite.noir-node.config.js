import { defineConfig } from "vite";
import { resolve } from "node:path";

export default defineConfig({
  resolve: {
    alias: {
      "../assets/hta.js": resolve(import.meta.dirname, "hta.js"),
      "fake-indexeddb/auto": resolve(import.meta.dirname, "node_modules/fake-indexeddb/auto/index.mjs")
    }
  },
  build: {
    target: "node18",
    outDir: "dist-node",
    emptyOutDir: true,
    lib: {
      entry: resolve(import.meta.dirname, "../extensions/blockchain-proof-noir/node/worker.mjs"),
      formats: ["es"],
      fileName: () => "worker.mjs"
    },
    rollupOptions: {
      output: { inlineDynamicImports: true }
    }
  }
});

import { test } from "node:test";
import assert from "node:assert/strict";
import net from "node:net";
import WebSocket from "ws";
import { startBridge } from "../bridge/resp-bridge.mjs";

function respClient(port, chunks) {
  return new Promise((resolve, reject) => {
    const socket = net.connect(port, "127.0.0.1");
    let data = "";
    socket.on("data", (chunk) => (data += chunk));
    socket.on("connect", () => socket.end(chunks));
    socket.on("end", () => resolve(data));
    socket.on("error", reject);
  });
}

const bulk = (s) => `$${Buffer.byteLength(s)}\r\n${s}\r\n`;

test("EVAL forwards to the extension and replies bulk", async () => {
  const bridge = await startBridge({ respPort: 17355, wsPort: 17356 });
  const extension = new WebSocket("ws://127.0.0.1:17356");
  extension.on("message", (raw) => {
    const { id, source } = JSON.parse(raw);
    extension.send(JSON.stringify({ id, ok: true, value: `echoed:${source}` }));
  });
  await new Promise((resolve) => extension.on("open", resolve));
  const reply = await respClient(17355, `*2\r\n${bulk("EVAL")}${bulk("(+ 1 2)")}*1\r\n${bulk("QUIT")}`);
  assert.ok(reply.includes("$14\r\nechoed:(+ 1 2)\r\n"), reply);
  assert.ok(reply.endsWith("+OK\r\n"), reply);
  await bridge.close();
});

test("EVAL without a connected extension is an error", async () => {
  const bridge = await startBridge({ respPort: 17357, wsPort: 17358 });
  const reply = await respClient(17357, `*2\r\n${bulk("EVAL")}${bulk("1")}*1\r\n${bulk("QUIT")}`);
  assert.ok(reply.startsWith("-ERR hara extension not connected"), reply);
  await bridge.close();
});

test("malformed bulk size is a protocol error and the bridge stays alive", async () => {
  const bridge = await startBridge({ respPort: 17359, wsPort: 17360 });
  const reply = await respClient(17359, `*1\r\n$abc\r\nPING\r\n`);
  assert.ok(reply.startsWith("-ERR Protocol error"), reply);
  const pong = await respClient(17359, `*1\r\n${bulk("PING")}*1\r\n${bulk("QUIT")}`);
  assert.ok(pong.startsWith("+PONG"), pong);
  await bridge.close();
});

import net from "node:net";
import { WebSocketServer } from "ws";

/** Minimal RESP2 reader: arrays of bulk/simple strings only. */
function createRespParser(onCommand) {
  let buffer = Buffer.alloc(0);
  let tail = Promise.resolve();
  return (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    // Serialize command handling: an async command (EVAL) must finish
    // replying before the next command (e.g. QUIT) touches the socket.
    tail = tail.then(async () => {
      for (;;) {
        const command = readCommand(buffer);
        if (!command) return;
        buffer = buffer.subarray(command.consumed);
        await onCommand(command.args);
      }
    });
  };
}

function readCommand(buffer) {
  if (buffer.length === 0 || buffer[0] !== 0x2a /* * */) return null;
  const lineEnd = buffer.indexOf("\r\n");
  if (lineEnd === -1) return null;
  const count = Number(buffer.subarray(1, lineEnd).toString());
  const args = [];
  let cursor = lineEnd + 2;
  for (let i = 0; i < count; i++) {
    if (buffer[cursor] !== 0x24 /* $ */) return null;
    const sizeEnd = buffer.indexOf("\r\n", cursor);
    if (sizeEnd === -1) return null;
    const size = Number(buffer.subarray(cursor + 1, sizeEnd).toString());
    if (buffer.length < sizeEnd + 2 + size + 2) return null;
    args.push(buffer.subarray(sizeEnd + 2, sizeEnd + 2 + size).toString());
    cursor = sizeEnd + 2 + size + 2;
  }
  return { args, consumed: cursor };
}

export async function startBridge({ respPort = 7355, wsPort = 7356 }) {
  let extension = null;
  const pending = new Map();
  let next = 1;

  const wss = new WebSocketServer({ port: wsPort, host: "127.0.0.1" });
  wss.on("connection", (socket) => {
    extension = socket;
    socket.on("message", (raw) => {
      const { id, ok, value, error } = JSON.parse(raw);
      const entry = pending.get(id);
      if (!entry) return;
      pending.delete(id);
      ok ? entry.resolve(value) : entry.reject(new Error(error ?? "eval failed"));
    });
    socket.on("close", () => {
      if (extension === socket) extension = null;
    });
  });

  const evalInExtension = (source) =>
    new Promise((resolve, reject) => {
      if (!extension) {
        reject(new Error("hara extension not connected"));
        return;
      }
      const id = next++;
      pending.set(id, { resolve, reject });
      extension.send(JSON.stringify({ id, source }));
    });

  // allowHalfOpen: clients may send their commands and FIN in one write
  // (socket.end(payload)); replies must still be writable afterwards.
  const server = net.createServer({ allowHalfOpen: true }, (socket) => {
    const write = {
      simple: (s) => socket.write(`+${s}\r\n`),
      error: (s) => socket.write(`-ERR ${s}\r\n`),
      bulk: (s) => socket.write(`$${Buffer.byteLength(s)}\r\n${s}\r\n`),
    };
    socket.on(
      "data",
      createRespParser(async (args) => {
        const command = (args[0] ?? "").toUpperCase();
        try {
          switch (command) {
            case "PING": write.simple("PONG"); break;
            case "HELLO": write.simple("OK"); break;
            case "INFO": write.bulk("chrome-hara resp bridge (subset: PING HELLO EVAL INFO QUIT)"); break;
            case "EVAL": write.bulk(String(await evalInExtension(args[1] ?? ""))); break;
            case "QUIT": write.simple("OK"); socket.end(); break;
            default: write.error(`unknown command: ${command}`);
          }
        } catch (error) {
          write.error(String(error?.message ?? error));
        }
      }),
    );
  });

  await Promise.all([
    new Promise((resolve) => server.listen(respPort, "127.0.0.1", resolve)),
    new Promise((resolve) => wss.on("listening", resolve)),
  ]);
  return {
    close: () => {
      // ws does not close existing connections; terminate them so close() resolves.
      for (const client of wss.clients) client.terminate();
      return Promise.all([
        new Promise((resolve) => server.close(resolve)),
        new Promise((resolve) => wss.close(resolve)),
      ]);
    },
  };
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const respPort = Number(process.argv[2] ?? 7355);
  const wsPort = Number(process.argv[3] ?? 7356);
  await startBridge({ respPort, wsPort });
  console.log(`chrome-hara resp bridge: resp=127.0.0.1:${respPort} ws=127.0.0.1:${wsPort}`);
}

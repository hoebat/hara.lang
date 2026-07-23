import assert from "node:assert/strict";
import test from "node:test";
import { decodeHta, encodeHta, HtaContext, HtaKeyword } from "./hta.js";

test("HTA1 browser codec matches the Java/Rust golden vector",()=>{assert.deepEqual([...encodeHta(["x",42,true])],[72,84,65,49,9,0,0,0,3,4,0,0,0,1,120,3,0,0,0,0,0,0,0,42,2]);assert.deepEqual(decodeHta(encodeHta(["x",42,true])),["x",42,true]);});
test("canonical maps ignore insertion order",()=>{const a=new Map([[new HtaKeyword("b"),2],[new HtaKeyword("a"),1]]),b=new Map([[new HtaKeyword("a"),1],[new HtaKeyword("b"),2]]);assert.deepEqual(encodeHta(a),encodeHta(b));});
test("context exposes worker results as promises",async()=>{const worker=new FakeWorker();const context=new HtaContext({worker,moduleUrl:"runtime.wasm"});worker.emit({type:"ready"});const result=context.call("eval",["(+ 1 2)"]);await Promise.resolve();const call=worker.sent.find(message=>message.type==="call");worker.emit({type:"result",id:call.id,ok:true,frame:encodeHta(3)});assert.equal(await result,3);context.close();});
test("context cancellation is forwarded to its worker",async()=>{const worker=new FakeWorker();const context=new HtaContext({worker,moduleUrl:"runtime.wasm"});worker.emit({type:"ready"});const result=context.call("eval",["slow"]);result.cancel();await Promise.resolve();await Promise.resolve();assert.equal(worker.sent.at(-1).type,"cancel");context.close();});

class FakeWorker{constructor(){this.listeners={};this.sent=[];}addEventListener(type,handler){this.listeners[type]=handler;}postMessage(message){this.sent.push(message);}emit(data){this.listeners.message({data});}terminate(){this.terminated=true;}}

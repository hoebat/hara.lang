const MAGIC = new Uint8Array([0x48, 0x54, 0x41, 0x31]);
const TAG = { nil: 0, false: 1, true: 2, i64: 3, string: 4, bytes: 5, keyword: 6, symbol: 7, list: 8, vector: 9, set: 10, map: 11 };
const encoder = new TextEncoder();
const decoder = new TextDecoder("utf-8", { fatal: true });

export class HtaKeyword { constructor(name) { this.name = name; } }
export class HtaSymbol { constructor(name) { this.name = name; } }

export function encodeHta(value) {
  const output = [...MAGIC];
  writeValue(output, value);
  return Uint8Array.from(output);
}

export function decodeHta(input) {
  const bytes = input instanceof Uint8Array ? input : new Uint8Array(input);
  if (bytes.length < 4 || !MAGIC.every((byte, index) => bytes[index] === byte)) {
    throw new Error("hta/value-malformed: invalid HTA1 header");
  }
  const reader = new Reader(bytes, 4);
  const value = reader.value();
  if (reader.cursor !== bytes.length) throw new Error("hta/value-malformed: trailing bytes");
  return value;
}

function writeValue(output, value) {
  if (value === null || value === undefined) output.push(TAG.nil);
  else if (value === false) output.push(TAG.false);
  else if (value === true) output.push(TAG.true);
  else if (typeof value === "bigint" || Number.isSafeInteger(value)) {
    output.push(TAG.i64); writeI64(output, BigInt(value));
  } else if (typeof value === "string") { output.push(TAG.string); writeBytes(output, encoder.encode(value)); }
  else if (value instanceof Uint8Array) { output.push(TAG.bytes); writeBytes(output, value); }
  else if (value instanceof HtaKeyword) { output.push(TAG.keyword); writeBytes(output, encoder.encode(value.name)); }
  else if (value instanceof HtaSymbol) { output.push(TAG.symbol); writeBytes(output, encoder.encode(value.name)); }
  else if (Array.isArray(value)) { output.push(TAG.vector); writeSequence(output, value); }
  else if (value instanceof Set) { output.push(TAG.set); writeCanonical(output, [...value]); }
  else if (value instanceof Map) {
    const entries = [...value].map(([key, item]) => [bare(key), bare(item)]).sort((a, b) => compare(a[0], b[0]));
    output.push(TAG.map); writeU32(output, entries.length);
    for (const [key, item] of entries) output.push(...key, ...item);
  } else throw new Error(`hta/value-unsupported: ${Object.prototype.toString.call(value)}`);
}

function bare(value) { const output = []; writeValue(output, value); return output; }
function writeSequence(output, values) { writeU32(output, values.length); for (const value of values) writeValue(output, value); }
function writeCanonical(output, values) { const encoded = values.map(bare).sort(compare); writeU32(output, encoded.length); for (const value of encoded) output.push(...value); }
function compare(left, right) { for (let i=0;i<Math.min(left.length,right.length);i++) if(left[i]!==right[i]) return left[i]-right[i]; return left.length-right.length; }
function writeBytes(output, bytes) { writeU32(output, bytes.length); output.push(...bytes); }
function writeU32(output, value) { if(value<0||value>0xffff_ffff)throw new Error("hta/value-too-large"); output.push(value>>>24,(value>>>16)&255,(value>>>8)&255,value&255); }
function writeI64(output, value) { const normalized=BigInt.asUintN(64,value); for(let shift=56n;shift>=0n;shift-=8n)output.push(Number((normalized>>shift)&255n)); }

class Reader {
  constructor(bytes, cursor) { this.bytes=bytes; this.cursor=cursor; }
  take(size) { const end=this.cursor+size; if(end>this.bytes.length)throw new Error("hta/value-malformed: truncated value"); const value=this.bytes.subarray(this.cursor,end);this.cursor=end;return value; }
  u32() { const value=this.take(4); return ((value[0]*0x1000000)+(value[1]<<16)+(value[2]<<8)+value[3])>>>0; }
  data() { return this.take(this.u32()); }
  sequence() { const size=this.u32(), result=[]; for(let i=0;i<size;i++)result.push(this.value()); return result; }
  value() {
    const tag=this.take(1)[0];
    if(tag===TAG.nil)return null;if(tag===TAG.false)return false;if(tag===TAG.true)return true;
    if(tag===TAG.i64){const bytes=this.take(8);let value=0n;for(const byte of bytes)value=(value<<8n)|BigInt(byte);value=BigInt.asIntN(64,value);return value>=BigInt(Number.MIN_SAFE_INTEGER)&&value<=BigInt(Number.MAX_SAFE_INTEGER)?Number(value):value;}
    if(tag===TAG.string)return decoder.decode(this.data());if(tag===TAG.bytes)return this.data().slice();
    if(tag===TAG.keyword)return new HtaKeyword(decoder.decode(this.data()));if(tag===TAG.symbol)return new HtaSymbol(decoder.decode(this.data()));
    if(tag===TAG.list||tag===TAG.vector)return this.sequence();if(tag===TAG.set)return new Set(this.sequence());
    if(tag===TAG.map){const size=this.u32(),result=new Map();for(let i=0;i<size;i++)result.set(this.value(),this.value());return result;}
    throw new Error("hta/value-malformed: unknown value tag");
  }
}

export class HtaContext {
  constructor({ worker, moduleUrl, moduleBytes, hostCalls = {} }) {
    this.worker=worker;this.hostCalls=hostCalls;this.next=1;this.pending=new Map();
    this.ready=new Promise((resolve,reject)=>{this.readyResolve=resolve;this.readyReject=reject;});
    worker.addEventListener("message", event=>this.message(event.data));
    worker.addEventListener("error", error=>this.fail(error));
    worker.postMessage({type:"init",moduleUrl,moduleBytes});
  }
  async call(target, args=[]) { await this.ready; const id=this.next++; let cancel;
    const promise=new Promise((resolve,reject)=>{this.pending.set(id,{resolve,reject});cancel=()=>this.worker.postMessage({type:"cancel",id});});
    promise.cancel=cancel;this.worker.postMessage({type:"call",id,frame:encodeHta([target,args])});return promise;
  }
  async message(message) {
    if(message.type==="ready"){this.readyResolve();return;}if(message.type==="fatal"){this.fail(errorFrom(message.error));return;}
    if(message.type==="result"){const pending=this.pending.get(message.id);if(!pending)return;this.pending.delete(message.id);message.ok?pending.resolve(decodeHta(message.frame)):pending.reject(errorFrom(decodeHta(message.frame)));return;}
    if(message.type==="host-call"){const key=`${message.service}/${message.method}`,handler=this.hostCalls[key];try{if(!handler)throw new Error(`hta/host-call-denied: ${key}`);const value=await handler(...decodeHta(message.frame));this.worker.postMessage({type:"delivery",call:message.call,ok:true,frame:encodeHta(value)});}catch(error){this.worker.postMessage({type:"delivery",call:message.call,ok:false,frame:encodeHta(errorValue(error))});}}
  }
  fail(error){this.readyReject(error);for(const pending of this.pending.values())pending.reject(error);this.pending.clear();}
  close(){this.worker.postMessage({type:"close"});this.worker.terminate();}
}

function errorValue(error){return new Map([[new HtaKeyword("code"),new HtaKeyword("host/error")],[new HtaKeyword("message"),String(error?.message??error)],[new HtaKeyword("origin"),new HtaKeyword("browser")],[new HtaKeyword("retryable"),false]]);}
function errorFrom(value){if(value instanceof Error)return value;if(value instanceof Map){for(const[key,item]of value)if(key instanceof HtaKeyword&&key.name==="message")return new Error(String(item));}return new Error(String(value));}

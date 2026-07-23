use crate::core::Value;
#[cfg(test)]
use im_rc::Vector as PVector;

const MAGIC: &[u8; 4] = b"HTA1";
const NIL: u8 = 0;
const FALSE: u8 = 1;
const TRUE: u8 = 2;
const I64: u8 = 3;
const STRING: u8 = 4;
const BYTES: u8 = 5;
const KEYWORD: u8 = 6;
const SYMBOL: u8 = 7;
const LIST: u8 = 8;
const VECTOR: u8 = 9;
const SET: u8 = 10;
const MAP: u8 = 11;
const HANDLE: u8 = 12;

pub fn encode(value: &Value) -> Result<Vec<u8>, String> {
    let mut output = MAGIC.to_vec();
    encode_bare(value, &mut output)?;
    Ok(output)
}

pub fn decode(bytes: &[u8]) -> Result<Value, String> {
    if !bytes.starts_with(MAGIC) { return Err("hta/value-malformed: invalid HTA1 header".into()); }
    let mut reader = Reader { bytes, cursor: MAGIC.len() };
    let value = reader.value()?;
    if reader.cursor != bytes.len() { return Err("hta/value-malformed: trailing bytes".into()); }
    Ok(value)
}

fn encode_bare(value: &Value, output: &mut Vec<u8>) -> Result<(), String> {
    match value {
        Value::Nil => output.push(NIL),
        Value::Bool(false) => output.push(FALSE),
        Value::Bool(true) => output.push(TRUE),
        Value::Number(value) => { output.push(I64); output.extend_from_slice(&value.to_be_bytes()); }
        Value::String(value) => { output.push(STRING); encode_bytes(value.as_bytes(), output)?; }
        Value::Bytes(value) => { output.push(BYTES); encode_bytes(value, output)?; }
        Value::ByteBuffer(value) => { output.push(BYTES); encode_bytes(&value.borrow(), output)?; }
        Value::Keyword(value) => { output.push(KEYWORD); encode_bytes(value.as_bytes(), output)?; }
        Value::Symbol(value) => { output.push(SYMBOL); encode_bytes(value.as_bytes(), output)?; }
        Value::List(values) => encode_sequence(LIST, values.iter(), output)?,
        Value::Vector(values) => encode_sequence(VECTOR, values.iter(), output)?,
        Value::Set(values) => {
            let mut encoded = values.iter().map(bare).collect::<Result<Vec<_>, _>>()?;
            encoded.sort();
            output.push(SET); encode_len(encoded.len(), output)?;
            for value in encoded { output.extend_from_slice(&value); }
        }
        Value::Map(values) => {
            let mut encoded = values.iter().map(|(key, value)| Ok((bare(key)?, bare(value)?))).collect::<Result<Vec<_>, String>>()?;
            encoded.sort_by(|left, right| left.0.cmp(&right.0));
            output.push(MAP); encode_len(encoded.len(), output)?;
            for (key, value) in encoded { output.extend_from_slice(&key); output.extend_from_slice(&value); }
        }
        Value::Extension(value) => {
            output.push(HANDLE);
            encode_bytes(value.provider.as_bytes(), output)?;
            encode_bytes(value.type_name.as_bytes(), output)?;
            output.extend_from_slice(&value.handle.to_be_bytes());
        }
        _ => return Err(format!("hta/value-unsupported: {}", value.display())),
    }
    Ok(())
}

fn bare(value: &Value) -> Result<Vec<u8>, String> { let mut output=Vec::new(); encode_bare(value, &mut output)?; Ok(output) }

fn encode_sequence<'a>(tag: u8, values: impl Iterator<Item=&'a Value>, output: &mut Vec<u8>) -> Result<(), String> {
    let values = values.collect::<Vec<_>>(); output.push(tag); encode_len(values.len(), output)?;
    for value in values { encode_bare(value, output)?; }
    Ok(())
}

fn encode_bytes(value: &[u8], output: &mut Vec<u8>) -> Result<(), String> { encode_len(value.len(), output)?; output.extend_from_slice(value); Ok(()) }
fn encode_len(value: usize, output: &mut Vec<u8>) -> Result<(), String> { let value=u32::try_from(value).map_err(|_| "hta/value-too-large")?; output.extend_from_slice(&value.to_be_bytes()); Ok(()) }

struct Reader<'a> { bytes: &'a [u8], cursor: usize }
impl Reader<'_> {
    fn value(&mut self) -> Result<Value, String> {
        let tag=self.byte()?;
        match tag {
            NIL=>Ok(Value::Nil), FALSE=>Ok(Value::Bool(false)), TRUE=>Ok(Value::Bool(true)),
            I64=>{ let bytes=self.take(8)?; Ok(Value::Number(i64::from_be_bytes(bytes.try_into().unwrap()))) },
            STRING=>Ok(Value::String(String::from_utf8(self.data()?.to_vec()).map_err(|_| "hta/value-malformed: invalid UTF-8")?)),
            BYTES=>Ok(Value::Bytes(self.data()?.to_vec())),
            KEYWORD=>Ok(Value::Keyword(String::from_utf8(self.data()?.to_vec()).map_err(|_| "hta/value-malformed: invalid UTF-8")?)),
            SYMBOL=>Ok(Value::Symbol(String::from_utf8(self.data()?.to_vec()).map_err(|_| "hta/value-malformed: invalid UTF-8")?)),
            LIST=>Ok(Value::List(self.sequence()?.into())), VECTOR=>Ok(Value::Vector(self.sequence()?.into())),
            SET=>Ok(Value::Set(self.sequence()?)),
            MAP=>{ let size=self.len()?; let mut values=Vec::with_capacity(size); for _ in 0..size { values.push((self.value()?,self.value()?)); } Ok(Value::Map(values)) },
            HANDLE=>{let provider=String::from_utf8(self.data()?.to_vec()).map_err(|_|"hta/value-malformed: invalid handle owner")?;let type_name=String::from_utf8(self.data()?.to_vec()).map_err(|_|"hta/value-malformed: invalid handle type")?;let bytes=self.take(8)?;Ok(Value::Extension(crate::core::ExtensionValue{provider,type_name,handle:u64::from_be_bytes(bytes.try_into().unwrap())}))},
            _=>Err("hta/value-malformed: unknown value tag".into()),
        }
    }
    fn sequence(&mut self)->Result<Vec<Value>,String>{ let size=self.len()?; (0..size).map(|_|self.value()).collect() }
    fn data(&mut self)->Result<&[u8],String>{ let size=self.len()?; self.take(size) }
    fn len(&mut self)->Result<usize,String>{ let bytes=self.take(4)?; Ok(u32::from_be_bytes(bytes.try_into().unwrap()) as usize) }
    fn byte(&mut self)->Result<u8,String>{ Ok(self.take(1)?[0]) }
    fn take(&mut self,size:usize)->Result<&[u8],String>{ let end=self.cursor.checked_add(size).ok_or("hta/value-malformed: length overflow")?; if end>self.bytes.len(){return Err("hta/value-malformed: truncated value".into());} let output=&self.bytes[self.cursor..end]; self.cursor=end; Ok(output) }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn canonical_round_trip() {
        let value=Value::Map(vec![(Value::Keyword("b".into()),Value::Number(2)),(Value::Keyword("a".into()),Value::Vector(PVector::from(vec![Value::Bool(true),Value::Nil]))) ]);
        let encoded=encode(&value).unwrap();
        assert_eq!(encode(&decode(&encoded).unwrap()).unwrap(), encoded);
    }
    #[test]
    fn canonical_maps_ignore_insertion_order() {
        let a=Value::Map(vec![(Value::String("b".into()),Value::Number(2)),(Value::String("a".into()),Value::Number(1))]);
        let b=Value::Map(vec![(Value::String("a".into()),Value::Number(1)),(Value::String("b".into()),Value::Number(2))]);
        assert_eq!(encode(&a).unwrap(),encode(&b).unwrap());
    }
    #[test]
    fn opaque_handles_round_trip() {
        let value=Value::Extension(crate::core::ExtensionValue{provider:"runtime".into(),type_name:"cursor".into(),handle:42});
        assert_eq!(decode(&encode(&value).unwrap()).unwrap(),value);
    }
}

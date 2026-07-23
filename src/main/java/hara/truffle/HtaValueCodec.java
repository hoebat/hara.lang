package hara.truffle;

import hara.lang.data.Keyword;
import hara.lang.data.Symbol;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.IMapType;
import hara.lang.data.types.ISetType;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Canonical, dependency-free value encoding used by HTA v1. */
final class HtaValueCodec {
  private static final byte[] MAGIC = {'H', 'T', 'A', '1'};
  private static final int NIL = 0;
  private static final int FALSE = 1;
  private static final int TRUE = 2;
  private static final int I64 = 3;
  private static final int STRING = 4;
  private static final int BYTES = 5;
  private static final int KEYWORD = 6;
  private static final int SYMBOL = 7;
  private static final int LIST = 8;
  private static final int VECTOR = 9;
  private static final int SET = 10;
  private static final int MAP = 11;

  private HtaValueCodec() {}

  static byte[] encode(Object value) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.writeBytes(MAGIC);
    write(output, HaraBox.unwrap(value));
    return output.toByteArray();
  }

  static Object decode(byte[] bytes) {
    if (bytes.length < MAGIC.length) throw malformed("missing HTA1 header");
    for (int i = 0; i < MAGIC.length; i++) {
      if (bytes[i] != MAGIC[i]) throw malformed("invalid HTA1 header");
    }
    Reader reader = new Reader(bytes, MAGIC.length);
    Object value = reader.read();
    if (reader.remaining() != 0) throw malformed("trailing bytes");
    return value;
  }

  private static void write(ByteArrayOutputStream output, Object value) {
    if (value == null || value == HaraNull.SINGLETON) {
      output.write(NIL);
    } else if (value instanceof Boolean) {
      output.write((Boolean) value ? TRUE : FALSE);
    } else if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      output.write(I64);
      writeLong(output, ((Number) value).longValue());
    } else if (value instanceof String) {
      output.write(STRING);
      writeBytes(output, ((String) value).getBytes(StandardCharsets.UTF_8));
    } else if (value instanceof byte[]) {
      output.write(BYTES);
      writeBytes(output, (byte[]) value);
    } else if (value instanceof Keyword) {
      Keyword keyword = (Keyword) value;
      output.write(KEYWORD);
      writeText(output, qualified(keyword.getNamespace(), keyword.getName()));
    } else if (value instanceof Symbol) {
      Symbol symbol = (Symbol) value;
      output.write(SYMBOL);
      writeText(output, qualified(symbol.getNamespace(), symbol.getName()));
    } else if (value instanceof IMapType<?, ?>) {
      writeMap(output, ((IMapType<?, ?>) value).iterator());
    } else if (value instanceof Map<?, ?>) {
      writeMap(output, ((Map<?, ?>) value).entrySet().iterator());
    } else if (value instanceof ISetType<?>) {
      writeSet(output, ((ISetType<?>) value).iterator());
    } else if (value instanceof java.util.Set<?>) {
      writeSet(output, ((java.util.Set<?>) value).iterator());
    } else if (value instanceof ILinearType<?>) {
      output.write(VECTOR);
      writeCollection(output, (ILinearType<?>) value);
    } else if (value instanceof List<?>) {
      output.write(VECTOR);
      writeCollection(output, (List<?>) value);
    } else if (value instanceof Collection<?>) {
      output.write(LIST);
      writeCollection(output, (Collection<?>) value);
    } else {
      throw new HaraException("hta/value-unsupported: " + value.getClass().getName());
    }
  }

  private static void writeSet(ByteArrayOutputStream output, Iterator<?> iterator) {
    ArrayList<byte[]> encoded = new ArrayList<>();
    iterator.forEachRemaining(value -> encoded.add(encodeBare(value)));
    encoded.sort(HtaValueCodec::compareUnsigned);
    output.write(SET);
    writeInt(output, encoded.size());
    encoded.forEach(output::writeBytes);
  }

  private static void writeMap(ByteArrayOutputStream output, Iterator<?> iterator) {
    ArrayList<Map.Entry<byte[], byte[]>> encoded = new ArrayList<>();
    iterator.forEachRemaining(
        item -> {
          Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
          encoded.add(Map.entry(encodeBare(entry.getKey()), encodeBare(entry.getValue())));
        });
    encoded.sort((left, right) -> compareUnsigned(left.getKey(), right.getKey()));
    output.write(MAP);
    writeInt(output, encoded.size());
    encoded.forEach(
        entry -> {
          output.writeBytes(entry.getKey());
          output.writeBytes(entry.getValue());
        });
  }

  private static byte[] encodeBare(Object value) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    write(output, HaraBox.unwrap(value));
    return output.toByteArray();
  }

  private static void writeCollection(ByteArrayOutputStream output, Iterable<?> values) {
    ArrayList<Object> copy = new ArrayList<>();
    values.forEach(copy::add);
    writeInt(output, copy.size());
    copy.forEach(value -> write(output, HaraBox.unwrap(value)));
  }

  private static int compareUnsigned(byte[] left, byte[] right) {
    return java.util.Arrays.compareUnsigned(left, right);
  }

  private static String qualified(String namespace, String name) {
    return namespace == null ? name : namespace + "/" + name;
  }

  private static void writeText(ByteArrayOutputStream output, String value) {
    writeBytes(output, value.getBytes(StandardCharsets.UTF_8));
  }

  private static void writeBytes(ByteArrayOutputStream output, byte[] value) {
    writeInt(output, value.length);
    output.writeBytes(value);
  }

  private static void writeInt(ByteArrayOutputStream output, int value) {
    output.write((value >>> 24) & 0xff);
    output.write((value >>> 16) & 0xff);
    output.write((value >>> 8) & 0xff);
    output.write(value & 0xff);
  }

  private static void writeLong(ByteArrayOutputStream output, long value) {
    for (int shift = 56; shift >= 0; shift -= 8) output.write((int) (value >>> shift) & 0xff);
  }

  private static HaraException malformed(String message) {
    return new HaraException("hta/value-malformed: " + message);
  }

  private static final class Reader {
    private final ByteBuffer input;

    private Reader(byte[] bytes, int offset) {
      input =
          ByteBuffer.wrap(bytes, offset, bytes.length - offset).slice().order(ByteOrder.BIG_ENDIAN);
    }

    private int remaining() {
      return input.remaining();
    }

    private Object read() {
      require(1);
      int tag = Byte.toUnsignedInt(input.get());
      switch (tag) {
        case NIL:
          return HaraNull.SINGLETON;
        case FALSE:
          return false;
        case TRUE:
          return true;
        case I64:
          require(8);
          return input.getLong();
        case STRING:
          return text();
        case BYTES:
          return bytes();
        case KEYWORD:
          return Keyword.create(text());
        case SYMBOL:
          return Symbol.create(text());
        case LIST:
        case VECTOR:
          return sequence();
        case SET:
          return set();
        case MAP:
          return map();
        default:
          throw malformed("unknown value tag");
      }
    }

    private ArrayList<Object> sequence() {
      int size = size();
      ArrayList<Object> result = new ArrayList<>(size);
      for (int i = 0; i < size; i++) result.add(read());
      return result;
    }

    private LinkedHashSet<Object> set() {
      int size = size();
      LinkedHashSet<Object> result = new LinkedHashSet<>();
      for (int i = 0; i < size; i++) result.add(read());
      return result;
    }

    private LinkedHashMap<Object, Object> map() {
      int size = size();
      LinkedHashMap<Object, Object> result = new LinkedHashMap<>();
      for (int i = 0; i < size; i++) result.put(read(), read());
      return result;
    }

    private String text() {
      return new String(bytes(), StandardCharsets.UTF_8);
    }

    private byte[] bytes() {
      int size = size();
      require(size);
      byte[] result = new byte[size];
      input.get(result);
      return result;
    }

    private int size() {
      require(4);
      int size = input.getInt();
      if (size < 0) throw malformed("negative length");
      return size;
    }

    private void require(int amount) {
      if (amount < 0 || input.remaining() < amount) throw malformed("truncated value");
    }
  }
}

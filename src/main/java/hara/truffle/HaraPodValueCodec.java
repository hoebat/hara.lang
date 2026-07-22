package hara.truffle;

import com.google.protobuf.ByteString;
import hara.pod.v1.Handle;
import hara.pod.v1.ListValue;
import hara.pod.v1.MapEntry;
import hara.pod.v1.MapValue;
import hara.pod.v1.NullValue;
import hara.pod.v1.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the transport-neutral protobuf values to ordinary Hara values. */
public final class HaraPodValueCodec {
  private HaraPodValueCodec() {}

  public static Value encode(Object value) {
    Value.Builder result = Value.newBuilder();
    if (value == null || value == HaraNull.SINGLETON) {
      return result.setNullValue(NullValue.NULL_VALUE_UNSPECIFIED).build();
    }
    if (value instanceof Boolean) {
      return result.setBooleanValue((Boolean) value).build();
    }
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      return result.setIntegerValue(((Number) value).longValue()).build();
    }
    if (value instanceof Float || value instanceof Double) {
      return result.setFloatingValue(((Number) value).doubleValue()).build();
    }
    if (value instanceof String) {
      return result.setStringValue((String) value).build();
    }
    if (value instanceof byte[]) {
      return result.setBytesValue(ByteString.copyFrom((byte[]) value)).build();
    }
    if (value instanceof HaraPodHandle) {
      return result.setHandle(((HaraPodHandle) value).handle()).build();
    }
    if (value instanceof List) {
      ListValue.Builder list = ListValue.newBuilder();
      for (Object element : (List<?>) value) {
        list.addValues(encode(element));
      }
      return result.setList(list).build();
    }
    if (value instanceof Map) {
      MapValue.Builder map = MapValue.newBuilder();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        map.addEntries(
            MapEntry.newBuilder()
                .setKey(encode(entry.getKey()))
                .setValue(encode(entry.getValue())));
      }
      return result.setMap(map).build();
    }
    throw new IllegalArgumentException("Unsupported pod value: " + value.getClass().getName());
  }

  public static Object decode(Value value) {
    switch (value.getKindCase()) {
      case NULL_VALUE:
        return HaraNull.SINGLETON;
      case BOOLEAN_VALUE:
        return value.getBooleanValue();
      case INTEGER_VALUE:
        return value.getIntegerValue();
      case FLOATING_VALUE:
        return value.getFloatingValue();
      case STRING_VALUE:
        return value.getStringValue();
      case BYTES_VALUE:
        return value.getBytesValue().toByteArray();
      case HANDLE:
        return new HaraPodHandle(value.getHandle());
      case LIST:
        List<Object> list = new ArrayList<>();
        for (Value element : value.getList().getValuesList()) {
          list.add(decode(element));
        }
        return list;
      case MAP:
        Map<Object, Object> map = new LinkedHashMap<>();
        for (MapEntry entry : value.getMap().getEntriesList()) {
          map.put(decode(entry.getKey()), decode(entry.getValue()));
        }
        return map;
      case TENSOR:
        return value.getTensor();
      case KIND_NOT_SET:
      default:
        return HaraNull.SINGLETON;
    }
  }
}

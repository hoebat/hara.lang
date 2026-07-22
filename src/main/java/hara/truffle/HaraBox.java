package hara.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.ISetType;
import hara.lang.protocol.ICount;
import hara.lang.protocol.IFind;
import hara.lang.protocol.IFn;
import hara.lang.protocol.ILookup;
import hara.lang.protocol.IDisplay;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Truffle boundary value for Java-backed Hara values. */
@ExportLibrary(InteropLibrary.class)
public final class HaraBox implements TruffleObject {
  private final Object value;
  private final String display;

  public HaraBox(Object value) {
    this.value = value;
    this.display = display(value);
  }

  public static Object unwrap(Object value) {
    if (value instanceof HaraBox) {
      return ((HaraBox) value).value;
    }
    return value;
  }

  public static Object export(Object value) {
    if (value == null) {
      return HaraNull.SINGLETON;
    }
    if (value instanceof BigInteger) {
      return new HaraBigInteger((BigInteger) value);
    }
    if (value instanceof BigDecimal) {
      return new HaraDecimal((BigDecimal) value);
    }
    if (value instanceof Long
        || value instanceof Integer
        || value instanceof Double
        || value instanceof Float
        || value instanceof Boolean
        || value instanceof String
        || value instanceof TruffleObject) {
      return value;
    }
    return new HaraBox(value);
  }

  @ExportMessage
  Object toDisplayString(boolean allowSideEffects) {
    return display;
  }

  @ExportMessage
  boolean hasArrayElements() {
    return arraySize(value) >= 0;
  }

  @ExportMessage
  long getArraySize() throws UnsupportedMessageException {
    long size = arraySize(value);
    if (size < 0) {
      throw UnsupportedMessageException.create();
    }
    return size;
  }

  @ExportMessage
  boolean isArrayElementReadable(long index) {
    return index >= 0 && index < arraySize(value);
  }

  @ExportMessage
  Object readArrayElement(long index) throws InvalidArrayIndexException {
    if (!isArrayElementReadable(index)) {
      throw InvalidArrayIndexException.create(index);
    }
    return export(arrayElement(value, index));
  }

  @ExportMessage
  boolean isExecutable() {
    return value instanceof IFn;
  }

  @ExportMessage
  Object execute(Object[] arguments)
      throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
    if (!(value instanceof IFn)) {
      throw UnsupportedMessageException.create();
    }
    return export(HaraJavaAdapters.invokeFunction(value, arguments));
  }

  @ExportMessage
  boolean hasIterator() {
    return value instanceof Iterator || value instanceof Iterable;
  }

  @ExportMessage
  Object getIterator() throws UnsupportedMessageException {
    if (value instanceof Iterator) {
      return new HaraIterator((Iterator<?>) value, Function.identity());
    }
    if (value instanceof Iterable) {
      return new HaraIterator(((Iterable<?>) value).iterator(), Function.identity());
    }
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  boolean hasHashEntries() {
    return value instanceof Map || value instanceof ILookup || value instanceof ISetType;
  }

  @ExportMessage
  long getHashSize() throws UnsupportedMessageException {
    if (value instanceof Map) {
      return ((Map<?, ?>) value).size();
    }
    if (value instanceof ICount) {
      return ((ICount) value).count();
    }
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  boolean isHashEntryReadable(Object key) {
    if (value instanceof Map) {
      return ((Map<?, ?>) value).containsKey(key);
    }
    return hasHashEntry(value, key);
  }

  @ExportMessage
  Object readHashValue(Object key) throws UnknownKeyException, UnsupportedMessageException {
    if (!isHashEntryReadable(key)) {
      if (!hasHashEntries()) {
        throw UnsupportedMessageException.create();
      }
      throw UnknownKeyException.create(key);
    }
    if (value instanceof Map) {
      return export(((Map<?, ?>) value).get(key));
    }
    if (value instanceof ISetType) {
      return export(key);
    }
    return export(lookupValue((ILookup<?, ?>) value, key));
  }

  @ExportMessage
  Object getHashEntriesIterator() throws UnsupportedMessageException {
    if (value instanceof Map) {
      Iterator<?> entries = ((Map<?, ?>) value).entrySet().iterator();
      return new HaraIterator(entries, entry -> {
        Map.Entry<?, ?> pair = (Map.Entry<?, ?>) entry;
        return new HaraHashEntry(pair.getKey(), pair.getValue());
      });
    }
    if (value instanceof ILookup) {
      ILookup<?, ?> lookup = (ILookup<?, ?>) value;
      return new HaraIterator(
          lookup.keys(), key -> new HaraHashEntry(key, lookupValue(lookup, key)));
    }
    if (value instanceof ISetType) {
      return new HaraIterator(((ISetType<?>) value).iterator(), key -> new HaraHashEntry(key, key));
    }
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  Object getHashKeysIterator() throws UnsupportedMessageException {
    if (value instanceof Map) {
      return new HaraIterator(((Map<?, ?>) value).keySet().iterator(), Function.identity());
    }
    if (value instanceof ILookup) {
      return new HaraIterator(((ILookup<?, ?>) value).keys(), Function.identity());
    }
    if (value instanceof ISetType) {
      return new HaraIterator(((ISetType<?>) value).iterator(), Function.identity());
    }
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  Object getHashValuesIterator() throws UnsupportedMessageException {
    if (value instanceof Map) {
      return new HaraIterator(((Map<?, ?>) value).values().iterator(), Function.identity());
    }
    if (value instanceof ILookup) {
      return new HaraIterator(((ILookup<?, ?>) value).vals(), Function.identity());
    }
    if (value instanceof ISetType) {
      return new HaraIterator(((ISetType<?>) value).iterator(), Function.identity());
    }
    throw UnsupportedMessageException.create();
  }

  @Override
  public String toString() {
    return display;
  }

  @TruffleBoundary
  private static String display(Object value) {
    return value instanceof IDisplay ? ((IDisplay) value).display() : String.valueOf(value);
  }

  private static long arraySize(Object value) {
    if (value instanceof ILinearType) {
      return ((ILinearType<?>) value).count();
    }
    if (value instanceof List) {
      return ((List<?>) value).size();
    }
    return value != null && value.getClass().isArray() ? Array.getLength(value) : -1;
  }

  private static Object arrayElement(Object value, long index) {
    if (value instanceof ILinearType) {
      return ((ILinearType<?>) value).nth(index);
    }
    if (value instanceof List) {
      return ((List<?>) value).get((int) index);
    }
    return Array.get(value, (int) index);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Object lookupValue(ILookup<?, ?> lookup, Object key) {
    return ((ILookup) lookup).lookup(key);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static boolean hasHashEntry(Object value, Object key) {
    if (value instanceof ISetType) {
      return ((ISetType) value).find(key) != null;
    }
    if (value instanceof IFind) {
      return ((IFind) value).find(key) != null;
    }
    return false;
  }

  @ExportLibrary(InteropLibrary.class)
  static final class HaraIterator implements TruffleObject {
    private final Iterator<?> iterator;
    private final Function<Object, Object> mapper;

    HaraIterator(Iterator<?> iterator, Function<Object, Object> mapper) {
      this.iterator = iterator;
      this.mapper = mapper;
    }

    @ExportMessage
    boolean isIterator() {
      return true;
    }

    @ExportMessage
    boolean hasIteratorNextElement() {
      return iterator.hasNext();
    }

    @ExportMessage
    Object getIteratorNextElement() throws StopIterationException {
      if (!iterator.hasNext()) {
        throw StopIterationException.create();
      }
      return export(mapper.apply(iterator.next()));
    }
  }

  @ExportLibrary(InteropLibrary.class)
  static final class HaraHashEntry implements TruffleObject {
    private final Object key;
    private final Object value;

    HaraHashEntry(Object key, Object value) {
      this.key = key;
      this.value = value;
    }

    @ExportMessage
    boolean hasArrayElements() {
      return true;
    }

    @ExportMessage
    long getArraySize() {
      return 2;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
      return index == 0 || index == 1;
    }

    @ExportMessage
    Object readArrayElement(long index) throws InvalidArrayIndexException {
      if (index == 0) {
        return export(key);
      }
      if (index == 1) {
        return export(value);
      }
      throw InvalidArrayIndexException.create(index);
    }
  }
}

package hara.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.Arrays;

@ExportLibrary(InteropLibrary.class)
public final class HaraRecord implements TruffleObject {
  private final HaraType type;
  private final Object[] values;

  public HaraRecord(HaraType type, Object[] values) {
    this.type = type;
    this.values = values.clone();
  }

  public Object read(String field) throws UnknownIdentifierException {
    int index = type.fieldIndex(field);
    if (index < 0) {
      throw UnknownIdentifierException.create(field);
    }
    return values[index];
  }

  public HaraType type() {
    return type;
  }

  @ExportMessage
  boolean hasMembers() {
    return true;
  }

  @ExportMessage
  Object getMembers(boolean includeInternal) {
    return new HaraMemberNames(type.fields());
  }

  @ExportMessage
  boolean isMemberReadable(String member) {
    return type.fieldIndex(member) >= 0;
  }

  @ExportMessage
  Object readMember(String member) throws UnknownIdentifierException {
    return HaraBox.export(read(member));
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof HaraRecord
        && type == ((HaraRecord) other).type
        && Arrays.deepEquals(values, ((HaraRecord) other).values);
  }

  @Override
  public int hashCode() {
    return 31 * System.identityHashCode(type) + Arrays.deepHashCode(values);
  }

  @Override
  @TruffleBoundary
  public String toString() {
    StringBuilder result = new StringBuilder("#<").append(type.name());
    for (int i = 0; i < values.length; i++) {
      result.append(i == 0 ? " " : ", ").append(type.fields()[i]).append("=").append(values[i]);
    }
    return result.append(">").toString();
  }

  @ExportLibrary(InteropLibrary.class)
  static final class HaraMemberNames implements TruffleObject {
    private final String[] names;

    HaraMemberNames(String[] names) {
      this.names = names;
    }

    @ExportMessage
    boolean hasArrayElements() {
      return true;
    }

    @ExportMessage
    long getArraySize() {
      return names.length;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
      return index >= 0 && index < names.length;
    }

    @ExportMessage
    Object readArrayElement(long index) throws InvalidArrayIndexException {
      if (!isArrayElementReadable(index)) {
        throw InvalidArrayIndexException.create(index);
      }
      return names[(int) index];
    }
  }
}

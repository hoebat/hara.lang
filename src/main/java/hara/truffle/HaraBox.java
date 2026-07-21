package hara.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import hara.lang.protocol.IDisplay;

@ExportLibrary(InteropLibrary.class)
public final class HaraBox implements TruffleObject {
  private final String display;

  public HaraBox(Object value) {
    this.display = display(value);
  }

  public static Object export(Object value) {
    if (value == null) {
      return HaraNull.SINGLETON;
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

  @Override
  public String toString() {
    return display;
  }

  @TruffleBoundary
  private static String display(Object value) {
    return value instanceof IDisplay ? ((IDisplay) value).display() : String.valueOf(value);
  }
}

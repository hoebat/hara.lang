package hara.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public final class HaraVar implements TruffleObject {
  private final String namespace;
  private final String name;
  private volatile Object value;

  HaraVar(String namespace, String name, Object value) {
    this.namespace = namespace;
    this.name = name;
    this.value = value;
  }

  public Object get() {
    return value;
  }

  void set(Object value) {
    this.value = value;
  }

  @ExportMessage
  Object toDisplayString(boolean allowSideEffects) {
    return displayName();
  }

  @Override
  public String toString() {
    return displayName();
  }

  @TruffleBoundary
  private String displayName() {
    return "#'" + namespace + "/" + name;
  }
}

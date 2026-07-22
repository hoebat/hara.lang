package hara.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import hara.lang.protocol.IDeref;

@ExportLibrary(InteropLibrary.class)
public final class HaraVar implements TruffleObject, IDeref<Object> {
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

  @Override
  public Object deref() {
    return get();
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

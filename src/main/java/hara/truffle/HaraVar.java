package hara.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import hara.lang.protocol.IDeref;
import hara.lang.protocol.IMetadata;
import hara.lang.protocol.IObjType;
import hara.lang.protocol.IReset;
import hara.lang.data.Keyword;
import hara.lang.protocol.Constant;
import hara.lang.protocol.ILookup;
import hara.lang.data.Map;
import hara.lang.data.types.IVarType;

@ExportLibrary(InteropLibrary.class)
public final class HaraVar
    implements TruffleObject, IDeref<Object>, IReset<Object>, IVarType, IObjType {
  private final String namespace;
  private final String name;
  private final IMetadata metadata;
  private volatile Object value;

  HaraVar(String namespace, String name, Object value) {
    this(namespace, name, value, Map.Standard.EMPTY);
  }

  HaraVar(String namespace, String name, Object value, IMetadata metadata) {
    this.namespace = namespace;
    this.name = name;
    this.metadata = metadata == null ? Map.Standard.EMPTY : metadata;
    this.value = value;
  }

  public Object get() {
    return value;
  }

  @Override
  public Object deref() {
    return get();
  }

  @Override
  public Object reset(Object value) {
    set(value);
    return value;
  }

  @Override
  public IMetadata meta() {
    return metadata;
  }

  @Override
  public HaraVar withMeta(IMetadata metadata) {
    return new HaraVar(namespace, name, value, metadata);
  }

  @Override
  public Boolean isDynamic() {
    if (!(metadata instanceof ILookup<?, ?>)) return false;
    Object value = ((ILookup<Object, Object>) metadata).lookup(Keyword.create("dynamic"));
    return Boolean.TRUE.equals(value);
  }

  @Override
  public Boolean isMacro() {
    if (!(metadata instanceof ILookup<?, ?>)) return false;
    Object value = ((ILookup<Object, Object>) metadata).lookup(Keyword.create("macro"));
    return Boolean.TRUE.equals(value);
  }

  @Override
  public Boolean isControl() {
    if (!(metadata instanceof ILookup<?, ?>)) return false;
    Object value = ((ILookup<Object, Object>) metadata).lookup(Keyword.create("control"));
    return Boolean.TRUE.equals(value);
  }

  @Override
  public long hashCalc(Constant.HashType type) {
    return System.identityHashCode(this);
  }

  @Override
  public String display() {
    return displayName();
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

package hara.lang.context;

import hara.lang.protocol.Constant;
import hara.lang.protocol.IApplicable;
import hara.lang.protocol.IDeref;
import hara.lang.protocol.IMetadata;
import hara.lang.protocol.IObjType;
import hara.lang.protocol.IPointer;
import java.util.Collections;
import java.util.Map;

/** A context-qualified value that can be invoked or transformed by its runtime. */
public final class Pointer implements IPointer, IApplicable, IDeref<Object>, IObjType {
  private final Object context;
  private final Map<?, ?> values;
  private final ContextRegistry registry;

  public Pointer(Object context, Map<?, ?> values, ContextRegistry registry) {
    if (context == null) throw new IllegalArgumentException("Context required");
    this.context = context;
    this.values = values == null ? Collections.emptyMap() : values;
    this.registry = registry == null ? new ContextRegistry() : registry;
  }

  public Object context() {
    return context;
  }

  public Map<?, ?> values() {
    return values;
  }

  public ContextRegistry registry() {
    return registry;
  }

  @Override
  public Object ptrContext() {
    return context;
  }

  @Override
  public Iterable<?> ptrKeys() {
    return values.keySet();
  }

  @Override
  public Object ptrVal(Object key) {
    return values.get(key);
  }

  @Override
  public Object deref() {
    return registry.get(context).derefPtr(this);
  }

  @Override
  public Object applyIn(Object runtime, Object[] args) {
    return registry.get(runtime == null ? context : runtime).invokePtr(this, args);
  }

  @Override
  public Object transformIn(Object runtime, Object[] args) {
    return registry.get(runtime == null ? context : runtime).transformInPtr(this, args);
  }

  @Override
  public Object transformOut(Object runtime, Object[] args, Object value) {
    return registry.get(runtime == null ? context : runtime).transformOutPtr(this, value);
  }

  @Override
  public IMetadata meta() {
    return null;
  }

  @Override
  public IObjType withMeta(IMetadata meta) {
    return this;
  }

  @Override
  public long hashCalc(Constant.HashType type) {
    return System.identityHashCode(this);
  }

  @Override
  public String display() {
    return "!" + context + values;
  }

  @Override
  public Constant.ObjType getObjType() {
    return Constant.ObjType.CLASS;
  }

  @Override
  public String getObjName() {
    return "POINTER";
  }
}

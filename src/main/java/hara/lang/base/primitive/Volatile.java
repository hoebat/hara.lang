package hara.lang.base.primitive;

import hara.lang.base.*;
import hara.lang.base.primitive.Bits;
import hara.lang.data.types.ObjMutable;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.IMapType;
import hara.lang.data.types.ISequentialType;
import hara.lang.data.types.ISetType;
import hara.lang.data.Tuple;
import hara.lang.protocol.*;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class Volatile<V> implements IDeref<V>, IReset<V>, IDisplay {

  public volatile V _val;

  public Volatile(V val) {
    _val = val;
  }

  @Override
  public V deref() {
    return _val;
  }

  @Override
  public String display() {
    return "#vol <" + _val + ">";
  }

  @Override
  public V reset(V newval) {
    return _val = newval;
  }
}

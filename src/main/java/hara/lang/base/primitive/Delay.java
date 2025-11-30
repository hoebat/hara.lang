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

public class Delay<V> implements IDeref<V>, IRealize<V>, IDisplay {
  volatile Throwable _ex;
  volatile Supplier<V> _fn;
  volatile V _val;

  public Delay(Supplier<V> fn) {
    _fn = fn;
    _val = null;
    _ex = null;
  }

  @Override
  public V deref() {
    if (_fn != null) {
      synchronized (this) {
        // double check
        if (_fn != null) {
          try {
            _val = _fn.get();
          } catch (Throwable t) {
            _ex = t;
          }
          _fn = null;
        }
      }
    }
    if (_ex != null) throw Ex.Sneaky(_ex);
    return _val;
  }

  @Override
  public String display() {
    return isRealized() ? "#delay <" + G.display(_val) + ">" : "#delay.pending<>";
  }

  @Override
  public synchronized boolean isRealized() {
    return _fn == null;
  }

  @Override
  public V realize() {
    return deref();
  }
}

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

public class Flag implements IDeref<Boolean>, IReset<Boolean>, IDisplay {
  private boolean _val;

  public Flag(boolean val) {
    _val = val;
  }

  @Override
  public Boolean deref() {
    return _val;
  }

  @Override
  public String display() {
    return "#flag <" + _val + ">";
  }

  @Override
  public Boolean reset(Boolean val) {
    return _val = val;
  }
}

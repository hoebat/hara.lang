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

public class Counter implements IDeref<Integer>, IReset<Integer>, IDisplay {

  private int _c;

  public Counter(int count) {
    _c = count;
  }

  public int dec() {
    return _c -= 1;
  }

  public int dec(int n) {
    return _c -= n;
  }

  @Override
  public Integer deref() {
    return _c;
  }

  @Override
  public String display() {
    return "#counter <" + _c + ">";
  }

  public int inc() {
    return _c += 1;
  }

  public int inc(int n) {
    return _c += n;
  }

  @Override
  public Integer reset(Integer count) {
    return _c = count;
  }
}

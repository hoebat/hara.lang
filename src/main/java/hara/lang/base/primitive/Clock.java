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

public class Clock {

  private static final Clock _clock;

  static {
    _clock = new Clock();
  }

  public static final long currentTimeMicros() {
    return currentTimeNanos() / 1000;
  }

  public static final long currentTimeMillis() {
    return currentTimeNanos() / 1000000;
  }

  public static final long currentTimeNanos() {
    return _clock._tsys + (System.nanoTime() - _clock._toff);
  }

  private final long _toff;

  private final long _tsys;

  private Clock() {
    _tsys = System.currentTimeMillis() * 1000000;

    // typically 36 ns, between these two lines.
    _toff = System.nanoTime();
  }
}

package hara.lang.protocol;

import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.*;

import hara.lang.base.*;
import hara.lang.base.Ex;
import hara.lang.data.*;
import hara.data.types.*;
import hara.lang.base.Arr;
import hara.lang.base.It;
import hara.lang.base.Str;
import hara.lang.base.G;

public interface IWatch<R, V> {
  default void addWatch(Object key, Consumer<WatchEntry<R, V>> f) {
    throw new UnsupportedOperationException("Not Supported");
  }

  default Iterator<Map.Entry<Object, Consumer<WatchEntry<R, V>>>> getWatches() {
    return null;
  }

  default void notifyWatches(V oldVal, V newVal) {
    Iterator<Map.Entry<Object, Consumer<WatchEntry<R, V>>>> ws = getWatches();
    if (ws != null) {
      ws.forEachRemaining(
          e -> e.getValue().accept(new WatchEntry<R, V>(e.getKey(), this, oldVal, newVal)));
    }
  }

  default void removeWatch(Object key) {
    throw new UnsupportedOperationException("Not Supported");
  }

  @SuppressWarnings("unchecked")
  public class WatchEntry<R, V> extends Tuple.Tup5.L<Object, R, Object, V, V> {

    WatchEntry(Object key, IWatch<R, V> ref, V oldVal, V newVal) {
      super(null, key, (R) ref, null, oldVal, newVal);
    }

    public V oldVal() {
      return this.D();
    }

    public V newVal() {
      return this.E();
    }
  }
}

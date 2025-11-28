package hara.lang.protocol;

import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.*;

import hara.lang.base.*;
import hara.lang.base.Ex;
import hara.lang.base.Std;
import hara.lang.base.Data;
import hara.lang.base.Arr;
import hara.lang.base.It;
import hara.lang.base.Str;
import hara.lang.base.G;

public interface ILookup<K, V> extends IFind<K, Map.Entry<K, V>> {

  Iterator<K> keys();

  default V lookup(K key) {
    return lookup(key, null);
  }

  default V lookup(K key, V notFound) {
    Map.Entry<K, V> ret = find(key);
    return (ret == null) ? notFound : ret.getValue();
  }

  Iterator<V> vals();
}

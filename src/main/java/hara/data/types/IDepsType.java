package hara.data.types;

import hara.data.types.*;

import java.util.Iterator;
import hara.lang.protocol.*;

public interface IDepsType<K, E> {
  E depGet(IContext ctx, K id);

  ISetType<E> depEntries(IContext ctx, K id);

  Iterator<K> depIds(IContext ctx);
}

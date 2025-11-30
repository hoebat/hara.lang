package hara.lang.data.types;

import hara.lang.base.Iter;
import hara.lang.protocol.*;

public abstract class ObjPersistent implements IObjType, IPersistent, IHashCached {
  public final IMetadata _meta;
  private long _hash;

  public ObjPersistent() {
    _meta = null;
  }

  public ObjPersistent(IMetadata meta) {
    _meta = meta;
  }

  public final IMetadata meta() {
    return _meta;
  }

  public long hashCurrent() {
    return _hash;
  }

  public void hashPut(long hash) {
    _hash = hash;
  }

  public String display() {
    if (this instanceof IColl) {
      return Iter.display(((IColl) this).iterator());
    }
    return super.toString();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public String toString() {
    if (this instanceof IColl) {
      return getClass().getName()
          + "<"
          + Iter.display(((IColl) this).iterator(), "", "", ",")
          + ">";
    } else {
      return getClass().getName() + "<" + display() + ">";
    }
  }
}

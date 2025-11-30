import os

files = {
    'src/main/java/hara/lang/protocol/Constant.java': """package hara.lang.protocol;

public interface Constant {

  public static final Object[] EMPTY_ARRAY = new Object[] {};
  public static final Boolean F = Boolean.FALSE;
  public static final Boolean T = Boolean.TRUE;

  public enum MetaType {
    OBJECT,
    MAP,
    STRING
  }

  public enum HashType {
    SYSTEM,
    MURMUR3,
    SIP
  };

  public enum ObjType {
    CLASS,
    TYPE,
    NIL,
    BOOLEAN,
    NUMBER,
    CHARACTER,
    STRING,
    SYMBOL,
    KEYWORD,
    PATTERN,
    DATE,
    UUID,
    URI,
    SEQUENTIAL,
    LIST,
    VECTOR,
    TUPLE,
    MAP,
    SET,
    FUNCTION,
    ATOM,
    META,
    OBJECT,
    ITERATOR,
    FUTURE,
    PROMISE,
    DELAY,
    PENDING,
    ERROR,
    READER,
    POINTER
  }
}
""",
    'src/main/java/hara/lang/protocol/IHash.java': """package hara.lang.protocol;

import hara.lang.base.G;

public interface IHash {

  default long hashCalc() {
    return hashCalc(hashType());
  }

  long hashCalc(Constant.HashType t);

  default long hashGet() {
    return hashCalc(hashType());
  }

  default long hashGet(Constant.HashType t) {
    return hashCalc(t);
  }

  default String hashSeed() {
    return "";
  }

  default Constant.HashType hashType() {
    return G.DEFAULT_HASH;
  }
}
""",
    'src/main/java/hara/lang/protocol/IObjType.java': """package hara.lang.protocol;

public interface IObjType extends IHash, IDisplay {

  default Constant.ObjType getObjType() {
    return Constant.ObjType.CLASS;
  }

  default String getObjName() {
    return getObjType().toString();
  }

  @Override
  default String hashSeed() {
    return "::" + getObjName() + "";
  }

  IMetadata meta();

  IObjType withMeta(IMetadata meta);
}
""",
    'src/main/java/hara/lang/data/types/ObjPersistent.java': """package hara.lang.data.types;

import hara.lang.protocol.*;
import hara.lang.base.Iter;
import java.util.Iterator;

public abstract class ObjPersistent implements IObjType, IPersistent, IHashCached {
  public final IMetadata _meta;
  private long _hash;

  public ObjPersistent() {
    _meta = null;
  }

  public ObjPersistent(IMetadata meta) {
    _meta = meta;
  }

  @Override
  public final IMetadata meta() {
    return _meta;
  }

  @Override
  public long hashCurrent() {
    return _hash;
  }

  @Override
  public void hashPut(long hash) {
    _hash = hash;
  }

  @Override
  public String display() {
    if (this instanceof IColl) {
      return Iter.display(((IColl) this).iterator());
    }
    return super.toString();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
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
""",
    'src/main/java/hara/lang/data/types/ObjMutable.java': """package hara.lang.data.types;

import hara.lang.protocol.*;
import hara.lang.base.Iter;
import java.util.Iterator;

public abstract class ObjMutable implements IObjType, IMutable, IHash {
  public IMetadata _meta;

  public ObjMutable() {
    _meta = null;
  }

  public ObjMutable(IMetadata meta) {
    _meta = meta;
  }

  @Override
  public IMetadata meta() {
    return _meta;
  }

  @Override
  public ObjMutable withMeta(IMetadata meta) {
    _meta = meta;
    return this;
  }

  @Override
  public String display() {
    if (this instanceof IColl) {
      return Iter.display(((IColl) this).iterator());
    }
    return super.toString();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
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
"""
}

def write_files():
    for filepath, content in files.items():
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Updated {filepath}")

if __name__ == '__main__':
    write_files()

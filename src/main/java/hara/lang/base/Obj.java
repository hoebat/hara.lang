package hara.lang.base;

import hara.data.types.*;

import hara.lang.data.*;

import java.util.Iterator;

import hara.lang.data.Keyword;
import hara.lang.protocol.Constant;
import hara.lang.protocol.*;

public interface Obj {

  abstract class MT implements IObjType, IMutable, IHash {
    protected IMetadata _meta;

    public MT() {
      _meta = null;
    }

    public MT(IMetadata meta) {
      _meta = meta;
    }

    @Override
    public IMetadata meta() {
      return _meta;
    }

    @Override
    public MT withMeta(IMetadata meta) {
      _meta = meta;
      return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public String toString() {
      if (this instanceof IColl) {
        return getClass().getName()
            + "<"
            + It.display(((IColl) this).iterator(), "", "", ",")
            + ">";
      } else {
        return getClass().getName() + "<" + display() + ">";
      }
    }
  }

  abstract class PT implements IObjType, IPersistent, IHashCached {
    protected final IMetadata _meta;
    private long _hash;

    public PT() {
      _meta = null;
    }

    public PT(IMetadata meta) {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public String toString() {
      if (this instanceof IColl) {
        return getClass().getName()
            + "<"
            + It.display(((IColl) this).iterator(), "", "", ",")
            + ">";
      } else {
        return getClass().getName() + "<" + display() + ">";
      }
    }
  }
  /*
  abstract class SEQ<V> extends PT
  	implements
  	IColl<V>,
  	IObjType,
	ISequentialType<V>{

  	public SEQ() {
  		super(null);
  	}

  	public SEQ(IMetadata meta) {
  		super(meta);
  	}

  	@Override
  	public ObjType getObjType() {
  		return Constant.ObjType.SEQUENTIAL;
  	}

  	@Override
  	public SEQ<V> cons(V e) {
		return new Cons<V>(_meta, e, this);
  	}

  	@Override
  	public SEQ<V> conj(V e) {
		return new Cons<V>(_meta, e, this);
  	}

  	@Override
  	public String hashSeed() {
  		return null;
  	}

  	@SuppressWarnings("rawtypes")
  	@Override
  	public IColl empty() {
		return Cons.EMPTY.withMeta(_meta);
  	}
  }*/

  abstract class FN extends PT {

    public FN() {
      super(null);
    }

    public FN(IMetadata meta) {
      super(meta);
    }

    @Override
    public Constant.ObjType getObjType() {
      return Constant.ObjType.FUNCTION;
    }

    @Override
    public FN withMeta(IMetadata meta) {
      throw new Ex.Unsupported();
    }

    @Override
    public long hashCalc(Constant.HashType t) {
      return G.hashFn(t).apply(hashSeed()) * 31 + ((IHash) _meta).hashCalc(t);
    }

    @Override
    public String display() {
      var name = Keyword.create("name").invoke(_meta);
      return "#<" + name + ">";
    }
  }

  abstract class EMPTY<E> extends PT implements IColl<E>, INth<E> {

    public EMPTY() {
      super(null);
    }

    public EMPTY(IMetadata meta) {
      super(meta);
    }

    @Override
    public long count() {
      return 0;
    }

    @Override
    public EMPTY<E> empty() {
      return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Iterator iterator() {
      return It.emptyIterator();
    }

    @Override
    public E nth(long i) {
      return null;
    }
  }
}

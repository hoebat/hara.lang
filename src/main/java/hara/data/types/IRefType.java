package hara.data.types;

import hara.lang.base.Obj;
import hara.lang.protocol.IHashCached;
import hara.lang.protocol.IMetadata;
import hara.lang.protocol.IObjType;

public interface IRefType {

  abstract class MT extends Obj.MT implements IHashCached, IObjType {
    private long _hash;

    public MT(IMetadata meta) {
      super(meta);
    }

    @Override
    public long hashCurrent() {
      return _hash;
    }

    @Override
    public void hashPut(long hash) {
      _hash = hash;
    }
  }

  abstract class PT extends Obj.PT implements IHashCached, IObjType {
    private long _hash;

    public PT(IMetadata meta) {
      super(meta);
    }

    @Override
    public long hashCurrent() {
      return _hash;
    }

    @Override
    public void hashPut(long hash) {
      _hash = hash;
    }
  }
}

package hara.lang.data.types;

import hara.lang.data.types.ObjFn;

import hara.lang.base.Ex;
import hara.lang.base.G;
import hara.lang.data.Keyword;
import hara.lang.protocol.*;

public abstract class ObjFn extends ObjPersistent {

  public ObjFn() {
    super(null);
  }

  public ObjFn(IMetadata meta) {
    super(meta);
  }

  @Override
  public Constant.ObjType getObjType() {
    return Constant.ObjType.FUNCTION;
  }

  @Override
  public ObjFn withMeta(IMetadata meta) {
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

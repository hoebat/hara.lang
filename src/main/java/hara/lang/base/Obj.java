package hara.lang.base;

import hara.lang.data.types.ObjPersistent;

import hara.lang.data.Keyword;
import hara.lang.protocol.*;

import java.util.Iterator;

public interface Obj {

  /*
     abstract class SEQ<V> extends ObjPersistent
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

}

package hara.data.types;

import hara.lang.data.*;

import hara.data.types.*;

import java.util.Iterator;
import hara.lang.protocol.*;
import hara.lang.base.Eq;

public interface ISequentialType<E>
    extends Iterable<E>, ICount, IEquality, IHash, IObjType, IOrderedType<E> {

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  default boolean equality(Object obj) {
    if (obj instanceof ISequentialType) {
      return (count() == ((ISequentialType) obj).count())
          && Eq.eqIterator((Iterator) this.iterator(), ((ISequentialType) obj).iterator());
    } else if (obj instanceof java.util.List) {
      return (this.count() == ((java.util.List) obj).size())
          && Eq.eqIterator((Iterator) this.iterator(), ((java.util.List) obj).iterator());
    } else {
      return false;
    }
  }

  @Override
  default Constant.ObjType getObjType() {
    return Constant.ObjType.SEQUENTIAL;
  }
}

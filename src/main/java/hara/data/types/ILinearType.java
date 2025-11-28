package hara.data.types;

import hara.lang.data.*;

import hara.data.types.*;

import hara.lang.protocol.*;
import hara.lang.base.Ex;

public interface ILinearType<E>
    extends IColl<E>,
        IPushFirst<E>,
        IPushLast<E>,
        IPopFirst,
        IPopLast,
        IPeekFirst<E>,
        IPeekLast<E>,
        ICons<E>,
        IConj<E>,
        INth<E>,
        ICount {

  @Override
  default ILinearType<E> cons(E e) {
    return (ILinearType<E>) pushFirst(e);
  }

  @Override
  default ILinearType<E> conj(E e) {
    return (ILinearType<E>) pushLast(e);
  }

  @Override
  default IPushFirst<E> pushFirst(E e) {
    throw new Ex.Unsupported();
  }

  @Override
  default IPopFirst popFirst() {
    throw new Ex.Unsupported();
  }

  @Override
  default String startString() {
    return "[";
  }

  @Override
  default String endString() {
    return "]";
  }
}

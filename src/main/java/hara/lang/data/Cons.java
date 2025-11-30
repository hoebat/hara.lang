package hara.lang.data;

import hara.lang.data.types.ObjPersistent;

import hara.lang.data.types.ILinkedType;
import hara.lang.data.types.ISequentialType;
import hara.lang.base.Ex;
import hara.lang.base.Obj;
import hara.lang.protocol.IMetadata;

import java.util.Iterator;

public class Cons<E> extends ObjPersistent implements ISequentialType<E>, ILinkedType<E> {

  private final E _first;
  private final ILinkedType<E> _more;

  public Cons(IMetadata meta, E first, ILinkedType<E> more) {
    super(meta);
    _first = first;
    _more = more;
  }

  @Override
  public final Cons<E> withMeta(IMetadata meta) {
    return (meta() == meta) ? this : new Cons<E>(meta, _first, _more);
  }

  //
  // I.Seq
  //

  @Override
  public Cons<E> cons(E e) {
    return new Cons<E>(null, e, this);
  }

  @Override
  public final E peekFirst() {
    return _first;
  }

  @Override
  public final ILinkedType<E> popFirst() {
    return _more;
  }

  @Override
  public long count() {
    return 1 + ((_more == null) ? 0 : _more.count());
  }

  @Override
  public Iterator<E> iterator() {
    // Logic for iterator needs to be implemented or delegated.
    // In Std.java it threw TODO. But we can probably implement a simple iterator.
    // However, to keep identical behavior I will keep TODO.
    throw new Ex.TODO();
  }

  @Override
  public Cons<E> pushFirst(E e) {
    return new Cons<E>(_meta, e, this);
  }

  @Override
  public Tuple.Tup0 empty() {
    return Tuple.Tup0.EMPTY.withMeta(_meta);
  }
}

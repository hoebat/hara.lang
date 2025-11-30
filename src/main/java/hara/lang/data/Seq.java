package hara.lang.data;

import hara.lang.data.types.ObjPersistent;

import hara.lang.data.types.ILinkedType;
import hara.lang.data.types.ISequentialType;
import hara.lang.protocol.IMetadata;

import java.util.Iterator;

public class Seq<E> extends ObjPersistent implements ISequentialType<E>, ILinkedType<E> {

  final Iterator<E> _iter;
  final State<E> _state;

  static class State<V> {
    volatile V _val;
    volatile V _rest;
  }

  @SuppressWarnings("unchecked")
  public Seq(Iterator<E> iter) {
    _iter = iter;
    _state = new State<E>();
    _state._val = (E) _state;
    _state._rest = (E) _state;
  }

  public Seq(IMetadata meta, Iterator<E> iter, State<E> state) {
    super(meta);
    _iter = iter;
    _state = state;
  }

  @Override
  public E peekFirst() {
    if (_state._val == _state)
      synchronized (_state) {
        if (_state._val == _state) _state._val = _iter.next();
      }
    return _state._val;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Seq<E> popFirst() {
    if (_state._rest == _state) {
      synchronized (_state) {
        if (_state._rest == _state) {
          peekFirst();
          _state._rest = _iter.hasNext() ? (E) (new Seq<E>(_iter)) : null;
        }
      }
    }
    return (Seq<E>) _state._rest;
  }

  @Override
  public Iterator<E> iterator() {
    return _iter;
  }

  @Override
  public long count() {
    return 1 + popFirst().count();
  }

  @Override
  public Seq<E> withMeta(IMetadata meta) {
    return new Seq<E>(meta, _iter, _state);
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

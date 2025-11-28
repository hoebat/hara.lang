package hara.lang.base.iter;

import hara.lang.base.Ex;
import java.util.Iterator;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class ConcatIterator<E> implements java.util.Iterator<E> {

  private static final class Iterators<E> {

    private final java.util.Iterator<E> head;
    private Iterators<E> tail;

    @SuppressWarnings("unchecked")
    Iterators(java.util.Iterator<? extends E> head) {
      this.head = (java.util.Iterator<E>) head;
    }
  }

  private Iterators<E> curr;
  private Iterators<E> last;
  private boolean nextCalculated = false;

  public ConcatIterator(java.util.Iterator<? extends java.util.Iterator<? extends E>> iterators) {
    this.curr = this.last = iterators.hasNext() ? new Iterators<>(iterators.next()) : null;
    while (iterators.hasNext()) {
      this.last = this.last.tail = new Iterators<>(iterators.next());
    }
  }

  @Override
  public boolean hasNext() {
    if (nextCalculated) {
      return curr != null;
    } else {
      nextCalculated = true;
      while (true) {
        if (curr.head.hasNext()) {
          return true;
        } else {
          curr = curr.tail;
          if (curr == null) {
            last = null; // release reference
            return false;
          }
        }
      }
    }
  }

  @Override
  public E next() {
    if (!hasNext()) {
      throw new Ex.NoSuchElement();
    }
    nextCalculated = false;
    return curr.head.next();
  }

  public Iterator<E> concat(java.util.Iterator<? extends E> that) {
    if (curr == null) {
      nextCalculated = false;
      curr = last = new Iterators<>(that);
    } else {
      last = last.tail = new Iterators<>(that);
    }
    return this;
  }
}

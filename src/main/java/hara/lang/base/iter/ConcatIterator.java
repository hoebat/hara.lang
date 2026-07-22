package hara.lang.base.iter;

import hara.lang.base.Ex;

import java.util.ArrayDeque;
import java.util.Iterator;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class ConcatIterator<E> implements java.util.Iterator<E> {

  private final Iterator<? extends Iterator<? extends E>> sources;
  private final ArrayDeque<Iterator<? extends E>> appended = new ArrayDeque<>();
  private Iterator<? extends E> current;

  public ConcatIterator(java.util.Iterator<? extends java.util.Iterator<? extends E>> iterators) {
    this.sources = iterators;
  }

  @Override
  public boolean hasNext() {
    while (current == null || !current.hasNext()) {
      if (sources.hasNext()) {
        current = sources.next();
      } else if (!appended.isEmpty()) {
        current = appended.removeFirst();
      } else {
        current = null;
        return false;
      }
    }
    return true;
  }

  @Override
  public E next() {
    if (!hasNext()) {
      throw new Ex.NoSuchElement();
    }
    return current.next();
  }

  public Iterator<E> concat(java.util.Iterator<? extends E> that) {
    appended.addLast(that);
    return this;
  }
}

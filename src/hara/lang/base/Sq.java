package hara.lang.base;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface Sq {

	public class ToIterator<E> implements Iterator<E> {
	
		I.Seq<E> _seq;
		boolean _start;
	
		public ToIterator(I.Seq<E> seq) {
			_seq = seq;
			_start = true;
		}
	
		@Override
		public boolean hasNext() {
			return !_seq.restEnd();
		}
	
		@Override
		public E next() throws NoSuchElementException {
			if (_start) {
				E out = _seq.first();
				_start = false;
				return out;
			}
			I.Seq<E> more = _seq.restMore();
			if (more == null)
				throw new NoSuchElementException();
	
			_seq = more;
			return _seq.first();
		}
	
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	@SuppressWarnings("hiding")
	public class ToSpliterator<T> implements Spliterator<T> {
		private I.Seq<T> _seq;
	
		public ToSpliterator(I.Seq<T> seq) {
			_seq = seq;
		}
	
		public static <T> Stream<T> stream(I.Seq<T> seq) {
			return StreamSupport.stream(new ToSpliterator<T>(seq), false);
		}
	
		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			if (_seq == null)
				return false;
	
			action.accept((T) _seq.first());
			_seq = _seq.next();
			return true;
		}
	
		@Override
		public Spliterator<T> trySplit() {
			return null;
		}
	
		@Override
		public long estimateSize() {
			return Long.MAX_VALUE;
		}
	
		@Override
		public int characteristics() {
			return IMMUTABLE;
		}
	}

	public static <V> int seqIndexOf(I.Seq<V> seq, V o) {
		for (int i = 0; seq != null; seq = seq.next(), i++) {
			if (seq.first().hashCode() == o.hashCode())
				return i;
		}
		return -1;
	}

	@SuppressWarnings("unchecked")
	public static <V> V[] toArray(I.Seq<V> seq) {
		int len = (int) seq.count();
		V[] ret = (V[]) new Object[len];
		for (int i = 0; seq != null; ++i, seq = seq.restMore())
			ret[i] = seq.first();
		return ret;
	}

	@SuppressWarnings("unchecked")
	public static <V> java.util.Iterator<V> toIterator(I.Seq<V> seq) {
		return (Iterator<V>) (seq.restEnd() ? Iter.emptyIterator() : new Sq.ToIterator<V>(seq));
	}

}

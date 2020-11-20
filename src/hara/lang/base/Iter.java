package hara.lang.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface Iter {

	public enum State {
		READY, NOT_SET, DONE
	}

	public interface UnmodifiableIterator<E> extends Iterator<E> {

		@Override
		default void remove() {
			throw new UnsupportedOperationException();
		}
	}

	public interface ProxyIterator extends Iterator<T> {

		Iterator<T> delegate();

		@Override
		default boolean hasNext() {
			return delegate().hasNext();
		}

		@Override
		default T next() {
			return delegate().next();
		}

		@Override
		default void remove() {
			delegate().remove();
		}
	}

	public class ToSeq<E> extends Obj.SEQ<E> {

		final Iterator<E> _iter;
		final State<E> _state;

		static class State<V> {
			volatile V _val;
			volatile V _rest;
		}

		public static <E> ToSeq<E> create(Iterator<E> iter) {
			if (iter.hasNext())
				return new ToSeq<E>(iter);
			return null;
		}

		@SuppressWarnings("unchecked")
		public ToSeq(Iterator<E> iter) {
			_iter = iter;
			_state = new State<E>();
			_state._val = (E) _state;
			_state._rest = (E) _state;
		}

		public ToSeq(I.Metadata meta, Iterator<E> iter, State<E> state) {
			super(meta);
			_iter = iter;
			_state = state;
		}

		public E first() {
			if (_state._val == _state)
				synchronized (_state) {
					if (_state._val == _state)
						_state._val = _iter.next();
				}
			return _state._val;
		}

		public ToSeq<E> withMeta(I.Metadata meta) {
			return new ToSeq<E>(meta, _iter, _state);
		}

		@SuppressWarnings("unchecked")
		@Override
		public I.Seq<E> restMore() {
			if (_state._rest == _state) {
				synchronized (_state) {
					if (_state._rest == _state) {
						first();
						_state._rest = _iter.hasNext() ? (E) (new ToSeq<E>(_iter)) : null;
					}
				}
			}
			return (I.Seq<E>) _state._rest;
		}

		@Override
		public boolean restEnd() {
			return !_iter.hasNext();
		}
	}

	public interface Nil {

		public class Iterator<V> implements java.util.Iterator<V> {

			public static Iterator<Object> INSTANCE = new Iterator<Object>();

			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public V next() {
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		}

		public class ListIterator<V> implements java.util.ListIterator<V> {

			public static ListIterator<Object> INSTANCE = new ListIterator<Object>();

			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public V next() {
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean hasPrevious() {
				return false;
			}

			@Override
			public V previous() {
				return null;
			}

			@Override
			public int nextIndex() {
				return -1;
			}

			@Override
			public int previousIndex() {
				return -1;
			}

			@Override
			public void set(V e) {
				throw new UnsupportedOperationException();

			}

			@Override
			public void add(V e) {
				throw new UnsupportedOperationException();
			}
		}

		public class Spliterator<V> implements java.util.Spliterator<V> {

			public static Spliterator<Object> INSTANCE = new Spliterator<Object>();

			public boolean hasNext() {
				return false;
			}

			public V next() {
				throw new NoSuchElementException();
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean tryAdvance(Consumer<? super V> action) {
				return false;
			}

			@Override
			public Spliterator<V> trySplit() {
				return null;
			}

			@Override
			public long estimateSize() {
				return 0;
			}

			@Override
			public int characteristics() {
				return 0;
			}
		}

	}

	public static Iterator<?> emptyIterator() {
		return Nil.Iterator.INSTANCE;
	}

	public static ListIterator<?> emptyListIterator() {
		return Nil.ListIterator.INSTANCE;
	}

	public static Spliterator<?> emptySpliterator() {
		return Nil.Spliterator.INSTANCE;
	}

	public static <V> Iter.ToSeq<V> toSeq(Iterator<V> iter) {
		return new ToSeq<V>(iter);
	}

	static Iterator<Object> from(Object... elements) {
		Objects.requireNonNull(elements, "elements is null");
		return new Iterator<Object>() {
			int i = 0;

			@Override
			public boolean hasNext() {
				return i < elements.length;
			}

			@Override
			public Object next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				return elements[i++];
			}
		};
	}

	public static <V> Iterator<V> from(BooleanSupplier hasNext, Supplier<V> next) {
		return new Iterator<V>() {
			@Override
			public boolean hasNext() {
				return hasNext.getAsBoolean();
			}

			@Override
			public V next() {
				return next.get();
			}
		};
	}

	public static <V> Iterator<V> fromLookup(I.SequentialLookupType<V> vec) {

		return new Iterator<V>() {
			long count = vec.count();
			long i = 0;

			public boolean hasNext() {
				return i < count;
			}

			public V next() {
				if (i < count)
					return vec.nth(i++);
				else
					throw new NoSuchElementException();
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	//
	// Map
	//

	public static <E, R> Iterator<R> map(Iterator<E> it, Function<E, R> f) {
		return from(it::hasNext, () -> f.apply(it.next()));
	}

	//
	// Reduce
	//

	public static <U, R> R reduce(Iterator<U> it, R init, BiFunction<R, U, R> f) {
		var acc = init;
		while (it.hasNext()) {
			acc = f.apply(acc, it.next());
		}
		return acc;
	}

	public static <V> boolean equals(Iterator<V> a, Iterator<V> b, BiPredicate<V, V> equals) {
		while (a.hasNext()) {
			if (!equals.test(a.next(), b.next())) {
				return false;
			}
		}
		return true;
	}

	public static Iterator<Long> range(long min, long max) {
		return new Iterator<Long>() {

			long i = min;

			@Override
			public boolean hasNext() {
				return i < max;
			}

			@Override
			public Long next() {
				if (hasNext()) {
					return i++;
				} else {
					throw new NoSuchElementException();
				}
			}
		};
	}

	public static <V> boolean contains(Iterator<V> iterator, Predicate<V> f) {
		while (iterator.hasNext()) {
			if (f.test(iterator.next())) {
				return true;
			}
		}
		return false;
	}

	public static String toString(Iterator<?> iterator) {
		StringBuilder sb = new StringBuilder().append('[');
		boolean first = true;
		while (iterator.hasNext()) {
			if (!first) {
				sb.append(", ");
			}
			first = false;
			sb.append(iterator.next());
		}
		return sb.append(']').toString();
	}

	public static <V> Iterator<V> filter(Iterator<V> it, Predicate<V> f) {
		return new Iterator<V>() {

			private V next = null;
			private State state = State.NOT_SET;

			private void prime() {
				if (state == State.NOT_SET) {
					while (it.hasNext()) {
						next = it.next();
						if (f.test(next)) {
							state = State.READY;
							return;
						}
					}
					state = State.DONE;
				}
			}

			@Override
			public boolean hasNext() {
				prime();
				return state != State.DONE;
			}

			@Override
			public V next() {
				prime();
				if (next == State.DONE) {
					throw new NoSuchElementException();
				}

				V val = (V) next;
				state = State.NOT_SET;
				return val;
			}
		};
	}

	public static <E> ArrayList<E> toArrayList(Iterator<? extends E> it) {
		ArrayList<E> list = new ArrayList<E>();
		while (it.hasNext()) { list.add(it.next());}
		return list;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <E> E[] toArray(Iterator it) {
		return (E[]) toArrayList(it).toArray();
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <E> E[] toArray(Iterator it, Class<E> cls) {
		ArrayList c = toArrayList(it);
		
		E[] arr = Arr.newArray(cls, c.size());
		Arr.fillArray(c, arr);
		return arr;
	}

	public static Iterator<Boolean> booleans(boolean... arr) {
		return new Arr.ToIter_boolean(arr, 0);
	}

	public static Iterator<Byte> bytes(byte... arr) {
		return new Arr.ToIter_byte(arr, 0);
	}
	
	public static Iterator<Character> chars(char... arr) {
		return new Arr.ToIter_char(arr, 0);
	}
	
	public static Iterator<Short> shorts(short... arr) {
		return new Arr.ToIter_short(arr, 0);
	}
	
	public static Iterator<Integer> ints(int... arr) {
		return new Arr.ToIter_int(arr, 0);
	}
	
	public static Iterator<Long> longs(long... arr) {
		return new Arr.ToIter_long(arr, 0);
	}
	
	public static Iterator<Float> floats(float... arr) {
		return new Arr.ToIter_float(arr, 0);
	}
	
	public static Iterator<Double> doubles(double... arr) {
		return new Arr.ToIter_double(arr, 0);
	}
}

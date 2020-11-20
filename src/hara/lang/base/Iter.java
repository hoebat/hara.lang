package hara.lang.base;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface Iter {

	public interface T {

		public interface UnmodifiableIteratorType<V> 
			extends java.util.Iterator<V> {

			@Override
			default void remove() {
				throw new Ex.Unsupported();
			}
		}


		public interface UnmodifiableListIteratorType<V> 
			extends java.util.ListIterator<V> {

			@Override
			default void remove() {
				throw new Ex.Unsupported();
			}


			@Override
			default void set(V e) {
				throw new Ex.Unsupported();

			}

			@Override
			default void add(V e) {
				throw new Ex.Unsupported();
			}
		}


		public interface EmptySpliterator<V> 
			extends java.util.Spliterator<V> {

			@Override
			default boolean tryAdvance(Consumer<? super V> action) {
				return false;
			}

			@Override
			default Spliterator<V> trySplit() {
				return null;
			}

			@Override
			default long estimateSize() {
				return 0;
			}

			@Override
			default int characteristics() {
				return 0;
			}
		}

		public interface EmptyIteratorType<V> extends UnmodifiableIteratorType<V> {

			@Override
			default boolean hasNext() {
				return false;
			}

			@Override
			default V next() {
				throw new Ex.NoSuchElement();
			}
		}
		
		public interface EmptyListIteratorType<V> 
			extends UnmodifiableListIteratorType<V> {

			@Override
			default boolean hasPrevious() {
				return false;
			}

			@Override
			default V previous() {
				return null;
			}

			@Override
			default int nextIndex() {
				return -1;
			}

			@Override
			default int previousIndex() {
				return -1;
			}
			
			@Override
			default boolean hasNext() {
				return false;
			}

			@Override
			default V next() {
				throw new NoSuchElementException();
			}
		}


		public class ToSeq<E> extends Obj.SEQ<E> {
		
			final Iterator<E> _iter;
			final State<E> _state;
		
			static class State<V> {
				volatile V _val;
				volatile V _rest;
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
		
			@Override
			public E first() {
				if (_state._val == _state)
					synchronized (_state) {
						if (_state._val == _state)
							_state._val = _iter.next();
					}
				return _state._val;
			}

			@Override
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


		public enum State {
			READY, NOT_SET, DONE
		}
	}

	public interface Nil {
		public static Iterator<Object> ITERATOR = new Iterator<Object>();
		public class Iterator<V> implements T.EmptyIteratorType<V> {}
		public static ListIterator<Object> LIST_ITERATOR = new ListIterator<Object>();
		public class ListIterator<V> implements T.EmptyListIteratorType<V> {}
		public static Spliterator<Object> SPLITERATOR = new Spliterator<Object>();
		public class Spliterator<V> implements T.EmptySpliterator<V> {}
	}

	public static Iterator<?> emptyIterator() {
		return Nil.ITERATOR;
	}

	public static ListIterator<?> emptyListIterator() {
		return Nil.LIST_ITERATOR;
	}

	public static Spliterator<?> emptySpliterator() {
		return Nil.SPLITERATOR;
	}

	public static <V> T.ToSeq<V> toSeq(Iterator<V> iter) {
		return new T.ToSeq<V>(iter);
	}

	public static Iterator<Boolean> booleans(boolean... arr) {
		return new Arr.T.ToIter_boolean(arr, 0);
	}

	public static Iterator<Byte> bytes(byte... arr) {
		return new Arr.T.ToIter_byte(arr, 0);
	}
	
	public static Iterator<Character> chars(char... arr) {
		return new Arr.T.ToIter_char(arr, 0);
	}
	
	public static Iterator<Short> shorts(short... arr) {
		return new Arr.T.ToIter_short(arr, 0);
	}
	
	public static Iterator<Integer> ints(int... arr) {
		return new Arr.T.ToIter_int(arr, 0);
	}
	
	public static Iterator<Long> longs(long... arr) {
		return new Arr.T.ToIter_long(arr, 0);
	}
	
	public static Iterator<Float> floats(float... arr) {
		return new Arr.T.ToIter_float(arr, 0);
	}
	
	public static Iterator<Double> doubles(double... arr) {
		return new Arr.T.ToIter_double(arr, 0);
	}

	public static Iterator<Object> objects(Object... arr) {
		return new Arr.T.ToIter(arr, 0);
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
		while (it.hasNext()) {acc = f.apply(acc, it.next());}
		return acc;
	}
	
	//
	// Equals
	//

	public static <V> boolean equals(Iterator<V> a, Iterator<V> b, BiPredicate<V, V> equals) {
		while (a.hasNext()) {
			if (!equals.test(a.next(), b.next())) { return false;}
		}
		return true;
	}

	//
	// Filter
	//

	public static <V> Iterator<V> filter(Iterator<V> it, Predicate<V> f) {
		return new Iterator<V>() {

			private V next = null;
			private T.State state = T.State.NOT_SET;

			private void prime() {
				if (state == T.State.NOT_SET) {
					while (it.hasNext()) {
						next = it.next();
						if (f.test(next)) {
							state = T.State.READY;
							return;
						}
					}
					state = T.State.DONE;
				}
			}

			@Override
			public boolean hasNext() {
				prime();
				return state != T.State.DONE;
			}

			@Override
			public V next() {
				prime();
				if (next == T.State.DONE) {
					throw new NoSuchElementException();
				}

				V val = (V) next;
				state = T.State.NOT_SET;
				return val;
			}
		};
	}

	//
	// Range
	//
	
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
	
	//
	// Range
	//
	
	public static <V> boolean contains(Iterator<V> iterator, Predicate<V> f) {
		while (iterator.hasNext()) {
			if (f.test(iterator.next())) { return true; }
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
	
	//
	// Arrays
	//

	public static <E> ArrayList<E> toArrayList(Iterator<? extends E> it) {
		ArrayList<E> list = new ArrayList<E>();
		while (it.hasNext()) { list.add(it.next());}
		return list;
	}
	
	public static Object[] toArray(Iterator<Object> it) {
		return toArrayList(it).toArray();
	}
	
	public static <E> E[] toArray(Iterator<E> it, Class<E> cls) {
		ArrayList<E> c = toArrayList(it);
		E[] arr = Arr.newArray(cls, c.size());
		Arr.fillArray(c, arr);
		return arr;
	}

}

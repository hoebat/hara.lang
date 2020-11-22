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


import java.util.concurrent.atomic.AtomicReference;

public interface It {

	public interface T {

		public interface UnmodifiableIteratorType<V> extends java.util.Iterator<V> {

			@Override
			default void remove() {
				throw new Ex.Unsupported();
			}
		}

		public interface UnmodifiableListIteratorType<V> extends java.util.ListIterator<V> {

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

		public interface EmptySpliterator<V> extends java.util.Spliterator<V> {

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

		public interface EmptyListIteratorType<V> extends UnmodifiableListIteratorType<V> {

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
				throw new Ex.NoSuchElement();
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

			ConcatIterator(java.util.Iterator<? extends java.util.Iterator<? extends E>> iterators) {
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

		final class SingletonIterator<E> implements java.util.Iterator<E> {

			private final E _elem;
			private boolean _next = true;

			SingletonIterator(E element) {
				this._elem = element;
			}

			@Override
			public boolean hasNext() {
				return _next;
			}

			@Override
			public E next() {
				if (!hasNext()) {
					throw new Ex.NoSuchElement();
				}
				_next = false;
				return _elem;
			}
		}

		public enum State {
			READY, NOT_SET, DONE
		}
	}

	public interface Nil {
		public static Iterator<Object> ITERATOR = new Iterator<Object>();

		public class Iterator<V> implements T.EmptyIteratorType<V> {
		}

		public static ListIterator<Object> LIST_ITERATOR = new ListIterator<Object>();

		public class ListIterator<V> implements T.EmptyListIteratorType<V> {
		}

		public static Spliterator<Object> SPLITERATOR = new Spliterator<Object>();

		public class Spliterator<V> implements T.EmptySpliterator<V> {
		}
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

	public static <E> T.ToSeq<E> toSeq(Iterator<E> iter) {
		return new T.ToSeq<E>(iter);
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

	@SafeVarargs
	public static <E> Iterator<E> objects(E... arr) {
		return new Arr.T.ToIter<E>(arr, 0);
	}
	
	public static <E> Iterator<E> from(E[] arr) {
		return new Arr.T.ToIter<>(arr, 0);
	}
	
	public static <E> Iterator<E> from(Iterable<E> coll) {
		return coll.iterator();
	}
	
	public static <E> Iterator<E> from(BooleanSupplier hasNext, Supplier<E> next) {
		return new Iterator<E>() {
			@Override
			public boolean hasNext() {
				return hasNext.getAsBoolean();
			}

			@Override
			public E next() {
				return next.get();
			}
		};
	}

	public static <E> Iterator<E> fromLookup(Data.SequentialLookupType<E> vec) {

		return new Iterator<E>() {
			long _cnt = vec.count();
			long _i = 0;

			@Override
			public boolean hasNext() {
				return _i < _cnt;
			}

			@Override
			public E next() {
				if (_i < _cnt)
					return vec.nth(_i++);
				else
					throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	//
	// Collect
	//
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <E, R> R collect(
			Function<Iterator, R> term, 
			E[] array, 
			Function<Iterator, Iterator>... pl) {
		return term.apply(stream(from(array), pl));
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <E, R> R collect(
			Function<Iterator, R> term, 
			Iterable<E> coll, 
			Function<Iterator, Iterator>... pl) {
		return term.apply(stream(from(coll), pl));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <R> R collect(
			Function<Iterator, R> term, 
			Iterator it, 
			Function<Iterator, Iterator>... pl) {
		return term.apply(stream(it, pl));
	}

	//
	// Stream
	//
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static  <E, R> Iterator<R> stream(
			Iterator<E> it, 
			Function<Iterator, Iterator>... fns) {
		return (Iterator<R>) Arr.reduce((itr, f) -> f.apply(itr), it, fns);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <E, R> Iterator<R> stream(
			E[] array,
			Function<Iterator, Iterator>... fns) {
		return (Iterator<R>) Arr.reduce((itr, f) -> f.apply(itr), from(array), fns);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <E, R> Iterator<R> stream(
			Iterable<E> coll,
			Function<Iterator, Iterator>... fns) {
		return (Iterator<R>) Arr.reduce((itr, f) -> f.apply(itr), coll.iterator(), fns);
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

	public static <E, R> R reduce(Iterator<E> it, R init, BiFunction<R, E, R> f) {
		var _acc = init;
		while (it.hasNext()) {
			_acc = f.apply(_acc, it.next());
		}
		return _acc;
	}

	public static <E, R> R reduce(Iterator<E> it, R init, BiFunction<R, E, R> f, Supplier<Boolean> end) {
		var _acc = init;
		while (it.hasNext()) {
			_acc = f.apply(_acc, it.next());
			if(end.get()) { return _acc; }
		}
		return _acc;
	}

	//
	// Equals
	//

	public static <E> boolean equals(Iterator<E> a, Iterator<E> b, BiPredicate<E, E> equals) {
		while (a.hasNext()) {
			if (!equals.test(a.next(), b.next())) {
				return false;
			}
		}
		return true;
	}

	//
	// Filter
	//

	public static <E> Iterator<E> filter(Iterator<E> it, Predicate<E> f) {
		return new Iterator<E>() {

			private E _val = null;
			private T.State _state = T.State.NOT_SET;

			private void prime() {
				if (_state == T.State.NOT_SET) {
					while (it.hasNext()) {
						_val = it.next();
						if (f.test(_val)) {
							_state = T.State.READY;
							return;
						}
					}
					_state = T.State.DONE;
				}
			}

			@Override
			public boolean hasNext() {
				prime();
				return _state != T.State.DONE;
			}

			@Override
			public E next() {
				prime();
				if (_state == T.State.DONE) {
					throw new NoSuchElementException();
				}
				_state = T.State.NOT_SET;
				return _val;
			}
		};
	}

	//
	// Keep
	//

	public static <E, R> Iterator<R> keep(Iterator<E> it, Function<E, R> f) {
		return new Iterator<R>() {

			private R _out = null;
			private T.State _state = T.State.NOT_SET;

			private void prime() {
				if (_state == T.State.NOT_SET) {
					while (it.hasNext()) {
						E _val = it.next();
						_out = f.apply(_val);
						if (_out != null) {
							_state = T.State.READY;
							return;
						}
					}
					_state = T.State.DONE;
				}
			}

			@Override
			public boolean hasNext() {
				prime();
				return _state != T.State.DONE;
			}

			@Override
			public R next() {
				prime();
				if (_state == T.State.DONE) {
					throw new NoSuchElementException();
				}

				_state = T.State.NOT_SET;
				return _out;
			}
		};
	}

	//
	// Range
	//

	public static Iterator<Long> range(long min, long max) {
		return new Iterator<Long>() {

			long _i = min;

			@Override
			public boolean hasNext() {
				return _i < max;
			}

			@Override
			public Long next() {
				if (hasNext()) {
					return _i++;
				} else {
					throw new NoSuchElementException();
				}
			}
		};
	}

	//
	// Range
	//

	public static <E> boolean contains(Iterator<E> it, Predicate<E> f) {
		while (it.hasNext()) {
			if (f.test(it.next())) {
				return true;
			}
		}
		return false;
	}

	public static <E> String toString(Iterator<E> it) {
		StringBuilder _sb = new StringBuilder().append('[');
		boolean _start = true;
		while (it.hasNext()) {
			if (!_start) {
				_sb.append(", ");
			}
			_start = false;
			_sb.append(it.next());
		}
		return _sb.append(']').toString();
	}

	public static void pr(Iterator<?> it) {
		System.out.println(toString(it));
	}

	//
	// Arrays
	//

	public static <E> ArrayList<E> toJList(Iterator<? extends E> it) {
		ArrayList<E> list = new ArrayList<E>();
		while (it.hasNext()) {
			list.add(it.next());
		}
		return list;
	}

	public static Object[] toArray(Iterator<?> it) {
		return toJList(it).toArray();
	}

	public static <E> E[] toArray(Iterator<E> it, Class<E> cls) {
		ArrayList<E> c = toJList(it);
		E[] arr = Arr.newArray(cls, c.size());
		Arr.fillArray(c.iterator(), arr);
		return arr;
	}

	//
	// Combinators
	//

	public static <E> Iterator<E> drop(Iterator<E> it, int n) {

		return new Iterator<E>() {

			long _n = n;

			@Override
			public boolean hasNext() {
				while (_n > 0 && it.hasNext()) {
					it.next(); // discarded
					_n--;
				}
				return it.hasNext();
			}

			@Override
			public E next() {
				if (!it.hasNext()) {
					throw new Ex.NoSuchElement();
				}
				return it.next();
			}
		};
	}

	public static <E> Iterator<E> take(Iterator<E> it, int n) {
		return new Iterator<E>() {
			long _n = n;

			@Override
			public boolean hasNext() {
				return _n > 0 && it.hasNext();
			}

			@Override
			public E next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				_n--;
				return it.next();
			}
		};
	}

	public static <E> I.Pair<Iterator<E>, Iterator<E>> duplicate(Iterator<E> it) {
		final java.util.Queue<E> _gap = new java.util.LinkedList<>();
		final AtomicReference<Iterator<E>> _ahead = new AtomicReference<>();
		class PairIterator implements Iterator<E> {

			@Override
			public boolean hasNext() {
				return (this != _ahead.get() && !_gap.isEmpty()) || it.hasNext();
			}

			@Override
			public E next() {
				if (_gap.isEmpty()) {
					_ahead.set(this);
				}
				if (this == _ahead.get()) {
					final E element = it.next();
					_gap.add(element);
					return element;
				} else {
					return _gap.poll();
				}
			}
		}
		return Std.pair(new PairIterator(), new PairIterator());
	}

	public static <E> Iterator<E> constantly(E t) {
		return new Iterator<E>() {
			@Override
			public boolean hasNext() {
				return true;
			}

			@Override
			public E next() {
				return t;
			}
		};
	}

	public static <E> Iterator<E> repeatedly(Supplier<E> f) {
		return new Iterator<E>() {
			@Override
			public boolean hasNext() {
				return true;
			}

			@Override
			public E next() {
				return f.get();
			}
		};
	}

	public static <E> boolean every(Iterator<E> it, Predicate<? super E> pred) {
		while (it.hasNext()) {
			if (!pred.test(it.next())) {
				return false;
			}
		}
		return true;
	}

	public static <E> boolean any(Iterator<E> it, Predicate<? super E> pred) {
		while (it.hasNext()) {
			if (pred.test(it.next())) {
				return true;
			}
		}
		return false;
	}

	public static <E> Iterator<E> iterate(E seed, Function<? super E, ? extends E> f) {
		return new Iterator<E>() {
			E _curr = seed;

			@Override
			public boolean hasNext() {
				return true;
			}

			@Override
			public E next() {
				E n = f.apply(_curr);
				E p = _curr;
				_curr = n;
				return p;
			}
		};
	}

	public static <E> Iterator<E> cycle(Supplier<Iterator<E>> f) {
		return new Iterator<E>() {
			Iterator<E> _it = f.get();

			@Override
			public boolean hasNext() {
				return true;
			}

			@Override
			public E next() {
				if (_it.hasNext()) {
					return _it.next();
				} else {
					_it = f.get();
				}
				if (!_it.hasNext()) {
					_it = f.get();
					if (!_it.hasNext()) {
						throw new Ex.NoSuchElement();
					}
				}
				return _it.next();
			}
		};
	}

	public default <A, B> Iterator<I.Pair<A, B>> zipPair(Iterator<A> it0, Iterator<B> it1) {
		return new Iterator<I.Pair<A, B>>() {
			@Override
			public boolean hasNext() {
				return it0.hasNext() && it1.hasNext();
			}

			@Override
			public I.Pair<A, B> next() {
				if (!hasNext()) {
					throw new Ex.NoSuchElement();
				}
				return Std.pair(it0.next(), it1.next());
			}
		};
	}
	
	public static <E> Iterator<I.Pair<E, E>> partitionPair(Iterator<E> it) {
		return new Iterator<I.Pair<E, E>>() {
			E _v0;
			T.State _state = T.State.NOT_SET;
			
			@Override
			public boolean hasNext() {
				if(_state == T.State.NOT_SET) {
					if (it.hasNext()) {
						_v0 = it.next();
						_state = T.State.READY;
					} else {
						return false;
					}
				} 
				return it.hasNext();
			}

			@Override
			public I.Pair<E, E> next() {
				if(it.hasNext()) {
					var pair = Std.pair(_v0, it.next());
					_state = T.State.NOT_SET;
					return pair;
				}
				throw new Ex.NoSuchElement();
			}
		};
	}

	public static <E, R> Iterator<R> mapcat(Iterator<E> it, Function<? super E, ? extends Iterable<? extends R>> f) {
		return new Iterator<R>() {

			final Iterator<? extends E> _it = it;
			Iterator<?> _curr = emptyIterator();

			@Override
			public boolean hasNext() {
				boolean _nxt;
				while (!(_nxt = _curr.hasNext()) && _it.hasNext()) {
					_curr = f.apply(_it.next()).iterator();
				}
				return _nxt;
			}

			@SuppressWarnings("unchecked")
			@Override
			public R next() {
				if (!hasNext()) {
					throw new Ex.NoSuchElement();
				}
				return (R) _curr.next();
			}
		};
	}

}

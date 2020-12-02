package hara.lang.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map.Entry;
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

	@SuppressWarnings("rawtypes")
	public static Iterator iter(Object obj) {
		if(obj == null) {
			return Nil.ITERATOR;
		} else if (obj instanceof Iterator) {
			return (Iterator)obj;
		} else if(obj instanceof Iterable) {
			return ((Iterable)obj).iterator();
		} else if(obj instanceof java.util.Map) {
			return ((java.util.Map)obj).entrySet().iterator();
		} else if(obj instanceof java.util.Map.Entry) {
			Entry e = (Entry)obj;
			return It.objects(e.getKey(), e.getValue());
		} else if (obj.getClass().isArray()) {
			return Arr.toIter(obj);
		} else {
			throw new Ex.Unsupported();
		}
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

	public static <E> boolean equals(Iterator<E> a, Iterator<E> b, BiPredicate<E, E> equals) {
		while (a.hasNext()) {
			if (!equals.test(a.next(), b.next())) {
				return false;
			}
		}
		return true;
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

	//
	// Reduce
	//


	public static <E> E reduce(Iterator<E> it, BiFunction<E, E, E> f) {
		var _acc = it.next();
		while (it.hasNext()) {
			_acc = f.apply(_acc, it.next());
		}
		return _acc;
	}
	
	public static <E, R> R reduce(Iterator<E> it, R init, BiFunction<R, E, R> f) {
		var _acc = init;
		while (it.hasNext()) {
			_acc = f.apply(_acc, it.next());
		}
		return _acc;
	}
	
	public static <E, R> R reduce(Iterator<E> it, R init, BiFunction<R, E, R> f, Function<R, Boolean> end) {
		var _acc = init;
		while (it.hasNext()) {
			if(end.apply(_acc)) { return _acc; }
			_acc = f.apply(_acc, it.next());
		}
		return _acc;
	}
	
	@SuppressWarnings("unchecked")
	public static <E, R> R reduceIn(Iterator<E> it, R init, BiFunction<R, E, R> f) {
		R _init;
		boolean _change = false;
		if(init instanceof I.ToMutable) {
			_init = (R)((I.ToMutable)init).toMutable();
			_change = true;
		} else {
			_init = init;
		}
		R out = reduce(it, _init, f);
		if(out instanceof I.ToPersistent && _change == true) {
			return (R)((I.ToPersistent)out).toPersistent();
		} else {
			return out;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <E, R> long count(Iterator it) {
		return (long) reduce(it, 0, (acc, i) -> acc + 1);
	}

	//
	// Map
	//


	public static <E, R> Function<Iterator<E>, Iterator<R>> map(Function<E, R> f) {
		return (it) -> map(it, f);
	}
	
	public static <E, R> Iterator<R> map(Iterator<E> it, Function<E, R> f) {
		return from(it::hasNext, () -> f.apply(it.next()));
	}
	
	public static <E> Consumer<Iterator<E>> each(Consumer<E> f) {
		return (it) -> it.forEachRemaining(f);
	}
	
	public static <E> void each(Iterator<E> it, Consumer<E> f) {
		it.forEachRemaining(f);
	}


	//
	// Equals
	//

	public static <E, R> Function<Iterator<E>, Iterator<R>> mapcat(Function<? super E, ? extends Iterator<? extends R>> f) {
		return (it) -> mapcat(it, f);
	}

	public static <E, R> Iterator<R> mapcat(Iterator<E> it, Function<? super E, ? extends Iterator<? extends R>> f) {
		return new Iterator<R>() {
	
			final Iterator<? extends E> _it = it;
			Iterator<?> _curr = emptyIterator();
	
			@Override
			public boolean hasNext() {
				boolean _nxt;
				while (!(_nxt = _curr.hasNext()) && _it.hasNext()) {
					_curr = f.apply(_it.next());
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

	//
	// Filter
	//


	public static <E, R> Function<Iterator<E>, Iterator<E>> filter(Predicate<E> f) {
		return (it) -> filter(it, f);
	}
	
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
	
	public static <E, R>  Function<Iterator<E>, Iterator<R>> keep(Function<E, R> f) {
		return (it) -> keep(it, f);
	}

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
	// Group by
	

	public static <K, E, R> HashMap<K, ArrayList<R>> 
		groupBy(Iterator<E> it, Function<E, K> f, Function<E, R> p, HashMap<K, ArrayList<R>> map) {
		return reduce(it, map, (m, e) -> {
			K key = f.apply(e);
			ArrayList<R> l = m.get(key);
			if(l == null) {
				l = new ArrayList<R>();
				m.put(key, l);
			}
			l.add(p.apply(e));
			return m;
		});
	}
	
	public static <K, E, R> HashMap<K, ArrayList<R>> 
		groupBy(Iterator<E> it, Function<E, K> f, Function<E, R> p) {
		return groupBy(it, f, p, new HashMap<K, ArrayList<R>>());
	}

	public static <K, E> Function<Iterator<E>, HashMap<K, ArrayList<E>>> groupBy(Function<E, K> f) {
		return (it) -> groupBy(it, f, x -> x);
	}

	public static <K, E, R> Function<Iterator<E>, HashMap<K, ArrayList<R>>> groupBy(Function<E, K> f, Function<E, R> p) {
		return (it) -> groupBy(it, f, p);
	}
	
	
	//

	//
	// Range
	//
	
	public static Iterator<Long> range(long max) {
		return range(0, max);
	}

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

	public static <E> Function<Iterator<E>, Boolean> contains(Predicate<E> f) {
		return it -> contains(it, f);
	}

	public static <E> boolean contains(Iterator<E> it, Predicate<E> f) {
		while (it.hasNext()) {
			if (f.test(it.next())) {
				return true;
			}
		}
		return false;
	}

	public static <E> String toString(
			Iterator<E> it, String start, 
			String end, String sep, Function<E, String> f) {
		StringBuilder _sb = new StringBuilder().append(start);
		boolean _start = true;
		while (it.hasNext()) {
			if (!_start) {
				_sb.append(sep);
			}
			_start = false;
			_sb.append(f.apply(it.next()));
		}
		return _sb.append(end).toString();
	}
	
	public static <E> String display(
			Iterator<E> it, String start, 
			String end, String sep) {
		return toString(it, start, end, sep, G::display);
	}
	
	public static <E> String display(Iterator<E> it) {
		return display(it, "(", ")", " ");
	}


	/*
	public static void prString(Iterator<?> it, ) {
		System.out.println(toString(it));
	}
	*/

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void pr(Iterator it) {
		System.out.println(display(it, "", "", " "));
	}
	
	//
	// Nth
	//

	public static <E> E nth(Iterator<E> it, long idx) {
		if(idx < 0) { throw new Ex.OutOfBounds(); }
		var i = idx;
		for(i = idx; i > 0; i--) {
			it.next();
		}
		return it.next();
	}
	
	//
	// Arrays
	//

	public static <E> ArrayList<E> toArrayList(Iterator<? extends E> it) {
		ArrayList<E> list = new ArrayList<E>();
		while (it.hasNext()) {
			list.add(it.next());
		}
		return list;
	}
	
	@SafeVarargs
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <E> ArrayList<E> toArrayList(Object input, Function<Iterator, Iterator>... pl) {
		Iterator it = iter(input);
		return (ArrayList<E>) collect(
				It::toArrayList,
				it,
				pl);
	}

	public static Object[] toArray(Iterator<?> it) {
		return toArrayList(it).toArray();
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object[] toArray(Object input, Function<Iterator, Iterator>... pl) {
		Iterator it = iter(input);
		return collect(
				It::toArray,
				it,
				pl);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <E> E[] toArray(Iterator<E> it, Class cls) {
		ArrayList<E> c = toArrayList(it);
		E[] arr = (E[]) Arr.newArray(cls, c.size());
		Arr.fillArray(c.iterator(), arr);
		return arr;
	}

	
	
	//
	// Concat
	//

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Iterator concat(Iterator x, Iterator y) {
		return new T.ConcatIterator(It.objects(x, y));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })	
	public static Iterator concat(Iterator<Iterator> all) {
		return new T.ConcatIterator(all);
	}
	
	
	//
	// Combinators
	//

	
	public static <E>  Function<Iterator<E>, Iterator<E>> drop(int n) {
		return (it) -> drop(it, n);
	}

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
	
	public static <E>  Function<Iterator<E>, Iterator<E>> take(int n) {
		return (it) -> take(it, n);
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

	@SuppressWarnings({ "unchecked", "rawtypes" })
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
		return new Std.T.Tup2.L(null, new PairIterator(), new PairIterator());
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
	
	public static <E> Function<Iterator<E>, Boolean> every(Predicate<? super E> pred) {
		return it -> every(it, pred);
	}

	public static <E> boolean every(Iterator<E> it, Predicate<? super E> pred) {
		while (it.hasNext()) {
			if (!pred.test(it.next())) {
				return false;
			}
		}
		return true;
	}
	

	public static <E> Function<Iterator<E>, Boolean> any(Predicate<? super E> pred) {
		return it -> any(it, pred);
	}

	public static <E> boolean any(Iterator<E> it, Predicate<? super E> pred) {
		while (it.hasNext()) {
			if (pred.test(it.next())) {
				return true;
			}
		}
		return false;
	}
	

	public static <E> Function<Iterator<E>, E> some(Predicate<? super E> pred) {
		return it -> some(it, pred);
	}

	public static <E> E some(Iterator<E> it, Predicate<? super E> pred) {
		while (it.hasNext()) {
			var e = it.next();
			if (pred.test(e)) {
				return e;
			}
		}
		return null;
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

	public static <A, B> Iterator<I.Pair<A, B>> zipPair(Iterator<A> it0, Iterator<B> it1) {
		return new Iterator<I.Pair<A, B>>() {
			@Override
			public boolean hasNext() {
				return it0.hasNext() && it1.hasNext();
			}

			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public I.Pair<A, B> next() {
				if (!hasNext()) {
					throw new Ex.NoSuchElement();
				}
				return new Std.T.Tup2.L(null, it0.next(), it1.next());
			}
		};
	}

	@SuppressWarnings({ "rawtypes"})
	public static Iterator<Object[]> zip(Iterator<Iterator<Object>> input) {
		Iterator[] its = It.toArray(input, Iterator.class);
		
		return new Iterator<Object[]>() {
			@Override
			public boolean hasNext() {
				return Arr.every((it) -> it.hasNext(), its);
			}

			@Override
			public Object[] next() {
				if (!hasNext()) {
					throw new Ex.NoSuchElement();
				}
				return Arr.map((it) -> it.next(), Object.class, its);
			}
		};
	}
	
	public static <E> Iterator<Entry<E, E>> partitionPair(Iterator<E> it) {
		return new Iterator<Entry<E, E>>() {
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

			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public I.Pair<E, E> next() {
				if(it.hasNext()) {
					var pair = new Std.T.Tup2.L(null, _v0, it.next());
					_state = T.State.NOT_SET;
					return pair;
				}
				throw new Ex.NoSuchElement();
			}
		};
	}
}

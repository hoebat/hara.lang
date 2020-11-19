package hara.lang.base;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import hara.lang.base.I.Cons;


public interface Iter {

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

	public static <U, V> Iterator<V> map(Iterator<U> it, Function<U, V> f) {
		return from(it::hasNext, () -> f.apply(it.next()));
	}

	@SuppressWarnings("rawtypes")
	public static <U> Iterator map(Iterator<U> it, CFn f) {
		return from(it::hasNext, () -> f.invoke(it.next()));
	}
	
	//
	// Filter
	//


	  static final Object NONE = new Object();
	  
	  /**
	   * @param it an iterator
	   * @param f  a predicate
	   * @return an iterator which only yields values that satisfy the predicate
	   */
	  public static <V> Iterator<V> filter(Iterator<V> it, Predicate<V> f) {
	    return new Iterator<V>() {

	      private Object next = NONE;
	      private boolean done = false;

	      private void prime() {
	        if (next == NONE && !done) {
	          while (it.hasNext()) {
	            next = it.next();
	            if (f.test((V) next)) {
	              return;
	            }
	          }
	          done = true;
	        }
	      }

	      @Override
	      public boolean hasNext() {
	        prime();
	        return !done;
	      }

	      @Override
	      public V next() {
	        prime();
	        if (next == NONE) {
	          throw new NoSuchElementException();
	        }

	        V val = (V) next;
	        next = NONE;
	        return val;
	      }
	    };
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

	@SuppressWarnings("rawtypes")
	public static Object reduce(Iterator it, Object init, CFn f) {
		var acc = init;
		while (it.hasNext()) {
			acc = f.invoke(acc, it.next());
		}
		return acc;
	}

	@SuppressWarnings("rawtypes")
	public static Object reduce(Iterator it, CFn f) {
		return reduce(it, f.invoke(), f);
	}

	public static <V> boolean equals(Iterator<V> a, Iterator<V> b, BiPredicate<V, V> equals) {
		while (a.hasNext()) {
			if (!equals.test(a.next(), b.next())) {
				return false;
			}
		}
		return true;
	}

	public static <V> Iterator<V> range(long min, long max, LongFunction<V> f) {
		return new Iterator<V>() {

			long i = min;

			@Override
			public boolean hasNext() {
				return i < max;
			}

			@Override
			public V next() {
				if (hasNext()) {
					return f.apply(i++);
				} else {
					throw new NoSuchElementException();
				}
			}
		};
	}

	public static <V> Iterator<V> range(long max, LongFunction<V> f) {
		return range(0, max, f);
	}

	public static Iterator<Long> range(long min, long max) {
		return range(min, max, (e) -> e);
	}

	public static Iterator<Long> range(long max) {
		return range(0, max, (e) -> e);
	}
}

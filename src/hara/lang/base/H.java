package hara.lang.base;

import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface H {

	public class Reduce<E, R> implements Function<Iterable<E>, R> {

		final Supplier<R> _init;

		final BiFunction<R, E, R> _step;

		public Reduce(Supplier<R> init, BiFunction<R, E, R> step) {
			_init = init;
			_step = step;
		}

		@Override
		public R apply(Iterable<E> c) {
			R acc = _init.get();
			Iterator<E> it = c.iterator();
			while (it.hasNext()) {
				acc = _step.apply(acc, it.next());
			}
			return acc;
		}
	}
	
	public interface Xf<E, R> extends Function<Iterator<E>, Iterator<R>> {}

	public interface XfStream<E, R> extends Function<Iterable<E>, Iterator<R>> {
		Function<Iterator<E>, Iterator<R>> getXf();

		default Iterator<R> apply(Iterable<E> c) {
			return getXf().apply(c.iterator());
		}
	}

	public class Map<E, R> implements XfStream<E, R> {

		final Function<E, R> _fn;

		public Xf<E, R> getXf() {
			return (it) -> Iter.from(it::hasNext, () -> _fn.apply(it.next()));
		}

		public Map(Function<E, R> fn) {
			_fn = fn;
		}
	}

	public class Filter<E> implements XfStream<E, E> {

		final Predicate<E> _pred;

		public Xf<E, E> getXf() {
			return (it) -> Iter.filter(it, _pred);
		}

		public Filter(Predicate<E> pred) {
			_pred = pred;
		}
	}

	@SuppressWarnings("rawtypes")
	public class Pipe<E, R> implements XfStream<E, R> {

		final XfStream[] _xfs;

		@SuppressWarnings("unchecked")
		public Xf<E, R> getXf() {
			return (Xf<E, R>) (it) -> {
				Iterator iter = it;
				for(int i = 0; i < _xfs.length; i++) {
					iter = (Iterator) _xfs[i].getXf().apply(iter);
				}
				return (Iterator<R>) iter;
			};
		}

		public Pipe(XfStream... xfs) {
			_xfs = xfs;
		}
	}
	
	/*
	

	public interface Add<R, X, Y> extends IFn<R, X, Y, Object> {

		R invoke(X x, Y y);

		public class AddLL implements Add<Long, Long, Long> {
			public Long invoke(Long x, Long y) {
				return x + y;
			}
		}
	}

	public interface Map<E, R> extends Function<Iterable<E>, Iterator<R>> {

		public Function<E, R> getFn();

		@Override
		default Iterator<R> apply(Iterable<E> c) {
			var it = c.iterator();
			return (Iterator<R>) Iter.from(it::hasNext, () -> getFn().apply(it.next()));
		}

		public class Fn<E, R> implements Map<E, R> {
			final Function<E, R> _fn;

			public Fn(Function<E, R> fn) {
				_fn = fn;
			}

			@Override
			public Function<E, R> getFn() {
				return _fn;
			}
		}
	}

	public interface Filter<E> extends Function<Iterable<E>, Iterator<E>> {

		public Predicate<E> getPred();

		@Override
		default Iterator<E> apply(Iterable<E> c) {
			var it = c.iterator();
			return Iter.filter(it, getPred());
		}

		public class Fn<E> implements Filter<E> {
			final Predicate<E> _fn;

			public Fn(Predicate<E> fn) {
				_fn = fn;
			}

			@Override
			public Predicate<E> getPred() {
				return _fn;
			}
		}
	}

	public interface Reduce<E, R> extends Function<Iterable<E>, R> {

		public Supplier<R> initFn();

		public BiFunction<R, E, R> stepFn();

		@Override
		default R apply(Iterable<E> c) {
			R acc = initFn().get();
			Iterator<E> it = c.iterator();
			while (it.hasNext()) {
				acc = stepFn().apply(acc, it.next());
			}
			return acc;
		}

		public class Fn<E, R> implements Reduce<E, R> {
			final Supplier<R> _init;
			final BiFunction<R, E, R> _step;

			public Fn(Supplier<R> init, BiFunction<R, E, R> step) {
				_init = init;
				_step = step;
			}

			@Override
			public Supplier<R> initFn() {
				return _init;
			}

			public BiFunction<R, E, R> stepFn() {
				return _step;
			}
		}
	}
	*/
}

package hara.lang.base;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import hara.lang.experimental.H.Reduce;

public interface H {
	
	public interface Add<R, X, Y> extends IFn<R, X, Y, Object> {
		
		R invoke(X x, Y y);
		
		public class AddLL implements Add<Long, Long, Long> {
			public Long invoke(Long x, Long y) {
				return x + y;
			}
		}
	}
	
	/*
	public abstract class C_1$1<E, R> {
		final Function<E, R> _fn;
		
		public C_1$1(Function<E, R> fn) {
			_fn = fn;
		}
		
		Function<E, R> getFn() {
			return _fn;
		}
	}
	*/
	
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
			public Supplier<R> initFn() { return _init; }
			public BiFunction<R, E, R> stepFn() { return _step; }
		}
	}
}

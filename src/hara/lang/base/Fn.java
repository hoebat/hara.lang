package hara.lang.base;

import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface Fn {

	public enum UnitType {
		MAP, FILTER, MAPCAT, COMPLEX
	}
	
	public interface Rf<E, R> {
		BiFunction<R, E, R> getStep();
	}

	public interface Xf<E, R> {
		Object getUnit();
		UnitType getUnitType();
		Function<Iterator<E>, Iterator<R>> getXf();
	}
	
	public interface OFn extends I.Fn<Object, Object, Object> {} 
	
	public class Fn0<R> implements I.Fn<R, Object, Object> {

		final Supplier<R> _f0;

		Fn0(Supplier<R> f0) {
			_f0 = f0;
		}

		@Override
		public Supplier<R> getArg0() {
			return _f0;
		}
	}

	public class Fn1<R, T1> implements I.Fn<R, T1, Object> {

		final Function<T1, R> _f1;

		Fn1(Function<T1, R> f1) {
			_f1 = f1;
		}

		@Override
		public Function<T1, R> getArg1() {
			return _f1;
		}
	}

	public class Fn2<R, T1, T2> implements I.Fn<R, T1, T2> {

		final BiFunction<T1, T2, R> _f2;

		Fn2(BiFunction<T1, T2, R> f2) {
			_f2 = f2;
		}

		@Override
		public BiFunction<T1, T2, R> getArg2() {
			return _f2;
		}
	}

	public class FnN<R, T1, T2> implements I.Fn<R, T1, T2> {

		final Supplier<R> _f0;
		final Function<T1, R> _f1;
		final BiFunction<T1, T2, R> _f2;
		final Function<Object[], R> _fN;

		FnN(Supplier<R> f0, Function<T1, R> f1, BiFunction<T1, T2, R> f2, Function<Object[], R> fN) {
			_f0 = f0;
			_f1 = f1;
			_f2 = f2;
			_fN = fN;
		}

		@Override
		public Supplier<R> getArg0() {
			return _f0;
		}

		@Override
		public Function<T1, R> getArg1() {
			return _f1;
		}

		@Override
		public BiFunction<T1, T2, R> getArg2() {
			return _f2;
		}

		@Override
		public Function<Object[], R> getArgN() {
			return _fN;
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toFn(Object f) {
		if (f instanceof I.Fn) {
			return (I.Fn) f;
		} else if (f instanceof Supplier) {
			return new Fn0((Supplier) f);
		} else if (f instanceof Function) {
			return new Fn1((Function) f);
		} else if (f instanceof BiFunction) {
			return new Fn2((BiFunction) f);
		} else {
			throw new Ex.Unsupported();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public class Comp implements OFn {

		final I.Fn[] _fns;

		public Comp(Object... fns) {
			Iterator it = Arr.toRevIter(fns);
			_fns = Iter.toArray(Iter.map(it, Fn::toFn), I.Fn.class);
		}

		public Comp(Object fn) {
			this(new Object[] { fn });
		}

		@Override
		public Function getArg1() {
			return (x) -> Iter.reduce(Arr.toIter((Object[]) _fns), x, (acc, f) -> ((I.Fn) f).invoke(acc));
		}
	}

	public class Reduce<E, R> implements I.Fn<R, Iterator<E>, Object>, Rf<E, R> {

		final Supplier<R> _init;
		final BiFunction<R, E, R> _step;
		final Function<Iterator<E>, R> _f1;

		public Reduce(Supplier<R> init, BiFunction<R, E, R> step) {
			_init = init;
			_step = step;
			_f1 = new Function<Iterator<E>, R>() {
				@Override
				public R apply(Iterator<E> it) {
					return Iter.reduce(it, _init.get(), _step);
				}
			};
		}

		@Override
		public Supplier<R> getArg0() {
			return _init;
		}

		@Override
		public Function<Iterator<E>, R> getArg1() {
			return _f1;
		}

		@Override
		public BiFunction<R, E, R> getStep() {
			return _step;
		}
	}

	public class ReduceIn<E, R> implements I.Fn<R, R, Iterator<E>>, Rf<E, R>{

		final BiFunction<R, E, R> _step;
		final BiFunction<R, Iterator<E>, R> _f2;

		public ReduceIn(BiFunction<R, E, R> step) {
			_step = step;
			_f2 = new BiFunction<R, Iterator<E>, R>() {
				@Override
				public R apply(R self, Iterator<E> it) {
					return Iter.reduce(it, self, _step);
				}
			};
		}

		@Override
		public BiFunction<R, Iterator<E>, R> getArg2() {
			return _f2;
		}

		@Override
		public BiFunction<R, E, R> getStep() {
			return _step;
		}
	}

	public class Map<E, R> implements I.Fn<Iterator<R>, Iterator<E>, Object>, Xf<E, R> {

		final Function<E, R> _unit;
		final Function<Iterator<E>, Iterator<R>> _xf;

		public Map(Function<E, R> unit) {
			_unit = unit;
			_xf = (it) -> Iter.map(it, _unit);
		}

		@Override
		public Function<Iterator<E>, Iterator<R>> getArg1() { return _xf; }

		@Override
		public Function<E, R> getUnit() { return _unit; }
		
		@Override
		public Function<Iterator<E>, Iterator<R>> getXf() { return _xf; }

		@Override
		public UnitType getUnitType() {
			return UnitType.MAP;
		}
	}

	public class Filter<E> implements I.Fn<Iterator<E>, Iterator<E>, Object>, Xf<E, E> {

		final Predicate<E> _pred;
		final Function<Iterator<E>, Iterator<E>> _xf;

		public Filter(Predicate<E> pred) {
			_pred = pred;
			_xf = (it) -> Iter.filter(it, _pred);
		}

		@Override
		public Predicate<E> getUnit() {
			return _pred;
		}
		
		@Override
		public Function<Iterator<E>, Iterator<E>> getArg1() {
			return _xf;
		}
		
		@Override
		public Function<Iterator<E>, Iterator<E>> getXf() {
			return _xf;
		}

		@Override
		public UnitType getUnitType() {
			return UnitType.FILTER;
		}
	}

	@SuppressWarnings("rawtypes")
	public class Pipe<E, R> implements I.Fn<Iterator<R>, Iterator<E>, Object>, Xf<E, R> {

		final Xf[] _pipe;
		final Function<Iterator<E>, Iterator<R>> _xf;

		@Override
		public Function<Iterator<E>, Iterator<R>> getArg1() { return _xf; }
		
		@Override
		public Function<Iterator<E>, Iterator<R>> getXf() { return _xf;}

		@SuppressWarnings("unchecked")
		public Pipe(Xf... pipe) {
			_pipe = pipe;
			_xf = (it) -> {
				Iterator iter = it;
				for (int i = 0; i < _pipe.length; i++) {
					iter = (Iterator) _pipe[i].getXf().apply(iter);
				}
				return (Iterator<R>) iter;
			};
		}

		@Override
		public Object getUnit() {
			// Depends of the Unit type of the actual pipe
			throw new Ex.TODO();
		}

		@Override
		public UnitType getUnitType() {
			// Depends of the Unit type of the actual pipe
			throw new Ex.TODO();
		}
	}

	public class JuxtPair<E, K, V> implements I.Fn<I.Pair<K, V>, E, Object> {

		final Function<E, K> _fk;
		final Function<E, V> _fv;
		final Function<E, I.Pair<K, V>> _f1;

		public JuxtPair(Function<E, K> fk, Function<E, V> fv) {
			_fk = fk;
			_fv = fv;
			_f1 = (e) -> new Tup.Tup2.L<K, V>(null, _fk.apply(e), _fv.apply(e));
		}

		@Override
		public Function<E, I.Pair<K, V>> getArg1() {
			return _f1;
		}
	}

	@SuppressWarnings("rawtypes")
	public class Juxt<E> implements I.Fn<Object[], E, Object> {

		final Function[] _fns;
		final Function<E, Object[]> _f1;

		@SuppressWarnings("unchecked")
		public Juxt(Function... fns) {
			_fns = fns;
			Iterator<Function> it = Arr.toIter((Object)_fns);
			_f1 = (e) -> Iter.toArray(
					Iter.map(it, f -> f.apply(e)));
		}

		@Override
		public Function<E, Object[]> getArg1() {
			return _f1;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public interface Static {
		public static Fn1 identity = new Fn1(x -> x);
		public static OFn comp(Object... fns) {return new Comp(fns);}
		
		// Reduce
		public static Reduce reduce(Supplier init, BiFunction step) {
			return new Reduce(init, step);
		}
		
		public static Reduce reduce(Object init, Object step) {
			return new Reduce(toFn(init).getArg0(), toFn(init).getArg2());
		}
		
		// Reduce In
		public static ReduceIn reduceIn(BiFunction step) {
			return new ReduceIn(step);
		}
		public static ReduceIn reduceIn(Object step) {
			return new ReduceIn(toFn(step).getArg2());
		}
	}
	
}

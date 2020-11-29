package hara.lang.base;

import java.lang.invoke.MethodHandle;
import java.util.Iterator;
import java.util.function.*;

import hara.lang.data.List;

public interface Fn {

	public enum UnitType {
		MAP, FILTER, MAPCAT, COMPLEX
	}

	public interface T {

		public class Fn0<R> extends Obj.FN implements I.Fn<R, Object, Object> {

			final Supplier<R> _f0;

			Fn0(Supplier<R> f0) {
				this(null, f0);
			}
			
			Fn0(I.Metadata meta, Supplier<R> f0) {
				super(meta);
				_f0 = f0;
			}

			@Override
			public Supplier<R> getArg0() {
				return _f0;
			}
		}

		public class Pred1<T1> extends Obj.FN implements I.Fn<Boolean, T1, Object> {

			final Predicate<T1> _p1;

			Pred1(Predicate<T1> p1) {
				this(null, p1);
			}
			
			Pred1(I.Metadata meta, Predicate<T1> p1) {
				super(meta);
				_p1 = p1;
			}

			@Override
			public Function<T1, Boolean> getArg1() {
				return (e) -> _p1.test(e);
			}
		}

		public class Fn1<R, T1> extends Obj.FN implements I.Fn<R, T1, Object> {

			final Function<T1, R> _f1;

			Fn1(Function<T1, R> f1) {
				this(null, f1);
			}

			Fn1(I.Metadata meta, Function<T1, R> f1) {
				super(meta);
				_f1 = f1;
			}

			@Override
			public Function<T1, R> getArg1() {
				return _f1;
			}
		}

		public class Pred2<T1, T2> extends Obj.FN implements I.Fn<Boolean, T1, T2> {

			final BiPredicate<T1, T2> _p2;

			Pred2(BiPredicate<T1, T2> p2) {
				this(null, p2);
			}
			
			Pred2(I.Metadata meta, BiPredicate<T1, T2> p2) {
				super(meta);
				_p2 = p2;
			}

			@Override
			public BiFunction<T1, T2, Boolean> getArg2() {
				return (o, e) -> _p2.test(o, e);
			}
		}

		public class Fn2<R, T1, T2> extends Obj.FN implements I.Fn<R, T1, T2> {

			final BiFunction<T1, T2, R> _f2;

			Fn2(BiFunction<T1, T2, R> f2) {
				this(null, f2);
			}
			
			Fn2(I.Metadata meta, BiFunction<T1, T2, R> f2) {
				super(meta);
				_f2 = f2;
			}

			@Override
			public BiFunction<T1, T2, R> getArg2() {
				return _f2;
			}
		}

		public class FnN<R, T1, T2> extends Obj.FN implements I.Fn<R, T1, T2> {

			final Supplier<R> _f0;
			final Function<T1, R> _f1;
			final BiFunction<T1, T2, R> _f2;
			final Function<Object, R> _fN;

			FnN(Supplier<R> f0, Function<T1, R> f1, BiFunction<T1, T2, R> f2, Function<Object, R> fN) {
				this(null, f0, f1, f2, fN);
			}
			
			FnN(I.Metadata meta, Supplier<R> f0, Function<T1, R> f1, BiFunction<T1, T2, R> f2, Function<Object, R> fN) {
				super(meta);
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
			public Function<Object, R> getArgN() {
				return _fN;
			}
		}

		public interface Rf<E, R> {
			BiFunction<R, E, R> getStep();
		}

		public interface Xf<E, R> {
			Object getUnit();

			UnitType getUnitType();

			Function<Iterator<E>, Iterator<R>> getXf();
		}

		public class FnExact<R> extends Obj.FN implements I.Fn<R, Object, Object> {
			final MethodHandle _mh;
			final int _num;

			FnExact(MethodHandle mh, int num) {
				this(null, mh, num);
			}
			
			FnExact(I.Metadata meta, MethodHandle mh, int num) {
				super(meta);
				_mh = mh;
				_num = num;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Function<Object, R> getArgN() {
				return (args) -> {
					java.util.List arr = It.toArrayList(It.iter(args));

					if (arr.size() != _num) {
						throw new Ex.Arity(arr.size(), "Only " + _num + " Args supported");
					}
					try {
						return (R) _mh.invokeWithArguments(arr);
					} catch (Throwable t) {
						throw Ex.Sneaky(t);
					}
				};
			}
		}

		public class FnMulti<R> extends Obj.FN implements I.Fn<R, Object, Object> {
			final Supplier<R> _f0;
			final Function<Object, R> _f1;
			final BiFunction<Object, Object, R> _f2;
			final Data.MapType<Integer, Object> _fns;

			FnMulti(Data.MapType<Integer, Object> fns) {
				this(null, fns);
			}

			@SuppressWarnings({ "unchecked", "rawtypes" })
			FnMulti(I.Metadata meta, Data.MapType<Integer, Object> fns) {
				super(meta);
				_f0 = (Supplier) fns.lookup(0);
				_f1 = (Function) fns.lookup(1);
				_f2 = (BiFunction) fns.lookup(2);
				_fns = fns;
			}

			@Override
			public Supplier<R> getArg0() {
				if (_f0 == null) {
					throw new Ex.Arity(0, "Not Allowed");
				}
				return _f0;
			}

			@Override
			public Function<Object, R> getArg1() {
				if (_f1 == null) {
					throw new Ex.Arity(1, "Not Allowed");
				}
				return _f1;
			}

			@Override
			public BiFunction<Object, Object, R> getArg2() {
				if (_f2 == null) {
					throw new Ex.Arity(2, "Not Allowed");
				}
				return _f2;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Function<Object, R> getArgN() {
				return (args) -> {
					Object[] arr = Arr.toArray(args);
					var len = arr.length;
					var f = _fns.lookup(len);
					if (f == null) {
						throw new Ex.Arity(arr.length, "");
					} else {
						switch (len) {
						case 0:
							return getArg0().get();
						case 1:
							return getArg1().apply(arr[0]);
						case 2:
							return getArg2().apply(arr[0], arr[1]);
						default:
							try {
								return (R) ((MethodHandle) f).invokeWithArguments(Arr.toList(arr));
							} catch (Throwable t) {
								throw Ex.Sneaky(t);
							}
						}
					}

				};
			}

		}

		public class FnVargs<R> extends Obj.FN implements I.Fn<R, Object, Object> {
			final Function<Object, R> _fn;

			FnVargs(Function<Object, R> fn) {
				this(null, fn);
			}
			
			FnVargs(I.Metadata meta, Function<Object, R> fn) {
				super(meta);
				_fn = fn;
			}

			@Override
			public Supplier<R> getArg0() {
				return () -> _fn.apply(Arr.objects());
			}

			@Override
			public Function<Object, R> getArg1() {
				return (e) -> {
					G.prn(e, Arr.objects(e));
					return _fn.apply(Arr.objects(e));
				};
			}

			@Override
			public BiFunction<Object, Object, R> getArg2() {
				return (e0, e1) -> {
					var out =  _fn.apply(Arr.objects(e0, e1));
					return out;
				};
			}

			@Override
			public Function<Object, R> getArgN() {
				return _fn;
			}
		}

		public class FnReduceArray<E> extends Obj.FN implements I.Fn<E, E, E> {
			final E _init;
			final BiFunction<E, E, E> _f2;

			@SuppressWarnings({ "rawtypes" })
			FnReduceArray(E init, BiFunction p) {
				this(null, init, p);
			}
			
			@SuppressWarnings({ "rawtypes", "unchecked" })
			FnReduceArray(I.Metadata meta, E init, BiFunction p) {
				super(meta);
				_init = init;
				_f2 = p;
			}

			@Override
			public Supplier<E> getArg0() {
				return () -> _init;
			}

			@Override
			public Function<E, E> getArg1() {
				return (e) -> _f2.apply(_init, e);
			}

			@Override
			public BiFunction<E, E, E> getArg2() {
				return (e0, e1) -> _f2.apply(e0, e1);
			}

			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public Function getArgN() {
				return (es) -> It.reduce(It.iter(es), _f2);
			}
		}

		public class FnReduceInit<E> extends Obj.FN implements I.Fn<E, E, E> {
			final E _init;
			final BiFunction<E, E, E> _f2;

			@SuppressWarnings({ "rawtypes" })
			FnReduceInit(E init, BiFunction p) {
				this(null, init, p);
			}
			
			@SuppressWarnings({ "rawtypes", "unchecked" })
			FnReduceInit(I.Metadata meta, E init, BiFunction p) {
				super(meta);
				_init = init;
				_f2 = p;
			}

			@Override
			public Supplier<E> getArg0() {
				return () -> _init;
			}

			@Override
			public Function<E, E> getArg1() {
				return (e) -> _f2.apply(_init, e);
			}

			@Override
			public BiFunction<E, E, E> getArg2() {
				return (e0, e1) -> _f2.apply(_f2.apply(_init, e0), e1);
			}

			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public Function getArgN() {
				return (es) -> It.reduce(It.iter(es), _init, _f2);
			}
		}

		public class FnReduceSelf<E, R> extends Obj.FN implements I.Fn<R, R, E> {
			final R _init;
			final BiFunction<R, E, R> _f2;

			@SuppressWarnings({ "rawtypes" })
			FnReduceSelf(R init, BiFunction p) {
				this(null, init, p);
			}

			@SuppressWarnings({ "rawtypes", "unchecked" })
			FnReduceSelf(I.Metadata meta, R init, BiFunction p) {
				super(meta);
				_init = init;
				_f2 = p;
			}

			@Override
			public Supplier<R> getArg0() {
				return () -> _init;
			}

			@Override
			public Function<R, R> getArg1() {
				return (e) -> e;
			}

			@Override
			public BiFunction<R, E, R> getArg2() {
				return (e0, e1) -> _f2.apply(e0, e1);
			}

			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public Function getArgN() {
				return (es) -> {
					Iterator it = It.iter(es);
					var self = it.next();
					boolean change = false;
					if (self instanceof I.ToMutable) {
						self = ((I.ToMutable) self).toMutable();
						change = true;
					}
					var out = It.reduce(it, self, (BiFunction) _f2);
					if (change && out instanceof I.ToPersistent) {
						return ((I.ToPersistent) self).toPersistent();
					}
					return out;
				};
			}
		}
	}

	public interface H {

		@SuppressWarnings({ "rawtypes", "unchecked" })
		public class Comp implements I.OFn {

			final I.Fn[] _fns;

			public Comp(Object[] fns) {
				Iterator it = Arr.toRevIter(fns);
				_fns = It.toArray(It.map(it, Fn::toFn), I.Fn.class);
			}

			@Override
			public Function getArg1() {
				return (x) -> It.reduce(Arr.toIter((Object[]) _fns), x, (acc, f) -> ((I.Fn) f).invoke(acc));
			}
		}
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public class Partial implements I.OFn {

			final I.Fn _f;
			final List _args;

			public Partial(Object[] vars) {
				if(vars == null || vars.length == 0) {
					throw new Ex.Runtime("Input cannot be null or with length 0");
				}
				_f = Fn.toFn(vars[0]);
				_args = List.Standard.into(Arr.toIter(vars, 1, vars.length));
			}

			@Override
			public Supplier getArg0() {
				return () -> _f.apply(_args);
			}

			@Override
			public Function getArg1() {
				return (x) -> _f.apply(_args.conj(x));
			}

			@Override
			public BiFunction getArg2() {
				return (x, y) -> _f.apply(_args.conj(x).conj(y));
			}

			@Override
			public Function getArgN() {
				return (args) -> _f.apply(
						It.concat(It.iter(_args), It.iter(args)));
			}
		}

		public class Filter<E> extends Obj.FN implements I.Fn<Iterator<E>, Iterator<E>, Object>, T.Xf<E, E> {

			final Predicate<E> _pred;
			final Function<Iterator<E>, Iterator<E>> _xf;

			public Filter(Predicate<E> pred) {
				_pred = pred;
				_xf = (it) -> It.filter(it, _pred);
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
		public class Juxt<E> extends Obj.FN implements I.Fn<Object[], E, Object> {

			final Function[] _fns;
			final Function<E, Object[]> _f1;

			@SuppressWarnings("unchecked")
			public Juxt(Function... fns) {
				_fns = fns;
				Iterator<Function> it = Arr.toIter(_fns);
				_f1 = (e) -> It.toArray(It.map(it, f -> f.apply(e)));
			}

			@Override
			public Function<E, Object[]> getArg1() {
				return _f1;
			}
		}

		public class JuxtPair<E, K, V> extends Obj.FN implements I.Fn<I.Pair<K, V>, E, Object> {

			final Function<E, K> _fk;
			final Function<E, V> _fv;
			final Function<E, I.Pair<K, V>> _f1;

			@SuppressWarnings({ "unchecked", "rawtypes" })
			public JuxtPair(Function<E, K> fk, Function<E, V> fv) {
				_fk = fk;
				_fv = fv;
				_f1 = (e) -> new Std.T.Tup2.L(null, _fk.apply(e), _fv.apply(e));
			}

			@Override
			public Function<E, I.Pair<K, V>> getArg1() {
				return _f1;
			}
		}

		public class Map<E, R> implements I.Fn<Iterator<R>, Iterator<E>, Object>, T.Xf<E, R> {

			final Function<E, R> _unit;
			final Function<Iterator<E>, Iterator<R>> _xf;

			public Map(Function<E, R> unit) {
				_unit = unit;
				_xf = It.map(_unit);
			}

			@Override
			public Function<Iterator<E>, Iterator<R>> getArg1() {
				return _xf;
			}

			@Override
			public Function<E, R> getUnit() {
				return _unit;
			}

			@Override
			public Function<Iterator<E>, Iterator<R>> getXf() {
				return _xf;
			}

			@Override
			public UnitType getUnitType() {
				return UnitType.MAP;
			}
		}

		@SuppressWarnings("rawtypes")
		public class Pipe<E, R> extends Obj.FN implements I.Fn<Iterator<R>, Iterator<E>, Object>, T.Xf<E, R> {

			final T.Xf[] _pipe;
			final Function<Iterator<E>, Iterator<R>> _xf;

			@Override
			public Function<Iterator<E>, Iterator<R>> getArg1() {
				return _xf;
			}

			@Override
			public Function<Iterator<E>, Iterator<R>> getXf() {
				return _xf;
			}

			@SuppressWarnings("unchecked")
			public Pipe(T.Xf... pipe) {
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

		public class Reduce<E, R> extends Obj.FN implements I.Fn<R, Iterator<E>, Object>, T.Rf<E, R> {

			final Supplier<R> _init;
			final BiFunction<R, E, R> _step;
			final Supplier<Boolean> _end;
			final Function<Iterator<E>, R> _f1;

			public Reduce(Supplier<R> init, BiFunction<R, E, R> step) {
				_init = init;
				_step = step;
				_end = null;
				_f1 = (it) -> It.reduce(it, _init.get(), _step);
			}

			public Reduce(Supplier<R> init, BiFunction<R, E, R> step, Supplier<Boolean> end) {
				_init = init;
				_step = step;
				_end = end;
				_f1 = (it) -> It.reduce(it, _init.get(), _step, _end);
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

		public class ReduceIn<E, R> extends Obj.FN implements I.Fn<R, R, Iterator<E>>, T.Rf<E, R> {

			final BiFunction<R, E, R> _step;
			final Supplier<Boolean> _end;
			final BiFunction<R, Iterator<E>, R> _f2;

			public ReduceIn(BiFunction<R, E, R> step) {
				_step = step;
				_end = null;
				_f2 = new BiFunction<R, Iterator<E>, R>() {
					@Override
					public R apply(R self, Iterator<E> it) {
						return It.reduce(it, self, _step);
					}
				};
			}

			public ReduceIn(BiFunction<R, E, R> step, Supplier<Boolean> end) {
				_step = step;
				_end = end;
				_f2 = new BiFunction<R, Iterator<E>, R>() {
					@Override
					public R apply(R self, Iterator<E> it) {
						return It.reduce(it, self, _step, _end);
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

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static T.Fn1 identity = new T.Fn1(x -> x);

	public static <E, R> H.Map<E, R> map(Function<E, R> f) {
		return new H.Map<E, R>(f);
	}

	@SuppressWarnings("unchecked")
	public static <E, R, F> H.Reduce<E, R> reduce(R init, F step) {
		return new H.Reduce<E, R>(Fn.toFn(init).getArg0(), Fn.toFn(init).getArg2());
	}

	// Reduce
	public static <E, R> H.Reduce<E, R> reduce(Supplier<R> init, BiFunction<R, E, R> step) {
		return new H.Reduce<E, R>(init, step);
	}

	// Reduce
	public static <E, R> H.Reduce<E, R> reduce(Supplier<R> init, BiFunction<R, E, R> step, Supplier<Boolean> end) {
		return new H.Reduce<E, R>(init, step, end);
	}

	// Reduce In
	public static <E, R> H.ReduceIn<E, R> reduceIn(BiFunction<R, E, R> step, Supplier<Boolean> end) {
		return new H.ReduceIn<E, R>(step);
	}

	// Reduce In
	public static <E, R> H.ReduceIn<E, R> reduceIn(BiFunction<R, E, R> step) {
		return new H.ReduceIn<E, R>(step);
	}

	@SuppressWarnings("unchecked")
	public static <E, R, F> H.ReduceIn<E, R> reduceIn(F step) {
		return new H.ReduceIn<E, R>(Fn.toFn(step).getArg2());
	}

	public static long hashMurmur(Object o) {
		if (o instanceof I.Hash) {
			return ((I.Hash) o).hashGet(G.HashType.MURMUR3);
		} else if (o == null) {
			return 0;
		} else {
			return o.hashCode();
		}
	}

	public static long hashSip(Object o) {
		if (o == null)
			return 0;
		return o.hashCode();
	}

	public static Function<Object, Long> hashFn(G.HashType t) {

		switch (t) {
		case MURMUR3:
			return item -> Long.valueOf(hashMurmur(item));
		case SIP:
			return item -> Long.valueOf(hashSip(item));
		case SYSTEM:
			return item -> Long.valueOf(item.hashCode());
		default:
			throw new UnsupportedOperationException("Not Supported");
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toFn(I.Metadata meta, Supplier f) {
		return new T.Fn0(f);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toFn(I.Metadata meta, Function f) {
		return new T.Fn1(f);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toFn(I.Metadata meta, Predicate p) {
		return new T.Pred1(p);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toFn(I.Metadata meta, BiFunction f) {
		return new T.Fn2(f);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toFn(I.Metadata meta, BiPredicate p) {
		return new T.Pred2(p);
	}

	@SuppressWarnings({ "rawtypes" })
	public static I.Fn toFn(Object f) {
		return toFn(null, f);
	}
	
	@SuppressWarnings({ "rawtypes" })
	public static I.Fn toFn(I.Metadata meta, Object f) {
		if (f instanceof I.Fn) {
			return (I.Fn) f;
		} else if (f instanceof Supplier) {
			return toFn(meta, (Supplier) f);
		} else if (f instanceof Function) {
			return toFn(meta, (Function) f);
		} else if (f instanceof Predicate) {
			return toFn(meta, (Predicate) f);
		} else if (f instanceof BiFunction) {
			return toFn(meta, (BiFunction) f);
		} else if (f instanceof BiPredicate) {
			return toFn(meta, (BiPredicate) f);
		} else {
			throw new Ex.Unsupported();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toReduceInit(I.Metadata meta, Object init, BiFunction f) {
		return new T.FnReduceInit(meta, init, f);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toReduceArray(I.Metadata meta, Object init, BiFunction f) {
		return new T.FnReduceArray(meta, init, f);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toReduceSelf(I.Metadata meta, Object init, BiFunction f) {
		return new T.FnReduceSelf(meta, init, f);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toVargs(I.Metadata meta, Function<Object, Object> f) {
		return new T.FnVargs(meta, f);
	}

	@SuppressWarnings({ "rawtypes" })
	public static I.Fn toExact(I.Metadata meta, MethodHandle mh, int num) {
		return new T.FnExact(meta, mh, num);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn toMulti(I.Metadata meta, Data.MapType<Integer, Object> fns) {
		return new T.FnMulti(meta, fns);
	}

	//
	// Checks
	//

}

package hara.lang.lib;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.*;
import java.util.Map.Entry;

import hara.lang.base.*;
import hara.lang.base.Module;
import hara.lang.data.*;
import static hara.lang.lib.Builtin.Basic.*;
import static hara.lang.lib.Builtin.Collection.zipmap;
import static hara.lang.lib.Builtin.Lambda.*;
import static hara.lang.lib.Builtin.Struct.*;


@SuppressWarnings({ "unchecked", "rawtypes" })
public interface Env {

	
	public interface Invoke {

		public static Predicate<Object> assignable(Method m, int i) {
			var cparam = m.getParameterTypes()[i];
			return (arg) -> {
				if(arg == null) {
					return true;
				} else {
					return cparam.isAssignableFrom(arg.getClass());	
				}
			};
		}

		public static <R, ITR> R invokeMatch(List<Method> ms, ITR args, int len) {
			var arr = Arr.toArray(args);
			var l = It.reduce(
			  It.range(len), 
			  ms.iterator(), 
			  (it, i) -> It.filter(
					  it, (m) -> assignable(m, i.intValue()).test(arr[i.intValue()])));
		
			if(!l.hasNext()) {
				throw new Ex.Unsupported(ms.peekFirst().getName() + " cannot be applied to " + G.display(arr));
			}
			Method m = l.next();
			return invokeMethod(m, arr);
		}

		public static <R, ITR> R invokeMatch(List<Method> ms, Object arg) {
			
			var it = 
			  It.filter(
				ms.iterator(), 
				(m) -> assignable(m, 0).test(arg));
			if(!it.hasNext()) {
				throw new Ex.Unsupported(ms.peekFirst().getName() + " cannot be applied to " + arg);
			}
			var method = it.next();
			return invokeMethod(method, new Object[]{arg});
		}

		public static <R> R invokeMethod(Method m, Object[] args) {
			try {
				return (R) m.invoke(null, args);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException t) {
				throw Ex.Sneaky(t);
			}
		}
		
	}
	
	public interface S {

		public static Object reduceInit(Module.ReduceInit type) {
			switch (type) {
			case EMPTY_LIST:   return List.Standard.EMPTY;
			case EMPTY_MAP:    return Map.Standard.EMPTY;
			case EMPTY_QUEUE:  return Queue.Standard.EMPTY;
			case EMPTY_VECTOR: return Vector.Standard.EMPTY;
			case NIL:  return null;
			case ONE:  return 1;
			case ZERO: return 0;
			case NEG_ONE:  return -1;
			case TRUE: return true;
			case FALSE: return false;
			default:   throw new Ex.Unsupported();
			}
		}
		
		public static I.Fn reduceFn(I.Fn f, I.Metadata meta, Module.Reduce opts) {
			var init = reduceInit(opts.init());
			switch (opts.type()) {
			case ARRAY:   return new Fn.T.ReduceArray(meta, init, f);
			case INIT:    return new Fn.T.ReduceInit(meta, init, f);
			case SELF:    return new Fn.T.ReduceSelf(meta, init, f);
			case COMPARE: return new Fn.T.ReduceCompare(meta, (Boolean)init, f);
			default:    throw new Ex.Unsupported();
			}
		}

		public static Object getAnnotation(Annotation[] ann, Class c) {
			return Arr.some((a) -> a.annotationType() == c, ann);
		}

		public static Object getAnnotation(Method m, Class c) {
			return getAnnotation(m.getAnnotations(), c);
		}

		public static Module.Fn fnOpts(Method m) {
			return (Module.Fn)getAnnotation(m, Module.Fn.class);
		}

		public static Module.Reduce rdOpts(Method m) {
			return (Module.Reduce)getAnnotation(m, Module.Reduce.class);
		}

		public static Module.Var varOpts(Method m) {
			return (Module.Var)getAnnotation(m, Module.Var.class);
		}
	}

	public static class FnEval<AST> extends Obj.FN implements I.Fn {
		class FnEnv implements I.Env {
			final I.Lookup _map;
			final I.Env _parent;
			final I.Runtime _rt;

			FnEnv(I.Env parent, I.Lookup map, I.Runtime rt) {
				_parent = parent;
				_map = map;
				_rt = rt;
			}

			@Override
			public I.Lookup getMap() {
				return _map;
			}

			@Override
			public I.Env getParent() {
				return _parent;
			}

			@Override
			public I.Runtime getRuntime() {
				return _rt;
			}
		}
		
		final AST  _body;
		final Data.LinearType _params;
	    
	    final I.Runtime _rt;
		final I.Env _env;
	
		public FnEval(I.Metadata meta, I.Runtime rt, I.Env env, Data.LinearType params, AST body) {
	    	super(meta);
	    	_rt = rt;
			_env = env;
	    	_params = params;
	    	_body = body;
	    }
		
		public void checkArgs(int size) {
			if (size != _params.count()) {
				throw new Ex.Arity(size, "Only " + _params.count() + " Args supported");
			}
		}
	
		@Override
		public Supplier getArg0() {
			checkArgs(0);
			return () -> invokeEval(new LinkedList());
		}
	
		@Override
		public Function getArg1() {
			checkArgs(1);
			return (arg) -> invokeEval(Arrays.asList(arg));
		}
	
		@Override
		public BiFunction getArg2() {
			checkArgs(2);
			return (a1, a2) -> invokeEval(Arrays.asList(a1, a2));
		}
	
		@Override
		public Function getArgN() {
			return (vargs) -> {
				java.util.List args = It.toArrayList(It.iter(vargs));
				checkArgs(args.size());
				return invokeEval(args);
			};
		}
	    
	    public Object invokeEval(java.util.List args) {
			try {
				var map = zipmap(_params, args);
				return _rt.eval(_body, new FnEnv(_env, map, _rt));
			} catch (Throwable t) {
				throw Ex.Sneaky(t);
			}
		}
	    
	}

	public static class FnStaticLookup<R> extends Obj.FN implements I.Fn<R, Object, Object> {
		
		final Map<Integer, List<Method>> _mths;
	
		public FnStaticLookup(I.Metadata meta, Map<Integer, List<Method>> mths) {
			super(meta);
			_mths = mths;
		}
		
		@Override
		public Supplier<R> getArg0() {
			Method m = getMethods(0).peekFirst();
			return () -> Invoke.invokeMethod(m, G.EMPTY_ARRAY);
		}
	
		@Override
		public Function<Object, R> getArg1() {
			List<Method> mths = _mths.lookup(1);
			return (arg) -> Invoke.invokeMatch(mths, arg);
		}
	
		@Override
		public BiFunction<Object, Object, R> getArg2() {
			List<Method> mths = _mths.lookup(2);
			return (a0, a1) -> Invoke.invokeMatch(mths, Arr.objects(a0, a1), 2);
		}
	
		@Override
		public Function<Object, R> getArgN() {
			return (vargs) ->  {
				var args = Arr.toArray(vargs);
				List<Method> mths = _mths.lookup(args.length);
				return Invoke.invokeMatch(mths, args, args.length);
			};
		}
	
		public List<Method> getMethods(int argc) {
			var l = _mths.lookup(argc);
			if(l == null) {
				throw new Ex.Arity(argc, "");
			}
			return l;
		}
	}

	public static class FnStaticVargs<R> extends Obj.FN implements I.Fn<R, Object, Object> {
		
		final int _argc;
		final Method _m;
	
		public FnStaticVargs(I.Metadata meta, Method m) {
			super(meta);
			_m = m;
			_argc = m.getParameterCount() - 1;
		}
	
		@Override
		public Supplier<R> getArg0() {
			if(_argc == 0) {
				return () -> Invoke.invokeMethod(_m, new Object[] {G.EMPTY_ARRAY});
			} else {
				throw new Ex.Arity(0, "Need at least " + _argc);
			}
		}
	
		@Override
		public Function<Object, R> getArg1() {
			if(_argc == 0) {
				return (arg) -> Invoke.invokeMethod(_m, new Object[] { new Object[] { arg }});
			} else if (_argc == 1) {
				return (arg) ->  Invoke.invokeMethod(_m, new Object[] { new Object[] { arg, G.EMPTY_ARRAY }});
			} else {
				throw new Ex.Arity(1, "Need at least " + _argc);
			}
		}
	
		@Override
		public BiFunction<Object, Object, R> getArg2() {
			if(_argc == 0) {
				return (a0, a1) -> Invoke.invokeMethod(_m, new Object[] { new Object[] { a0, a1 }});
			} else if (_argc == 1) {
				return (a0, a1) ->  Invoke.invokeMethod(_m, new Object[] { a0, new Object[] { a1 }});
			} else if (_argc == 2) {
				return (a0, a1) ->  Invoke.invokeMethod(_m, new Object[] { a0, a1, G.EMPTY_ARRAY});
			} else {
				throw new Ex.Arity(2, "Need at least " + _argc);
			}
		}
	
		@Override
		public Function<Object, R> getArgN() {
			return (vargs) ->  {
				var it = It.iter(vargs);
				var pargs = It.toArrayList(It.take(it, _argc));
				var args = It.toArray(it);
				pargs.add(args);
				return Invoke.invokeMethod(_m, pargs.toArray());
			};
		}
	}

	public static HashMap<String, ArrayList<Method>> getMethods(Class cls, HashMap<String, ArrayList<Method>> map) {
		
		Function pairs 
			= It.keep((m) -> {
				Module.Fn v = S.fnOpts((Method)m);
				return (v != null && !v.helper()) ? Builtin.Struct.pair(v.name(), m) : null; });
		
		return It.groupBy(
					(Iterator)pairs.apply(It.iter(cls.getDeclaredMethods())), 
					(Function)p -> ((I.Pair) p).getKey(),
					(Function)p -> ((I.Pair) p).getValue(),
					(HashMap)map);
	}
	
	public static HashMap<String, ArrayList<Method>> getMethods(Class cls) {
		return getMethods(cls, new HashMap());
	}
	
	public static I.Fn createFn(Entry<String, ArrayList<Method>> p){
	
		ArrayList<Method> all = p.getValue();

		BiFunction<ArrayList<Method>, Predicate<Method>, ArrayList<Method>> filterList 
			= (list, pred) -> It.toArrayList(It.filter(It.iter(list), pred));
		BiFunction<ArrayList<Method>, Function<Method, ?>, ArrayList> keepList 
			= (list, f) -> It.toArrayList(It.keep(It.iter(list), f));
		
		var vArgs  = filterList.apply(all, (m) -> S.fnOpts(m).vargs());
		var nArgs  = filterList.apply(all, (m) -> !S.fnOpts(m).vargs());
		ArrayList<Module.Reduce> ropts  = keepList.apply(all, (m) -> S.rdOpts(m));
		ArrayList<Module.Var> vopts  = keepList.apply(all, (m) -> S.varOpts(m));
		
		boolean single = all.size() == 1;
		
		var meta = hashMap(Arr.objects(
				keyword("name"), p.getKey(),
				keyword("rt"), S.fnOpts(all.get(0)).rt(),
				keyword("env"), S.fnOpts(all.get(0)).env()));
		
		if(!ropts.isEmpty()) {
			meta = meta.assoc(keyword("reduce"), ropts.get(0));
		}
		if(!vopts.isEmpty()) {
			if(vopts.get(0).macro()) {
				meta = meta.assoc(keyword("macro"), true);
			}
			if(vopts.get(0).dynamic()) {
				meta = meta.assoc(keyword("dynamic"), true);
			}
			if(vopts.get(0).control()) {
				meta = meta.assoc(keyword("control"), true);
			}
		}

		if(vArgs.isEmpty()) {
			var options = groupBy(
					(Function)
					(m) -> ((Method)m).getParameterCount() , all);
			var f = new FnStaticLookup(meta, options);
			if(ropts.isEmpty()) {
				return f;
			} else {
				return S.reduceFn(f, meta, ropts.get(0));
			}
		} else if(nArgs.isEmpty()) {
			if(vArgs.size() >= 1) {
				return new FnStaticVargs(meta, vArgs.get(0));
			} else {
				throw new Ex.Runtime("MORE than one VARG " + meta);
			}
		} else {
			throw new Ex.Runtime("TODO: " + meta);
		}
	}
	
	public static Map<Symbol, Var> loadStatic() {

		var raw = new HashMap();
		
		Arr.reduce((map, cls) -> getMethods(cls, map),
				raw,
				Builtin.class.getDeclaredClasses());
		
		Arr.reduce((map, cls) -> getMethods(cls, map),
				raw,
				Macro.class.getDeclaredClasses());
	
		Map<Symbol, Var> fns = mapEntries(
				(Function)(obj) -> {
					var p = (Entry)obj;
					var k = (String)p.getKey();
					var f = createFn(p);
					return pair(
							symbol(k), 
							new Var(k, f).withMeta(((I.ObjType)f).meta()));
				}, raw);
		
		return fns;
	}
	
	/*
	public static void main(String[] args) {
		var map = loadStatic();
		G.prn(map);
	}
	*/
}

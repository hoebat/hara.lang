package hara.lang.lib;

import hara.lang.base.*;
import hara.lang.data.*;

import static hara.lang.lib.Builtin.Basic.*;
import static hara.lang.lib.Builtin.Collection.zipmap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.function.*;


@SuppressWarnings({"rawtypes", "unchecked"})
public class Var extends Data.NamespacedType.MT 
	implements Data.StringType, Data.VarType, I.Reset<Object> {

	Object _val;

	public Var(String nsname, Object v) {
		super(Map.Standard.EMPTY, nsname);
		_val = v;
	}

	@Override
	public Object deref() {
		return _val;
	}

	@Override
	public String display(){
		return "#'" + pathString();
	}

	@Override
	public G.ObjType getObjType() {
		return G.ObjType.POINTER;
	}
	

	@Override
	public Boolean isControl() {
		return (Boolean) keyword("control").invoke(_meta, false);
	}

	@Override
	public Boolean isDynamic() {
		return (Boolean) keyword("dynamic").invoke(_meta, false);
	}

	@Override
	public Boolean isMacro() {
		return (Boolean) keyword("macro").invoke(_meta, false);
	}

	@Override
	public Object reset(Object v) {
		return _val = v;
	}
	
	public static class FnEval<AST> extends Obj.FN implements I.Fn {
		class Env implements I.Env{
        	final I.Lookup _map;
        	final I.Env _parent;

        	Env(I.Env parent, I.Lookup map) {
        		_parent = parent;
        		_map = map;
        	}
        	
			@Override
			public I.Lookup getMap() {
				return _map;
			}

			@Override
			public I.Env getParent() {
				return _parent;
			}
        }
		
		final AST  _body;
		final Data.LinearType _params;
        
        final I.Runtime _rt;

		public FnEval(I.Metadata meta, I.Runtime rt, Data.LinearType params, AST body) {
        	super(meta);
        	_rt = rt;
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
				return _rt.eval(_body, new Env(_rt.getEnv(), map));
			} catch (Throwable t) {
				throw Ex.Sneaky(t);
			}
		}
        
	}
	
	public interface S {
	
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

	public static class StaticLookup<R> extends Obj.FN implements I.Fn<R, Object, Object> {
		
		final Map<Integer, List<Method>> _mths;

		public StaticLookup(I.Metadata meta, Map<Integer, List<Method>> mths) {
			super(meta);
			_mths = mths;
		}
		
		@Override
		public Supplier<R> getArg0() {
			Method m = getMethods(0).peekFirst();
			return () -> S.invokeMethod(m, G.EMPTY_ARRAY);
		}

		@Override
		public Function<Object, R> getArg1() {
			List<Method> mths = _mths.lookup(1);
			return (arg) -> S.invokeMatch(mths, arg);
		}

		@Override
		public BiFunction<Object, Object, R> getArg2() {
			List<Method> mths = _mths.lookup(2);
			return (a0, a1) -> S.invokeMatch(mths, Arr.objects(a0, a1), 2);
		}

		@Override
		public Function<Object, R> getArgN() {
			return (vargs) ->  {
				var args = Arr.toArray(vargs);
				List<Method> mths = _mths.lookup(args.length);
				return S.invokeMatch(mths, args, args.length);
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
	
	public static class StaticVargs<R> extends Obj.FN implements I.Fn<R, Object, Object> {
		
		final int _argc;
		final Method _m;

		public StaticVargs(I.Metadata meta, Method m) {
			super(meta);
			_m = m;
			_argc = m.getParameterCount() - 1;
		}

		@Override
		public Supplier<R> getArg0() {
			if(_argc == 0) {
				return () -> S.invokeMethod(_m, new Object[] {G.EMPTY_ARRAY});
			} else {
				throw new Ex.Arity(0, "Need at least " + _argc);
			}
		}

		@Override
		public Function<Object, R> getArg1() {
			if(_argc == 0) {
				return (arg) -> S.invokeMethod(_m, new Object[] { new Object[] { arg }});
			} else if (_argc == 1) {
				return (arg) ->  S.invokeMethod(_m, new Object[] { new Object[] { arg, G.EMPTY_ARRAY }});
			} else {
				throw new Ex.Arity(1, "Need at least " + _argc);
			}
		}

		@Override
		public BiFunction<Object, Object, R> getArg2() {
			if(_argc == 0) {
				return (a0, a1) -> S.invokeMethod(_m, new Object[] { new Object[] { a0, a1 }});
			} else if (_argc == 1) {
				return (a0, a1) ->  S.invokeMethod(_m, new Object[] { a0, new Object[] { a1 }});
			} else if (_argc == 2) {
				return (a0, a1) ->  S.invokeMethod(_m, new Object[] { a0, a1, G.EMPTY_ARRAY});
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
				return S.invokeMethod(_m, pargs.toArray());
			};
		}
	}


}
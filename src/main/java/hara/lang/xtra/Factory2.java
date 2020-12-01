package hara.lang.xtra;

import java.lang.reflect.Method;
import java.lang.annotation.Annotation;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import hara.lang.base.*;
import hara.lang.base.Module;
import hara.lang.data.List;
import hara.lang.data.Map;
import hara.lang.data.Queue;
import hara.lang.data.Symbol;
import hara.lang.data.Vector;
import hara.lang.lib.Builtin;
import hara.lang.lib.Builtin.Struct;

import static hara.lang.lib.Builtin.Basic.*;
import static hara.lang.lib.Builtin.Struct.*;

public interface Factory2 {

	public interface To {

		@SuppressWarnings("rawtypes")
		public static Supplier supplier(Method m) {
			try {
				var mHandle = LU.unreflect(m);
				var site = LambdaMetafactory.metafactory(LU, "get", MethodType.methodType(Supplier.class),
						MethodType.methodType(Object.class), mHandle, mHandle.type());
				MethodHandle factory = site.getTarget();
				return (Supplier) factory.invoke();
			} catch (Throwable t) {
				throw Ex.Sneaky(t);
			}
		}

		@SuppressWarnings("rawtypes")
		public static Function function(Method m) {
			try {
				var mHandle = LU.unreflect(m);
				var site = LambdaMetafactory.metafactory(LU, "apply", MethodType.methodType(Function.class),
						MethodType.methodType(Object.class, Object.class), mHandle, mHandle.type());
				MethodHandle factory = site.getTarget();
				return (Function) factory.invoke();
			} catch (Throwable t) {
				throw Ex.Sneaky(t);
			}
		}

		@SuppressWarnings("rawtypes")
		public static BiFunction bifunction(Method m) {
			try {
				var mHandle = LU.unreflect(m);
				var site = LambdaMetafactory.metafactory(LU, "apply", MethodType.methodType(BiFunction.class),
						MethodType.methodType(Object.class, Object.class, Object.class), mHandle, mHandle.type());
				MethodHandle factory = site.getTarget();
				return (BiFunction) factory.invoke();
			} catch (Throwable t) {
				throw Ex.Sneaky(t);
			}
		}

		public static MethodHandle handle(Method m) {
			try {
				return LU.unreflect(m);
			} catch (Throwable t) {
				throw Ex.Sneaky(t);
			}
		}
	}

	public static MethodHandles.Lookup LU = MethodHandles.lookup();

	public static Object reduceInit(Module.ReduceInit type) {
		switch (type) {
		case EMPTY_LIST:   return List.Standard.EMPTY;
		case EMPTY_MAP:    return Map.Standard.EMPTY;
		case EMPTY_QUEUE:  return Queue.Standard.EMPTY;
		case EMPTY_VECTOR: return Vector.Standard.EMPTY;
		case NIL:  return null;
		case ONE:  return 1;
		case ZERO: return 0;
		default:   throw new Ex.Unsupported();
		}
	}
	
	public static I.Metadata methodMeta(Method m) {
		return hashMap(new Object[] {
				 symbol("name"), m.getName(),
				 symbol("params"), list(m.getParameters())
		});
	}
	

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static HashMap<String, ArrayList<Method>> getMethods(Class cls) {
		
		Function pairs 
			= It.keep((m) -> {
				Module.Var v = (Module.Var) getAnnotation((Method) m, Module.Var.class);
				return (v != null) ? Builtin.Struct.pair(v.name(), m) : null; });
		
		Function groups 
			= It.groupBy(
					p -> ((I.Pair) p).getKey(),
					p -> ((I.Pair) p).getValue());
		
		return (HashMap<String, ArrayList<Method>>) 
				It.collect(groups, cls.getDeclaredMethods(), pairs);
	}

	/*
	@SuppressWarnings("rawtypes")
	public static I.Fn fnSupplier(Method m) {
		return Fn.toFn(methodMeta(m), To.supplier(m));
	}

	@SuppressWarnings("rawtypes")
	public static I.Fn fnSingle(Method m) {
		return Fn.toFn(methodMeta(m), To.function(m));
	}

	@SuppressWarnings("rawtypes")
	public static I.Fn fnBi(Method m) {
		return Fn.toFn(methodMeta(m), To.bifunction(m));
	}

	@SuppressWarnings("rawtypes")
	public static I.Fn fnReduce(Method m, Module.Reduce opts) {
		BiFunction f = To.bifunction(m);
		var init = reduceInit(opts.init());
		var meta = methodMeta(m);
		switch (opts.type()) {
		case ARRAY: return Fn.toReduceArray(meta, init, f);
		case INIT:  return Fn.toReduceInit(meta, init, f);
		case SELF:  return Fn.toReduceSelf(meta, init, f);
		default:    throw new Ex.Unsupported();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static I.Fn fnVargs(Method m) {
		return Fn.toVargs(methodMeta(m), To.function(m));
	}

	@SuppressWarnings({ "rawtypes"})
	public static I.Fn fnExact(Method m, int count) {
		return Fn.toExact(methodMeta(m), To.handle(m), count);
	}
	*/
	
	@SuppressWarnings("rawtypes")
	public static Object getAnnotation(Annotation[] ann, Class c) {
		return Arr.some((a) -> a.annotationType() == c, ann);
	}

	@SuppressWarnings("rawtypes")
	public static Object getAnnotation(Method m, Class c) {
		return getAnnotation(m.getAnnotations(), c);
	}

	@SuppressWarnings("rawtypes")
	public static I.Fn createFn(Method m, Module.Fn mFn, Module.Reduce mReduce){
		if (mFn != null) {
			if (mFn.vargs()) { return fnVargs(m); }
		} else if (mReduce != null) {
			return fnReduce(m, mReduce);
		}
		
		var cnt = m.getParameterCount();
		switch (cnt) {
		case 0:  return fnSupplier(m);
		case 1:  return fnSingle(m);
		case 2:  return fnBi(m);
		default: return fnExact(m, cnt);
		}
	}

	public static Session.Var createSingleStatic(String ns, Method m) {
		var mVar = (Module.Var) getAnnotation(m, Module.Var.class);
		var mFn = (Module.Fn) getAnnotation(m, Module.Fn.class);
		var mReduce = (Module.Reduce) getAnnotation(m, Module.Reduce.class);
		var mName = (mVar.name() == "") ? m.getName() : mVar.name();
		Session.Var v = new Session.Var(ns + "/" + mName, null);
		v.reset(createFn(m, mFn, mReduce));
		return v;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Session.Var createMultiStatic(String ns, ArrayList<Method> l) {
		var mVar = (Module.Var) getAnnotation(l.get(0), Module.Var.class);
		var mName = (mVar.name() == "") ? l.get(0).getName() : mVar.name();

		Session.Var v = new Session.Var(ns + "/" + mName, null);
		
		var map = It.collect(
				Map.Standard::into,
				l,
				(Function)It.map((method) -> {
					Method m = (Method)method;
					Object f;
					var cnt = m.getParameterCount();
					switch(cnt) {
					case 0: f = To.supplier(m); break;
					case 1: f = To.function(m); break;
					case 2: f = To.bifunction(m); break;
					default: f = To.handle(m); break;
					}
					return Builtin.Struct.pair(cnt, f);
				}));
		v.reset(Fn.toMulti(methodMeta(l.get(0)), (Data.MapType<Integer, Object>) map));
		return v;
	}

	@SuppressWarnings({ "unchecked" })
	public static Session.Var createVarStatic(String ns, Object entry) {
		var e = (Entry<String, ArrayList<Method>>) entry;
		var l = e.getValue();
		switch (l.size()) {
		case 0:
			throw new Ex.Runtime("Not Allowed");
		case 1:
			return createSingleStatic(ns, l.get(0));
		default:
			return createMultiStatic(ns, l);
		}
	}

	public static BiFunction<String, Object, Session.Var> createVarFn(Session.Instance rt) {
		return (s, entry) -> {
			var v = createVarStatic(s, entry);
			var f = new Fn.H.Partial(new Object[] {
				v.deref(), rt
			});
			v.reset(f);
			return v;
		};
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static HashMap<String, ArrayList<Method>> loadMethods(Class cls) {
		return (HashMap<String, ArrayList<Method>>) It.collect(
				(Function) It.groupBy(
						p -> ((I.Pair) p).getKey(),
						p -> ((I.Pair) p).getValue()), 
						cls.getDeclaredMethods(), 
						(Function) It.keep((m) -> {
							Module.Var v = (Module.Var) getAnnotation((Method) m, Module.Var.class);
							return (v != null) ? Builtin.Struct.pair(v.name(), m) : null;
						}));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Map.Standard<Symbol, Session.Var> loadModule(Class cls, BiFunction<String, Object, Session.Var> create) {
		Module.Ns ns = (Module.Ns) getAnnotation(cls.getAnnotations(), Module.Ns.class);
		var methods = loadMethods(cls);

		var m = It.collect(Map.Standard::into, methods.entrySet(), (Function) It.keep(entry -> {
			var v = create.apply(ns.name(), entry);
			var str = (String) ((Entry) entry).getKey();
			return (v != null && v.deref() != null) ? Builtin.Struct.pair(Symbol.create(str), v) : null;
		}));
		return (Map.Standard<Symbol, Session.Var>) m;
	}
	


	@SuppressWarnings("rawtypes")
	public static Map.Standard<Symbol, Session.Var> loadModule(Class cls) {
		return loadModule(cls, Factory2::createVarStatic);
	}
	

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Map.Standard<Symbol, Session.Var> loadModule(Session.Instance rt, Class cls) {
		return loadModule(cls, Factory2.createVarFn(rt));
	}

	public static void main(String[] args) {
		G.prn(loadModule(Builtin.class).keys());
	}

	/*
	 * 
	 * 
	 * G.prn(ns.name(), It.toArrayList(loadVars(Builtin.class).get("+"), (Function)
	 * It.map(p -> createSingle(ns.name(), (Method) ((I.Pair)
	 * p).getValue()))).get(0));
	 * 
	 * G.prn(v, ((I.Fn)v.deref()).invoke(1,2,3)); /* Session.Var v = new
	 * Session.Var(ns + "/" + mName, f); G.prn(v, mVar, mFn, mReduce);
	 * 
	 * G.prn(f.apply(new Object[] {1,2,3}));
	 */
	/*
	 * public static Iterator<Entry<String, Method>> allMethods() { Method[] methods
	 * = Builtin.class.getDeclaredMethods();
	 * 
	 * return It.keep(Arr.toIter(methods), (m) -> { var ann = Arr.some((a) ->
	 * ((Annotation) a).annotationType() == Core.class, m.getAnnotations()); return
	 * (ann != null) ? Builtin.pair(((Core) ann).name(), m) : null; }); }
	 */

	/*
	 * MethodHandles.Lookup caller = MethodHandles.lookup(); //MethodType methodType
	 * = MethodType.methodType(String.class, Integer.TYPE); MethodType methodType =
	 * MethodType.methodType(Object.class, Object.class); MethodType
	 * actualMethodType = MethodType.methodType(String.class, Object.class);
	 * MethodType invokedType = MethodType.methodType(Function.class); CallSite site
	 * = LambdaMetafactory.metafactory(caller, "apply", invokedType, methodType,
	 * caller.findStatic(String.class, "valueOf", actualMethodType), methodType);
	 * MethodHandle factory = site.getTarget(); Function r = (Function)
	 * factory.invoke(); System.out.println(r.apply(1));
	 */
}

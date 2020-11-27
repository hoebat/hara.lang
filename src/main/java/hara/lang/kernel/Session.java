package hara.lang.kernel;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.*;

import hara.lang.base.*;
import hara.lang.data.*;
import hara.lang.lib.Read;
import hara.lang.lib.Reflect;
import hara.lang.lib.Builtin;

public interface Session {

	@SuppressWarnings({ "unchecked", "rawtypes" })
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
		public Object reset(Object v) {
			return _val = v;
		}

		@Override
		public Boolean isDynamic() {
			return (Boolean) ((Map)_meta).lookup(Builtin.keyword("dynamic"), false);
		}

		@Override
		public Boolean isMacro() {
			return (Boolean) ((Map)_meta).lookup(Builtin.keyword("macro"), false);
		}

		@Override
		public G.ObjType getObjType() {
			return G.ObjType.POINTER;
		}

		@Override
		public String display(){
			return "#'" + pathString();
		}
	}

	public class Global implements Data.EnvType {
		
		public static Map.Standard<Symbol, Var> methods = 
			Factory.loadModule(Builtin.class);
		
		/*
		public static Map.Standard<Symbol, Data.VarType> methods 
			= Builtin.hashMap(new Object[] {
				Builtin.symbol("+"), new Var("+", Fn.toReduceInit(0, Builtin::add)),
				Builtin.symbol("-"), new Var("-", Fn.toReduceArray(0, Builtin::minus)),
				Builtin.symbol("*"), new Var("*", Fn.toReduceInit(1, Builtin::multiply)),
				Builtin.symbol("/"), new Var("/", Fn.toReduceArray(1, Builtin::divide)),
				Builtin.symbol("list"), new Var("list", Fn.toVargs(Builtin::list)),
				Builtin.symbol("vector"), new Var("vector", Fn.toVargs(Builtin::vector)),
				Builtin.symbol("vec"), new Var("vec", Fn.toFn(Builtin::vec)),
				Builtin.symbol("hashmap"), new Var("hashmap", Fn.toVargs(Builtin::hashMap)),
				Builtin.symbol("hashset"), new Var("hashset", Fn.toVargs(Builtin::hashSet)),
				Builtin.symbol("pair"), new Var("pair", Fn.toFn(Builtin::pair)),
				Builtin.symbol("type"), new Var("type", Fn.toFn(Builtin::type)),
				//Builtin.symbol("merge"), new Var("merge", Fn.toFn(Builtin::merge)),
				//Builtin.symbol("flag"), new Var("flag", Fn.toFn(Builtin::flag)),
			});
		*/

		@Override
		public Data.EnvType getParent() {
			return null;
		}

		@Override
		public Map.Standard<Symbol, Var> getMap() {
			return methods;
		}
	}
	
	@SuppressWarnings("rawtypes")
	public class RT implements I.Context {
		public final Foundation _F;
		public final String _key;
		public final Loader _loader;
		public final Global _env;
		public Ut.RefCache<String, Class> REGISTRY;
		
		
		public RT(Foundation F, String key) {
			_F = F;
			_key = key;
			_loader = new Loader();
			_env = new Global();
		}

		@Override
		public Object call(Object... args) {
			System.out.println("CALLED: " + args);
			return null;
		}
		
		@SuppressWarnings("unchecked")
		public ArrayList<String> getClasspath() {
			return (ArrayList<String>)
					It.collect(
					  It::toArrayList,
					  Arr.toIter(_loader.getURLs()),
					  (it) -> It.map(it, url -> url.toString()));
		}
		
		public ArrayList<String> addClasspath(String[] paths) {
			Arr.toIter(paths).forEachRemaining(
				(path) -> {
					try {
						_loader.addURL(new URL(path));
					} catch (MalformedURLException e) {
						e.printStackTrace();
					}
				});
			return getClasspath();
		}
		
		@SuppressWarnings("unchecked")
		public ArrayList<String> findClasspath(String[] names) {
			return (ArrayList<String>)
					It.collect(
					  It::toArrayList,
					  Arr.toIter(names),
					  (it) -> It.keep(it, (n) -> {
						try {
							_loader.loadClass((String)n);
							return n;
						} catch (Throwable t) {
							return null;
						}
					}));	
		}
		
		public Object eval(String input) {
			var ast = Read.LispReader.readString(input, null);
			var out = Eval.eval(ast, _env);
			return out;
		}
		
		public Object invokeStaticMethodVariadic(String className, String methodName, Object... args) {
			return invokeStaticMethod(className, methodName, args);
		}

		public Object invokeStaticMethod(String className, String methodName, Object[] args) {
			Class c = _loader.getClass(className, true);
			return Reflect.invokeStaticMethod(c, methodName, args);
		}

		public Object getStaticField(String className, String fieldName) {
			Class c = _loader.getClass(className, true);
			return Reflect.getStaticField(c, fieldName);
		}

		public Object setStaticField(String className, String fieldName, Object val) {
			Class c = _loader.getClass(className, true);
			return Reflect.setStaticField(c, fieldName, val);
		}
	}

	@SuppressWarnings("rawtypes")
	public class Loader extends URLClassLoader implements I.Watch<Loader, Class> {

		public static URL[] EMPTY_URLS = new URL[] {};
		
		final Ut.RefCache<String, Class> CACHE = new Ut.RefCache<>();

		public Loader() {
			super(EMPTY_URLS, ClassLoader.getSystemClassLoader());
		}

		public Loader(ClassLoader parent) {
			super(EMPTY_URLS, parent);
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			Class c = lookupClass(name);
			return (c != null) ? c : super.findClass(name);
		}

		@Override
		protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			Class c = findLoadedClass(name);
			if (c == null) {
				c = lookupClass(name);
				if (c == null)
					c = super.loadClass(name, false);
			}
			if (resolve)
				resolveClass(c);
			return c;
		}

		@Override
		public void addURL(URL url) {
			super.addURL(url);
		}
		
		public Class defineClass(String name, byte[] bytes, Object srcForm) {
			Class c = defineClass(name, bytes, 0, bytes.length);
			CACHE.register(name, c);
			return c;
		}

		public Class<?> lookupClass(String name) {
			var cr = CACHE.getLookup().get(name);
			return (cr != null) ? cr.get() : null;
		}
		
		public Class getClass(String name, boolean load) {

			try {
				return Class.forName(name, load, this);
			} catch (ClassNotFoundException e) {
				throw Ex.Sneaky(e);
			}
		}
	}

	public interface Eval {
	
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public static Object apply(Object f, Object[] elems) {
			if (f instanceof I.Fn) {
				return ((I.Fn) f).apply(elems);
			} else if (f instanceof Supplier) {
				return ((Supplier) f).get();
			} else if (f instanceof Consumer) {
				((Consumer) f).accept(elems[0]);
				return null;
			} else if (f instanceof Predicate) {
				return ((Predicate) f).test(elems[0]);
			} else if (f instanceof Function) {
				return ((Function) f).apply(elems[0]);
			} else if (f instanceof BiFunction) {
				return ((BiFunction) f).apply(elems[0], elems[1]);
			} else if (f instanceof BiPredicate) {
				return ((BiPredicate) f).test(elems[0], elems[1]);
			} else {
				throw new Ex.Unsupported();
			}
		}
	
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public static Object evalReflect(List ast, Data.EnvType env) {
			return Reflect.invokeInstanceMethod(
					ast.nth(1), 
					((Symbol) ast.nth(2)).display(),
					It.toArray(ast, (Function)It.drop(3)));
		}
	
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public static Object evalList(List ast, Data.EnvType env) {
			if (ast.count() == 0)
				return ast;
	
			Object f;
			var fst = ast.peekFirst();
			if (fst instanceof Symbol) {
				if (fst == Symbol.create(".")) {
					return evalReflect(ast, env);
				} else {
					Var v = (Var) env.getMap().lookup((Symbol) fst);
					if (v == null) {
						throw new Ex.Runtime("Not found: " + Builtin.prStr(fst));
					}
					if (v.isMacro()) {
						f = v.deref();
						var ret = apply(f, It.toArray(ast, (Function)It.drop(1)));
						return eval(ret, env);
					}
				}
			}
	
			ArrayList elems = It.toArrayList(
					ast,
					(Function)It.map(obj -> eval(obj, env)));
			return apply(elems.get(0), It.toArray(elems, (Function)It.drop(1)));
		}
	
		@SuppressWarnings({ "rawtypes", "unchecked", "unused" })
		public static Object eval(Object ast, Data.EnvType env) {
			if (ast instanceof Symbol) {
				Var v = (Var) env.getMap().lookup((Symbol) ast);
				if (v.isMacro()) {
					throw new Ex.Runtime("Cannot return a macro value");
				} else if (v != null) {
					return v.deref();
				} else {
					throw new Ex.Runtime("Cannot find symbol");
				}
			} else if (ast instanceof List) {
				return evalList((List) ast, env);
			} else if (ast instanceof Data.MapType) {
				Function<Entry, Iterator<Object>> mf = (e) -> It.objects(
						eval(e.getKey(), env),
						eval(e.getValue(), env));
				return Builtin.hashMap(It.toArray(ast, (Function)It.mapcat(mf)));
			} else if (ast instanceof I.Coll) {
				return It.toArray(ast, (Function)It.map(obj -> eval(obj, env)));
			} else {
				return ast;
			}
		}
	}
}

package hara.lang.kernel;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.*;

import hara.lang.base.*;
import hara.lang.base.Module;
import hara.lang.base.Ut.RefCache;
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
			return (Boolean) ((Map)_meta).lookup(Builtin.Basic.keyword("dynamic"), false);
		}

		@Override
		public Boolean isMacro() {
			return (Boolean) ((Map)_meta).lookup(Builtin.Basic.keyword("macro"), false);
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

	public class StaticEnv implements Data.EnvType {
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public static Map.Standard<Symbol, Var> methods =
			It.reduce(
				It.objects(
						Factory.loadModule(Builtin.Basic.class),
						Factory.loadModule(Builtin.Collection.class),
						Factory.loadModule(Builtin.Java.class),
						Factory.loadModule(Builtin.Lambda.class),
						Factory.loadModule(Builtin.Ops.class),
						Factory.loadModule(Builtin.Structure.class),
						Factory.loadModule(Builtin.Time.class),
						Factory.loadModule(Builtin.Util.class)),
				null,
				(a, b) -> (Map.Standard)Builtin.Collection.merge((Map)a, (Map)b));
			

		@Override
		public Data.EnvType getParent() {
			return null;
		}

		@Override
		public Map.Standard<Symbol, Var> getMap() {
			return methods;
		}
	}
	
	public class RTEnv implements Data.EnvType {
		
		final Data.EnvType _parent;
		final RT _rt;
		final Map.Standard<Symbol, Var> _methods;
		
		public RTEnv(Data.EnvType parent, RT rt) {
			_parent = parent;
			_rt = rt;
			_methods = Factory.loadModule(rt, Methods.class);
		}
		
		@Override
		public Data.EnvType getParent() {
			return _parent;
		}

		@Override
		public Map.Standard<Symbol, Var> getMap() {
			return _methods;
		}		
	}
	
	@SuppressWarnings("rawtypes")
	public class RT implements I.Context {
		public final Foundation _F;
		public final String _key;
		public final Loader _loader;
		public final StaticEnv _static;
		public final RTEnv _env;
		public Ut.RefCache<String, Class> REGISTRY;
		
		public RT(Foundation F, String key) {
			_F = F;
			_key = key;
			_loader = new Loader();
			_static = new StaticEnv();
			_env = new RTEnv(_static, this);
		}

		@Override
		public Object call(Object... args) {
			System.out.println("CALLED: " + args);
			return null;
		}

		public Foundation getFoundation() {
			return _F;
		}
		
		public Loader getClassLoader() {
			return _loader;
		}

		public Ut.RefCache<String, Class> getClassRegistry() {
			return REGISTRY;
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
		
		public <AST> Object eval(AST input) {
			return Eval.eval(input, _env);
		}
		
		@SuppressWarnings("unchecked")
		public <AST> AST readString(String input) {
			return (AST) Read.LispReader.readString(input, null);
		}
		
		public Object evalString(String input) {
			return eval(readString(input));
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
		public static Object apply(Object f, Iterator it) {
			if (f instanceof I.Fn) {
				return ((I.Fn) f).apply(it);
			} else if (f instanceof Supplier) {
				return ((Supplier) f).get();
			} else if (f instanceof Consumer) {
				((Consumer) f).accept(it.next());
				return null;
			} else if (f instanceof Predicate) {
				return ((Predicate) f).test(it.next());
			} else if (f instanceof Function) {
				return ((Function) f).apply(it.next());
			} else if (f instanceof BiFunction) {
				return ((BiFunction) f).apply(it.next(), it.next());
			} else if (f instanceof BiPredicate) {
				return ((BiPredicate) f).test(it.next(), it.next());
			} else {
				throw new Ex.Unsupported();
			}
		}
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public static Object apply(Object f, Object[] arr) {
			if (f instanceof I.Fn) {
				return ((I.Fn) f).apply(arr);
			} else if (f instanceof Supplier) {
				return ((Supplier) f).get();
			} else if (f instanceof Consumer) {
				((Consumer) f).accept(arr[0]);
				return null;
			} else if (f instanceof Predicate) {
				return ((Predicate) f).test(arr[0]);
			} else if (f instanceof Function) {
				return ((Function) f).apply(arr[0]);
			} else if (f instanceof BiFunction) {
				return ((BiFunction) f).apply(arr[0], arr[1]);
			} else if (f instanceof BiPredicate) {
				return ((BiPredicate) f).test(arr[0], arr[1]);
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
					Var v = (Var) env.find((Symbol) fst);
					if (v == null) {
						throw new Ex.Runtime("Not found: " + G.display(fst));
					}
					if (v.isMacro()) {
						f = v.deref();
						var ret = apply(f, It.iter(ast.popFirst()));
						return eval(ret, env);
					}
				}
			}
			Iterator it = It.map(It.iter(ast), obj -> eval(obj, env));
			return apply(it.next(), It.toArray(it));
		}
	
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public static Object eval(Object ast, Data.EnvType env) {
			if (ast instanceof Symbol) {
				Var v = (Var) env.find((Symbol) ast);
				if (v == null) {
					throw new Ex.Runtime("Cannot find symbol");
				} else if (v.isMacro()) {
					throw new Ex.Runtime("Cannot return a macro value");
				} else {
					return v.deref();
				} 
			} else if (ast instanceof List) {
				return evalList((List) ast, env);
			} else if (ast instanceof Data.MapType) {
				Function<Entry, Entry> mf = (e) -> Builtin.Structure.pair(
						eval(e.getKey(), env),
						eval(e.getValue(), env));
				return Map.Standard.into(It.map(It.iter(ast), mf));
			} else if (ast instanceof Data.LinearType) {
				return Builtin.Structure.vector(It.toArrayList(It.map(It.iter(ast), obj -> eval(obj, env))));
			} else {
				return ast;
			}
		}
	}

	@Module.Ns(name = "builtin", tag = "rt")
	public interface Methods {
	
		@Module.Var(name = "eval")
		@Module.Fn(rt = true)
		public static <AST> Object eval(RT rt, AST input) {
			return rt.eval(input);
		}

		@SuppressWarnings("rawtypes")
		@Module.Var(name = "class:for")
		@Module.Fn(rt = true)
		public static Class classFor(RT rt, String name) {
			try {
				return Class.forName(name, true, rt.getClassLoader());
			} catch (ClassNotFoundException t) {
				throw Ex.Sneaky(t);
			}
		}
		
		@Module.Var(name = "read-string")
		@Module.Fn(rt = true)
		public static <AST> AST readString(RT rt, String input) {
			return rt.readString(input);
		}
		
		@Module.Var(name = "sys:loader")
		@Module.Fn(rt = true)
		public static Loader sysloader(RT rt) {
			return rt.getClassLoader();
		}
		
		@Module.Var(name = "sys:foundation")
		@Module.Fn(rt = true)
		public static Foundation sysFoundation(RT rt) {
			return rt.getFoundation();
		}
		
		@SuppressWarnings("rawtypes")
		@Module.Var(name = "sys:registry")
		@Module.Fn(rt = true)
		public static RefCache<String, Class> sysRegistry(RT rt) {
			return rt.getClassRegistry();
		}
		
		@Module.Var(name = "sys:add-paths")
		@Module.Fn(rt = true)
		public static ArrayList<String> sysAddPath(RT rt, String[] paths) {
			return rt.addClasspath(paths);
		}
		
		@Module.Var(name = "sys:list-paths")
		@Module.Fn(rt = true)
		public static ArrayList<String> sysListPath(RT rt) {
			return rt.getClasspath();
		}
		
		@Module.Var(name = "invoke:static")
		@Module.Fn(rt = true)
		public static Object invokeStatic(RT rt, String cls, String method, Object[] args) {
			return rt.invokeStaticMethod(cls, method, args);
		}
	
		
	}
}

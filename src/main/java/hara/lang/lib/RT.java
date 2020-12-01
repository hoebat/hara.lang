package hara.lang.lib;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import hara.lang.base.*;
import hara.lang.base.I.Env;
import hara.lang.base.I.Lookup;
import hara.lang.data.*;

import static hara.lang.lib.Builtin.Struct.*;
import static hara.lang.lib.Builtin.Lambda.*;

@SuppressWarnings("unchecked")
public interface RT {

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

	public class RootEnv<AST> implements I.Env<Symbol, Var> {
		
		final I.Env<Symbol, Var> _parent;
		final I.Runtime<AST, Symbol, Var> _rt;
		final Map<Symbol, Var> _methods;
		
		public RootEnv(I.Env<Symbol, Var> parent, I.Runtime<AST, Symbol, Var> rt) {
			_parent = parent;
			_rt = rt;
			_methods = loadMethods(rt);
		}
		
		@SuppressWarnings("rawtypes")
		public Map<Symbol, Var> loadMethods(I.Runtime<AST, Symbol, Var> rt) {
			return mapVals(
					(Function)(obj) -> {
						var v = (Var)obj;
						if((Boolean)Keyword.create("rt").invoke(v.meta())) {
							v.reset(partial(v.deref(), Arr.objects(rt)));
						} 
						return v;
					},
					Directory.loadStatic());
		}
		
		@Override
		public I.Env<Symbol, Var> getParent() {
			return _parent;
		}

		@Override
		public Map<Symbol, Var> getMap() {
			return _methods;
		}
	}

	@SuppressWarnings("rawtypes")
	public class ClassEnv implements I.Env<Symbol, Object> {

		final I.Runtime _rt;
		final ConcurrentHashMap<String, Class> _map = new ConcurrentHashMap();

		public ClassEnv(I.Runtime rt) {
			_rt = rt;
		}
		
		@Override
		public Entry<Symbol, Object> find(Symbol sym) {
			if(sym.getNamespace() == null) {
				var s = sym.getName();
				Class c = _map.getOrDefault(s, null);
				if(c == null) { c = _rt.classFor(s);}
				return (c != null) 
						? pair(sym, c)
						: null;
			} else {
				var s = sym.getNamespace();
				Class c = _map.getOrDefault(s, null);
				if(c == null) { c = _rt.classFor(s);}
				return (c != null) 
						? pair(sym, Builtin.Interop.invokeFn(c, sym.getName()))
						: null;
			}
		}

		@Override
		public Env getParent() {
			return null;
		}

		@Override
		public Lookup getMap() {
			throw new Ex.Unsupported();
		}
		
		public Class addAlias(Symbol sym, Class c) {
			var s = sym.getName();
			var p = _map.getOrDefault(s, null);
			_map.put(sym.getName(), c);
			return p;
		}
		
		public Class removeAlias(Symbol sym) {
			var s = sym.getName();
			var c = _map.getOrDefault(s, null);
			_map.remove(s);
			return c;
		}
	}

	public class UserEnv<AST> implements I.Env<Symbol, Var> {
		
		final I.Env<Symbol, Var> _parent;
		final ConcurrentHashMap<Symbol, Var> _methods;
		final I.Lookup<Symbol, Var> _facade;
		public final ClassEnv _class;
		
		@SuppressWarnings("rawtypes")
		public UserEnv(I.Env<Symbol, Var> parent, I.Runtime rt) {
			_parent = parent;
			_methods = new ConcurrentHashMap<Symbol, Var>();
			_facade = new Ut.MapFacade<>(_methods);
			_class = new ClassEnv(rt);
		}
		
		@Override
		public I.Env<Symbol, Var> getParent() {
			return _parent;
		}

		@Override
		public I.Lookup<Symbol, Var> getMap() {
			return _facade;
		}

		@Override
		@SuppressWarnings("rawtypes")
		public Entry find(Symbol sym) {
			Entry e = getMap().find(sym);
			if(e == null) e = _class.find(sym);
			if(e == null) e = _parent.find(sym);
			return e;
		}
		
		public Var getObj(Symbol key) {
			return _methods.get(key);
		}
		
		public Var setObj(Symbol key, Var val) {
			_methods.put(key, val);
			return val;
		}
	}

	@SuppressWarnings("rawtypes")
	public class Instance<AST> implements I.Runtime<AST, Symbol, Var> {
		public final I.Context _root;
		public final String _key;
		public final Loader _loader;
		public final RootEnv _rootEnv;
		public final UserEnv _userEnv;
		public final ThreadLocal<List<I.Env<Symbol, Var>>> _stack;
		
		public Instance(I.Context root, String key) {
			_root = root;
			_key = key;
			_loader = new Loader();
			_rootEnv =  new RootEnv(null, this);
			_userEnv =  new UserEnv(_rootEnv, this);
			_stack = new ThreadLocal() {
		             @Override protected List<I.Env<Symbol, Var>> initialValue() {
		                 return list(Arr.objects(_userEnv));
		             }
				};
		}

		@Override
		public Object call(Object... args) {
			System.out.println("CALLED: " + args);
			return null;
		}

		@Override
		public I.Context getRoot() {
			return _root;
		}
		
		@Override
		public Loader classLoader() {
			return _loader;
		}
		
		@Override
		public Object eval(AST input) {
			return Eval.eval(input, _stack.get().peekFirst());
		}
		
		@Override
		public Object eval(AST input, I.Env env) {
			try {
				_stack.set((List)_stack.get().pushFirst(env));
				return eval(input);
			} finally {
				_stack.set((List)_stack.get().popFirst());
			}
		}
		
		@Override
		public AST readString(String input) {
			return (AST) Read.LispReader.readString(input, null);
		}
		
		@Override
		public I.Fn findFn(Class cls, String name) {
			return null;
		}

		@Override
		public I.Fn findFn(Class cls, String name, int args) {
			return null;
		}

		@Override
		public I.Env getEnv() {
			return _userEnv;
		}
		
		@Override
		public Var getObj(Symbol key) {
			return _userEnv.getObj(key);
		}

		@Override
		public Var setObj(Symbol key, Var value) {
			return _userEnv.setObj(key, value);
		}

		@Override
		public Class classFor(String name) {
			try {
				return _loader.loadClass(name, true);
			} catch (ClassNotFoundException t) {
				return null;
			}
		}

		@Override
		public String[] addPaths(String[] paths) {
			Arr.toIter(paths).forEachRemaining(
					(path) -> {
						try {
							_loader.addURL(new URL(path));
						} catch (MalformedURLException e) {
							e.printStackTrace();
						}
					});
				return listPaths();
		}

		@Override
		public String[] listPaths() {
			return It.toArray(
					It.map(It.iter(_loader.getURLs()), url -> url.toString()),
					String.class);
		}

		@Override
		public Class addAlias(Symbol key, Class v) {
			return _userEnv._class.addAlias(key, v);
		}

		@Override
		public Class removeAlias(Symbol key) {
			return _userEnv._class.removeAlias(key);
		}

		@Override
		public I.Lookup listAlias() {
			return new Ut.MapFacade(_userEnv._class._map);
		}
	}
}

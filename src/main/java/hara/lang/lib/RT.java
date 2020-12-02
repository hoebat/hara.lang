package hara.lang.lib;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import hara.lang.base.*;
import hara.lang.data.*;

import static hara.lang.lib.Builtin.Struct.*;
import static hara.lang.lib.Builtin.Lambda.*;

@SuppressWarnings("unchecked")
public interface RT {

	@SuppressWarnings("rawtypes")
	public class SubLoader extends URLClassLoader {
		
		final ConcurrentHashMap<String, Class> _cache;
		
		public SubLoader(URL[] urls, ConcurrentHashMap<String, Class> cache) {
			super(urls, ClassLoader.getSystemClassLoader());
			_cache = cache;
		}

		@Override
		public void addURL(URL url) {
			throw new Ex.Unsupported();
		}
		
		@Override
		public synchronized Class<?> findClass(String name) throws ClassNotFoundException {
			try {
				Class c = super.findClass(name);
				_cache.put(name, c);
				return c;
				
			} catch (Throwable t) {
				return null;
			}
		}
	}

	@SuppressWarnings("rawtypes")
	public class Loader extends URLClassLoader implements I.Watch<Loader, Class> {
		
		final static URL[] EMPTY_URLS = new URL[] {};
		
		final HashSet<URL> _urls = new HashSet();
		final Ut.AsSet<URL> _urls_facade = new Ut.AsSet<>(_urls);
		final ConcurrentHashMap<String, Class> _cache = new ConcurrentHashMap();
		final Ut.AsMap<String, Class> _cache_facade = new Ut.AsMap(_cache);
		final ClassLoader _parent;

		public Loader() {
			this(ClassLoader.getSystemClassLoader());
		}

		public Loader(ClassLoader parent) {
			super(EMPTY_URLS, parent);
			_parent = parent;
		}

		@SuppressWarnings("resource")
		@Override
		protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			Class c = null; 

			if (c == null) {
				c = _cache.get(name);
				//if(c != null) G.prn("CACHE", c);
			}
			
			if (c == null) {
			   try {
				 c = findLoadedClass(name);
				 //if(c != null) G.prn("LOADER", c);
			   } catch (Throwable t) {}
			}
			
			if (c == null) {
				c = new SubLoader(getURLs(), _cache).findClass(name);
				//if(c != null) G.prn("PATH", c);
			}
			
			if (c == null) {
				c = super.loadClass(name, true);
			}
			if (resolve) resolveClass(c);
			return c;
		}

		@Override
		public void addURL(URL url) {
			_urls.add(url);
		}
		
		@Override
		public URL[] getURLs() {
			return _urls.toArray(EMPTY_URLS);
		}
		
		public Ut.AsMap<String, Class> getCache() {
			return _cache_facade;
		}
		
		public Ut.AsSet<URL> getPaths() {
			return _urls_facade;
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
					Env.loadStatic());
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
		final ConcurrentHashMap<String, Class> _alias = new ConcurrentHashMap();
		public final Ut.AsMap<String, Class> _alias_facade = new Ut.AsMap<>(_alias);

		public ClassEnv(I.Runtime rt) {
			_rt = rt;
		}
		
		@Override
		public Entry<Symbol, Object> find(Symbol sym) {
			if(sym.getNamespace() == null) {
				var s = sym.getName();
				Class c = _alias.getOrDefault(s, null);
				if(c == null) { c = _rt.classFor(s);}
				return (c != null) 
						? pair(sym, c)
						: null;
			} else {
				var s = sym.getNamespace();
				Class c = _alias.getOrDefault(s, null);
				if(c == null) { c = _rt.classFor(s);}

				if (c != null) {
					
					try {
						return pair(sym, Builtin.Interop.invokeGetStatic(c, sym.getName()));
					} catch (Throwable t) {}
					
					try {
						return pair(sym, Builtin.Interop.invokeFn(c, sym.getName()));
					} catch (Throwable t) {}
					
					throw new Ex.Runtime("No method or field found: " + sym.getName());	
				}
				return null;
			}
		}

		@Override
		public I.Env getParent() {
			return null;
		}

		@Override
		public I.Lookup getMap() {
			throw new Ex.Unsupported();
		}
		
		public Class addAlias(Symbol sym, Class c) {
			var s = sym.getName();
			var p = _alias.getOrDefault(s, null);
			_alias.put(sym.getName(), c);
			return p;
		}
		
		public Class removeAlias(Symbol sym) {
			var s = sym.getName();
			var c = _alias.getOrDefault(s, null);
			_alias.remove(s);
			return c;
		}
	}

	public class UserEnv<AST> implements I.Env<Symbol, Var> {
		
		final I.Env<Symbol, Var> _parent;
		final ConcurrentHashMap<Symbol, Var> _methods;
		public final ClassEnv _class;
		public final I.Lookup<Symbol, Var> _facade;
		
		@SuppressWarnings("rawtypes")
		public UserEnv(I.Env<Symbol, Var> parent, I.Runtime rt) {
			_parent = parent;
			_methods = new ConcurrentHashMap<Symbol, Var>();
			_facade = new Ut.AsMap<Symbol, Var>(_methods);
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
			return _root.call(args);
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
		public Data.MapType<String, Class> classCache() {
			return _loader.getCache();
		}
		
		@Override
		public Object eval(AST input) {
			Thread.currentThread().setContextClassLoader(_loader);
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
		public I.Coll<URL> pathAdd(String[] paths) {
			Arr.toIter(paths).forEachRemaining(
					(path) -> {
						try {
							_loader.getPaths().conj(new URL(path));
						} catch (MalformedURLException t) {
							throw Ex.Sneaky(t);
						}
					});
			return _loader.getPaths();
		}
		
		@Override
		public I.Coll<URL> pathRemove(String[] paths) {
			Arr.toIter(paths).forEachRemaining(
					(path) -> {
						try {
							_loader.getPaths().dissoc(new URL(path));
						} catch (MalformedURLException t) {
							throw Ex.Sneaky(t);
						}
					});
			return _loader.getPaths();
		}

		@Override
		public Class aliasAdd(Symbol key, Class v) {
			return _userEnv._class.addAlias(key, v);
		}

		@Override
		public Class aliasRemove(Symbol key) {
			return _userEnv._class.removeAlias(key);
		}

		@Override
		public Ut.AsMap<String, Class> aliasCache() {
			return _userEnv._class._alias_facade;
		}

		@Override
		public I.Coll<URL> pathCache() {
			return _loader.getPaths();
		}
	}
}

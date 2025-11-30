package hara.kernel.base;

import hara.kernel.protocol.IEnv;
import hara.kernel.protocol.IRuntime;
import hara.lang.base.Ex;
import hara.lang.base.Iter;
import hara.lang.base.primitive.Array;
import hara.lang.data.*;
import hara.lang.data.types.IMapType;
import hara.lang.protocol.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static hara.kernel.base.Builtin.Lambda.mapVals;
import static hara.kernel.base.Builtin.Lambda.partial;
import static hara.kernel.base.Builtin.Struct.list;
import static hara.kernel.base.Builtin.Struct.pair;

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
  public class Loader extends URLClassLoader implements IWatch<Loader, Class> {

    static final URL[] EMPTY_URLS = new URL[] {};

    final HashSet<URL> _urls = new HashSet();
    final AsSet<URL> _urls_facade = new AsSet<>(_urls);
    final ConcurrentHashMap<String, Class> _cache = new ConcurrentHashMap();
    final AsMap<String, Class> _cache_facade = new AsMap(_cache);
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
    protected synchronized Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
      Class c = null;

      if (c == null) {
        c = _cache.get(name);
        // if(c != null) G.prn("CACHE", c);
      }

      if (c == null) {
        try {
          c = findLoadedClass(name);
          // if(c != null) G.prn("LOADER", c);
        } catch (Throwable t) {
        }
      }

      if (c == null) {
        c = new SubLoader(getURLs(), _cache).findClass(name);
        // if(c != null) G.prn("PATH", c);
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

    public AsMap<String, Class> getCache() {
      return _cache_facade;
    }

    public AsSet<URL> getPaths() {
      return _urls_facade;
    }
  }

  public class RootEnv<AST> implements IEnv<Symbol, Var> {

    final IEnv<Symbol, Var> _parent;
    final IRuntime<AST, Symbol, Var> _rt;
    final Map<Symbol, Var> _methods;

    public RootEnv(IEnv<Symbol, Var> parent, IRuntime<AST, Symbol, Var> rt) {
      _parent = parent;
      _rt = rt;
      _methods = loadMethods(rt);
    }

    @Override
    public IRuntime getRuntime() {
      return _rt;
    }

    @SuppressWarnings("rawtypes")
    public Map<Symbol, Var> loadMethods(IRuntime<AST, Symbol, Var> rt) {
      return mapVals(
          (Function)
              (obj) -> {
                var v = (Var) obj;
                if ((Boolean) Keyword.create("rt").invoke(v.meta())) {
                  v.reset(partial(v.deref(), Array.objects(rt)));
                }
                return v;
              },
          Env.loadStatic());
    }

    @Override
    public IEnv<Symbol, Var> getParent() {
      return _parent;
    }

    @Override
    public Map<Symbol, Var> getMap() {
      return _methods;
    }

    @Override
    public Iterator<Symbol> keys() {
      return Iter.map(_methods.iterator(), e -> e.getKey());
    }

    @Override
    public Iterator<Var> vals() {
      return Iter.map(_methods.iterator(), e -> e.getValue());
    }
  }

  @SuppressWarnings("rawtypes")
  public class ClassEnv implements IEnv<Symbol, Object> {

    final IRuntime _rt;
    final ConcurrentHashMap<String, Class> _alias = new ConcurrentHashMap();
    public final AsMap<String, Class> _alias_facade = new AsMap<>(_alias);

    public ClassEnv(IRuntime rt) {
      _rt = rt;
    }

    @Override
    public IRuntime getRuntime() {
      return _rt;
    }

    @Override
    public Entry<Symbol, Object> find(Symbol sym) {
      if (sym.getNamespace() == null) {
        var s = sym.getName();
        Class c = _alias.getOrDefault(s, null);
        if (c == null) {
          c = _rt.classFor(s);
        }
        return (c != null) ? pair(sym, c) : null;
      } else {
        var s = sym.getNamespace();
        Class c = _alias.getOrDefault(s, null);
        if (c == null) {
          c = _rt.classFor(s);
        }

        if (c != null) {

          try {
            return pair(sym, Builtin.Interop.invokeGetStatic(c, sym.getName()));
          } catch (Throwable t) {
          }

          try {
            return pair(sym, Builtin.Interop.invokeFn(c, sym.getName()));
          } catch (Throwable t) {
          }

          throw new Ex.Runtime("No method or field found: " + sym.getName());
        }
        return null;
      }
    }

    @Override
    public IEnv getParent() {
      return null;
    }

    @Override
    public ILookup getMap() {
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

  public class UserEnv<AST> implements IEnv<Symbol, Var> {

    final IEnv<Symbol, Var> _parent;
    final ConcurrentHashMap<Symbol, Var> _methods;
    public final ClassEnv _class;
    public final ILookup<Symbol, Var> _facade;

    @SuppressWarnings("rawtypes")
    public UserEnv(IEnv<Symbol, Var> parent, IRuntime rt) {
      _parent = parent;
      _methods = new ConcurrentHashMap<Symbol, Var>();
      _facade = new AsMap<Symbol, Var>(_methods);
      _class = new ClassEnv(rt);
    }

    @Override
    public IRuntime getRuntime() {
      return getParent().getRuntime();
    }

    @Override
    public IEnv<Symbol, Var> getParent() {
      return _parent;
    }

    @Override
    public ILookup<Symbol, Var> getMap() {
      return _facade;
    }

    @Override
    public Iterator<Symbol> keys() {
      return Iter.concat(Iter.map(_methods.entrySet().iterator(), e -> e.getKey()), _parent.keys());
    }

    @Override
    public Iterator<Var> vals() {
      return Iter.concat(
          Iter.map(_methods.entrySet().iterator(), e -> e.getValue()), _parent.vals());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Entry find(Symbol sym) {
      Entry e = getMap().find(sym);
      if (e == null) e = _class.find(sym);
      if (e == null) e = _parent.find(sym);
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
  public class Instance<AST> implements IRuntime<AST, Symbol, Var> {
    public final IContext _root;
    public final String _key;
    public final Loader _loader;
    public final RootEnv _rootEnv;
    public final UserEnv _userEnv;
    public final ThreadLocal<List<IEnv<Symbol, Var>>> _stack;

    public Instance(IContext root, String key) {
      _root = root;
      _key = key;
      _loader = new Loader();
      _rootEnv = new RootEnv(null, this);
      _userEnv = new UserEnv(_rootEnv, this);
      _stack =
          new ThreadLocal() {
            @Override
            protected List<IEnv<Symbol, Var>> initialValue() {
              return list(Array.objects(_userEnv));
            }
          };
    }

    @Override
    public Object call(Object... args) {
      return _root.call(args);
    }

    @Override
    public IContext getRoot() {
      return _root;
    }

    @Override
    public Loader classLoader() {
      return _loader;
    }

    @Override
    public IMapType<String, Class> classCache() {
      return _loader.getCache();
    }

    @Override
    public Object eval(AST input) {
      Thread.currentThread().setContextClassLoader(_loader);
      return Eval.eval(input, _stack.get().peekFirst());
    }

    @Override
    public Object eval(AST input, IEnv env) {
      try {
        _stack.set((List) _stack.get().pushFirst(env));
        return eval(input);
      } finally {
        _stack.set((List) _stack.get().popFirst());
      }
    }

    @Override
    public AST readString(String input) {
      return (AST) Read.LispReader.readString(input, null);
    }

    @Override
    public IFn findFn(Class cls, String name) {
      return null;
    }

    @Override
    public IFn findFn(Class cls, String name, int args) {
      return null;
    }

    @Override
    public IEnv getEnv() {
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
    public IColl<URL> pathAdd(URL url) {
      _loader.addURL(url);
      return _loader.getPaths();
    }

    @Override
    public IColl<URL> pathAdd(String[] paths) {
      Array.toIter(paths)
          .forEachRemaining(
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
    public IColl<URL> pathRemove(String[] paths) {
      Array.toIter(paths)
          .forEachRemaining(
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
    public AsMap<String, Class> aliasCache() {
      return _userEnv._class._alias_facade;
    }

    @Override
    public IColl<URL> pathCache() {
      return _loader.getPaths();
    }
  }
}

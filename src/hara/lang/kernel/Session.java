package hara.lang.kernel;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import hara.lang.base.*;
import hara.lang.experimental.AReference;
import hara.lang.experimental.IPersistentMap;
import hara.lang.experimental.ISeq;
import hara.lang.experimental.Namespace;
import hara.lang.experimental.Symbol;
import hara.lang.experimental.Var;

public class Session {

	public static URL[] EMPTY_URLS = new URL[] {};

	@SuppressWarnings("rawtypes")
	public class Loader extends URLClassLoader {
		final HashMap<Integer, Object[]> CONSTANTS = new HashMap<Integer, Object[]>();
		final ConcurrentHashMap<String, Reference<Class>> LU = new ConcurrentHashMap<String, Reference<Class>>();
		final ReferenceQueue<Class> RQ = new ReferenceQueue<Class>();

		public Loader() {
			super(EMPTY_URLS, ClassLoader.getSystemClassLoader());
		}

		public Loader(ClassLoader parent) {
			super(EMPTY_URLS, parent);
		}

		public Class defineClass(String name, byte[] bytes, Object srcForm) {
			G.clearCache(RQ, LU);
			Class c = defineClass(name, bytes, 0, bytes.length);
			LU.put(name, new SoftReference<Class>(c, RQ));
			return c;
		}

		public Class<?> findInMemoryClass(String name) {
			Reference<Class> cr = LU.get(name);
			if (cr != null) {
				Class c = cr.get();
				if (c != null)
					return c;
				else
					LU.remove(name, cr);
			}
			return null;
		}

		protected Class<?> findClass(String name) throws ClassNotFoundException {
			Class c = findInMemoryClass(name);
			if (c != null)
				return c;
			else
				return super.findClass(name);
		}

		protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			Class c = findLoadedClass(name);
			if (c == null) {
				c = findInMemoryClass(name);
				if (c == null)
					c = super.loadClass(name, false);
			}
			if (resolve)
				resolveClass(c);
			return c;
		}

		public void registerConstants(int id, Object[] val) {
			CONSTANTS.put(id, val);
		}

		public Object[] getConstants(int id) {
			return CONSTANTS.get(id);
		}

		public void addURL(URL url) {
			super.addURL(url);
		}

	}

	public class Namespace extends AReference implements Serializable {
		final public Symbol name;
		transient final AtomicReference<IPersistentMap> mappings = new AtomicReference<IPersistentMap>();
		transient final AtomicReference<IPersistentMap> aliases = new AtomicReference<IPersistentMap>();

		final static ConcurrentHashMap<Symbol, Namespace> namespaces = new ConcurrentHashMap<Symbol, Namespace>();

		public String toString() {
			return name.toString();
		}

		Namespace(Symbol name) {
			super(name.meta());
			this.name = name;
			mappings.set(RT.DEFAULT_IMPORTS);
			aliases.set(RT.map());
		}

		public static ISeq all() {
			return RT.seq(namespaces.values());
		}

		public Symbol getName() {
			return name;
		}

		public IPersistentMap getMappings() {
			return mappings.get();
		}

		public Var intern(Symbol sym) {
			if (sym.ns != null) {
				throw new IllegalArgumentException("Can't intern namespace-qualified symbol");
			}
			IPersistentMap map = getMappings();
			Object o;
			Var v = null;
			while ((o = map.valAt(sym)) == null) {
				if (v == null)
					v = new Var(this, sym);
				IPersistentMap newMap = map.assoc(sym, v);
				mappings.compareAndSet(map, newMap);
				map = getMappings();
			}
			if (o instanceof Var && ((Var) o).ns == this)
				return (Var) o;

			if (v == null)
				v = new Var(this, sym);

			warnOrFailOnReplace(sym, o, v);

			while (!mappings.compareAndSet(map, map.assoc(sym, v)))
				map = getMappings();

			return v;
		}

		private void warnOrFailOnReplace(Symbol sym, Object o, Object v) {
			if (o instanceof Var) {
				Namespace ns = ((Var) o).ns;
				if (ns == this || (v instanceof Var && ((Var) v).ns == RT.CLOJURE_NS))
					return;
				if (ns != RT.CLOJURE_NS)
					throw new IllegalStateException(sym + " already refers to: " + o + " in namespace: " + name);
			}
			RT.errPrintWriter().println("WARNING: " + sym + " already refers to: " + o + " in namespace: " + name
					+ ", being replaced by: " + v);
		}

		Object reference(Symbol sym, Object val) {
			if (sym.ns != null) {
				throw new IllegalArgumentException("Can't intern namespace-qualified symbol");
			}
			IPersistentMap map = getMappings();
			Object o;
			while ((o = map.valAt(sym)) == null) {
				IPersistentMap newMap = map.assoc(sym, val);
				mappings.compareAndSet(map, newMap);
				map = getMappings();
			}
			if (o == val)
				return o;

			warnOrFailOnReplace(sym, o, val);

			while (!mappings.compareAndSet(map, map.assoc(sym, val)))
				map = getMappings();

			return val;

		}

		public static boolean areDifferentInstancesOfSameClassName(Class cls1, Class cls2) {
			return (cls1 != cls2) && (cls1.getName().equals(cls2.getName()));
		}

		Class referenceClass(Symbol sym, Class val) {
			if (sym.ns != null) {
				throw new IllegalArgumentException("Can't intern namespace-qualified symbol");
			}
			IPersistentMap map = getMappings();
			Class c = (Class) map.valAt(sym);
			while ((c == null) || (areDifferentInstancesOfSameClassName(c, val))) {
				IPersistentMap newMap = map.assoc(sym, val);
				mappings.compareAndSet(map, newMap);
				map = getMappings();
				c = (Class) map.valAt(sym);
			}
			if (c == val)
				return c;

			throw new IllegalStateException(sym + " already refers to: " + c + " in namespace: " + name);
		}

		public void unmap(Symbol sym) {
			if (sym.ns != null) {
				throw new IllegalArgumentException("Can't unintern namespace-qualified symbol");
			}
			IPersistentMap map = getMappings();
			while (map.containsKey(sym)) {
				IPersistentMap newMap = map.without(sym);
				mappings.compareAndSet(map, newMap);
				map = getMappings();
			}
		}

		public Class importClass(Symbol sym, Class c) {
			return referenceClass(sym, c);

		}

		public Class importClass(Class c) {
			String n = c.getName();
			return importClass(Symbol.intern(n.substring(n.lastIndexOf('.') + 1)), c);
		}

		public Var refer(Symbol sym, Var var) {
			return (Var) reference(sym, var);

		}

		public static Namespace findOrCreate(Symbol name) {
			Namespace ns = namespaces.get(name);
			if (ns != null)
				return ns;
			Namespace newns = new Namespace(name);
			ns = namespaces.putIfAbsent(name, newns);
			return ns == null ? newns : ns;
		}

		public static Namespace remove(Symbol name) {
			if (name.equals(RT.CLOJURE_NS.name))
				throw new IllegalArgumentException("Cannot remove hara namespace");
			return namespaces.remove(name);
		}

		public static Namespace find(Symbol name) {
			return namespaces.get(name);
		}

		public Object getMapping(Symbol name) {
			return mappings.get().valAt(name);
		}

		public Var findInternedVar(Symbol symbol) {
			Object o = mappings.get().valAt(symbol);
			if (o != null && o instanceof Var && ((Var) o).ns == this)
				return (Var) o;
			return null;
		}

		public IPersistentMap getAliases() {
			return aliases.get();
		}

		public Namespace lookupAlias(Symbol alias) {
			IPersistentMap map = getAliases();
			return (Namespace) map.valAt(alias);
		}

		public void addAlias(Symbol alias, Namespace ns) {
			if (alias == null || ns == null)
				throw new NullPointerException("Expecting Symbol + Namespace");
			IPersistentMap map = getAliases();
			while (!map.containsKey(alias)) {
				IPersistentMap newMap = map.assoc(alias, ns);
				aliases.compareAndSet(map, newMap);
				map = getAliases();
			}
			// you can rebind an alias, but only to the initially-aliased namespace.
			if (!map.valAt(alias).equals(ns))
				throw new IllegalStateException(
						"Alias " + alias + " already exists in namespace " + name + ", aliasing " + map.valAt(alias));
		}

		public void removeAlias(Symbol alias) {
			IPersistentMap map = getAliases();
			while (map.containsKey(alias)) {
				IPersistentMap newMap = map.without(alias);
				aliases.compareAndSet(map, newMap);
				map = getAliases();
			}
		}

		private Object readResolve() throws ObjectStreamException {
			// ensures that serialized namespaces are "deserialized" to the
			// namespace in the present runtime
			return findOrCreate(name);
		}
	}
	
	
}

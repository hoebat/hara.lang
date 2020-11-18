package hara.lang.kernel;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import hara.lang.base.*;

public class Session {

	public static URL[] EMPTY_URLS = new URL[] {};

	@SuppressWarnings("rawtypes")
	public class SessionClassLoader extends URLClassLoader {
		final HashMap<Integer, Object[]> CONSTANTS = new HashMap<Integer, Object[]>();
		final ConcurrentHashMap<String, Reference<Class>> LU = new ConcurrentHashMap<String, Reference<Class>>();
		final ReferenceQueue<Class> RQ = new ReferenceQueue<Class>();

		public SessionClassLoader() {
			super(EMPTY_URLS, ClassLoader.getSystemClassLoader());
		}

		public SessionClassLoader(ClassLoader parent) {
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

	
}

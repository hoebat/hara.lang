package hara.lang.base;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.List;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface Java {

	public interface Access {
		
	}
	
	public interface First {
		
		public static Object first(Iterator it) {
			return it.next();
		}

		public static Object first(Iterable c) {
			return first(c.iterator());
		}
	
		public static Object first(Collection c) {
			return first(c.iterator());
		}

		public static Object first(List c) {
			return c.get(0);
		}
	}
	
	public interface Count {
		
		public static long count(Iterator it) {
			int i = 0;
			while(it.hasNext()) {it.next();i++;};
			return i;
		}
		
		public static long count(Iterable c) {
			return count(c.iterator());
		}
		
		public static long count(Collection c) {
			return c.size();
		}
	}

	public interface Nth {

		public static Object nth(Collection c, long idx) {
			return c.iterator();
		}

		public static Object nth (List c, long idx) {
			return c.get((int)idx);
		}
	}
	
	public interface Reduce {
		
	}
	
	public static Map assoc(Map c, Object key, Object val) {
		c.put(key, val);
		return c;
	}
	
	public static Map dissoc(Map c, Object key) {
		c.remove(key);
		return c;
	}

}

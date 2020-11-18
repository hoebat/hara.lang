package hara.lang.base;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public interface G {
	
	public static final Object[] EMPTY_ARRAY = new Object[] {};
	public static final Boolean F = Boolean.FALSE;
	public static final Boolean T = Boolean.TRUE;
	
	public enum MetaType { OBJECT, MAP, STRING }
	public enum HashType { SYSTEM, MURMUR3, SIP };
	public enum ObjType { KEYWORD, SYMBOL, POINTER, FUNCTION, MAP, SET, ITERATOR, SEQUENTIAL}

	public static final HashType DEFAULT_HASH = HashType.MURMUR3;

	@SuppressWarnings("unchecked")
	public static int compare(Object k1, Object k2) {
		if (k1 == k2)
			return 0;
		if (k1 != null) {
			if (k2 == null)
				return 1;
			if (k1 instanceof Number)
				return Num.compare((Number) k1, (Number) k2);
			return ((Comparable<Object>) k1).compareTo(k2);
		}
		return -1;
	}

	public static boolean equals(Object k1, Object k2) {
		return (k1 == k2) ? true : (k1 != null && k1.equals(k2));
	}

	public static boolean identical(Object k1, Object k2) {
		return k1 == k2;
	}

	@SuppressWarnings({ "unchecked", "hiding" })
	private static <T extends Throwable> void sneakyThrow0(Throwable t) throws T {
		throw (T) t;
	}

	public static RuntimeException sneakyThrow(Throwable t) {
		if (t == null)
			throw new NullPointerException();
		sneakyThrow0(t);
		return null;
	}

	public static RuntimeException runtimeException(String s) {
		return new RuntimeException(s);
	}

	public static RuntimeException runtimeException(String s, Throwable e) {
		return new RuntimeException(s, e);
	}

	@SuppressWarnings("rawtypes")
	public static Iterator toIter(Object v) {
		return null;
	}

	static public <K, V> void clearCache(ReferenceQueue<V> rq, ConcurrentHashMap<K, Reference<V>> cache) {
		// cleanup any dead entries
		if (rq.poll() != null) {
			while (rq.poll() != null)
				;
			for (Map.Entry<K, Reference<V>> e : cache.entrySet()) {
				Reference<V> val = e.getValue();
				if (val != null && val.get() == null)
					cache.remove(e.getKey(), val);
			}
		}
	}
}

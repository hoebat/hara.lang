package hara.lang.base;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public interface Ut {

	public class Clock {

		private final static Clock _clock;

		static {
			_clock = new Clock();
		}

		private final long _tsys;
		private final long _toff;

		private Clock() {
			_tsys = System.currentTimeMillis() * 1000000;

			// typically 36 ns, between these two lines.
			_toff = System.nanoTime();
		}

		public static final long currentTimeNanos() {
			return _clock._tsys + (System.nanoTime() - _clock._toff);
		}

		public static final long currentTimeMicros() {
			return currentTimeNanos() / 1000;
		}

		public static final long currentTimeMillis() {
			return currentTimeNanos() / 1000000;
		}

	}

	public class Counter implements I.Deref<Integer> {

		private int _c;

		public Counter(int count) {
			_c = count;
		}

		public int inc() {
			return _c += 1;
		}

		public int inc(int n) {
			return _c += n;
		}

		public int dec() {
			return _c -= 1;
		}

		public int dec(int n) {
			return _c -= n;
		}

		public int reset(int count) {
			_c = count;
			return count;
		}

		public Integer deref() {
			return _c;
		}
	}

	public class Delay<V> implements I.Deref<V>, I.Realize<V> {
		volatile V _val;
		volatile Throwable _ex;
		volatile Supplier<V> _fn;

		public Delay(Supplier<V> fn) {
			_fn = fn;
			_val = null;
			_ex = null;
		}

		public V deref() {
			if (_fn != null) {
				synchronized (this) {
					// double check
					if (_fn != null) {
						try {
							_val = _fn.get();
						} catch (Throwable t) {
							_ex = t;
						}
						_fn = null;
					}
				}
			}
			if (_ex != null)
				throw Ex.Sneaky(_ex);
			return _val;
		}

		@Override
		synchronized public boolean isRealized() {
			return _fn == null;
		}

		@Override
		public V realize() {
			return deref();
		}
	}

	public final class Volatile<V> implements I.Deref<V> {

		public volatile V _val;

		public Volatile(V val) {
			_val = val;
		}

		public V deref() {
			return _val;
		}

		public V reset(V newval) {
			return _val = newval;
		}

	}
	
	public final class RefCache<K, V> implements I.Lookup<K, Reference<V>> {

		final ConcurrentHashMap<K, Reference<V>> _lu;
		final ReferenceQueue<V> _rq;
		
		public RefCache() {
			 _lu = new ConcurrentHashMap<K, Reference<V>>();
			 _rq = new ReferenceQueue<V>();
		}

		@Override
		public Entry<K, Reference<V>> find(K key) {
			var ret = _lu.getOrDefault(key, null);
			return (ret == null) ? null : Tup.pair(key, ret);
		}

		@Override
		public Iterator<K> keys() {
			return _lu.keys().asIterator();
		}

		@Override
		public Reference<V> lookup(K key) {
			return _lu.get(key);
		}

		@Override
		public Iterator<Reference<V>> vals() {
			return _lu.values().iterator();
		}
		
		public ReferenceQueue<V> getQueue() {
			return _rq;
		}
		
		public ConcurrentHashMap<K, Reference<V>> getLookup() {
			return _lu;
		}
		
		public V getOrCreate(K key, Supplier<Reference<V>> f) {
			var ref = _lu.get(key);
			if (ref != null) {
				var v = ref.get();
				if (v != null) {
					return v;
				}
			}
			
			ref = f.get();
			_lu.put(key, ref);
			return ref.get();
		}

		public void clearCache() {
			if (_rq.poll() != null) {
				while (_rq.poll() != null) {}
				
				var it = _lu.entrySet().iterator();
				Iter.filter(it, (e) -> (e.getValue() == null) || (e.getValue().get() == null))
					.forEachRemaining((e) -> _lu.remove(e.getKey()));
			}
		}
	}

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
	
}

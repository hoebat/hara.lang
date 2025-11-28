package hara.lang.base;

import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.*;

public interface I {

	public interface Assoc<K, V> {
		Assoc<K, V> assoc(K k, V v);
	}

	public interface Coll<E> extends Iterable<E>, Equality, Conj<E>, Empty, Count, Hash, Display {

		String startString();

		String endString();

		default String sepString() {
			return " ";
		}

		@Override
		default String display() {
			return It.display(iterator(), startString(), endString(), sepString());
		}
	}

	public interface Component {
    
		Metadata getProps();

		Metadata getStatus();

		boolean isStarted();

		boolean isStopped();

		Component start();

		Component stop();

		default Component kill() {
		  return this.stop();
		}
    
		default boolean isRemote() {
		  return false;
		}
	}

	public interface Conj<E> {
		Conj<E> conj(E e);
	}

	public interface Cons<E> {
		Cons<E> cons(E e);
	}

	public interface Context {
		Object call(Object... args);
	}

	public interface Count {
		long count();
	}

	public interface Deref<V> {
		V deref();
	}

	public interface DerefTimeout<V> {
		V derefTimeout(long ms, V timeoutVal);
	}

	public interface Display {
    
		String display();
		/*
		 * default String display() { return toString(); }
		 */
	}

	public interface Dissoc<K> {
		Dissoc<K> dissoc(K k);
	}

	public interface Empty {
		Empty empty();
	}

	public interface Equality {
		boolean equality(Object other);
	}

	public interface ExInfo {
		public Metadata getData();
	}

	public interface Find<K, V> {
		V find(K key);

		default boolean has(K key) {
			return find(key) != null;
		}
	}

	public interface Fn<R, T1, T2> extends Function<Object, R> {

		@SuppressWarnings("rawtypes")
		public static <R, T1, T2> R applyAsIterator(Fn<R, T1, T2> f, Iterator vargs) {
			return f.getArgN().apply(vargs);
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		public static <R, T1, T2> R applyAsIterator(Fn<R, T1, T2> f, Iterator it, long size) {
			switch ((int)size) {
			case 0:  return f.getArg0().get();
			case 1:  return f.getArg1().apply((T1)it.next());
			case 2:  return f.getArg2().apply((T1)it.next(), (T2)it.next());
			default: return f.getArgN().apply(it);
			}
		}

		@SuppressWarnings("unchecked")
		public static <R, T1, T2> R applyAsArray(Fn<R, T1, T2> f, Object[] vargs) {
			int len = vargs.length;
			switch (len) {
			case 0:  return f.getArg0().get();
			case 1:  return f.getArg1().apply((T1)vargs[0]);
			case 2:  return f.getArg2().apply((T1)vargs[0], (T2)(T1)vargs[1]);
			default: return f.getArgN().apply(vargs);
			}
		}
		
		@SuppressWarnings({"rawtypes" })
		@Override
		default R apply(Object vargs) {
			if (vargs instanceof Iterator) {
				return applyAsIterator(this, (Iterator)vargs);
			} else if (vargs.getClass().isArray()) {
				return applyAsArray(this, (Object[])vargs);
			} else if (vargs instanceof java.util.List) {
				var l = (java.util.List)vargs;
				return applyAsIterator(this, l.iterator(), l.size());
			} else if (vargs instanceof Data.LinearType) {
				var l = (Data.LinearType)vargs;
				return applyAsIterator(this, l.iterator(), l.count());
			} else if (vargs instanceof Iterable) {
				return applyAsIterator(this, (Iterator)vargs);
			} else {
				throw new Ex.Unsupported();
			}
		}

		default Supplier<R> getArg0() {
			throw new Ex.Arity(0, "No arity 0");
		}

		default Function<T1, R> getArg1() {
			throw new Ex.Arity(1, "No arity 1");
		}

		default BiFunction<T1, T2, R> getArg2() {
			throw new Ex.Arity(2, "No arity 2");
		}

		default Function<Object, R> getArgN() {
			throw new Ex.Arity(0, "No arity N");
		}

		default R invoke() {
			return getArg0().get();
		}

		default R invoke(T1 a1) {
			return getArg1().apply(a1);
		}

		default R invoke(T1 a1, T2 a2) {
			return getArg2().apply(a1, a2);
		}

		default R invoke(Object... vargs) {
			return getArgN().apply(vargs);
		}
	}

	public interface OFn extends Fn<Object, Object, Object> {
	}

	public interface Hash {

		default long hashCalc() {
			return hashCalc(hashType());
		}

		long hashCalc(G.HashType t);

		default long hashGet() {
			return hashCalc(hashType());
		}

		default long hashGet(G.HashType t) {
			return hashCalc(t);
		}

		String hashSeed();;

		default G.HashType hashType() {
			return G.DEFAULT_HASH;
		}
	}

	public interface HashCached extends Hash {

		long hashCurrent();

		@Override
		default long hashGet() {
			long h = hashCurrent();
			if (h == 0) {
				h = hashCalc();
				hashPut(h);
			}
			return h;
		}

		@Override
		default long hashGet(G.HashType t) {
			return (hashType() == t) ? hashGet() : hashCalc(t);
		}

		void hashPut(long hash);
	}

	public interface Indexed<K, V> {
		K indexOf(V val);
	}

	public interface IndexedKV<K, V> {
		long indexOfKey(K key);

		long indexOfVal(V val);
	}

	public interface InvokeIn {
		Object invokeIn(Context context, Object... args);
	}

	public interface Lookup<K, V> extends Find<K, Map.Entry<K, V>> {

		Iterator<K> keys();
		
		default V lookup(K key) {
			return lookup(key, null);
		}

		default V lookup(K key, V notFound) {
			Map.Entry<K, V> ret = find(key);
			return (ret == null) ? notFound : ret.getValue();
		}

		Iterator<V> vals();
	}

	public interface Metadata {

		G.MetaType getMetatype();
	}

	public interface Mutable {
	}

	public interface Namespaced {
		String getName();

		String getNamespace();
	}

	public interface Nth<E> {
		E nth(long i);
	}

	public interface ObjType extends Hash, Display {

		default G.ObjType getObjType() {
			return G.ObjType.CLASS;
		}

		default String getObjName() {
			return getObjType().toString();
		}

		@Override
		default String hashSeed() {
			return "::" + getObjName() + "";
		}

		I.Metadata meta();

		ObjType withMeta(I.Metadata meta);
	}

	public interface Pair<K, V> extends Map.Entry<K, V> {
		@Override
		default V setValue(V value) {
			throw new Ex.Unsupported();
		}
	}

	public interface PeekFirst<E> {
		E peekFirst();
	}

	public interface PeekLast<E> {
		E peekLast();
	}

	public interface Persistent {
	}

	public interface PopFirst {
		PopFirst popFirst();
	}

	public interface PopLast {
		PopLast popLast();
	}

	public interface PushFirst<E> {
		PushFirst<E> pushFirst(E e);
	}

	public interface PushLast<E> {
		PushLast<E> pushLast(E e);
	}

	public interface Ranged {
		long rangeMax();

		long rangeMin();
	}

	public interface Realize<V> {
		boolean isRealized();

		V realize();
	}

	public interface Reset<V> {
		V reset(V v);
	}

	public interface ToMutable extends Persistent {
		Mutable toMutable();
	}

	public interface ToPersistent extends Mutable {
		Persistent toPersistent();
	}

	public interface Validate<V> {
		default Predicate<V> getValidator() {
			return null;
		}

		default boolean validate(V newVal) {
			var f = getValidator();
			if (f == null)
				return true;

			boolean result = f.test(newVal);
			if(!result) {
				throw new IllegalStateException("Validator rejected value: " + newVal);
			}
			return result;
		}
	}

	public interface Watch<R, V> {
		default void addWatch(Object key, Consumer<WatchEntry<R, V>> f) {
			throw new UnsupportedOperationException("Not Supported");
		}

		default Iterator<Map.Entry<Object, Consumer<WatchEntry<R, V>>>> getWatches() {
			return null;
		}

		default void notifyWatches(V oldVal, V newVal) {
			Iterator<Map.Entry<Object, Consumer<WatchEntry<R, V>>>> ws = getWatches();
			if (ws != null) {
				ws.forEachRemaining(e -> e.getValue().accept(new WatchEntry<R, V>(e.getKey(), this, oldVal, newVal)));
			}
		}

		default void removeWatch(Object key) {
			throw new UnsupportedOperationException("Not Supported");
		}

		@SuppressWarnings("unchecked")
		public class WatchEntry<R, V> extends Std.T.Tup5.L<Object, R, Object, V, V> {

			WatchEntry(Object key, Watch<R, V> ref, V oldVal, V newVal) {
				super(null, key, (R) ref, null, oldVal, newVal);
			}

			public V oldVal() {
				return this.D();
			}

			public V newVal() {
				return this.E();
			}
		}
	}

	public interface HasRuntime {
		Object getRuntime();
	}

}

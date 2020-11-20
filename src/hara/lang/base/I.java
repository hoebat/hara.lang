package hara.lang.base;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface I {
	
	public interface Assoc<K, V> {
		Assoc<K, V> assoc(K k, V v);
	}
	
	public interface Coll<V> extends 
		Iterable<V>, 
		Equality, 
		Conj<V>, 
		Empty, 
		Count, 
		Hash, 
		ToSeq<V> {
	}

	public interface Component{
		Metadata  getProps();
		Metadata  getStatus();
		boolean isStarted();
		boolean isStopped();
		Component start();
		Component stop();
	}

	public interface Conj<V> {
		Conj<V> conj(V e);
	}

	public interface Cons<V> {
		Cons<V> cons(V e);
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
		default String display() {
			return toString();
		}
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
	}
	
	public interface Fn<R, T1, T2> extends Function<Object[], R>{
		
		@SuppressWarnings("unchecked")
		@Override
		default R apply(Object[] input) {
			int len = input.length;
			switch(len) {
			case 0: return invoke();
			case 1: return invoke((T1)input[0]);
			case 2: return invoke((T1)input[0],(T2)input[2]);
			default: 
				return invoke(input);
			}
		}
		default Supplier<R> getArg0() {
			throw new Ex.Arity(0, "No arity 0");
		}
		default Function<T1, R>   getArg1() {
			throw new Ex.Arity(1, "No arity 1");
		}
		default BiFunction<T1, T2, R> getArg2() {
			throw new Ex.Arity(2, "No arity 2");
		}
		
		default Function<Object[], R> getArgN(){
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

	public interface Hash {
		
		default long hashCalc() {
			return hashCalc(hashType());
		}
		
		long hashCalc(G.HashType t);
		
		default long hashGet(){
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
			if(h == 0) {
				h = hashCalc();
				hashPut(h);
			}
			return h;
		}
		
		@Override
		default long hashGet(G.HashType t) {
			return (hashType() == t)
					? hashGet()
					: hashCalc(t);
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

		V lookup(K key);

		default V lookup(K key, V notFound) {
			Map.Entry<K, V> ret = find(key);
			return (ret == null)
				? notFound
				: ret.getValue();
		}
		
		Iterator<V> vals();
	}

	public interface Metadata {
		
		G.MetaType getMetatype();
	}
	
	public interface Mutable {}
	
	public interface Namespaced {
		String getName();
		String getNamespace();
	}
	
	public interface Nth<V> {
		V nth(long i);
	}

	public interface ObjType extends Hash {
		
		G.ObjType getObjType();
		
		@Override
		default String hashSeed() {
			return "HARA::"+ getObjType().toString() + "";
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
		PushFirst<E> pushFirst(E v);
	}
	
	public interface PushLast<E> {
		PushLast<E> pushLast(E v);
	}

	public interface Ranged {
		long rangeMax();
		long rangeMin();
	}

	public interface Realize<V> {
		boolean isRealized();
		V realize();
	}

	public interface Seq<E> extends
		Cons<E>,
		Iterable<E>,
		Nth<E>, 
		Count,
		ToSeq<E> {
		
		@Override
		default long count() {
			return !restEnd() ? 0 : 1 + restMore().count();
		}
		E first();
		@Override
		default Iterator<E> iterator() {
			return Sq.toIterator(this);
		}
		
		default Seq<E> next() {
			return restMore().toSeq();
		}
		
		@Override
		default E nth(long i) {
			if (i == 0) return first();
			Seq<E> s = next();
			return (s != null) ? s.nth(i - 1) : null;
		}

		boolean restEnd();

		Seq<E> restMore();
	}
	
	public interface SeqArray<C, V> 
		extends SequentialLookupType<V>,
				I.PeekFirst<V>, 
				I.PeekLast<V>, 
				I.Seq<V> {
		
		@Override
		default long count() {
			return rawLength() - rawIndex() - 1;
		}
	
		@Override
		default V first() {
			return nth(0);
		}
	
		@SuppressWarnings("unchecked")
		@Override
		default Iterator<V> iterator() {
			return Arr.toIter(rawArray());
		}
	
		@SuppressWarnings("unchecked")
		@Override
		default SeqArray<C, V> next() {
			if (count() > 0) return (SeqArray<C, V>) restMore();
			return null;
		}
	
		@Override
		default V nth(long idx) {
			C arr = rawArray();
			return (arr == null) ? null : rawFn().apply(arr, rawIndex() + idx);
		}
	
		C rawArray();
	
		BiFunction<C, Long, V> rawFn();
	
		long rawIndex();
	
		long rawLength();
	
		@Override
		default boolean restEnd() {
			return count() < 1;
		}
	}

	public interface SequentialLookupType<V>
		extends SequentialType<V>, Iterable<V>, I.Count, I.Nth<V>, I.Lookup<Long, V>, I.PeekFirst<V>, I.PeekLast<V> {
	
		@Override
		default Entry<Long, V> find(Long idx) {
			if (idx >= 0 && idx < count()) {
				V out = nth(idx);
				return new Entry<Long, V>() {
					@Override
					public Long getKey() {
						return idx;
					}
	
					@Override
					public V getValue() {
						return out;
					}
	
					@Override
					public V setValue(V value) {
						throw new UnsupportedOperationException("Not Supported");
					}
				};
			}
			throw new IndexOutOfBoundsException();
		}
	
		@Override
		default Iterator<Long> keys() {
			return Iter.range(0, count());
		}
	
		@Override
		default V lookup(Long idx) {
			return nth(idx);
		}
	
		@Override
		default V peekFirst() {
			return nth(0);
		}
	
		@Override
		default V peekLast() {
			return nth(count() - 1);
		}
	
		@Override
		default Iterator<V> vals() {
			return this.iterator();
		}
	}

	public interface SequentialType<V> extends 
		Iterable<V>, 
		I.Count, 
		I.Equality, 
		I.Hash,
		I.ObjType {
	
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		default boolean equality(Object obj) {
			if (obj instanceof SequentialType) {
				return (count() == ((SequentialType) obj).count())
						&& Eq.eqIterator(
								(Iterator)this.iterator(), 
								((SequentialType) obj).iterator());
			} else if (obj instanceof java.util.List) {
				return (this.count() == ((java.util.List) obj).size())
						&& Eq.eqIterator(
								(Iterator)this.iterator(), 
								((java.util.List) obj).iterator());
			} else {
				return false;
			}
		}
	
		@Override
		default G.ObjType getObjType() {
			return G.ObjType.SEQUENTIAL;
		}
		
		@Override
		default long hashCalc(G.HashType t) {
			
			Function<Object, Long> f = G.hashFn(t);
			return Iter.reduce(
					iterator(), 
					Long.valueOf(hashSeed().hashCode()),
					(acc, item) -> (acc * 31) + f.apply(item));
		}
	}

	public interface ToMutable extends Persistent {
		Mutable toMutable();
	}
	
	public interface ToPersistent extends Mutable {
		Persistent toPersistent();
	}

	
	public interface ToSeq<E> extends Iterable<E> {
		default Seq<E> toSeq() {
			return Iter.toSeq(iterator());
		};
	}
	
	public interface Validate<V> {
		default Predicate<V> getValidator() {
			return null;
		}
		default boolean validate(V newVal) {
			var f = getValidator();
			if (f == null) return true;
			return f.test(newVal);
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
			if(ws != null) {
				ws.forEachRemaining(e -> e.getValue().accept(
						new WatchEntry<R, V>(e.getKey(), this, oldVal, newVal)));
			}
		}
		default void removeWatch(Object key) {
			throw new UnsupportedOperationException("Not Supported");
		}
	}

	@SuppressWarnings("unchecked")
	public class WatchEntry<R, V> extends Tup.Tup4.L<Object, R, V, V> {
		
		WatchEntry(Object key, Watch<R, V> ref, V oldVal, V newVal) {
			super(null, key, (R) ref, oldVal, newVal);
		}
	}
}

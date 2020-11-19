package hara.lang.base;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface I {

	public interface Assoc<K, V> {
		Assoc<K, V> assoc(K k, V v);
	}
	
	public interface Component{
		Component start();
		Component stop();
		boolean isStopped();
		boolean isStarted();
		Metadata  getStatus();
		Metadata  getProps();
	}

	public interface Conj<V> {
		Conj<V> conj(V e);
	}

	public interface Cons<V> {
		Cons<V> cons(V e);
	}

	public interface Count {
		long count();
	}

	public interface Deref {
		Object deref();
	}

	public interface DerefTimeout {
		Object derefTimeout(long ms, T timeoutVal);
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
	
	public interface Hash {
		
		default G.HashType hashType() {
			return G.DEFAULT_HASH;
		}
		
		String hashSeed();
		
		default long hashCalc() {
			return hashCalc(hashType());
		}
		
		long hashCalc(G.HashType t);
		
		default long hashGet(){
			return hashCalc(hashType());
		};
		
		default long hashGet(G.HashType t) {
			return hashCalc(t);
		}
	}

	public interface HashCached extends Hash {
		
		long hashCurrent();

		void hashPut(long hash);
		
		default long hashGet() {
			long h = hashCurrent();
			if(h == 0) {
				h = hashCalc();
				hashPut(h);
			}
			return h;
		}
		
		default long hashGet(G.HashType t) {
			return (hashType() == t)
					? hashGet()
					: hashCalc(t);
		}
	}

	public interface Indexed<K, V> {
		K indexOf(V val);
	}

	public interface IndexedKV<K, V> {
		long indexOfKey(K key);

		long indexOfVal(V val);
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
		
		I.Metadata meta();

		ObjType withMeta(I.Metadata meta);
		
		default String hashSeed() {
			return "HARA::"+ getObjType().toString() + "";
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
	
	public interface Reducible<R, V> {
		R reduce(IFn<R, ?, V, ?> f);

		R reduce(IFn<R, ?, V, ?> f, R initial);
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
			return Sq.toIterator((Seq<E>) this);
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
			return G.toIter(rawArray());
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

	public interface SequentialType<V> extends 
		Iterable<V>, 
		I.Count, 
		I.Equality, 
		I.Hash,
		I.ObjType {
	
		@Override
		default long hashCalc(G.HashType t) {
			
			Function<Object, Long> f = G.hashFn(t);
			return Iter.reduce(
					iterator(), 
					Long.valueOf(hashSeed().hashCode()),
					(acc, item) -> (acc * 31) + f.apply(item));
		}
	
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
		
		default G.ObjType getObjType() {
			return G.ObjType.SEQUENTIAL;
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
			return Iter.range(count(), (e) -> e);
		}
	
		@Override
		default V lookup(Long idx) {
			return nth(idx);
		}
	
		@Override
		default V peekFirst() {
			return nth((long) 0);
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

	public interface Validate {
		default CFn getValidator() {
			return null;
		}
		default boolean validate(Object newVal) {
			CFn f = getValidator();
			if (f == null) return true;
			return (boolean)f.invoke(newVal);
		}
	}

	public interface Watch {
		default void addWatch(Object key, CFn f) {
			throw new UnsupportedOperationException("Not Supported");
		}
		default Iterator<Map.Entry<Object, CFn>> getWatches() {
			return null;
		}
		default void notifyWatches(Object oldVal, Object newVal) {
			Iterator<Map.Entry<Object, CFn>> ws = getWatches();
			if(ws != null) {
				Iter.map(ws, e -> e.getValue().invoke(e.getKey(), this, oldVal, newVal));
			}
		}
		default void removeWatch(Object key) {
			throw new UnsupportedOperationException("Not Supported");
		}
	}
}

package hara.lang.base;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.Function;


public interface Coll {
	

	public interface StringType extends I.Hash {

		@Override
		default long hashCalc(G.HashType t) {
			switch(t) {
			case SYSTEM: return (hashSeed()+"|"+toString()).hashCode();
			case MURMUR3: return Hash.Murmur3.hashChars(hashSeed()+"|"+toString());
			case SIP:
				break;
			default:
				throw new UnsupportedOperationException("Not Supported");
			}
			return -1;
		}
	}
	

	public interface SeqType<E>
			extends I.Coll<E>, 
					I.Cons<E>, 
					I.PushFirst<E>, 
					I.PopFirst, 
					I.Conj<E>, 
					I.Seq<E>, 
					I.SequentialType<E> {
	
		@Override
		default SeqType<E> conj(E e) {
			return (SeqType<E>) cons(e);
		}
	
		@Override
		default SeqType<E> popFirst() {
			return (SeqType<E>) restMore();
		}
	
		@Override
		default SeqType<E> pushFirst(E v) {
			return (SeqType<E>) cons(v);
		}
	}

	public interface MapType<K, V>
			extends I.Coll<Entry<K, V>>,
					I.ObjType,
					I.Lookup<K, V>, 
					I.Assoc<K, V>,
					I.Dissoc<K>, 
					I.Find<K, Entry<K, V>> {
		default java.util.Map<K, V> asJavaMap() {
			return null;
		}
		
		@Override
		default MapType<K, V> conj(Entry<K, V> e) {
			return (MapType<K, V>) assoc(e.getKey(), e.getValue());
		}
	
		@Override
		default V lookup(K key) {
			Entry<K, V> e = find(key);
			return (e == null) ? null : e.getValue();
		}
	
		@Override
		default V lookup(K key, V notFound) {
			Entry<K, V> e = find(key);
			return (e == null) ? notFound : e.getValue();
		}

		@Override
		default G.ObjType getObjType() {
			return G.ObjType.MAP;
		}
		
		@Override
		default long hashCalc(G.HashType t) {
			Function<Object, Long> f = G.hashFn(t);
			
			return It.reduce(
					iterator(), 
					Long.valueOf(hashSeed().hashCode()),
					(acc, item) -> acc + f.apply(item));
		}
	}

	public interface NamespacedType {
	
		public abstract class MT extends RefType.MT implements I.Namespaced {
	
			private final String _name;
			private final String _ns;
			private transient String _str;
	
			public MT(I.Metadata meta, String nsname) {
				super(meta);
	
				int i = nsname.indexOf('/');
				if (i == -1 || nsname.equals("/")) {
					_ns = null;
					_name = nsname;
				} else {
					_ns = nsname.substring(0, i);
					_name = nsname.substring(i + 1);
				}
			}
	
			public MT(I.Metadata meta, String ns, String name) {
				super(meta);
				_ns = ns;
				_name = name;
			}
	
			@Override
			public String getName() {
				return _name;
			}
	
			@Override
			public String getNamespace() {
				return _ns;
			}
	
			public String pathString() {
				if (_str == null) {
					if (_ns != null)
						_str = (_ns + "/" + _name);
					else
						_str = _name;
				}
				return _str;
			}
		}
	
		public abstract class PT extends RefType.PT implements I.Namespaced {
	
			private final String _name;
			private final String _ns;
			private transient String _str;
	
			public PT(I.Metadata meta, String nsname) {
				super(meta);
	
				int i = nsname.indexOf('/');
				if (i == -1 || nsname.equals("/")) {
					_ns = null;
					_name = nsname;
				} else {
					_ns = nsname.substring(0, i);
					_name = nsname.substring(i + 1);
				}
			}
	
			public PT(I.Metadata meta, String ns, String name) {
				super(meta);
				_ns = ns;
				_name = name;
			}
	
			@Override
			public String getName() {
				return _name;
			}
	
			@Override
			public String getNamespace() {
				return _ns;
			}
	
			public String pathString() {
				if (_str == null) {
					if (_ns != null)
						_str = (_ns + "/" + _name);
					else
						_str = _name;
				}
				return _str;
			}
		}
	}

	public interface RefType {
		
		abstract class MT extends Obj.MT implements 
			I.HashCached,
			I.ObjType {
			private long _hash;
	
			public MT(I.Metadata meta) {
				super(meta);
			}
	
			@Override
			public long hashCurrent() {
				return _hash;
			}
	
			@Override
			public void hashPut(long hash) {
				_hash = hash;
			}
		}
		
		abstract class PT extends Obj.PT implements 
			I.HashCached,
			I.ObjType  {
			private long _hash;
	
			public PT(I.Metadata meta) {
				super(meta);
			}
	
			@Override
			public long hashCurrent() {
				return _hash;
			}
	
			@Override
			public void hashPut(long hash) {
				_hash = hash;
			}
		}
	}

	public interface VectorType<V>
			extends I.Coll<V>, I.SequentialType<V>, I.SequentialLookupType<V>, I.Nth<V>, I.PopLast, I.PushLast<V> {
	
		@Override
		default VectorType<V> conj(V v) {
			return (VectorType<V>) pushLast(v);
		}
	}

	public interface SetType<E> {}
	
	public interface DepsType<K, E> {
		E depGet(I.Context ctx, K id);
		SetType<E> depEntries(I.Context ctx, K id);
		Iterator<K> depIds(I.Context ctx);
	}
}

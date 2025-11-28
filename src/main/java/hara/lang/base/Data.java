package hara.lang.base;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;

import hara.lang.protocol.IPopFirst;
import hara.lang.protocol.IPushFirst;
import hara.lang.protocol.Constant;
import hara.lang.protocol.*;

public interface Data {
	

	public interface StringType extends IHash {

		@Override
		default long hashCalc(Constant.HashType t) {
			switch(t) {
			case SYSTEM: return (hashSeed()+"|"+toString()).hashCode();
			case MURMUR3: return Ut.Murmur3.hashChars(hashSeed()+"|"+toString());
			case SIP:
				break;
			default:
				throw new UnsupportedOperationException("Not Supported");
			}
			return -1;
		}
	}
	
	public interface OrderedType<E> extends Iterable<E>, IHash{

		@Override
		default long hashCalc(Constant.HashType t) {
			
			Function<Object, Long> f = G.hashFn(t);
			return It.reduce(
					iterator(), 
					Long.valueOf(hashSeed().hashCode()),
					(acc, item) -> (acc * 31) + f.apply(item));
		}
	}
	
	public interface UnOrderedType<E> extends Iterable<E>, IHash{

		@Override
		default long hashCalc(Constant.HashType t) {
			Function<Object, Long> f = G.hashFn(t);
			
			return It.reduce(
					iterator(), 
					Long.valueOf(hashSeed().hashCode()),
					(acc, item) -> acc + f.apply(item));
		}
	}
	
	
	public interface SetType<E>
			extends IColl<E>,
					IObjType,
					IDissoc<E>,
					IFind<E, E>,
					UnOrderedType<E>,
					IFn<E, E, E> {

		default java.util.Set<E> asJavaSet() {
			return null;
		}
		
		@Override
		default Constant.ObjType getObjType() {
			return Constant.ObjType.SET;
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		default boolean equality(Object obj) {			
			if (obj instanceof SetType) {
				return (count() == ((SetType) obj).count())
						&& It.every(
							this.iterator(),
							(e) -> ((SetType) obj).find(e) != null);
			} else if (obj instanceof java.util.Set) {
				return (this.count() == ((java.util.Set) obj).size())
						&& It.every(
							((java.util.Set) obj).iterator(),
							(e) -> this.find((E)e) != null);
			} else {
				return false;
			}
		}


		@Override
		default String startString() {
			return "#{";
		}

		@Override
		default String endString() {
			return "}";
		}

	
		@Override
		default public E invoke(E key) {
			return find(key);
		}
		
		@Override
		default public E invoke(E key, E notFound) {
			var ret = find(key);
			return (ret == null) ? notFound : ret;
		}
	}

	public interface MapType<K, V>
			extends IColl<Entry<K, V>>,
					IObjType,
					IMetadata,
					ILookup<K, V>,
					IAssoc<K, V>,
					IDissoc<K>,
					IFind<K, Entry<K, V>>,
					UnOrderedType<Entry<K, V>>,
					IFn <V, K, V> {
		default java.util.Map<K, V> asJavaMap() {
			return null;
		}
		
		@Override
		default Constant.MetaType getMetatype() {
			return Constant.MetaType.MAP;
		}	

		@Override
		default Constant.ObjType getObjType() {
			return Constant.ObjType.MAP;
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
		default Iterator<K> keys() {
			return It.map(iterator(), (n) -> n.getKey());
		}

		@Override
		default Iterator<V> vals() {
			return It.map(iterator(), (n) -> n.getValue());
		}

		@Override
		default String startString() {
			return "{";
		}

		@Override
		default String endString() {
			return "}";
		}

		@Override
		default String sepString() {
			return ", ";
		}
		
		@Override
		default String display() {
			return It.toString(
				iterator(), startString(), endString(), sepString(),
				(o) -> G.display(o.getKey()) 
						+ " "
						+ G.display(o.getValue()));
		}
		
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		default boolean equality(Object obj) {
						
			if (obj instanceof MapType) {
				return (count() == ((MapType) obj).count())
						&& It.every(
							this.iterator(),
							(e) -> {
								Map.Entry oe = (Entry) ((MapType) obj).find(e.getKey());
								return oe != null && Eq.eq(oe.getValue(), e.getValue()); 
							});
			} else if (obj instanceof java.util.Map) {
				return (this.count() == ((java.util.Map) obj).size())
						&& It.every(
							((java.util.Map) obj).entrySet().iterator(),
							(e) -> {
								Map.Entry oe = (Map.Entry)e;
								Map.Entry te = (Map.Entry)((MapType)this).find(oe.getKey());
								return te != null && Eq.eq(te.getValue(), oe.getValue()); 
							});
			} else {
				return false;
			}
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		default public Function getArg1() {
			return key -> lookup((K) key);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		default public BiFunction getArg2() {
			return (key, notFound) -> lookup((K)key, (V)notFound);
		}
	
	}

	public interface NamespacedType {
	
		public abstract class MT extends RefType.MT implements INamespaced {
	
			private final String _name;
			private final String _ns;
			private transient String _str;
	
			public MT(IMetadata meta, String nsname) {
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
	
			public MT(IMetadata meta, String ns, String name) {
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
	
		public abstract class PT extends RefType.PT implements INamespaced {
	
			private final String _name;
			private final String _ns;
			private transient String _str;
	
			public PT(IMetadata meta, String nsname) {
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
	
			public PT(IMetadata meta, String ns, String name) {
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
			IHashCached,
			IObjType {
			private long _hash;
	
			public MT(IMetadata meta) {
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
			IHashCached,
			IObjType  {
			private long _hash;
	
			public PT(IMetadata meta) {
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

	public interface VectorType<E>
			extends IColl<E>,
					SequentialType<E>, 
					SequentialLookupType<E>, 
					LinearType<E>,
					INth<E>,
					IPopLast,
					IPushLast<E>,
					IFn <E, Integer, E> {
	
		@Override
		default VectorType<E> conj(E v) {
			return (VectorType<E>) pushLast(v);
		}

		@Override
		default String startString() {
			return "[";
		}

		@Override
		default String endString() {
			return "]";
		}

		@Override
		default public E invoke(Integer key) {
			return nth(key);
		}
	}
	
	public interface DepsType<K, E> {
		E depGet(IContext ctx, K id);
		SetType<E> depEntries(IContext ctx, K id);
		Iterator<K> depIds(IContext ctx);
	}


	public interface SequentialLookupType<E>
		extends SequentialType<E>, Iterable<E>, ICount, INth<E>, ILookup<Long, E>, IPeekFirst<E>, IPeekLast<E> {
	
		@Override
		default Entry<Long, E> find(Long idx) {
			if (idx >= 0 && idx < count()) {
				E out = nth(idx);
				return new Entry<Long, E>() {
					@Override
					public Long getKey() {
						return idx;
					}
	
					@Override
					public E getValue() {
						return out;
					}
	
					@Override
					public E setValue(E value) {
						throw new UnsupportedOperationException("Not Supported");
					}
				};
			}
			throw new IndexOutOfBoundsException();
		}
	
		@Override
		default Iterator<Long> keys() {
			return It.range(0, count());
		}
	
		@Override
		default E lookup(Long idx) {
			return nth(idx);
		}
	
		@Override
		default E peekFirst() {
			return nth(0);
		}
	
		@Override
		default E peekLast() {
			return nth(count() - 1);
		}
	
		@Override
		default Iterator<E> vals() {
			return this.iterator();
		}
	}


	public interface SequentialType<E> extends 
		Iterable<E>, 
		ICount,
		IEquality,
		IHash,
		IObjType,
		OrderedType<E> {
	
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
		default Constant.ObjType getObjType() {
			return Constant.ObjType.SEQUENTIAL;
		}
	}
	
	public interface LinkedType<E>
		extends IColl<E>,
				IPushFirst<E>,
				IPopFirst,
				IPeekFirst<E>,
				ICons<E>,
				IConj<E>,
				ICount{
		
		@Override
		default LinkedType<E> cons(E e) {
			return (LinkedType<E>) pushFirst(e);
		}
	
		@Override
		default LinkedType<E> conj(E e) {
			return (LinkedType<E>) pushFirst(e);
		}
	
		@Override
		default String startString() {
			return "(";
		}

		@Override
		default String endString() {
			return ")";
		}
	}
	
	public interface LinearType<E>
		extends IColl<E>,
				IPushFirst<E>,
				IPushLast<E>,
				IPopFirst,
				IPopLast,
				IPeekFirst<E>,
				IPeekLast<E>,
				ICons<E>,
				IConj<E>,
				INth<E>,
				ICount{
		
		@Override
		default LinearType<E> cons(E e) {
			return (LinearType<E>) pushFirst(e);
		}
	
		@Override
		default LinearType<E> conj(E e) {
			return (LinearType<E>) pushLast(e);
		}

		@Override
		default IPushFirst<E> pushFirst(E e) {
			throw new Ex.Unsupported();
		}

		@Override
		default IPopFirst popFirst() {
			throw new Ex.Unsupported();
		}
		
		@Override
		default String startString() {
			return "[";
		}

		@Override
		default String endString() {
			return "]";
		}
	}

	public interface LinearView<E> extends LinearType<E> {	
		public LinearType<E> subview(int start, int end);
	}
	
	public interface VarType extends IDeref<Object> {
		Boolean isDynamic();
		Boolean isMacro();
		Boolean isControl();
	}
}

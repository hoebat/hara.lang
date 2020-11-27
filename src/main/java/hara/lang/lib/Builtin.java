package hara.lang.lib;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.Supplier;

import hara.lang.base.*;
import hara.lang.base.Std.T;
import hara.lang.data.*;
import static hara.lang.lib.Module.ReduceInit.*;
import static hara.lang.lib.Module.ReduceType.*;

@SuppressWarnings({ "unchecked", "rawtypes" })
@Module.Ns(name = "global")
public interface Builtin {

	//
	// Ops
	//

	@Module.Var(name = "+")
	@Module.Reduce(type = INIT, init = ZERO)
	public static Object add(Object x, Object y) {
		return Num.add(x, y);
	}

	@Module.Var(name = "-")
	@Module.Reduce(type = ARRAY, init = ZERO)
	public static Object minus(Object x, Object y) {
		return Num.minus(x, y);
	}

	@Module.Var(name = "*")
	@Module.Reduce(type = INIT, init = ONE)
	public static Object multiply(Object x, Object y) {
		return Num.multiply(x, y);
	}

	@Module.Var(name = "/")
	@Module.Reduce(type = ARRAY, init = ONE)
	public static Object divide(Object x, Object y) {
		return Num.divide(x, y);
	}

	//
	// Print
	//

	@Module.Var(name = "pr-str")
	public static String prStr(Object e) {
		return G.displayItem(e);
	}
	
	//
	// Data
	//

	@Module.Var(name = "pair")
	public static T.Tup2.L<Object, Object> pair(Object key, Object val) {
		return new T.Tup2.L(null, key, val);
	}

	
	@Module.Var(name = "tup")	
	public static T.Tup0 tup() {
		return T.Tup0.EMPTY;
	}

	@Module.Var(name = "tup")	
	public static  T.Tup1.L tup(Object a) {
		return new T.Tup1.L(null, a);
	}

	@Module.Var(name = "tup")	
	public static  T.Tup2.L tup(Object a, Object b) {
		return new T.Tup2.L(null, a, b);
	}

	@Module.Var(name = "tup")	
	public static  T.Tup3.L tup(Object a, Object b, Object c) {
		return new T.Tup3.L(null, a, b, c);
	}

	@Module.Var(name = "tup")	
	public static  T.Tup4.L tup(Object a, Object b, Object c, Object d) {
		return new T.Tup4.L(null, a, b, c, d);
	}

	@Module.Var(name = "tup")	
	public static  T.Tup5.L tup(Object a, Object b, Object c, Object d, Object e) {
		return new T.Tup5.L(null, a, b, c, d, e);
	}
	
	
	/*
	@Module.Fn(vargs = true)	
	public static Object tup(Object[] xs) {
		switch(xs.length) {
		case 0: return T.Tup0.EMPTY;
		case 1: return new T.Tup1.L(null, xs[0]);
		case 2: return new T.Tup2.L(null, xs[0], xs[1]);
		case 3: return new T.Tup3.L(null, xs[0], xs[1], xs[2]);
		case 4: return new T.Tup4.L(null, xs[0], xs[1], xs[2], xs[3]); 
		case 5: return new T.Tup5.L(null, xs[0], xs[1], xs[2], xs[3], xs[4]);
		default: return list(xs);
		}
	}
	*/

	@Module.Var(name = "atom")
	public static <V> Atom.Standard<V> atom(V val) {
		return new Atom.Standard<V>(val);
	}

	@Module.Var(name = "atom:basic")
	public static <V> Atom.Basic<V> atomBasic(V val) {
		return new Atom.Basic<V>(val);
	}

	@Module.Var(name = "list")
	@Module.Fn(vargs = true)
	public static List.Standard list(Object[] elements) {
		return List.Standard.from(null, elements);
	}

	@Module.Var(name = "list:mut")
	@Module.Fn(vargs = true)
	public static List.Mutable listMut(Object[] elements) {
		return List.Mutable.from(null, elements);
	}

	@Module.Var(name = "queue")
	@Module.Fn(vargs = true)
	public static Queue.Standard queue(Object[] elements) {
		return Queue.Standard.from(null, elements);
	}

	@Module.Var(name = "queue:mut")
	@Module.Fn(vargs = true)
	public static Queue.Mutable queueMut(Object[] elements) {
		return Queue.Mutable.from(null, elements);
	}

	@Module.Var(name = "vector")
	@Module.Fn(vargs = true)
	public static Vector.Standard vector(Object[] elements) {
		return Vector.Standard.from(null, elements);
	}

	@Module.Var(name = "vec")
	public static Vector.Standard vec(Object arr) {
		if (arr instanceof Iterator) {
			return Vector.Standard.from(null, (Iterator) arr);
		} else if (arr instanceof Iterable) {
			return Vector.Standard.from(null, ((Iterable) arr).iterator());
		} else if (arr.getClass().isArray()) {
			return Vector.Standard.from(null, (Object[]) arr);
		}
		throw new Ex.Unsupported();
	}

	@Module.Var(name = "vector:mut")
	@Module.Fn(vargs = true)
	public static Vector.Mutable vectorMut(Object[] elements) {
		return Vector.Mutable.from(null, elements);
	}

	@Module.Var(name = "hashset")
	@Module.Fn(vargs = true)
	public static Set.Standard hashSet(Object[] elements) {
		return Set.Standard.from(null, elements);
	}

	@Module.Var(name = "hashset:mut")
	@Module.Fn(vargs = true)
	public static Set.Mutable hashSetMut(Object[] elements) {
		return Set.Mutable.from(null, elements);
	}

	@Module.Var(name = "set")
	public static Set.Standard set(Object arr) {
		if (arr instanceof Iterator) {
			return Set.Standard.from(null, (Iterator) arr);
		} else if (arr instanceof Iterable) {
			return Set.Standard.from(null, ((Iterable) arr).iterator());
		} else if (arr.getClass().isArray()) {
			return Set.Standard.from(null, (Object[]) arr);
		}
		throw new Ex.Unsupported();
	}

	@Module.Var(name = "orderedset")
	@Module.Fn(vargs = true)
	public static OrderedSet.Standard orderedSet(Object[] elements) {
		return OrderedSet.Standard.from(null, elements);
	}

	@Module.Var(name = "orderedset:mut")
	@Module.Fn(vargs = true)
	public static OrderedSet.Mutable orderedSetMut(Object[] elements) {
		return OrderedSet.Mutable.from(null, elements);
	}

	@Module.Var(name = "sortedset")
	@Module.Fn(vargs = true)
	public static SortedSet.Standard sortedSet(Object[] elements) {
		return SortedSet.Standard.from(null, elements);
	}

	@Module.Var(name = "sortedset:mut")
	@Module.Fn(vargs = true)
	public static SortedSet.Mutable sortedSetMut(Object[] elements) {
		return SortedSet.Mutable.from(null, elements);
	}

	@Module.Var(name = "hashmap")
	@Module.Fn(vargs = true)
	public static <E, K, V> Map.Standard<K, V> hashMap(Object[] elements) {
		return Map.Standard.from(null, elements);
	}

	@Module.Var(name = "hashmap:mut")
	@Module.Fn(vargs = true)
	public static <E, K, V> Map.Mutable<K, V> hashMapMut(Object[] elements) {
		return Map.Mutable.from(null, elements);
	}

	@Module.Var(name = "orderedmap")
	@Module.Fn(vargs = true)
	public static <E, K, V> OrderedMap.Standard<K, V> orderedMap(Object[] elements) {
		return OrderedMap.Standard.from(null, elements);
	}

	@Module.Var(name = "orderedmap:mut")
	@Module.Fn(vargs = true)
	public static <E, K, V> OrderedMap.Mutable<K, V> orderedMapMut(Object[] elements) {
		return OrderedMap.Mutable.from(null, elements);
	}

	@Module.Var(name = "sortedmap")
	@Module.Fn(vargs = true)
	public static <E, K, V> SortedMap.Standard<K, V> sortedMap(Object[] elements) {
		return SortedMap.Standard.from(null, elements);
	}

	@Module.Var(name = "sortedmap:mut")
	@Module.Fn(vargs = true)
	public static <E, K, V> SortedMap.Mutable<K, V> sortedMapMut(Object[] elements) {
		return SortedMap.Mutable.from(null, elements);
	}

	@Module.Var(name = "symbol")
	public static Symbol symbol(String nsname) {
		return Symbol.create(nsname);
	}

	@Module.Var(name = "symbol")
	public static Symbol symbol(String ns, String name) {
		return Symbol.create(ns, name);
	}

	@Module.Var(name = "keyword")
	public static Keyword keyword(String nsname) {
		return Keyword.create(nsname);
	}

	@Module.Var(name = "keyword")
	public static Keyword keyword(String ns, String name) {
		return Keyword.create(ns, name);
	}
	
	@Module.Var(name = "meta")
	public static I.Metadata meta(Object obj) {
		if(obj instanceof I.ObjType) {
			return ((I.ObjType)obj).meta();
		} else {
			return null;
		}
	}
	
	@Module.Var(name = "with-meta")
	public static Object withMeta(Object obj, I.Metadata meta) {
		if(obj instanceof I.ObjType) {
			return ((I.ObjType)obj).withMeta(meta);
		} else {
			throw new Ex.Unsupported();
		}
	}

	//
	// Collection
	//
	
	public static java.util.Map merge(java.util.Map target, Iterator<Entry> it) {
		return It.reduce(it, target,
				(m, e) -> {
					m.put(e.getKey(), e.getValue());
					return m;
				});
	}

	public static Data.MapType merge(Data.MapType target, Iterator<Entry> it) {
		return It.reduce(it, target,
				(m, e) -> {
					return (Data.MapType) m.assoc(e.getKey(), e.getValue());
				});
	}
	
	@Module.Var(name = "merge")
	@Module.Reduce(type=ARRAY, init=EMPTY_MAP)
	public static Object merge(Object target, Object other) {
		if(target instanceof java.util.Map) {
			return merge((java.util.Map)target, It.iter(other));
		} else if (target instanceof Data.MapType) {
			return merge((Data.MapType)target, It.iter(other));
		} else {
			throw new Ex.Unsupported();
		}
	}

	@Module.Var(name = "bench:fn")
	public static long bench(Supplier f) {
		long start = Ut.Clock.currentTimeNanos();
		f.get();
		long end = Ut.Clock.currentTimeNanos();
		return end - start;
	}

	@Module.Var(name = "comp")
	@Module.Fn(vargs = true)
	public static <F> I.OFn comp(F... fns) {
		return new Fn.H.Comp(fns);
	}

	//
	//
	//

	@Module.Var(name = "now")
	public static long now() {
		return Ut.Clock.currentTimeNanos();
	}

	@Module.Var(name = "type")
	public static Class<? extends Object> type(Object x) {
		return (x != null) ? x.getClass() : null;
	}

	@Module.Var(name = "volatile")
	public static <V> Ut.Volatile<V> vol(V val) {
		return new Ut.Volatile<V>(val);
	}

	@Module.Var(name = "counter")
	public static Ut.Counter counter() {
		return new Ut.Counter(-1);
	}

	@Module.Var(name = "counter")
	public static Ut.Counter counter(Integer start) {
		return new Ut.Counter(start);
	}

	//
	//
	//

	@Module.Var(name = "flag")
	public static Ut.Flag flag() {
		return new Ut.Flag(false);
	}

	@Module.Var(name = "flag")
	public static Ut.Flag flag(Boolean val) {
		return new Ut.Flag(val);
	}

	@Module.Var(name = "identical")
	public static boolean identical(Object k1, Object k2) {
		return k1 == k2;
	}

	//
	// Checks
	//

	@Module.Var(name = "integer?")
	public static boolean isInteger(Object x) {
		return (x instanceof Integer) || (x instanceof Long) || (x instanceof BigInteger);
	}

	@Module.Var(name = "class:primitive?")
	public static boolean isPrimitive(Class<?> c) {
		return (c != null) && c.isPrimitive() && !(c == Void.TYPE);
	}

	@Module.Var(name = "compare")
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

	@Module.Var(name = "equals")
	public static boolean equals(Object k1, Object k2) {
		return (k1 == k2) ? true : (k1 != null && k1.equals(k2));
	}
}

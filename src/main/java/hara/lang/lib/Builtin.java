package hara.lang.lib;

import static hara.lang.base.Module.ReduceInit.*;
import static hara.lang.base.Module.ReduceType.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

import hara.lang.base.*;
import hara.lang.base.Module;
import hara.lang.base.Std.T;
import hara.lang.data.*;

@SuppressWarnings({ "unchecked", "rawtypes" })

@Module.Ns(name = "builtin")
public interface Builtin {

	@Module.Ns(name = "builtin", tag = "basic")
	public interface Basic {

		@Module.Var(name = "atom")
		public static <V> Atom.Standard<V> atom(V val) {
			return new Atom.Standard<V>(val);
		}

		@Module.Var(name = "atom:basic")
		public static <V> Atom.Basic<V> atomBasic(V val) {
			return new Atom.Basic<V>(val);
		}

		@Module.Var(name = "volatile")
		public static <V> Ut.Volatile<V> atomVolatile(V val) {
			return new Ut.Volatile<V>(val);
		}

		@Module.Var(name = "call")
		@Module.Fn(vargs = true)
		public static Object call(Object vargs) {
			Object[] args = Arr.toArray(vargs);
			I.Fn f = Fn.toFn(args[1]);
			Object[] arr = Arr.toArray(It.concat(It.objects(args[0]), Arr.toIter(args, 2, args.length)));
			return f.apply(arr);
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

		@Module.Var(name = "counter")
		public static Ut.Counter counter() {
			return new Ut.Counter(-1);
		}

		@Module.Var(name = "counter")
		public static Ut.Counter counter(Integer start) {
			return new Ut.Counter(start);
		}

		@Module.Var(name = "deref")
		public static <V, R> V deref(R ref) {
			if (ref instanceof I.Deref) {
				return (V) ((I.Deref) ref).deref();
			} else if (ref instanceof java.util.concurrent.Future) {
				try {
					return (V) ((java.util.concurrent.Future) ref).get();
				} catch (InterruptedException | ExecutionException t) {
					throw Ex.Sneaky(t);
				}
			} else {
				throw new Ex.Unsupported();
			}
		}

		@Module.Var(name = "equals")
		public static boolean equals(Object k1, Object k2) {
			return (k1 == k2) ? true : (k1 != null && k1.equals(k2));
		}

		@Module.Var(name = "=")
		public static boolean equivalent(Object k1, Object k2) {
			return Eq.eq(k1, k2);
		}

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

		@Module.Var(name = "keyword")
		public static Keyword keyword(String nsname) {
			return Keyword.create(nsname);
		}

		@Module.Var(name = "keyword")
		public static Keyword keyword(String ns, String name) {
			return Keyword.create(ns, name);
		}

		//
		//
		//

		@Module.Var(name = "meta")
		public static I.Metadata meta(Object obj) {
			if (obj instanceof I.ObjType) {
				return ((I.ObjType) obj).meta();
			} else {
				return null;
			}
		}

		//
		// Checks
		//

		@Module.Var(name = "symbol")
		public static Symbol symbol(String nsname) {
			return Symbol.create(nsname);
		}

		@Module.Var(name = "symbol")
		public static Symbol symbol(String ns, String name) {
			return Symbol.create(ns, name);
		}

		@Module.Var(name = "type")
		public static Class<? extends Object> type(Object x) {
			return (x != null) ? x.getClass() : null;
		}

		@Module.Var(name = "with-meta")
		public static Object withMeta(Object obj, I.Metadata meta) {
			if (obj instanceof I.ObjType) {
				return ((I.ObjType) obj).withMeta(meta);
			} else {
				throw new Ex.Unsupported();
			}
		}
	}

	@Module.Ns(name = "builtin", tag = "collection")
	public interface Collection {

		@Module.Var(name = "conj")
		@Module.Reduce(type = SELF, init = EMPTY_VECTOR)
		public static <C, E> C conj(C target, E e) {
			if (target instanceof I.Coll) {
				return (C) ((I.Coll) target).conj(e);
			} else if (target instanceof java.util.List) {
				((java.util.List) target).add(e);
				return target;
			} else if (target instanceof java.util.Map) {
				var entry = (Entry) e;
				((java.util.Map) target).put(entry.getKey(), entry.getValue());
				return target;
			} else if (target instanceof java.util.Set) {
				((java.util.Set) target).add(e);
				return target;
			} else {
				throw new Ex.Unsupported();
			}
		}

		@Module.Var(name = "cons")
		@Module.Reduce(type = SELF, init = EMPTY_LIST)
		public static <C, E> C cons(C target, E e) {
			if (target instanceof I.Cons) {
				return (C) ((I.Cons) target).cons(e);
			} else if (target instanceof java.util.List) {
				((java.util.List) target).add(0, e);
				return target;
			} else {
				throw new Ex.Unsupported();
			}
		}

		@Module.Var(name = "find")
		public static <C, K, V> V find(C lu, K key) {
			if (lu instanceof I.Find) {
				return (V) ((I.Find) lu).find(key);
			} else {
				throw new Ex.Unsupported();
			}
		}

		@Module.Var(name = "has?")
		public static <C, K> Boolean has(C lu, K key) {
			if (lu instanceof I.Find) {
				return ((I.Find) lu).has(key);
			} else {
				throw new Ex.Unsupported();
			}
		}

		@Module.Var(name = "into")
		public static <C, E> C into(C coll, Object other) {
			return (C) It.reduceIn(It.iter(other), coll, Collection::conj);
		}

		@Module.Var(name = "iter")
		public static Iterator iter(Object obj) {
			return It.iter(obj);
		}

		@Module.Var(name = "keys")
		public static <C, K> Iterator<K> keys(C m) {
			if (m instanceof I.Lookup) {
				return ((I.Lookup) m).keys();
			} else if (m instanceof java.util.Map) {
				return ((java.util.Map) m).keySet().iterator();
			} else {
				throw new Ex.Unsupported();
			}
		}

		@Module.Var(name = "get")
		public static <C, K, V> V lookup(C lu, K key) {
			if (lu instanceof I.Lookup) {
				return (V) ((I.Lookup) lu).lookup(key);
			} else {
				throw new Ex.Unsupported();
			}
		}

		@Module.Var(name = "get")
		public static <C, K, V> V lookup(C lu, K key, V notFound) {
			if (lu instanceof I.Lookup) {
				return (V) ((I.Lookup) lu).lookup(key, notFound);
			} else {
				throw new Ex.Unsupported();
			}
		}

		@Module.Var(name = "get-in")
		public static <C, KS, V> V lookupIn(C lu, KS ks) {
			return (V) lookupIn(lu, ks, (V) null);
		}

		@Module.Var(name = "get-in")
		public static <C, KS, V> V lookupIn(C lu, KS ks, V notFound) {
			var it = It.iter(ks);
			var entry = (Entry) find(lu, it.next());
			var out = new Ut.Volatile(entry);
			Entry ret = It.reduce(it, entry, (e, k) -> {
				Entry v = (Entry) find(e.getValue(), k);
				out.reset(v);
				return v;
			}, () -> out.deref() == null);
			return (out.deref() != null) ? (V) ret.getValue() : notFound;
		}

		@Module.Var(name = "merge")
		@Module.Reduce(type = ARRAY, init = EMPTY_MAP)
		public static <C> C merge(C target, C other) {
			if (target == null) {
				return other;
			} else if (target instanceof java.util.Map) {
				return (C) merge((java.util.Map) target, It.iter(other));
			} else if (target instanceof Data.MapType) {
				return (C) merge((Data.MapType) target, It.iter(other));
			} else {
				throw new Ex.Unsupported();
			}
		}

		public static Data.MapType merge(Data.MapType target, Iterator<Entry> it) {
			return It.reduce(it, target, (m, e) -> {
				return (Data.MapType) m.assoc(e.getKey(), e.getValue());
			});
		}

		public static java.util.Map merge(java.util.Map target, Iterator<Entry> it) {
			return It.reduce(it, target, (m, e) -> {
				m.put(e.getKey(), e.getValue());
				return m;
			});
		}

		@Module.Var(name = "vals")
		public static <C, V> Iterator<V> vals(C m) {
			if (m instanceof I.Lookup) {
				return ((I.Lookup) m).vals();
			} else if (m instanceof java.util.Map) {
				return ((java.util.Map) m).values().iterator();
			} else {
				throw new Ex.Unsupported();
			}
		}

	}

	@Module.Ns(name = "builtin", tag = "java")
	public interface Java {

		@Module.Var(name = "integer?")
		public static boolean isInteger(Object x) {
			return (x instanceof Integer) || (x instanceof Long) || (x instanceof BigInteger);
		}

		@Module.Var(name = "class:primitive?")
		public static boolean isPrimitive(Class<?> c) {
			return (c != null) && c.isPrimitive() && !(c == Void.TYPE);
		}

		//
		// Checks
		//

		@Module.Var(name = "j:arr")
		@Module.Fn(vargs = true)
		public static Object[] jArr(Object vargs) {
			return Arr.toArray(vargs);
		}

		@Module.Var(name = "j:hash-map")
		@Module.Fn(vargs = true)
		public static java.util.HashMap jHashMap(Object vargs) {
			var m = new java.util.HashMap();
			It.each(It.partitionPair(It.iter(vargs)), (p) -> m.put(((Entry) p).getKey(), ((Entry) p).getValue()));
			return m;
		}

		@Module.Var(name = "j:list")
		@Module.Fn(vargs = true)
		public static java.util.ArrayList jlist(Object vargs) {
			var l = new java.util.ArrayList();
			It.each(It.iter(vargs), (e) -> l.add(e));
			return l;
		}

	}

	@Module.Ns(name = "builtin", tag = "lambda")
	public interface Lambda {

		//
		// Merge
		//

		//
		// Array
		//

		@Module.Var(name = "apply")
		@Module.Fn(vargs = true)
		public static Object apply(Object vargs) {
			Object[] args = Arr.toArray(vargs);
			I.Fn f = Fn.toFn(args[0]);
			var lit = It.iter(args[args.length - 1]);
			var it = It.concat(Arr.toIter(args, 1, args.length - 1), lit);
			return f.apply(it);
		}

		@Module.Var(name = "comp")
		@Module.Fn(vargs = true)
		public static <F> I.OFn comp(Object fns) {
			return new Fn.H.Comp(Arr.toArray(fns));
		}

		@Module.Var(name = "F")
		@Module.Fn(vargs = true)
		public static Boolean F(Object vargs) {
			return false;
		}

		/*
		 * @Module.Var(name = "stream") public static Iterator stream(Object source,
		 * Object f) { var it = It.iter(source); return (Iterator)
		 * Fn.toFn(f).invoke(it); }
		 * 
		 * @Module.Var(name = "stream")
		 */

		@Module.Var(name = "identity")
		public static Object identity(Object x) {
			return x;
		}

		@Module.Var(name = "keep")
		public static I.Fn<Iterator, Iterator, Object> keep(Object f) {
			return Fn.toFn((Function) (source) -> keep(f, source));
		}

		@Module.Var(name = "keep")
		public static Iterator keep(Object f, Object source) {
			var it = It.iter(source);
			I.Fn fn = Fn.toFn(f);
			return It.keep(it, (e) -> fn.invoke(e));
		}

		@Module.Var(name = "map")
		public static I.Fn<Iterator, Iterator, Object> map(I.Fn f) {
			return Fn.toFn((Function) (source) -> map(f, source));
		}

		@Module.Var(name = "map")
		public static Iterator map(I.Fn f, Object source) {
			var it = It.iter(source);
			return It.from(it::hasNext, () -> f.invoke(it.next()));
		}

		@Module.Var(name = "mapcat")
		public static I.Fn<Iterator, Iterator, Object> mapcat(I.Fn f) {
			return Fn.toFn((Function) (source) -> mapcat(f, source));
		}

		@Module.Var(name = "mapcat")
		public static Iterator mapcat(I.Fn f, Object source) {
			var it = It.iter(source);
			// I.Fn fn = Fn.toFn(f);
			return It.mapcat(it, (e) -> (Iterator) f.invoke(e));
		}

		@Module.Var(name = "NIL")
		@Module.Fn(vargs = true)
		public static Object NIL(Object x) {
			return null;
		}

		@Module.Var(name = "partial")
		@Module.Fn(vargs = true)
		public static <F> I.OFn partial(Object args) {
			return new Fn.H.Partial(Arr.toArray(args));
		}

		@Module.Var(name = "pipe")
		@Module.Fn(vargs = true)
		public static Function<Iterator, Iterator> pipe(Object vargs) {
			var pl = It.iter(vargs);
			var fns = It.map(pl, Fn::toFn);
			return (it) -> It.reduce(fns, it, (i, f) -> (Iterator) ((I.Fn) f).invoke(i));
		}

		@Module.Var(name = "range")
		public static Iterator<Long> range(Long max) {
			return It.range(max);
		}

		@Module.Var(name = "range")
		public static Iterator<Long> range(Long min, Long max) {
			return It.range(min, max);
		}

		@Module.Var(name = "reduce")
		public static Object reduce(I.Fn f, Object source) {
			var it = It.iter(source);
			return It.reduce(it, (acc, e) -> f.invoke(acc, e));
		}

		@Module.Var(name = "reduce")
		public static Object reduce(I.Fn f, Object init, Object source) {
			var it = It.iter(source);
			return It.reduce(it, init, (acc, e) -> f.invoke(acc, e));
		}

		//
		// Merge
		//

		//
		// Array
		//

		//
		//
		//

		//
		// Checks
		//

		@Module.Var(name = "T")
		@Module.Fn(vargs = true)
		public static Boolean T(Object vargs) {
			return true;
		}

		@Module.Var(name = "zip")
		@Module.Fn(vargs = true)
		public static Iterator zip(Object vargs) {
			return It.zip(It.iter(vargs));
		}

	}

	@Module.Ns(name = "builtin", tag = "ops")
	public interface Ops {

		@Module.Var(name = "+")
		@Module.Reduce(type = INIT, init = ZERO)
		public static Object add(Object x, Object y) {
			return Num.add(x, y);
		}

		@Module.Var(name = "dec")
		public static Object dec(Object x) {
			return Num.minus(x, 1);
		}

		@Module.Var(name = "/")
		@Module.Reduce(type = ARRAY, init = ONE)
		public static Object divide(Object x, Object y) {
			return Num.divide(x, y);
		}

		@Module.Var(name = "inc")
		public static Object inc(Object x) {
			return Num.add(x, 1);
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

	}

	@Module.Ns(name = "builtin", tag = "structure")
	public interface Structure {

		//
		// Coll
		//

		@Module.Var(name = "hash-map")
		@Module.Fn(vargs = true)
		public static <E, K, V> Map.Standard<K, V> hashMap(Object elements) {
			return Map.Standard.into(It.partitionPair(It.iter(elements)));
		}

		@Module.Var(name = "hash-set")
		@Module.Fn(vargs = true)
		public static Set.Standard hashSet(Object elements) {
			return Set.Standard.into(It.iter(elements));
		}

		@Module.Var(name = "list")
		@Module.Fn(vargs = true)
		public static List.Standard list(Object elements) {
			return List.Standard.into(It.iter(elements));
		}

		@Module.Var(name = "mut:hash-map")
		@Module.Fn(vargs = true)
		public static <E, K, V> Map.Mutable<K, V> mutHashMap(Object elements) {
			return Map.Mutable.into(It.partitionPair(It.iter(elements)));
		}

		@Module.Var(name = "mut:hash-set")
		@Module.Fn(vargs = true)
		public static Set.Mutable mutHashSet(Object elements) {
			return Set.Mutable.into(It.iter(elements));
		}

		@Module.Var(name = "mut:list")
		@Module.Fn(vargs = true)
		public static List.Mutable mutList(Object elements) {
			return List.Mutable.into(It.iter(elements));
		}

		@Module.Var(name = "mut:ordered-map")
		@Module.Fn(vargs = true)
		public static <E, K, V> OrderedMap.Mutable<K, V> mutOrderedMap(Object elements) {
			return OrderedMap.Mutable.into(It.partitionPair(It.iter(elements)));
		}

		@Module.Var(name = "mut:ordered-set")
		@Module.Fn(vargs = true)
		public static OrderedSet.Mutable mutOrderedSet(Object elements) {
			return OrderedSet.Mutable.into(It.iter(elements));
		}

		@Module.Var(name = "mut:queue")
		@Module.Fn(vargs = true)
		public static Queue.Mutable mutQueue(Object elements) {
			return Queue.Mutable.into(It.iter(elements));
		}

		@Module.Var(name = "mut:sorted-map")
		@Module.Fn(vargs = true)
		public static <E, K, V> SortedMap.Mutable<K, V> mutSortedMap(Object elements) {
			return SortedMap.Mutable.into(It.partitionPair(It.iter(elements)));
		}

		@Module.Var(name = "mut:sorted-set")
		@Module.Fn(vargs = true)
		public static SortedSet.Mutable mutSortedSet(Object elements) {
			return SortedSet.Mutable.into(It.iter(elements));
		}

		@Module.Var(name = "mut:vector")
		@Module.Fn(vargs = true)
		public static Vector.Mutable mutVector(Object elements) {
			return Vector.Mutable.into(It.iter(elements));
		}

		@Module.Var(name = "ordered-map")
		@Module.Fn(vargs = true)
		public static <E, K, V> OrderedMap.Standard<K, V> orderedMap(Object elements) {
			return OrderedMap.Standard.into(It.partitionPair(It.iter(elements)));
		}

		@Module.Var(name = "ordered-set")
		@Module.Fn(vargs = true)
		public static OrderedSet.Standard orderedSet(Object elements) {
			return OrderedSet.Standard.into(It.iter(elements));
		}

		@Module.Var(name = "pair")
		public static T.Tup2.L<Object, Object> pair(Object key, Object val) {
			return new T.Tup2.L(null, key, val);
		}

		@Module.Var(name = "queue")
		@Module.Fn(vargs = true)
		public static Queue.Standard queue(Object elements) {
			return Queue.Standard.into(It.iter(elements));
		}

		@Module.Var(name = "set")
		public static Set.Standard set(Object arr) {
			return Set.Standard.into(It.iter(arr));
		}

		@Module.Var(name = "sorted-map")
		@Module.Fn(vargs = true)
		public static <E, K, V> SortedMap.Standard<K, V> sortedMap(Object elements) {
			return SortedMap.Standard.into(It.partitionPair(It.iter(elements)));
		}

		@Module.Var(name = "sorted-set")
		@Module.Fn(vargs = true)
		public static SortedSet.Standard sortedSet(Object elements) {
			return SortedSet.Standard.into(It.iter(elements));
		}

		@Module.Var(name = "tup")
		public static T.Tup0 tup() {
			return T.Tup0.EMPTY;
		}

		@Module.Var(name = "tup")
		public static T.Tup1.L tup(Object a) {
			return new T.Tup1.L(null, a);
		}

		@Module.Var(name = "tup")
		public static T.Tup2.L tup(Object a, Object b) {
			return new T.Tup2.L(null, a, b);
		}

		@Module.Var(name = "tup")
		public static T.Tup3.L tup(Object a, Object b, Object c) {
			return new T.Tup3.L(null, a, b, c);
		}

		@Module.Var(name = "tup")
		public static T.Tup4.L tup(Object a, Object b, Object c, Object d) {
			return new T.Tup4.L(null, a, b, c, d);
		}

		@Module.Var(name = "tup")
		public static T.Tup5.L tup(Object a, Object b, Object c, Object d, Object e) {
			return new T.Tup5.L(null, a, b, c, d, e);
		}

		public static Data.LinearType tup(Object[] xs) {
			switch (xs.length) {
			case 0:
				return T.Tup0.EMPTY;
			case 1:
				return new T.Tup1.L(null, xs[0]);
			case 2:
				return new T.Tup2.L(null, xs[0], xs[1]);
			case 3:
				return new T.Tup3.L(null, xs[0], xs[1], xs[2]);
			case 4:
				return new T.Tup4.L(null, xs[0], xs[1], xs[2], xs[3]);
			case 5:
				return new T.Tup5.L(null, xs[0], xs[1], xs[2], xs[3], xs[4]);
			default:
				throw new Ex.Arity(xs.length, "");
			}
		}

		@Module.Var(name = "vec")
		public static Data.LinearType vec(Object elements) {
			return vector(elements);
		}

		@Module.Var(name = "vector")
		@Module.Fn(vargs = true)
		public static Data.LinearType vector(Object elements) {
			if (elements instanceof Iterator) {
				return Vector.Standard.into((Iterator) elements);
			} else if (elements instanceof java.util.Collection) {
				java.util.Collection l = (java.util.List) elements;
				if (l.size() > 5) {
					return Vector.Standard.into(It.iter(l));
				} else {
					return tup(l.toArray());
				}
			} else if (elements instanceof I.Coll) {
				I.Coll l = (I.Coll) elements;
				if (l.count() > 5) {
					return Vector.Standard.into(It.iter(l));
				} else {
					return tup(It.toArray(l.iterator()));
				}
			} else if (elements.getClass().isArray()) {
				Object[] l = (Object[]) elements;
				if (l.length > 5) {
					return Vector.Standard.into(It.iter(l));
				} else {
					return tup(l);
				}
			} else {
				throw new Ex.Unsupported();
			}
		}

	}

	@Module.Ns(name = "builtin", tag = "time")
	public interface Time {

		@Module.Var(name = "bench:fn")
		public static long bench(Supplier f) {
			long start = Ut.Clock.currentTimeNanos();
			f.get();
			long end = Ut.Clock.currentTimeNanos();
			return end - start;
		}

		//
		//
		//

		@Module.Var(name = "now")
		public static long now() {
			return Ut.Clock.currentTimeNanos();
		}

	}

	@Module.Ns(name = "builtin", tag = "lambda")
	public interface Util {

		//
		// Print
		//

		@Module.Var(name = "pr-str")
		public static String prStr(Object e) {
			return G.display(e);
		}

	}

	//
	// Merge
	//

	//
	// Array
	//

	//
	//
	//

	//
	// Checks
	//

	/*
	 * @Module.Var(name = "stream") public static Iterator stream(Object source,
	 * Object f) { var it = It.iter(source); return (Iterator)
	 * Fn.toFn(f).invoke(it); }
	 * 
	 * @Module.Var(name = "stream")
	 */

}

package hara.kernel.base;

import hara.data.types.ILinearType;
import hara.data.types.IMapType;
import hara.kernel.protocol.IRuntime;
import hara.lang.base.*;
import hara.lang.data.*;
import hara.lang.data.Map.Standard;
import hara.lang.data.Tuple.*;
import hara.lang.protocol.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.net.URL;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static hara.kernel.base.Module.ReduceInit.*;
import static hara.kernel.base.Module.ReduceType.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public interface Builtin {

  @Module.Ns(name = "global", tag = "basic")
  public interface Basic {

    @Module.Fn(name = "atom", complete = true)
    public static <V> Atom.Standard<V> atom(V val) {
      return new Atom.Standard<V>(val);
    }

    @Module.Fn(name = "atom:basic", complete = true)
    public static <V> Atom.Basic<V> atomBasic(V val) {
      return new Atom.Basic<V>(val);
    }

    @Module.Fn(name = "volatile", complete = true)
    public static <V> Ut.Volatile<V> atomVolatile(V val) {
      return new Ut.Volatile<V>(val);
    }

    @Module.Fn(name = "compare", complete = true)
    public static int compare(Object k1, Object k2) {
      if (k1 == k2) return 0;
      if (k1 != null) {
        if (k2 == null) return 1;
        if (k1 instanceof Number) return Num.compare((Number) k1, (Number) k2);
        return ((Comparable<Object>) k1).compareTo(k2);
      }
      return -1;
    }

    @Module.Fn(name = "counter", complete = true)
    public static Ut.Counter counter() {
      return new Ut.Counter(-1);
    }

    @Module.Fn(name = "counter", complete = true)
    public static Ut.Counter counter(Integer start) {
      return new Ut.Counter(start);
    }

    @Module.Fn(name = "deref", protocol = true)
    public static <V> V deref(IDeref<V> ref) {
      return ref.deref();
    }

    @Module.Fn(name = "deref", protocol = true, method = "derefTimeout")
    public static <V> V deref(IDerefTimeout<V> ref, long ms, V timeoutVal) {
      return ref.derefTimeout(ms, timeoutVal);
    }

    @Module.Fn(name = "deref", option = true)
    public static <V> V deref(java.util.concurrent.Future<V> ref) {
      try {
        return ref.get();
      } catch (InterruptedException | ExecutionException t) {
        throw Ex.Sneaky(t);
      }
    }

    @Module.Fn(name = "deref", option = true)
    public static <V> V deref(java.util.concurrent.Future<V> ref, long ms, V timeoutVal) {
      try {
        return ref.get(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
      } catch (InterruptedException | ExecutionException t) {
        throw Ex.Sneaky(t);
      } catch (TimeoutException e) {
        return timeoutVal;
      }
    }

    @Module.Fn(name = "flag", complete = true)
    public static Ut.Flag flag() {
      return new Ut.Flag(false);
    }

    @Module.Fn(name = "flag", complete = true)
    public static Ut.Flag flag(Boolean val) {
      return new Ut.Flag(val);
    }

    @Module.Fn(name = "hash", protocol = true)
    public static long hash(IHash obj) {
      return obj.hashGet();
    }

    @Module.Fn(name = "hash", fallback = true)
    public static long hash(Object obj) {
      return obj.hashCode();
    }

    @Module.Fn(name = "keyword", complete = true)
    public static Keyword keyword(String nsname) {
      return Keyword.create(nsname);
    }

    @Module.Fn(name = "keyword", complete = true)
    public static Keyword keyword(String ns, String name) {
      return Keyword.create(ns, name);
    }

    //
    //
    //

    @Module.Fn(name = "meta", protocol = true)
    public static IMetadata meta(IObjType obj) {
      return obj.meta();
    }

    @Module.Fn(name = "meta", fallback = true)
    public static IMetadata meta(Object obj) {
      return null;
    }

    @Module.Fn(name = "realize", protocol = true)
    public static <V> V realize(IRealize<V> obj) {
      return obj.realize();
    }

    @Module.Fn(name = "realized?", protocol = true)
    public static <V> boolean realized(IRealize<V> obj) {
      return obj.isRealized();
    }

    @Module.Fn(name = "reset!", protocol = true)
    public static <V> V reset(IReset<V> obj, V val) {
      return obj.reset(val);
    }

    //
    // Checks
    //

    @Module.Fn(name = "symbol", complete = true)
    public static Symbol symbol(String nsname) {
      return Symbol.create(nsname);
    }

    @Module.Fn(name = "symbol", complete = true)
    public static Symbol symbol(String ns, String name) {
      return Symbol.create(ns, name);
    }

    @Module.Fn(name = "type", complete = true)
    public static Class<? extends Object> type(Object x) {
      return (x != null) ? x.getClass() : null;
    }

    @Module.Fn(name = "with-meta", protocol = true)
    public static IObjType withMeta(IObjType obj, IMetadata meta) {
      return obj.withMeta(meta);
    }
  }

  @Module.Ns(name = "global", tag = "check")
  public interface Check {

    @Module.Fn(name = "class?", complete = true)
    public static <TYPE> boolean isClass(TYPE x) {
      return (x instanceof Class);
    }

    @Module.Fn(name = "falsey?", complete = true)
    public static <TYPE> boolean isFalsey(TYPE x) {
      if (x == null) {
        return true;
      } else if (x instanceof Boolean) {
        return !((Boolean) x).booleanValue();
      } else {
        return false;
      }
    }

    @Module.Fn(name = "integer?", complete = true)
    public static <TYPE> boolean isInteger(TYPE x) {
      return (x instanceof Integer) || (x instanceof Long) || (x instanceof BigInteger);
    }

    @Module.Fn(name = "truthy?", complete = true)
    public static <TYPE> boolean isTruthy(TYPE x) {
      if (x == null) {
        return false;
      } else if (x instanceof Boolean) {
        return ((Boolean) x).booleanValue();
      } else {
        return true;
      }
    }

    //
    // Checks
    //

  }

  @Module.Ns(name = "global", tag = "collection")
  public interface Collection {

    @Module.Fn(name = "assoc", protocol = true)
    public static <K, V> IAssoc assoc(IAssoc coll, K key, V val) {
      return coll.assoc(key, val);
    }

    @Module.Fn(name = "assoc", option = true)
    public static <K, V> java.util.Map assoc(java.util.Map coll, K key, V val) {
      coll.put(key, val);
      return coll;
    }

    @Module.Fn(name = "conj", protocol = true)
    @Module.Reduce(type = ARRAY, init = EMPTY_VECTOR)
    public static <E> IConj conj(IConj coll, E e) {
      return coll.conj(e);
    }

    @Module.Fn(name = "conj", option = true)
    public static <E> java.util.List conj(java.util.List coll, E e) {
      coll.add(e);
      return coll;
    }

    @Module.Fn(name = "conj", option = true)
    public static <K, V> java.util.Map conj(java.util.Map coll, Entry<K, V> e) {
      coll.put(e.getKey(), e.getValue());
      return coll;
    }

    @Module.Fn(name = "conj", option = true)
    public static <E> java.util.Set conj(java.util.Set coll, E e) {
      coll.add(e);
      return coll;
    }

    @Module.Fn(name = "cons", protocol = true)
    @Module.Reduce(type = ARRAY, init = EMPTY_LIST)
    public static <E> ICons cons(ICons coll, E e) {
      return coll.cons(e);
    }

    @Module.Fn(name = "cons", option = true)
    public static <E> java.util.List cons(java.util.List coll, E e) {
      coll.add(0, e);
      return coll;
    }

    @Module.Fn(name = "count", option = true)
    public static <E> long count(E[] e) {
      return e.length;
    }

    @Module.Fn(name = "count", protocol = true)
    public static long count(ICount coll) {
      return coll.count();
    }

    @Module.Fn(name = "count", option = true)
    public static long count(Iterable coll) {
      return It.count(It.iter(coll));
    }

    @Module.Fn(name = "count", option = true)
    public static long count(java.util.Collection coll) {
      return coll.size();
    }

    @Module.Fn(name = "count", option = true)
    public static long count(String s) {
      return s.length();
    }

    @Module.Fn(name = "dissoc", protocol = true)
    @Module.Reduce(type = SELF, init = NIL)
    public static <K, V> IDissoc<K> dissoc(IDissoc coll, K key) {
      return coll.dissoc(key);
    }

    @Module.Fn(name = "dissoc", option = true)
    public static <K, V> java.util.Map dissoc(java.util.Map coll, K key) {
      coll.remove(key);
      return coll;
    }

    @Module.Fn(name = "dissoc", option = true)
    public static <K, V> java.util.Set dissoc(java.util.Set coll, K key) {
      coll.remove(key);
      return coll;
    }

    @Module.Fn(name = "empty", protocol = true)
    public static IEmpty empty(IEmpty coll) {
      return coll.empty();
    }

    @Module.Fn(name = "empty", option = true)
    public static java.util.Collection empty(java.util.Collection coll) {
      coll.clear();
      return coll;
    }

    @Module.Fn(name = "find", protocol = true)
    public static <K, V> V find(IFind lu, K key) {
      return (V) lu.find(key);
    }

    @Module.Fn(name = "find", option = true)
    public static <K, V> Entry<K, V> find(java.util.Map lu, K key) {
      return lu.containsKey(key) ? Struct.pair(key, (V) lu.get(key)) : null;
    }

    @Module.Fn(name = "find", option = true)
    public static <K, V> V find(java.util.Set lu, V key) {
      return lu.contains(key) ? key : null;
    }

    @Module.Fn(name = "get", protocol = true, method = "lookup")
    public static <K, V> V get(ILookup<K, V> lu, K key) {
      return lu.lookup(key);
    }

    @Module.Fn(name = "get", protocol = true, method = "lookup")
    public static <K, V> V get(ILookup<K, V> lu, K key, V notFound) {
      return lu.lookup(key, notFound);
    }

    @Module.Fn(name = "get", option = true)
    public static <K, V> V get(java.util.Map<K, V> lu, K key) {
      return lu.get(key);
    }

    @Module.Fn(name = "get", option = true)
    public static <K, V> V get(java.util.Map<K, V> lu, K key, V notFound) {
      return lu.getOrDefault(key, notFound);
    }

    @Module.Fn(name = "get", option = true)
    public static <E> E get(java.util.Set<E> lu, E key) {
      return lu.contains(key) ? key : null;
    }

    @Module.Fn(name = "get", option = true)
    public static <E> E get(java.util.Set<E> lu, E key, E notFound) {
      return lu.contains(key) ? key : notFound;
    }

    @Module.Fn(name = "has?", protocol = true, method = "has")
    public static <K> boolean has(IFind lu, K key) {
      return lu.has(key);
    }

    @Module.Fn(name = "has?", option = true)
    public static <K> boolean has(java.util.Map lu, K key) {
      return lu.containsKey(key);
    }

    @Module.Fn(name = "has?", option = true)
    public static <K> boolean has(java.util.Set lu, K key) {
      return lu.contains(key);
    }

    @Module.Fn(name = "index:of", protocol = true, method = "indexOf")
    public static <K, V> K indexOf(IIndexed<K, V> lu, V val) {
      return lu.indexOf(val);
    }

    @Module.Fn(name = "index:key", protocol = true, method = "indexOfKey")
    public static <K, V> long indexOfKey(IIndexedKV<K, V> lu, K key) {
      return lu.indexOfKey(key);
    }

    @Module.Fn(name = "index:val", protocol = true, method = "indexOfVal")
    public static <K, V> long indexOfVal(IIndexedKV<K, V> lu, V val) {
      return lu.indexOfVal(val);
    }

    @Module.Fn(name = "into", protocol = true)
    public static <ITR> IConj into(IConj coll, ITR source) {
      return It.reduceIn(It.iter(source), coll, Collection::conj);
    }

    @Module.Fn(name = "into", option = true)
    public static <ITR> java.util.List into(java.util.List coll, ITR source) {
      return It.reduceIn(It.iter(source), coll, Collection::conj);
    }

    @Module.Fn(name = "into", option = true)
    public static <ITR> java.util.Map into(java.util.Map coll, ITR source) {
      return It.reduceIn((Iterator<Entry>) It.iter(source), coll, Collection::conj);
    }

    @Module.Fn(name = "into", option = true)
    public static <ITR> java.util.Set into(java.util.Set coll, ITR source) {
      return It.reduceIn(It.iter(source), coll, Collection::conj);
    }

    @Module.Fn(name = "iter", complete = true)
    public static <ITR> Iterator iter(ITR obj) {
      return It.iter(obj);
    }

    @Module.Fn(name = "keys", protocol = true)
    public static <K, V> Iterator<K> keys(ILookup<K, V> m) {
      return m.keys();
    }

    @Module.Fn(name = "keys", option = true)
    public static <K, V> Iterator<K> keys(java.util.Map<K, V> m) {
      return m.keySet().iterator();
    }

    @Module.Fn(name = "merge", protocol = true)
    @Module.Reduce(type = SELF, init = EMPTY_MAP)
    public static <ITR> IAssoc merge(IAssoc coll, ITR other) {
      Iterator<Entry> it = It.iter(other);
      return It.reduce(
          it,
          coll,
          (m, e) -> {
            return m.assoc(e.getKey(), e.getValue());
          });
    }

    @Module.Fn(name = "merge", option = true)
    public static <ITR> java.util.Map merge(java.util.Map coll, ITR other) {
      Iterator<Entry> it = It.iter(other);
      return It.reduce(
          it,
          coll,
          (m, e) -> {
            m.put(e.getKey(), e.getValue());
            return m;
          });
    }

    @Module.Fn(name = "nth", option = true)
    public static <E> E nth(E[] coll, long idx) {
      return coll[((int) idx)];
    }

    @Module.Fn(name = "nth", protocol = true)
    public static <E> E nth(INth<E> coll, long idx) {
      return coll.nth(idx);
    }

    @Module.Fn(name = "nth", option = true)
    public static <E> E nth(Iterable<E> coll, long idx) {
      return (E) It.nth(It.iter(coll), idx);
    }

    @Module.Fn(name = "nth", option = true)
    public static <E> E nth(java.util.List<E> coll, long idx) {
      return coll.get((int) idx);
    }

    @Module.Fn(name = "nth", option = true)
    public static Character nth(String s, long idx) {
      return s.charAt((int) idx);
    }

    @Module.Fn(name = "range", complete = true)
    public static Iterator<Long> range(Long max) {
      return It.range(max);
    }

    @Module.Fn(name = "range", complete = true)
    public static Iterator<Long> range(Long min, Long max) {
      return It.range(min, max);
    }

    @Module.Fn(name = "to:list", complete = true)
    public static <ITR> List.Standard toList(ITR source) {
      return List.Standard.into(It.iter(source));
    }

    @Module.Fn(name = "to:map", complete = true)
    public static <ITR> Map.Standard toMap(ITR source) {
      return Map.Standard.into(It.iter(source));
    }

    @Module.Fn(name = "to:mutable", complete = true)
    public static IMutable toMutable(IToMutable coll) {
      return coll.toMutable();
    }

    @Module.Fn(name = "to:persistent", complete = true)
    public static IPersistent toPersistent(IToPersistent coll) {
      return coll.toPersistent();
    }

    @Module.Fn(name = "to:seq", complete = true)
    public static <ITR> Seq toSeq(ITR source) {
      return new Seq(It.iter(source));
    }

    @Module.Fn(name = "to:set", complete = true)
    public static <ITR> Set.Standard toSet(ITR source) {
      return Set.Standard.into(It.iter(source));
    }

    @Module.Fn(name = "to:vec", complete = true)
    public static <ITR> Vector.Standard toVec(ITR source) {
      return Vector.Standard.into(It.iter(source));
    }

    @Module.Fn(name = "vals", protocol = true)
    public static <K, V> Iterator<V> vals(ILookup<K, V> m) {
      return m.vals();
    }

    @Module.Fn(name = "vals", option = true)
    public static <K, V> Iterator<V> vals(java.util.Map<K, V> m) {
      return m.values().iterator();
    }

    @Module.Fn(name = "zip", vargs = true, complete = true)
    public static <ITR> Iterator zip(ITR elements) {
      return It.zip(It.iter(elements));
    }

    @Module.Fn(name = "zipmap", complete = true)
    public static <ITR> Map.Standard zipmap(ITR keys, ITR vals) {
      return toMap(It.zipPair(It.iter(keys), It.iter(vals)));
    }
  }

  public interface Interop {

    @Module.Fn(name = "class:constructors")
    public static Constructor[] classConstructors(Class cls) {
      return cls.getDeclaredConstructors();
    }

    @Module.Fn(name = "class:fields")
    public static Field[] classFields(Class cls) {
      return cls.getDeclaredFields();
    }

    @Module.Fn(name = "class", rt = true)
    public static Class classFor(IRuntime rt, String name) {
      return rt.classFor(name);
    }

    @Module.Fn(name = "class:methods")
    public static Method[] classMethods(Class cls) {
      return cls.getDeclaredMethods();
    }

    @Module.Fn(name = "class:inner")
    public static Class[] classInner(Class cls) {
      return cls.getDeclaredClasses();
    }

    @Module.Fn(name = "class:static-methods")
    public static Set<String> classStaticMethods(Class cls) {
      Iterator<Method> methods = It.iter(cls.getMethods());
      return Collection.toSet(
          It.map(
              It.filter(methods, (m) -> Modifier.isStatic(m.getModifiers())), (m) -> m.getName()));
    }

    @Module.Fn(name = "class:static-fields")
    public static Iterator<String> classStaticFields(Class cls) {
      Iterator<Field> fields = It.iter(cls.getFields());
      return It.map(
          It.filter(fields, (m) -> Modifier.isStatic(m.getModifiers())), (m) -> m.getName());
    }

    @Module.Fn(name = "invoke:new", vargs = true)
    public static <R, ITR> Object invokeNew(Class c, ITR args) {
      return Reflect.invokeConstructor(c, It.toArray(args));
    }

    @Module.Fn(name = "invoke", vargs = true)
    public static <R, ITR> Object invokeObj(Object o, String method, ITR args) {
      return Reflect.invokeInstanceMethod(o, method, It.toArray(args));
    }

    @Module.Fn(name = "invoke:get")
    public static <R> Object invokeGet(Object o, String name) {
      return Reflect.getInstanceField(o, name);
    }

    @Module.Fn(name = "invoke:set")
    public static <R> Object invokeSet(Object o, String name, Object val) {
      return Reflect.setInstanceField(o, name, val);
    }

    @Module.Fn(name = "invoke:static", vargs = true)
    public static <R, ITR> Object invokeStatic(Class c, String method, ITR args) {
      return Reflect.invokeStaticMethod(c, method, It.toArray(args));
    }

    @Module.Fn(name = "invoke:fn")
    public static <R, ITR> IFn invokeFn(Class c, String method) {
      var lu = classStaticMethods(c);
      if (lu.has(method)) {
        return Fn.toFnVargs(
            Struct.hashMap(Arr.objects(Basic.keyword("name"), c.getName() + "/" + method)),
            (Function) args -> (R) Reflect.invokeStaticMethod(c, method, It.toArray(args)));
      } else {
        throw new Ex.Info(
            "Method not found: " + method,
            Struct.hashMap(Arr.objects(Basic.keyword("options"), lu)));
      }
    }

    @Module.Fn(name = "invoke:get-static", vargs = true)
    public static <R> Object invokeGetStatic(Class c, String name) {
      return Reflect.getStaticField(c, name);
    }

    @Module.Fn(name = "invoke:set-static", vargs = true)
    public static <R> Object invokeSetStatic(Class c, String name, Object val) {
      return Reflect.setStaticField(c, name, val);
    }
  }

  @Module.Ns(name = "global", tag = "lambda")
  public interface Lambda {

    @Module.Fn(name = "apply", vargs = true, complete = true)
    public static <R, FN, ITR> R apply(FN f, ITR vargs) {
      return apply(Fn.toFn(f), vargs);
    }

    @Module.Fn(name = "apply", vargs = true, helper = true)
    public static <R, ITR> R apply(IFn f, ITR vargs) {
      Object[] args = Arr.toArray(vargs);
      var lit = It.iter(args[args.length - 1]);
      var it = It.concat(Arr.toIter(args, 1, args.length - 1), lit);
      return (R) f.apply(it);
    }

    @Module.Fn(name = "call", vargs = true, complete = true)
    public static <R, ANY, FN, ITR> R call(ANY o, FN f, ITR vargs) {
      return call(o, Fn.toFn(f), vargs);
    }

    @Module.Fn(name = "call", vargs = true, helper = true)
    public static <R, ANY, ITR> R call(ANY o, IFn f, ITR vargs) {
      Object[] arr = Arr.toArray(It.concat(It.objects(o), It.iter(vargs)));
      return (R) f.apply(arr);
    }

    @Module.Fn(name = "comp", vargs = true, complete = true)
    public static <FN, ITR> IFn comp(ITR fns) {
      return new Fn.T.Comp(fns);
    }

    @Module.Fn(name = "F", vargs = true, complete = true)
    public static <ITR> Boolean F(ITR vargs) {
      return false;
    }

    @Module.Fn(name = "group-by", complete = true)
    public static <FN, ITR> Map.Standard groupBy(FN f, ITR source) {
      return groupBy(Fn.toFn(f), Fn.toFn((Function) Lambda::identity), source);
    }

    @Module.Fn(name = "group-by", helper = true)
    public static <ITR, K> Map.Standard<K, List> groupBy(IFn fk, IFn fv, ITR source) {
      return (Standard<K, List>)
          It.reduceIn(
              It.iter(source),
              (Map<K, List>) Map.Standard.EMPTY,
              (m, e) -> {
                K key = (K) fk.invoke(e);
                List v = m.lookup(key);
                if (v == null) {
                  v = List.Standard.EMPTY;
                }
                return (Map<K, List>) m.assoc(key, (List) v.conj(fv.invoke(e)));
              });
    }

    @Module.Fn(name = "group-by", complete = true)
    public static <FN, ITR> Map.Standard groupBy(IPair<FN, FN> f, ITR source) {
      return groupBy(Fn.toFn(f.getKey()), Fn.toFn(f.getValue()), source);
    }

    @Module.Fn(name = "identity", complete = true)
    public static <ANY> ANY identity(ANY x) {
      return x;
    }

    @Module.Fn(name = "juxt", vargs = true, complete = true)
    public static <FN, ITR> IFn<Iterator, Iterator, Object> juxt(ITR fns) {
      var jl = It.toArray(It.map(It.iter(fns), Fn::toFn));
      return Fn.toFn(
          (Function) (e) -> Struct.vector(It.map(It.iter(jl), (f) -> ((IFn) f).invoke(e))));
    }

    @Module.Fn(name = "keep", complete = true)
    public static <FN> IFn<Iterator, Iterator, Object> keep(FN f) {
      return keep(Fn.toFn(f));
    }

    @Module.Fn(name = "keep", complete = true)
    public static <FN, ITR> Iterator keep(FN f, ITR source) {
      return keep(Fn.toFn(f), source);
    }

    @Module.Fn(name = "keep", helper = true)
    public static IFn<Iterator, Iterator, Object> keep(IFn f) {
      return Fn.toFn((Function) (source) -> keep(f, source));
    }

    @Module.Fn(name = "keep", helper = true)
    public static <ITR> Iterator keep(IFn f, ITR source) {
      var it = It.iter(source);
      return It.from(it::hasNext, () -> f.invoke(it.next()));
    }

    @Module.Fn(name = "map", complete = true)
    public static <FN> IFn<Iterator, Iterator, Object> map(FN f) {
      return map(Fn.toFn(f));
    }

    @Module.Fn(name = "map", complete = true)
    public static <FN, ITR> Iterator map(FN f, ITR source) {
      return map(Fn.toFn(f), source);
    }

    @Module.Fn(name = "map", helper = true)
    public static IFn<Iterator, Iterator, Object> map(IFn f) {
      return Fn.toFn((Function) (source) -> map(f, source));
    }

    @Module.Fn(name = "map", helper = true)
    public static <ITR> Iterator map(IFn f, ITR source) {
      var it = It.iter(source);
      return It.from(it::hasNext, () -> f.invoke(it.next()));
    }

    @Module.Fn(name = "map:apply", complete = true)
    public static <FN> IFn<Iterator, Iterator, Object> mapApply(FN f) {
      return mapApply(Fn.toFn(f));
    }

    @Module.Fn(name = "map:apply", complete = true)
    public static <FN, ITR> Iterator mapApply(FN f, ITR source) {
      return mapApply(Fn.toFn(f), source);
    }

    @Module.Fn(name = "map:apply", helper = true)
    public static IFn<Iterator, Iterator, Object> mapApply(IFn f) {
      return Fn.toFn((Function) (source) -> mapApply(f, source));
    }

    @Module.Fn(name = "map:apply", helper = true)
    public static <ITR> Iterator mapApply(IFn f, ITR source) {
      var it = It.iter(source);
      return It.from(it::hasNext, () -> f.apply(it.next()));
    }

    @Module.Fn(name = "mapcat", complete = true)
    public static <FN> IFn<Iterator, Iterator, Object> mapcat(FN f) {
      return mapcat(Fn.toFn(f));
    }

    @Module.Fn(name = "mapcat", complete = true)
    public static <FN, ITR> Iterator mapcat(FN f, ITR source) {
      return mapcat(Fn.toFn(f), source);
    }

    @Module.Fn(name = "mapcat", helper = true)
    public static IFn<Iterator, Iterator, Object> mapcat(IFn f) {
      return Fn.toFn((Function) (source) -> mapcat(f, source));
    }

    @Module.Fn(name = "mapcat", helper = true)
    public static <ITR> Iterator mapcat(IFn f, ITR source) {
      var it = It.iter(source);
      return It.mapcat(it, (e) -> (Iterator) f.invoke(e));
    }

    @Module.Fn(name = "map:entries", complete = true)
    public static <FN, ITR> Map.Standard mapEntries(FN f, ITR source) {
      return mapEntries(Fn.toFn(f), source);
    }

    @Module.Fn(name = "map:entries", helper = true)
    public static <ITR> Map.Standard mapEntries(IFn f, ITR source) {
      Iterator<Entry> it = It.iter(source);
      return Map.Standard.into(It.map(it, e -> (Entry) f.invoke(e)));
    }

    @Module.Fn(name = "map:juxt", helper = true)
    public static <ITR> Map.Standard mapJuxt(IFn fk, IFn fv, ITR source) {
      var it = It.iter(source);
      return Map.Standard.into(It.map(it, e -> Struct.pair(fk.invoke(e), fv.invoke(e))));
    }

    @Module.Fn(name = "map:juxt", complete = true)
    public static <FN, ITR> Map.Standard mapJuxt(IPair<FN, FN> f, ITR source) {
      return mapJuxt(Fn.toFn(f.getKey()), Fn.toFn(f.getValue()), source);
    }

    @Module.Fn(name = "map:keys", complete = true)
    public static <FN, ITR> Map.Standard mapKeys(FN f, ITR source) {
      return mapKeys(Fn.toFn(f), source);
    }

    @Module.Fn(name = "map:keys", helper = true)
    public static <ITR> Map.Standard mapKeys(IFn f, ITR source) {
      Iterator<Entry> it = It.iter(source);
      return Map.Standard.into(It.map(it, e -> Struct.pair(f.invoke(e.getKey()), e.getValue())));
    }

    @Module.Fn(name = "map:vals", complete = true)
    public static <FN, ITR> Map.Standard mapVals(FN f, ITR source) {
      return mapVals(Fn.toFn(f), source);
    }

    @Module.Fn(name = "map:vals", helper = true)
    public static <ITR> Map.Standard mapVals(IFn f, ITR source) {
      Iterator<Entry> it = It.iter(source);
      return Map.Standard.into(It.map(it, e -> Struct.pair(e.getKey(), f.invoke(e.getValue()))));
    }

    @Module.Fn(name = "NIL", vargs = true, complete = true)
    public static <ITR> Object NIL(ITR x) {
      return null;
    }

    @Module.Fn(name = "partial", vargs = true, complete = true)
    public static <FN, ITR> IFn partial(FN f, ITR vargs) {
      return new Fn.T.Partial(f, vargs);
    }

    @Module.Fn(name = "partition:pair", helper = true)
    public static IFn<Iterator, Iterator, Object> partitionPair() {
      return Fn.toFn((Function) (s) -> partitionPair(s));
    }

    @Module.Fn(name = "partition:pair", complete = true)
    public static <ITR> Iterator partitionPair(ITR source) {
      var it = It.iter(source);
      return It.partitionPair(it);
    }

    @Module.Fn(name = "pipe", vargs = true, complete = true)
    public static <FN, ITR> IFn<Iterator, Iterator, Object> pipe(ITR fns) {
      var pl = It.toArray(It.map(It.iter(fns), Fn::toFn));
      return Fn.toFn(
          (Function) (it) -> Arr.reduce((i, f) -> (Iterator) ((IFn) f).invoke(i), it, pl));
    }

    @Module.Fn(name = "reduce", complete = true)
    public static <ITR, FN, R> R reduce(FN f, FN end, R init, ITR source) {
      return reduce(Fn.toFn(f), Fn.toFn(end), init, source);
    }

    @Module.Fn(name = "reduce", complete = true)
    public static <ITR, FN, R> R reduce(FN f, ITR source) {
      return reduce(Fn.toFn(f), source);
    }

    @Module.Fn(name = "reduce", complete = true)
    public static <ITR, FN, R> R reduce(FN f, R init, ITR source) {
      return reduce(Fn.toFn(f), init, source);
    }

    @Module.Fn(name = "reduce", helper = true)
    public static <ITR, R> R reduce(IFn f, IFn end, R init, ITR source) {
      var it = It.iter(source);
      return (R)
          It.reduce(it, init, (acc, e) -> (R) f.invoke(acc, e), (acc) -> (Boolean) end.invoke(acc));
    }

    @Module.Fn(name = "reduce", helper = true)
    public static <ITR, R> R reduce(IFn f, ITR source) {
      var it = It.iter(source);
      return (R) It.reduce(it, (acc, e) -> f.invoke(acc, e));
    }

    @Module.Fn(name = "reduce", helper = true)
    public static <ITR, R> R reduce(IFn f, R init, ITR source) {
      var it = It.iter(source);
      return (R) It.reduce(it, init, (acc, e) -> (R) f.invoke(acc, e));
    }

    @Module.Fn(name = "reduce-in", complete = true)
    public static <ITR, FN, R> R reduceIn(R init, FN f, ITR source) {
      return reduceIn(init, Fn.toFn(f), source);
    }

    @Module.Fn(name = "reduce-in", helper = true)
    public static <ITR, R> R reduceIn(R init, IFn f, ITR source) {
      var it = It.iter(source);
      return (R) It.reduceIn(it, init, (acc, e) -> (R) f.invoke(acc, e));
    }

    @Module.Fn(name = "T", vargs = true, complete = true)
    public static <ITR> Boolean T(ITR vargs) {
      return true;
    }
  }

  @Module.Ns(name = "global", tag = "ops")
  public interface Ops {

    @Module.Fn(name = "+")
    @Module.Reduce(type = INIT, init = ZERO)
    public static Number add(Number x, Number y) {
      return Num.add(x, y);
    }

    @Module.Fn(name = "b&")
    @Module.Reduce(type = INIT, init = NEG_ONE)
    public static Number bitAnd(Number x, Number y) {
      return Num.and(x, y);
    }

    @Module.Fn(name = "b|")
    @Module.Reduce(type = INIT, init = ZERO)
    public static Number bitOr(Number x, Number y) {
      return Num.or(x, y);
    }

    @Module.Fn(name = "dec")
    public static Number dec(Number x) {
      return Num.minus(x, 1);
    }

    @Module.Fn(name = "/")
    @Module.Reduce(type = ARRAY, init = ONE)
    public static Number divide(Number x, Number y) {
      return Num.divide(x, y);
    }

    @Module.Fn(name = "equals")
    @Module.Reduce(type = COMPARE, init = TRUE)
    public static boolean equals(Object k1, Object k2) {
      return (k1 == k2) ? true : (k1 != null && k1.equals(k2));
    }

    @Module.Fn(name = "=")
    @Module.Reduce(type = COMPARE, init = TRUE)
    public static Boolean equivalent(Object k1, Object k2) {
      return Eq.eq(k1, k2);
    }

    @Module.Fn(name = ">")
    @Module.Reduce(type = COMPARE, init = TRUE)
    public static Boolean gt(Number x, Number y) {
      return Num.gt(x, y);
    }

    @Module.Fn(name = ">=")
    @Module.Reduce(type = COMPARE, init = TRUE)
    public static Boolean gte(Number x, Number y) {
      return Num.gte(x, y);
    }

    @Module.Fn(name = "identical")
    @Module.Reduce(type = COMPARE, init = TRUE)
    public static boolean identical(Object k1, Object k2) {
      return k1 == k2;
    }

    @Module.Fn(name = "inc")
    public static Number inc(Number x) {
      return Num.add(x, 1);
    }

    @Module.Fn(name = "neg?")
    public static Boolean isNeg(Number x) {
      return Num.lt(x, 0);
    }

    @Module.Fn(name = "pos?")
    public static Boolean isPos(Number x) {
      return Num.gt(x, 0);
    }

    @Module.Fn(name = "zero?")
    public static Boolean isZero(Number x) {
      return Num.eq(x, 0);
    }

    @Module.Fn(name = "<")
    @Module.Reduce(type = COMPARE, init = TRUE)
    public static Boolean lt(Number x, Number y) {
      return Num.lt(x, y);
    }

    @Module.Fn(name = "<=")
    @Module.Reduce(type = COMPARE, init = TRUE)
    public static Boolean lte(Number x, Number y) {
      return Num.lte(x, y);
    }

    @Module.Fn(name = "-")
    @Module.Reduce(type = ARRAY, init = ZERO)
    public static Number minus(Number x, Number y) {
      return Num.minus(x, y);
    }

    @Module.Fn(name = "*")
    @Module.Reduce(type = INIT, init = ONE)
    public static Number multiply(Number x, Number y) {
      return Num.multiply(x, y);
    }

    @Module.Fn(name = "not=")
    @Module.Reduce(type = COMPARE, init = FALSE)
    public static Boolean notEquivalent(Object k1, Object k2) {
      return !Eq.eq(k1, k2);
    }
  }

  public interface Runtime {

    @Module.Fn(name = "eval", rt = true)
    public static <AST> Object eval(IRuntime rt, AST input) {
      return rt.eval(input);
    }

    @Module.Fn(name = "read-string", rt = true)
    public static <AST> AST readString(IRuntime<AST, ?, ?> rt, String input) {
      return rt.readString(input);
    }

    @Module.Fn(name = "ctl", vargs = true, rt = true)
    public static <ITR> Object ctl(IRuntime rt, ITR args) {
      return rt.getRoot().call(Arr.toArray(args));
    }

    @Module.Fn(name = "sys:path", rt = true)
    public static IColl<URL> sysPath(IRuntime rt) {
      return rt.pathCache();
    }

    @Module.Fn(name = "sys:path-add", rt = true)
    public static <ITR> IColl<String> sysPathAdd(IRuntime rt, ITR paths) {
      return rt.pathAdd((String[]) It.toArray(It.iter(paths), String.class));
    }

    @Module.Fn(name = "sys:path-remove", rt = true)
    public static <ITR> IColl<String> sysPathRemove(IRuntime rt, ITR paths) {
      return rt.pathRemove((String[]) It.toArray(It.iter(paths), String.class));
    }

    @Module.Fn(name = "sys:path-purge", rt = true)
    public static IColl<String> sysPathPurge(IRuntime rt) {
      return (IColl) rt.pathCache().empty();
    }

    @Module.Fn(name = "sys:globals", rt = true)
    public static IFind sysGlobals(IRuntime rt) {
      return rt.getEnv().getMap();
    }

    @Module.Fn(name = "sys:loader", rt = true)
    public static ClassLoader sysloader(IRuntime rt) {
      return rt.classLoader();
    }

    @Module.Fn(name = "sys:root", rt = true)
    public static IContext sysRoot(IRuntime rt) {
      return rt.getRoot();
    }

    @Module.Fn(name = "sys:call", vargs = true, rt = true)
    public static <ITR> Object sysCall(IRuntime rt, ITR inputs) {
      return rt.call(Arr.toArray(inputs));
    }

    @Module.Fn(name = "sys:alias-add", rt = true)
    public static Class sysAddAlias(IRuntime rt, Symbol sym, Class c) {
      return rt.aliasAdd(sym, c);
    }

    @Module.Fn(name = "sys:alias-remove", rt = true)
    public static Class sysRemoveAlias(IRuntime rt, Symbol sym) {
      return rt.aliasRemove(sym);
    }

    @Module.Fn(name = "sys:alias-purge", rt = true)
    public static IColl sysAliasPurge(IRuntime rt, Symbol sym) {
      return (IColl) rt.aliasCache().empty();
    }

    @Module.Fn(name = "sys:alias", rt = true)
    public static IColl sysListAlias(IRuntime rt) {
      return rt.aliasCache();
    }

    @Module.Fn(name = "sys:import", vargs = true, rt = true)
    public static <ITR> Class[] sysImport(IRuntime rt, ITR inputs) {
      Iterator<Entry> it = It.partitionPair(It.iter(inputs));
      return (Class[])
          It.toArray(
              (Iterator) It.map(it, (p) -> rt.aliasAdd(p.getKey(), (Class) p.getValue())),
              Class.class);
    }

    @Module.Fn(name = "sys:cache", rt = true)
    public static IColl sysCache(IRuntime rt) {
      return rt.classCache();
    }

    @Module.Fn(name = "sys:cache-add", rt = true)
    public static IColl sysCacheAdd(IRuntime rt, String name, Class cls) {
      return (IColl) ((IAssoc) rt.classCache()).assoc(name, cls);
    }

    @Module.Fn(name = "sys:cache-purge", rt = true)
    public static IColl sysCachePurge(IRuntime rt) {
      return (IColl) rt.classCache().empty();
    }

    @Module.Fn(name = "sys:cache-remove", rt = true, vargs = true)
    public static <ITR> IColl sysCacheRemove(IRuntime rt, ITR names) {
      return It.reduce(
          It.iter(names), rt.classCache(), (m, n) -> (IMapType) ((IMapType) m).dissoc(n));
    }
  }

  @Module.Ns(name = "global", tag = "structure")
  public interface Struct {

    //
    // Coll
    //

    @Module.Fn(name = "hash-map", vargs = true, complete = true)
    public static <ITR, K, V> Map.Standard<K, V> hashMap(ITR elements) {
      return Map.Standard.into(It.partitionPair(It.iter(elements)));
    }

    @Module.Fn(name = "hash-set", vargs = true, complete = true)
    public static <ITR, E> Set.Standard<E> hashSet(ITR elements) {
      return Set.Standard.into(It.iter(elements));
    }

    @Module.Fn(name = "j:arr", complete = true)
    public static <ITR, E> E[] jArr(Class<E> type, ITR vargs) {
      return (E[]) It.toArray(It.iter(vargs), type);
    }

    @Module.Fn(name = "j:objs", vargs = true, complete = true)
    public static <ITR> Object[] jArr(ITR vargs) {
      return Arr.toArray(vargs);
    }

    @Module.Fn(name = "j:hash-map", vargs = true, complete = true)
    public static <ITR> java.util.HashMap jHashMap(ITR vargs) {
      var m = new java.util.HashMap();
      It.each(
          It.partitionPair(It.iter(vargs)),
          (p) -> m.put(((Entry) p).getKey(), ((Entry) p).getValue()));
      return m;
    }

    @Module.Fn(name = "j:hash-set", vargs = true, complete = true)
    public static <ITR> java.util.HashSet jHashSet(ITR vargs) {
      var s = new java.util.HashSet();
      It.each(It.iter(vargs), (e) -> s.add(e));
      return s;
    }

    //
    // Checks
    //

    @Module.Fn(name = "j:list", vargs = true, complete = true)
    public static <ITR> java.util.ArrayList jList(ITR vargs) {
      return It.toArrayList(It.iter(vargs));
    }

    @Module.Fn(name = "list", vargs = true, complete = true)
    public static <ITR, E> List.Standard<E> list(ITR elements) {
      return List.Standard.into(It.iter(elements));
    }

    @Module.Fn(name = "mut:hash-map", vargs = true, complete = true)
    public static <ITR, K, V> Map.Mutable<K, V> mutHashMap(ITR elements) {
      return Map.Mutable.into(It.partitionPair(It.iter(elements)));
    }

    @Module.Fn(name = "mut:hash-set", vargs = true, complete = true)
    public static <ITR, E> Set.Mutable<E> mutHashSet(ITR elements) {
      return Set.Mutable.into(It.iter(elements));
    }

    @Module.Fn(name = "mut:list", vargs = true, complete = true)
    public static <ITR, E> List.Mutable<E> mutList(ITR elements) {
      return List.Mutable.into(It.iter(elements));
    }

    @Module.Fn(name = "mut:ordered-map", vargs = true, complete = true)
    public static <ITR, K, V> OrderedMap.Mutable<K, V> mutOrderedMap(ITR elements) {
      return OrderedMap.Mutable.into(It.partitionPair(It.iter(elements)));
    }

    @Module.Fn(name = "mut:ordered-set", vargs = true, complete = true)
    public static <ITR, E> OrderedSet.Mutable<E> mutOrderedSet(ITR elements) {
      return OrderedSet.Mutable.into(It.iter(elements));
    }

    @Module.Fn(name = "mut:queue", vargs = true, complete = true)
    public static <ITR, E> Queue.Mutable<E> mutQueue(ITR elements) {
      return Queue.Mutable.into(It.iter(elements));
    }

    @Module.Fn(name = "mut:sorted-map", vargs = true, complete = true)
    public static <ITR, K, V> SortedMap.Mutable<K, V> mutSortedMap(ITR elements) {
      return SortedMap.Mutable.into(It.partitionPair(It.iter(elements)));
    }

    @Module.Fn(name = "mut:sorted-set", vargs = true, complete = true)
    public static <ITR, E> SortedSet.Mutable<E> mutSortedSet(ITR elements) {
      return SortedSet.Mutable.into(It.iter(elements));
    }

    @Module.Fn(name = "mut:vector", vargs = true, complete = true)
    public static <ITR, E> Vector.Mutable<E> mutVector(ITR elements) {
      return Vector.Mutable.into(It.iter(elements));
    }

    @Module.Fn(name = "ordered-map", vargs = true, complete = true)
    public static <ITR, K, V> OrderedMap.Standard<K, V> orderedMap(ITR elements) {
      return OrderedMap.Standard.into(It.partitionPair(It.iter(elements)));
    }

    @Module.Fn(name = "ordered-set", vargs = true, complete = true)
    public static <ITR, E> OrderedSet.Standard<E> orderedSet(ITR elements) {
      return OrderedSet.Standard.into(It.iter(elements));
    }

    @Module.Fn(name = "pair", complete = true)
    public static <K, V> IPair<K, V> pair(K key, V val) {
      return new Tuple.Tup2.L(null, key, val);
    }

    @Module.Fn(name = "queue", vargs = true, complete = true)
    public static <ITR, E> Queue.Standard<E> queue(ITR elements) {
      return Queue.Standard.into(It.iter(elements));
    }

    @Module.Fn(name = "sorted-map", vargs = true, complete = true)
    public static <ITR, K, V> SortedMap.Standard<K, V> sortedMap(ITR elements) {
      return SortedMap.Standard.into(It.partitionPair(It.iter(elements)));
    }

    @Module.Fn(name = "sorted-set", vargs = true, complete = true)
    public static <ITR, E> SortedSet.Standard<E> sortedSet(ITR elements) {
      return SortedSet.Standard.into(It.iter(elements));
    }

    @Module.Fn(name = "to:facade", complete = true)
    public static <E> ILinearType<E> toFacade(java.util.List<E> l) {
      return new Ut.AsList<E>(l);
    }

    @Module.Fn(name = "to:facade", complete = true)
    public static <K, V> IMapType<K, V> toFacade(java.util.Map<K, V> m) {
      return new Ut.AsMap<K, V>(m);
    }

    @Module.Fn(name = "tup", complete = true)
    public static Tup0 tup() {
      return Tup0.EMPTY;
    }

    @Module.Fn(name = "tup", complete = true)
    public static Tup1.L tup(Object a) {
      return new Tup1.L(null, a);
    }

    @Module.Fn(name = "tup", complete = true)
    public static Tup2.L tup(Object a, Object b) {
      return new Tup2.L(null, a, b);
    }

    @Module.Fn(name = "tup", complete = true)
    public static Tup3.L tup(Object a, Object b, Object c) {
      return new Tup3.L(null, a, b, c);
    }

    @Module.Fn(name = "tup", complete = true)
    public static Tup4.L tup(Object a, Object b, Object c, Object d) {
      return new Tup4.L(null, a, b, c, d);
    }

    @Module.Fn(name = "tup", complete = true)
    public static Tup5.L tup(Object a, Object b, Object c, Object d, Object e) {
      return new Tup5.L(null, a, b, c, d, e);
    }

    public static ILinearType tuple(Object[] xs) {
      switch (xs.length) {
        case 0:
          return Tup0.EMPTY;
        case 1:
          return new Tup1.L(null, xs[0]);
        case 2:
          return new Tup2.L(null, xs[0], xs[1]);
        case 3:
          return new Tup3.L(null, xs[0], xs[1], xs[2]);
        case 4:
          return new Tup4.L(null, xs[0], xs[1], xs[2], xs[3]);
        case 5:
          return new Tup5.L(null, xs[0], xs[1], xs[2], xs[3], xs[4]);
        default:
          throw new Ex.Arity(xs.length, "");
      }
    }

    @Module.Fn(name = "vector", vargs = true, complete = true)
    public static <ITR, E> Vector.Standard<E> vector(ITR elements) {
      return Vector.Standard.into(It.iter(elements));
    }
  }

  @Module.Ns(name = "global", tag = "time")
  public interface Time {

    @Module.Fn(name = "bench:fn", complete = true)
    public static long bench(IFn f) {
      long start = Ut.Clock.currentTimeNanos();
      f.invoke();
      long end = Ut.Clock.currentTimeNanos();
      return end - start;
    }

    //
    //
    //

    @Module.Fn(name = "now", complete = true)
    public static long now() {
      return Ut.Clock.currentTimeNanos();
    }
  }

  @Module.Ns(name = "global", tag = "lambda")
  public interface Util {

    //
    // Print
    //

    @Module.Fn(name = "pr-str", complete = true)
    public static String prStr(Object e) {
      return G.display(e);
    }

    @Module.Fn(name = "str", vargs = true, complete = true)
    public static <ITR> String str(ITR args) {
      return It.toString(
          It.iter(args),
          "",
          "",
          "",
          (e) -> {
            if (e == null) {
              return "";
            } else if (e instanceof String) {
              return (String) e;
            } else if (e instanceof IDisplay) {
              return ((IDisplay) e).display();
            } else {
              return e.toString();
            }
          });
    }
  }
}

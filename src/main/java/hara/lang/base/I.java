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
	}

	public interface IComponent
	{
		Component start();
		Component stop();
		Component kill();
	}

	public interface IComponentQuery
	{
		boolean started(Component component);
		boolean stopped(Component component);
		Object info(Component component, int level);
		boolean remote(Component component);
		Object health(Component component);
	}

	public interface IComponentProps
	{
		Object props(Component component);
	}

	public interface IComponentOptions
	{
		Object getOptions(Component component);
	}

	public interface IComponentTrack
	{
		Object getTrackPath(Component component);
	}

	public interface ISpace
	{
		Object contextSet(Object sp, Object ctx, Object key, Object options);
		Object contextUnset(Object sp, Object ctx);
		Object contextList(Object sp);
		Object contextGet(Object sp, Object ctx);
		Object rtActive(Object sp);
		Object rtGet(Object sp, Object ctx);
		Object rtStart(Object sp, Object ctx);
		boolean rtStarted(Object sp, Object ctx);
		boolean rtStopped(Object sp, Object ctx);
		Object rtStop(Object sp, Object ctx);
	}

	public interface IPointer
	{
		Object ptrContext(Object ptr);
		Object ptrKeys(Object ptr);
		Object ptrVal(Object ptr, Object key);
	}

	public interface IContext
	{
		Object rawEval(Object rt, String string);
		Object initPtr(Object rt, Object ptr);
		Object tagsPtr(Object rt, Object ptr);
		Object derefPtr(Object rt, Object ptr);
		Object displayPtr(Object rt, Object ptr);
		Object invokePtr(Object rt, Object ptr, Object args);
		Object transformInPtr(Object rt, Object ptr, Object args);
		Object transformOutPtr(Object rt, Object ptr, Object retrn);
	}

	public interface IContextLifeCycle
	{
		boolean hasModule(Object rt, Object moduleId);
		Object setupModule(Object rt, Object moduleId);
		Object teardownModule(Object rt, Object moduleId);
		boolean hasPtr(Object rt, Object ptr);
		Object setupPtr(Object rt, Object ptr);
		Object teardownPtr(Object rt, Object ptr);
	}

	public interface IReturn
	{
		Object getValue(Object obj);
		Object getError(Object obj);
		boolean hasError(Object obj);
		Object getStatus(Object obj);
		Object getMetadata(Object obj);
		boolean isContainer(Object obj);
	}

	public interface IWatch
	{
		Object addWatch(Object obj, Object k, Object f, Object opts);
		boolean hasWatch(Object obj, Object k, Object opts);
		Object removeWatch(Object obj, Object k, Object opts);
		Object listWatch(Object obj, Object opts);
	}

	public interface IDispatch
	{
		Object submit(Object dispatch, Object entry);
		boolean bulk(Object dispatch);
	}

	public interface ISink
	{
		Object collect(Object sink, Object xf, Object supply);
	}

	public interface ISource
	{
		Object produce(Object source);
	}

	public interface IBlocking
	{
		Object takeElement(Object source);
	}

	public interface IStage
	{
		Object stageUnit(Object stage);
		boolean stageRealized(Object stage);
		Object stageRealize(Object stage);
	}

	public interface ITrack
	{
		Object trackPath(Object component);
	}

	public interface IBinary
	{
		Object toBitstr(Object x);
		Object toBitseq(Object x);
		Object toBitset(Object x);
		Object toBytes(Object x);
		Object toNumber(Object x);
	}

	public interface IByteSource
	{
		Object toInputStream(Object obj);
	}

	public interface IByteSink
	{
		Object toOutputStream(Object obj);
	}

	public interface IByteChannel
	{
		Object toChannel(Object obj);
	}

	public interface IString
	{
		String toString(Object x);
	}

	public interface IArchive
	{
		Object url(Object archive);
		Object path(Object archive, Object entry);
		Object list(Object archive);
		boolean has(Object archive, Object entry);
		Object archive(Object archive, Object root, Object inputs);
		Object extract(Object archive, Object output, Object entries);
		Object insert(Object archive, Object entry, Object input);
		Object remove(Object archive, Object entry);
		Object write(Object archive, Object entry, Object stream);
		Object stream(Object archive, Object entry);
	}

	public interface IDeps
	{
		Object getEntry(Object context, Object id);
		Object getDeps(Object context, Object id);
		Object listEntries(Object context);
	}

	public interface IDepsMutate
	{
		Object addEntry(Object context, Object id, Object entry, Object deps);
		Object removeEntry(Object context, Object id);
		Object refreshEntry(Object context, Object id);
	}

	public interface IDepsCompile
	{
		Object stepConstruct(Object context, Object acc, Object id);
		Object initConstruct(Object context);
	}

	public interface IDepsTeardown
	{
		Object stepDeconstruct(Object context, Object acc, Object id);
	}

	public interface IDepsLibrary
	{
		Object parseNative(Object context, Object input, Object opts);
	}

	public interface IDepsProducer
	{
		Object produce(Object context, Object opts);
	}

	public interface IClient extends OFn {

	}

	public interface IRequest
	{
		Object requestSingle(Object client, Object command, Object opts);
		Object processSingle(Object client, Object output, Object opts);
		Object requestBulk(Object client, Object commands, Object opts);
		Object processBulk(Object client, Object inputs, Object outputs, Object opts);
	}

	public interface IRequestTransact
	{
		Object transactStart(Object client);
		Object transactEnd(Object client);
		Object transactCombine(Object client, Object commands);
	}

	public interface IApplicable
	{
		Object applyIn(Object app, Object rt, Object args);
		Object applyDefault(Object app);
		Object transformIn(Object app, Object rt, Object args);
		Object transformOut(Object app, Object rt, Object args, Object retrn);
	}

	public interface ITemplate
	{
		Object match(Object template, Object obj);
	}

	public interface IStateGet
	{
		Object getState(Object obj, Object opts);
	}

	public interface IStateSet
	{
		Object updateState(Object obj, Object f, Object args, Object opts);
		Object setState(Object obj, Object v, Object opts);
		Object emptyState(Object obj, Object opts);
		Object cloneState(Object obj, Object opts);
	}

	public interface IBlock
	{
		Object type();
		Object tag();
		String string();
		Number length();
		Number width();
		Number height();
		Number prefixed();
		Number suffixed();
		boolean verify();
	}

	public interface IBlockModifier
	{
		Object modify(Object accumulator, Object input);
	}

	public interface IBlockExpression
	{
		Object value();
		String valueString();
	}

	public interface IBlockContainer
	{
		Object children();
		Object replaceChildren(Object children);
	}

	public interface ILogger
	{
		Object loggerWrite(Object logger, Object entry);
	}

	public interface ILoggerProcess
	{
		Object loggerProcess(Object logger, Object entries);
	}

	public interface IWire
	{
		Object read(Object remote);
		Object write(Object remote, Object command);
		Object close(Object remote);
	}

	public interface IInstant
	{
		long toLong(Object t);
		boolean hasTimezone(Object t);
		Object getTimezone(Object t);
		Object withTimezone(Object t, Object tz);
	}

	public interface IRepresentation
	{
		Object millisecond(Object t, Object opts);
		Object second(Object t, Object opts);
		Object minute(Object t, Object opts);
		Object hour(Object t, Object opts);
		Object day(Object t, Object opts);
		Object dayOfWeek(Object t, Object opts);
		Object month(Object t, Object opts);
		Object year(Object t, Object opts);
	}

	public interface IDuration
	{
		Object toLength(Object d, Object opts);
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

	public interface Env<K, V> extends I.Lookup<K, V> {
		Env<K, V> getParent();

		I.Lookup<K, V> getMap();
		
		@Override
		default Entry<K, V> find(K k) {
			Entry<K, V> e = getMap().find(k);
			if(e == null) {
				Env<K, V> env = getParent();
				if(env != null) {
					return env.find(k);
				}
			}
			return e;
		}

		@Override
		default Iterator<K> keys() {
			throw new Ex.Unsupported();
		}

		@Override
		default Iterator<V> vals() {
			throw new Ex.Unsupported();
		}
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

		G.ObjType getObjType();

		@Override
		default String hashSeed() {
			return "HARA::" + getObjType().toString() + "";
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

	@SuppressWarnings("rawtypes")
	public interface Runtime<AST, K, V> extends Context {
		Fn           findFn(Class cls, String name);
		Fn           findFn(Class cls, String name, int args);
		Object       eval(AST ast);
		Object       eval(AST ast, Env env);
		Env<K, V>    getEnv();
		V            getObj(K key);
		V            setObj(K key, V value);
		AST          readString(String input);
		Class        classFor(String name);
		ClassLoader  classLoader();
		Coll<Entry<String, Class>> classCache();
		Context      getRoot();
		Coll<URL>    pathCache();
		Coll<URL>    pathAdd(String[] paths);
		Coll<URL>    pathRemove(String[] paths);
		Class        aliasAdd(K key, Class v);
		Class        aliasRemove(K key);
		Coll<Entry<String, Class>>  aliasCache();
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
			if (ws != null) {
				ws.forEachRemaining(e -> e.getValue().accept(new WatchEntry<R, V>(e.getKey(), this, oldVal, newVal)));
			}
		}

		default void removeWatch(Object key) {
			throw new UnsupportedOperationException("Not Supported");
		}

		@SuppressWarnings("unchecked")
		public class WatchEntry<R, V> extends Std.T.Tup4.L<Object, R, V, V> {

			WatchEntry(Object key, Watch<R, V> ref, V oldVal, V newVal) {
				super(null, key, (R) ref, oldVal, newVal);
			}
		}
	}

}

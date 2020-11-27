package hara.lang.data;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import hara.lang.base.*;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface Atom<V> {

	public abstract class Struct<R, V> implements Swap<R, V>, I.Reset<V> {
		final AtomicReference<V> _state;

		public Struct(V init) {
			_state = new AtomicReference<V>(init);
		}

		@Override
		public V deref() {
			return _state.get();
		}

		@Override
		public boolean compareAndSet(V oldVal, V newVal) {
			return _state.compareAndSet(oldVal, newVal);
		}

		@Override
		public V reset(V newVal) {
			_state.set(newVal);
			return newVal;
		}
	}

	@SuppressWarnings("rawtypes")
	public final class Basic<V> extends Struct<Atom, V> {
		public Basic(V state) {
			super(state);
		}
	}

	@SuppressWarnings("rawtypes")
	public final class Standard<V> extends Struct<Atom, V> implements Swap<Atom, V> {

		final Predicate<V> _validator;
		final ConcurrentHashMap<Object, Consumer<WatchEntry<Atom, V>>> _watches 
			= new ConcurrentHashMap<Object, Consumer<WatchEntry<Atom, V>>>();

		public Standard(V init) {
			super(init);
			_validator = null;
		}
		
		public Standard(V init, Predicate<V> validator) {
			super(init);
			_validator = validator;
		}

		@Override
		public Predicate<V> getValidator() {
			return _validator;
		}

		@Override
		public void addWatch(Object key, Consumer<WatchEntry<Atom, V>> f) {
			_watches.put(key, f);
		}
		
		@Override
		public void removeWatch(Object key) {
			_watches.remove(key);
		}
		
		@Override
		public Iterator<Map.Entry<Object, Consumer<WatchEntry<Atom, V>>>> getWatches() {
			return _watches.entrySet().iterator();
		}

	}
	
	public interface Swap<R, V> extends I.Watch<R, V>, I.Validate<V>, I.Deref<V> {
		
		boolean compareAndSet(V oldVal, V newVal);

		default Object swap(Function<V, V> f) {
			for (;;) {
				var v = deref();
				var newVal = f.apply(v);
				validate(newVal);
				if (compareAndSet(v, newVal)) {
					notifyWatches(v, newVal);
					return newVal;
				}
			}
		}

		default Object swap(BiFunction<V, Object, V> f, Object arg) {
			for (;;) {
				var v = deref();
				var newVal = f.apply(v, arg);
				validate(newVal);
				if (compareAndSet(v, newVal)) {
					notifyWatches(v, newVal);
					return newVal;
				}
			}
		}

		default Object swap(BiFunction<V, Object[], V> f, Object... vargs) {
			for (;;) {
				var v = deref();
				var newVal = f.apply(v, vargs);
				validate(newVal);
				if (compareAndSet(v, newVal)) {
					notifyWatches(v, newVal);
					return newVal;
				}
			}
		}
	}
}


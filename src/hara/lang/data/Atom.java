package hara.lang.data;

import java.util.concurrent.atomic.AtomicReference;

import hara.lang.base.*;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface Atom {

	public abstract class Struct implements Swap {
		final AtomicReference<Object> _state;

		public Struct(Object init) {
			_state = new AtomicReference<Object>(init);
		}

		@Override
		public Object deref() {
			return _state.get();
		}

		public boolean compareAndSet(Object oldVal, Object newVal) {
			return _state.compareAndSet(oldVal, newVal);
		}

		public Object reset(Object newVal) {
			_state.set(newVal);
			return newVal;
		}
	}
	
	public final class Basic extends Struct {
		public Basic(Object state) {
			super(state);
		}
	}

	public final class Standard extends Struct implements Swap {

		public Standard(Object init) {
			super(init);
			_validator = null;
		}
		
		public Standard(Object init, CFn validator) {
			super(init);
			_validator = validator;
		}

		final CFn _validator;
		final ConcurrentHashMap<Object, CFn> _watches = new ConcurrentHashMap<Object, CFn>();

		@Override
		public CFn getValidator() {
			return _validator;
		}
		
		public void addWatch(Object key, CFn f) {
			_watches.put(key, f);
		}
		
		public void removeWatch(Object key) {
			_watches.remove(key);
		}
		
		public Iterator<Map.Entry<Object, CFn>> getWatches() {
			return _watches.entrySet().iterator();
		}

	}
	
	public interface Swap extends I.Watch, I.Validate, I.Deref {
		
		boolean compareAndSet(Object oldVal, Object newVal);

		default Object swap(CFn f) {
			for (;;) {
				var v = deref();
				var newVal = f.invoke(v);
				validate(newVal);
				if (compareAndSet(v, newVal)) {
					notifyWatches(v, newVal);
					return newVal;
				}
			}
		}

		default Object swap(CFn f, Object arg) {
			for (;;) {
				var v = deref();
				var newVal = f.invoke(v, arg);
				validate(newVal);
				if (compareAndSet(v, newVal)) {
					notifyWatches(v, newVal);
					return newVal;
				}
			}
		}

		default Object swap(CFn f, Object arg1, Object arg2) {
			for (;;) {
				var v = deref();
				var newVal = f.invoke(v, arg1, arg2);
				validate(newVal);
				if (compareAndSet(v, newVal)) {
					notifyWatches(v, newVal);
					return newVal;
				}
			}
		}

		default Object swap(CFn f, Object x, Object y, Object z) {
			for (;;) {
				var v = deref();
				var newVal = f.invoke(v, x, y, z);
				validate(newVal);
				if (compareAndSet(v, newVal)) {
					notifyWatches(v, newVal);
					return newVal;
				}
			}
		}
	}
}


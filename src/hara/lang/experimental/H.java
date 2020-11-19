package hara.lang.experimental;

import java.util.Iterator;
import java.util.Map.Entry;

import hara.lang.base.*;

public interface H {

	public interface Map<R, E, $> extends IFn<Iterator<R>, IFn<R, E, $, $>, Iterable<E>, $> {
		
		@Override
		default Iterator<R> invoke(IFn<R, E, $, $> f, Iterable<E> c) {
			var it = c.iterator();
			return (Iterator<R>) Iter.from(it::hasNext, () -> f.invoke(it.next()));
		}

		public class Fn<R, E> implements Map<R, E, Object> {}
	}

	public interface Reduce<R, E, $> extends IFn<R, IFn<R, R, E, $>, Iterable<E>, $> {

		@Override
		default R invoke(IFn<R, R, E, $> f, Iterable<E> c) {
			R acc = f.invoke();
			Iterator<E> it = c.iterator();
			while (it.hasNext()) {
				acc = f.invoke(acc, it.next());
			}
			return acc;
		}

		public class Fn<R, E> implements Reduce<R, E, Object> {}
	}

	public interface Juxt<K, V, E, $>
			extends IFn<Iterator<Entry<K, V>>, IFn<K, E, $, $>, IFn<V, E, $, $>, Iterable<E>> {

		public static <K, V, E, $> IFn<Entry<K, V>, E, $, $> createFn(IFn<K, E, $, $> fk, IFn<V, E, $, $> fv) {
			return new IFn<Entry<K, V>, E, $, $>() {

				@Override
				public Entry<K, V> invoke(E e) {

					K k = fk.invoke(e);
					V v = fv.invoke(e);
					return new Entry<K, V>() {

						@Override
						public K getKey() {
							return k;
						}

						@Override
						public V getValue() {
							return v;
						}

						@Override
						public V setValue(V value) {
							throw new Ex.Unsupported();
						}
					};
				};
			};

		}

		@Override
		default Iterator<Entry<K, V>> invoke(IFn<K, E, $, $> fk, IFn<V, E, $, $> fv, Iterable<E> c) {
			var it = c.iterator();
			var f =  Juxt.createFn(fk, fv);
			return Iter.from(it::hasNext, () -> f.invoke(it.next()));
		}

		public class Fn<K, V, E> implements Juxt<K, V, E, Object> {}
	}
}

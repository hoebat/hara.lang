package hara.lang.data;

import java.util.Iterator;
import java.util.Map.Entry;

import hara.lang.base.*;

public interface OrderedMap<K, V> 
	extends Data.MapType<K, V>, 
			I.Nth<Entry<K, V>> {
	
	public interface Base<K, V> extends OrderedMap<K, V> {
		public Map<K, Entry<Integer, V>> _lookup();
		public Vector<Entry<K, V>> _order();
		
		@Override
		default Iterator<Entry<K, V>> iterator() {
			return It.keep(_order().iterator(), x -> x);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		default Entry<K, V> find(K key) {
			var rec = _lookup().find(key);
			return (rec != null) 
					? new Std.T.Tup2.L(null, key, rec.getValue().getValue())
					: null;
		}

		@Override
		default long count() {
			return _lookup().count();
		}

		@Override
		default Entry<K, V> nth(long i) {
			return It.nth(iterator(), i);
		}

	}

	@SuppressWarnings("unchecked")
	public class Mutable<K, V> extends Data.RefType.MT 
		implements Base<K, V>, I.ToPersistent {
		
		private Vector.Mutable<Entry<K, V>> _order;
		private Map.Mutable<K, Entry<Integer, V>> _lookup;

		public Mutable(I.Metadata meta) {
			this(meta, Vector.Mutable.empty(null), Map.Mutable.empty(null));
		}

		public Mutable(
				I.Metadata meta,  
				Vector.Mutable<Entry<K, V>> order,
				Map.Mutable<K, Entry<Integer, V>> lookup) {
			super(meta);
			_order = order;
			_lookup = lookup;
		}
		
		@SuppressWarnings("rawtypes")
		public static <K, V> Mutable<K, V> from(I.Metadata meta, Object... elements){
			return into(new Mutable<K, V>(null), (Iterator)It.partitionPair(Arr.toIter(elements)));
		}

		public static <K, V> Mutable<K, V> into(Iterator<Entry<K, V>> it) {
			return into(new Mutable<K, V>(null), it);
		}

		public static <K, V> Mutable<K, V> into(
				Mutable<K, V> map,
				Iterator<Entry<K, V>> it){
			It.reduce(
					it,
					map,
					(m, e) -> m.assoc(e.getKey(), e.getValue()));
			return map;
		}
		
		@Override
		public Mutable<K, V> empty() {
			_order.empty();
			_lookup.empty();
			return this;
		}
		
		@SuppressWarnings("rawtypes")
		@Override
		public Mutable<K, V> assoc(K k, V v) {
			var rec = _lookup.find(k);
			if(rec == null) {
				_order.conj(new Std.T.Tup2.L(null, k, v));
				_lookup.assoc(k, new Std.T.Tup2.L(null, (int)_order.count() - 1, v));
				return this;
			} else {
				if (v == rec.getValue().getValue()) {
					return this;
				} else {
					var idx = rec.getValue().getKey();
					_order.assoc(idx, new Std.T.Tup2.L(null, k, v));
					_lookup.assoc(k, new Std.T.Tup2.L(null, idx, v));
					return this;
				}
			}
			
		}
		@Override
		public Mutable<K, V> dissoc(K k) {
			var rec = _lookup.find(k);
			if (rec == null) {
				return this;
			} else {
				var idx = rec.getValue().getKey();
				_order.assoc(idx, null);
				_lookup.dissoc(k);

				// Amortized compaction
				if (_order.count() > 32 && _order.count() > 2 * _lookup.count()) {
					Vector.Mutable<Entry<K, V>> newOrder = Vector.Mutable.empty(null);
					Map.Mutable<K, Entry<Integer, V>> newLookup = Map.Mutable.empty(null);

					Iterator<Entry<K, V>> it = _order.iterator();
					int newIdx = 0;
					while (it.hasNext()) {
						Entry<K, V> entry = it.next();
						if (entry != null) {
							newOrder.conj(entry);
							newLookup.assoc(entry.getKey(), new Std.T.Tup2.L(null, newIdx, entry.getValue()));
							newIdx++;
						}
					}
					_order = newOrder;
					_lookup = newLookup;
				}
				return this;
			}
		}
		
		@Override
		public Standard<K, V> toPersistent() {
			return new Standard<K, V>(_meta, _order.toPersistent(), _lookup.toPersistent());
		}
		@Override
		public Map<K, Entry<Integer, V>> _lookup() {
			return _lookup;
		}
		@Override
		public Vector<Entry<K, V>> _order() {
			return _order;
		}
	}
	

	@SuppressWarnings("unchecked")
	public class Standard<K, V> extends Data.RefType.PT 
		implements Base<K, V>, I.ToMutable {
		private final Map.Standard<K, Entry<Integer, V>> _lookup;
		private final Vector.Standard<Entry<K, V>> _order;
		
		@SuppressWarnings("rawtypes")
		public static Standard EMPTY = new Standard(null);
		
		public Standard(I.Metadata meta) {
			this(meta, Vector.Standard.empty(null), Map.Standard.EMPTY);
		}

		public Standard(
				I.Metadata meta,  
				Vector.Standard<Entry<K, V>> order,
				Map.Standard<K, Entry<Integer, V>> lookup) {
			super(meta);
			_order = order;
			_lookup = lookup;
		}
		
		public static <K, V> Standard<K, V> from(I.Metadata meta, Object... elements){
			return (Standard<K, V>) Mutable.from(meta, elements).toPersistent();
		}
		
		public static <K, V> Standard<K, V> into(Iterator<Entry<K, V>> it) {
			return Mutable.into(it).toPersistent();
		}

		public static <K, V> Standard<K, V> into(Standard<K, V> map, Iterator<Entry<K, V>> it){
			return Mutable.into(map.toMutable(), it).toPersistent();
		}

		@Override
		public Standard<K, V> empty() {
			return (_meta == null) ? EMPTY : EMPTY.withMeta(_meta);
		}

		@Override
		public Standard<K, V> withMeta(I.Metadata meta) {
			return new Standard<K, V>(_meta, _order, _lookup);
		}


		@SuppressWarnings("rawtypes")
		@Override
		public Standard<K, V> assoc(K k, V v) {
			var rec = _lookup.find(k);
			if(rec == null) {
				var norder =  (Vector.Standard)_order.conj(new Std.T.Tup2.L(null, k, v));
				var nlookup = _lookup.assoc(k, new Std.T.Tup2.L(null, (int)norder.count() - 1, v));
				return new Standard<K, V>(_meta, norder, nlookup);
			} else {
				if (v == rec.getValue().getValue()) {
					return this;
				} else {
					var idx = rec.getValue().getKey();
					var norder = _order.assoc(idx, new Std.T.Tup2.L(null, k, v));
					var nlookup = _lookup.assoc(k, new Std.T.Tup2.L(null, idx, v));
					return new Standard<K, V>(_meta, norder, nlookup);
				}
			}
		}

		@Override
		public Standard<K, V> dissoc(K k) {
			var rec = _lookup.find(k);
			if (rec == null) {
				return this;
			} else {
				var idx = rec.getValue().getKey();
				var norder = _order.assoc(idx, null);
				var nlookup = _lookup.dissoc(k);

				// Amortized compaction
				if (norder.count() > 32 && norder.count() > 2 * nlookup.count()) {
					Vector.Mutable<Entry<K, V>> newOrder = Vector.Mutable.empty(null);
					Map.Mutable<K, Entry<Integer, V>> newLookup = Map.Mutable.empty(null);

					Iterator<Entry<K, V>> it = norder.iterator();
					int newIdx = 0;
					while (it.hasNext()) {
						Entry<K, V> entry = it.next();
						if (entry != null) {
							newOrder.conj(entry);
							newLookup.assoc(entry.getKey(), new Std.T.Tup2.L(null, newIdx, entry.getValue()));
							newIdx++;
						}
					}
					return new Standard<K, V>(_meta, newOrder.toPersistent(), newLookup.toPersistent());
				}
				return new Standard<K, V>(_meta, norder, nlookup);
			}
		}

		@Override
		public Map<K, Entry<Integer, V>> _lookup() {
			return _lookup;
		}

		@Override
		public Vector<Entry<K, V>> _order() {
			return _order;
		}

		@Override
		public Mutable<K, V> toMutable() {
			return new Mutable<K, V>(_meta, 
					_order.toMutable(),
					_lookup.toMutable());
		}
	}
}

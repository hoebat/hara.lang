package hara.lang.data;

import java.util.Iterator;

import hara.lang.base.*;

public interface OrderedSet<E> 
	extends Data.SetType<E>, 
			I.Nth<E> {
	
	public interface Base<E> extends OrderedSet<E> {
		public Map<E, Integer> _lookup();
		public Vector<E> _order();
		
		@Override
		default Iterator<E> iterator() {
			return It.keep(_order().iterator(), x -> x);
		}

		@Override
		default E find(E e) {
			var rec = _lookup().find(e);
			return (rec != null) ? e : null;
		}

		@Override
		default long count() {
			return _lookup().count();
		}

		@Override
		default E nth(long i) {
			return It.nth(iterator(), i);
		}
	}

	public class Mutable<E> extends Data.RefType.MT 
		implements Base<E>, I.ToPersistent {
		
		private Vector.Mutable<E> _order;
		
		private Map.Mutable<E, Integer> _lookup;
		
		@SuppressWarnings("unchecked")
		public Mutable(I.Metadata meta) {
			this(meta, Vector.Mutable.empty(null), Map.Mutable.empty(null));
		}

		
		@Override
		public Mutable<E> empty() {
			_order.empty();
			_lookup.empty();
			return this;
		}

		public Mutable(
				I.Metadata meta,  
				Vector.Mutable<E> order,
				Map.Mutable<E, Integer> lookup) {
			super(meta);
			_order = order;
			_lookup = lookup;
		}


		@SuppressWarnings("unchecked")
		public static <E> Mutable<E> from(I.Metadata meta, E... objs) {
			Mutable<E> mut = new Mutable<E>(meta);
			return Arr.reduce((arr, e) -> arr.conj(e) , mut, objs);
		}

		public static <E> Mutable<E> into(Iterator<E> it) {
			return into(new Mutable<E>(null), it);
		}
		
		public static <E> Mutable<E> into(Mutable<E> coll, Iterator<E> it) {
			return It.reduce(it, coll, (m, e) -> m.conj(e));
		}
		
		@Override
		public Mutable<E> conj(E e) {
			if (e == null) {
				throw new Ex.Unsupported();
			}
			var rec = _lookup.find(e);
			if(rec == null) {
				_order.conj(e);
				_lookup.assoc(e, (int)_order.count() - 1);
				return this;
			} else {
				return this;
			}
			
		}
		@Override
		public Mutable<E> dissoc(E e) {
			var rec = _lookup.find(e);
			if(rec == null) { 
				return this; 
			} else {
				var idx = rec.getValue();
				_order.assoc(idx, null);
				_lookup.dissoc(e);
				return this;
			}
		}
		
		@Override
		public Standard<E> toPersistent() {
			return new Standard<E>(_meta, _order.toPersistent(), _lookup.toPersistent());
		}
		@Override
		public Map<E, Integer> _lookup() {
			return _lookup;
		}
		@Override
		public Vector<E> _order() {
			return _order;
		}
	}
	

	public class Standard<E> extends Data.RefType.PT 
		implements Base<E>, I.ToMutable {
		
		private final Map.Standard<E, Integer> _lookup;
		private final Vector.Standard<E> _order;
		
		@SuppressWarnings("rawtypes")
		public static Standard EMPTY = new Standard(null);
		
		@SuppressWarnings("unchecked")
		public Standard(I.Metadata meta) {
			this(meta, Vector.Standard.empty(null), Map.Standard.EMPTY);
		}

		public Standard(
				I.Metadata meta,  
				Vector.Standard<E> order,
				Map.Standard<E, Integer> lookup) {
			super(meta);
			_order = order;
			_lookup = lookup;
		}

		@SuppressWarnings("unchecked")
		public static <E> Standard<E> from(I.Metadata meta, E... elements){
			return Mutable.from(meta, elements).toPersistent();
		}

		public static <E> Standard<E> into(Iterator<E> it) {
			return Mutable.into(it).toPersistent();
		}
		
		public static <E> Standard<E> into(Standard<E> coll, Iterator<E> it) {
			return Mutable.into(coll.toMutable(), it).toPersistent();
		}

		@SuppressWarnings("unchecked")
		@Override
		public Standard<E> empty() {
			return (_meta == null) ? EMPTY : EMPTY.withMeta(_meta);
		}

		@Override
		public Standard<E> withMeta(I.Metadata meta) {
			return new Standard<E>(_meta, _order, _lookup);
		}


		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public Standard<E> conj(E e) {
			if (e == null) {
				throw new Ex.Unsupported();
			}
			var rec = _lookup.find(e);
			if(rec == null) {
				var norder =  (Vector.Standard)_order.conj(e);
				var nlookup = _lookup.assoc(e, (int)norder.count() - 1);
				return new Standard<E>(_meta, norder, nlookup);
			} else {
				return this;
			}
		}

		@Override
		public Standard<E> dissoc(E e) {
			var rec = _lookup.find(e);
			if(rec == null) { 
				return this; 
			} else {
				var idx = rec.getValue();
				var norder = _order.assoc((int)idx, null);
				var nlookup = _lookup.dissoc(e);
				return new Standard<E>(_meta, norder, nlookup);
			}
		}

		@Override
		public Map<E, Integer> _lookup() {
			return _lookup;
		}

		@Override
		public Vector<E> _order() {
			return _order;
		}

		@Override
		public Mutable<E> toMutable() {
			return new Mutable<E>(_meta, 
					_order.toMutable(),
					_lookup.toMutable());
		}

	}
}
package hara.lang.base;

public interface Cons<E> extends
	Coll.SeqType<E>,
 	I.ObjType {

	public static Empty<Object> EMPTY = new Empty<>(null);
	
	@SuppressWarnings("unchecked")
	public static <E> Cons<E> empty(I.Metadata meta) {
		return (Empty<E>) (meta == null ? EMPTY : EMPTY.withMeta(meta));
	}

	public final class Empty<E> extends Obj.EMPTY<E> implements Cons<E>{

		public Empty(I.Metadata meta) {
			super(meta);
		}

		@Override
		public Cons<E> withMeta(I.Metadata meta) {
			return (meta() == meta) 
				? this 
				: new Empty<E>(meta);
		}
		
		@Override
		public Cons<E> cons(E e) {
			return new Standard<E>(_meta, e, null);
		}
	}

	public class Standard<E> extends Obj.SEQ<E> implements Cons<E> {

		private final E _first;
		private final I.Seq<E> _more;

		public Standard(I.Metadata meta, E first, I.Seq<E> more) {
			super(meta);
			_first = first;
			_more = more;
		}

		@Override
		public final Standard<E> withMeta(I.Metadata meta) {
			return (meta() == meta) ? this : new Standard<E>(meta, _first, _more);
		}

		//
		// I.Seq
		//

		public Standard<E> cons(E e) {
			return new Cons.Standard<E>(null, e, this);
		}
		
		@Override
		public final E first() {
			return _first;
		}

		@SuppressWarnings("unchecked")
		@Override
		public final I.Seq<E> restMore() {
			return (_more != null) ? _more : (I.Seq<E>) EMPTY;
		}

		@Override
		public final boolean restEnd() {
			return _more == null;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public Empty<E> empty() {
			return (Empty<E>) Cons.empty(_meta);
		}

		@Override
		public long count() {
			return 1 + ((_more == null) ? 0 : _more.count());
		}
	}
}

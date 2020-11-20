package hara.lang.base;

import java.util.Iterator;


public interface Tup {

	public final static Tup0 EMPTY = new Tup0(null);

	public interface Fn {

		public static Tup0 empty(I.Metadata meta) {
			return (meta == null) ? EMPTY : new Tup0(meta);
		}

	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public class Tup0 extends Obj.EMPTY implements I.SequentialType, Coll.SeqType {

		public Tup0(I.Metadata meta) {
			super(meta);
		}

		@Override
		public Tup0 withMeta(I.Metadata meta) {
			return (_meta == meta) ? this : new Tup0(meta);
		}

		@Override
		public Tup1 cons(Object e) {
			return new Tup1.L(_meta, e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public interface Tup1<A> extends I.SequentialType, Coll.SeqType, I.Cons {

		@Override
		default long count() {
			return 1;
		}
		
		A A();

		public class L<A> extends Obj.SEQ implements Tup1<A> {

			final A _a;

			public A A() {
				return _a;
			}

			public L(I.Metadata meta, A a) {
				super(meta);
				_a = a;
			}

			@Override
			public Iterator iterator() {
				return Iter.objects(_a);
			}

			@Override
			public I.ObjType withMeta(I.Metadata meta) {
				return (_meta == meta) ? this : new L<A>(meta, _a);
			}

			@Override
			public Object nth(long i) {
				switch ((int) i) {
				case 0:
					return _a;
				default:
					throw new Ex.NoSuchElement();
				}
			}
			
			@Override 
			public Tup2.L cons(Object e) {
				return new Tup2.L(_meta, e, _a);
			}
			
			@Override 
			public Tup2.L conj(Object e) {
				return new Tup2.L(_meta, _a, e);
			}

			@Override
			public Object first() {
				return _a;
			}

			@Override
			public boolean restEnd() {
				return false;
			}

			@Override
			public I.Seq restMore() {
				return Fn.empty(_meta);
			}
		}
	}
	
	public static <K, V>  Tup2.L<K, V> pair(K key, V val) {
		return new Tup2.L<K, V>(null, key, val);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public interface Tup2<A, B> extends Tup1<A>, I.SequentialType, Coll.SeqType, I.Cons {

		@Override
		default long count() {
			return 2;
		}

		B B();

		public class L<A, B>  extends Obj.SEQ implements Tup2<A, B>, I.Pair<A, B> {
			A _a;
			B _b;

			L(I.Metadata meta, A a, B b) {
				super(meta);
				_a = a;
				_b = b;
			}

			@Override
			public A A() {
				return _a;
			}

			@Override
			public B B() {
				return _b;
			}

			@Override
			public A getKey() {
				return _a;
			}

			@Override
			public B getValue() {
				return _b;
			}

			@Override
			public Iterator iterator() {
				return Iter.objects(_a, _b);
			}


			@Override
			public I.ObjType withMeta(I.Metadata meta) {
				return (_meta == meta) ? this : new L<A, B>(meta, _a, _b);
			}

			@Override
			public Object nth(long i) {
				switch ((int) i) {
				case 0: return _a;
				case 1: return _b;
				default: throw new Ex.NoSuchElement();
				}
			}

			
			@Override 
			public Tup3.L cons(Object e) {
				return new Tup3.L(_meta, e, _a, _b);
			}
			
			@Override 
			public Tup3.L conj(Object e) {
				return new Tup3.L(_meta, _a, _b, e);
			}

			@Override
			public Object first() {
				return _a;
			}

			@Override
			public boolean restEnd() {
				return false;
			}

			@Override
			public I.Seq restMore() {
				return new Tup1.L(_meta, _b);
			}
		}
	}


	@SuppressWarnings({"unchecked", "rawtypes"})
	public interface Tup3<A, B, X> extends Tup2<A, B>, I.SequentialType, Coll.SeqType, I.Cons {

		default long count() {
			return 3;
		}

		public X C();

		public class L<A, B, X> extends Obj.SEQ implements Tup3<A, B, X> {
			A _a;
			B _b;
			X _c;

			L(I.Metadata meta, A a, B b, X c) {
				super(meta);
				_a = a;
				_b = b;
				_c = c;
			}

			@Override
			public A A() {
				return _a;
			}

			@Override
			public B B() {
				return _b;
			}

			@Override
			public X C() {
				return _c;
			}

			@Override
			public Iterator iterator() {
				return Iter.objects(_a, _b, _c);
			}


			@Override
			public I.ObjType withMeta(I.Metadata meta) {
				return (_meta == meta) ? this : new L<A, B, X>(meta, _a, _b, _c);
			}

			@Override
			public Object nth(long i) {
				switch ((int) i) {
				case 0: return _a;
				case 1: return _b;
				case 2: return _c;
				default: throw new Ex.NoSuchElement();
				}
			}
			
			@Override 
			public Tup4.L cons(Object e) {
				return new Tup4.L(_meta, e, _a, _b, _c);
			}
			
			@Override 
			public Tup4.L conj(Object e) {
				return new Tup4.L(_meta, _a, _b, _c, e);
			}

			@Override
			public Object first() {
				return _a;
			}

			@Override
			public boolean restEnd() {
				return false;
			}

			@Override
			public I.Seq restMore() {
				return new Tup2.L(_meta, _b, _c);
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public interface Tup4<A, B, X, Y> extends Tup3<A, B, X>, I.SequentialType, Coll.SeqType, I.Cons {

		default long count() {
			return 4;
		}

		public Y D();

		public class L<A, B, X, Y> extends Obj.SEQ implements Tup4<A, B, X, Y> {
			A _a;
			B _b;
			X _c;
			Y _d;

			L(I.Metadata meta, A a, B b, X c, Y d) {
				_a = a;
				_b = b;
				_c = c;
				_d = d;
			}

			@Override
			public A A() {
				return _a;
			}

			@Override
			public B B() {
				return _b;
			}

			@Override
			public X C() {
				return _c;
			}

			@Override
			public Y D() {
				return _d;
			}

			@Override
			public Iterator iterator() {
				return Iter.objects(_a, _b, _c, _d);
			}


			@Override
			public I.ObjType withMeta(I.Metadata meta) {
				return (_meta == meta) ? this : new L<A, B, X, Y>(meta, _a, _b, _c, _d);
			}

			@Override
			public Object nth(long i) {
				switch ((int) i) {
				case 0: return _a;
				case 1: return _b;
				case 2: return _c;
				default: throw new Ex.NoSuchElement();
				}
			}
			
			@Override 
			public Cons.Standard cons(Object e) {
				throw new Ex.TODO();
				//return Cons.empty(meta) (_meta, e, _a, _b, _c);
			}
			
			@Override 
			public Obj.SEQ conj(Object e) {
				throw new Ex.TODO();
			}

			@Override
			public Object first() {
				return _a;
			}

			@Override
			public boolean restEnd() {
				return false;
			}

			@Override
			public Tup3.L restMore() {
				return new Tup3.L(_meta, _b, _c, _d);
			}
		}
	}
}

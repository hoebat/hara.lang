package hara.lang.base;

import java.util.Iterator;

public interface Std {

	@SuppressWarnings("unchecked")
	public static <E> Data.SeqType<E> empty(I.Metadata meta) {
		return (Data.SeqType<E> ) (meta == null ? T.Cons.EMPTY : T.Cons.EMPTY.withMeta(meta));
	}
	
	public static <K, V>  T.Tup2.L<K, V> pair(K key, V val) {
		return new T.Tup2.L<K, V>(null, key, val);
	}
	
	public static  T.Tup0 tuple(I.Metadata meta) {
		return new T.Tup0(meta);
	}
	
	public static  <A> T.Tup1<A> tuple(I.Metadata meta, A a) {
		return new T.Tup1.L<A>(meta, a);
	}
	
	public static  <A, B> T.Tup2<A, B> tuple(I.Metadata meta, A a, B b) {
		return new T.Tup2.L<A, B>(meta, a, b);
	}
	
	public static  <A, B, C> T.Tup3<A, B, C> tuple(I.Metadata meta, A a, B b, C c) {
		return new T.Tup3.L<A, B, C>(meta, a, b, c);
	}
	
	public static  <A, B, C, D> T.Tup4<A, B, C, D> tuple(I.Metadata meta, A a, B b, C c, D d) {
		return new T.Tup4.L<A, B, C, D>(meta, a, b, c, d);
	}
	
	@SuppressWarnings("unchecked")
	public static <E> I.Seq<E> cons(E e, Object seq) {
		if(seq instanceof I.Cons<?>) {
			return (I.Seq<E>) ((I.Cons<E>) seq).cons(e);
		} else if (seq instanceof I.Seq<?>) {
			return new T.Cons<E>(null, e, (I.Seq<E>) seq);
		}
		throw new Ex.Unsupported();
	}
	
	public interface T {

		public static Tup0 empty(I.Metadata meta) {
			return (meta == null) ? Tup0.EMPTY : new Tup0(meta);
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		public class Tup0 extends Obj.EMPTY implements Data.SequentialType, Data.SeqType {

			public final static Tup0 EMPTY = new Tup0(null);
			
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
		public interface Tup1<A> extends Data.SequentialType, Data.SeqType, I.Cons {

			@Override
			default long count() {
				return 1;
			}
			
			A A();

			public class L<A> extends Obj.SEQ implements Tup1<A> {

				final A _a;

				@Override
				public A A() {
					return _a;
				}

				public L(I.Metadata meta, A a) {
					super(meta);
					_a = a;
				}

				@Override
				public Iterator iterator() {
					return It.objects(_a);
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
					return Std.tuple(_meta);
				}
			}
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		public interface Tup2<A, B> extends Tup1<A>, Data.SequentialType, Data.SeqType, I.Cons {

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
					return It.objects(_a, _b);
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
		public interface Tup3<A, B, X> extends Tup2<A, B>, Data.SequentialType, Data.SeqType, I.Cons {

			@Override
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
					return It.objects(_a, _b, _c);
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
		public interface Tup4<A, B, X, Y> extends Tup3<A, B, X>, Data.SequentialType, Data.SeqType, I.Cons {

			@Override
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
					return It.objects(_a, _b, _c, _d);
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
				public Cons cons(Object e) {
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

		public class Cons<E> extends Obj.SEQ<E> implements 
			Data.SeqType<E>,
			I.ObjType {
			
			public static Cons0<Object> EMPTY = new Cons0<>(null);
		
			private final E _first;
			private final I.Seq<E> _more;
		
			public Cons(I.Metadata meta, E first, I.Seq<E> more) {
				super(meta);
				_first = first;
				_more = more;
			}
		
			@Override
			public final Cons<E> withMeta(I.Metadata meta) {
				return (meta() == meta) ? this : new Cons<E>(meta, _first, _more);
			}
		
			//
			// I.Seq
			//
		
			@Override
			public Cons<E> cons(E e) {
				return new Cons<E>(null, e, this);
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
			public Cons0<E> empty() {
				return (Cons0<E>) Std.empty(_meta);
			}
		
			@Override
			public long count() {
				return 1 + ((_more == null) ? 0 : _more.count());
			}
		}

		public final class Cons0<E> extends Obj.EMPTY<E> implements 
			Data.SeqType<E>,
			I.ObjType {
		
			public Cons0(I.Metadata meta) {
				super(meta);
			}
		
			@Override
			public Cons0<E> withMeta(I.Metadata meta) {
				return (meta() == meta) 
					? this 
					: new Cons0<E>(meta);
			}
			
			@Override
			public Cons<E> cons(E e) {
				return new Cons<E>(_meta, e, null);
			}
		}
	}
}

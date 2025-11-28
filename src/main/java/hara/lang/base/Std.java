package hara.lang.base;

import hara.lang.protocol.*;
import java.util.Iterator;

import hara.lang.data.*;

public interface Std {
	
	public interface T {

		@SuppressWarnings({"unchecked", "rawtypes"})
		public class Tup0 extends Obj.EMPTY implements Data.SequentialType, Data.LinearType {

			public final static Tup0 EMPTY = new Tup0(null);
			
			public Tup0(IMetadata meta) {
				super(meta);
			}

			@Override
			public Tup0 withMeta(IMetadata meta) {
				return (_meta == meta) ? this : new Tup0(meta);
			}
			
			@Override
			public Tup1.L pushFirst(Object e) {
				return new Tup1.L(_meta, e);
			}

			@Override
			public Tup1.L pushLast(Object e) {
				return new Tup1.L(_meta, e);
			}

			@Override
			public Tup0 popFirst() {
				return this;
			}

			@Override
			public Tup0 popLast() {
				return this;
			}

			@Override
			public Object peekFirst() {
				return null;
			}

			@Override
			public Object peekLast() {
				return null;
			}
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		public interface Tup1<A> extends 
			Data.SequentialType, 
			Data.LinearType {

			@Override
			default long count() {
				return 1;
			}

			@Override
			default Tup0 empty() {
				return Tup0.EMPTY.withMeta(meta());
			}
			
			A A();

			public class L<A> extends Obj.PT implements Tup1<A> {

				final A _a;

				@Override
				public A A() {
					return _a;
				}

				public L(IMetadata meta, A a) {
					super(meta);
					_a = a;
				}

				@Override
				public Iterator iterator() {
					return It.objects(_a);
				}

				@Override
				public IObjType withMeta(IMetadata meta) {
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
				public Tup2 pushFirst(Object e) {
					return new Tup2.L(_meta, e, _a);
				}

				@Override
				public Tup2 pushLast(Object e) {
					return new Tup2.L(_meta, _a, e);
				}

				@Override
				public Tup0 popFirst() {
					return Tup0.EMPTY.withMeta(_meta);
				}

				@Override
				public Tup0 popLast() {
					return Tup0.EMPTY.withMeta(_meta);
				}

				@Override
				public Object peekFirst() {
					return _a;
				}

				@Override
				public Object peekLast() {
					return _a;
				}
			}
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		public interface Tup2<A, B> extends Tup1<A>, Data.SequentialType, Data.LinearType {

			@Override
			default long count() {
				return 2;
			}

			B B();

			public class L<A, B>  extends Obj.PT 
				implements Tup2<A, B>, 
						  IPair<A, B> {
				A _a;
				B _b;

				public L(IMetadata meta, A a, B b) {
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
				public IObjType withMeta(IMetadata meta) {
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
				public Tup3 pushFirst(Object e) {
					return new Tup3.L(_meta, e, _a, _b);
				}
				
				@Override 
				public Tup3 pushLast(Object e) {
					return new Tup3.L(_meta, _a, _b, e);
				}

				@Override
				public Tup1 popFirst() {
					return new Tup1.L(_meta, _b);
				}

				@Override
				public Tup1 popLast() {
					return new Tup1.L(_meta, _a);
				}

				@Override
				public Object peekFirst() {
					return _a;
				}

				@Override
				public Object peekLast() {
					return _b;
				}
			}
		}


		@SuppressWarnings({"unchecked", "rawtypes"})
		public interface Tup3<A, B, X> extends Tup2<A, B>, Data.SequentialType, Data.LinearType {

			@Override
			default long count() {
				return 3;
			}

			public X C();

			public class L<A, B, C> extends Obj.PT implements Tup3<A, B, C> {
				A _a;
				B _b;
				C _c;

				public L(IMetadata meta, A a, B b, C c) {
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
				public C C() {
					return _c;
				}

				@Override
				public Iterator iterator() {
					return It.objects(_a, _b, _c);
				}


				@Override
				public IObjType withMeta(IMetadata meta) {
					return (_meta == meta) ? this : new L<A, B, C>(meta, _a, _b, _c);
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
				public Tup4 pushFirst(Object e) {
					return new Tup4.L(_meta, e, _a, _b, _c);
				}
				
				@Override 
				public Tup4 pushLast(Object e) {
					return new Tup4.L(_meta, _a, _b, _c, e);
				}

				@Override
				public Tup2 popFirst() {
					return new Tup2.L(_meta, _b, _c);
				}

				@Override
				public Tup2 popLast() {
					return new Tup2.L(_meta, _a, _b);
				}

				@Override
				public Object peekFirst() {
					return _a;
				}

				@Override
				public Object peekLast() {
					return _c;
				}
			}
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		public interface Tup4<A, B, C, D> extends Tup3<A, B, C>, Data.SequentialType, Data.LinearType {

			@Override
			default long count() {
				return 4;
			}

			public D D();

			public class L<A, B, C, D> extends Obj.PT implements Tup4<A, B, C, D> {
				A _a;
				B _b;
				C _c;
				D _d;

				public L(IMetadata meta, A a, B b, C c, D d) {
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
				public C C() {
					return _c;
				}

				@Override
				public D D() {
					return _d;
				}

				@Override
				public Iterator iterator() {
					return It.objects(_a, _b, _c, _d);
				}


				@Override
				public IObjType withMeta(IMetadata meta) {
					return (_meta == meta) ? this : new L<A, B, C, D>(meta, _a, _b, _c, _d);
				}

				@Override
				public Object nth(long i) {
					switch ((int) i) {
					case 0: return _a;
					case 1: return _b;
					case 2: return _c;
					case 3: return _d;
					default: throw new Ex.NoSuchElement();
					}
				}
				
				@Override
				public Tup5 pushFirst(Object e) {
					return new Tup5.L(_meta, e, _a, _b, _c, _d);
				}

				@Override
				public Tup5 pushLast(Object e) {
					return new Tup5.L(_meta, _a, _b, _c, _d, e);
				}

				@Override
				public Tup3 popFirst() {
					return new Tup3.L(_meta, _b, _c, _d);
				}

				@Override
				public Tup3 popLast() {
					return new Tup3.L(_meta, _a, _b, _c);
				}

				@Override
				public Object peekFirst() {
					return _a;
				}

				@Override
				public Object peekLast() {
					return _d;
				}
			}
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		public interface Tup5<A, B, C, D, E> extends Tup4<A, B, C, D>, Data.SequentialType, Data.LinearType {

			@Override
			default long count() {
				return 5;
			}

			public E E();

			public class L<A, B, X, Y, Z> extends Obj.PT implements Tup5<A, B, X, Y, Z> {
				A _a;
				B _b;
				X _c;
				Y _d;
				Z _e;

				public L(IMetadata meta, A a, B b, X c, Y d, Z e) {
					_a = a;
					_b = b;
					_c = c;
					_d = d;
					_e = e;
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
				public Z E() {
					return _e;
				}

				@Override
				public Iterator iterator() {
					return It.objects(_a, _b, _c, _d, _e);
				}


				@Override
				public IObjType withMeta(IMetadata meta) {
					return (_meta == meta) ? this : new L<A, B, X, Y, Z>(meta, _a, _b, _c, _d, _e);
				}

				@Override
				public Object nth(long i) {
					switch ((int) i) {
					case 0: return _a;
					case 1: return _b;
					case 2: return _c;
					case 3: return _d;
					case 4: return _e;
					default: throw new Ex.NoSuchElement();
					}
				}
				
				@Override
				public Data.LinearType pushFirst(Object x) {
					return List.Standard.from(_meta, x, _a, _b, _c, _d, _e);
				}

				@Override
				public Data.LinearType pushLast(Object x) {
					return Vector.Standard.from(_meta, _a, _b, _c, _d, _e, x);
				}

				@Override
				public Tup4 popFirst() {
					return new Tup4.L(_meta, _b, _c, _d, _e);
				}

				@Override
				public Tup4 popLast() {
					return new Tup4.L(_meta, _a, _b, _c, _d);
				}

				@Override
				public Object peekFirst() {
					return _a;
				}

				@Override
				public Object peekLast() {
					return _e;
				}
			}
		}
		
		public class Seq<E> extends Obj.PT 
			implements Data.SequentialType<E>, Data.LinkedType<E> {

			final Iterator<E> _iter;
			final State<E> _state;

			static class State<V> {
				volatile V _val;
				volatile V _rest;
			}

			@SuppressWarnings("unchecked")
			public Seq(Iterator<E> iter) {
				_iter = iter;
				_state = new State<E>();
				_state._val = (E) _state;
				_state._rest = (E) _state;
			}

			public Seq(IMetadata meta, Iterator<E> iter, State<E> state) {
				super(meta);
				_iter = iter;
				_state = state;
			}

			@Override
			public E peekFirst() {
				if (_state._val == _state)
					synchronized (_state) {
						if (_state._val == _state)
							_state._val = _iter.next();
					}
				return _state._val;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Seq<E> popFirst() {
				if (_state._rest == _state) {
					synchronized (_state) {
						if (_state._rest == _state) {
							peekFirst();
							_state._rest = _iter.hasNext() ? (E) (new Seq<E>(_iter)) : null;
						}
					}
				}
				return (Seq<E>) _state._rest;
			}

			@Override
			public Iterator<E> iterator() {
				return _iter;
			}

			@Override
			public long count() {
				return 1 + popFirst().count();
			}

			@Override
			public Seq<E> withMeta(IMetadata meta) {
				return new Seq<E>(meta, _iter, _state);
			}

			@Override
			public Cons<E> pushFirst(E e) {
				return new Cons<E>(_meta, e, this);
			}

			@Override
			public Tup0 empty() {
				return Tup0.EMPTY.withMeta(_meta);
			}
		}

		public class Cons<E> extends Obj.PT implements 
			Data.SequentialType<E>,
			Data.LinkedType<E> {
		
			private final E _first;
			private final Data.LinkedType<E> _more;
		
			public Cons(IMetadata meta, E first, Data.LinkedType<E> more) {
				super(meta);
				_first = first;
				_more = more;
			}
		
			@Override
			public final Cons<E> withMeta(IMetadata meta) {
				return (meta() == meta) ? this : new Cons<E>(meta, _first, _more);
			}
		
			//
			// ISeq
			//
		
			@Override
			public Cons<E> cons(E e) {
				return new Cons<E>(null, e, this);
			}
			
			@Override
			public final E peekFirst() {
				return _first;
			}
		
			@Override
			public final Data.LinkedType<E> popFirst() {
				return _more;
			}
		
			@Override
			public long count() {
				return 1 + ((_more == null) ? 0 : _more.count());
			}

			@Override
			public Iterator<E> iterator() {
				throw new Ex.TODO();
			}
			
			@Override
			public Cons<E> pushFirst(E e) {
				return new Cons<E>(_meta, e, this);
			}

			@Override
			public Tup0 empty() {
				return Tup0.EMPTY.withMeta(_meta);
			}
		}
	}
}

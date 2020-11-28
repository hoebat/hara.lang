package hara.lang.data;

import java.util.*;

import hara.lang.base.*;

import static hara.lang.base.P.Bits.log2Ceil;

public interface List<E> extends Data.VectorType<E> {

	public static int DEFAULT_CAPACITY = 4;

	public interface S {

		public static Object[] resize(Object[] oldArr, int size, int offset, int newCapacity) {

			Object[] newArr = new Object[newCapacity];

			int truncatedSize = Math.min(size, oldArr.length - offset);
			System.arraycopy(oldArr, offset, newArr, 0, truncatedSize);
			if (size != truncatedSize) {
				System.arraycopy(oldArr, 0, newArr, truncatedSize, size - truncatedSize);
			}

			return newArr;
		}
		
		public static Object[] clone(Object[] elements) {
			return Arrays.copyOf(elements, elements.length);
		}
		
		public static Object[] addAt(Object[] elements, int offset, int mask, int i, Object val) {
			elements[offset + i & mask] = val;
			return elements;
		}

		public static Object[] addLast(Object[] elements, int size, int offset, int mask, Object val, Ut.Flag resized) {
			Object[] out = elements;
			if (size == elements.length) {
				out = resize(elements, size, offset, size << 1);
				resized.reset(true);
				out[size++] = val;
			} else {
				out[(offset + size++) & mask] = val;
			}
			return out;
		}

		public static Object[] addFirst(Object[] elements, int size, int offset, int mask, Object val, Ut.Flag resized) {
			Object[] out = elements;
			if(size == elements.length) {
				out = resize(elements, size, offset, size << 1);
				resized.reset(true);
				out[out.length - 1] = val;
			} else {
				out[(offset - 1 ) & mask] = val;
			}
			return out;
		}

	}

	public interface Base<E> extends List<E>, I.Assoc<Integer, E>, I.ObjType {

		public Object[] _elements();

		public int _size();

		public int _mask();

		public int _offset();

		@Override
		default Iterator<E> iterator() {

			return new Iterator<E>() {

				final int limit = _offset() + _size();
				int idx = _offset();

				@Override
				public boolean hasNext() {
					return idx != limit;
				}

				@Override
				public E next() {
					if (idx == limit) {
						throw new NoSuchElementException();
					}

					@SuppressWarnings("unchecked")
					E val = (E) _elements()[idx++ & _mask()];
					return val;
				}
			};
		}
		
		@Override
		default long count() {
			return _size();
		}

		@SuppressWarnings("unchecked")
		@Override
		default E nth(long i) {
			if (i < 0 || i >= _size()) {
				throw new IndexOutOfBoundsException(i + " must be within [0," + _size() + ")");
			}
			return (E) _elements()[(_offset() + (int) i) & _mask()];
		}

		@Override
		default String startString() {
			return "(";
		}

		@Override
		default String endString() {
			return ")";
		}
	}

	public class Mutable<E> extends Data.RefType.MT implements Base<E>, I.ToPersistent {

		private Object[] _elements;
		private int _mask;
		private int _size, _offset;
		
		public Mutable() {
			this(null, List.DEFAULT_CAPACITY);
		}
		
		public Mutable(I.Metadata meta, int capacity) {
			super(meta);
			_elements = new Object[Math.max(1, 1 << log2Ceil(capacity))];
			_size = 0;
			_offset = 0;
			_mask = _elements.length - 1;
		}

		public Mutable(I.Metadata meta, Object[] elements, int size, int offset, int mask) {
			super(meta);
			_elements = elements;
			_size = size;
			_mask = mask;
			_offset = offset;
		}

		@Override
		public Mutable<E> empty() {
			Arrays.fill(_elements, null);
			_offset = 0;
			_size = 0;
			return this;
		}

		@SuppressWarnings("unchecked")
		public static <E> Mutable<E> from(I.Metadata meta, E... objs) {
			Mutable<E> mut = new Mutable<E>(meta, objs.length);
			return Arr.reduce((arr, e) -> arr.pushLast(e) , mut, objs);
		}

		public static <E> Mutable<E> into(Iterator<E> it) {
			return into(new Mutable<E>(), it);
		}
		
		public static <E> Mutable<E> into(Mutable<E> coll, Iterator<E> it) {
			return It.reduce(it, coll, (m, e) -> m.conj(e));
		}
		
		@Override
		public Mutable<E> pushFirst(E e) {
			var resized = new Ut.Flag(false);
			_elements =  S.addFirst(_elements, _size, _offset, _mask, e, resized);
			_size++;
			
			if(resized.deref()) {
				_mask = _elements.length - 1;
				_offset = _mask;
			} else {
				_offset = (_offset - 1) & _mask;
			}
			return this;
		}

		@Override
		public Mutable<E> pushLast(E e) {
			var resized = new Ut.Flag(false);
			_elements =  S.addLast(_elements, _size, _offset, _mask, e, resized);
			_size++;
			
			if(resized.deref()) {
				_mask = _elements.length - 1;
				_offset = 0;
			} 
			return this;
		}

		@Override
		public Mutable<E> popFirst() {
			if(_size==0) { return this; }
			_elements[_offset] = null;
			_offset = (_offset + 1) & _mask;
			_size--;
			return this;
		}

		@Override
		public Mutable<E> popLast() {
			if(_size==0) { return this; }
			_elements[(_offset + _size - 1) & _mask] = null;
			
			_size--;
			return this;
		}

		@Override
		public Mutable<E> assoc(Integer idx, E v) {
			if (idx == _size) {
			   return pushLast(v);
			} else if (idx > Integer.MAX_VALUE) {
				throw new IndexOutOfBoundsException();
		    } else {
		    	_elements = S.addAt(_elements, _offset, _mask, idx, v);
		    }
			return this;
		}

		@Override
		public Standard<E> toPersistent() {
			return new Standard<E>(_meta, _elements.clone(), _size, _offset, _mask);
		}

		public Standard<E> toPersistentRaw() {
			return new Standard<E>(_meta, _elements, _size, _offset, _mask);
		}

		@Override
		public Mutable<E> cons(E v){
			return pushFirst(v);
		}
		
		@Override
		public Mutable<E> conj(E v){
			return pushLast(v);
		}

		@Override
		public Object[] _elements() {
			return _elements;
		}

		@Override
		public int _size() {
			return _size;
		}

		@Override
		public int _mask() {
			return _mask;
		}

		@Override
		public int _offset() {
			return _offset;
		}
	}

	@SuppressWarnings("rawtypes")
	public class Standard<E> extends Data.RefType.PT implements Base<E>, I.ToMutable {

		final Object[] _elements;
		final int _mask;
		final int _size, _offset;
		
		public static Standard EMPTY = new Standard(null, new Object[] {null, null, null, null}, 0, 0, 3);

		public Standard(I.Metadata meta, final Object[] elements, int size, int offset, int mask) {
			super(meta);
			_elements = elements;
			_size = size;
			_mask = mask;
			_offset = offset;
		}
		
		@SuppressWarnings("unchecked")
		public static <E> Standard<E> from(I.Metadata meta, E... objs) {
			return Mutable.from(meta, objs).toPersistentRaw();
		}

		@SuppressWarnings("unchecked")
		@Override
		public Standard<E> empty() {
			return (_meta != null) ? (Standard) EMPTY.withMeta(_meta) : EMPTY;
		}
		
		public static <E> Standard<E> into(Iterator<E> it) {
			return Mutable.into(it).toPersistent();
		}
		
		public static <E> Standard<E> into(Standard<E> coll, Iterator<E> it) {
			return Mutable.into(coll.toMutable(), it).toPersistent();
		}

		@Override
		public Standard<E> withMeta(I.Metadata meta) {
			return new Standard<E>(_meta, _elements.clone(), _size, _offset, _mask);
		}

		@Override
		public Standard<E> pushFirst(E e) {
			var resized = new Ut.Flag(false);
			var elems = S.addFirst(_elements.clone(), _size, _offset, _mask, e, resized);
			
			if(resized.deref()) {
				return new Standard<E>(_meta, elems, _size + 1, elems.length - 1, elems.length - 1);
			} else {
				return new Standard<E>(_meta, elems, _size + 1, (_offset - 1) & _mask, _mask);
			}
		}

		@Override
		public Standard<E> pushLast(E e) {
			var resized = new Ut.Flag(false);
			var elems =  S.addLast(_elements.clone(), _size, _offset, _mask, e, resized);
			
			if(resized.deref()) {
				return new Standard<E>(_meta, elems, _size + 1, 0, elems.length - 1);
			}  else {
				return new Standard<E>(_meta, elems, _size + 1, _offset, _mask);
			}
		}

		@Override
		public Standard<E> popFirst() {
			if(_size==0) { return this; }
			var elems = _elements.clone();
			elems[_offset] = null;
			return new Standard<E>(_meta, elems, _size - 1, (_offset + 1) & _mask, _mask);
		}

		@Override
		public Standard<E> popLast() {
			if(_size==0) { return this; }
			var elems = _elements.clone();
			elems[(_offset + _size - 1) & _mask] = null;
			return new Standard<E>(_meta, elems, _size - 1, _offset, _mask);
		}

		@Override
		public Standard<E> assoc(Integer idx, E v) {
			if (idx == _size) {
				return pushLast(v);
			} else if (idx > Integer.MAX_VALUE) {
				throw new IndexOutOfBoundsException();
			} else {
			    var elems = S.addAt(_elements.clone(), _offset, _mask, idx, v);
			    return new Standard<E>(_meta, elems, _size, _offset, _mask);
			}
		}

		@Override
		public Mutable<E> toMutable() {
			return new Mutable<E>(_meta, _elements.clone(), _size, _offset, _mask);
		}
		
		@Override
		public Standard<E> cons(E v){
			return pushFirst(v);
		}
		
		@Override
		public Standard<E> conj(E v){
			return pushLast(v);
		}

		@Override
		public Object[] _elements() {
			return _elements;
		}

		@Override
		public int _size() {
			return _size;
		}

		@Override
		public int _mask() {
			return _mask;
		}

		@Override
		public int _offset() {
			return _offset;
		}
	}
}

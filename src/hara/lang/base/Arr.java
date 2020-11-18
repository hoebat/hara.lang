package hara.lang.base;

import java.util.Iterator;
import java.util.function.BiFunction;

public interface Arr {

	public class ToIter implements Iterator<Object> {
		final Object[] _array;
		int _i;
	
		ToIter(Object array, int i) {
			_i = i;
			_array = (Object[]) array;
		}
	
		public boolean hasNext() {
			return _array != null && _i < _array.length;
		}
	
		public Object next() {
			if (_array != null && _i < _array.length)
				return _array[_i++];
			throw new java.util.NoSuchElementException();
		}
	
		public void remove() {
			throw new UnsupportedOperationException("remove() not supported");
		}
	}

	public static class ToIter_boolean implements Iterator<Boolean> {
		final boolean[] _array;
		int _i;
	
		ToIter_boolean(boolean[] array, int i) {
			_array = array;
			_i = i;
		}
	
		public boolean hasNext() {
			return _array != null && _i < _array.length;
		}
	
		public Boolean next() {
			if (_array != null && _i < _array.length)
				return Boolean.valueOf(_array[_i++]);
			throw new java.util.NoSuchElementException();
		}
	
		public void remove() {
			throw new UnsupportedOperationException("remove() not supported");
		}
	
	}

	public static class ToIter_byte implements Iterator<Byte> {
		final byte[] _array;
		int _i;
	
		ToIter_byte(byte[] array, int i) {
			_array = array;
			_i = i;
		}
	
		public boolean hasNext() {
			return _array != null && _i < _array.length;
		}
	
		public Byte next() {
			if (_array != null && _i < _array.length)
				return _array[_i++];
			throw new java.util.NoSuchElementException();
		}
	
		public void remove() {
			throw new UnsupportedOperationException("remove() not supported");
		}
	
	}

	public static class ToIter_char implements Iterator<Character> {
		final char[] _array;
		int _i;
	
		ToIter_char(char[] array, int i) {
			_array = array;
			_i = i;
		}
	
		public boolean hasNext() {
			return _array != null && _i < _array.length;
		}
	
		public Character next() {
			if (_array != null && _i < _array.length)
				return _array[_i++];
			throw new java.util.NoSuchElementException();
		}
	
		public void remove() {
			throw new UnsupportedOperationException("remove() not supported");
		}
	
	}

	public static class ToIter_double implements Iterator<Double> {
		final double[] _array;
		int _i;
	
		ToIter_double(double[] array, int i) {
			_array = array;
			_i = i;
		}
	
		public boolean hasNext() {
			return _array != null && _i < _array.length;
		}
	
		public Double next() {
			if (_array != null && _i < _array.length)
				return _array[_i++];
			throw new java.util.NoSuchElementException();
		}
	
		public void remove() {
			throw new UnsupportedOperationException("remove() not supported");
		}
	
	}

	public static class ToIter_float implements Iterator<Double> {
		final float[] _array;
		int _i;
	
		ToIter_float(float[] array, int i) {
			_array = array;
			_i = i;
		}
	
		public boolean hasNext() {
			return _array != null && _i < _array.length;
		}
	
		public Double next() {
			if (_array != null && _i < _array.length)
				return Double.valueOf(_array[_i++]);
			throw new java.util.NoSuchElementException();
		}
	
		public void remove() {
			throw new UnsupportedOperationException("remove() not supported");
		}
	}

	public static class ToIter_int implements Iterator<Long> {
		final int[] _array;
		int _i;
	
		ToIter_int(int[] array, int i) {
			_array = array;
			_i = i;
		}
	
		public boolean hasNext() {
			return _array != null && _i < _array.length;
		}
	
		public Long next() {
			if (_array != null && _i < _array.length)
				return Long.valueOf(_array[_i++]);
			throw new java.util.NoSuchElementException();
		}
	
		public void remove() {
			throw new UnsupportedOperationException("remove() not supported");
		}
	}

	public static class ToIter_long implements Iterator<Long> {
		final long[] _array;
		int _i;
	
		ToIter_long(long[] array, int i) {
			_array = array;
			_i = i;
		}
	
		public boolean hasNext() {
			return _array != null && _i < _array.length;
		}
	
		public Long next() {
			if (_array != null && _i < _array.length)
				return Long.valueOf(_array[_i++]);
			throw new java.util.NoSuchElementException();
		}
	
		public void remove() {
			throw new UnsupportedOperationException("remove() not supported");
		}
	
	}

	public static class ToIter_short implements Iterator<Long> {
		final short[] _array;
		int _i;
	
		ToIter_short(short[] array, int i) {
			_array = array;
			_i = i;
		}
	
		public boolean hasNext() {
			return _array != null && _i < _array.length;
		}
	
		public Long next() {
			if (_array != null && _i < _array.length)
				return Long.valueOf(_array[_i++]);
			throw new java.util.NoSuchElementException();
		}
	
		public void remove() {
			throw new UnsupportedOperationException("remove() not supported");
		}
	
	}

	public class ToSeq extends Obj.PT implements I.SeqArray<Object[], Object> {
	
		public final Object[] _array;
		final int _i;
	
		ToSeq(Object array, int i) {
			this(null, array, i);
		}
	
		ToSeq(I.Metadata meta, Object array, int i) {
			super(meta);
			_i = i;
			_array = (Object[]) array;
		}
	
		@Override
		public long rawLength() {
			return (_array == null) ? 0 : _array.length;
		}
	
		@Override
		public Object[] rawArray() {
			return _array;
		}
	
		@Override
		public long rawIndex() {
			return _i;
		}
	
		@Override
		public final BiFunction<Object[], Long, Object> rawFn() {
			return (arr, idx) -> arr[idx.intValue()];
		}
	
		public ToSeq withMeta(I.Metadata meta) {
			if (meta() == meta)
				return this;
			return new ToSeq(meta, _array, _i);
		}
	
		@Override
		public I.Seq<Object> restMore() {
			return (_array != null && _i < _array.length - 1) ? new ToSeq(_meta, _array, _i + 1) : this;
		}
	}

	@SuppressWarnings("rawtypes")
	public static Iterator toIter(Object array) {
		if (array == null || java.lang.reflect.Array.getLength(array) == 0)
			return Iter.emptyIterator();
		Class aclass = array.getClass();
		if (aclass == int[].class)
			return new ToIter_int((int[]) array, 0);
		if (aclass == float[].class)
			return new ToIter_float((float[]) array, 0);
		if (aclass == double[].class)
			return new ToIter_double((double[]) array, 0);
		if (aclass == long[].class)
			return new ToIter_long((long[]) array, 0);
		if (aclass == byte[].class)
			return new ToIter_byte((byte[]) array, 0);
		if (aclass == char[].class)
			return new ToIter_char((char[]) array, 0);
		if (aclass == short[].class)
			return new ToIter_short((short[]) array, 0);
		if (aclass == boolean[].class)
			return new ToIter_boolean((boolean[]) array, 0);
		return new ToIter(array, 0);
	}

	@SuppressWarnings("rawtypes")
	public static Iterator toIter(Object... array) {
		if (array == null || array.length == 0)
			return Iter.emptyIterator();
		return new ToIter(array, 0);
	}

	@SuppressWarnings("rawtypes")
	public static I.Seq toSeq(Object... array) {
		if (array == null || array.length == 0)
			return null;
		return new ToSeq(array, 0);
	}

}

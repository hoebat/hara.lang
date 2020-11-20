package hara.lang.base;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.BiFunction;

public interface Arr {
	
	public static boolean[] booleans(boolean... arr) {
		return arr;
	}

	public static byte[] bytes(byte... arr) {
		return arr;
	}
	
	public static char[] chars(char... arr) {
		return arr;
	}
	
	public static short[] shorts(short... arr) {
		return arr; 
	}
	
	public static int[] ints(int... arr) {
		return arr;
	}
	
	public static long[] longs(long... arr) {
		return arr;
	}
	
	public static float[] floats(float... arr) {
		return arr; 
	}
	
	public static double[] doubles(double... arr) {
		return arr; 
	}
	
	public static String[] strings(String... arr) {
		return arr; 
	}
	
	public static Object[] objects(Object... arr) {
		return arr; 
	}

	public interface T {

		public class ToIter implements Iterator<Object> {
			final Object[] _array;
			int _i;
		
			ToIter(Object array, int i) {
				_i = i;
				_array = (Object[]) array;
			}
		
			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public Object next() {
				if (_array != null && _i < _array.length)
					return _array[_i++];
				throw new java.util.NoSuchElementException();
			}
		
			@Override
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
		
			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public Boolean next() {
				if (_array != null && _i < _array.length)
					return Boolean.valueOf(_array[_i++]);
				throw new java.util.NoSuchElementException();
			}
		
			@Override
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
		
			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public Byte next() {
				if (_array != null && _i < _array.length)
					return _array[_i++];
				throw new java.util.NoSuchElementException();
			}
		
			@Override
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
		
			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public Character next() {
				if (_array != null && _i < _array.length)
					return _array[_i++];
				throw new java.util.NoSuchElementException();
			}
		
			@Override
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
		
			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public Double next() {
				if (_array != null && _i < _array.length)
					return _array[_i++];
				throw new java.util.NoSuchElementException();
			}
		
			@Override
			public void remove() {
				throw new UnsupportedOperationException("remove() not supported");
			}
		
		}

		public static class ToIter_float implements Iterator<Float> {
			final float[] _array;
			int _i;
		
			ToIter_float(float[] array, int i) {
				_array = array;
				_i = i;
			}
		
			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public Float next() {
				if (_array != null && _i < _array.length)
					return _array[_i++];
				throw new java.util.NoSuchElementException();
			}
		
			@Override
			public void remove() {
				throw new UnsupportedOperationException("remove() not supported");
			}
		}

		public static class ToIter_int implements Iterator<Integer> {
			final int[] _array;
			int _i;
		
			ToIter_int(int[] array, int i) {
				_array = array;
				_i = i;
			}
		
			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public Integer next() {
				if (_array != null && _i < _array.length)
					return _array[_i++];
				throw new java.util.NoSuchElementException();
			}
		
			@Override
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
		
			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public Long next() {
				if (_array != null && _i < _array.length)
					return Long.valueOf(_array[_i++]);
				throw new java.util.NoSuchElementException();
			}
		
			@Override
			public void remove() {
				throw new UnsupportedOperationException("remove() not supported");
			}
		
		}

		public static class ToIter_short implements Iterator<Short> {
			final short[] _array;
			int _i;
		
			ToIter_short(short[] array, int i) {
				_array = array;
				_i = i;
			}
		
			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public Short next() {
				if (_array != null && _i < _array.length)
					return _array[_i++];
				throw new java.util.NoSuchElementException();
			}
		
			@Override
			public void remove() {
				throw new UnsupportedOperationException("remove() not supported");
			}
		
		}

		public class ToRevIter implements Iterator<Object> {
			final Object[] _array;
			int _i;
		
			ToRevIter(Object array, int offset) {
				_array = (Object[]) array;
				_i = _array.length - 1 - offset;
			}
		
			@Override
			public boolean hasNext() {
				return _array != null && _i >= 0;
			}
		
			@Override
			public Object next() {
				if (_array != null && _i >= 0)
					return _array[_i--];
				throw new java.util.NoSuchElementException();
			}
		
			@Override
			public void remove() {
				throw new UnsupportedOperationException("remove() not supported");
			}
		}

		public class ToSeq extends Obj.SEQ<Object> implements I.SeqArray<Object[], Object> {
		
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
		
			@Override
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
	}

	@SuppressWarnings("rawtypes")	
	public static Iterator toRevIter(Object[] array) {
		return new T.ToRevIter(array, 0);
	}

	@SuppressWarnings("rawtypes")
	public static Iterator toIter(Object array) {
		if (array == null || java.lang.reflect.Array.getLength(array) == 0)
			return Iter.emptyIterator();
		Class aclass = array.getClass();
		if (aclass == int[].class)
			return new T.ToIter_int((int[]) array, 0);
		if (aclass == float[].class)
			return new T.ToIter_float((float[]) array, 0);
		if (aclass == double[].class)
			return new T.ToIter_double((double[]) array, 0);
		if (aclass == long[].class)
			return new T.ToIter_long((long[]) array, 0);
		if (aclass == byte[].class)
			return new T.ToIter_byte((byte[]) array, 0);
		if (aclass == char[].class)
			return new T.ToIter_char((char[]) array, 0);
		if (aclass == short[].class)
			return new T.ToIter_short((short[]) array, 0);
		if (aclass == boolean[].class)
			return new T.ToIter_boolean((boolean[]) array, 0);
		return new T.ToIter(array, 0);
	}

	@SuppressWarnings("rawtypes")
	public static Iterator toIter(Object... array) {
		if (array == null || array.length == 0)
			return Iter.emptyIterator();
		return new T.ToIter(array, 0);
	}

	@SuppressWarnings("unchecked")
	public static <E> E[] newArray(Class<E> type, int length) {
		return (E[]) Array.newInstance(type, length);
	}

	@SuppressWarnings("unchecked")
	public static <E> E[] newArray(E[] reference, int length) {
		Class<?> type = reference.getClass().getComponentType();

		E[] result = (E[]) Array.newInstance(type, length);
		return result;
	}

	public static <E> E[] concat(E[] first, E[] second, Class<E> type) {
		E[] result = newArray(type, first.length + second.length);
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	public static <E> E[] concat(E element, E[] array) {
		E[] result = newArray(array, array.length + 1);
		result[0] = element;
		System.arraycopy(array, 0, result, 1, array.length);
		return result;
	}
	
	public static <E> E[] concat(E[] array, E element) {
		E[] result = Arrays.copyOf(array, array.length + 1);
		result[array.length] = element;
		return result;
	}


	  public static void checkPositionIndexes(int start, int end, int size) {
	    if (start < 0 || end < start || end > size) {
	      throw new IndexOutOfBoundsException();
	    }
	  }

	public static Object[] fillArray(Iterable<?> elements, Object[] array) {
		int i = 0;
		for (Object element : elements) {
			array[i++] = element;
		}
		return array;
	}

	static void swap(Object[] array, int i, int j) {
		Object temp = array[i];
		array[i] = array[j];
		array[j] = temp;
	}

	static Object[] checkNotNull(Object... array) {
		return checkNotNull(array, array.length);
	}

	static Object[] checkNotNull(Object[] array, int length) {
		for (int i = 0; i < length; i++) {
			checkNotNull(array[i], i);
		}
		return array;
	}

	static Object checkNotNull(Object element, int index) {
		if (element == null) {
			throw new Ex.NULL("at index " + index);
		}
		return element;
	}

}

package hara.lang.base;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

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
	
	public static <R, E> R reduce(BiFunction<R, E, R> f, R init, E[] elements) {
		R out = init;
		for(E e : elements) {
			out = f.apply(out, e);
		}
		return out;
	}
	
	public static <R, E> R reduce(BiFunction<R, E, R> f, R init, E[] elements, Supplier<Boolean> end) {
		R out = init;
		for(E e : elements) {
			out = f.apply(out, e);
			if (end.get()) {return out;}
		}
		return out;
	}

	public interface T {

		public class ToIter<E> implements Iterator<E> {
			final E[] _array;
			int _i;
		
			public ToIter(E[] array, int i) {
				_i = i;
				_array = array;
			}
		
			@SuppressWarnings("unchecked")
			public ToIter(Object array, int i) {
				_i = i;
				_array = (E[])array;
			}

			@Override
			public boolean hasNext() {
				return _array != null && _i < _array.length;
			}
		
			@Override
			public E next() {
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

		public class ToSeq extends Obj.SEQ<Object> implements Data.SeqArray<Object[], Object> {
		
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
			return It.emptyIterator();
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
	
	public static <E> E[] concat(E[] first, E[] second, Class<E> type) {
		E[] result = newArray(type, first.length + second.length);
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	@SuppressWarnings("unchecked")
	public static <E> E[] newArray(Class<E> type, int length) {
		return (E[]) Array.newInstance(type, length);
	}
	
	@SuppressWarnings({"unchecked" })
	public static <E> Iterator<E> toIter(E... array) {
		if (array == null || array.length == 0)
			return (Iterator<E>) It.emptyIterator();
		return new T.ToIter<E>(array, 0);
	}


	  public static void checkPosition(int start, int end, int size) {
	    if (start < 0 || end < start || end > size) {
	      throw new IndexOutOfBoundsException();
	    }
	  }

	static <E> void swap(E[] array, int i, int j) {
		E temp = array[i];
		array[i] = array[j];
		array[j] = temp;
	}

	@SafeVarargs
	static <E> E[] checkNotNull(E... array) {
		return checkNotNull(array, array.length);
	}

	static <E> E[]checkNotNull(E[] array, int length) {
		for (int i = 0; i < length; i++) {
			checkNotNull(array[i], i);
		}
		return array;
	}
	
	public static <E> E[] fillArray(Iterator<E> it, E[] array) {
		int i = 0;
		while(it.hasNext()) {
			array[i++] = it.next();
		}
		return array;
	}
	
	@SuppressWarnings("unchecked")
	public static <E, R> R[] map(Function<E, R> f, Class<E> type, E[] array) {
		var out = (R[]) Array.newInstance(type, array.length);
		return (R[]) fillArray(toIter(array), out);
	}

}

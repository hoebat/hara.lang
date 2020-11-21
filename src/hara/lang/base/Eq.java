package hara.lang.base;

import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;

public interface Eq {

	public static boolean eq(Object a, Object b) {
		if (a == b) {
			return true;
		} else if (a == null || b == null) {
			return false;
		} else if (a instanceof Number && b instanceof Number) {
			return Num.eq(a, b);
		} else if (a instanceof I.Equality) {
			return ((I.Equality) a).equality(b);
		} else if (b instanceof I.Equality) {
			return ((I.Equality) b).equality(a);
		} else {
			return a.equals(b);
		}
	}

	public static boolean eqIterable(Iterable<Object> a, Iterable<Object> b) {
		return eqIterator(a.iterator(), b.iterator(), Eq::eq);
	}

	public static boolean eqList(List<Object> a, List<Object> b) {
		if (a.size() != b.size()) {
			return false;
		} else {
			return eqIterator(a.iterator(), b.iterator(), Eq::eq);
		}
	}

	public static boolean eqList(List<Object> a, Iterator<Object> b) {
		return eqIterator(a.iterator(), b, Eq::eq);
	}

	public static boolean eqIterator(Iterator<Object> a, Iterator<Object> b) {
		return eqIterator(a, b, Eq::eq);
	}

	public static boolean eqIterator(Iterator<Object> a, Iterator<Object> b, BiPredicate<Object, Object> equals) {
		return It.equals(a, b, equals);
	}
	
	@SuppressWarnings("unchecked")
	public static int compare(Object k1, Object k2) {
		if (k1 == k2)
			return 0;
		if (k1 != null) {
			if (k2 == null)
				return 1;
			if (k1 instanceof Number)
				return Num.compare((Number) k1, (Number) k2);
			return ((Comparable<Object>) k1).compareTo(k2);
		}
		return -1;
	}

	public static boolean equals(Object k1, Object k2) {
		return (k1 == k2) ? true : (k1 != null && k1.equals(k2));
	}

	public static boolean identical(Object k1, Object k2) {
		return k1 == k2;
	}
}

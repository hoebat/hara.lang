package hara.lang.base;

import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;
import hara.lang.protocol.*;

public interface Eq {

	public static boolean eq(Object a, Object b) {
		if (a == b) {
			return true;
		} else if (a == null || b == null) {
			return false;
		} else if (a instanceof Number && b instanceof Number) {
			return Num.eq(a, b);
		} else if (a instanceof IEquality) {
			return ((IEquality) a).equality(b);
		} else if (b instanceof IEquality) {
			return ((IEquality) b).equality(a);
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
}

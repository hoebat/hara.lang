package hara.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import hara.lang.base.*;
import hara.lang.base.Fn.Static.A;

@SuppressWarnings({"unchecked", "rawtypes"})
class FnTest {
	
	public static Object[] arr(Object... arr) {
		return arr;
	}
	public static void iterHas(Iterator it, Object[] arr) {
		assertArrayEquals(arr, Iter.toArray(it));
	}

	public interface CB {

		public static I.Fn U = new I.Fn() {
			@Override
			public Object invoke(Object f) {
				return new I.Fn() {
					@Override
					public Object invoke(Object x) {
						return ((I.Fn)((I.Fn)f).invoke(f)).invoke(x);
					}
				};
			}
		};

		public class A implements I.Fn {
			final I.Fn _f;
			
			public A(I.Fn f) { _f = f; }

			@Override
			public Object invoke(Object x) {
				return _f.invoke(U.invoke(x));
			}
		}
		
		public static I.Fn Z = new I.Fn() {

			@Override
			public Object invoke(Object f) {
				A gA = new A((I.Fn)f);
				return gA.invoke(gA);
			}
		};
		
		public static I.Fn factorial = new I.Fn() {

			@Override
			public Object invoke(Object f) {
				return new I.Fn() {

					@Override
					public Object invoke(Object v) {
						Integer n = (Integer)v;
						return (n == 0) ? 1 : n * (Integer)((I.Fn)f).invoke(n - 1);
					}
				};
			}
		};
	}
	
	public interface Comb {

		public static Function U = new Function() {
			@Override
			public Object apply(Object f) {
				return new Function() {
					@Override
					public Object apply(Object x) {
						return ((Function)((Function)f).apply(f)).apply(x);
					}
				};
			}
		};

		public class A implements Function {
			final Function _f;
			
			public A(Function f) { _f = f; }

			@Override
			public Object apply(Object x) {
				return _f.apply(U.apply(x));
			}
		}
		
		public static Function Z = new Function() {

			@Override
			public Object apply(Object f) {
				A gA = new A((Function)f);
				return gA.apply(gA);
			}
		};
		
		public static Function factorial = new Function() {

			@Override
			public Object apply(Object f) {
				return new Function<Integer, Integer>() {

					@Override
					public Integer apply(Integer n) {
						return (n == 0) ? 1 : n * (Integer)((Function)f).apply(n - 1);
					}
				};
			}
			
		};
	}
	

	@Test
	void combinatorSimple() {
		assertEquals(
				((Function)(Comb.Z.apply(Comb.factorial))).apply(5),
				120);
		
		assertEquals(
				((I.Fn)(CB.Z.invoke(CB.factorial))).invoke(5),
				120);
	}
	
	@Test
	void MapSimple() {
		
		iterHas(
		new Fn.Map<Integer, Integer>(
				(i) -> i + 10
			).invoke(Iter.ints(1,2,3)),
		arr(11, 12, 13));	
	}	
	
	@Test
	void FilterSimple() {

		iterHas(
		new Fn.Filter<Integer>(
				(i) -> i % 2 == 0
			).invoke(Iter.ints(1,2,3,4)),
		arr(2, 4));	
	}	
	
	@Test
	void CompSimple() {
		
		Fn.Comp inc = new Fn.Comp(
				new Function<Integer, Integer>() {
					@Override
					public Integer apply(Integer t) {
						return t + 1;
					}
				},
				new Function<Integer, Integer>() {
					@Override
					public Integer apply(Integer t) {
						return t * 10;
					}
				});
		assertEquals(21, inc.invoke(2));	
	}	
	/*

	@SuppressWarnings("rawtypes")


	System.out.printf("\nOUT: %d %d %d %d", mit.next(), mit.next(), mit.next(), mit.next());
	
	Iterator<Integer> fit = new H.Filter<Integer>(
			(i) -> i % 2 == 0)
			.apply((Iterable)Arrays.asList(1,2,3,4,5));
	
	System.out.printf("\nOUT: %d %d", fit.next(), fit.next());

	Integer rit = new H.Reduce<Integer, Integer>(
			() -> 0,
			(acc, i) -> acc + i)
			.apply((Iterable)Arrays.asList(1,2,3,4,5));
	
	System.out.printf("\nOUT: %d", rit);

	//
	// Pipe
	// 
	Iterator<Integer> pipe = 
		new H.Pipe(
			new H.Map<Integer, Integer>((i) -> i + 1),
			new H.Map<Integer, Integer>((i) -> i + 1),
			new H.Map<Integer, Integer>((i) -> i + 1),
			new H.Filter<Integer>((i) -> i % 2 == 0))
			.apply((Iterable)Arrays.asList(1,2,3,4,5,6,7,8));

	System.out.printf("\nPIPE: %d %d %d %d", pipe.next(), pipe.next(), pipe.next(), pipe.next());
	
	
	void consConsEq() {

		var x = L.cons().cons(1).cons(2);
		var y = Vector.Standard.EMPTY.conj(2).conj(1);
		
		assert(((I.Equality) x).equality(y));
	}
	*/

}

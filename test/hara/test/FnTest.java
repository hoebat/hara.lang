package hara.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import hara.lang.base.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class FnTest {
	
	public static Object[] arr(Object... arr) {
		return arr;
	}
	public static void iterHas(Iterator it, Object[] arr) {
		assertArrayEquals(arr, Iter.toArray(it));
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

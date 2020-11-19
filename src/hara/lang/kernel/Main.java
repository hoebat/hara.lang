package hara.lang.kernel;

import hara.lang.base.*;
import java.util.*;

public class Main {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void main(String[] args){
		
		Iterator<Integer> mit = new H.Map<Integer, Integer>(
				(i) -> i + 1)
				.apply((Iterable)Arrays.asList(1,2,3,4,5));

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
		
	}
}

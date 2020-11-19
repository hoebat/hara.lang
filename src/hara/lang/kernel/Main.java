package hara.lang.kernel;

import hara.lang.base.*;
import java.util.*;

public class Main {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void main(String[] args){
		
		Iterator<Integer> mit = new H.Map.Fn<Integer, Integer>(
				(i) -> i + 1)
				.apply((Iterable)Arrays.asList(1,2,3,4,5));
		
		System.out.printf("\nOUT: %d %d %d %d", mit.next(), mit.next(), mit.next(), mit.next());

		Integer rit = new H.Reduce.Fn<Integer, Integer>(
				() -> 0,
				(acc, i) -> acc + i)
				.apply((Iterable)Arrays.asList(1,2,3,4,5));
		
		System.out.printf("\nOUT: %d", rit);
	}
}

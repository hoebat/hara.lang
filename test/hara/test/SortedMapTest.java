package hara.test;

import static org.junit.jupiter.api.Assertions.*;
import hara.lang.data.*;

import org.junit.jupiter.api.Test;

@SuppressWarnings({ "unused", "unchecked" })
class SortedMapTest {
	
	

	@Test
	void test() {
		var x = SortedMap.Standard.EMPTY;
		var y = x.assoc("1", "2").assoc("3", "4");
		assertEquals (y.count(), 2);
	}

}

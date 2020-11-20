package hara.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import hara.lang.base.*;
import hara.lang.data.*;


@SuppressWarnings("unchecked")
class MapTest {

	@Test
	void basicKeywordTests() {
		var k1 = Keyword.create("hello");
		var k2 = Keyword.create("hello");
		assertArrayEquals(
				Iter.toArray(Keyword.CACHE.keys()),
				Arr.objects("hello"));
		
		assertEquals(k1, k2);
		assertEquals(k1.hashCode(), k2.hashCode());
		assertEquals(k1.hashGet(),  k2.hashGet());
		assertNotEquals(
				Arr.longs(k1.hashGet(),  k2.hashGet()),
				Arr.longs(k1.hashCode(), k2.hashCode()));
		
		assertEquals(k1.display(), ":hello");
		
		var k3 = Keyword.create("hello/world");
		assertNotEquals(k1, k3);
		assertArrayEquals(
				Iter.toArray(Keyword.CACHE.keys()),
				Arr.objects("hello", "hello/world"));
	}
	
	@Test
	void basicMapLookup() {
		var k1 = Keyword.create("hello");
		var k2 = Keyword.create("world");
		var m = SortedMap.Standard.EMPTY.assoc(k1, k2);
		
		assertEquals(m.count(), 1);
		assertEquals(m.lookup(k1).hashCode(), k2.hashCode());
		
		var n = m.assoc(k2, k1);
		assertArrayEquals(
				Iter.toArray(n.keys()),
				Arr.objects(k1,k2));
	}

}

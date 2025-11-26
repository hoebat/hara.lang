package hara.lang.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class CollectionsTest {

    @Test
    public void testList() {
        List.Standard<Integer> list = List.Standard.from(null, 1, 2, 3);
        assertEquals(3, list.count());
        assertEquals(Integer.valueOf(1), list.nth(0));
        assertEquals(Integer.valueOf(2), list.nth(1));
        assertEquals(Integer.valueOf(3), list.nth(2));
    }

    @Test
    public void testVector() {
        Vector.Standard<Integer> vector = Vector.Standard.from(null, 1, 2, 3);
        assertEquals(3, vector.count());
        assertEquals(Integer.valueOf(1), vector.nth(0));
        assertEquals(Integer.valueOf(2), vector.nth(1));
        assertEquals(Integer.valueOf(3), vector.nth(2));
    }

    @Test
    public void testMap() {
        Map.Standard<Keyword, Integer> map = Map.Standard.from(null, Keyword.create("a"), 1, Keyword.create("b"), 2);
        assertEquals(2, map.count());
        assertEquals(Integer.valueOf(1), map.lookup(Keyword.create("a")));
        assertEquals(Integer.valueOf(2), map.lookup(Keyword.create("b")));
    }

    @Test
    public void testSet() {
        Set.Standard<Integer> set = Set.Standard.from(null, 1, 2, 3);
        assertEquals(3, set.count());
        assertNotNull(set.find(1));
        assertNotNull(set.find(2));
        assertNotNull(set.find(3));
        assertNull(set.find(4));
    }
}

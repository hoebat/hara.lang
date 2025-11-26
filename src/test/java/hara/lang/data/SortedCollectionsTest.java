package hara.lang.data;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Iterator;
import java.util.Map.Entry;

public class SortedCollectionsTest {

    @Test
    public void testSortedMap() {
        SortedMap.Standard<String, Integer> map = SortedMap.Standard.from(null, "c", 3, "a", 1, "b", 2);
        assertEquals(3, map.count());
        assertEquals(Integer.valueOf(1), map.lookup("a"));

        // Test iteration order
        Iterator<Entry<String, Integer>> it = map.iterator();
        assertEquals("a", it.next().getKey());
        assertEquals("b", it.next().getKey());
        assertEquals("c", it.next().getKey());
    }

    @Test
    public void testSortedSet() {
        SortedSet.Standard<Integer> set = SortedSet.Standard.from(null, 3, 1, 2, 1, 3);
        assertEquals(3, set.count());
        assertNotNull(set.find(1));

        // Test iteration order
        Iterator<Integer> it = set.iterator();
        assertEquals(Integer.valueOf(1), it.next());
        assertEquals(Integer.valueOf(2), it.next());
        assertEquals(Integer.valueOf(3), it.next());
    }

    @Test
    public void testMutableSortedMap() {
        SortedMap.Mutable<String, Integer> map = SortedMap.Mutable.from(null, "c", 3, "a", 1);
        map.assoc("b", 2);
        assertEquals(3, map.count());

        // Test iteration order
        Iterator<Entry<String, Integer>> it = map.iterator();
        assertEquals("a", it.next().getKey());
        assertEquals("b", it.next().getKey());
        assertEquals("c", it.next().getKey());

        // Test dissoc
        map.dissoc("b");
        assertEquals(2, map.count());
        assertNull(map.lookup("b"));
    }

    @Test
    public void testMutableSortedSet() {
        SortedSet.Mutable<Integer> set = SortedSet.Mutable.from(null, 3, 1);
        set.conj(2);
        assertEquals(3, set.count());

        // Test iteration order
        Iterator<Integer> it = set.iterator();
        assertEquals(Integer.valueOf(1), it.next());
        assertEquals(Integer.valueOf(2), it.next());
        assertEquals(Integer.valueOf(3), it.next());

        // Test dissoc
        set.dissoc(2);
        assertEquals(2, set.count());
        assertNull(set.find(2));
    }
}

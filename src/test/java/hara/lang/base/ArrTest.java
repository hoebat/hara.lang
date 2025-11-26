package hara.lang.base;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Iterator;

public class ArrTest {

    @Test
    public void testReduce() {
        Integer[] arr = {1, 2, 3, 4, 5};
        Integer result = Arr.reduce((a, b) -> a + b, arr);
        assertEquals(Integer.valueOf(15), result);
    }

    @Test
    public void testEvery() {
        Integer[] arr = {2, 4, 6, 8, 10};
        assertTrue(Arr.every(n -> n % 2 == 0, arr));
        assertFalse(Arr.every(n -> n > 5, arr));
    }

    @Test
    public void testAny() {
        Integer[] arr = {1, 3, 5, 7, 9};
        assertTrue(Arr.any(n -> n > 5, arr));
        assertFalse(Arr.any(n -> n % 2 == 0, arr));
    }

    @Test
    public void testToIter() {
        Integer[] arr = {1, 2, 3};
        Iterator<Integer> iter = Arr.toIter(arr);
        assertTrue(iter.hasNext());
        assertEquals(Integer.valueOf(1), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(Integer.valueOf(2), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(Integer.valueOf(3), iter.next());
        assertFalse(iter.hasNext());
    }
}

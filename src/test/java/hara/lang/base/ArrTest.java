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

  @Test
  public void testConcat() {
    Integer[] first = {1, 2, 3};
    Integer[] second = {4, 5, 6};
    Integer[] result = Arr.concat(first, second, Integer.class);
    assertArrayEquals(new Integer[] {1, 2, 3, 4, 5, 6}, result);
  }

  @Test
  public void testMap() {
    Integer[] arr = {1, 2, 3};
    String[] result = Arr.map(n -> n.toString(), String.class, arr);
    assertArrayEquals(new String[] {"1", "2", "3"}, result);
  }

  @Test
  public void testSome() {
    Integer[] arr = {1, 2, 3, 4, 5};
    Integer result = Arr.some(n -> n > 3, arr);
    assertEquals(Integer.valueOf(4), result);
  }

  @Test
  public void testToRevIter() {
    Integer[] arr = {1, 2, 3};
    Iterator<Integer> iter = Arr.toRevIter(arr);
    assertTrue(iter.hasNext());
    assertEquals(Integer.valueOf(3), iter.next());
    assertTrue(iter.hasNext());
    assertEquals(Integer.valueOf(2), iter.next());
    assertTrue(iter.hasNext());
    assertEquals(Integer.valueOf(1), iter.next());
    assertFalse(iter.hasNext());
  }

  @Test
  public void testConcatWithEmptyArrays() {
    Integer[] first = {};
    Integer[] second = {};
    Integer[] result = Arr.concat(first, second, Integer.class);
    assertArrayEquals(new Integer[] {}, result);
  }

  @Test
  public void testMapWithEmptyArray() {
    Integer[] arr = {};
    String[] result = Arr.map(n -> n.toString(), String.class, arr);
    assertArrayEquals(new String[] {}, result);
  }

  @Test
  public void testSomeWithNoMatch() {
    Integer[] arr = {1, 2, 3, 4, 5};
    Integer result = Arr.some(n -> n > 5, arr);
    assertNull(result);
  }

  @Test
  public void testToRevIterWithEmptyArray() {
    Integer[] arr = {};
    Iterator<Integer> iter = Arr.toRevIter(arr);
    assertFalse(iter.hasNext());
  }
}

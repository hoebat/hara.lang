package hara.lang.base;

import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

public class IterTest {

  @Test
  public void testIter() {
    Integer[] arr = {1, 2, 3};
    Iterator<Integer> iter = Iter.iter(arr);
    assertTrue(iter.hasNext());
    assertEquals(Integer.valueOf(1), iter.next());
    assertTrue(iter.hasNext());
    assertEquals(Integer.valueOf(2), iter.next());
    assertTrue(iter.hasNext());
    assertEquals(Integer.valueOf(3), iter.next());
    assertFalse(iter.hasNext());
  }

  @Test
  public void testReduce() {
    List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
    Integer result = Iter.reduce(list.iterator(), (a, b) -> a + b);
    assertEquals(Integer.valueOf(15), result);
  }

  @Test
  public void testMap() {
    List<Integer> list = Arrays.asList(1, 2, 3);
    Iterator<String> iter = Iter.map(list.iterator(), n -> "v" + n);
    assertEquals("v1", iter.next());
    assertEquals("v2", iter.next());
    assertEquals("v3", iter.next());
    assertFalse(iter.hasNext());
  }

  @Test
  public void testFilter() {
    List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
    Iterator<Integer> iter = Iter.filter(list.iterator(), n -> n % 2 == 0);
    assertTrue(iter.hasNext());
    assertEquals(Integer.valueOf(2), iter.next());
    assertTrue(iter.hasNext());
    assertEquals(Integer.valueOf(4), iter.next());
    assertFalse(iter.hasNext());
  }

  @Test
  public void testConcat() {
    List<Integer> list1 = Arrays.asList(1, 2);
    List<Integer> list2 = Arrays.asList(3, 4);
    Iterator<Integer> iter = Iter.concat(list1.iterator(), list2.iterator());
    assertEquals(Integer.valueOf(1), iter.next());
    assertEquals(Integer.valueOf(2), iter.next());
    assertEquals(Integer.valueOf(3), iter.next());
    assertEquals(Integer.valueOf(4), iter.next());
    assertFalse(iter.hasNext());
  }

  @Test
  public void testConcatDoesNotRequestLaterSourcesUntilNeeded() {
    final int[] requested = {0};
    Iterator<Iterator<Integer>> sources =
        new Iterator<Iterator<Integer>>() {
          private int sourceIndex;

          @Override
          public boolean hasNext() {
            return sourceIndex < 2;
          }

          @Override
          public Iterator<Integer> next() {
            requested[0]++;
            int start = sourceIndex++ * 2;
            return Arrays.asList(start, start + 1).iterator();
          }
        };

    Iterator<Integer> iter = Iter.concat(sources);
    assertEquals(0, requested[0]);
    assertEquals(Integer.valueOf(0), iter.next());
    assertEquals(1, requested[0]);
    assertEquals(Integer.valueOf(1), iter.next());
    assertEquals(1, requested[0]);
    assertEquals(Integer.valueOf(2), iter.next());
    assertEquals(2, requested[0]);
  }
}

package hara.lang.base;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hara.lang.base.iter.CloseableIterator;
import hara.lang.base.iter.ConcatIterator;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Lifecycle checks for resource-backed iterator composition. */
public class CloseableIteratorTest {
  @Test
  public void concatClosesActiveAndQueuedSourcesWithoutForcingLaziness() {
    TrackingIterator first = new TrackingIterator(1);
    TrackingIterator second = new TrackingIterator(2);
    ConcatIterator<Integer> concat = new ConcatIterator<>(Iter.objects(first));
    concat.concat(second);

    assertFalse(first.closed);
    assertFalse(second.closed);
    assertTrue(concat.hasNext());
    assertTrue(concat.next() == 1);

    concat.close();
    concat.close();
    assertTrue(first.closed);
    assertTrue(second.closed);
    assertFalse(concat.hasNext());
  }

  @Test
  public void cycleAcquiresLazilyAndClosesEachResourceGeneration() {
    AtomicInteger acquired = new AtomicInteger();
    AtomicInteger closed = new AtomicInteger();
    CloseableIterator<Integer> cycle =
        (CloseableIterator<Integer>)
            Iter.cycle(
                () -> {
                  acquired.incrementAndGet();
                  return new CountingIterator(acquired.get(), closed);
                });

    assertEquals(0, acquired.get());
    assertEquals(Integer.valueOf(1), cycle.next());
    assertEquals(1, acquired.get());
    assertEquals(Integer.valueOf(2), cycle.next());
    assertEquals(2, acquired.get());
    assertEquals(1, closed.get());
    cycle.close();
    cycle.close();
    assertEquals(2, closed.get());
    assertFalse(cycle.hasNext());
  }

  @Test
  public void mapFilterTakeDropAndPartitionOwnTheirResourceSource() {
    AtomicInteger closed = new AtomicInteger();
    Iterator<?>[] derived = {
      Iter.map(new CountingIterator(1, closed), value -> value),
      Iter.filter(new CountingIterator(1, closed), value -> true),
      Iter.take(new CountingIterator(1, closed), 1),
      Iter.drop(new CountingIterator(1, closed), 0),
      Iter.partitionPair(new CountingIterator(1, closed))
    };

    assertEquals(0, closed.get());
    for (int index = 0; index < derived.length; index++) {
      Iter.close(derived[index]);
      Iter.close(derived[index]);
      assertEquals(index + 1, closed.get());
      assertFalse(derived[index].hasNext());
    }
  }

  @Test
  public void exhaustionAlsoReleasesTheResourceSource() {
    AtomicInteger closed = new AtomicInteger();
    Iterator<Integer> mapped = Iter.map(new CountingIterator(1, closed), value -> value);
    assertEquals(Integer.valueOf(1), mapped.next());
    assertFalse(mapped.hasNext());
    assertEquals(1, closed.get());
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void zipClosesEveryResourceWhenTheShortestSourceEnds() {
    TrackingIterator first = new TrackingIterator(1);
    TrackingIterator second = new TrackingIterator(2);
    CloseableIterator<Object[]> zipped =
        (CloseableIterator<Object[]>)
            Iter.zip((Iterator) Iter.objects((Iterator) first, (Iterator) second));

    assertTrue(zipped.hasNext());
    assertTrue(Arrays.equals(new Object[] {1, 2}, zipped.next()));
    assertFalse(zipped.hasNext());
    assertTrue(first.closed);
    assertTrue(second.closed);
    zipped.close();
  }

  private static final class CountingIterator implements CloseableIterator<Integer> {
    private final int value;
    private final AtomicInteger closed;
    private boolean delivered;
    private boolean isClosed;

    private CountingIterator(int value, AtomicInteger closed) {
      this.value = value;
      this.closed = closed;
    }

    @Override
    public boolean hasNext() {
      return !isClosed && !delivered;
    }

    @Override
    public Integer next() {
      if (!hasNext()) throw new NoSuchElementException();
      delivered = true;
      return value;
    }

    @Override
    public void close() {
      if (isClosed) return;
      isClosed = true;
      closed.incrementAndGet();
    }
  }

  private static final class TrackingIterator implements CloseableIterator<Integer> {
    private final int value;
    private boolean delivered;
    private boolean closed;

    private TrackingIterator(int value) {
      this.value = value;
    }

    @Override
    public boolean hasNext() {
      return !closed && !delivered;
    }

    @Override
    public Integer next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      delivered = true;
      return value;
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}

package hara.lang.base;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hara.lang.base.iter.CloseableIterator;
import hara.lang.base.iter.ConcatIterator;
import java.util.NoSuchElementException;
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

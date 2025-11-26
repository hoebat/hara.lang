package hara.lang.base;

import org.junit.Test;
import static org.junit.Assert.*;

public class NumTest {

    @Test
    public void testAdd() {
        assertEquals(5L, Num.add(2, 3));
        assertEquals(5.0, (double) Num.add(2.0, 3.0), 0.0);
        assertEquals(5L, Num.add(2L, 3L));
    }

    @Test
    public void testMinus() {
        assertEquals(-1L, Num.minus(2, 3));
        assertEquals(-1.0, (double) Num.minus(2.0, 3.0), 0.0);
        assertEquals(-1L, Num.minus(2L, 3L));
    }

    @Test
    public void testMultiply() {
        assertEquals(6L, Num.multiply(2, 3));
        assertEquals(6.0, (double) Num.multiply(2.0, 3.0), 0.0);
        assertEquals(6L, Num.multiply(2L, 3L));
    }

    @Test
    public void testDivide() {
        assertEquals(2.0, (double) Num.divide(6.0, 3.0), 0.0);
    }
}

package hara.kernel.base;

import org.junit.Test;
import static org.junit.Assert.*;
import hara.lang.data.Keyword;
import hara.lang.data.Symbol;
import hara.lang.data.Atom;

public class BuiltinTest {

    @Test
    public void testBasicAtom() {
        Atom.Standard<Integer> atom = Builtin.Basic.atom(10);
        assertEquals(Integer.valueOf(10), atom.deref());
    }

    @Test
    public void testBasicCompare() {
        assertEquals(0, Builtin.Basic.compare(1, 1));
        assertEquals(1, Builtin.Basic.compare(2, 1));
        assertEquals(-1, Builtin.Basic.compare(1, 2));
    }

    @Test
    public void testCheckIsClass() {
        assertTrue(Builtin.Check.isClass(String.class));
        assertFalse(Builtin.Check.isClass("hello"));
    }

    @Test
    public void testCheckIsFalsey() {
        assertTrue(Builtin.Check.isFalsey(null));
        assertTrue(Builtin.Check.isFalsey(false));
        assertFalse(Builtin.Check.isFalsey(true));
        assertFalse(Builtin.Check.isFalsey(0));
    }

    @Test
    public void testCheckIsInteger() {
        assertTrue(Builtin.Check.isInteger(10));
        assertTrue(Builtin.Check.isInteger(10L));
        assertFalse(Builtin.Check.isInteger(10.0));
    }

    @Test
    public void testCheckIsTruthy() {
        assertFalse(Builtin.Check.isTruthy(null));
        assertFalse(Builtin.Check.isTruthy(false));
        assertTrue(Builtin.Check.isTruthy(true));
        assertTrue(Builtin.Check.isTruthy(0));
    }
}

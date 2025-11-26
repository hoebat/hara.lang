package hara.lang.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class AtomTest {

    @Test
    public void testAtom() {
        Atom.Standard<Integer> atom = new Atom.Standard<>(10);
        assertEquals(Integer.valueOf(10), atom.deref());

        atom.reset(20);
        assertEquals(Integer.valueOf(20), atom.deref());

        assertTrue(atom.cas(20, 30));
        assertEquals(Integer.valueOf(30), atom.deref());
        assertFalse(atom.cas(20, 40));
        assertEquals(Integer.valueOf(30), atom.deref());

        atom.swap(n -> n + 10);
        assertEquals(Integer.valueOf(40), atom.deref());
    }
}

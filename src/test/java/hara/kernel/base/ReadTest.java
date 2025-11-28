package hara.kernel.base;

import org.junit.Test;
import static org.junit.Assert.*;
import hara.lang.data.Symbol;
import hara.lang.data.List;
import hara.lang.data.Map;
import hara.lang.data.Keyword;
import hara.lang.data.OrderedMap;

public class ReadTest {

    @Test
    public void testReadStringNumber() {
        assertEquals(123L, Read.LispReader.readString("123", null));
        assertEquals(123.45, Read.LispReader.readString("123.45", null));
    }

    @Test
    public void testReadStringSymbol() {
        assertEquals(Symbol.create("a"), Read.LispReader.readString("a", null));
        assertEquals(Keyword.create("a"), Read.LispReader.readString(":a", null));
    }

    @Test
    public void testReadStringList() {
        Object result = Read.LispReader.readString("(+ 1 2)", null);
        assertTrue(result instanceof List);
        List list = (List) result;
        assertEquals(3, list.count());
        assertEquals(Symbol.create("+"), list.nth(0));
        assertEquals(1L, list.nth(1));
        assertEquals(2L, list.nth(2));
    }

    @Test
    public void testReadStringMap() {
        Object result = Read.LispReader.readString("{:a 1 :b 2}", null);
        assertTrue(result instanceof OrderedMap);
        OrderedMap map = (OrderedMap) result;
        assertEquals(2, map.count());
        assertEquals(1L, map.lookup(Keyword.create("a")));
        assertEquals(2L, map.lookup(Keyword.create("b")));
    }
}

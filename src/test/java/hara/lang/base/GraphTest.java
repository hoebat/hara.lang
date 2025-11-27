package hara.lang.base;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;

public class GraphTest {

    @Test
    public void testSizeOf() {
        // Primitive
        long sizeInt = Graph.sizeOf(Integer.valueOf(1));
        assertTrue(sizeInt > 0);

        // String
        String s = "hello";
        long sizeString = Graph.sizeOf(s);
        assertTrue(sizeString > 0);

        // Array
        int[] arr = new int[100];
        long sizeArr = Graph.sizeOf(arr);
        assertTrue(sizeArr >= 400);

        // Object graph
        ArrayList<Object> list = new ArrayList<>();
        list.add(new Integer(1));
        list.add(new String("test"));
        long sizeList = Graph.sizeOf(list);
        assertTrue(sizeList > sizeInt + sizeString);
    }
}

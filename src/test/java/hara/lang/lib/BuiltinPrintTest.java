package hara.lang.lib;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import hara.lang.base.Arr;

public class BuiltinPrintTest {

    @Test
    public void testPrintln() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            Builtin.Util.println(Arr.objects("Hello", "World"));
            System.out.flush();
            String output = baos.toString();
            // println adds newline
            assertEquals("Hello World" + System.lineSeparator(), output);
        } finally {
            System.setOut(originalOut);
        }
    }
}

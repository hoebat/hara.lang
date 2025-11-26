package hara.lang.kernel;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JSONTest {
    @Test
    public void testParse() {
        String json = "{\"pi\": 3.14}";
        assertEquals(3.14, JSON.parse(json), 0.001);
    }
}

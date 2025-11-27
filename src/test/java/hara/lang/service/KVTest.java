package hara.lang.service;

import hara.lang.kernel.Foundation;
import org.junit.Test;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class KVTest {

    @Test
    public void testKVBasic() {
        String testFile = "test_kv_basic.aof";
        new File(testFile).delete();

        KV kv = new KV(testFile);
        kv.load();

        kv.set("foo", "bar");
        assertEquals("bar", kv.get("foo"));

        kv.del("foo");
        assertNull(kv.get("foo"));

        kv.close();
        new File(testFile).delete();
    }

    @Test
    public void testPersistence() {
        String testFile = "test_kv_persist.aof";
        new File(testFile).delete();

        KV kv1 = new KV(testFile);
        kv1.load();
        kv1.set("a", "1");
        kv1.set("b", "2");
        kv1.del("b");
        kv1.close();

        KV kv2 = new KV(testFile);
        kv2.load();
        assertEquals("1", kv2.get("a"));
        assertNull(kv2.get("b"));
        kv2.close();

        new File(testFile).delete();
    }

    @Test
    public void testFoundationIntegration() {
        String testFile = "test_foundation_store.aof";
        new File(testFile).delete();

        // Pass specific store file to Foundation to avoid messing with default "dump.aof"
        Foundation f = new Foundation(testFile);

        f.call("STORE", "SET", "hello", "world");
        Object val = f.call("STORE", "GET", "hello");
        assertEquals("world", val);

        f.call("STORE", "DEL", "hello");
        Object val2 = f.call("STORE", "GET", "hello");
        assertNull(val2);

        f.STORE.close();
        new File(testFile).delete();
    }
}

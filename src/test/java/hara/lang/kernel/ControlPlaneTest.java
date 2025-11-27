package hara.lang.kernel;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import hara.lang.base.I;
import hara.lang.lib.RT;
import hara.lang.lib.Builtin;

public class ControlPlaneTest {

    @Test
    public void testFoundationRegistry() {
        Foundation f = new Foundation();

        // Test basic commands via runCommand (mimicking RESP)
        assertEquals("PONG", f.call("PING"));
        assertEquals(Arrays.asList("hello"), f.call("ECHO", "hello"));

        // Test sub-commands
        Object jvmVersion = f.call("JVM", "VERSION");
        assertNotNull(jvmVersion);
    }

    @Test
    public void testLispCtl() {
        // Setup a minimal runtime environment
        Foundation f = new Foundation();
        String sessionKey = "test-session";
        f.RTS.put(sessionKey, new RT.Instance(f, sessionKey));
        I.Runtime rt = f.RTS.get(sessionKey);

        // Test invoking ctl via Builtin (mimicking Lisp call)
        // (ctl :ping)
        Object res = Builtin.Runtime.ctl(rt, Arrays.asList("PING"));
        assertEquals("PONG", res);

        // (ctl :echo "foo")
        Object resEcho = Builtin.Runtime.ctl(rt, Arrays.asList("ECHO", "foo"));
        assertEquals(Arrays.asList("foo"), resEcho);
    }
}

package hara.kernel.command;

import org.junit.Test;
import static org.junit.Assert.*;
import hara.kernel.Foundation;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import hara.lang.base.Iter;

public class CoreTest {

  @Test
  public void testEcho() {
    Foundation f = new Foundation();
    List<Object> args = Arrays.asList("hello", "world");
    assertEquals(args, Core.cmdECHO(f, args));
  }

  @Test
  public void testPing() {
    Foundation f = new Foundation();
    assertEquals("PONG", Core.cmdPING(f, null));
  }

  @Test
  public void testHelp() {
    Foundation f = new Foundation();
    // Since Foundation registry is static/singleton-like or populated on init?
    // Foundation constructor initializes REGISTRY.
    Object res = Core.cmdHELP(f, null);
    assertNotNull(res);
    assertTrue(res instanceof List);
  }
}

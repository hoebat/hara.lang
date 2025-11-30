package hara.kernel.command;

import org.junit.Test;
import static org.junit.Assert.*;
import hara.kernel.Foundation;
import hara.lang.base.Ex;
import java.util.List;
import java.util.Arrays;

public class MavenTest {

  @Test(expected = Ex.Runtime.class)
  public void testLoadNoSession() {
    Foundation f = new Foundation();
    // Should throw because session "unknown" does not exist
    Maven.load(f, Arrays.asList("unknown", "lib"));
  }
}

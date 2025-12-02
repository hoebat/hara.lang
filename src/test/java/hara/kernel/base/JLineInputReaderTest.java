package hara.kernel.base;

import org.jline.reader.LineReader;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class JLineInputReaderTest {

  @Test
  public void testRead() throws IOException {
    LineReader mockLineReader =
        (LineReader)
            java.lang.reflect.Proxy.newProxyInstance(
                JLineInputReaderTest.class.getClassLoader(),
                new Class[] {LineReader.class},
                new java.lang.reflect.InvocationHandler() {
                  private int count = 0;

                  @Override
                  public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
                      throws Throwable {
                    if (method.getName().equals("readLine")
                        && args != null
                        && args.length == 1
                        && args[0] instanceof String) {
                      if (count == 0) {
                        count++;
                        return "line1";
                      } else if (count == 1) {
                        count++;
                        return "line2";
                      }
                      return null;
                    }
                    return null; // Default return for other methods
                  }
                });

    JLineInputReader reader = new JLineInputReader(mockLineReader);
    char[] cbuf = new char[100];

    // Read all lines (since buffer size 100 > total length 12)
    int len = reader.read(cbuf, 0, 100);
    assertEquals(12, len); // "line1\nline2\n"
    assertEquals("line1\nline2\n", new String(cbuf, 0, len));

    // Read EOF
    len = reader.read(cbuf, 0, 100);
    assertEquals(-1, len);
  }
}

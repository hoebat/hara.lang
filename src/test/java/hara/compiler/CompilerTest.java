package hara.compiler;

import java.util.function.Function;
import junit.framework.TestCase;
import hara.kernel.Foundation;

public class CompilerTest extends TestCase {

  private void assertCompile(String expression, Long expected) {
    Foundation f = new Foundation();
    try {
      Object result = f.call("COMPILE", expression);
      assertNotNull(result);
      assertTrue(result instanceof Function);
      @SuppressWarnings("unchecked")
      Function<Long, Long> fn = (Function<Long, Long>) result;
      assertEquals(expected, fn.apply(1L));
    } catch (Exception e) {
      e.printStackTrace();
      fail("Should not have thrown an exception: " + e);
    }
  }

  public void testCompile() {
    assertCompile("(fn [x] (+ x 1))", 2L);
  }

  public void testCompileWithDifferentLiteral() {
    assertCompile("(fn [x] (+ x 5))", 6L);
  }

  public void testCompileWithSubtraction() {
    assertCompile("(fn [x] (- x 5))", -4L);
  }

  public void testCompileWithMultiplication() {
    assertCompile("(fn [x] (* x 5))", 5L);
  }

  public void testCompileWithDivision() {
    assertCompile("(fn [x] (/ x 5))", 0L);
  }

  public void testCompileWithAlternateOperandOrder() {
    assertCompile("(fn [x] (+ 5 x))", 6L);
  }

  public void testCompileWithInvalidExpression() {
    Foundation f = new Foundation();
    try {
      f.call("COMPILE", "(fn [x] (+ y z))");
      fail("Should have thrown a CompilerException");
    } catch (Exception e) {
      assertTrue(
          "Expected CompilerException, but got " + e.getCause(),
          e.getCause() instanceof CompilerException);
      assertEquals("Unsupported expression format", e.getCause().getMessage());
    }
  }
}

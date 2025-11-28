package hara.kernel.base;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReflectTest {

  @Test
  public void testInvokeInstanceMethod() {
    String s = "hello";
    Object result = Reflect.invokeInstanceMethod(s, "toUpperCase", new Object[] {});
    assertEquals("HELLO", result);
  }

  @Test
  public void testInvokeStaticMethod() {
    Object result = Reflect.invokeStaticMethod(String.class, "valueOf", new Object[] {123});
    assertEquals("123", result);
  }

  public static class TestClass {
    public static String testMethod(String arg) {
      return "called with " + arg;
    }
  }

  @Test
  public void testInvokeMatchingMethod() {
    Object result =
        Reflect.invokeStaticMethod(TestClass.class, "testMethod", new Object[] {"test"});
    assertEquals("called with test", result);
  }
}

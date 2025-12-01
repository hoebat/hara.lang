package hara.kernel.base;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Tests for Java interop methods in {@link Builtin.Interop}. These tests target previously
 * uncovered interop functionality.
 */
public class BuiltinInteropTest {

  private RT.Instance runtime;

  @Before
  public void setUp() {
    runtime = new RT.Instance<>(null, "test");
  }

  @Test
  public void testClassFor() throws Exception {
    Class<?> clazz = Builtin.Interop.classFor(runtime, "java.lang.String");
    assertEquals(String.class, clazz);
  }

  @Test
  public void testClassConstructors() {
    java.lang.reflect.Constructor<?>[] constructors =
        Builtin.Interop.classConstructors(String.class);
    assertTrue("String should have constructors", constructors.length > 0);
  }

  @Test
  public void testClassMethods() {
    Method[] methods = Builtin.Interop.classMethods(String.class);
    assertTrue("String should have methods", methods.length > 0);
  }

  @Test
  public void testClassFields() {
    Field[] fields = Builtin.Interop.classFields(String.class);
    assertNotNull("classFields should not return null", fields);
  }

  @Test
  public void testInvokeNew() throws Exception {
    Object obj = Builtin.Interop.invokeNew(String.class, new Object[] {"hello"});
    assertTrue("invokeNew should create String instance", obj instanceof String);
    assertEquals("hello", obj);
  }

  @Test
  public void testInvokeObj() throws Exception {
    String str = "hello";
    Object result = Builtin.Interop.invokeObj(str, "toUpperCase", new Object[] {});
    assertEquals("HELLO", result);
  }

  @Test
  public void testInvokeGet() throws Exception {
    TestClass obj = new TestClass();
    Object result = Builtin.Interop.invokeGet(obj, "field");
    assertEquals("value", result);
  }

  public static class TestClass {
    public String field = "value";
  }

  @Test
  public void testInvokeStatic() throws Exception {
    Object result = Builtin.Interop.invokeStatic(Integer.class, "parseInt", new Object[] {"42"});
    assertEquals(42, result);
  }
}

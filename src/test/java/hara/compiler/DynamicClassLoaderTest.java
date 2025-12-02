package hara.compiler;

import junit.framework.TestCase;

public class DynamicClassLoaderTest extends TestCase {

  public void testConstructor() {
    ClassLoader parentClassLoader = getClass().getClassLoader();
    DynamicClassLoader classLoader = new DynamicClassLoader(parentClassLoader);
    assertNotNull(classLoader);
    assertEquals(parentClassLoader, classLoader.getParent());
  }

  public void testDefineClass() throws Exception {
    ClassLoader parentClassLoader = getClass().getClassLoader();
    DynamicClassLoader classLoader = new DynamicClassLoader(parentClassLoader);

    String className = "MyTestClass";
    byte[] bytecode =
        new byte[] {
          (byte) 0xCA,
          (byte) 0xFE,
          (byte) 0xBA,
          (byte) 0xBE, // Magic number
          0x00,
          0x00,
          0x00,
          0x34, // Java 8 version (52)
          0x00,
          0x0A, // Constant pool count
          0x0A,
          0x00,
          0x03,
          0x00,
          0x07, // Methodref #3.#7 <java/lang/Object."<init>":()V>
          0x07,
          0x00,
          0x08, // Class #8 <MyTestClass>
          0x07,
          0x00,
          0x09, // Class #9 <java/lang/Object>
          0x01,
          0x00,
          0x0B,
          'M',
          'y',
          'T',
          'e',
          's',
          't',
          'C',
          'l',
          'a',
          's',
          's', // Utf8 #4 <MyTestClass>
          0x01,
          0x00,
          0x06,
          '<',
          'i',
          'n',
          'i',
          't',
          '>', // Utf8 #5 <init>
          0x01,
          0x00,
          0x03,
          '(',
          ')',
          'V', // Utf8 #6 <()V>
          0x01,
          0x00,
          0x04,
          'C',
          'o',
          'd',
          'e', // Utf8 #7 <code>
          0x01,
          0x00,
          0x10,
          'j',
          'a',
          'v',
          'a',
          '/',
          'l',
          'a',
          'n',
          'g',
          '/',
          'O',
          'b',
          'j',
          'e',
          'c',
          't', // Utf8 #8 <java/lang/Object>
          0x01,
          0x00,
          0x0A,
          'S',
          'o',
          'u',
          'r',
          'c',
          'e',
          'F',
          'i',
          'l',
          'e', // Utf8 #9 <SourceFile>
          0x01,
          0x00,
          0x0E,
          'M',
          'y',
          'T',
          'e',
          's',
          't',
          'C',
          'l',
          'a',
          's',
          's',
          '.',
          'j',
          'a',
          'v',
          'a', // Utf8 #10 <MyTestClass.java>
          0x00,
          0x21, // Access flags (public, super)
          0x00,
          0x02, // This class #2 <MyTestClass>
          0x00,
          0x03, // Super class #3 <java/lang/Object>
          0x00,
          0x00, // Interfaces count
          0x00,
          0x00, // Fields count
          0x00,
          0x01, // Methods count
          0x00,
          0x01, // Access flags (public)
          0x00,
          0x05, // Name #5 <init>
          0x00,
          0x06, // Descriptor #6 <()V>
          0x00,
          0x01, // Attributes count
          0x00,
          0x07, // Attribute name #7 <code>
          0x00,
          0x00,
          0x00,
          0x11, // Attribute length
          0x00,
          0x01, // Max stack
          0x00,
          0x01, // Max locals
          0x00,
          0x00,
          0x00,
          0x05, // Code length
          0x2A, // aload_0
          (byte) 0xB7,
          0x00,
          0x01, // invokespecial #1 <java/lang/Object."<init>":()V>
          (byte) 0xB1, // return
          0x00,
          0x00, // Exception table length
          0x00,
          0x00, // Attributes count
          0x00,
          0x01, // Attributes count
          0x00,
          0x09, // Attribute name #9 <SourceFile>
          0x00,
          0x00,
          0x00,
          0x02, // Attribute length
          0x00,
          0x0A // SourceFile #10 <MyTestClass.java>
        };

    Class<?> definedClass = classLoader.defineClass(className, bytecode);
    assertNotNull(definedClass);
    assertEquals(className, definedClass.getName());
    assertEquals(classLoader, definedClass.getClassLoader());
  }
}

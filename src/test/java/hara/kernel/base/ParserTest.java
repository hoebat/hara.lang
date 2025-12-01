package hara.kernel.base;

import hara.lang.data.Keyword;
import hara.lang.data.List;
import hara.lang.data.OrderedMap;
import hara.lang.data.Symbol;
import hara.lang.data.Vector;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.*;

public class ParserTest {

  @Test
  public void testReadStringNumber() {
    assertEquals(123L, Parser.LispReader.readString("123", null));
    assertEquals(123.45, Parser.LispReader.readString("123.45", null));
    assertEquals(BigInteger.valueOf(123), Parser.LispReader.readString("123N", null));
    assertEquals(new BigDecimal("123.45"), Parser.LispReader.readString("123.45M", null));
    assertEquals(0xFFL, Parser.LispReader.readString("0xFF", null));
  }

  @Test
  public void testReadStringSymbol() {
    assertEquals(Symbol.create("a"), Parser.LispReader.readString("a", null));
    assertEquals(Keyword.create("a"), Parser.LispReader.readString(":a", null));
    assertEquals(Keyword.create("ns", "a"), Parser.LispReader.readString(":ns/a", null));
    assertEquals(Symbol.create("ns", "a"), Parser.LispReader.readString("ns/a", null));
  }

  @Test
  public void testReadStringString() {
    assertEquals("hello", Parser.LispReader.readString("\"hello\"", null));
    assertEquals("hello \"world\"", Parser.LispReader.readString("\"hello \\\"world\\\"\"", null));
  }

  @Test
  public void testReadStringChar() {
    assertEquals('a', Parser.LispReader.readString("\\a", null));
    assertEquals('\n', Parser.LispReader.readString("\\newline", null));
    assertEquals(' ', Parser.LispReader.readString("\\space", null));
  }

  @Test
  public void testReadStringList() {
    Object result = Parser.LispReader.readString("(+ 1 2)", null);
    assertTrue(result instanceof List);
    List list = (List) result;
    assertEquals(3, list.count());
    assertEquals(Symbol.create("+"), list.nth(0));
    assertEquals(1L, list.nth(1));
    assertEquals(2L, list.nth(2));
  }

  @Test
  public void testReadStringVector() {
    Object result = Parser.LispReader.readString("[1 2 3]", null);
    // Vectors <= 5 elements might be Tuples if not handled carefully, but Parser.VectorReader
    // checks size > 5
    // Parser.java: if (list.size() > 5) return vector(list); else return tuple(list.toArray());
    // So [1 2 3] is a Tuple.
    assertTrue(result instanceof hara.lang.data.types.ILinearType);
    hara.lang.data.types.ILinearType v = (hara.lang.data.types.ILinearType) result;
    assertEquals(3, v.count());
  }

  @Test
  public void testReadStringVectorLarge() {
    Object result = Parser.LispReader.readString("[1 2 3 4 5 6]", null);
    // > 5 elements -> Vector
    assertTrue(result instanceof Vector);
    Vector v = (Vector) result;
    assertEquals(6, v.count());
  }

  @Test
  public void testReadStringMap() {
    Object result = Parser.LispReader.readString("{:a 1 :b 2}", null);
    assertTrue(result instanceof OrderedMap);
    OrderedMap map = (OrderedMap) result;
    assertEquals(2, map.count());
    assertEquals(1L, map.lookup(Keyword.create("a")));
    assertEquals(2L, map.lookup(Keyword.create("b")));
  }

  @Test
  public void testReadQuote() {
    Object result = Parser.LispReader.readString("'a", null);
    assertTrue(result instanceof List);
    List l = (List) result;
    assertEquals(Symbol.create("quote"), l.nth(0));
    assertEquals(Symbol.create("a"), l.nth(1));
  }

  @Test
  public void testReadComment() {
    // Comments return the reader, which loop in read() ignores.
    // readString(";", null) -> might throw EOF if nothing else.
    try {
      Parser.LispReader.readString("; comment", null);
      fail("Should throw EOF");
    } catch (Exception e) {
      // Expected EOF
    }

    assertEquals(1L, Parser.LispReader.readString("; comment\n1", null));
  }

  @Test
  public void testReadMetadata() {
    Object result = Parser.LispReader.readString("^:foo [1]", null);
    assertTrue(result instanceof hara.lang.protocol.IObjType);
    hara.lang.protocol.IObjType obj = (hara.lang.protocol.IObjType) result;
    hara.lang.data.types.IMapType meta = (hara.lang.data.types.IMapType) obj.meta();
    assertEquals(Boolean.TRUE, meta.lookup(Keyword.create("foo")));
  }

  @Test
  public void testSyntaxQuote() {
    Object result = Parser.LispReader.readString("`a", null);
    assertTrue(result instanceof List);
    assertEquals(Symbol.create("syntax-quote"), ((List) result).nth(0));
  }

  @Test
  public void testReadUnmatchedDelimiter() {
    try {
      Parser.LispReader.readString(")", null);
      fail("Should throw RuntimeException for unmatched delimiter");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Unmatched delimiter"));
    }
  }

  @Test
  public void testReadUnfinishedString() {
    try {
      Parser.LispReader.readString("\"", null);
      fail("Should throw RuntimeException for EOF while reading string");
    } catch (Exception e) {
      // ReaderException wraps the actual exception
      assertTrue(e.getCause().getMessage().contains("EOF while reading string"));
    }
  }

  @Test
  public void testReadInvalidNumber() {
    try {
      // "123a" - Parser splits at macro/whitespace. 'a' is not macro/whitespace.
      // Wait, 123a is read as a token? No, readNumber reads until macro or whitespace.
      // If 1 starts, it calls readNumber.
      // readNumber loops until macro or whitespace. 'a' is neither.
      // So it reads "123a" and tries to matchNumber("123a").
      // matchNumber will return null.
      // Then it throws NumberFormatException.
      Parser.LispReader.readString("123a", null);
      fail("Should throw NumberFormatException");
    } catch (Exception e) {
      assertTrue(e.getCause() instanceof NumberFormatException);
    }
  }

  @Test
  public void testReadNilTrueFalse() {
    assertNull(Parser.LispReader.readString("nil", null));
    assertEquals(Boolean.TRUE, Parser.LispReader.readString("true", null));
    assertEquals(Boolean.FALSE, Parser.LispReader.readString("false", null));
  }

  @Test
  public void testDeref() {
    Object result = Parser.LispReader.readString("@a", null);
    assertTrue(result instanceof List);
    List l = (List) result;
    assertEquals(Symbol.create("deref"), l.nth(0));
    assertEquals(Symbol.create("a"), l.nth(1));
  }
}

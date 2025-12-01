package hara.kernel.base;

import hara.lang.base.Ex;
import hara.lang.data.*;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

public class EvalTest {

  private RT.Instance<Object> rt;

  @Before
  public void setUp() {
    rt = new RT.Instance<>(null, "test");
    // Ensure helper functions are bound if needed
    rt.eval(rt.readString("(def add (fn [a b] (+ a b)))"));
  }

  private void assertNumber(long expected, Object actual) {
    if (actual instanceof BigInteger) {
      assertEquals(BigInteger.valueOf(expected), actual);
    } else if (actual instanceof Long) {
      assertEquals(Long.valueOf(expected), actual);
    } else if (actual instanceof Integer) {
      assertEquals(Integer.valueOf((int) expected), actual);
    } else {
      fail("Expected number, got: " + (actual == null ? "null" : actual.getClass().getName()));
    }
  }

  // --- Symbol Evaluation ---

  @Test
  public void testEvalSymbol() {
    Symbol sym = Symbol.create("a");
    rt.setObj(sym, new Var(sym.getName(), 10));
    assertNumber(10, Eval.eval(sym, rt.getEnv()));
  }

  @Test(expected = Ex.Runtime.class)
  public void testEvalUnboundSymbol() {
    Eval.eval(Symbol.create("unbound-var"), rt.getEnv());
  }

  // --- List Evaluation ---

  @Test
  public void testEvalListFunctionCall() {
    // (+ 1 2)
    List list = List.Standard.from(null, Symbol.create("+"), 1, 2);
    assertNumber(3, Eval.eval(list, rt.getEnv()));
  }

  @Test
  public void testEvalListMacro() {
    // (if true 1 2)
    List list = List.Standard.from(null, Symbol.create("if"), true, 1, 2);
    assertNumber(1, Eval.eval(list, rt.getEnv()));
  }

  // --- Map Evaluation ---

  @Test
  public void testEvalMap() {
    // {:a (+ 1 2)} -> {:a 3}
    List expr = List.Standard.from(null, Symbol.create("+"), 1, 2);
    Map map = Map.Standard.from(null, Keyword.create("a"), expr);

    Object res = Eval.eval(map, rt.getEnv());
    assertTrue(res instanceof Map);
    assertNumber(3, ((Map) res).lookup(Keyword.create("a")));
  }

  @Test
  public void testEvalMapKeys() {
    // {(keyword "k") 1} -> {:k 1}
    List expr = List.Standard.from(null, Symbol.create("keyword"), "k");
    Map map = Map.Standard.from(null, expr, 1);

    Object res = Eval.eval(map, rt.getEnv());
    assertTrue(res instanceof Map);
    assertNumber(1, ((Map) res).lookup(Keyword.create("k")));
  }

  // --- Vector/Tuple Evaluation ---

  @Test
  public void testEvalVector() {
    // [(+ 1 1) 3] -> [2 3]
    List expr = List.Standard.from(null, Symbol.create("+"), 1, 1);
    Vector vec = Vector.Standard.from(null, expr, 3);

    Object res = Eval.eval(vec, rt.getEnv());

    // Note: Eval returns tuple for small vectors (<= 5)

    if (res instanceof Tuple.Tup2) {
      Tuple.Tup2 t = (Tuple.Tup2) res;
      assertEquals(2L, ((Number) t.A()).longValue()); // Eval returns BigInteger/Long
      assertEquals(3L, ((Number) t.B()).longValue());
    } else {
      Vector v = (Vector) res;
      assertNumber(2, v.nth(0));
      assertNumber(3, v.nth(1));
    }
  }

  @Test
  public void testEvalTuple() {
    // Tuple literal evaluation
    // Tuple.Tup2.L does not implement Tuple (interface namespace),
    // but let's check Tuple.Tup2

    // Let's use a list inside tuple
    List expr = List.Standard.from(null, Symbol.create("+"), 5, 5);
    Tuple.Tup2 input = new Tuple.Tup2.L(null, expr, 100);

    Object res = Eval.eval(input, rt.getEnv());
    // Should return a tuple (since count <= 5)
    assertTrue(res instanceof Tuple.Tup2);
    assertEquals(10L, ((Number) ((Tuple.Tup2) res).A()).longValue());
    assertEquals(100L, ((Number) ((Tuple.Tup2) res).B()).longValue());
  }

  // --- Apply ---

  @Test
  public void testApply() {
    Object fn = rt.eval(Symbol.create("+"));
    Object res = Eval.apply(fn, new Object[] {1, 2});
    assertNumber(3, res);
  }
}

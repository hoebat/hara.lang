package hara.transpile;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import hara.lang.data.Symbol;

public class TranspileTest {

  @Test
  public void testEmitBasic() {
    Map<String, Object> grammar = new HashMap<>();
    Map<String, Object> mopts = new HashMap<>();

    String res = Transpile.emit("hello", grammar, mopts);
    assertEquals("\"hello\"", res);

    res = Transpile.emit(123, grammar, mopts);
    assertEquals("123", res);

    Symbol sym = Symbol.create("foo");
    res = Transpile.emit(sym, grammar, mopts);
    assertEquals("foo", res);

    res = Transpile.emit(Arrays.asList(Symbol.create("add"), 1, 2), grammar, mopts);
    assertEquals("(add 1 2)", res);
  }
}

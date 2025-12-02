package hara.transpile;

import hara.transpile.base.Emit;
import java.util.Map;

public class Transpile {
  public static String emit(Object form, Map<String, Object> grammar, Map<String, Object> mopts) {
    return Emit.emitMain(form, grammar, mopts);
  }
}

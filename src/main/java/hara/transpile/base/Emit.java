package hara.transpile.base;

import java.util.Map;

public class Emit {
  public static String emitMainLoop(
      Object form, Map<String, Object> grammar, Map<String, Object> mopts) {
    return EmitCommon.emitCommonLoop(form, grammar, mopts, Emit::emitMainLoop);
  }

  public static String emitMain(
      Object form, Map<String, Object> grammar, Map<String, Object> mopts) {
    return emitMainLoop(form, grammar, mopts);
  }
}

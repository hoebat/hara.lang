package hara.transpile.base;

import java.util.Map;
import java.util.List;
import hara.lang.data.Symbol;
import hara.lang.data.Keyword;

public class EmitCommon {

  public interface EmitFn {
    String apply(Object form, Map<String, Object> grammar, Map<String, Object> mopts);
  }

  public static String emitCommonLoop(
      Object form, Map<String, Object> grammar, Map<String, Object> mopts, EmitFn emitFn) {
    // Basic implementation for now
    if (form instanceof String) {
      return "\"" + form + "\"";
    }
    if (form instanceof Number) {
      return form.toString();
    }
    if (form == null) {
      return "null";
    }
    if (form instanceof Symbol) {
      return ((Symbol) form).getName();
    }
    if (form instanceof Keyword) {
      return ((Keyword) form).getName();
    }
    if (form instanceof List) {
      List<?> list = (List<?>) form;
      if (list.isEmpty()) {
        return "()";
      }
      // Basic invoke handling
      Object first = list.get(0);
      String op = emitFn.apply(first, grammar, mopts);

      StringBuilder sb = new StringBuilder();
      sb.append("(").append(op);
      for (int i = 1; i < list.size(); i++) {
        sb.append(" ").append(emitFn.apply(list.get(i), grammar, mopts));
      }
      sb.append(")");
      return sb.toString();
    }

    return form.toString();
  }
}

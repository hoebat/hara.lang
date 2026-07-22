package hara.truffle;

import hara.lang.data.List;
import hara.lang.data.Symbol;
import hara.lang.data.Vector;
import hara.lang.data.types.ILinearType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Map;

final class HaraMacro {
  private static final int MAX_EXPANSION_CACHE_ENTRIES = 256;
  private static final Object NULL_EXPANSION = new Object();

  private final Symbol name;
  private final Symbol[] parameters;
  private final Object[] body;
  private final Map<InvocationKey, Object> expansionCache = new ConcurrentHashMap<>();

  HaraMacro(Symbol name, ILinearType<?> parameters, Object[] body) {
    this.name = name;
    this.parameters = new Symbol[(int) parameters.count()];
    for (int i = 0; i < parameters.count(); i++) {
      Object parameter = parameters.nth(i);
      if (!(parameter instanceof Symbol)) {
        throw new HaraException("defmacro parameter must be a symbol");
      }
      this.parameters[i] = (Symbol) parameter;
    }
    this.body = body;
  }

  Object expand(List<?> invocation) {
    if (invocation.count() - 1 != parameters.length) {
      throw new HaraException(
          name.display()
              + " expects "
              + parameters.length
              + " arguments, received "
              + (invocation.count() - 1));
    }

    InvocationKey cacheKey = new InvocationKey(invocation);
    Object cached = expansionCache.get(cacheKey);
    if (cached != null) {
      return cached == NULL_EXPANSION ? null : cached;
    }

    Map<Symbol, Object> environment = new HashMap<>();
    for (int i = 0; i < parameters.length; i++) {
      environment.put(parameters[i], invocation.nth(i + 1L));
    }

    Object result = null;
    for (Object form : body) {
      result = evaluate(form, environment);
    }
    if (expansionCache.size() < MAX_EXPANSION_CACHE_ENTRIES) {
      expansionCache.putIfAbsent(cacheKey, result == null ? NULL_EXPANSION : result);
    }
    return result;
  }

  private Object evaluate(Object form, Map<Symbol, Object> environment) {
    if (form instanceof Symbol) {
      return environment.getOrDefault(form, form);
    }
    if (!(form instanceof List<?>)) {
      return form;
    }

    List<?> list = (List<?>) form;
    if (list.count() == 0) {
      return list;
    }
    Object operator = list.nth(0);
    if (Symbol.create("quote").equals(operator)) {
      requireCount(list, 2, "quote");
      return list.nth(1);
    }
    if (Symbol.create("syntax-quote").equals(operator)) {
      requireCount(list, 2, "syntax-quote");
      return syntaxQuote(list.nth(1), environment);
    }
    if (Symbol.create("do").equals(operator)) {
      Object result = null;
      for (int i = 1; i < list.count(); i++) {
        result = evaluate(list.nth(i), environment);
      }
      return result;
    }
    if (Symbol.create("if").equals(operator)) {
      if (list.count() != 3 && list.count() != 4) {
        throw new HaraException("if expects two or three arguments");
      }
      Object condition = evaluate(list.nth(1), environment);
      if (truthy(condition)) {
        return evaluate(list.nth(2), environment);
      }
      return list.count() == 4 ? evaluate(list.nth(3), environment) : null;
    }
    if (Symbol.create("list").equals(operator)) {
      Object[] values = new Object[(int) list.count() - 1];
      for (int i = 1; i < list.count(); i++) {
        values[i - 1] = evaluate(list.nth(i), environment);
      }
      return List.Standard.from(null, values);
    }
    throw new HaraException("Unsupported macro expression: " + form);
  }

  private Object syntaxQuote(Object form, Map<Symbol, Object> environment) {
    if (form instanceof Symbol || form == null) {
      return form;
    }
    if (form instanceof List<?>) {
      List<?> list = (List<?>) form;
      if (isForm(list, "unquote")) {
        return evaluate(list.nth(1), environment);
      }
      if (isForm(list, "unquote-splicing")) {
        throw new HaraException("unquote-splicing is only valid inside a collection");
      }

      ArrayList<Object> values = new ArrayList<>();
      for (Object item : list) {
        appendQuoted(values, item, environment);
      }
      return List.Standard.from(null, values.toArray());
    }
    if (form instanceof Vector<?>) {
      ArrayList<Object> values = new ArrayList<>();
      for (Object item : (Vector<?>) form) {
        appendQuoted(values, item, environment);
      }
      return Vector.Standard.from(null, values.toArray());
    }
    return form;
  }

  private void appendQuoted(
      ArrayList<Object> values, Object item, Map<Symbol, Object> environment) {
    if (item instanceof List<?> && isForm((List<?>) item, "unquote-splicing")) {
      Object expanded = evaluate(((List<?>) item).nth(1), environment);
      if (!(expanded instanceof ILinearType<?>)) {
        throw new HaraException("unquote-splicing expects a sequential value");
      }
      for (Object value : (ILinearType<?>) expanded) {
        values.add(value);
      }
      return;
    }
    values.add(syntaxQuote(item, environment));
  }

  private boolean isForm(List<?> form, String name) {
    return form.count() == 2 && Symbol.create(name).equals(form.nth(0));
  }

  private void requireCount(List<?> form, long expected, String formName) {
    if (form.count() != expected) {
      throw new HaraException(formName + " expects " + (expected - 1) + " arguments");
    }
  }

  private boolean truthy(Object value) {
    return value != null && !Boolean.FALSE.equals(value);
  }

  private static final class InvocationKey {
    private final Object[] arguments;
    private final int hash;

    private InvocationKey(List<?> invocation) {
      arguments = new Object[(int) invocation.count() - 1];
      for (int i = 1; i < invocation.count(); i++) {
        arguments[i - 1] = invocation.nth(i);
      }
      hash = Arrays.deepHashCode(arguments);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof InvocationKey
          && Arrays.deepEquals(arguments, ((InvocationKey) other).arguments);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }
}

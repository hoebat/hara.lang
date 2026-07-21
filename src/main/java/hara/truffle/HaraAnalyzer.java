package hara.truffle;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import hara.lang.data.List;
import hara.lang.data.Symbol;
import hara.lang.data.types.ILinearType;
import hara.truffle.node.HaraExpressionNode;
import hara.truffle.node.HaraNodes;
import hara.truffle.node.HaraRootNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class HaraAnalyzer {
  private final HaraLanguage language;
  private final FrameDescriptor.Builder frames;
  private final Map<Symbol, Integer> locals;

  private HaraAnalyzer(
      HaraLanguage language, FrameDescriptor.Builder frames, Map<Symbol, Integer> locals) {
    this.language = language;
    this.frames = frames;
    this.locals = locals;
  }

  static com.oracle.truffle.api.RootCallTarget compile(HaraLanguage language, Object form) {
    FrameDescriptor.Builder frames = FrameDescriptor.newBuilder();
    HaraAnalyzer analyzer = new HaraAnalyzer(language, frames, Map.of());
    HaraExpressionNode body = analyzer.analyze(form);
    return new HaraRootNode(language, frames.build(), body, new int[0], true).getCallTarget();
  }

  private HaraExpressionNode analyze(Object form) {
    if (form instanceof Symbol) {
      Symbol symbol = (Symbol) form;
      Integer slot = locals.get(symbol);
      if (slot == null) {
        throw error("Unbound symbol: " + symbol.display());
      }
      return new HaraNodes.ReadLocal(slot);
    }
    if (!(form instanceof List<?>)) {
      return new HaraNodes.Literal(form);
    }
    List<?> list = (List<?>) form;
    if (list.count() == 0) {
      return new HaraNodes.Literal(list);
    }

    Object operator = list.nth(0);
    if (operator instanceof Symbol && ((Symbol) operator).getNamespace() == null) {
      String name = ((Symbol) operator).getName();
      switch (name) {
        case "quote":
          return analyzeQuote(list);
        case "do":
          return analyzeDo(list, 1);
        case "if":
          return analyzeIf(list);
        case "let":
          return analyzeLet(list);
        case "fn":
          return analyzeFunction(list);
        case "+":
          return analyzeAdd(list);
        default:
          return analyzeInvocation(list);
      }
    }
    return analyzeInvocation(list);
  }

  private HaraExpressionNode analyzeQuote(List<?> form) {
    requireCount(form, 2, "quote");
    return new HaraNodes.Literal(form.nth(1));
  }

  private HaraExpressionNode analyzeDo(List<?> form, int start) {
    HaraExpressionNode[] expressions = new HaraExpressionNode[(int) form.count() - start];
    for (int i = start; i < form.count(); i++) {
      expressions[i - start] = analyze(form.nth(i));
    }
    return new HaraNodes.Do(expressions);
  }

  private HaraExpressionNode analyzeIf(List<?> form) {
    if (form.count() != 3 && form.count() != 4) {
      throw error("if expects two or three arguments");
    }
    HaraExpressionNode alternative =
        form.count() == 4 ? analyze(form.nth(3)) : new HaraNodes.Literal(null);
    return new HaraNodes.If(analyze(form.nth(1)), analyze(form.nth(2)), alternative);
  }

  private HaraExpressionNode analyzeLet(List<?> form) {
    if (form.count() < 3 || !isBindingVector(form.nth(1))) {
      throw error("let expects a binding vector and a body");
    }
    ILinearType<?> bindings = (ILinearType<?>) form.nth(1);
    if (bindings.count() % 2 != 0) {
      throw error("let expects an even number of binding forms");
    }

    int bindingCount = (int) bindings.count() / 2;
    int[] slots = new int[bindingCount];
    HaraExpressionNode[] initializers = new HaraExpressionNode[bindingCount];
    Map<Symbol, Integer> bodyLocals = new HashMap<>(locals);
    Set<Symbol> bindingNames = new HashSet<>();
    for (int i = 0; i < bindingCount; i++) {
      Object name = bindings.nth(i * 2L);
      if (!(name instanceof Symbol)) {
        throw error("let binding name must be a symbol");
      }
      Symbol symbol = (Symbol) name;
      if (!bindingNames.add(symbol)) {
        throw error("Duplicate let binding: " + symbol.display());
      }
      initializers[i] = analyze(bindings.nth(i * 2L + 1));
      slots[i] = frames.addSlot(FrameSlotKind.Object, symbol, null);
      bodyLocals.put(symbol, slots[i]);
    }

    HaraAnalyzer bodyAnalyzer = new HaraAnalyzer(language, frames, bodyLocals);
    HaraExpressionNode body = bodyAnalyzer.analyzeDo(form, 2);
    return new HaraNodes.Let(slots, initializers, body);
  }

  private HaraExpressionNode analyzeFunction(List<?> form) {
    if (form.count() < 3 || !isBindingVector(form.nth(1))) {
      throw error("fn expects a parameter vector and a body");
    }
    ILinearType<?> parameters = (ILinearType<?>) form.nth(1);

    FrameDescriptor.Builder functionFrames = FrameDescriptor.newBuilder();
    Map<Symbol, Integer> functionLocals = new HashMap<>();
    int[] parameterSlots = new int[(int) parameters.count()];
    for (int i = 0; i < parameters.count(); i++) {
      Object parameter = parameters.nth(i);
      if (!(parameter instanceof Symbol)) {
        throw error("fn parameter must be a symbol");
      }
      Symbol symbol = (Symbol) parameter;
      if (functionLocals.containsKey(symbol)) {
        throw error("Duplicate fn parameter: " + symbol.display());
      }
      parameterSlots[i] = functionFrames.addSlot(FrameSlotKind.Object, symbol, null);
      functionLocals.put(symbol, parameterSlots[i]);
    }

    HaraAnalyzer functionAnalyzer = new HaraAnalyzer(language, functionFrames, functionLocals);
    HaraExpressionNode body = functionAnalyzer.analyzeDo(form, 2);
    HaraRootNode root =
        new HaraRootNode(language, functionFrames.build(), body, parameterSlots, false);
    return new HaraNodes.FunctionLiteral(
        new HaraFunction(root.getCallTarget(), parameterSlots.length));
  }

  private HaraExpressionNode analyzeAdd(List<?> form) {
    requireCount(form, 3, "+");
    return new HaraNodes.Add(analyze(form.nth(1)), analyze(form.nth(2)));
  }

  private HaraExpressionNode analyzeInvocation(List<?> form) {
    HaraExpressionNode target = analyze(form.nth(0));
    HaraExpressionNode[] arguments = new HaraExpressionNode[(int) form.count() - 1];
    for (int i = 1; i < form.count(); i++) {
      arguments[i - 1] = analyze(form.nth(i));
    }
    return new HaraNodes.Invoke(target, arguments);
  }

  private void requireCount(List<?> form, long expected, String name) {
    if (form.count() != expected) {
      throw error(name + " expects " + (expected - 1) + " arguments");
    }
  }

  private HaraException error(String message) {
    return new HaraException(message);
  }

  private boolean isBindingVector(Object value) {
    return value instanceof ILinearType<?> && "[".equals(((ILinearType<?>) value).startString());
  }
}

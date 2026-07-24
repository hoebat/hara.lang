package hara.truffle;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import hara.lang.data.Keyword;
import hara.lang.data.List;
import hara.lang.data.Symbol;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.IMapType;
import hara.truffle.node.HaraExpressionNode;
import hara.truffle.node.HaraNodes;
import hara.truffle.node.HaraRootNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Direct node lowerer for the closed std.lib.foundation bootstrap subset.
 *
 * <p>This is intentionally not a general Hara analyzer. Unsupported forms fail artifact loading,
 * allowing auto mode to use source and making strict mode expose compiler/runtime skew.
 */
final class FoundationHirLowerer {
  private static final AtomicLong COMPILATIONS = new AtomicLong();
  private final HaraLanguage language;
  private final HaraContext context;
  private final FrameDescriptor.Builder frames;
  private final Map<Symbol, Integer> locals;
  private final HaraNodes.RecurTarget recurTarget;

  private FoundationHirLowerer(
      HaraLanguage language,
      HaraContext context,
      FrameDescriptor.Builder frames,
      Map<Symbol, Integer> locals,
      HaraNodes.RecurTarget recurTarget) {
    this.language = language;
    this.context = context;
    this.frames = frames;
    this.locals = locals;
    this.recurTarget = recurTarget;
  }

  static RootCallTarget compile(HaraLanguage language, HaraContext context, Object[] forms) {
    COMPILATIONS.incrementAndGet();
    FrameDescriptor.Builder frames = FrameDescriptor.newBuilder();
    FoundationHirLowerer lowerer =
        new FoundationHirLowerer(language, context, frames, Map.of(), null);
    HaraExpressionNode body = lowerer.lowerForms(forms);
    return new HaraRootNode(
            language,
            frames.build(),
            body,
            new int[0],
            new int[0],
            new int[0],
            null,
            true,
            false)
        .getCallTarget();
  }

  static long compilationCount() {
    return COMPILATIONS.get();
  }

  private HaraExpressionNode lower(Object form) {
    if (form instanceof Symbol symbol) {
      Integer slot = locals.get(symbol);
      return slot == null
          ? new HaraNodes.ReadGlobal(context.canonicalSymbol(symbol))
          : new HaraNodes.ReadLocal(slot);
    }
    if (!(form instanceof List<?> list)) {
      HaraExpressionNode collection = lowerCollection(form);
      return collection == null ? new HaraNodes.Literal(form) : collection;
    }
    if (list.count() == 0) return new HaraNodes.Literal(list);
    Object operator = list.nth(0);
    if (!(operator instanceof Symbol symbol) || symbol.getNamespace() != null) {
      return lowerInvocation(list);
    }
    return switch (symbol.getName()) {
      case "quote" -> lowerQuote(list);
      case "if" -> lowerIf(list);
      case "let" -> lowerLet(list);
      case "loop" -> lowerLoop(list);
      case "recur" -> lowerRecur(list);
      case "fn" -> lowerFn(list);
      case "defn" -> lowerDefn(list);
      case "ns" -> lowerNamespace(list);
      case "+" -> lowerAdd(list);
      case "-" -> lowerVariadicNumeric(list, HaraNodes.Numeric.Operator.SUBTRACT, 0L);
      case "mod" -> lowerNumeric(list, HaraNodes.Numeric.Operator.REMAINDER);
      case "=" -> lowerCompare(list, HaraNodes.Compare.Operator.EQUAL);
      case "<" -> lowerCompare(list, HaraNodes.Compare.Operator.LESS);
      case ">" -> lowerCompare(list, HaraNodes.Compare.Operator.GREATER);
      default -> lowerInvocation(list);
    };
  }

  private HaraExpressionNode lowerForms(Object[] forms) {
    HaraExpressionNode[] nodes = new HaraExpressionNode[forms.length];
    for (int index = 0; index < forms.length; index++) nodes[index] = lower(forms[index]);
    return new HaraNodes.Do(nodes);
  }

  private HaraExpressionNode lowerCollection(Object form) {
    if (form instanceof IMapType<?, ?> map) {
      ArrayList<HaraExpressionNode> nodes = new ArrayList<>();
      for (Object value : map) {
        java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) value;
        nodes.add(lower(entry.getKey()));
        nodes.add(lower(entry.getValue()));
      }
      HaraNodes.CollectionLiteral.Kind kind =
          form instanceof hara.lang.data.OrderedMap
              ? HaraNodes.CollectionLiteral.Kind.ORDERED_MAP
              : HaraNodes.CollectionLiteral.Kind.MAP;
      return new HaraNodes.CollectionLiteral(kind, nodes.toArray(HaraExpressionNode[]::new));
    }
    if (form instanceof ILinearType<?> linear) {
      HaraExpressionNode[] nodes = new HaraExpressionNode[(int) linear.count()];
      for (int index = 0; index < nodes.length; index++) nodes[index] = lower(linear.nth(index));
      return new HaraNodes.CollectionLiteral(HaraNodes.CollectionLiteral.Kind.VECTOR, nodes);
    }
    return null;
  }

  private HaraExpressionNode lowerQuote(List<?> form) {
    requireCount(form, 2, "quote");
    return new HaraNodes.Literal(form.nth(1));
  }

  private HaraExpressionNode lowerIf(List<?> form) {
    if (form.count() != 3 && form.count() != 4) fail("if expects two or three arguments");
    return new HaraNodes.If(
        lower(form.nth(1)),
        lower(form.nth(2)),
        form.count() == 4 ? lower(form.nth(3)) : new HaraNodes.Literal(null));
  }

  private HaraExpressionNode lowerLet(List<?> form) {
    if (form.count() < 3 || !bindingVector(form.nth(1))) {
      fail("let expects a binding vector and a body");
    }
    ILinearType<?> bindings = (ILinearType<?>) form.nth(1);
    if ((bindings.count() & 1) != 0) fail("let expects even bindings");
    Map<Symbol, Integer> bodyLocals = new HashMap<>(locals);
    HaraExpressionNode body;
    ArrayList<Integer> slots = new ArrayList<>();
    ArrayList<HaraExpressionNode> initializers = new ArrayList<>();
    for (int index = 0; index < bindings.count(); index += 2) {
      Object binding = bindings.nth(index);
      if (!(binding instanceof Symbol symbol) || symbol.getNamespace() != null) {
        fail("foundation HIR supports symbol let bindings only");
      }
      Symbol symbol = (Symbol) binding;
      FoundationHirLowerer initializerLowerer =
          new FoundationHirLowerer(language, context, frames, bodyLocals, recurTarget);
      initializers.add(initializerLowerer.lower(bindings.nth(index + 1)));
      int slot = frames.addSlot(FrameSlotKind.Object, symbol, null);
      slots.add(slot);
      bodyLocals.put(symbol, slot);
    }
    FoundationHirLowerer bodyLowerer =
        new FoundationHirLowerer(language, context, frames, bodyLocals, recurTarget);
    body = bodyLowerer.lowerBody(form, 2);
    for (int index = slots.size() - 1; index >= 0; index--) {
      body =
          new HaraNodes.Let(
              new int[] {slots.get(index)},
              new HaraExpressionNode[] {initializers.get(index)},
              body);
    }
    return body;
  }

  private HaraExpressionNode lowerLoop(List<?> form) {
    if (form.count() < 3 || !bindingVector(form.nth(1))) {
      fail("loop expects a binding vector and a body");
    }
    ILinearType<?> bindings = (ILinearType<?>) form.nth(1);
    if ((bindings.count() & 1) != 0) fail("loop expects even bindings");
    int count = (int) bindings.count() / 2;
    int[] slots = new int[count];
    HaraExpressionNode[] initializers = new HaraExpressionNode[count];
    Map<Symbol, Integer> bodyLocals = new HashMap<>(locals);
    for (int index = 0; index < count; index++) {
      Object binding = bindings.nth(index * 2L);
      if (!(binding instanceof Symbol symbol) || symbol.getNamespace() != null) {
        fail("foundation HIR supports symbol loop bindings only");
      }
      Symbol symbol = (Symbol) binding;
      initializers[index] = lower(bindings.nth(index * 2L + 1));
      slots[index] = frames.addSlot(FrameSlotKind.Object, symbol, null);
      bodyLocals.put(symbol, slots[index]);
    }
    HaraNodes.RecurTarget target = new HaraNodes.RecurTarget(count);
    FoundationHirLowerer bodyLowerer =
        new FoundationHirLowerer(language, context, frames, bodyLocals, target);
    return new HaraNodes.Loop(target, slots, initializers, bodyLowerer.lowerBody(form, 2));
  }

  private HaraExpressionNode lowerRecur(List<?> form) {
    if (recurTarget == null) fail("recur used outside loop");
    if (form.count() - 1 != recurTarget.arity()) fail("recur arity mismatch");
    HaraExpressionNode[] values = new HaraExpressionNode[recurTarget.arity()];
    for (int index = 0; index < values.length; index++) values[index] = lower(form.nth(index + 1));
    return new HaraNodes.Recur(recurTarget, values);
  }

  private HaraExpressionNode lowerFn(List<?> form) {
    if (form.count() >= 3 && bindingVector(form.nth(1))) {
      return lowerFunction((ILinearType<?>) form.nth(1), bodyForms(form, 2));
    }
    HaraExpressionNode[] alternatives = new HaraExpressionNode[(int) form.count() - 1];
    for (int index = 1; index < form.count(); index++) {
      List<?> clause = requireClause(form.nth(index), "fn");
      alternatives[index - 1] =
          lowerFunction((ILinearType<?>) clause.nth(0), bodyForms(clause, 1));
    }
    return new HaraNodes.MultiFunction(alternatives);
  }

  private HaraExpressionNode lowerFunction(ILinearType<?> parameters, Object[] bodyForms) {
    FrameDescriptor.Builder functionFrames = FrameDescriptor.newBuilder();
    Map<Symbol, Integer> functionLocals = new HashMap<>();
    int restIndex = -1;
    for (int index = 0; index < parameters.count(); index++) {
      Object parameter = parameters.nth(index);
      if (parameter instanceof Symbol symbol && "&".equals(symbol.getName())) restIndex = index;
    }
    boolean variadic = restIndex >= 0;
    int fixedArity = variadic ? restIndex : (int) parameters.count();
    int[] parameterSlots = new int[fixedArity + (variadic ? 1 : 0)];
    for (int index = 0; index < parameterSlots.length; index++) {
      int sourceIndex = variadic && index == fixedArity ? restIndex + 1 : index;
      Object parameter = parameters.nth(sourceIndex);
      if (!(parameter instanceof Symbol symbol)
          || symbol.getNamespace() != null
          || "&".equals(symbol.getName())) {
        fail("foundation HIR supports symbol parameters only");
      }
      Symbol symbol = (Symbol) parameter;
      parameterSlots[index] = functionFrames.addSlot(FrameSlotKind.Object, symbol, null);
      functionLocals.put(symbol, parameterSlots[index]);
    }
    if (variadic && restIndex + 2 != parameters.count()) fail("invalid variadic parameters");

    int[] captureSlots = new int[locals.size()];
    int[] captureSources = new int[locals.size()];
    int captureIndex = 0;
    for (Map.Entry<Symbol, Integer> entry : locals.entrySet()) {
      if (functionLocals.containsKey(entry.getKey())) continue;
      int slot = functionFrames.addSlot(FrameSlotKind.Object, entry.getKey(), null);
      functionLocals.put(entry.getKey(), slot);
      captureSlots[captureIndex] = slot;
      captureSources[captureIndex] = entry.getValue();
      captureIndex++;
    }
    if (captureIndex != captureSlots.length) {
      captureSlots = java.util.Arrays.copyOf(captureSlots, captureIndex);
      captureSources = java.util.Arrays.copyOf(captureSources, captureIndex);
    }
    FoundationHirLowerer functionLowerer =
        new FoundationHirLowerer(language, context, functionFrames, functionLocals, null);
    HaraExpressionNode functionBody = functionLowerer.lowerForms(bodyForms);
    HaraRootNode root =
        new HaraRootNode(
            language,
            functionFrames.build(),
            functionBody,
            parameterSlots,
            captureSlots,
            captureSources,
            null,
            false,
            variadic);
    return new HaraNodes.FunctionLiteral(
        root.getCallTarget(), fixedArity, variadic, captureSlots.length != 0);
  }

  private HaraExpressionNode lowerDefn(List<?> form) {
    if (form.count() < 4 || !(form.nth(1) instanceof Symbol rawName)) {
      fail("defn expects a name, parameters, and body");
    }
    Symbol rawName = (Symbol) form.nth(1);
    Symbol name = definitionSymbol(rawName, form);
    context.declareCurrent(name);
    int parametersIndex = 2;
    String doc = null;
    if (form.nth(parametersIndex) instanceof String value) {
      doc = value;
      parametersIndex++;
    }
    IMapType<?, ?> attributes = null;
    if (form.nth(parametersIndex) instanceof IMapType<?, ?> value) {
      attributes = value;
      parametersIndex++;
    }
    Object parameterForm = form.nth(parametersIndex);
    HaraExpressionNode function;
    Object[] signatures;
    if (bindingVector(parameterForm)) {
      signatures = new Object[] {parameterForm};
      function =
          lowerFunction(
              (ILinearType<?>) parameterForm,
              bodyForms(form, parametersIndex + 1));
    } else {
      int count = (int) form.count() - parametersIndex;
      HaraExpressionNode[] alternatives = new HaraExpressionNode[count];
      signatures = new Object[count];
      for (int index = 0; index < count; index++) {
        List<?> clause = requireClause(form.nth(parametersIndex + index), "defn");
        signatures[index] = clause.nth(0);
        alternatives[index] =
            lowerFunction((ILinearType<?>) clause.nth(0), bodyForms(clause, 1));
      }
      function = new HaraNodes.MultiFunction(alternatives);
    }
    IMapType<Object, Object> metadata =
        name.meta() instanceof IMapType<?, ?>
            ? (IMapType<Object, Object>) name.meta()
            : hara.lang.data.Map.Standard.EMPTY;
    if (attributes != null) {
      for (Object value : attributes) {
        java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) value;
        metadata = (IMapType<Object, Object>) metadata.assoc(entry.getKey(), entry.getValue());
      }
    }
    if (doc != null) metadata = (IMapType<Object, Object>) metadata.assoc(Keyword.create("doc"), doc);
    metadata =
        (IMapType<Object, Object>)
            metadata.assoc(
                Keyword.create("arglists"),
                hara.lang.data.Vector.Standard.from(null, signatures));
    return new HaraNodes.DefineGlobal(name.withMeta(metadata), function);
  }

  private HaraExpressionNode lowerNamespace(List<?> form) {
    if (form.count() < 2 || !(form.nth(1) instanceof Symbol name)) fail("invalid ns form");
    Symbol name = (Symbol) form.nth(1);
    Object[] clauses = bodyForms(form, 2);
    context.setCurrentNamespace(name, clauses);
    return new HaraNodes.SetNamespace(name, clauses);
  }

  private HaraExpressionNode lowerAdd(List<?> form) {
    if (form.count() == 1) return new HaraNodes.Literal(0L);
    HaraExpressionNode result = lower(form.nth(1));
    for (int index = 2; index < form.count(); index++) {
      result = new HaraNodes.Add(result, lower(form.nth(index)));
    }
    return result;
  }

  private HaraExpressionNode lowerNumeric(
      List<?> form, HaraNodes.Numeric.Operator operator) {
    requireCount(form, 3, operator.name());
    return new HaraNodes.Numeric(operator, lower(form.nth(1)), lower(form.nth(2)));
  }

  private HaraExpressionNode lowerVariadicNumeric(
      List<?> form, HaraNodes.Numeric.Operator operator, long identity) {
    if (form.count() == 1) fail("numeric operation expects an argument");
    HaraExpressionNode result =
        form.count() == 2
            ? new HaraNodes.Numeric(operator, new HaraNodes.Literal(identity), lower(form.nth(1)))
            : lower(form.nth(1));
    for (int index = 2; index < form.count(); index++) {
      result = new HaraNodes.Numeric(operator, result, lower(form.nth(index)));
    }
    return result;
  }

  private HaraExpressionNode lowerCompare(
      List<?> form, HaraNodes.Compare.Operator operator) {
    if (form.count() < 3) fail("comparison expects at least two arguments");
    if (form.count() == 3) {
      return new HaraNodes.Compare(operator, lower(form.nth(1)), lower(form.nth(2)));
    }
    HaraExpressionNode[] values = new HaraExpressionNode[(int) form.count() - 1];
    for (int index = 1; index < form.count(); index++) values[index - 1] = lower(form.nth(index));
    return new HaraNodes.CompareChain(operator, values);
  }

  private HaraExpressionNode lowerInvocation(List<?> form) {
    HaraExpressionNode[] arguments = new HaraExpressionNode[(int) form.count() - 1];
    for (int index = 1; index < form.count(); index++) arguments[index - 1] = lower(form.nth(index));
    return new HaraNodes.Invoke(lower(form.nth(0)), arguments);
  }

  private HaraExpressionNode lowerBody(List<?> form, int start) {
    return lowerForms(bodyForms(form, start));
  }

  private static Object[] bodyForms(List<?> form, int start) {
    Object[] body = new Object[(int) form.count() - start];
    for (int index = start; index < form.count(); index++) body[index - start] = form.nth(index);
    return body;
  }

  private static List<?> requireClause(Object value, String owner) {
    if (!(value instanceof List<?> clause)
        || clause.count() < 2
        || !bindingVector(clause.nth(0))) {
      fail(owner + " requires parameter-vector clauses");
    }
    return (List<?>) value;
  }

  @SuppressWarnings("unchecked")
  private static Symbol definitionSymbol(Symbol symbol, List<?> form) {
    IMapType<Object, Object> metadata =
        symbol.meta() instanceof IMapType<?, ?>
            ? (IMapType<Object, Object>) symbol.meta()
            : hara.lang.data.Map.Standard.EMPTY;
    if (form.meta() instanceof IMapType<?, ?> source) {
      for (String key : new String[] {"file", "line", "column", "end-line", "end-column"}) {
        Keyword keyword = Keyword.create(key);
        Object value = ((IMapType<Object, Object>) source).lookup(keyword);
        if (value != null) metadata = (IMapType<Object, Object>) metadata.assoc(keyword, value);
      }
    }
    return symbol.withMeta(metadata);
  }

  private static boolean bindingVector(Object value) {
    return value instanceof ILinearType<?> linear && "[".equals(linear.startString());
  }

  private static void requireCount(List<?> form, long count, String name) {
    if (form.count() != count) fail(name + " has invalid arity");
  }

  private static void fail(String message) {
    throw new HaraException("Invalid foundation executable HIR: " + message);
  }
}

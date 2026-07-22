package hara.truffle;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.SourceSection;
import hara.lang.data.Keyword;
import hara.lang.data.List;
import hara.lang.data.Symbol;
import hara.lang.data.types.IMapType;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.ISetType;
import hara.truffle.node.HaraExpressionNode;
import hara.truffle.node.HaraNodes;
import hara.truffle.node.HaraRootNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class HaraAnalyzer {
  private final HaraLanguage language;
  private final HaraContext context;
  private final SourceSection sourceSection;
  private final FrameDescriptor.Builder frames;
  private final Map<Symbol, Integer> locals;
  private final Map<Symbol, Integer> enclosingLocals;
  private final Map<Symbol, Integer> captureSlots;
  private final Map<Symbol, Integer> captureSources;
  private final HaraNodes.RecurTarget recurTarget;

  private HaraAnalyzer(
      HaraLanguage language,
      SourceSection sourceSection,
      HaraContext context,
      FrameDescriptor.Builder frames,
      Map<Symbol, Integer> locals,
      Map<Symbol, Integer> enclosingLocals,
      Map<Symbol, Integer> captureSlots,
      Map<Symbol, Integer> captureSources,
      HaraNodes.RecurTarget recurTarget) {
    this.language = language;
    this.context = context;
    this.sourceSection = sourceSection;
    this.frames = frames;
    this.locals = locals;
    this.enclosingLocals = enclosingLocals;
    this.captureSlots = captureSlots;
    this.captureSources = captureSources;
    this.recurTarget = recurTarget;
  }

  static com.oracle.truffle.api.RootCallTarget compile(
      HaraLanguage language, Object[] forms, SourceSection sourceSection, HaraContext context) {
    FrameDescriptor.Builder frames = FrameDescriptor.newBuilder();
    HaraAnalyzer analyzer =
        new HaraAnalyzer(
            language, sourceSection, context, frames, Map.of(), Map.of(), Map.of(), Map.of(), null);
    HaraExpressionNode[] expressions = new HaraExpressionNode[forms.length];
    for (int i = 0; i < forms.length; i++) {
      expressions[i] = analyzer.analyze(forms[i]);
    }
    HaraExpressionNode body = new HaraNodes.Do(expressions);
    return new HaraRootNode(
            language,
            frames.build(),
            body,
            new int[0],
            new int[0],
            new int[0],
            sourceSection,
            true,
            false)
        .getCallTarget();
  }

  private HaraExpressionNode analyze(Object form) {
    Object expanded = expandMacro(form);
    if (expanded != form) {
      return analyze(expanded);
    }
    if (form instanceof Symbol) {
      Symbol symbol = (Symbol) form;
      Integer slot = locals.get(symbol);
      if (slot == null) {
        if (enclosingLocals.containsKey(symbol)) {
          slot = captureSlots.get(symbol);
          if (slot == null) {
            slot = frames.addSlot(FrameSlotKind.Object, symbol, null);
            captureSlots.put(symbol, slot);
            captureSources.put(symbol, enclosingLocals.get(symbol));
            locals.put(symbol, slot);
          }
          return new HaraNodes.ReadLocal(slot);
        }
        return new HaraNodes.ReadGlobal(symbol);
      }
      return new HaraNodes.ReadLocal(slot);
    }
    if (!(form instanceof List<?>)) {
      HaraExpressionNode collection = analyzeCollectionLiteral(form);
      if (collection != null) {
        return collection;
      }
      return new HaraNodes.Literal(form);
    }
    List<?> list = (List<?>) form;
    if (list.count() == 0) {
      return new HaraNodes.Literal(list);
    }

    Object operator = list.nth(0);
    if (operator instanceof Symbol) {
      Symbol symbolOperator = (Symbol) operator;
      if ("x".equals(symbolOperator.getNamespace())
          || "x:array".equals(symbolOperator.getName())
          || "x:object".equals(symbolOperator.getName())
          || "x:len".equals(symbolOperator.getName())
          || "x:get".equals(symbolOperator.getName())
          || "x:set".equals(symbolOperator.getName())
          || "x:delete".equals(symbolOperator.getName())
          || "x:append".equals(symbolOperator.getName())
          || "x:insert".equals(symbolOperator.getName())
          || "x:remove".equals(symbolOperator.getName())
          || "x:clone".equals(symbolOperator.getName())
          || "x:slice".equals(symbolOperator.getName())) {
        if ("array".equals(symbolOperator.getName())
            || "x:array".equals(symbolOperator.getName())) {
          return analyzeMutableCollection(list, HaraNodes.CollectionLiteral.Kind.MUTABLE_ARRAY);
        }
        if ("object".equals(symbolOperator.getName())
            || "x:object".equals(symbolOperator.getName())) {
          return analyzeMutableCollection(list, HaraNodes.CollectionLiteral.Kind.MUTABLE_OBJECT);
        }
        String mutableName = symbolOperator.getName();
        if ("len".equals(mutableName) || "x:len".equals(mutableName)) {
          return analyzeMutableOperation(list, HaraNodes.MutableOperation.Operator.LENGTH, 2, 2);
        }
        if ("get".equals(mutableName) || "x:get".equals(mutableName)) {
          return analyzeMutableOperation(list, HaraNodes.MutableOperation.Operator.GET, 3, 4);
        }
        if ("set".equals(mutableName) || "x:set".equals(mutableName)) {
          return analyzeMutableOperation(list, HaraNodes.MutableOperation.Operator.SET, 4, 4);
        }
        if ("delete".equals(mutableName) || "x:delete".equals(mutableName)) {
          return analyzeMutableOperation(list, HaraNodes.MutableOperation.Operator.DELETE, 3, 3);
        }
        if ("append".equals(mutableName) || "x:append".equals(mutableName)) {
          return analyzeMutableOperation(list, HaraNodes.MutableOperation.Operator.APPEND, 3, 3);
        }
        if ("insert".equals(mutableName) || "x:insert".equals(mutableName)) {
          return analyzeMutableOperation(list, HaraNodes.MutableOperation.Operator.INSERT, 4, 4);
        }
        if ("remove".equals(mutableName) || "x:remove".equals(mutableName)) {
          return analyzeMutableOperation(list, HaraNodes.MutableOperation.Operator.REMOVE, 3, 3);
        }
        if ("clone".equals(mutableName) || "x:clone".equals(mutableName)) {
          return analyzeMutableOperation(list, HaraNodes.MutableOperation.Operator.CLONE, 2, 2);
        }
        if ("slice".equals(mutableName) || "x:slice".equals(mutableName)) {
          return analyzeMutableOperation(list, HaraNodes.MutableOperation.Operator.SLICE, 3, 4);
        }
      }
      if (symbolOperator.getNamespace() != null) {
        return analyzeInvocation(list);
      }
      String name = symbolOperator.getName();
      switch (name) {
        case "quote":
          return analyzeQuote(list);
        case "do":
          return analyzeDo(list, 1);
        case "if":
          return analyzeIf(list);
        case "when":
          return analyzeWhen(list, false);
        case "when-not":
          return analyzeWhen(list, true);
        case "and":
          return analyzeAnd(list);
        case "or":
          return analyzeOr(list);
        case "let":
          return analyzeLet(list);
        case "loop":
          return analyzeLoop(list);
        case "recur":
          return analyzeRecur(list);
        case "throw":
          return analyzeThrow(list);
        case "try":
          return analyzeTry(list);
        case "fn":
          return analyzeFunction(list);
        case "defn":
          return analyzeDefn(list);
        case "def":
          return analyzeDef(list);
        case "defstruct":
          return analyzeDefStruct(list);
        case "defprotocol":
          return analyzeDefProtocol(list);
        case "extend-type":
          return analyzeExtendType(list);
        case "defmacro":
          return analyzeDefMacro(list);
        case "ns":
          return analyzeNamespace(list);
        case "field":
          return analyzeField(list);
        case "protocol-call":
          return analyzeProtocolCall(list);
        case "host-symbol":
          return analyzeHostSymbol(list);
        case "host-get":
          return analyzeHostGet(list);
        case "host-call":
          return analyzeHostCall(list);
        case "+":
          return analyzeAdd(list);
        case "-":
          return analyzeNumeric(list, HaraNodes.Numeric.Operator.SUBTRACT, "-");
        case "*":
          return analyzeNumeric(list, HaraNodes.Numeric.Operator.MULTIPLY, "*");
        case "/":
          return analyzeNumeric(list, HaraNodes.Numeric.Operator.DIVIDE, "/");
        case "mod":
          return analyzeNumeric(list, HaraNodes.Numeric.Operator.REMAINDER, "mod");
        case "bytes":
          return analyzeBytes(list);
        case "<":
          return analyzeCompare(list, HaraNodes.Compare.Operator.LESS, "<");
        case "<=":
          return analyzeCompare(list, HaraNodes.Compare.Operator.LESS_OR_EQUAL, "<=");
        case ">":
          return analyzeCompare(list, HaraNodes.Compare.Operator.GREATER, ">");
        case ">=":
          return analyzeCompare(list, HaraNodes.Compare.Operator.GREATER_OR_EQUAL, ">=");
        case "=":
          return analyzeCompare(list, HaraNodes.Compare.Operator.EQUAL, "=");
        case "not=":
          return analyzeCompare(list, HaraNodes.Compare.Operator.NOT_EQUAL, "not=");
        default:
          return analyzeInvocation(list);
      }
    }
    return analyzeInvocation(list);
  }

  private HaraExpressionNode analyzeCollectionLiteral(Object form) {
    if (form instanceof IMapType<?, ?>) {
      IMapType<?, ?> map = (IMapType<?, ?>) form;
      ArrayList<HaraExpressionNode> elements = new ArrayList<>();
      Iterator<?> iterator = map.iterator();
      while (iterator.hasNext()) {
        Object entry = iterator.next();
        if (!(entry instanceof java.util.Map.Entry<?, ?>)) {
          throw error("map literal contains a non-entry value");
        }
        java.util.Map.Entry<?, ?> pair = (java.util.Map.Entry<?, ?>) entry;
        elements.add(analyze(pair.getKey()));
        elements.add(analyze(pair.getValue()));
      }
      HaraNodes.CollectionLiteral.Kind kind = HaraNodes.CollectionLiteral.Kind.MAP;
      if (form instanceof hara.lang.data.OrderedMap) {
        kind = HaraNodes.CollectionLiteral.Kind.ORDERED_MAP;
      } else if (form instanceof hara.lang.data.SortedMap) {
        kind = HaraNodes.CollectionLiteral.Kind.SORTED_MAP;
      }
      return new HaraNodes.CollectionLiteral(kind, elements.toArray(new HaraExpressionNode[0]));
    }
    if (form instanceof ISetType<?>) {
      ISetType<?> set = (ISetType<?>) form;
      ArrayList<HaraExpressionNode> elements = new ArrayList<>();
      Iterator<?> iterator = set.iterator();
      while (iterator.hasNext()) {
        elements.add(analyze(iterator.next()));
      }
      HaraNodes.CollectionLiteral.Kind kind = HaraNodes.CollectionLiteral.Kind.SET;
      if (form instanceof hara.lang.data.OrderedSet) {
        kind = HaraNodes.CollectionLiteral.Kind.ORDERED_SET;
      } else if (form instanceof hara.lang.data.SortedSet) {
        kind = HaraNodes.CollectionLiteral.Kind.SORTED_SET;
      }
      return new HaraNodes.CollectionLiteral(kind, elements.toArray(new HaraExpressionNode[0]));
    }
    if (form instanceof ILinearType<?>) {
      ILinearType<?> linear = (ILinearType<?>) form;
      HaraNodes.CollectionLiteral.Kind kind =
          form instanceof hara.lang.data.Tuple
              ? HaraNodes.CollectionLiteral.Kind.TUPLE
              : form instanceof hara.lang.data.Queue
                  ? HaraNodes.CollectionLiteral.Kind.QUEUE
                  : HaraNodes.CollectionLiteral.Kind.VECTOR;
      HaraExpressionNode[] elements = new HaraExpressionNode[(int) linear.count()];
      for (int i = 0; i < elements.length; i++) {
        elements[i] = analyze(linear.nth(i));
      }
      return new HaraNodes.CollectionLiteral(kind, elements);
    }
    return null;
  }

  private HaraExpressionNode analyzeQuote(List<?> form) {
    requireCount(form, 2, "quote");
    return new HaraNodes.Literal(form.nth(1));
  }

  private HaraExpressionNode analyzeDo(List<?> form, int start) {
    Object[] forms = new Object[(int) form.count() - start];
    for (int i = start; i < form.count(); i++) {
      forms[i - start] = form.nth(i);
    }
    return analyzeForms(forms);
  }

  private HaraExpressionNode analyzeForms(Object[] forms) {
    HaraExpressionNode[] expressions = new HaraExpressionNode[forms.length];
    for (int i = 0; i < forms.length; i++) {
      expressions[i] = analyze(forms[i]);
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

  private HaraExpressionNode analyzeWhen(List<?> form, boolean negate) {
    if (form.count() < 2) throw error((negate ? "when-not" : "when") + " expects a condition");
    Object[] body = new Object[(int) form.count() - 2];
    for (int i = 2; i < form.count(); i++) body[i - 2] = form.nth(i);
    HaraExpressionNode consequent =
        body.length == 0 ? new HaraNodes.Literal(null) : analyzeForms(body);
    HaraExpressionNode condition = analyze(form.nth(1));
    if (negate) return new HaraNodes.If(condition, new HaraNodes.Literal(null), consequent);
    return new HaraNodes.If(condition, consequent, new HaraNodes.Literal(null));
  }

  private HaraExpressionNode analyzeAnd(List<?> form) {
    if (form.count() == 1) return new HaraNodes.Literal(true);
    HaraExpressionNode[] expressions = new HaraExpressionNode[(int) form.count() - 1];
    for (int i = 1; i < form.count(); i++) expressions[i - 1] = analyze(form.nth(i));
    return new HaraNodes.ShortCircuit(true, expressions);
  }

  private HaraExpressionNode analyzeOr(List<?> form) {
    if (form.count() == 1) return new HaraNodes.Literal(null);
    HaraExpressionNode[] expressions = new HaraExpressionNode[(int) form.count() - 1];
    for (int i = 1; i < form.count(); i++) expressions[i - 1] = analyze(form.nth(i));
    return new HaraNodes.ShortCircuit(false, expressions);
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
    int[] rawSlots = new int[bindingCount];
    HaraExpressionNode[] initializers = new HaraExpressionNode[bindingCount];
    ArrayList<int[]> patternSlots = new ArrayList<>();
    ArrayList<HaraExpressionNode[]> patternInitializers = new ArrayList<>();
    Map<Symbol, Integer> bodyLocals = new HashMap<>(locals);
    Map<Symbol, Integer> introducedLocals = new HashMap<>();
    for (int i = 0; i < bindingCount; i++) {
      Object pattern = bindings.nth(i * 2L);
      initializers[i] = analyze(bindings.nth(i * 2L + 1));
      rawSlots[i] =
          frames.addSlot(FrameSlotKind.Object, Symbol.create(null, "__hara_let_" + i), null);
      ArrayList<Integer> slots = new ArrayList<>();
      ArrayList<HaraExpressionNode> values = new ArrayList<>();
      addPatternBindings(
          pattern, new HaraNodes.ReadLocal(rawSlots[i]), frames, introducedLocals, slots, values);
      bodyLocals.putAll(introducedLocals);
      patternSlots.add(slots.stream().mapToInt(Integer::intValue).toArray());
      patternInitializers.add(values.toArray(new HaraExpressionNode[0]));
    }

    HaraAnalyzer bodyAnalyzer =
        new HaraAnalyzer(
            language,
            sourceSection,
            context,
            frames,
            bodyLocals,
            enclosingLocals,
            Map.of(),
            Map.of(),
            recurTarget);
    HaraExpressionNode body = bodyAnalyzer.analyzeDo(form, 2);
    for (int i = bindingCount - 1; i >= 0; i--) {
      body = new HaraNodes.Let(patternSlots.get(i), patternInitializers.get(i), body);
    }
    return new HaraNodes.Let(rawSlots, initializers, body);
  }

  private HaraExpressionNode analyzeFunction(List<?> form) {
    if (form.count() >= 2 && isBindingVector(form.nth(1))) {
      if (form.count() < 3) {
        throw error("fn expects a parameter vector and a body");
      }
      Object[] bodyForms = new Object[(int) form.count() - 2];
      for (int i = 2; i < form.count(); i++) {
        bodyForms[i - 2] = form.nth(i);
      }
      return analyzeFunction((ILinearType<?>) form.nth(1), bodyForms);
    }
    if (form.count() < 2) {
      throw error("fn expects a parameter vector and a body");
    }
    HaraExpressionNode[] alternatives = new HaraExpressionNode[(int) form.count() - 1];
    for (int i = 1; i < form.count(); i++) {
      Object clause = form.nth(i);
      if (!(clause instanceof List<?>) || ((List<?>) clause).count() < 2) {
        throw error("multi-arity fn clauses must contain a parameter vector and body");
      }
      List<?> clauseList = (List<?>) clause;
      if (!isBindingVector(clauseList.nth(0))) {
        throw error("multi-arity fn clause expects a parameter vector");
      }
      Object[] bodyForms = new Object[(int) clauseList.count() - 1];
      for (int j = 1; j < clauseList.count(); j++) {
        bodyForms[j - 1] = clauseList.nth(j);
      }
      alternatives[i - 1] = analyzeFunction((ILinearType<?>) clauseList.nth(0), bodyForms);
    }
    return new HaraNodes.MultiFunction(alternatives);
  }

  private HaraExpressionNode analyzeFunction(ILinearType<?> parameters, Object[] bodyForms) {

    FrameDescriptor.Builder functionFrames = FrameDescriptor.newBuilder();
    Map<Symbol, Integer> functionLocals = new HashMap<>();
    int restIndex = -1;
    for (int i = 0; i < parameters.count(); i++) {
      Object parameter = parameters.nth(i);
      if (parameter instanceof Symbol && "&".equals(((Symbol) parameter).getName())) {
        if (restIndex >= 0 || i == 0 || i + 2 != parameters.count()) {
          throw error("& must appear once before the final variadic binding");
        }
        restIndex = i;
      }
    }
    boolean variadic = restIndex >= 0;
    int fixedArity = variadic ? restIndex : (int) parameters.count();
    int[] parameterSlots = new int[fixedArity + (variadic ? 1 : 0)];
    ArrayList<Integer> destructureSlots = new ArrayList<>();
    ArrayList<HaraExpressionNode> destructureInitializers = new ArrayList<>();
    for (int i = 0; i < fixedArity; i++) {
      Object parameter = parameters.nth(i);
      Symbol rawSymbol =
          parameter instanceof Symbol ? (Symbol) parameter : Symbol.create(null, "__hara_arg_" + i);
      parameterSlots[i] = functionFrames.addSlot(FrameSlotKind.Object, rawSymbol, null);
      functionLocals.put(rawSymbol, parameterSlots[i]);
      if (!(parameter instanceof Symbol)) {
        addPatternBindings(
            parameter,
            new HaraNodes.ReadLocal(parameterSlots[i]),
            functionFrames,
            functionLocals,
            destructureSlots,
            destructureInitializers);
      }
    }
    if (variadic) {
      Object restParameter = parameters.nth(restIndex + 1);
      Symbol rawSymbol =
          restParameter instanceof Symbol
              ? (Symbol) restParameter
              : Symbol.create(null, "__hara_rest");
      parameterSlots[fixedArity] = functionFrames.addSlot(FrameSlotKind.Object, rawSymbol, null);
      functionLocals.put(rawSymbol, parameterSlots[fixedArity]);
      if (!(restParameter instanceof Symbol)) {
        addPatternBindings(
            restParameter,
            new HaraNodes.ReadLocal(parameterSlots[fixedArity]),
            functionFrames,
            functionLocals,
            destructureSlots,
            destructureInitializers);
      }
    }

    Map<Symbol, Integer> captureSlots = new LinkedHashMap<>();
    Map<Symbol, Integer> captureSources = new LinkedHashMap<>();
    HaraAnalyzer functionAnalyzer =
        new HaraAnalyzer(
            language,
            sourceSection,
            context,
            functionFrames,
            functionLocals,
            locals,
            captureSlots,
            captureSources,
            null);
    HaraExpressionNode body = functionAnalyzer.analyzeForms(bodyForms);
    if (!destructureSlots.isEmpty()) {
      body =
          new HaraNodes.Let(
              destructureSlots.stream().mapToInt(Integer::intValue).toArray(),
              destructureInitializers.toArray(new HaraExpressionNode[0]),
              body);
    }
    int[] capturedSlots = captureSlots.values().stream().mapToInt(Integer::intValue).toArray();
    int[] capturedSources = captureSources.values().stream().mapToInt(Integer::intValue).toArray();
    HaraRootNode root =
        new HaraRootNode(
            language,
            functionFrames.build(),
            body,
            parameterSlots,
            capturedSlots,
            capturedSources,
            sourceSection,
            false,
            variadic);
    return new HaraNodes.FunctionLiteral(
        root.getCallTarget(), fixedArity, variadic, capturedSlots.length != 0);
  }

  private HaraExpressionNode analyzeDefn(List<?> form) {
    if (form.count() < 4) {
      throw error("defn expects a name, parameter vector, and body");
    }

    Object name = form.nth(1);
    if (!(name instanceof Symbol)) {
      throw error("defn name must be a symbol");
    }
    Symbol symbol = (Symbol) name;
    if (symbol.getNamespace() != null) {
      throw error("defn name must not be qualified");
    }

    int parametersIndex = 2;
    if (form.nth(parametersIndex) instanceof String) {
      parametersIndex++;
    }
    if (parametersIndex < form.count() && form.nth(parametersIndex) instanceof IMapType<?, ?>) {
      parametersIndex++;
    }
    if (parametersIndex >= form.count()) {
      throw error("defn expects a body");
    }

    Object parameterForm = form.nth(parametersIndex);
    if (!isBindingVector(parameterForm)
        && (!(parameterForm instanceof List<?>) || ((List<?>) parameterForm).count() < 2)) {
      throw error("defn expects a parameter vector or arity clauses");
    }
    if (isBindingVector(parameterForm) && parametersIndex + 1 >= form.count()) {
      throw error("defn expects a body");
    }

    Object[] bodyForms = new Object[(int) form.count() - parametersIndex - 1];
    for (int i = parametersIndex + 1; i < form.count(); i++) {
      bodyForms[i - parametersIndex - 1] = form.nth(i);
    }
    HaraExpressionNode function;
    if (isBindingVector(parameterForm)) {
      function = analyzeFunction((ILinearType<?>) parameterForm, bodyForms);
    } else {
      List<?> clauses = form;
      HaraExpressionNode[] alternatives =
          new HaraExpressionNode[(int) form.count() - parametersIndex];
      for (int i = parametersIndex; i < form.count(); i++) {
        Object clause = form.nth(i);
        if (!(clause instanceof List<?>) || ((List<?>) clause).count() < 2) {
          throw error("defn arity clauses must contain a parameter vector and body");
        }
        List<?> clauseList = (List<?>) clause;
        if (!isBindingVector(clauseList.nth(0))) {
          throw error("defn arity clause expects a parameter vector");
        }
        Object[] clauseBody = new Object[(int) clauseList.count() - 1];
        for (int j = 1; j < clauseList.count(); j++) {
          clauseBody[j - 1] = clauseList.nth(j);
        }
        alternatives[i - parametersIndex] =
            analyzeFunction((ILinearType<?>) clauseList.nth(0), clauseBody);
      }
      function = new HaraNodes.MultiFunction(alternatives);
    }
    return new HaraNodes.DefineGlobal(symbol, function);
  }

  @SuppressWarnings("unchecked")
  private void addPatternBindings(
      Object pattern,
      HaraExpressionNode source,
      FrameDescriptor.Builder patternFrames,
      Map<Symbol, Integer> patternLocals,
      ArrayList<Integer> patternSlots,
      ArrayList<HaraExpressionNode> patternInitializers) {
    addPatternBindings(
        pattern, source, patternFrames, patternLocals, patternSlots, patternInitializers, null);
  }

  private void addPatternBindings(
      Object pattern,
      HaraExpressionNode source,
      FrameDescriptor.Builder patternFrames,
      Map<Symbol, Integer> patternLocals,
      ArrayList<Integer> patternSlots,
      ArrayList<HaraExpressionNode> patternInitializers,
      IMapType<?, ?> defaults) {
    if (pattern instanceof Symbol) {
      Symbol symbol = (Symbol) pattern;
      if (symbol.getNamespace() != null || patternLocals.containsKey(symbol)) {
        throw error("Invalid or duplicate binding: " + symbol.display());
      }
      HaraExpressionNode value = source;
      Object defaultForm =
          defaults == null ? null : ((IMapType<Object, Object>) defaults).lookup(symbol);
      if (defaultForm != null) {
        value = new HaraNodes.DefaultValue(source, analyze(defaultForm));
      }
      int slot = patternFrames.addSlot(FrameSlotKind.Object, symbol, null);
      patternLocals.put(symbol, slot);
      patternSlots.add(slot);
      patternInitializers.add(value);
      return;
    }
    if (pattern instanceof ILinearType<?> && isBindingVector(pattern)) {
      ILinearType<?> vector = (ILinearType<?>) pattern;
      for (int i = 0; i < vector.count(); i++) {
        Object element = vector.nth(i);
        if (element instanceof Symbol && "&".equals(((Symbol) element).getName())) {
          if (i + 2 != vector.count()) {
            throw error("& in a destructuring vector must precede its final binding");
          }
          addPatternBindings(
              vector.nth(i + 1),
              new HaraNodes.Rest(source, i),
              patternFrames,
              patternLocals,
              patternSlots,
              patternInitializers,
              defaults);
          return;
        }
        addPatternBindings(
            element,
            new HaraNodes.Lookup(source, i),
            patternFrames,
            patternLocals,
            patternSlots,
            patternInitializers,
            defaults);
      }
      return;
    }
    if (pattern instanceof IMapType<?, ?>) {
      IMapType<Object, Object> map = (IMapType<Object, Object>) pattern;
      Object as = map.lookup(Keyword.create(null, "as"));
      if (as != null) {
        addPatternBindings(
            as, source, patternFrames, patternLocals, patternSlots, patternInitializers, defaults);
      }
      Object keys = map.lookup(Keyword.create(null, "keys"));
      if (keys instanceof ILinearType<?>) {
        ILinearType<?> keySymbols = (ILinearType<?>) keys;
        for (int i = 0; i < keySymbols.count(); i++) {
          Object key = keySymbols.nth(i);
          if (!(key instanceof Symbol)) {
            throw error(":keys destructuring expects symbols");
          }
          addPatternBindings(
              key,
              new HaraNodes.Lookup(source, Keyword.create(null, ((Symbol) key).getName())),
              patternFrames,
              patternLocals,
              patternSlots,
              patternInitializers,
              mapDefaults(map));
        }
      }
      Object strs = map.lookup(Keyword.create(null, "strs"));
      if (strs instanceof ILinearType<?>) {
        ILinearType<?> keySymbols = (ILinearType<?>) strs;
        for (int i = 0; i < keySymbols.count(); i++) {
          Object key = keySymbols.nth(i);
          if (!(key instanceof Symbol)) {
            throw error(":strs destructuring expects symbols");
          }
          addPatternBindings(
              key,
              new HaraNodes.Lookup(source, ((Symbol) key).getName()),
              patternFrames,
              patternLocals,
              patternSlots,
              patternInitializers,
              mapDefaults(map));
        }
      }
      Object syms = map.lookup(Keyword.create(null, "syms"));
      if (syms instanceof ILinearType<?>) {
        ILinearType<?> keySymbols = (ILinearType<?>) syms;
        for (int i = 0; i < keySymbols.count(); i++) {
          Object key = keySymbols.nth(i);
          if (!(key instanceof Symbol)) {
            throw error(":syms destructuring expects symbols");
          }
          addPatternBindings(
              key,
              new HaraNodes.Lookup(source, key),
              patternFrames,
              patternLocals,
              patternSlots,
              patternInitializers,
              mapDefaults(map));
        }
      }
      Iterator<?> entries = map.iterator();
      while (entries.hasNext()) {
        Object entryValue = entries.next();
        if (!(entryValue instanceof java.util.Map.Entry<?, ?>)) {
          continue;
        }
        java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) entryValue;
        Object key = entry.getKey();
        if (key instanceof Keyword
            && ("as".equals(((Keyword) key).getName())
                || "keys".equals(((Keyword) key).getName())
                || "strs".equals(((Keyword) key).getName())
                || "syms".equals(((Keyword) key).getName())
                || "or".equals(((Keyword) key).getName()))) {
          continue;
        }
        addPatternBindings(
            entry.getValue(),
            new HaraNodes.Lookup(source, key),
            patternFrames,
            patternLocals,
            patternSlots,
            patternInitializers,
            mapDefaults(map));
      }
      return;
    }
    throw error("unsupported binding pattern");
  }

  @SuppressWarnings("unchecked")
  private IMapType<?, ?> mapDefaults(IMapType<?, ?> pattern) {
    Object defaults = ((IMapType<Object, Object>) pattern).lookup(Keyword.create(null, "or"));
    return defaults instanceof IMapType<?, ?> ? (IMapType<?, ?>) defaults : null;
  }

  private HaraExpressionNode analyzeLoop(List<?> form) {
    if (form.count() < 3 || !isBindingVector(form.nth(1))) {
      throw error("loop expects a binding vector and a body");
    }
    ILinearType<?> bindings = (ILinearType<?>) form.nth(1);
    if (bindings.count() % 2 != 0) {
      throw error("loop expects an even number of binding forms");
    }

    int bindingCount = (int) bindings.count() / 2;
    int[] rawSlots = new int[bindingCount];
    HaraExpressionNode[] initializers = new HaraExpressionNode[bindingCount];
    Map<Symbol, Integer> bodyLocals = new HashMap<>(locals);
    ArrayList<int[]> patternSlots = new ArrayList<>();
    ArrayList<HaraExpressionNode[]> patternInitializers = new ArrayList<>();
    Map<Symbol, Integer> introducedLocals = new HashMap<>();
    for (int i = 0; i < bindingCount; i++) {
      Object pattern = bindings.nth(i * 2L);
      initializers[i] = analyze(bindings.nth(i * 2L + 1));
      rawSlots[i] =
          frames.addSlot(FrameSlotKind.Object, Symbol.create(null, "__hara_loop_" + i), null);
      ArrayList<Integer> slots = new ArrayList<>();
      ArrayList<HaraExpressionNode> values = new ArrayList<>();
      addPatternBindings(
          pattern, new HaraNodes.ReadLocal(rawSlots[i]), frames, introducedLocals, slots, values);
      bodyLocals.putAll(introducedLocals);
      patternSlots.add(slots.stream().mapToInt(Integer::intValue).toArray());
      patternInitializers.add(values.toArray(new HaraExpressionNode[0]));
    }

    HaraNodes.RecurTarget target = new HaraNodes.RecurTarget(bindingCount);
    for (int i = 2; i < form.count() - 1; i++) {
      validateTailRecurs(form.nth(i), false);
    }
    validateTailRecurs(form.nth(form.count() - 1), true);
    HaraAnalyzer bodyAnalyzer =
        new HaraAnalyzer(
            language,
            sourceSection,
            context,
            frames,
            bodyLocals,
            enclosingLocals,
            Map.of(),
            Map.of(),
            target);
    HaraExpressionNode body = bodyAnalyzer.analyzeDo(form, 2);
    for (int i = bindingCount - 1; i >= 0; i--) {
      body = new HaraNodes.Let(patternSlots.get(i), patternInitializers.get(i), body);
    }
    return new HaraNodes.Loop(target, rawSlots, initializers, body);
  }

  private HaraExpressionNode analyzeRecur(List<?> form) {
    if (recurTarget == null) {
      throw error("recur used outside loop");
    }
    if (form.count() - 1 != recurTarget.arity()) {
      throw error("recur expects " + recurTarget.arity() + " arguments");
    }
    HaraExpressionNode[] values = new HaraExpressionNode[recurTarget.arity()];
    for (int i = 0; i < values.length; i++) {
      values[i] = analyze(form.nth(i + 1));
    }
    return new HaraNodes.Recur(recurTarget, values);
  }

  private HaraExpressionNode analyzeThrow(List<?> form) {
    requireCount(form, 2, "throw");
    return new HaraNodes.Throw(analyze(form.nth(1)));
  }

  private HaraExpressionNode analyzeTry(List<?> form) {
    if (form.count() < 2) {
      throw error("try expects a body");
    }
    ArrayList<Object> bodyForms = new ArrayList<>();
    List<?> catchForm = null;
    List<?> finallyForm = null;
    for (int i = 1; i < form.count(); i++) {
      Object clause = form.nth(i);
      if (clause instanceof List<?> && ((List<?>) clause).count() > 0) {
        Object name = ((List<?>) clause).nth(0);
        if (name instanceof Symbol && "catch".equals(((Symbol) name).getName())) {
          if (catchForm != null) {
            throw error("try supports one catch clause");
          }
          catchForm = (List<?>) clause;
          continue;
        }
        if (name instanceof Symbol && "finally".equals(((Symbol) name).getName())) {
          if (finallyForm != null || i != form.count() - 1) {
            throw error("finally must be the last try clause and may appear once");
          }
          finallyForm = (List<?>) clause;
          continue;
        }
      }
      if (catchForm != null || finallyForm != null) {
        throw error("try clauses must follow the body");
      }
      bodyForms.add(clause);
    }
    HaraExpressionNode body = analyzeForms(bodyForms.toArray());
    int catchSlot = -1;
    HaraExpressionNode catchBody = null;
    if (catchForm != null) {
      if (catchForm.count() < 4 || !(catchForm.nth(2) instanceof Symbol)) {
        throw error("catch expects a class, binding, and body");
      }
      catchSlot = frames.addSlot(FrameSlotKind.Object, catchForm.nth(2), null);
      Map<Symbol, Integer> catchLocals = new HashMap<>(locals);
      catchLocals.put((Symbol) catchForm.nth(2), catchSlot);
      HaraAnalyzer catchAnalyzer =
          new HaraAnalyzer(
              language,
              sourceSection,
              context,
              frames,
              catchLocals,
              enclosingLocals,
              Map.of(),
              Map.of(),
              recurTarget);
      catchBody = catchAnalyzer.analyzeDo(catchForm, 3);
    }
    HaraExpressionNode finallyBody = null;
    if (finallyForm != null) {
      finallyBody = analyzeDo(finallyForm, 1);
    }
    return new HaraNodes.Try(body, catchSlot, catchBody, finallyBody);
  }

  private void validateTailRecurs(Object form, boolean tail) {
    if (!(form instanceof List<?>) || ((List<?>) form).count() == 0) {
      return;
    }
    List<?> list = (List<?>) form;
    Object operator = list.nth(0);
    if (operator instanceof Symbol && "recur".equals(((Symbol) operator).getName())) {
      if (!tail) {
        throw error("recur must appear in tail position");
      }
      return;
    }
    if (operator instanceof Symbol && "if".equals(((Symbol) operator).getName())) {
      if (list.count() >= 2) validateTailRecurs(list.nth(1), false);
      if (list.count() >= 3) validateTailRecurs(list.nth(2), tail);
      if (list.count() >= 4) validateTailRecurs(list.nth(3), tail);
      return;
    }
    if (operator instanceof Symbol && "do".equals(((Symbol) operator).getName())) {
      for (int i = 1; i < list.count(); i++) {
        validateTailRecurs(list.nth(i), tail && i == list.count() - 1);
      }
      return;
    }
    if (operator instanceof Symbol && "let".equals(((Symbol) operator).getName())) {
      if (list.count() >= 2 && list.nth(1) instanceof ILinearType<?>) {
        ILinearType<?> bindings = (ILinearType<?>) list.nth(1);
        for (int i = 1; i < bindings.count(); i += 2) {
          validateTailRecurs(bindings.nth(i), false);
        }
      }
      for (int i = 2; i < list.count(); i++) {
        validateTailRecurs(list.nth(i), tail && i == list.count() - 1);
      }
      return;
    }
    if (operator instanceof Symbol
        && ("fn".equals(((Symbol) operator).getName())
            || "defn".equals(((Symbol) operator).getName())
            || "loop".equals(((Symbol) operator).getName()))) {
      return;
    }
    for (int i = 0; i < list.count(); i++) {
      validateTailRecurs(list.nth(i), false);
    }
  }

  private HaraExpressionNode analyzeAdd(List<?> form) {
    requireCount(form, 3, "+");
    return new HaraNodes.Add(analyze(form.nth(1)), analyze(form.nth(2)));
  }

  private HaraExpressionNode analyzeNumeric(
      List<?> form, HaraNodes.Numeric.Operator operator, String name) {
    requireCount(form, 3, name);
    return new HaraNodes.Numeric(operator, analyze(form.nth(1)), analyze(form.nth(2)));
  }

  private HaraExpressionNode analyzeCompare(
      List<?> form, HaraNodes.Compare.Operator operator, String name) {
    requireCount(form, 3, name);
    return new HaraNodes.Compare(operator, analyze(form.nth(1)), analyze(form.nth(2)));
  }

  private HaraExpressionNode analyzeBytes(List<?> form) {
    HaraExpressionNode[] elements = new HaraExpressionNode[(int) form.count() - 1];
    for (int i = 1; i < form.count(); i++) {
      elements[i - 1] = analyze(form.nth(i));
    }
    return new HaraNodes.Bytes(elements);
  }

  private HaraExpressionNode analyzeMutableCollection(
      List<?> form, HaraNodes.CollectionLiteral.Kind kind) {
    HaraExpressionNode[] elements = new HaraExpressionNode[(int) form.count() - 1];
    for (int i = 1; i < form.count(); i++) {
      elements[i - 1] = analyze(form.nth(i));
    }
    return new HaraNodes.CollectionLiteral(kind, elements);
  }

  private HaraExpressionNode analyzeMutableOperation(
      List<?> form, HaraNodes.MutableOperation.Operator operator, int minimum, int maximum) {
    if (form.count() < minimum || form.count() > maximum) {
      throw error("mutable operation has an invalid arity");
    }
    HaraExpressionNode[] arguments = new HaraExpressionNode[(int) form.count() - 1];
    for (int i = 1; i < form.count(); i++) {
      arguments[i - 1] = analyze(form.nth(i));
    }
    return new HaraNodes.MutableOperation(operator, arguments);
  }

  private HaraExpressionNode analyzeDef(List<?> form) {
    requireCount(form, 3, "def");
    Object name = form.nth(1);
    if (!(name instanceof Symbol)) {
      throw error("def name must be a symbol");
    }
    Symbol symbol = (Symbol) name;
    if (symbol.getNamespace() != null) {
      throw error("def name must not be qualified");
    }
    return new HaraNodes.DefineGlobal(symbol, analyze(form.nth(2)));
  }

  private HaraExpressionNode analyzeDefStruct(List<?> form) {
    requireCount(form, 3, "defstruct");
    Object name = form.nth(1);
    if (!(name instanceof Symbol)) {
      throw error("defstruct name must be a symbol");
    }
    Symbol symbol = (Symbol) name;
    if (symbol.getNamespace() != null) {
      throw error("defstruct name must not be qualified");
    }
    if (!isBindingVector(form.nth(2))) {
      throw error("defstruct expects a field vector");
    }

    ILinearType<?> fields = (ILinearType<?>) form.nth(2);
    String[] fieldNames = new String[(int) fields.count()];
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < fieldNames.length; i++) {
      Object field = fields.nth(i);
      if (!(field instanceof Symbol) || ((Symbol) field).getNamespace() != null) {
        throw error("defstruct field names must be unqualified symbols");
      }
      fieldNames[i] = ((Symbol) field).getName();
      if (!seen.add(fieldNames[i])) {
        throw error("Duplicate defstruct field: " + fieldNames[i]);
      }
    }
    return new HaraNodes.DefineGlobal(
        symbol, new HaraNodes.Literal(new HaraType(symbol.getName(), fieldNames)));
  }

  private HaraExpressionNode analyzeField(List<?> form) {
    requireCount(form, 3, "field");
    Object field = form.nth(2);
    String fieldName;
    if (field instanceof Keyword) {
      if (((Keyword) field).getNamespace() != null) {
        throw error("field name must not be qualified");
      }
      fieldName = ((Keyword) field).getName();
    } else if (field instanceof Symbol) {
      if (((Symbol) field).getNamespace() != null) {
        throw error("field name must not be qualified");
      }
      fieldName = ((Symbol) field).getName();
    } else {
      throw error("field name must be a keyword or symbol");
    }
    return new HaraNodes.ReadField(analyze(form.nth(1)), fieldName);
  }

  private HaraExpressionNode analyzeDefProtocol(List<?> form) {
    if (form.count() < 3) {
      throw error("defprotocol expects a name and method declarations");
    }
    Object name = form.nth(1);
    if (!(name instanceof Symbol) || ((Symbol) name).getNamespace() != null) {
      throw error("defprotocol name must be an unqualified symbol");
    }
    Map<String, Integer> methodArities = new LinkedHashMap<>();
    for (int i = 2; i < form.count(); i++) {
      Object declaration = form.nth(i);
      if (!(declaration instanceof List<?>) || ((List<?>) declaration).count() != 2) {
        throw error("defprotocol method declarations must be (name [arguments])");
      }
      List<?> method = (List<?>) declaration;
      Object methodName = method.nth(0);
      if (!(methodName instanceof Symbol) || ((Symbol) methodName).getNamespace() != null) {
        throw error("protocol method name must be an unqualified symbol");
      }
      if (!isBindingVector(method.nth(1))) {
        throw error("protocol method arguments must be a vector");
      }
      ILinearType<?> parameters = (ILinearType<?>) method.nth(1);
      if (parameters.count() == 0) {
        throw error("protocol methods must take a receiver as their first argument");
      }
      Set<Symbol> parameterNames = new HashSet<>();
      for (int parameterIndex = 0; parameterIndex < parameters.count(); parameterIndex++) {
        Object parameter = parameters.nth(parameterIndex);
        if (!(parameter instanceof Symbol) || !parameterNames.add((Symbol) parameter)) {
          throw error("protocol method arguments must be unique symbols");
        }
      }
      String methodKey = ((Symbol) methodName).getName();
      if (methodArities.put(methodKey, (int) parameters.count()) != null) {
        throw error("Duplicate protocol method: " + methodKey);
      }
    }
    return new HaraNodes.DefineProtocol(
        (Symbol) name, new HaraProtocol(((Symbol) name).getName(), methodArities));
  }

  private HaraExpressionNode analyzeExtendType(List<?> form) {
    if (form.count() < 4) {
      throw error("extend-type expects a type, protocol, and method implementations");
    }
    Object protocol = form.nth(2);
    if (!(protocol instanceof Symbol) || ((Symbol) protocol).getNamespace() != null) {
      throw error("extend-type protocol must be an unqualified symbol");
    }
    HaraNodes.ProtocolMethodImplementation[] methods =
        new HaraNodes.ProtocolMethodImplementation[(int) form.count() - 3];
    Set<String> seen = new HashSet<>();
    for (int i = 3; i < form.count(); i++) {
      Object implementation = form.nth(i);
      if (!(implementation instanceof List<?>) || ((List<?>) implementation).count() < 3) {
        throw error("extend-type implementations must be (name [arguments] body...)");
      }
      List<?> method = (List<?>) implementation;
      Object methodName = method.nth(0);
      if (!(methodName instanceof Symbol) || ((Symbol) methodName).getNamespace() != null) {
        throw error("extended method name must be an unqualified symbol");
      }
      String methodKey = ((Symbol) methodName).getName();
      if (!seen.add(methodKey)) {
        throw error("Duplicate extended method: " + methodKey);
      }
      if (!isBindingVector(method.nth(1))) {
        throw error("extended method arguments must be a vector");
      }
      Object[] body = new Object[(int) method.count() - 2];
      for (int j = 2; j < method.count(); j++) {
        body[j - 2] = method.nth(j);
      }
      methods[i - 3] =
          new HaraNodes.ProtocolMethodImplementation(
              methodKey, analyzeFunction((ILinearType<?>) method.nth(1), body));
    }
    return new HaraNodes.ExtendType(analyze(form.nth(1)), analyze(protocol), methods);
  }

  private HaraExpressionNode analyzeProtocolCall(List<?> form) {
    if (form.count() < 4) {
      throw error("protocol-call expects a protocol, method, receiver, and arguments");
    }
    Object method = form.nth(2);
    String methodName;
    if (method instanceof Keyword) {
      if (((Keyword) method).getNamespace() != null) {
        throw error("protocol method must not be qualified");
      }
      methodName = ((Keyword) method).getName();
    } else if (method instanceof Symbol) {
      if (((Symbol) method).getNamespace() != null) {
        throw error("protocol method must not be qualified");
      }
      methodName = ((Symbol) method).getName();
    } else {
      throw error("protocol method must be a keyword or symbol");
    }
    HaraExpressionNode[] arguments = new HaraExpressionNode[(int) form.count() - 4];
    for (int i = 4; i < form.count(); i++) {
      arguments[i - 4] = analyze(form.nth(i));
    }
    return new HaraNodes.ProtocolInvoke(
        analyze(form.nth(1)), methodName, analyze(form.nth(3)), arguments);
  }

  private HaraExpressionNode analyzeHostSymbol(List<?> form) {
    requireCount(form, 2, "host-symbol");
    if (!(form.nth(1) instanceof String)) {
      throw error("host-symbol expects a string name");
    }
    return new HaraNodes.HostSymbol((String) form.nth(1));
  }

  private HaraExpressionNode analyzeHostGet(List<?> form) {
    requireCount(form, 3, "host-get");
    return new HaraNodes.HostGet(analyze(form.nth(1)), memberName(form.nth(2)));
  }

  private HaraExpressionNode analyzeHostCall(List<?> form) {
    if (form.count() < 3) {
      throw error("host-call expects a target and member name");
    }
    HaraExpressionNode[] arguments = new HaraExpressionNode[(int) form.count() - 3];
    for (int i = 3; i < form.count(); i++) {
      arguments[i - 3] = analyze(form.nth(i));
    }
    return new HaraNodes.HostCall(analyze(form.nth(1)), memberName(form.nth(2)), arguments);
  }

  private String memberName(Object value) {
    if (value instanceof Keyword) {
      Keyword keyword = (Keyword) value;
      if (keyword.getNamespace() != null) {
        throw error("host member name must not be qualified");
      }
      return keyword.getName();
    }
    if (value instanceof Symbol) {
      Symbol symbol = (Symbol) value;
      if (symbol.getNamespace() != null) {
        throw error("host member name must not be qualified");
      }
      return symbol.getName();
    }
    if (value instanceof String) {
      return (String) value;
    }
    throw error("host member name must be a keyword, symbol, or string");
  }

  private HaraExpressionNode analyzeDefMacro(List<?> form) {
    if (form.count() < 4 || !isBindingVector(form.nth(2))) {
      throw error("defmacro expects a name, parameter vector, and body");
    }
    Object name = form.nth(1);
    if (!(name instanceof Symbol)) {
      throw error("defmacro name must be a symbol");
    }
    Symbol symbol = (Symbol) name;
    if (symbol.getNamespace() != null) {
      throw error("defmacro name must not be qualified");
    }
    Object[] body = new Object[(int) form.count() - 3];
    for (int i = 3; i < form.count(); i++) {
      body[i - 3] = form.nth(i);
    }
    context.defineMacro(symbol, new HaraMacro(symbol, (ILinearType<?>) form.nth(2), body));
    return new HaraNodes.Literal(null);
  }

  private HaraExpressionNode analyzeNamespace(List<?> form) {
    requireCount(form, 2, "ns");
    Object name = form.nth(1);
    if (!(name instanceof Symbol) || ((Symbol) name).getNamespace() != null) {
      throw error("ns name must be an unqualified symbol");
    }
    return new HaraNodes.SetNamespace((Symbol) name);
  }

  private Object expandMacro(Object form) {
    if (!(form instanceof List<?>)) {
      return form;
    }
    List<?> list = (List<?>) form;
    if (list.count() == 0 || !(list.nth(0) instanceof Symbol)) {
      return form;
    }
    Symbol operator = (Symbol) list.nth(0);
    if (operator.getNamespace() != null || "defmacro".equals(operator.getName())) {
      return form;
    }
    HaraMacro macro = context.resolveMacro(operator);
    return macro == null ? form : macro.expand(list);
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

package hara.truffle;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.SourceSection;
import hara.lang.data.Keyword;
import hara.lang.data.List;
import hara.lang.data.Symbol;
import hara.lang.data.types.ILinearType;
import hara.truffle.node.HaraExpressionNode;
import hara.truffle.node.HaraNodes;
import hara.truffle.node.HaraRootNode;
import java.util.HashMap;
import java.util.HashSet;
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

  private HaraAnalyzer(
      HaraLanguage language,
      SourceSection sourceSection,
      HaraContext context,
      FrameDescriptor.Builder frames,
      Map<Symbol, Integer> locals,
      Map<Symbol, Integer> enclosingLocals,
      Map<Symbol, Integer> captureSlots,
      Map<Symbol, Integer> captureSources) {
    this.language = language;
    this.context = context;
    this.sourceSection = sourceSection;
    this.frames = frames;
    this.locals = locals;
    this.enclosingLocals = enclosingLocals;
    this.captureSlots = captureSlots;
    this.captureSources = captureSources;
  }

  static com.oracle.truffle.api.RootCallTarget compile(
      HaraLanguage language, Object[] forms, SourceSection sourceSection, HaraContext context) {
    FrameDescriptor.Builder frames = FrameDescriptor.newBuilder();
    HaraAnalyzer analyzer =
        new HaraAnalyzer(
            language, sourceSection, context, frames, Map.of(), Map.of(), Map.of(), Map.of());
    HaraExpressionNode[] expressions = new HaraExpressionNode[forms.length];
    for (int i = 0; i < forms.length; i++) {
      expressions[i] = analyzer.analyze(forms[i]);
    }
    HaraExpressionNode body = new HaraNodes.Do(expressions);
    return new HaraRootNode(
            language, frames.build(), body, new int[0], new int[0], new int[0], sourceSection, true)
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
    }

    Map<Symbol, Integer> bodyLocals = new HashMap<>(locals);
    for (int i = 0; i < bindingCount; i++) {
      bodyLocals.put((Symbol) bindings.nth(i * 2L), slots[i]);
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
            Map.of());
    HaraExpressionNode body = bodyAnalyzer.analyzeDo(form, 2);
    return new HaraNodes.Let(slots, initializers, body);
  }

  private HaraExpressionNode analyzeFunction(List<?> form) {
    if (form.count() < 3 || !isBindingVector(form.nth(1))) {
      throw error("fn expects a parameter vector and a body");
    }
    Object[] bodyForms = new Object[(int) form.count() - 2];
    for (int i = 2; i < form.count(); i++) {
      bodyForms[i - 2] = form.nth(i);
    }
    return analyzeFunction((ILinearType<?>) form.nth(1), bodyForms);
  }

  private HaraExpressionNode analyzeFunction(ILinearType<?> parameters, Object[] bodyForms) {

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
            captureSources);
    HaraExpressionNode body = functionAnalyzer.analyzeForms(bodyForms);
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
            false);
    return new HaraNodes.FunctionLiteral(
        root.getCallTarget(), parameterSlots.length, capturedSlots.length != 0);
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

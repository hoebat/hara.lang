package hara.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import hara.kernel.builtin.BuiltinStruct;
import hara.lang.base.Iter;
import hara.lang.base.Eq;
import hara.lang.base.primitive.Num;
import hara.lang.base.iter.CloseableIterator;
import hara.lang.data.Symbol;
import hara.lang.data.List;
import hara.lang.data.Keyword;
import hara.lang.data.types.IMapType;
import hara.lang.data.types.ILinearType;
import hara.lang.protocol.IFn;
import hara.lang.protocol.IMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class HaraContext {
  private final TruffleLanguage.Env environment;
  private final Map<String, HaraNamespace> namespaces = new ConcurrentHashMap<>();
  private final Map<String, Map<String, HaraMacro>> macros = new ConcurrentHashMap<>();
  private final Map<String, Map<String, String>> aliases = new ConcurrentHashMap<>();
  private final Map<String, ModuleRecord> modules = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> moduleDependencies = new ConcurrentHashMap<>();
  private final Set<String> loadingModules = ConcurrentHashMap.newKeySet();
  private final Deque<String> loadingStack = new ArrayDeque<>();
  private volatile HaraNamespace currentNamespace;
  private final HaraProtocol ifnProtocol;

  HaraContext(TruffleLanguage.Env environment) {
    this.environment = environment;
    currentNamespace = namespace("user");
    Map<String, Integer> ifnMethods = new LinkedHashMap<>();
    ifnMethods.put("invoke", -1);
    ifnProtocol = new HaraProtocol("IFn", ifnMethods);
    currentNamespace.define("IFn", ifnProtocol);
    HaraJavaAdapters.install(this);
    installNumericBuiltins();
  }

  TruffleLanguage.Env environment() {
    return environment;
  }

  private HaraNamespace namespace(String name) {
    return namespaces.computeIfAbsent(name, HaraNamespace::new);
  }

  public void setCurrentNamespace(Symbol symbol) {
    if (symbol.getNamespace() != null) {
      throw new HaraException("Namespace name must not be qualified");
    }
    currentNamespace = namespace(symbol.getName());
    installNumericBuiltins(currentNamespace);
  }

  public HaraVar resolve(Symbol symbol) {
    String namespaceName = symbol.getNamespace();
    if (namespaceName != null) {
      namespaceName =
          aliases
              .getOrDefault(currentNamespace.name(), Map.of())
              .getOrDefault(namespaceName, namespaceName);
    }
    HaraNamespace namespace =
        namespaceName == null ? currentNamespace : namespaces.get(namespaceName);
    return namespace == null ? null : namespace.lookup(symbol.getName());
  }

  public Object macroExpand(Object form, boolean recursive) {
    Object result = form;
    int expansions = 0;
    do {
      Object expanded = macroExpandOnce(result);
      if (expanded == result) return result;
      result = expanded;
      expansions++;
      if (expansions > 1000) throw new HaraException("macro expansion exceeded 1000 steps");
    } while (recursive);
    return result;
  }

  private Object macroExpandOnce(Object form) {
    if (!(form instanceof List<?>)) return form;
    List<?> list = (List<?>) form;
    if (list.count() == 0 || !(list.nth(0) instanceof Symbol)) return form;
    Symbol operator = (Symbol) list.nth(0);
    if (operator.getNamespace() != null) return form;
    HaraMacro macro = resolveMacro(operator);
    return macro == null ? form : macro.expand(list);
  }

  public void defineAlias(Symbol alias, Symbol target) {
    if (alias.getNamespace() != null || target.getNamespace() != null) {
      throw new HaraException("alias names must be unqualified");
    }
    if (!namespaces.containsKey(target.getName())) {
      throw new HaraException("Cannot alias missing namespace: " + target.getName());
    }
    aliases
        .computeIfAbsent(currentNamespace.name(), ignored -> new ConcurrentHashMap<>())
        .put(alias.getName(), target.getName());
  }

  public HaraVar define(Symbol symbol, Object value) {
    if (symbol.getNamespace() != null && !symbol.getNamespace().equals(currentNamespace.name())) {
      throw new HaraException("Cannot define a var in another namespace: " + symbol.display());
    }
    return currentNamespace.define(symbol.getName(), value, symbol.meta());
  }

  public HaraProtocol ifnProtocol() {
    return ifnProtocol;
  }

  HaraProtocol defineProtocol(String name, Map<String, Integer> methodArities) {
    HaraProtocol protocol = new HaraProtocol(name, methodArities);
    currentNamespace.define(name, protocol);
    return protocol;
  }

  public boolean hostInteropAllowed() {
    return environment.isHostLookupAllowed();
  }

  public Object asGuestValue(Object value) {
    return environment.asGuestValue(value);
  }

  public boolean isHostObject(Object value) {
    return environment.isHostObject(value);
  }

  public Object asHostObject(Object value) {
    return environment.asHostObject(value);
  }

  public Object lookupHostSymbol(String name) {
    return environment.lookupHostSymbol(name);
  }

  HaraMacro resolveMacro(Symbol symbol) {
    String namespace = symbol.getNamespace();
    String namespaceName = namespace == null ? currentNamespace.name() : namespace;
    Map<String, HaraMacro> namespaceMacros = macros.get(namespaceName);
    return namespaceMacros == null ? null : namespaceMacros.get(symbol.getName());
  }

  void defineMacro(Symbol symbol, HaraMacro macro) {
    if (symbol.getNamespace() != null) {
      throw new HaraException("defmacro name must not be qualified");
    }
    macros
        .computeIfAbsent(currentNamespace.name(), ignored -> new ConcurrentHashMap<>())
        .put(symbol.getName(), macro);
  }

  private void installNumericBuiltins() {
    installNumericBuiltins(currentNamespace);
  }

  private void installNumericBuiltins(HaraNamespace target) {
    target.define("+", new VariadicBuiltin("+", values -> arithmetic("+", values)));
    target.define("-", new VariadicBuiltin("-", values -> arithmetic("-", values)));
    target.define("*", new VariadicBuiltin("*", values -> arithmetic("*", values)));
    target.define("/", new VariadicBuiltin("/", values -> arithmetic("/", values)));
    target.define("mod", new VariadicBuiltin("mod", values -> arithmetic("mod", values)));
    target.define("=", new VariadicBuiltin("=", values -> compare("=", values)));
    target.define("not=", new VariadicBuiltin("not=", values -> compare("not=", values)));
    target.define("<", new VariadicBuiltin("<", values -> compare("<", values)));
    target.define("<=", new VariadicBuiltin("<=", values -> compare("<=", values)));
    target.define(">", new VariadicBuiltin(">", values -> compare(">", values)));
    target.define(">=", new VariadicBuiltin(">=", values -> compare(">=", values)));
    target.define("bigint", new UnaryBuiltin("bigint", HaraNumericConversions::toBigInteger));
    target.define("bigdec", new UnaryBuiltin("bigdec", HaraNumericConversions::toBigDecimal));
    target.define("double", new UnaryBuiltin("double", HaraNumericConversions::toDouble));
    target.define(
        "not", new UnaryBuiltin("not", value -> value == null || Boolean.FALSE.equals(value)));
    target.define("load-string", new UnaryBuiltin("load-string", this::loadString));
    target.define("load-file", new UnaryBuiltin("load-file", this::loadFile));
    target.define("load-resource", new UnaryBuiltin("load-resource", this::loadResource));
    target.define("require", new VariadicBuiltin("require", this::requireModule));
    target.define("refer", new UnaryBuiltin("refer", this::referNamespace));
    target.define("in-ns", new UnaryBuiltin("in-ns", this::inNamespace));
    target.define("use", new UnaryBuiltin("use", this::useNamespace));
    target.define("iter", new UnaryBuiltin("iter", this::iterValue));
    target.define("iter-has?", new UnaryBuiltin("iter-has?", this::iterHasNext));
    target.define("iter-next", new UnaryBuiltin("iter-next", this::iterNext));
    target.define("iter-close", new UnaryBuiltin("iter-close", this::iterClose));
    target.define("concat", new VariadicBuiltin("concat", this::concatIterators));
    target.define("iter-map", new VariadicBuiltin("iter-map", this::iterMap));
    target.define("iter-filter", new VariadicBuiltin("iter-filter", this::iterFilter));
    target.define("iter-take-while", new VariadicBuiltin("iter-take-while", this::iterTakeWhile));
    target.define("iter-drop-while", new VariadicBuiltin("iter-drop-while", this::iterDropWhile));
    target.define("iter-mapcat", new VariadicBuiltin("iter-mapcat", this::iterMapcat));
    target.define("iter-keep", new VariadicBuiltin("iter-keep", this::iterKeep));
    target.define("iter-every?", new VariadicBuiltin("iter-every?", this::iterEvery));
    target.define("iter-any?", new VariadicBuiltin("iter-any?", this::iterAny));
    target.define("iter-some", new VariadicBuiltin("iter-some", this::iterSome));
    target.define("reduce", new VariadicBuiltin("reduce", this::reduceIterator));
    target.define("iter-take", new VariadicBuiltin("iter-take", this::iterTake));
    target.define("iter-drop", new VariadicBuiltin("iter-drop", this::iterDrop));
    target.define("iter-zip", new VariadicBuiltin("iter-zip", this::iterZip));
    target.define("iter-cycle", new UnaryBuiltin("iter-cycle", this::iterCycle));
    target.define(
        "iter-partition-pair", new UnaryBuiltin("iter-partition-pair", this::iterPartitionPair));
    target.define(
        "iter-partition-all",
        new VariadicBuiltin("iter-partition-all", values -> iterPartition(values, true)));
    target.define(
        "iter-partition",
        new VariadicBuiltin("iter-partition", values -> iterPartition(values, false)));
    target.define("iter-range", new VariadicBuiltin("iter-range", this::iterRange));
    target.define("iter-constantly", new UnaryBuiltin("iter-constantly", Iter::constantly));
    target.define("iter-repeatedly", new UnaryBuiltin("iter-repeatedly", this::iterRepeatedly));
    target.define("iter-iterate", new VariadicBuiltin("iter-iterate", this::iterIterate));
    target.define("alter-var-root", new VariadicBuiltin("alter-var-root", this::alterVarRoot));
    target.define("apply", new VariadicBuiltin("apply", this::applyFunction));
    target.define("module-revision", new UnaryBuiltin("module-revision", this::moduleRevision));
    target.define(
        "module-dependencies", new UnaryBuiltin("module-dependencies", this::moduleDependencies));
    target.define(
        "count",
        new UnaryBuiltin("count", value -> protocolCall("ICount", "count", new Object[] {value})));
    target.define(
        "get", new VariadicBuiltin("get", values -> protocolCall("ILookup", "lookup", values)));
    target.define(
        "assoc", new VariadicBuiltin("assoc", values -> protocolCall("IAssoc", "assoc", values)));
    target.define(
        "conj", new VariadicBuiltin("conj", values -> protocolCall("IConj", "conj", values)));
    target.define(
        "cons",
        new VariadicBuiltin(
            "cons",
            values -> {
              if (values.length != 2) {
                throw new HaraException("cons expects an item and a collection");
              }
              return protocolCall("ICons", "cons", new Object[] {values[1], values[0]});
            }));
    target.define("nth", new VariadicBuiltin("nth", values -> protocolCall("INth", "nth", values)));
    target.define(
        "empty",
        new UnaryBuiltin("empty", value -> protocolCall("IEmpty", "empty", new Object[] {value})));
    target.define(
        "dissoc",
        new VariadicBuiltin(
            "dissoc",
            values -> {
              if (values.length < 1) {
                throw new HaraException("dissoc expects a collection and at least one key");
              }
              Object result = values[0];
              for (int i = 1; i < values.length; i++) {
                if (HaraBox.unwrap(result) == null
                    || HaraBox.unwrap(result) == HaraNull.SINGLETON) {
                  return result;
                }
                result = protocolCall("IDissoc", "dissoc", new Object[] {result, values[i]});
              }
              return result;
            }));
    target.define(
        "peek",
        new UnaryBuiltin(
            "peek", value -> protocolCall("INavigation", "peek-first", new Object[] {value})));
    target.define(
        "pop",
        new UnaryBuiltin(
            "pop", value -> protocolCall("INavigation", "pop-first", new Object[] {value})));
  }

  private Object arithmetic(String operator, Object[] values) {
    if (operator.equals("+") && values.length == 0) return 0L;
    if (operator.equals("*") && values.length == 0) return 1L;
    if (values.length == 0) {
      throw new HaraException(operator + " expects at least one number");
    }
    if (operator.equals("mod") && values.length != 2) {
      throw new HaraException("mod expects two numbers");
    }
    if (operator.equals("-") && values.length == 1) return Num.minusP(values[0]);
    if (operator.equals("/") && values.length == 1) return Num.divide(1L, values[0]);
    Object result = values[0];
    for (int i = 1; i < values.length; i++) {
      Object value = values[i];
      if (operator.equals("+")) {
        result = Num.addP(result, value);
      } else if (operator.equals("-")) {
        result = Num.minusP(result, value);
      } else if (operator.equals("*")) {
        result = Num.multiplyP(result, value);
      } else if (operator.equals("/")) {
        result = Num.divide(result, value);
      } else if (operator.equals("mod")) {
        result = Num.remainder(result, value);
      } else {
        throw new HaraException("Unknown arithmetic operator: " + operator);
      }
    }
    return result;
  }

  private Object compare(String operator, Object[] values) {
    if (values.length < 2) {
      throw new HaraException(operator + " expects at least two arguments");
    }
    Object previous = HaraBox.unwrap(values[0]);
    for (int i = 1; i < values.length; i++) {
      Object current = HaraBox.unwrap(values[i]);
      boolean matches;
      if (operator.equals("=") || operator.equals("not=")) {
        boolean equal = Eq.eq(previous, current);
        if (operator.equals("not=") && !equal) return true;
        matches = equal;
      } else {
        if (!(previous instanceof Number) || !(current instanceof Number)) {
          throw new HaraException("comparison expects two numbers");
        }
        int comparison = Num.compare((Number) previous, (Number) current);
        if (operator.equals("<")) matches = comparison < 0;
        else if (operator.equals("<=")) matches = comparison <= 0;
        else if (operator.equals(">")) matches = comparison > 0;
        else if (operator.equals(">=")) matches = comparison >= 0;
        else throw new HaraException("Unknown comparison operator: " + operator);
      }
      if (!matches) return false;
      previous = current;
    }
    return !operator.equals("not=");
  }

  private Object protocolCall(String protocolName, String methodName, Object[] values) {
    if (values.length == 0) {
      throw new HaraException(methodName + " expects a collection value");
    }
    HaraVar variable = resolve(Symbol.create(protocolName));
    if (variable == null || !(variable.get() instanceof HaraProtocol)) {
      throw new HaraException("Missing protocol: " + protocolName);
    }
    Object receiver = HaraBox.unwrap(values[0]);
    if (isHostObject(receiver)) {
      receiver = asHostObject(receiver);
    }
    Object[] arguments = new Object[values.length - 1];
    System.arraycopy(values, 1, arguments, 0, arguments.length);
    return ((HaraProtocol) variable.get()).invoke(methodName, receiver, arguments);
  }

  private Object loadString(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("load-string expects a string");
    }
    ContextSnapshot snapshot = snapshot();
    try {
      return parseAndExecute((String) value, "<string>");
    } catch (RuntimeException error) {
      restore(snapshot);
      throw error;
    }
  }

  private Object loadFile(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("load-file expects a path string");
    }
    ContextSnapshot snapshot = snapshot();
    try {
      Path path = canonicalPath((String) value);
      Object result = parseAndExecute(Files.readString(path), path.toString());
      registerModule(path);
      return result;
    } catch (IOException | RuntimeException error) {
      restore(snapshot);
      throw new HaraException(
          "Unable to load Hara file: " + value + " (" + error.getMessage() + ")");
    }
  }

  private Object loadResource(Object value) {
    if (!(value instanceof String) || ((String) value).isEmpty()) {
      throw new HaraException("load-resource expects a non-empty resource name");
    }
    ContextSnapshot snapshot = snapshot();
    try (InputStream input =
        HaraContext.class.getClassLoader().getResourceAsStream((String) value)) {
      if (input == null) {
        throw new HaraException("Unable to find Hara resource: " + value);
      }
      return parseAndExecute(
          new String(input.readAllBytes(), StandardCharsets.UTF_8), "classpath:" + value);
    } catch (IOException | RuntimeException error) {
      restore(snapshot);
      if (error instanceof HaraException) throw (HaraException) error;
      throw new HaraException(
          "Unable to load Hara resource: " + value + " (" + error.getMessage() + ")");
    }
  }

  public Object requireModule(Object[] arguments) {
    if (arguments.length < 1 || arguments.length > 2 || !(arguments[0] instanceof String)) {
      throw new HaraException("require expects a path string");
    }
    String callerNamespace = currentNamespace.name();
    try {
      String requested = (String) arguments[0];
      boolean classpath = requested.startsWith("classpath:") || getResource(requested) != null;
      String resourceName =
          requested.startsWith("classpath:") ? requested.substring(10) : requested;
      String key = classpath ? "classpath:" + resourceName : canonicalPath(requested).toString();
      boolean reload =
          arguments.length == 2 && requireOption(arguments[1], "reload") == Boolean.TRUE;
      if (!loadingStack.isEmpty()) {
        moduleDependencies
            .computeIfAbsent(loadingStack.peekLast(), ignored -> ConcurrentHashMap.newKeySet())
            .add(key);
      }
      if (reload || !modules.containsKey(key)) {
        if (!loadingModules.add(key)) {
          throw new HaraException("Cyclic module require: " + key);
        }
        Map<String, HaraMacro> callerMacrosBefore =
            new LinkedHashMap<>(macros.getOrDefault(callerNamespace, Map.of()));
        try {
          loadingStack.addLast(key);
          if (classpath) {
            loadResource(resourceName);
            registerResource(resourceName);
          } else {
            loadFile(key);
          }
        } finally {
          loadingStack.removeLastOccurrence(key);
          loadingModules.remove(key);
        }
        ModuleRecord loaded = modules.get(key);
        relocateLoadedMacros(callerNamespace, callerMacrosBefore, loaded);
      }
      currentNamespace = namespace(callerNamespace);
      installNumericBuiltins(currentNamespace);
      if (arguments.length == 2) {
        applyRequireOptions(arguments[1], modules.get(key));
      }
      return null;
    } finally {
      currentNamespace = namespace(callerNamespace);
      installNumericBuiltins(currentNamespace);
    }
  }

  private void applyRequireOptions(Object options, ModuleRecord module) {
    if (!(options instanceof IMapType<?, ?>) || module == null) {
      throw new HaraException("require options expect a map");
    }
    @SuppressWarnings("rawtypes")
    Object alias = unwrapQuoted(((IMapType) options).lookup(Keyword.create("as")));
    if (alias != null && (!(alias instanceof Symbol) || ((Symbol) alias).getNamespace() != null)) {
      throw new HaraException("require :as expects an unqualified symbol");
    }
    if (alias != null) defineAlias((Symbol) alias, Symbol.create(module.namespace));

    @SuppressWarnings("rawtypes")
    Object refer = unwrapQuoted(((IMapType) options).lookup(Keyword.create("refer")));
    if (refer != null) {
      if (!(refer instanceof ILinearType<?>)) {
        throw new HaraException("require :refer expects a sequential collection of symbols");
      }
      HaraNamespace target = namespaces.get(module.namespace);
      for (Object value : (ILinearType<?>) refer) {
        if (!(value instanceof Symbol) || ((Symbol) value).getNamespace() != null) {
          throw new HaraException("require :refer expects unqualified symbols");
        }
        Symbol symbol = (Symbol) value;
        HaraVar variable = target == null ? null : target.lookup(symbol.getName());
        if (variable == null) {
          throw new HaraException(
              "Cannot refer missing var " + symbol.getName() + " from " + module.namespace);
        }
        currentNamespace.refer(symbol.getName(), variable);
        Map<String, HaraMacro> targetMacros = macros.get(module.namespace);
        if (targetMacros != null && targetMacros.containsKey(symbol.getName())) {
          macros
              .computeIfAbsent(currentNamespace.name(), ignored -> new ConcurrentHashMap<>())
              .put(symbol.getName(), targetMacros.get(symbol.getName()));
        }
      }
    }

    Object referMacros = unwrapQuoted(((IMapType) options).lookup(Keyword.create("refer-macros")));
    if (referMacros == null) return;
    if (!(referMacros instanceof ILinearType<?>)) {
      throw new HaraException("require :refer-macros expects a sequential collection of symbols");
    }
    Map<String, HaraMacro> targetMacros = macros.get(module.namespace);
    for (Object value : (ILinearType<?>) referMacros) {
      if (!(value instanceof Symbol) || ((Symbol) value).getNamespace() != null) {
        throw new HaraException("require :refer-macros expects unqualified symbols");
      }
      String name = ((Symbol) value).getName();
      HaraMacro macro = targetMacros == null ? null : targetMacros.get(name);
      if (macro == null) {
        throw new HaraException("Cannot refer missing macro " + name + " from " + module.namespace);
      }
      macros
          .computeIfAbsent(currentNamespace.name(), ignored -> new ConcurrentHashMap<>())
          .put(name, macro);
    }
  }

  private Object unwrapQuoted(Object value) {
    if (value instanceof List<?>
        && ((List<?>) value).count() == 2
        && Symbol.create("quote").equals(((List<?>) value).nth(0))) {
      return ((List<?>) value).nth(1);
    }
    return value;
  }

  private void relocateLoadedMacros(
      String callerNamespace, Map<String, HaraMacro> callerMacrosBefore, ModuleRecord module) {
    if (module == null || callerNamespace.equals(module.namespace)) return;
    Map<String, HaraMacro> callerMacros = macros.get(callerNamespace);
    if (callerMacros == null) return;
    Map<String, HaraMacro> moduleMacros =
        macros.computeIfAbsent(module.namespace, ignored -> new ConcurrentHashMap<>());
    for (Map.Entry<String, HaraMacro> entry : new LinkedHashMap<>(callerMacros).entrySet()) {
      HaraMacro previous = callerMacrosBefore.get(entry.getKey());
      if (previous != entry.getValue()) {
        moduleMacros.put(entry.getKey(), entry.getValue());
        callerMacros.remove(entry.getKey(), entry.getValue());
      }
    }
  }

  @SuppressWarnings("rawtypes")
  private Object requireOption(Object options, String name) {
    if (!(options instanceof IMapType)) {
      throw new HaraException("require options expect a map");
    }
    return ((IMapType) options).lookup(Keyword.create(name));
  }

  private Object moduleRevision(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("module-revision expects a path string");
    }
    String requested = (String) value;
    String key =
        requested.startsWith("classpath:")
            ? requested
            : (getResource(requested) == null
                ? canonicalPath(requested).toString()
                : "classpath:" + requested);
    ModuleRecord module = modules.get(key);
    return module == null ? 0L : module.revision;
  }

  private Object moduleDependencies(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("module-dependencies expects a path string");
    }
    String requested = (String) value;
    String key =
        requested.startsWith("classpath:")
            ? requested
            : (getResource(requested) == null
                ? canonicalPath(requested).toString()
                : "classpath:" + requested);
    Set<String> dependencies = moduleDependencies.getOrDefault(key, Set.of());
    return BuiltinStruct.vector(new LinkedHashSet<>(dependencies).toArray());
  }

  private Object referNamespace(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("refer expects a namespace string");
    }
    HaraNamespace target = namespaces.get((String) value);
    if (target == null) {
      throw new HaraException("Cannot refer missing namespace: " + value);
    }
    for (Map.Entry<String, HaraVar> entry : target.vars.entrySet()) {
      currentNamespace.refer(entry.getKey(), entry.getValue());
    }
    Map<String, HaraMacro> targetMacros = macros.get(target.name());
    if (targetMacros != null) {
      macros
          .computeIfAbsent(currentNamespace.name(), ignored -> new ConcurrentHashMap<>())
          .putAll(targetMacros);
    }
    return null;
  }

  private Object inNamespace(Object value) {
    if (!(value instanceof Symbol) || ((Symbol) value).getNamespace() != null) {
      throw new HaraException("in-ns expects an unqualified namespace symbol");
    }
    setCurrentNamespace((Symbol) value);
    return value;
  }

  private Object useNamespace(Object value) {
    if (value instanceof Symbol && ((Symbol) value).getNamespace() == null) {
      return referNamespace(((Symbol) value).getName());
    }
    return referNamespace(value);
  }

  private Object iterValue(Object value) {
    Object target = HaraBox.unwrap(value);
    if (target == null || target == HaraNull.SINGLETON) return Iter.emptyIterator();
    if (target instanceof String) return Iter.chars(((String) target).toCharArray());
    try {
      return Iter.iter(target);
    } catch (RuntimeException error) {
      throw new HaraException("iter does not support value: " + target);
    }
  }

  private Object iterHasNext(Object value) {
    Iterator<?> iterator = requireIterator(value, "iter-has?");
    return iterator.hasNext();
  }

  private Object iterNext(Object value) {
    Iterator<?> iterator = requireIterator(value, "iter-next");
    if (!iterator.hasNext()) throw new HaraException("iter-next reached the end of the iterator");
    return iterator.next();
  }

  private Object iterClose(Object value) {
    Iterator<?> iterator = requireIterator(value, "iter-close");
    Iter.close(iterator);
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object concatIterators(Object[] values) {
    return Iter.concat((Iterator) Iter.map(Iter.objects(values), value -> Iter.iter(value)));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterMap(Object[] values) {
    requireIteratorArity(values, 2, "iter-map");
    Iterator source = (Iterator) iterValue(values[1]);
    Object function = values[0];
    return closeable(
        Iter.map(source, value -> invokeCallable(function, new Object[] {value})), source);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterFilter(Object[] values) {
    requireIteratorArity(values, 2, "iter-filter");
    Iterator source = (Iterator) iterValue(values[1]);
    Object function = values[0];
    return closeable(
        Iter.filter(source, value -> truthy(invokeCallable(function, new Object[] {value}))),
        source);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterTakeWhile(Object[] values) {
    requireIteratorArity(values, 2, "iter-take-while");
    Iterator source = (Iterator) iterValue(values[1]);
    Object function = values[0];
    return closeable(
        new CloseableIterator<Object>() {
          private boolean finished;
          private boolean ready;
          private Object next;

          private void prime() {
            if (finished || ready) return;
            if (!source.hasNext()) {
              finished = true;
              Iter.close(source);
              return;
            }
            Object candidate = source.next();
            if (!truthy(invokeCallable(function, new Object[] {candidate}))) {
              finished = true;
              Iter.close(source);
              return;
            }
            next = candidate;
            ready = true;
          }

          @Override
          public boolean hasNext() {
            prime();
            return ready;
          }

          @Override
          public Object next() {
            prime();
            if (!ready) throw new NoSuchElementException();
            Object result = next;
            next = null;
            ready = false;
            return result;
          }

          @Override
          public void close() {
            finished = true;
            ready = false;
            next = null;
            Iter.close(source);
          }
        },
        source);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterDropWhile(Object[] values) {
    requireIteratorArity(values, 2, "iter-drop-while");
    Iterator source = (Iterator) iterValue(values[1]);
    Object function = values[0];
    return closeable(
        new CloseableIterator<Object>() {
          private boolean dropped;
          private boolean finished;
          private boolean ready;
          private Object next;

          private void prime() {
            if (finished || ready) return;
            while (!dropped && source.hasNext()) {
              Object candidate = source.next();
              if (!truthy(invokeCallable(function, new Object[] {candidate}))) {
                next = candidate;
                ready = true;
                dropped = true;
              }
            }
            if (!dropped) {
              finished = true;
              Iter.close(source);
              return;
            }
            if (!ready && source.hasNext()) {
              next = source.next();
              ready = true;
            } else if (!ready) {
              finished = true;
              Iter.close(source);
            }
          }

          @Override
          public boolean hasNext() {
            prime();
            return ready;
          }

          @Override
          public Object next() {
            prime();
            if (!ready) throw new NoSuchElementException();
            Object result = next;
            next = null;
            ready = false;
            return result;
          }

          @Override
          public void close() {
            finished = true;
            ready = false;
            next = null;
            Iter.close(source);
          }
        },
        source);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterMapcat(Object[] values) {
    requireIteratorArity(values, 2, "iter-mapcat");
    Iterator source = (Iterator) iterValue(values[1]);
    Object function = values[0];
    Iterator result =
        Iter.mapcat(
            source, value -> (Iterator) iterValue(invokeCallable(function, new Object[] {value})));
    return closeable(result, source);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterKeep(Object[] values) {
    requireIteratorArity(values, 2, "iter-keep");
    Iterator source = (Iterator) iterValue(values[1]);
    Object function = values[0];
    return closeable(
        Iter.keep(
            source,
            value -> {
              Object result = HaraBox.unwrap(invokeCallable(function, new Object[] {value}));
              return result == HaraNull.SINGLETON ? null : result;
            }),
        source);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterEvery(Object[] values) {
    requireIteratorArity(values, 2, "iter-every?");
    Iterator source = (Iterator) iterValue(values[1]);
    Object function = values[0];
    return Iter.every(source, value -> truthy(invokeCallable(function, new Object[] {value})));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterAny(Object[] values) {
    requireIteratorArity(values, 2, "iter-any?");
    Iterator source = (Iterator) iterValue(values[1]);
    Object function = values[0];
    return Iter.any(source, value -> truthy(invokeCallable(function, new Object[] {value})));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterSome(Object[] values) {
    requireIteratorArity(values, 2, "iter-some");
    Iterator source = (Iterator) iterValue(values[1]);
    Object function = values[0];
    return Iter.some(source, value -> truthy(invokeCallable(function, new Object[] {value})));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object reduceIterator(Object[] values) {
    if (values.length != 2 && values.length != 3) {
      throw new HaraException(
          "reduce expects a function and collection, optionally with an initial value");
    }
    Object function = values[0];
    Iterator source = (Iterator) iterValue(values[values.length - 1]);
    try {
      Object accumulator;
      if (values.length == 3) {
        accumulator = values[1];
      } else {
        if (!source.hasNext()) {
          throw new HaraException(
              "reduce cannot reduce an empty collection without an initial value");
        }
        accumulator = source.next();
      }
      while (source.hasNext()) {
        accumulator = invokeCallable(function, new Object[] {accumulator, source.next()});
      }
      return accumulator;
    } finally {
      Iter.close(source);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterTake(Object[] values) {
    requireIteratorArity(values, 2, "iter-take");
    Iterator source = (Iterator) iterValue(values[1]);
    return closeable(Iter.take(source, iterationCount(values[0], "iter-take")), source);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterDrop(Object[] values) {
    requireIteratorArity(values, 2, "iter-drop");
    Iterator source = (Iterator) iterValue(values[1]);
    return closeable(Iter.drop(source, iterationCount(values[0], "iter-drop")), source);
  }

  private Object iterZip(Object[] values) {
    if (values.length == 0) {
      throw new HaraException("iter-zip expects at least one source");
    }
    return new CloseableIterator<Object[]>() {
      private Iterator<?>[] sources;
      private boolean closed;

      private void initialize() {
        if (sources != null) return;
        sources = new Iterator<?>[values.length];
        for (int i = 0; i < values.length; i++) {
          sources[i] = (Iterator<?>) iterValue(values[i]);
        }
      }

      @Override
      public boolean hasNext() {
        if (closed) return false;
        initialize();
        for (Iterator<?> source : sources) {
          if (!source.hasNext()) return false;
        }
        return true;
      }

      @Override
      public Object[] next() {
        if (!hasNext()) throw new NoSuchElementException();
        Object[] result = new Object[sources.length];
        for (int i = 0; i < sources.length; i++) {
          result[i] = sources[i].next();
        }
        return result;
      }

      @Override
      public void close() {
        if (closed) return;
        closed = true;
        if (sources != null) {
          for (Iterator<?> source : sources) Iter.close(source);
        }
      }
    };
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterCycle(Object value) {
    return Iter.cycle(() -> (Iterator) iterValue(value));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterPartitionPair(Object value) {
    Iterator<Object> source = (Iterator<Object>) iterValue(value);
    Iterator<Map.Entry<Object, Object>> pairs = Iter.partitionPair(source);
    return Iter.map(
        pairs, pair -> BuiltinStruct.vector(new Object[] {pair.getKey(), pair.getValue()}));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterPartition(Object[] values, boolean includePartial) {
    requireIteratorArity(values, 2, includePartial ? "iter-partition-all" : "iter-partition");
    int size = iterationCount(values[0], includePartial ? "iter-partition-all" : "iter-partition");
    if (size == 0) {
      throw new HaraException(
          (includePartial ? "iter-partition-all" : "iter-partition") + " expects a positive count");
    }
    Iterator source = (Iterator) iterValue(values[1]);
    return closeable(
        new CloseableIterator<Object>() {
          private boolean done;
          private boolean ready;
          private Object next;

          private void prime() {
            if (done || ready) return;
            if (!source.hasNext()) {
              done = true;
              Iter.close(source);
              return;
            }
            ArrayList<Object> chunk = new ArrayList<>(size);
            while (chunk.size() < size && source.hasNext()) {
              chunk.add(source.next());
            }
            if (!includePartial && chunk.size() < size) {
              done = true;
              Iter.close(source);
              return;
            }
            next = BuiltinStruct.vector(chunk.toArray());
            ready = true;
          }

          @Override
          public boolean hasNext() {
            prime();
            return ready;
          }

          @Override
          public Object next() {
            prime();
            if (!ready) throw new NoSuchElementException();
            Object result = next;
            next = null;
            ready = false;
            return result;
          }

          @Override
          public void close() {
            done = true;
            ready = false;
            next = null;
            Iter.close(source);
          }
        },
        source);
  }

  private Object iterRange(Object[] values) {
    if (values.length < 1 || values.length > 2) {
      throw new HaraException("iter-range expects an end or start and end");
    }
    if (!(values[0] instanceof Number) || (values.length == 2 && !(values[1] instanceof Number))) {
      throw new HaraException("iter-range expects numeric bounds");
    }
    long start = values.length == 1 ? 0L : ((Number) values[0]).longValue();
    long end = ((Number) values[values.length - 1]).longValue();
    return Iter.range(start, end);
  }

  private Object iterRepeatedly(Object function) {
    return Iter.repeatedly(() -> invokeCallable(function, new Object[0]));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterIterate(Object[] values) {
    requireIteratorArity(values, 2, "iter-iterate");
    Object function = values[0];
    return Iter.iterate(values[1], value -> invokeCallable(function, new Object[] {value}));
  }

  private static void requireIteratorArity(Object[] values, int expected, String name) {
    if (values.length != expected) {
      throw new HaraException(name + " expects " + (expected - 1) + " arguments");
    }
  }

  private static int iterationCount(Object value, String name) {
    if (!(value instanceof Number)) {
      throw new HaraException(name + " expects a numeric count");
    }
    long count = ((Number) value).longValue();
    if (count < 0 || count > Integer.MAX_VALUE) {
      throw new HaraException(name + " count is out of bounds: " + count);
    }
    return (int) count;
  }

  private static boolean truthy(Object value) {
    return value != null && value != HaraNull.SINGLETON && !Boolean.FALSE.equals(value);
  }

  @SuppressWarnings("unchecked")
  private static Iterator<?> closeable(Iterator<?> iterator, Iterator<?>... sources) {
    return new CloseableIterator<Object>() {
      private boolean closed;

      @Override
      public boolean hasNext() {
        return !closed && iterator.hasNext();
      }

      @Override
      public Object next() {
        if (!hasNext()) throw new NoSuchElementException();
        return iterator.next();
      }

      @Override
      public void close() {
        if (closed) return;
        closed = true;
        for (Iterator<?> source : sources) Iter.close(source);
        Iter.close(iterator);
      }
    };
  }

  private Object alterVarRoot(Object[] values) {
    if (values.length < 2 || !(HaraBox.unwrap(values[0]) instanceof HaraVar)) {
      throw new HaraException("alter-var-root expects a Var, function, and optional arguments");
    }
    HaraVar var = (HaraVar) HaraBox.unwrap(values[0]);
    Object function = HaraBox.unwrap(values[1]);
    Object[] arguments = new Object[values.length - 1];
    arguments[0] = var.deref();
    System.arraycopy(values, 2, arguments, 1, values.length - 2);
    Object updated = invokeCallable(function, arguments);
    return var.reset(updated);
  }

  private Object applyFunction(Object[] values) {
    if (values.length < 2) {
      throw new HaraException("apply expects a function and a final sequential value");
    }
    ArrayList<Object> arguments = new ArrayList<>();
    for (int i = 1; i < values.length - 1; i++) {
      arguments.add(values[i]);
    }
    Iterator<?> tail = (Iterator<?>) iterValue(values[values.length - 1]);
    while (tail.hasNext()) {
      arguments.add(tail.next());
    }
    return invokeCallable(values[0], arguments.toArray());
  }

  private Object invokeCallable(Object value, Object[] arguments) {
    Object function = HaraBox.unwrap(value);
    if (function instanceof HaraFunction) {
      HaraFunction haraFunction = (HaraFunction) function;
      HaraFunction selected = haraFunction.resolveArity(arguments.length);
      if (selected == null) {
        throw new HaraException("function has no matching arity: " + arguments.length);
      }
      return HaraBox.export(selected.callTarget().call(selected.callArguments(arguments)));
    }
    if (function instanceof HaraMultiFunction) {
      return HaraBox.export(((HaraMultiFunction) function).invoke(arguments));
    }
    if (function instanceof HaraStruct || function instanceof IFn) {
      return ifnProtocol.invoke("invoke", function, arguments);
    }
    if (function instanceof HaraType) {
      HaraType type = (HaraType) function;
      if (arguments.length != type.arity()) {
        throw new HaraException("constructor has no matching arity: " + arguments.length);
      }
      return new HaraStruct(type, arguments);
    }
    throw new HaraException("value is not callable: " + function);
  }

  private Iterator<?> requireIterator(Object value, String operation) {
    Object target = HaraBox.unwrap(value);
    if (!(target instanceof Iterator<?>)) {
      throw new HaraException(operation + " expects an iterator");
    }
    return (Iterator<?>) target;
  }

  private Path canonicalPath(String value) {
    return Path.of(value).toAbsolutePath().normalize();
  }

  private void registerModule(Path path) {
    String key = path.toString();
    ModuleRecord previous = modules.get(key);
    String namespaceName = currentNamespace.name();
    modules.put(
        key, new ModuleRecord(key, namespaceName, previous == null ? 1L : previous.revision + 1L));
    moduleDependencies.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
  }

  private void registerResource(String resourceName) {
    String key = "classpath:" + resourceName;
    ModuleRecord previous = modules.get(key);
    String namespaceName = currentNamespace.name();
    modules.put(
        key, new ModuleRecord(key, namespaceName, previous == null ? 1L : previous.revision + 1L));
    moduleDependencies.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
  }

  private java.net.URL getResource(String resourceName) {
    return HaraContext.class.getClassLoader().getResource(resourceName);
  }

  private ContextSnapshot snapshot() {
    Map<String, Map<String, Object>> values = new LinkedHashMap<>();
    Map<String, Map<String, HaraVar>> bindings = new LinkedHashMap<>();
    for (Map.Entry<String, HaraNamespace> namespace : namespaces.entrySet()) {
      Map<String, Object> namespaceValues = new LinkedHashMap<>();
      Map<String, HaraVar> namespaceBindings = new LinkedHashMap<>();
      for (Map.Entry<String, HaraVar> var : namespace.getValue().vars.entrySet()) {
        namespaceValues.put(var.getKey(), var.getValue().get());
        namespaceBindings.put(var.getKey(), var.getValue());
      }
      values.put(namespace.getKey(), namespaceValues);
      bindings.put(namespace.getKey(), namespaceBindings);
    }
    Map<String, Map<String, HaraMacro>> macroValues = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, HaraMacro>> entry : macros.entrySet()) {
      macroValues.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    Map<String, Map<String, String>> aliasValues = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : aliases.entrySet()) {
      aliasValues.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    Map<String, Set<String>> dependencyValues = new LinkedHashMap<>();
    for (Map.Entry<String, Set<String>> entry : moduleDependencies.entrySet()) {
      dependencyValues.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
    }
    return new ContextSnapshot(
        currentNamespace.name(),
        values,
        bindings,
        macroValues,
        aliasValues,
        new LinkedHashMap<>(modules),
        dependencyValues);
  }

  private void restore(ContextSnapshot snapshot) {
    namespaces.clear();
    for (Map.Entry<String, Map<String, Object>> entry : snapshot.values.entrySet()) {
      HaraNamespace namespace = namespace(entry.getKey());
      for (Map.Entry<String, Object> value : entry.getValue().entrySet()) {
        HaraVar binding = snapshot.bindings.get(entry.getKey()).get(value.getKey());
        if (binding == null) {
          namespace.define(value.getKey(), value.getValue());
        } else {
          binding.set(value.getValue());
          namespace.refer(value.getKey(), binding);
        }
      }
    }
    currentNamespace = namespace(snapshot.currentNamespace);
    macros.clear();
    for (Map.Entry<String, Map<String, HaraMacro>> entry : snapshot.macros.entrySet()) {
      macros.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
    }
    aliases.clear();
    for (Map.Entry<String, Map<String, String>> entry : snapshot.aliases.entrySet()) {
      aliases.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
    }
    modules.clear();
    modules.putAll(snapshot.modules);
    moduleDependencies.clear();
    for (Map.Entry<String, Set<String>> entry : snapshot.moduleDependencies.entrySet()) {
      moduleDependencies.put(entry.getKey(), ConcurrentHashMap.newKeySet());
      moduleDependencies.get(entry.getKey()).addAll(entry.getValue());
    }
  }

  private static final class ContextSnapshot {
    private final String currentNamespace;
    private final Map<String, Map<String, Object>> values;
    private final Map<String, Map<String, HaraVar>> bindings;
    private final Map<String, Map<String, HaraMacro>> macros;
    private final Map<String, Map<String, String>> aliases;
    private final Map<String, ModuleRecord> modules;
    private final Map<String, Set<String>> moduleDependencies;

    private ContextSnapshot(
        String currentNamespace,
        Map<String, Map<String, Object>> values,
        Map<String, Map<String, HaraVar>> bindings,
        Map<String, Map<String, HaraMacro>> macros,
        Map<String, Map<String, String>> aliases,
        Map<String, ModuleRecord> modules,
        Map<String, Set<String>> moduleDependencies) {
      this.currentNamespace = currentNamespace;
      this.values = values;
      this.bindings = bindings;
      this.macros = macros;
      this.aliases = aliases;
      this.modules = modules;
      this.moduleDependencies = moduleDependencies;
    }
  }

  private static final class ModuleRecord {
    private final String path;
    private final String namespace;
    private final long revision;

    private ModuleRecord(String path, String namespace, long revision) {
      this.path = path;
      this.namespace = namespace;
      this.revision = revision;
    }
  }

  private Object parseAndExecute(String sourceText, String name) {
    try {
      Source source = Source.newBuilder(HaraLanguage.ID, sourceText, name).build();
      return environment.parsePublic(source).call();
    } catch (RuntimeException error) {
      if (error instanceof HaraException) {
        throw (HaraException) error;
      }
      throw new HaraException("Unable to evaluate Hara source " + name + ": " + error.getMessage());
    }
  }

  private static final class UnaryBuiltin implements IFn<Object, Object, Object> {
    private final String name;
    private final Function<Object, Object> implementation;

    private UnaryBuiltin(String name, Function<Object, Object> implementation) {
      this.name = name;
      this.implementation = implementation;
    }

    @Override
    public Function<Object, Object> getArg1() {
      return implementation;
    }

    @Override
    public String toString() {
      return "#<builtin " + name + ">";
    }
  }

  private static final class VariadicBuiltin implements IFn<Object, Object, Object> {
    private final String name;
    private final Function<Object[], Object> implementation;

    private VariadicBuiltin(String name, Function<Object[], Object> implementation) {
      this.name = name;
      this.implementation = implementation;
    }

    @Override
    public Function<Object, Object> getArg1() {
      return value -> implementation.apply(new Object[] {value});
    }

    @Override
    public java.util.function.BiFunction<Object, Object, Object> getArg2() {
      return (first, second) -> implementation.apply(new Object[] {first, second});
    }

    @Override
    public Function<Object, Object> getArgN() {
      return values -> implementation.apply((Object[]) values);
    }

    @Override
    public String toString() {
      return "#<builtin " + name + ">";
    }
  }

  private static final class HaraNamespace {
    private final String name;
    private final Map<String, HaraVar> vars = new ConcurrentHashMap<>();

    private HaraNamespace(String name) {
      this.name = name;
    }

    private String name() {
      return name;
    }

    private HaraVar lookup(String symbolName) {
      return vars.get(symbolName);
    }

    private HaraVar define(String symbolName, Object value) {
      return define(symbolName, value, null);
    }

    private HaraVar define(String symbolName, Object value, IMetadata metadata) {
      return vars.compute(
          symbolName,
          (ignored, existing) -> {
            if (existing == null) {
              return new HaraVar(name, symbolName, value, metadata);
            }
            existing.set(value);
            return existing;
          });
    }

    private HaraVar refer(String symbolName, HaraVar value) {
      vars.put(symbolName, value);
      return value;
    }
  }
}

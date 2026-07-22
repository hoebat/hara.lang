package hara.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import hara.kernel.builtin.BuiltinStruct;
import hara.lang.base.Iter;
import hara.lang.data.Symbol;
import hara.lang.data.Keyword;
import hara.lang.data.types.IMapType;
import hara.lang.protocol.IFn;
import hara.lang.protocol.IMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
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
    target.define("bigint", new UnaryBuiltin("bigint", HaraNumericConversions::toBigInteger));
    target.define("bigdec", new UnaryBuiltin("bigdec", HaraNumericConversions::toBigDecimal));
    target.define("double", new UnaryBuiltin("double", HaraNumericConversions::toDouble));
    target.define(
        "not", new UnaryBuiltin("not", value -> value == null || Boolean.FALSE.equals(value)));
    target.define("load-string", new UnaryBuiltin("load-string", this::loadString));
    target.define("load-file", new UnaryBuiltin("load-file", this::loadFile));
    target.define("require", new VariadicBuiltin("require", this::requireModule));
    target.define("refer", new UnaryBuiltin("refer", this::referNamespace));
    target.define("in-ns", new UnaryBuiltin("in-ns", this::inNamespace));
    target.define("use", new UnaryBuiltin("use", this::useNamespace));
    target.define("iter", new UnaryBuiltin("iter", this::iterValue));
    target.define("iter-has?", new UnaryBuiltin("iter-has?", this::iterHasNext));
    target.define("iter-next", new UnaryBuiltin("iter-next", this::iterNext));
    target.define("iter-close", new UnaryBuiltin("iter-close", this::iterClose));
    target.define("concat", new VariadicBuiltin("concat", this::concatIterators));
    target.define("alter-var-root", new VariadicBuiltin("alter-var-root", this::alterVarRoot));
    target.define("apply", new VariadicBuiltin("apply", this::applyFunction));
    target.define("module-revision", new UnaryBuiltin("module-revision", this::moduleRevision));
    target.define(
        "module-dependencies", new UnaryBuiltin("module-dependencies", this::moduleDependencies));
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

  private Object requireModule(Object[] arguments) {
    if (arguments.length < 1 || arguments.length > 2 || !(arguments[0] instanceof String)) {
      throw new HaraException("require expects a path string");
    }
    String callerNamespace = currentNamespace.name();
    try {
      Path path = canonicalPath((String) arguments[0]);
      String key = path.toString();
      if (!loadingStack.isEmpty()) {
        moduleDependencies
            .computeIfAbsent(loadingStack.peekLast(), ignored -> ConcurrentHashMap.newKeySet())
            .add(key);
      }
      if (!modules.containsKey(key)) {
        if (!loadingModules.add(key)) {
          throw new HaraException("Cyclic module require: " + key);
        }
        try {
          loadingStack.addLast(key);
          loadFile(key);
        } finally {
          loadingStack.removeLastOccurrence(key);
          loadingModules.remove(key);
        }
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
    Object alias = ((IMapType) options).lookup(Keyword.create("as"));
    if (alias == null) return;
    if (!(alias instanceof Symbol) || ((Symbol) alias).getNamespace() != null) {
      throw new HaraException("require :as expects an unqualified symbol");
    }
    defineAlias((Symbol) alias, Symbol.create(module.namespace));
  }

  private Object moduleRevision(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("module-revision expects a path string");
    }
    ModuleRecord module = modules.get(canonicalPath((String) value).toString());
    return module == null ? 0L : module.revision;
  }

  private Object moduleDependencies(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("module-dependencies expects a path string");
    }
    Set<String> dependencies =
        moduleDependencies.getOrDefault(canonicalPath((String) value).toString(), Set.of());
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
    if (function instanceof HaraStruct || function instanceof IFn) {
      return ifnProtocol.invoke("invoke", function, arguments);
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

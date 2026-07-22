package hara.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import hara.lang.data.Symbol;
import hara.lang.protocol.IFn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class HaraContext {
  private final TruffleLanguage.Env environment;
  private final Map<String, HaraNamespace> namespaces = new ConcurrentHashMap<>();
  private final Map<String, Map<String, HaraMacro>> macros = new ConcurrentHashMap<>();
  private final Map<String, Map<String, String>> aliases = new ConcurrentHashMap<>();
  private final Map<String, ModuleRecord> modules = new ConcurrentHashMap<>();
  private final Set<String> loadingModules = ConcurrentHashMap.newKeySet();
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
    return currentNamespace.define(symbol.getName(), value);
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
    currentNamespace.define(
        "bigint", new UnaryBuiltin("bigint", HaraNumericConversions::toBigInteger));
    currentNamespace.define(
        "bigdec", new UnaryBuiltin("bigdec", HaraNumericConversions::toBigDecimal));
    currentNamespace.define("double", new UnaryBuiltin("double", HaraNumericConversions::toDouble));
    currentNamespace.define(
        "not", new UnaryBuiltin("not", value -> value == null || Boolean.FALSE.equals(value)));
    currentNamespace.define("load-string", new UnaryBuiltin("load-string", this::loadString));
    currentNamespace.define("load-file", new UnaryBuiltin("load-file", this::loadFile));
    currentNamespace.define("require", new UnaryBuiltin("require", this::requireModule));
    currentNamespace.define("refer", new UnaryBuiltin("refer", this::referNamespace));
    currentNamespace.define(
        "module-revision", new UnaryBuiltin("module-revision", this::moduleRevision));
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

  private Object requireModule(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("require expects a path string");
    }
    Path path = canonicalPath((String) value);
    if (!modules.containsKey(path.toString())) {
      String key = path.toString();
      if (!loadingModules.add(key)) {
        throw new HaraException("Cyclic module require: " + key);
      }
      try {
        loadFile(key);
      } finally {
        loadingModules.remove(key);
      }
    }
    return null;
  }

  private Object moduleRevision(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("module-revision expects a path string");
    }
    ModuleRecord module = modules.get(canonicalPath((String) value).toString());
    return module == null ? 0L : module.revision;
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
      currentNamespace.define(entry.getKey(), entry.getValue().get());
    }
    Map<String, HaraMacro> targetMacros = macros.get(target.name());
    if (targetMacros != null) {
      macros
          .computeIfAbsent(currentNamespace.name(), ignored -> new ConcurrentHashMap<>())
          .putAll(targetMacros);
    }
    return null;
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
  }

  private ContextSnapshot snapshot() {
    Map<String, Map<String, Object>> values = new LinkedHashMap<>();
    for (Map.Entry<String, HaraNamespace> namespace : namespaces.entrySet()) {
      Map<String, Object> namespaceValues = new LinkedHashMap<>();
      for (Map.Entry<String, HaraVar> var : namespace.getValue().vars.entrySet()) {
        namespaceValues.put(var.getKey(), var.getValue().get());
      }
      values.put(namespace.getKey(), namespaceValues);
    }
    Map<String, Map<String, HaraMacro>> macroValues = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, HaraMacro>> entry : macros.entrySet()) {
      macroValues.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    Map<String, Map<String, String>> aliasValues = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : aliases.entrySet()) {
      aliasValues.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
    }
    return new ContextSnapshot(
        currentNamespace.name(), values, macroValues, aliasValues, new LinkedHashMap<>(modules));
  }

  private void restore(ContextSnapshot snapshot) {
    namespaces.clear();
    for (Map.Entry<String, Map<String, Object>> entry : snapshot.values.entrySet()) {
      HaraNamespace namespace = namespace(entry.getKey());
      for (Map.Entry<String, Object> value : entry.getValue().entrySet()) {
        namespace.define(value.getKey(), value.getValue());
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
  }

  private static final class ContextSnapshot {
    private final String currentNamespace;
    private final Map<String, Map<String, Object>> values;
    private final Map<String, Map<String, HaraMacro>> macros;
    private final Map<String, Map<String, String>> aliases;
    private final Map<String, ModuleRecord> modules;

    private ContextSnapshot(
        String currentNamespace,
        Map<String, Map<String, Object>> values,
        Map<String, Map<String, HaraMacro>> macros,
        Map<String, Map<String, String>> aliases,
        Map<String, ModuleRecord> modules) {
      this.currentNamespace = currentNamespace;
      this.values = values;
      this.macros = macros;
      this.aliases = aliases;
      this.modules = modules;
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
      return vars.compute(
          symbolName,
          (ignored, existing) -> {
            if (existing == null) {
              return new HaraVar(name, symbolName, value);
            }
            existing.set(value);
            return existing;
          });
    }
  }
}

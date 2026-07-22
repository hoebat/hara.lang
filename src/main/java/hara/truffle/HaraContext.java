package hara.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import hara.lang.data.Symbol;
import hara.lang.protocol.IFn;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class HaraContext {
  private final TruffleLanguage.Env environment;
  private final Map<String, HaraNamespace> namespaces = new ConcurrentHashMap<>();
  private final Map<String, Map<String, HaraMacro>> macros = new ConcurrentHashMap<>();
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
    HaraNamespace namespace =
        symbol.getNamespace() == null ? currentNamespace : namespaces.get(symbol.getNamespace());
    return namespace == null ? null : namespace.lookup(symbol.getName());
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

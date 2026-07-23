package hara.truffle;

import hara.kernel.base.Parser;
import hara.lang.data.Keyword;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.IMapType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict metadata for a provider-generated namespace loaded through {@code :require}. */
public final class HaraExtensionManifest {
  private static final Pattern NAMESPACE = Pattern.compile("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+");
  private static final Set<String> FIELDS =
      Set.of(
          "namespace",
          "version",
          "provider",
          "module",
          "abi",
          "exports",
          "capabilities",
          "host-calls");
  private static final Set<String> REQUIRED_FIELDS =
      Set.of("namespace", "version", "provider", "module", "abi", "exports", "capabilities");
  private static final Set<String> EXPORT_FIELDS = Set.of("args", "returns", "async");

  private final String namespace;
  private final String version;
  private final String provider;
  private final String module;
  private final String abi;
  private final Map<String, Export> exports;
  private final java.util.List<String> capabilities;
  private final Map<String, java.util.List<String>> hostCalls;

  private HaraExtensionManifest(
      String namespace,
      String version,
      String provider,
      String module,
      String abi,
      Map<String, Export> exports,
      java.util.List<String> capabilities,
      Map<String, java.util.List<String>> hostCalls) {
    this.namespace = namespace;
    this.version = version;
    this.provider = provider;
    this.module = module;
    this.abi = abi;
    this.exports = Collections.unmodifiableMap(new LinkedHashMap<>(exports));
    this.capabilities = Collections.unmodifiableList(new ArrayList<>(capabilities));
    LinkedHashMap<String, java.util.List<String>> copiedHostCalls = new LinkedHashMap<>();
    hostCalls.forEach(
        (service, methods) ->
            copiedHostCalls.put(service, Collections.unmodifiableList(new ArrayList<>(methods))));
    this.hostCalls = Collections.unmodifiableMap(copiedHostCalls);
  }

  public static HaraExtensionManifest parse(String source, String origin) {
    final Object value;
    try {
      value = Parser.LispReader.readString(source, null);
    } catch (RuntimeException error) {
      throw invalid(origin, "cannot parse manifest", error);
    }
    if (!(value instanceof IMapType<?, ?>)) throw invalid(origin, "manifest must be a map");
    IMapType<?, ?> map = (IMapType<?, ?>) value;
    rejectUnknownKeys(map, FIELDS, origin, "manifest");

    String namespace = requireString(map, "namespace", origin);
    if (!NAMESPACE.matcher(namespace).matches()) {
      throw invalid(origin, "namespace must be a qualified lower-case symbol");
    }
    String version = requireString(map, "version", origin);
    String provider = requireKeyword(map, "provider", origin);
    String module = requireString(map, "module", origin);
    if (module.startsWith("/") || module.contains("..") || !module.endsWith(".wasm")) {
      throw invalid(origin, "module must be a relative .wasm file");
    }
    String abi = requireKeyword(map, "abi", origin);
    Map<String, Export> exports = parseExports(lookup(map, "exports"), origin);
    java.util.List<String> capabilities =
        parseKeywords(lookup(map, "capabilities"), origin, "capabilities");
    Map<String, java.util.List<String>> hostCalls =
        parseHostCalls(lookup(map, "host-calls"), origin);
    return new HaraExtensionManifest(
        namespace, version, provider, module, abi, exports, capabilities, hostCalls);
  }

  public String namespace() {
    return namespace;
  }

  public String version() {
    return version;
  }

  public String provider() {
    return provider;
  }

  public String module() {
    return module;
  }

  public String abi() {
    return abi;
  }

  public Map<String, Export> exports() {
    return exports;
  }

  public java.util.List<String> capabilities() {
    return capabilities;
  }

  public Map<String, java.util.List<String>> hostCalls() {
    return hostCalls;
  }

  public boolean permitsHostCall(String service, String method) {
    return hostCalls.getOrDefault(service, java.util.List.of()).contains(method);
  }

  private static Map<String, java.util.List<String>> parseHostCalls(Object value, String origin) {
    if (value == null) return Map.of();
    if (!(value instanceof IMapType<?, ?>)) throw invalid(origin, "host-calls must be a map");
    LinkedHashMap<String, java.util.List<String>> result = new LinkedHashMap<>();
    Iterator<?> iterator = ((IMapType<?, ?>) value).iterator();
    while (iterator.hasNext()) {
      Map.Entry<?, ?> entry = (Map.Entry<?, ?>) iterator.next();
      if (!(entry.getKey() instanceof String) || ((String) entry.getKey()).isBlank()) {
        throw invalid(origin, "host-call services must be non-empty strings");
      }
      Object methods = entry.getValue();
      if (!(methods instanceof ILinearType<?>)) {
        throw invalid(origin, "host-call methods must be vectors");
      }
      ArrayList<String> names = new ArrayList<>();
      for (Object method : (ILinearType<?>) methods) {
        if (!(method instanceof String) || ((String) method).isBlank()) {
          throw invalid(origin, "host-call methods must be non-empty strings");
        }
        names.add((String) method);
      }
      result.put((String) entry.getKey(), names);
    }
    return result;
  }

  private static Map<String, Export> parseExports(Object value, String origin) {
    if (!(value instanceof IMapType<?, ?>)) throw invalid(origin, "exports must be a map");
    Map<String, Export> result = new LinkedHashMap<>();
    Iterator<?> iterator = ((IMapType<?, ?>) value).iterator();
    while (iterator.hasNext()) {
      Map.Entry<?, ?> entry = (Map.Entry<?, ?>) iterator.next();
      if (!(entry.getKey() instanceof String) || ((String) entry.getKey()).isBlank()) {
        throw invalid(origin, "export names must be non-empty strings");
      }
      if (!(entry.getValue() instanceof IMapType<?, ?>)) {
        throw invalid(origin, "export " + entry.getKey() + " must be a map");
      }
      IMapType<?, ?> spec = (IMapType<?, ?>) entry.getValue();
      rejectUnknownKeys(spec, EXPORT_FIELDS, origin, "export " + entry.getKey());
      java.util.List<String> arguments = parseKeywords(lookup(spec, "args"), origin, "export args");
      String returns = requireKeyword(spec, "returns", origin);
      Object asyncValue = lookup(spec, "async");
      if (asyncValue != null && !(asyncValue instanceof Boolean)) {
        throw invalid(origin, "export async must be boolean");
      }
      result.put(
          (String) entry.getKey(), new Export(arguments, returns, Boolean.TRUE.equals(asyncValue)));
    }
    if (result.isEmpty()) throw invalid(origin, "exports cannot be empty");
    return result;
  }

  private static java.util.List<String> parseKeywords(Object value, String origin, String field) {
    if (!(value instanceof ILinearType<?>)) throw invalid(origin, field + " must be a vector");
    ArrayList<String> result = new ArrayList<>();
    for (Object item : (ILinearType<?>) value) {
      if (!(item instanceof Keyword)) throw invalid(origin, field + " must contain keywords");
      result.add(((Keyword) item).getName());
    }
    return result;
  }

  private static String requireString(IMapType<?, ?> map, String field, String origin) {
    Object value = lookup(map, field);
    if (!(value instanceof String) || ((String) value).isBlank()) {
      throw invalid(origin, field + " must be a non-empty string");
    }
    return (String) value;
  }

  private static String requireKeyword(IMapType<?, ?> map, String field, String origin) {
    Object value = lookup(map, field);
    if (!(value instanceof Keyword)) throw invalid(origin, field + " must be a keyword");
    return ((Keyword) value).getName();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Object lookup(IMapType<?, ?> map, String field) {
    return ((IMapType) map).lookup(Keyword.create(field));
  }

  private static void rejectUnknownKeys(
      IMapType<?, ?> map, Set<String> allowed, String origin, String subject) {
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    Iterator<?> iterator = map.iterator();
    while (iterator.hasNext()) {
      Object key = ((Map.Entry<?, ?>) iterator.next()).getKey();
      if (!(key instanceof Keyword)) throw invalid(origin, subject + " keys must be keywords");
      String name = ((Keyword) key).getName();
      if (!allowed.contains(name))
        throw invalid(origin, "unsupported " + subject + " field: " + name);
      seen.add(name);
    }
    if (subject.equals("manifest") && !seen.containsAll(REQUIRED_FIELDS)) {
      LinkedHashSet<String> missing = new LinkedHashSet<>(REQUIRED_FIELDS);
      missing.removeAll(seen);
      throw invalid(origin, "missing manifest fields: " + missing);
    }
  }

  private static IllegalArgumentException invalid(String origin, String message) {
    return new IllegalArgumentException("extension/malformed " + origin + ": " + message);
  }

  private static IllegalArgumentException invalid(
      String origin, String message, RuntimeException cause) {
    return new IllegalArgumentException("extension/malformed " + origin + ": " + message, cause);
  }

  public static final class Export {
    private final java.util.List<String> arguments;
    private final String returns;
    private final boolean async;

    private Export(java.util.List<String> arguments, String returns, boolean async) {
      this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
      this.returns = returns;
      this.async = async;
    }

    public java.util.List<String> arguments() {
      return arguments;
    }

    public String returns() {
      return returns;
    }

    public boolean async() {
      return async;
    }
  }
}

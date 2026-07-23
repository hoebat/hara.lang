package hara.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import hara.kernel.builtin.BuiltinStruct;
import hara.kernel.flavor.NativeCapability;
import hara.kernel.flavor.NativeFlavorAccess;
import hara.kernel.flavor.NativeFlavorException;
import hara.kernel.flavor.NativeFlavorProvider;
import hara.kernel.flavor.NativeFlavorRegistry;
import hara.kernel.jvm.JvmFlavorProvider;
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
import hara.lang.protocol.IDeref;
import hara.lang.protocol.IDerefTimeout;
import hara.lang.protocol.IDisplay;
import hara.lang.protocol.ICount;
import hara.lang.protocol.INth;
import hara.lang.block.Parser;
import hara.lang.zip.Zip;
import hara.lang.zip.Zipper;
import hara.lang.test.HaraTestRegistry;
import hara.lang.test.HaraTestResult;
import hara.lang.test.HaraMatcher;
import hara.lang.task.Task;
import hara.lang.task.TaskFunction;
import hara.lang.task.TaskProcess;
import hara.lang.task.TaskBulk;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public final class HaraContext {
  private static final String INTRINSIC_NAMESPACE = "hara.lang.intrinsic";
  private static final String CORE_NAMESPACE = "hara.lib.core";
  private static final Map<String, String> GENERATED_LIBRARIES =
      Map.of(
          "string", "hara.lib.string",
          "promise", "hara.lib.promise",
          "bytes", "hara.lib.bytes",
          "socket", "hara.lib.socket",
          "file", "hara.lib.file",
          "block", "std.block",
          "zip", "std.lib.zip");
  private static final Map<String, String> PORTABLE_LIBRARY_RESOURCES =
      Map.of("std.block", "std/block.hal", "std.lib.zip", "std/lib/zip.hal");
  private static final Map<String, String> DEFAULT_LIBRARY_ALIASES =
      Map.of(
          "string", "str",
          "promise", "promise",
          "bytes", "bytes",
          "socket", "socket",
          "file", "file");
  private static final Set<String> MARKER_METHOD_NAMES =
      Set.of(
          "get",
          "set",
          "push-first",
          "push-last",
          "pop-first",
          "pop-last",
          "insert",
          "remove",
          "clone",
          "slice",
          "map",
          "filter",
          "fold-left",
          "fold-right",
          "has?",
          "delete",
          "assign",
          "keys",
          "vals",
          "pairs");
  private final TruffleLanguage.Env environment;
  private final Map<String, HaraNamespace> namespaces = new ConcurrentHashMap<>();
  private final Map<String, Map<String, HaraMacro>> macros = new ConcurrentHashMap<>();
  private final Map<String, Map<String, String>> aliases = new ConcurrentHashMap<>();
  private final Map<String, String> nativeFlavors = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> nativeImports = new ConcurrentHashMap<>();
  private final NativeFlavorRegistry nativeFlavorRegistry =
      new NativeFlavorRegistry().register(JvmFlavorProvider.INSTANCE);
  private final HaraExtensionRegistry extensionRegistry =
      new HaraExtensionRegistry(HaraContext.class.getClassLoader());
  private final HaraLibraryLoader libraryLoader = new HaraLibraryLoader();
  private final Map<String, HaraWasmExtension> loadedExtensions = new ConcurrentHashMap<>();
  private final Map<String, ModuleRecord> modules = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> moduleDependencies = new ConcurrentHashMap<>();
  private final Set<String> loadingModules = ConcurrentHashMap.newKeySet();
  private final Deque<String> loadingStack = new ArrayDeque<>();
  private volatile HaraNamespace currentNamespace;
  private final HaraProtocol ifnProtocol;
  private final HaraTestRegistry testRegistry = new HaraTestRegistry();

  HaraContext(TruffleLanguage.Env environment) {
    this.environment = environment;
    currentNamespace = namespace(INTRINSIC_NAMESPACE);
    Map<String, Integer> ifnMethods = new LinkedHashMap<>();
    ifnMethods.put("invoke", -1);
    ifnProtocol = new HaraProtocol("IFn", ifnMethods);
    currentNamespace.define("IFn", ifnProtocol);
    HaraJavaAdapters.install(this);
    installNumericBuiltins(currentNamespace);
    installCoreBuiltins(namespace(CORE_NAMESPACE));
    installGeneratedLibraries();
    installNativeLibraries();
    currentNamespace = namespace("user");
    initializeUserNamespace(currentNamespace);
  }

  TruffleLanguage.Env environment() {
    return environment;
  }

  void closeExtensions() {
    for (HaraWasmExtension extension : loadedExtensions.values()) {
      extension.close();
    }
    loadedExtensions.clear();
  }

  private HaraNamespace namespace(String name) {
    return namespaces.computeIfAbsent(name, HaraNamespace::new);
  }

  @TruffleBoundary
  public void setCurrentNamespace(Symbol symbol) {
    if (symbol.getNamespace() != null) {
      throw new HaraException("Namespace name must not be qualified");
    }
    currentNamespace = namespace(symbol.getName());
    initializeUserNamespace(currentNamespace);
  }

  @TruffleBoundary
  public void setCurrentNamespace(Symbol symbol, Object[] clauses) {
    setCurrentNamespace(symbol);
    configureNativeFlavor(clauses);
    configureGeneratedAliases(clauses);
  }

  private void configureNativeFlavor(Object[] clauses) {
    List<?> flavorClause = null;
    for (Object clauseValue : clauses) {
      if (!(clauseValue instanceof List<?>)) continue;
      List<?> clause = (List<?>) clauseValue;
      if (clause.count() > 0
          && clause.nth(0) instanceof Keyword
          && "flavor".equals(((Keyword) clause.nth(0)).getName())) {
        if (flavorClause != null) throw new HaraException("ns accepts only one :flavor clause");
        flavorClause = clause;
      }
    }
    if (flavorClause != null) {
      if (flavorClause.count() != 2 || !(flavorClause.nth(1) instanceof Keyword)) {
        throw new HaraException(":flavor expects one keyword");
      }
      Keyword flavor = (Keyword) flavorClause.nth(1);
      if (flavor.getNamespace() != null) {
        throw new HaraException(":flavor expects an unqualified keyword");
      }
      nativeFlavorRegistry.require(flavor.getName());
      nativeFlavors.put(currentNamespace.name(), flavor.getName());
    }

    for (Object clauseValue : clauses) {
      if (!(clauseValue instanceof List<?>)) continue;
      List<?> clause = (List<?>) clauseValue;
      if (clause.count() == 0
          || !(clause.nth(0) instanceof Keyword)
          || !"import".equals(((Keyword) clause.nth(0)).getName())) continue;
      NativeFlavorProvider provider = nativeProvider();
      if (provider == null) throw new HaraException(":import requires an ns :flavor declaration");
      Map<String, Object> imports =
          nativeImports.computeIfAbsent(
              currentNamespace.name(), ignored -> new ConcurrentHashMap<>());
      for (int i = 1; i < clause.count(); i++) {
        Object spec = clause.nth(i);
        if (spec instanceof Symbol) {
          importNativeType(provider, imports, ((Symbol) spec).display());
        } else if (spec instanceof ILinearType<?>) {
          ILinearType<?> group = (ILinearType<?>) spec;
          if (group.count() == 0) continue;
          Object packageValue = group.nth(0);
          String packageName =
              packageValue instanceof Symbol
                  ? ((Symbol) packageValue).display()
                  : String.valueOf(packageValue);
          for (int j = 1; j < group.count(); j++) {
            Object classValue = group.nth(j);
            String className =
                classValue instanceof Symbol
                    ? ((Symbol) classValue).display()
                    : String.valueOf(classValue);
            importNativeType(provider, imports, packageName + "." + className);
          }
        } else {
          throw new HaraException(":import expects class symbols or package vectors");
        }
      }
    }
  }

  private void importNativeType(
      NativeFlavorProvider provider, Map<String, Object> imports, String name) {
    Object type = provider.resolveType(name, nativeAccess());
    String simpleName = name.substring(name.lastIndexOf('.') + 1);
    Object previous = imports.putIfAbsent(simpleName, type);
    if (previous != null && previous != type) {
      throw new HaraException("Native import already exists: " + simpleName);
    }
  }

  private void initializeUserNamespace(HaraNamespace target) {
    HaraNamespace intrinsic = namespace(INTRINSIC_NAMESPACE);
    for (Map.Entry<String, HaraVar> entry : intrinsic.vars.entrySet()) {
      if (target.lookup(entry.getKey()) == null) target.refer(entry.getKey(), entry.getValue());
    }
    HaraNamespace core = namespace(CORE_NAMESPACE);
    for (Map.Entry<String, HaraVar> entry : core.vars.entrySet()) {
      if (target.lookup(entry.getKey()) == null) target.refer(entry.getKey(), entry.getValue());
    }
    Map<String, String> namespaceAliases =
        aliases.computeIfAbsent(target.name(), ignored -> new ConcurrentHashMap<>());
    for (Map.Entry<String, String> entry : DEFAULT_LIBRARY_ALIASES.entrySet()) {
      namespaceAliases.putIfAbsent(entry.getValue(), GENERATED_LIBRARIES.get(entry.getKey()));
    }
  }

  @SuppressWarnings("rawtypes")
  private void configureGeneratedAliases(Object[] clauses) {
    Set<String> excluded = new LinkedHashSet<>();
    Map<String, String> overrides = new LinkedHashMap<>();
    ArrayList<Object> requireSpecs = new ArrayList<>();
    boolean intrinsicsSeen = false;
    for (Object clauseValue : clauses) {
      if (!(clauseValue instanceof List<?>) || ((List<?>) clauseValue).count() == 0) {
        throw new HaraException("ns clauses must be non-empty lists");
      }
      List<?> clause = (List<?>) clauseValue;
      Object head = clause.nth(0);
      if (!(head instanceof Keyword))
        throw new HaraException("ns clause must start with a keyword");
      String clauseName = ((Keyword) head).getName();
      if ("intrinsics".equals(clauseName)) {
        if (intrinsicsSeen) throw new HaraException("ns accepts only one :intrinsics clause");
        intrinsicsSeen = true;
        if (clause.count() != 2) {
          throw new HaraException(":intrinsics expects :all or an options map");
        }
        Object intrinsicOptions = clause.nth(1);
        if (Keyword.create("all").equals(intrinsicOptions)) continue;
        if (!(intrinsicOptions instanceof IMapType<?, ?>)) {
          throw new HaraException(":intrinsics expects :all or an options map");
        }
        IMapType options = (IMapType) intrinsicOptions;
        Iterator<?> optionIterator = options.iterator();
        while (optionIterator.hasNext()) {
          java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) optionIterator.next();
          if (!(entry.getKey() instanceof Keyword)) {
            throw new HaraException(":intrinsics option keys must be keywords");
          }
          String optionName = ((Keyword) entry.getKey()).getName();
          if (!"exclude".equals(optionName) && !"aliases".equals(optionName)) {
            throw new HaraException("Unsupported :intrinsics option: :" + optionName);
          }
        }
        Object excludeValue = options.lookup(Keyword.create("exclude"));
        if (excludeValue != null) {
          if (!(excludeValue instanceof ILinearType<?>)) {
            throw new HaraException(":intrinsics :exclude expects a vector of library symbols");
          }
          for (Object value : (ILinearType<?>) excludeValue) {
            String library = libraryName(value, ":intrinsics :exclude");
            if (!excluded.add(library)) {
              throw new HaraException("Duplicate intrinsic exclusion: " + library);
            }
          }
        }
        Object aliasesValue = options.lookup(Keyword.create("aliases"));
        if (aliasesValue != null) {
          if (!(aliasesValue instanceof IMapType<?, ?>)) {
            throw new HaraException(":intrinsics :aliases expects a map");
          }
          Iterator<?> iterator = ((IMapType<?, ?>) aliasesValue).iterator();
          while (iterator.hasNext()) {
            java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) iterator.next();
            String library = libraryName(entry.getKey(), ":intrinsics :aliases");
            if (!(entry.getValue() instanceof Symbol)
                || ((Symbol) entry.getValue()).getNamespace() != null) {
              throw new HaraException("Intrinsic aliases must be unqualified symbols");
            }
            String previous = overrides.put(library, ((Symbol) entry.getValue()).getName());
            if (previous != null) throw new HaraException("Duplicate intrinsic alias: " + library);
          }
        }
      } else if ("require".equals(clauseName)) {
        for (int i = 1; i < clause.count(); i++) requireSpecs.add(clause.nth(i));
      } else if ("flavor".equals(clauseName) || "import".equals(clauseName)) {
        // Native clauses are handled separately from portable library aliases.
      } else {
        throw new HaraException("Unsupported ns clause: :" + clauseName);
      }
    }
    for (String library : overrides.keySet()) {
      if (excluded.contains(library)) {
        throw new HaraException(
            "Intrinsic library cannot be both excluded and aliased: " + library);
      }
    }

    Map<String, String> namespaceAliases =
        aliases.computeIfAbsent(currentNamespace.name(), ignored -> new ConcurrentHashMap<>());
    namespaceAliases
        .entrySet()
        .removeIf(entry -> GENERATED_LIBRARIES.containsValue(entry.getValue()));
    for (Map.Entry<String, String> library : GENERATED_LIBRARIES.entrySet()) {
      if (excluded.contains(library.getKey())) continue;
      String alias =
          overrides.getOrDefault(library.getKey(), DEFAULT_LIBRARY_ALIASES.get(library.getKey()));
      putAlias(namespaceAliases, alias, library.getValue());
    }
    for (Object spec : requireSpecs) applyGeneratedRequire(spec, namespaceAliases);
  }

  private String libraryName(Object value, String operation) {
    if (!(value instanceof Symbol) || ((Symbol) value).getNamespace() != null) {
      throw new HaraException(operation + " expects unqualified library symbols");
    }
    String library = ((Symbol) value).getName();
    if (!GENERATED_LIBRARIES.containsKey(library)) {
      throw new HaraException("Unknown intrinsic library: " + library);
    }
    return library;
  }

  private void putAlias(Map<String, String> namespaceAliases, String alias, String target) {
    String previous = namespaceAliases.putIfAbsent(alias, target);
    if (previous != null && !previous.equals(target)) {
      throw new HaraException("Namespace alias already refers to " + previous + ": " + alias);
    }
  }

  private void applyGeneratedRequire(Object specValue, Map<String, String> namespaceAliases) {
    if (!(specValue instanceof ILinearType<?>)) {
      throw new HaraException(":require expects vectors such as [std.lib.string :as str]");
    }
    ILinearType<?> spec = (ILinearType<?>) specValue;
    if (spec.count() == 0 || !(spec.nth(0) instanceof Symbol)) {
      throw new HaraException(":require namespace must be a symbol");
    }
    String target = ((Symbol) spec.nth(0)).display();
    HaraNamespace required = requiredNamespace(target);
    if (required == null) throw new HaraException("Cannot require missing namespace: " + target);
    java.util.Set<String> excludedRefers = new java.util.HashSet<>();
    for (int i = 1; i < spec.count(); i += 2) {
      if (i + 1 >= spec.count() || !(spec.nth(i) instanceof Keyword)) {
        throw new HaraException("Malformed :require options for " + target);
      }
      if ("exclude".equals(((Keyword) spec.nth(i)).getName())) {
        Object excluded = spec.nth(i + 1);
        if (!(excluded instanceof ILinearType<?>)) {
          throw new HaraException(":require :exclude expects a vector of symbols");
        }
        for (Object value : (ILinearType<?>) excluded) {
          if (!(value instanceof Symbol) || ((Symbol) value).getNamespace() != null) {
            throw new HaraException(":require :exclude expects unqualified symbols");
          }
          excludedRefers.add(((Symbol) value).getName());
        }
      }
    }
    for (int i = 1; i < spec.count(); i += 2) {
      if (i + 1 >= spec.count() || !(spec.nth(i) instanceof Keyword)) {
        throw new HaraException("Malformed :require options for " + target);
      }
      String option = ((Keyword) spec.nth(i)).getName();
      Object value = spec.nth(i + 1);
      if ("as".equals(option)) {
        if (!(value instanceof Symbol) || ((Symbol) value).getNamespace() != null) {
          throw new HaraException(":require :as expects an unqualified symbol");
        }
        putAlias(namespaceAliases, ((Symbol) value).getName(), target);
      } else if ("refer".equals(option)) {
        if (value instanceof Keyword && "all".equals(((Keyword) value).getName())) {
          for (String referred : required.symbolNames()) {
            if (!excludedRefers.contains(referred)) currentNamespace.refer(referred, required.lookup(referred));
          }
        } else {
          if (!(value instanceof ILinearType<?>)) {
            throw new HaraException(":require :refer expects a vector of symbols or :all");
          }
          for (Object referred : (ILinearType<?>) value) {
            if (!(referred instanceof Symbol) || ((Symbol) referred).getNamespace() != null) {
              throw new HaraException(":require :refer expects unqualified symbols");
            }
            String name = ((Symbol) referred).getName();
            if (excludedRefers.contains(name)) continue;
            HaraVar variable = required.lookup(name);
            if (variable == null) {
              throw new HaraException("Cannot refer missing var " + name + " from " + target);
            }
            currentNamespace.refer(name, variable);
          }
        }
      } else {
        throw new HaraException("Unsupported :require option: :" + option);
      }
    }
  }

  private synchronized HaraNamespace requiredNamespace(String target) {
    libraryLoader.ensure(this, target);
    HaraNamespace existing = namespaces.get(target);
    if (existing != null) return existing;
    if (PORTABLE_LIBRARY_RESOURCES.containsKey(target)) {
      if (supportsJavaLibrary(target)) return installJavaLibrary(target);
      loadResource(PORTABLE_LIBRARY_RESOURCES.get(target));
      return namespaces.get(target);
    }
    HaraExtensionPackage extensionPackage = extensionRegistry.discover(target);
    if (extensionPackage == null) return null;
    return installExtension(extensionPackage);
  }

  /**
   * Java implementations are an optional optimization; portable Hara resources are the fallback.
   */
  private boolean supportsJavaLibrary(String namespaceName) {
    return !Boolean.getBoolean("hara.disable.java." + namespaceName);
  }

  private HaraNamespace installJavaLibrary(String namespaceName) {
    HaraNamespace target = namespace(namespaceName);
    if ("std.block".equals(namespaceName)) {
      target.define(
          "parse",
          new UnaryBuiltin(
              "std.block/parse",
              value -> Parser.parseString(String.valueOf(HaraBox.unwrap(value)))));
      target.define(
          "parse-root",
          new UnaryBuiltin(
              "std.block/parse-root",
              value -> Parser.parseRoot(String.valueOf(HaraBox.unwrap(value)))));
    } else if ("std.lib.zip".equals(namespaceName)) {
      target.define(
          "zipper",
          new UnaryBuiltin("std.lib.zip/zipper", value -> Zip.zipper(HaraBox.unwrap(value))));
      target.define(
          "step-left",
          new UnaryBuiltin(
              "std.lib.zip/step-left", value -> Zip.stepLeft((Zipper) HaraBox.unwrap(value))));
      target.define(
          "step-right",
          new UnaryBuiltin(
              "std.lib.zip/step-right", value -> Zip.stepRight((Zipper) HaraBox.unwrap(value))));
      target.define(
          "step-inside",
          new UnaryBuiltin(
              "std.lib.zip/step-inside", value -> Zip.stepInside((Zipper) HaraBox.unwrap(value))));
      target.define(
          "step-outside",
          new UnaryBuiltin(
              "std.lib.zip/step-outside",
              value -> Zip.stepOutside((Zipper) HaraBox.unwrap(value))));
    }
    return target;
  }

  private HaraNamespace installExtension(HaraExtensionPackage extensionPackage) {
    HaraExtensionManifest manifest = extensionPackage.manifest();
    HaraWasmExtension extension = new HaraWasmExtension(extensionPackage);
    HaraNamespace generated = namespace(manifest.namespace());
    for (Map.Entry<String, HaraExtensionManifest.Export> export : manifest.exports().entrySet()) {
      String name = export.getKey();
      generated.define(
          name,
          new VariadicBuiltin(
              manifest.namespace() + "/" + name,
              values -> invokeExtension(extension, name, export.getValue(), values)));
    }
    loadedExtensions.put(manifest.namespace(), extension);
    return generated;
  }

  private Object invokeExtension(
      HaraWasmExtension extension,
      String name,
      HaraExtensionManifest.Export export,
      Object[] values) {
    if (extension.isHta()) return new HaraPromise(extension.invokeAsync(name, values));
    if (!export.async()) return extension.invoke(name, values);
    return new HaraPromise(CompletableFuture.supplyAsync(() -> extension.invoke(name, values)));
  }

  @TruffleBoundary
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

  /** Names visible in the current namespace, used by interactive tooling. */
  public java.util.List<String> currentSymbolNames() {
    LinkedHashSet<String> names = new LinkedHashSet<>(currentNamespace.symbolNames());
    names.addAll(MARKER_METHOD_NAMES);
    nativeImports
        .getOrDefault(currentNamespace.name(), Map.of())
        .forEach(
            (simpleName, type) -> {
              names.add(simpleName);
              if (!(type instanceof Class<?>)) return;
              Class<?> cls = (Class<?>) type;
              java.util.Arrays.stream(cls.getFields())
                  .filter(field -> java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                  .forEach(field -> names.add(simpleName + "/" + field.getName()));
              java.util.Arrays.stream(cls.getMethods())
                  .filter(method -> java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                  .forEach(method -> names.add(simpleName + "/" + method.getName()));
            });
    namespaces.forEach(
        (namespaceName, namespace) -> {
          if (!namespaceName.startsWith("hara.native.")) return;
          for (String name : namespace.symbolNames()) names.add(namespaceName + "/" + name);
        });
    for (Map.Entry<String, String> alias :
        aliases.getOrDefault(currentNamespace.name(), Map.of()).entrySet()) {
      HaraNamespace target = namespaces.get(alias.getValue());
      if (target == null) continue;
      for (String name : target.symbolNames()) names.add(alias.getKey() + "/" + name);
    }
    return new ArrayList<>(names);
  }

  @TruffleBoundary
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

  @TruffleBoundary
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

  @TruffleBoundary
  public HaraVar define(Symbol symbol, Object value) {
    if (symbol.getNamespace() != null && !symbol.getNamespace().equals(currentNamespace.name())) {
      throw new HaraException("Cannot define a var in another namespace: " + symbol.display());
    }
    IMetadata metadata = symbol.meta();
    if (metadata == null && value instanceof Task task) {
      java.util.ArrayList<Object> entries = new java.util.ArrayList<>();
      Object doc = task.config().get("doc");
      if (doc != null) { entries.add(Keyword.create("doc")); entries.add(doc); }
      if (task.arglists() != null) {
        entries.add(Keyword.create("arglists"));
        entries.add(task.arglists());
      }
      if (!entries.isEmpty()) metadata = hara.lang.data.Map.Standard.from(null, entries.toArray());
    }
    return currentNamespace.define(symbol.getName(), value, metadata);
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

  private NativeFlavorAccess nativeAccess() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) loader = HaraContext.class.getClassLoader();
    Set<NativeCapability> capabilities =
        hostInteropAllowed() ? Set.of(NativeCapability.REFLECTION) : Set.of();
    return NativeFlavorAccess.of(loader, capabilities);
  }

  private NativeFlavorProvider nativeProvider() {
    String flavor = nativeFlavors.get(currentNamespace.name());
    return flavor == null ? null : nativeFlavorRegistry.require(flavor);
  }

  private JvmFlavorProvider jvmProvider() {
    NativeFlavorProvider provider = nativeProvider();
    if (!(provider instanceof JvmFlavorProvider)) {
      throw new HaraException("JVM native operation requires an ns :flavor :jvm declaration");
    }
    return (JvmFlavorProvider) provider;
  }

  public boolean hasNativeSymbol(Symbol symbol) {
    Map<String, Object> imports = nativeImports.get(currentNamespace.name());
    if (imports == null) return false;
    String importedName = symbol.getNamespace() == null ? symbol.getName() : symbol.getNamespace();
    return imports.containsKey(importedName);
  }

  public Object resolveNativeSymbol(Symbol symbol) {
    Map<String, Object> imports = nativeImports.get(currentNamespace.name());
    if (imports == null) throw new HaraException("No native imports in the current namespace");
    String importedName = symbol.getNamespace() == null ? symbol.getName() : symbol.getNamespace();
    Object type = imports.get(importedName);
    if (type == null) throw new HaraException("Native type is not imported: " + importedName);
    if (symbol.getNamespace() == null) return type;
    NativeFlavorProvider provider = nativeProvider();
    try {
      return provider.readStatic(type, symbol.getName(), nativeAccess());
    } catch (NativeFlavorException error) {
      if (error.kind() != NativeFlavorException.Kind.UNSUPPORTED) throw error;
      return new VariadicBuiltin(
          symbol.display(),
          arguments -> provider.invokeStatic(type, symbol.getName(), arguments, nativeAccess()));
    }
  }

  public boolean matchesNativeThrowable(Symbol type, Throwable throwable) {
    if (!hasNativeSymbol(type)) return false;
    NativeFlavorProvider provider = nativeProvider();
    return provider != null
        && provider.matchesThrowable(resolveNativeSymbol(type), throwable, nativeAccess());
  }

  public Object constructNative(Object type, Object[] arguments) {
    NativeFlavorProvider provider = nativeProvider();
    if (provider == null) throw new HaraException("new requires an ns :flavor declaration");
    return provider.construct(HaraBox.unwrap(type), arguments, nativeAccess());
  }

  public Object readNativeMember(Object receiver, String member) {
    NativeFlavorProvider provider = nativeProvider();
    if (provider == null)
      throw new HaraException("Native member access requires an ns :flavor declaration");
    return provider.readMember(HaraBox.unwrap(receiver), member, nativeAccess());
  }

  public Object indexNative(Object receiver, Object index) {
    NativeFlavorProvider provider = nativeProvider();
    if (provider == null)
      throw new HaraException("Native indexed access requires an ns :flavor declaration");
    return provider.index(HaraBox.unwrap(receiver), HaraBox.unwrap(index), nativeAccess());
  }

  @TruffleBoundary
  HaraMacro resolveMacro(Symbol symbol) {
    String namespace = symbol.getNamespace();
    String namespaceName = namespace == null ? currentNamespace.name() : namespace;
    Map<String, HaraMacro> namespaceMacros = macros.get(namespaceName);
    HaraMacro macro = namespaceMacros == null ? null : namespaceMacros.get(symbol.getName());
    if (macro == null && namespace == null && "fact".equals(symbol.getName())) {
      libraryLoader.ensure(this, "code.test");
      namespaceMacros = macros.get(namespaceName);
      macro = namespaceMacros == null ? null : namespaceMacros.get(symbol.getName());
    }
    if (macro != null || INTRINSIC_NAMESPACE.equals(namespaceName)) return macro;
    Map<String, HaraMacro> intrinsicMacros = macros.get(INTRINSIC_NAMESPACE);
    return intrinsicMacros == null ? null : intrinsicMacros.get(symbol.getName());
  }

  void defineMacro(Symbol symbol, HaraMacro macro) {
    if (symbol.getNamespace() != null) {
      throw new HaraException("defmacro name must not be qualified");
    }
    macros
        .computeIfAbsent(currentNamespace.name(), ignored -> new ConcurrentHashMap<>())
        .put(symbol.getName(), macro);
  }

  private void defineIntrinsicMacro(Symbol symbol, HaraMacro macro) {
    HaraNamespace previous = currentNamespace;
    try {
      currentNamespace = namespace(INTRINSIC_NAMESPACE);
      defineMacro(symbol, macro);
    } finally {
      currentNamespace = previous;
    }
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
    target.define(
        "meta",
        new UnaryBuiltin(
            "meta",
            value -> {
              Object unwrapped = HaraBox.unwrap(value);
              if (unwrapped == null || unwrapped == HaraNull.SINGLETON) return null;
              if (unwrapped instanceof HaraVar) return ((HaraVar) unwrapped).meta();
              if (unwrapped instanceof hara.lang.protocol.IObjType) {
                return ((hara.lang.protocol.IObjType) unwrapped).meta();
              }
              return null;
            }));
    target.define("in-ns", new UnaryBuiltin("in-ns", this::inNamespace));
    target.define(
        "current-symbols",
        new VariadicBuiltin(
            "current-symbols",
            values -> {
              requireMethodArity("current-symbols", values, 0);
              return currentSymbolNames().toArray();
            }));
    target.define("use", new UnaryBuiltin("use", this::useNamespace));
    target.define("iter", new UnaryBuiltin("iter", this::iterValue));
    target.define("seq", new VariadicBuiltin("seq", this::seqValue));
    target.define("seq?", new UnaryBuiltin("seq?", this::isSeq));
    target.define("iter?", new UnaryBuiltin("iter?", this::isIterator));
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
    target.define("iter-interpose", new VariadicBuiltin("iter-interpose", this::iterInterpose));
    target.define("iter-interleave", new VariadicBuiltin("iter-interleave", this::iterInterleave));
    target.define("iter-every?", new VariadicBuiltin("iter-every?", this::iterEvery));
    target.define("iter-any?", new VariadicBuiltin("iter-any?", this::iterAny));
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
    target.define("conj", new VariadicBuiltin("conj", this::conjoin));
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

  void installTestLibrary() {
    HaraNamespace tests = namespace("code.test");
    tests.define(
        "register!",
        new VariadicBuiltin(
            "code.test/register!",
            values -> {
              if ((values.length != 2 && values.length != 3) || !(values[1] instanceof HaraFunction)) {
                throw new HaraException("code.test/register! expects a name, function, and optional metadata");
              }
              testRegistry.register(
                  currentNamespace.name(),
                  String.valueOf(values[0]),
                  values.length == 3 ? values[2] : null,
                  (HaraFunction) values[1]);
              return null;
            }));
    tests.define(
        "run!",
        new VariadicBuiltin(
            "code.test/run!",
            values -> {
              if (values.length > 1) throw new HaraException("code.test/run! accepts at most one options map");
              IMapType<?, ?> options = values.length == 1 && values[0] instanceof IMapType<?, ?>
                  ? (IMapType<?, ?>) values[0] : null;
              Object selector = options == null ? null : lookupValue(options, Keyword.create("filter"));
              Object namespaceSelector = options == null ? null : lookupValue(options, Keyword.create("namespace"));
              Object nameSelector = options == null ? null : lookupValue(options, Keyword.create("name"));
              Object metadataSelector = options == null ? null : lookupValue(options, Keyword.create("metadata"));
              Object onlySelector = options == null ? null : lookupValue(options, Keyword.create("only"));
              Object excludeSelector = options == null ? null : lookupValue(options, Keyword.create("exclude"));
              Object hidden = options == null ? null : lookupValue(options, Keyword.create("hidden"));
              Object clear = options == null ? null : lookupValue(options, Keyword.create("clear"));
              if (Boolean.TRUE.equals(clear)) testRegistry.clear();
              ArrayList<Object> results = new ArrayList<>();
              for (HaraTestResult result : testRegistry.runAll(test ->
                  testSelector(selector, test)
                      && testSelector(namespaceSelector, test.namespace())
                      && testSelector(nameSelector, test.name())
                      && testSelector(onlySelector, test)
                      && (excludeSelector == null || !testSelector(excludeSelector, test))
                      && metadataMatches(metadataSelector, test.metadata())
                      && (Boolean.TRUE.equals(hidden) || !metadataFlag(test.metadata(), "hidden")))) {
                java.util.Map<Object, Object> resultMap = new LinkedHashMap<>();
                resultMap.put("status", result.status().name());
                resultMap.put("name", result.test().name());
                resultMap.put("namespace", result.test().namespace());
                resultMap.put("elapsed", result.elapsedMillis());
                if (result.error() != null) resultMap.put("error", String.valueOf(result.error().getMessage()));
                if (result.test().metadata() != null) resultMap.put("metadata", result.test().metadata());
                results.add(
                    resultMap);
              }
              return results;
            }));
    tests.define("run", tests.lookup("run!").get());
    tests.define("run-tests", tests.lookup("run!").get());
    tests.define("tests", new VariadicBuiltin("code.test/tests", values -> {
      if (values.length != 0) throw new HaraException("code.test/tests expects no arguments");
      ArrayList<Object> entries = new ArrayList<>();
      for (hara.lang.test.HaraTestCase test : testRegistry.tests()) {
        java.util.Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("namespace", test.namespace());
        entry.put("name", test.name());
        entry.put("metadata", test.metadata());
        entries.add(entry);
      }
      return entries;
    }));
    tests.define("registry", new VariadicBuiltin("code.test/registry", values -> {
      if (values.length != 0) throw new HaraException("code.test/registry expects no arguments");
      java.util.Map<String, Object> result = new LinkedHashMap<>();
      for (java.util.Map.Entry<String, java.util.Map<String, hara.lang.test.HaraTestCase>> namespace
          : testRegistry.snapshot().entrySet()) {
        java.util.Map<String, Object> facts = new LinkedHashMap<>();
        for (java.util.Map.Entry<String, hara.lang.test.HaraTestCase> fact : namespace.getValue().entrySet()) {
          java.util.Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("name", fact.getValue().name());
          entry.put("metadata", fact.getValue().metadata());
          facts.put(fact.getKey(), entry);
        }
        result.put(namespace.getKey(), facts);
      }
      return result;
    }));
    tests.define("all-facts", new VariadicBuiltin("code.test/all-facts", values -> {
      String selectedNamespace = values.length == 0 ? null : testNamespace(values[0]);
      java.util.Map<String, Object> result = new LinkedHashMap<>();
      for (hara.lang.test.HaraTestCase test : testRegistry.tests()) {
        if (selectedNamespace != null && !selectedNamespace.equals(test.namespace())) continue;
        result.put(test.namespace() + "/" + test.name(), testRecord(test));
      }
      return result;
    }));
    tests.define("list-facts", new VariadicBuiltin("code.test/list-facts", values -> {
      String selectedNamespace = values.length == 0 ? currentNamespace.name() : testNamespace(values[0]);
      ArrayList<Object> result = new ArrayList<>();
      for (hara.lang.test.HaraTestCase test : testRegistry.tests()) {
        if (selectedNamespace.equals(test.namespace())) result.add(Symbol.create(test.name()));
      }
      return result;
    }));
    tests.define("get-fact", new VariadicBuiltin("code.test/get-fact", values -> {
      if (values.length == 0) throw new HaraException("get-fact expects a name");
      String namespace = values.length > 1 ? testNamespace(values[0]) : currentNamespace.name();
      Object name = values.length > 1 ? values[1] : values[0];
      hara.lang.test.HaraTestCase test = testRegistry.find(namespace, String.valueOf(name));
      return test == null ? null : testRecord(test);
    }));
    tests.define("remove-fact", new VariadicBuiltin("code.test/remove-fact", values -> {
      if (values.length == 0 || values.length > 2) throw new HaraException("remove-fact expects a name");
      String namespace = values.length == 2 ? testNamespace(values[0]) : currentNamespace.name();
      Object name = values.length == 2 ? values[1] : values[0];
      hara.lang.test.HaraTestCase test = testRegistry.find(namespace, String.valueOf(name));
      if (test != null) testRegistry.remove(namespace, String.valueOf(name));
      return test != null;
    }));
    tests.define("purge-facts", new VariadicBuiltin("code.test/purge-facts", values -> {
      testRegistry.clearNamespace(values.length == 0 ? currentNamespace.name() : testNamespace(values[0]));
      return null;
    }));
    tests.define("purge-all", new VariadicBuiltin("code.test/purge-all", values -> {
      if (values.length == 0) testRegistry.clear();
      else testRegistry.clearNamespace(testNamespace(values[0]));
      return null;
    }));
    tests.define("fact-id", new UnaryBuiltin("code.test/fact-id", value ->
        "test-" + String.valueOf(value).replace('.', '_').replace('/', '_').replace('-', '_')));
    tests.define("get-global", new VariadicBuiltin("code.test/get-global", values -> {
      boolean firstIsKey = values.length == 1 && values[0] instanceof Keyword;
      String namespace = values.length == 0 || firstIsKey ? currentNamespace.name() : testNamespace(values[0]);
      Object result = testRegistry.globals(namespace);
      int keyStart = firstIsKey ? 0 : (values.length == 0 ? 0 : 1);
      for (int i = keyStart; i < values.length; i++) {
        if (!(result instanceof java.util.Map<?, ?> map)) return null;
        result = map.get(keyName(values[i]));
      }
      return result;
    }));
    tests.define("set-global", new VariadicBuiltin("code.test/set-global", values -> {
      String namespace = values.length > 1 ? testNamespace(values[0]) : currentNamespace.name();
      Object value = values.length > 1 ? values[1] : values[0];
      return testRegistry.setGlobals(namespace, asStringMap(value));
    }));
    tests.define("set-flag", new VariadicBuiltin("code.test/set-flag", values -> {
      if (values.length == 3) {
        String first = testNamespace(values[0]);
        String flag = keyName(values[1]);
        boolean value = Boolean.TRUE.equals(values[2]);
        // Preserve the public Hara namespace form, while accepting the source
        // runtime's (fact-id flag value) form when the fact exists locally.
        if (testRegistry.find(currentNamespace.name(), first) != null) {
          return testRegistry.setFactFlag(currentNamespace.name(), first, flag, value);
        }
        return testRegistry.setFlag(first, flag, value);
      }
      if (values.length == 4) {
        return testRegistry.setFactFlag(
            testNamespace(values[0]), testNamespace(values[1]), keyName(values[2]),
            Boolean.TRUE.equals(values[3]));
      }
      throw new HaraException("set-flag expects fact, flag, value or namespace, fact, flag, value");
    }));
    tests.define("get-flag", new VariadicBuiltin("code.test/get-flag", values -> {
      if (values.length == 2) {
        String first = testNamespace(values[0]);
        String flag = keyName(values[1]);
        if (testRegistry.find(currentNamespace.name(), first) != null) {
          return testRegistry.factFlag(currentNamespace.name(), first, flag);
        }
        return testRegistry.flag(first, flag);
      }
      if (values.length == 3) {
        return testRegistry.factFlag(
            testNamespace(values[0]), testNamespace(values[1]), keyName(values[2]));
      }
      throw new HaraException("get-flag expects fact, flag or namespace, fact, flag");
    }));
    tests.define("setup-fact", new UnaryBuiltin("code.test/setup-fact", value -> {
      hara.lang.test.HaraTestCase test = testRegistry.find(currentNamespace.name(), String.valueOf(value));
      if (test != null) testRegistry.runFixtures(test.namespace(), "before");
      return test == null ? null : testRecord(test);
    }));
    tests.define("teardown-fact", new UnaryBuiltin("code.test/teardown-fact", value -> {
      hara.lang.test.HaraTestCase test = testRegistry.find(currentNamespace.name(), String.valueOf(value));
      if (test != null) testRegistry.runFixtures(test.namespace(), "after");
      return test == null ? null : testRecord(test);
    }));
    tests.define("fact:list", tests.lookup("tests").get());
    tests.define("fact:get", tests.lookup("get-fact").get());
    tests.define("fact:remove", tests.lookup("remove-fact").get());
    tests.define("fact:purge", tests.lookup("purge-facts").get());
    tests.define("fact:setup", tests.lookup("setup-fact").get());
    tests.define("fact:teardown", tests.lookup("teardown-fact").get());
    tests.define("fact:setup?", tests.lookup("get-flag").get());
    tests.define("fact:all", tests.lookup("run!").get());
    tests.define("fact:global", tests.lookup("get-global").get());
    tests.define("summarise", new UnaryBuiltin("code.test/summarise", value -> summariseTestResults(value)));
    tests.define("fact:ns", new VariadicBuiltin("code.test/fact:ns", values -> values));
    tests.define("fact:ns-load", new UnaryBuiltin("code.test/fact:ns-load", value -> value));
    tests.define("fact:symbol", new UnaryBuiltin("code.test/fact:symbol", value -> value));
    tests.define("fact:missing", new VariadicBuiltin("code.test/fact:missing", values -> hara.lang.data.Vector.Standard.EMPTY));
    tests.define("fact:exec", new VariadicBuiltin("code.test/fact:exec", values -> {
      if (values.length == 0) return null;
      hara.lang.test.HaraTestCase test = testRegistry.find(currentNamespace.name(), String.valueOf(values[0]));
      if (test == null) return null;
      return test.body().callTarget().call(test.body().callArguments(new Object[0]));
    }));
    tests.define("capture", new VariadicBuiltin("code.test/capture", values ->
        values.length == 0 ? HaraMatcher.anything() : values[0]));
    tests.define("with-new-context", new VariadicBuiltin("code.test/with-new-context", values -> {
      if (values.length == 0) return hara.lang.data.Map.Standard.EMPTY;
      Object body = values[values.length - 1];
      return body instanceof HaraFunction ? invokeCallable(body, new Object[0]) : body;
    }));
    tests.define("run:interrupt", tests.lookup("run!").get());
    tests.define("run:current", tests.lookup("run!").get());
    tests.define("run:load", tests.lookup("run!").get());
    tests.define("run:unload", tests.lookup("run!").get());
    tests.define("run:test", tests.lookup("run!").get());
    tests.define("run-errored", tests.lookup("run!").get());
    tests.define("print-options", new VariadicBuiltin("code.test/print-options", values ->
        hara.lang.data.Set.Standard.from(null, Keyword.create("help"), Keyword.create("current"), Keyword.create("default"), Keyword.create("disable"), Keyword.create("all"))));
    tests.define("-main", tests.lookup("run!").get());
    tests.define("=>", Symbol.create("=>"));

    tests.define(
        "assert!",
        new VariadicBuiltin(
            "code.test/assert!",
            values -> {
              if (values.length != 2) throw new HaraException("code.test/assert! expects actual and expected");
              Object actual = HaraBox.unwrap(values[0]);
              Object expected = HaraBox.unwrap(values[1]);
              boolean matched;
              if (expected instanceof HaraMatcher) {
                matched = ((HaraMatcher) expected).matches(actual);
              } else if (expected instanceof HaraFunction function) {
                matched = Boolean.TRUE.equals(function.callTarget().call(function.callArguments(new Object[] {actual})));
              } else if (expected instanceof Class<?> type) {
                matched = actual != null && type.isInstance(actual);
              } else {
                matched = Eq.eq(actual, expected);
              }
              if (!matched) {
                throw new HaraException("Assertion failed: expected " + values[1] + ", received " + values[0]);
              }
              return true;
            }));
    tests.define("assert-throws!", new VariadicBuiltin("code.test/assert-throws!", values -> {
      if (values.length != 2 || !(values[0] instanceof HaraFunction)) {
        throw new HaraException("code.test/assert-throws! expects a function and matcher");
      }
      try {
        HaraFunction function = (HaraFunction) values[0];
        function.callTarget().call(function.callArguments(new Object[0]));
      } catch (Throwable error) {
        Object matcher = HaraBox.unwrap(values[1]);
        if (matcher instanceof HaraMatcher && ((HaraMatcher) matcher).matches(error)) return true;
        if (matcher instanceof HaraFunction predicate
            && Boolean.TRUE.equals(predicate.callTarget().call(predicate.callArguments(new Object[] {error})))) {
          return true;
        }
        if (matcher instanceof Class<?> type && type.isInstance(error)) return true;
        if (matcher instanceof HaraMatcher && ((HaraMatcher) matcher).kind() == HaraMatcher.Kind.THROWS) return true;
        throw new HaraException("Unexpected exception: " + error.getMessage());
      }
      throw new HaraException("Expected expression to throw");
    }));
    tests.define("checker?", new UnaryBuiltin("code.test/checker?", value -> value instanceof HaraMatcher));
    tests.define("->checker", new UnaryBuiltin("code.test/->checker", value ->
        value instanceof HaraMatcher ? value : HaraMatcher.satisfies(value)));
    tests.define("verify", new VariadicBuiltin("code.test/verify", values -> {
      if (values.length != 2 || !(values[0] instanceof HaraMatcher)) {
        throw new HaraException("verify expects a checker and value");
      }
      HaraMatcher checker = (HaraMatcher) values[0];
      boolean matched;
      Throwable error = null;
      try {
        matched = checker.matches(HaraBox.unwrap(values[1]));
      } catch (Throwable throwable) {
        matched = false;
        error = throwable;
      }
      java.util.Map<String, Object> result = new LinkedHashMap<>();
      result.put("status", error == null ? Keyword.create("success") : Keyword.create("exception"));
      result.put("data", error == null ? matched : error);
      result.put("checker", checker);
      result.put("actual", values[1]);
      return result;
    }));
    tests.define("succeeded?", new UnaryBuiltin("code.test/succeeded?", value -> {
      Object status;
      Object data;
      if (value instanceof IMapType<?, ?> map) {
        status = lookupValue(map, Keyword.create("status"));
        data = lookupValue(map, Keyword.create("data"));
      } else if (value instanceof java.util.Map<?, ?> map) {
        status = map.get("status");
        data = map.get("data");
      } else return false;
      return (status instanceof Keyword && "success".equals(((Keyword) status).getName())
          || "success".equals(String.valueOf(status))) && Boolean.TRUE.equals(data);
    }));
    tests.define("anything", HaraMatcher.anything());
    tests.define("contains", new VariadicBuiltin("code.test/contains", values -> {
      if (values.length < 1) throw new HaraException("contains expects a value");
      return HaraMatcher.contains(values[0], matcherOption(values, "in-any-order"), matcherOption(values, "gaps-ok"));
    }));
    tests.define("contains-in", new VariadicBuiltin("code.test/contains-in", values -> {
      if (values.length < 1) throw new HaraException("contains-in expects a value");
      return HaraMatcher.containsIn(values[0], matcherOption(values, "in-any-order"), matcherOption(values, "gaps-ok"));
    }));
    tests.define("just", new VariadicBuiltin("code.test/just", values -> {
      if (values.length < 1) throw new HaraException("just expects a value");
      return HaraMatcher.just(values[0], matcherOption(values, "in-any-order"), matcherOption(values, "gaps-ok"));
    }));
    tests.define("just-in", new UnaryBuiltin("code.test/just-in", HaraMatcher::justIn));
    tests.define("exactly", new VariadicBuiltin("code.test/exactly", values -> {
      if (values.length < 1 || values.length > 2) throw new HaraException("exactly expects a value and optional projection");
      return HaraMatcher.exactly(values[0]);
    }));
    tests.define("approx", new VariadicBuiltin("code.test/approx", values -> {
      if (values.length < 1 || values.length > 2 || !(values[0] instanceof Number)) {
        throw new HaraException("approx expects a numeric value and optional threshold");
      }
      double threshold = values.length == 2 ? ((Number) values[1]).doubleValue() : 0.001d;
      return HaraMatcher.approximate(values[0], threshold);
    }));
    tests.define("satisfies", new UnaryBuiltin("code.test/satisfies", HaraMatcher::satisfies));
    tests.define("stores", new UnaryBuiltin("code.test/stores", HaraMatcher::stores));
    tests.define("any", new VariadicBuiltin("code.test/any", HaraMatcher::any));
    tests.define("all", new VariadicBuiltin("code.test/all", HaraMatcher::all));
    tests.define("is-not", new UnaryBuiltin("code.test/is-not", HaraMatcher::isNot));
    tests.define("is", new UnaryBuiltin("code.test/is", HaraMatcher::predicate));
    tests.define("nil?", new UnaryBuiltin("code.test/nil?", value -> value == null));
    tests.define("string?", new UnaryBuiltin("code.test/string?", value -> value instanceof String));
    tests.define("number?", new UnaryBuiltin("code.test/number?", value -> value instanceof Number));
    tests.define("integer?", new UnaryBuiltin("code.test/integer?", value -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long));
    tests.define("boolean?", new UnaryBuiltin("code.test/boolean?", value -> value instanceof Boolean));
    tests.define("map?", new UnaryBuiltin("code.test/map?", value -> value instanceof IMapType<?, ?> || value instanceof java.util.Map<?, ?>));
    tests.define("vector?", new UnaryBuiltin("code.test/vector?", value -> value instanceof hara.lang.data.Vector<?>));
    tests.define("seq?", new UnaryBuiltin("code.test/seq?", value -> value instanceof ILinearType<?>));
    tests.define("coll?", new UnaryBuiltin("code.test/coll?", value -> value instanceof ILinearType<?> || value instanceof IMapType<?, ?> || value instanceof Iterable<?>));
    tests.define("fn?", new UnaryBuiltin("code.test/fn?", value -> value instanceof HaraFunction));
    tests.define("var?", new UnaryBuiltin("code.test/var?", value -> value instanceof HaraVar));
    tests.define("any?", new UnaryBuiltin("code.test/any?", value -> {
      if (value instanceof Iterable<?> iterable) return iterable.iterator().hasNext();
      return false;
    }));
    tests.define("empty?", new UnaryBuiltin("code.test/empty?", value -> {
      if (value == null) return true;
      if (value instanceof ICount count) return count.count() == 0;
      if (value instanceof Iterable<?> iterable) return !iterable.iterator().hasNext();
      return false;
    }));
    tests.define("throws", new VariadicBuiltin("code.test/throws", values -> {
      if (values.length > 1) throw new HaraException("throws expects an optional predicate");
      return HaraMatcher.throwsMatcher(values.length == 0 ? null : values[0]);
    }));
    tests.define("throws-info", new VariadicBuiltin("code.test/throws-info", values -> {
      if (values.length > 1) throw new HaraException("throws-info expects an optional map");
      return HaraMatcher.throwsInfo(values.length == 0 ? null : values[0]);
    }));
    tests.define("use-fixtures", new VariadicBuiltin("code.test/use-fixtures", values -> {
      if (values.length != 2 || !(values[0] instanceof Keyword) || !(values[1] instanceof HaraFunction)) {
        throw new HaraException("code.test/use-fixtures expects a phase keyword and function");
      }
      String phase = ((Keyword) values[0]).getName();
      if (!phase.equals("before") && !phase.equals("after")
          && !phase.equals("before-all") && !phase.equals("after-all")) {
        throw new HaraException(
            "code.test/use-fixtures phase must be :before, :after, :before-all, or :after-all");
      }
      testRegistry.registerFixture(currentNamespace.name(), phase, (HaraFunction) values[1]);
      return null;
    }));
    tests.define("clear!", new VariadicBuiltin("code.test/clear!", values -> {
      if (values.length == 0) testRegistry.clear();
      else if (values.length == 1) {
        Object namespace = values[0] instanceof Symbol ? ((Symbol) values[0]).display() : values[0];
        testRegistry.clearNamespace(String.valueOf(namespace));
      } else throw new HaraException("code.test/clear! expects no arguments or a namespace");
      return null;
    }));
    HaraMacro factMacro = HaraMacro.nativeMacro(Symbol.create("fact"), this::expandFact);
    defineIntrinsicMacro(Symbol.create("fact"), factMacro);
    tests.define("fact", factMacro);
    tests.define("fact:template", factMacro);

    HaraNamespace runtime = namespace("code.test.base.runtime");
    for (String name : new String[] {
      "all-facts", "list-facts", "get-fact", "remove-fact", "purge-facts", "purge-all",
      "get-global", "set-global", "get-flag", "set-flag", "setup-fact", "teardown-fact",
      "fact-id", "summarise"
    }) runtime.define(name, tests.lookup(name).get());
    HaraNamespace commonChecker = namespace("code.test.checker.common");
    for (String name : new String[] {"anything", "exactly", "approx", "satisfies", "stores", "throws", "throws-info", "checker?", "verify", "succeeded?", "->checker"}) {
      commonChecker.define(name, tests.lookup(name).get());
    }
    HaraNamespace collectionChecker = namespace("code.test.checker.collection");
    for (String name : new String[] {"contains", "contains-in", "just", "just-in"}) {
      collectionChecker.define(name, tests.lookup(name).get());
    }
    HaraNamespace logicChecker = namespace("code.test.checker.logic");
    for (String name : new String[] {"any", "all", "is-not"}) logicChecker.define(name, tests.lookup(name).get());
    HaraNamespace executive = namespace("code.test.base.executive");
    executive.define("summarise", tests.lookup("summarise").get());
    executive.define("run-namespace", tests.lookup("run!").get());
    executive.define("run-current", tests.lookup("run!").get());
    executive.define("test-namespace", tests.lookup("run!").get());
    HaraNamespace compile = namespace("code.test.compile");
    for (String name : new String[] {
      "fact", "fact:all", "fact:purge", "fact:list", "fact:symbol", "fact:get",
      "fact:missing", "fact:exec", "fact:setup", "fact:setup?", "fact:teardown",
      "fact:remove", "fact:template", "=>"
    }) compile.define(name, tests.lookup(name).get());
    HaraNamespace context = namespace("code.test.base.context");
    context.define("new-context", new VariadicBuiltin("code.test.base.context/new-context", values ->
        hara.lang.data.Map.Standard.EMPTY));
    HaraNamespace match = namespace("code.test.base.match");
    match.define("match-include", new VariadicBuiltin("code.test.base.match/match-include", values -> values.length > 1));
    match.define("match-exclude", new VariadicBuiltin("code.test.base.match/match-exclude", values -> false));
    match.define("match-options", new VariadicBuiltin("code.test.base.match/match-options", values -> true));
    HaraNamespace codeTestTask = namespace("code.test.task");
    codeTestTask.define("run", tests.lookup("run!").get());
    codeTestTask.define("run:test", tests.lookup("run!").get());
    codeTestTask.define("run:current", tests.lookup("run!").get());
  }

  void installTaskLibrary() {
    HaraNamespace taskNamespace = namespace("std.task");
    taskNamespace.define("task", new VariadicBuiltin("std.task/task", this::createTask));
    taskNamespace.define("map->Task", new UnaryBuiltin("std.task/map->Task", value -> {
      if (!(value instanceof IMapType<?, ?>)) throw new HaraException("map->Task expects a map");
      return createTask(new Object[] {value});
    }));
    taskNamespace.define("task?", new UnaryBuiltin("std.task/task?", value -> value instanceof Task));
    taskNamespace.define("task-status", new UnaryBuiltin("std.task/task-status", value -> {
      if (!(value instanceof Task task)) throw new HaraException("task-status expects a task");
      return Keyword.create(task.type());
    }));
    taskNamespace.define("task-info", new UnaryBuiltin("std.task/task-info", value -> {
      if (!(value instanceof Task task)) throw new HaraException("task-info expects a task");
      return hara.lang.data.Map.Standard.from(null, Keyword.create("fn"), Symbol.create(task.name()));
    }));
    taskNamespace.define("task-defaults", new UnaryBuiltin("std.task/task-defaults", value ->
        hara.lang.data.Map.Standard.from(null,
            Keyword.create("main"),
            hara.lang.data.Map.Standard.from(null,
                Keyword.create("arglists"),
                hara.lang.data.List.Standard.from(null,
                    hara.lang.data.Vector.Standard.EMPTY,
                hara.lang.data.Vector.Standard.from(null, Symbol.create("entry")))))));
    taskNamespace.define("single-function-print", new UnaryBuiltin("std.task/single-function-print", this::singleFunctionPrint));
    taskNamespace.define("process-ns-args", new VariadicBuiltin("std.task/process-ns-args", values -> {
      ArrayList<String> arguments = new ArrayList<>();
      if (values.length == 1 && values[0] instanceof Iterable<?> iterable) {
        for (Object value : iterable) arguments.add(String.valueOf(value));
      } else {
        for (Object value : values) arguments.add(String.valueOf(value));
      }
      String[] args = arguments.toArray(String[]::new);
      return TaskProcess.processNamespaceArgs(args);
    }));
    taskNamespace.define("invoke-intern-task", new VariadicBuiltin("std.task/invoke-intern-task", values -> {
      if (values.length < 2 || !(values[0] instanceof Symbol)) {
        throw new HaraException("invoke-intern-task expects a name and configuration");
      }
      Object config = values[1];
      Object type = config instanceof IMapType<?, ?> map ? lookupValue(map, Keyword.create("template")) : Keyword.create("default");
      Symbol definedName = (Symbol) values[0];
      if (config instanceof IMapType<?, ?> map) {
        Object doc = lookupValue(map, Keyword.create("doc"));
        Object arglists = lookupValue(map, Keyword.create("arglists"));
        if (arglists == null) {
          arglists = hara.lang.data.List.Standard.from(null,
              hara.lang.data.Vector.Standard.EMPTY,
              hara.lang.data.Vector.Standard.from(null, Symbol.create("entry")));
        }
        ArrayList<Object> metadata = new ArrayList<>();
        if (doc != null) { metadata.add(Keyword.create("doc")); metadata.add(doc); }
        if (arglists != null) { metadata.add(Keyword.create("arglists")); metadata.add(arglists); }
        if (!metadata.isEmpty()) {
          definedName = definedName.withMeta(hara.lang.data.Map.Standard.from(null, metadata.toArray()));
        }
      }
      return List.Standard.from(null, Symbol.create("def"), definedName,
          List.Standard.from(null, Symbol.create("std.task", "task"), type, ((Symbol) values[0]).getName(), config));
    }));
    taskNamespace.define("invoke", new VariadicBuiltin("std.task/invoke", values -> {
      if (values.length < 1 || !(values[0] instanceof Task task)) {
        throw new HaraException("std.task/invoke expects a task");
      }
      try {
        return TaskProcess.invoke(task, java.util.Arrays.copyOfRange(values, 1, values.length));
      } catch (Exception error) {
        throw new HaraException("Task invocation failed: " + error.getMessage());
      }
    }));
    HaraMacro deftaskMacro = HaraMacro.nativeMacro(Symbol.create("deftask"), this::expandDeftask);
    defineIntrinsicMacro(Symbol.create("deftask"), deftaskMacro);
    taskNamespace.define("deftask", deftaskMacro);

    HaraNamespace process = namespace("std.task.process");
    process.define("select-filter", new VariadicBuiltin("std.task.process/select-filter", values -> {
      if (values.length != 2) throw new HaraException("select-filter expects selector and id");
      try { return TaskProcess.selectFilter(values[0], values[1]); }
      catch (RuntimeException error) { throw new HaraException(error.getMessage()); }
    }));
    process.define("select-inputs", new VariadicBuiltin("std.task.process/select-inputs", values -> {
      if (values.length != 4 || !(values[0] instanceof Task)) {
        throw new HaraException("select-inputs expects task, lookup, environment, and selector");
      }
      try {
        return TaskProcess.selectInputs((Task) values[0], values[1], values[2], values[3]);
      } catch (Exception error) {
        throw new HaraException("Unable to select task inputs: " + error.getMessage());
      }
    }));
    process.define("invoke", taskNamespace.lookup("invoke").get());
    process.define("task-inputs", new VariadicBuiltin("std.task.process/task-inputs", values -> {
      if (values.length < 1 || !(values[0] instanceof Task)) throw new HaraException("task-inputs expects a task");
      try {
        return TaskProcess.taskInputs((Task) values[0], java.util.Arrays.copyOfRange(values, 1, values.length));
      } catch (Exception error) {
        throw new HaraException("Unable to construct task inputs: " + error.getMessage());
      }
    }));
    process.define("main-function", new VariadicBuiltin("std.task.process/main-function", values -> {
      if (values.length != 2 || (!(values[0] instanceof TaskFunction) && !(values[0] instanceof HaraFunction))
          || !(values[1] instanceof Number)) {
        throw new HaraException("main-function expects a function and count");
      }
      TaskFunction function = values[0] instanceof TaskFunction
          ? (TaskFunction) values[0] : toTaskFunction(values[0]);
      return TaskProcess.mainFunction(function, ((Number) values[1]).intValue());
    }));
    process.define("wrap-execute", new VariadicBuiltin("std.task.process/wrap-execute", values -> {
      if (values.length != 2 || !(values[1] instanceof Task)) {
        throw new HaraException("wrap-execute expects a function and task");
      }
      return wrapExecuteFunction(values[0], (Task) values[1]);
    }));
    process.define("wrap-input", new VariadicBuiltin("std.task.process/wrap-input", values -> {
      if (values.length != 2 || !(values[1] instanceof Task)) {
        throw new HaraException("wrap-input expects a function and task");
      }
      Object execute = wrapExecuteFunction(values[0], (Task) values[1]);
      return new VariadicBuiltin("std.task.process/wrap-input", inputValues -> {
        if (inputValues.length < 4) throw new HaraException("wrapped task expects input, params, lookup, and env");
        Object input = inputValues[0];
        if (input instanceof Keyword && "list".equals(((Keyword) input).getName())) {
          Object listFunction = taskConfig((Task) values[1], "item", "list");
          return invokeCallable(listFunction, new Object[] {inputValues[2], inputValues[3]});
        }
        if (input instanceof Keyword || input instanceof hara.lang.data.Vector<?>
            || input instanceof hara.lang.data.List<?> || input instanceof hara.lang.data.Set<?>) {
          try {
            java.util.List<?> selected = TaskProcess.selectInputs(
                (Task) values[1], inputValues[2], inputValues[3], input);
            ArrayList<Object> results = new ArrayList<>();
            for (Object selectedInput : selected) {
              Object[] forwarded = inputValues.clone();
              forwarded[0] = selectedInput;
              results.add(invokeCallable(execute, forwarded));
            }
            return results;
          } catch (Exception error) {
            throw new HaraException("Unable to select task inputs: " + error.getMessage());
          }
        }
        return invokeCallable(execute, inputValues);
      });
    }));

    HaraNamespace bulk = namespace("std.task.bulk");
    bulk.define("bulk-display", new VariadicBuiltin("std.task.bulk/bulk-display", values -> {
      if (values.length != 2) throw new HaraException("bulk-display expects index and input lengths");
      java.util.Map<String, Object> display = new LinkedHashMap<>();
      display.put("padding", 1);
      display.put("spacing", 1);
      ArrayList<Object> columns = new ArrayList<>();
      columns.add(java.util.Map.of("id", "index", "length", values[0], "align", "right"));
      columns.add(java.util.Map.of("id", "input", "length", values[1]));
      columns.add(java.util.Map.of("id", "data", "length", 60));
      columns.add(java.util.Map.of("id", "time", "length", 10));
      display.put("columns", columns);
      return display;
    }));
    bulk.define("bulk-process-item", new VariadicBuiltin("std.task.bulk/bulk-process-item", values -> {
      if (values.length < 2 || !(values[0] instanceof HaraFunction)) {
        throw new HaraException("bulk-process-item expects a function and context");
      }
      Object context = values[1];
      Object input = context instanceof IMapType<?, ?> map ? lookupValue(map, Keyword.create("input")) : null;
      Object params = values.length > 2 ? values[2] : hara.lang.data.Map.Standard.EMPTY;
      Object lookup = values.length > 3 ? values[3] : hara.lang.data.Map.Standard.EMPTY;
      Object env = values.length > 4 ? values[4] : hara.lang.data.Map.Standard.EMPTY;
      Object[] functionArgs = new Object[4 + Math.max(0, values.length - 5)];
      functionArgs[0] = input;
      functionArgs[1] = params;
      functionArgs[2] = lookup;
      functionArgs[3] = env;
      if (values.length > 5) System.arraycopy(values, 5, functionArgs, 4, values.length - 5);
      long start = System.nanoTime();
      try {
        Object returned = HaraBox.unwrap(invokeCallable(values[0], functionArgs));
        if (returned instanceof ILinearType<?> pair && pair.count() == 2) {
          Object result = pair.nth(1);
          if (result instanceof IMapType<?, ?> map) {
            Object withTime = ((IMapType) map).assoc(
                Keyword.create("time"), (System.nanoTime() - start) / 1_000_000L);
            return hara.lang.data.List.Standard.from(null, pair.nth(0), withTime);
          }
          if (result instanceof java.util.Map<?, ?> javaMap) {
            java.util.Map<Object, Object> withTime = new LinkedHashMap<>(javaMap);
            withTime.put("time", (System.nanoTime() - start) / 1_000_000L);
            return hara.lang.data.List.Standard.from(null, pair.nth(0), withTime);
          }
          return returned;
        }
        java.util.Map<String, Object> item = new LinkedHashMap<>();
        item.put("input", input);
        item.put("status", "RETURN");
        item.put("data", returned);
        item.put("time", (System.nanoTime() - start) / 1_000_000L);
        return hara.lang.data.List.Standard.from(null, input, item);
      } catch (Throwable error) {
        java.util.Map<String, Object> item = new LinkedHashMap<>();
        item.put("input", input);
        item.put("status", "ERROR");
        item.put("data", "errored");
        item.put("error", String.valueOf(error.getMessage()));
        item.put("time", (System.nanoTime() - start) / 1_000_000L);
        return hara.lang.data.List.Standard.from(null, input, item);
      }
    }));
    bulk.define("bulk-items", new VariadicBuiltin("std.task.bulk/bulk-items", values -> bulkItems(values, false)));
    bulk.define("bulk-items-single", new VariadicBuiltin("std.task.bulk/bulk-items-single", values -> bulkItems(values, false)));
    bulk.define("bulk-items-parallel", new VariadicBuiltin("std.task.bulk/bulk-items-parallel", values -> bulkItems(values, true)));
    bulk.define("bulk-warnings", new VariadicBuiltin("std.task.bulk/bulk-warnings", values -> {
      if (values.length == 0) throw new HaraException("bulk-warnings expects parameters and items");
      return filterBulkResults(values[values.length - 1], "WARN");
    }));
    bulk.define("bulk-errors", new VariadicBuiltin("std.task.bulk/bulk-errors", values -> {
      if (values.length == 0) throw new HaraException("bulk-errors expects parameters and items");
      return filterBulkResults(values[values.length - 1], "ERROR");
    }));
    bulk.define("bulk-results", new VariadicBuiltin("std.task.bulk/bulk-results", values -> {
      if (values.length == 0) throw new HaraException("bulk-results expects parameters and items");
      if (values.length >= 3 && !(values[values.length - 1] instanceof java.util.Map<?, ?>)) {
        Object value = values[values.length - 1];
        if (value instanceof Iterable<?> iterable) {
          ArrayList<Object> results = new ArrayList<>();
          for (Object item : iterable) {
            if (!(item instanceof ILinearType<?> pair) || pair.count() != 2) continue;
            Object result = pair.nth(1);
            Object status;
            Object data;
            if (result instanceof IMapType<?, ?> map) {
              status = lookupValue(map, Keyword.create("status"));
              data = lookupValue(map, Keyword.create("data"));
            } else if (result instanceof java.util.Map<?, ?> map) {
              status = map.get("status");
              data = map.get("data");
            } else {
              continue;
            }
            String name = status instanceof Keyword ? ((Keyword) status).getName() : String.valueOf(status);
            if ("return".equalsIgnoreCase(name)) {
              java.util.Map<String, Object> output = new LinkedHashMap<>();
              output.put("key", pair.nth(0));
              output.put("data", data);
              results.add(output);
            }
          }
          return results;
        }
      }
      return filterBulkResults(values[values.length - 1], "RETURN");
    }));
    bulk.define("prepare-columns", new VariadicBuiltin("std.task.bulk/prepare-columns", values -> {
      if (values.length != 2) throw new HaraException("prepare-columns expects columns and outputs");
      return TaskBulk.prepareColumns(asBulkMaps(values[0]), asObjects(values[1]));
    }));
    bulk.define("bulk-summary", new VariadicBuiltin("std.task.bulk/bulk-summary", values -> {
      if (values.length == 0) throw new HaraException("bulk-summary expects item results");
      if (values.length >= 7 && values[0] instanceof Task) {
        java.util.List<Object> items = asObjects(values[2]);
        java.util.List<Object> results = asObjects(values[3]);
        java.util.List<Object> warnings = asObjects(values[4]);
        java.util.List<Object> errors = asObjects(values[5]);
        long cumulative = 0L;
        for (Object item : items) {
          if (item instanceof ILinearType<?> pair && pair.count() == 2
              && pair.nth(1) instanceof IMapType<?, ?> map) {
            Object time = lookupValue(map, Keyword.create("time"));
            if (time instanceof Number number) cumulative += number.longValue();
          }
        }
        java.util.Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("items", items.size());
        summary.put("results", results.size());
        summary.put("warnings", warnings.size());
        summary.put("errors", errors.size());
        summary.put("cumulative", cumulative);
        summary.put("elapsed", values[6]);
        return summary;
      }
      return TaskBulk.summary(asBulkMaps(values[values.length - 1]));
    }));
    bulk.define("bulk-package", new VariadicBuiltin("std.task.bulk/bulk-package", values -> {
      int bundleIndex = values.length > 0 && values[0] instanceof Task ? 1 : 0;
      if (values.length < bundleIndex + 2
          || (!(values[bundleIndex] instanceof Map<?, ?>) && !(values[bundleIndex] instanceof IMapType<?, ?>))) {
        throw new HaraException("bulk-package expects a bundle");
      }
      String returnMode = values[bundleIndex + 1] instanceof Keyword
          ? ((Keyword) values[bundleIndex + 1]).getName() : String.valueOf(values[bundleIndex + 1]);
      String packageMode = values.length > bundleIndex + 2 && values[bundleIndex + 2] instanceof Keyword
          ? ((Keyword) values[bundleIndex + 2]).getName() : "map";
      Map<String, Object> converted = new LinkedHashMap<>();
      if (values[bundleIndex] instanceof Map<?, ?> bundle) bundle.forEach((key, value) -> converted.put(keyName(key), value));
      else for (Object object : (IMapType<?, ?>) values[bundleIndex]) {
        java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) object;
        converted.put(keyName(entry.getKey()), entry.getValue());
      }
      return packageBulkValue(converted, returnMode, packageMode);
    }));
    bulk.define("bulk", new VariadicBuiltin("std.task.bulk/bulk", values -> {
      if (values.length >= 6 && values[0] instanceof Task
          && (values[1] instanceof HaraFunction || values[1] instanceof TaskFunction)) {
        Task task = (Task) values[0];
        Object function = values[1];
        Object inputValue = values[2];
        Object params = values[3];
        Object lookup = values[4];
        Object env = values[5];
        Object[] bulkArgs = new Object[6 + Math.max(0, values.length - 6)];
        bulkArgs[0] = function;
        bulkArgs[1] = inputValue;
        bulkArgs[2] = hara.lang.data.Map.Standard.EMPTY;
        bulkArgs[3] = params;
        bulkArgs[4] = lookup;
        bulkArgs[5] = env;
        if (values.length > 6) System.arraycopy(values, 6, bulkArgs, 6, values.length - 6);
        boolean parallel = optionTrue(params, "parallel");
        Object items = bulkItems(bulkArgs, parallel);
        Object warnings = filterBulkResults(items, "WARN");
        Object errors = filterBulkResults(items, "ERROR");
        Object results = filterBulkResults(items, "RETURN");
        java.util.Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("items", items);
        bundle.put("warnings", warnings);
        bundle.put("errors", errors);
        bundle.put("results", results);
        bundle.put("summary", TaskBulk.summary(asBulkMaps(items)));
        Object returnMode = params instanceof IMapType<?, ?> map
            ? lookupValue(map, Keyword.create("return")) : null;
        String mode = returnMode instanceof Keyword ? ((Keyword) returnMode).getName() : "results";
        return packageBulkValue(bundle, mode, "map");
      }
      if (values.length < 2 || !(values[0] instanceof Task)) throw new HaraException("bulk expects task and inputs");
      java.util.List<Object> inputs = new ArrayList<>();
      if (values[1] instanceof Iterable<?> iterable) iterable.forEach(inputs::add);
      else inputs.add(values[1]);
      java.util.List<Map<String, Object>> items = TaskBulk.items((Task) values[0], inputs);
      return java.util.Map.of("items", items, "summary", TaskBulk.summary(items), "results", items);
    }));
  }

  @SuppressWarnings("rawtypes")
  private boolean optionTrue(Object options, String name) {
    if (options instanceof IMapType<?, ?> map) return Boolean.TRUE.equals(lookupValue(map, Keyword.create(name)));
    if (options instanceof java.util.Map<?, ?> map) return Boolean.TRUE.equals(map.get(name));
    return false;
  }

  private Object filterBulkResults(Object value, String status) {
    if (!(value instanceof Iterable<?> iterable)) throw new HaraException("bulk result filter expects items");
    ArrayList<Object> results = new ArrayList<>();
    for (Object item : iterable) {
      if (item instanceof java.util.Map<?, ?> map) {
        Object rawStatus = map.get("status");
        String actual = rawStatus instanceof Keyword
            ? ((Keyword) rawStatus).getName().toUpperCase()
            : String.valueOf(rawStatus).replace(":", "").toUpperCase();
        if (status.equals(actual) || ("ERROR".equals(status) && "CRITICAL".equals(actual))) {
          results.add(item);
        }
      } else if (item instanceof ILinearType<?> pair && pair.count() == 2) {
        Object result = pair.nth(1);
        if (result instanceof IMapType<?, ?> map) {
          Object rawStatus = lookupValue(map, Keyword.create("status"));
          String actual = rawStatus instanceof Keyword
              ? ((Keyword) rawStatus).getName().toUpperCase()
              : String.valueOf(rawStatus).replace(":", "").toUpperCase();
          if (status.equals(actual) || ("ERROR".equals(status) && "CRITICAL".equals(actual))) {
            results.add(item);
          }
        } else if (result instanceof java.util.Map<?, ?> map) {
          Object rawStatus = map.get("status");
          String actual = rawStatus instanceof Keyword
              ? ((Keyword) rawStatus).getName().toUpperCase()
              : String.valueOf(rawStatus).replace(":", "").toUpperCase();
          if (status.equals(actual) || ("ERROR".equals(status) && "CRITICAL".equals(actual))) {
            results.add(item);
          }
        }
      }
    }
    return results;
  }

  private Object packageBulkValue(java.util.Map<String, Object> bundle, String returnMode, String packageMode) {
    if ("all".equals(returnMode)) {
      java.util.Map<String, Object> all = new LinkedHashMap<>();
      for (String key : java.util.List.of("items", "warnings", "errors", "results", "summary")) {
        if (bundle.containsKey(key)) all.put(key, packageBulkValue(bundle, key, packageMode));
      }
      return all;
    }
    Object selected = bundle.get(returnMode);
    if (!(selected instanceof Iterable<?> iterable)
        || "warnings".equals(returnMode) || "errors".equals(returnMode)) return selected;
    ArrayList<Object> vector = new ArrayList<>();
    java.util.Map<Object, Object> map = new LinkedHashMap<>();
    for (Object value : iterable) {
      Object key = null;
      Object data = value;
      if (value instanceof ILinearType<?> pair && pair.count() == 2) {
        key = pair.nth(0);
        data = pair.nth(1);
        if (data instanceof IMapType<?, ?> haraMap) data = lookupValue(haraMap, Keyword.create("data"));
        else if (data instanceof java.util.Map<?, ?> javaMap) data = javaMap.get("data");
      } else if (value instanceof IMapType<?, ?> haraMap) {
        key = lookupValue(haraMap, Keyword.create("key"));
        if (key == null) key = lookupValue(haraMap, Keyword.create("input"));
        if (lookupValue(haraMap, Keyword.create("data")) != null) {
          data = lookupValue(haraMap, Keyword.create("data"));
        }
      } else if (value instanceof java.util.Map<?, ?> javaMap) {
        key = javaMap.get("key");
        if (key == null) key = javaMap.get("input");
        if (javaMap.containsKey("data")) data = javaMap.get("data");
      }
      if ("vector".equals(packageMode)) {
        vector.add(hara.lang.data.List.Standard.from(null, key, data));
      } else {
        map.put(key, data);
      }
    }
    return "vector".equals(packageMode) ? vector : map;
  }

  private Object bulkItems(Object[] values, boolean parallel) {
    if (values.length >= 2 && values[0] instanceof HaraFunction) {
      Object function = values[0];
      java.util.List<Object> inputs = asObjects(values[1]);
      Object params = values.length > 3 ? values[3] : hara.lang.data.Map.Standard.EMPTY;
      Object lookup = values.length > 4 ? values[4] : hara.lang.data.Map.Standard.EMPTY;
      Object env = values.length > 5 ? values[5] : hara.lang.data.Map.Standard.EMPTY;
      int extraStart = Math.min(values.length, 6);
      java.util.function.Function<Object, Object> process = input -> {
        Object[] callArgs = new Object[4 + Math.max(0, values.length - extraStart)];
        callArgs[0] = input;
        callArgs[1] = params;
        callArgs[2] = lookup;
        callArgs[3] = env;
        if (values.length > extraStart) {
          System.arraycopy(values, extraStart, callArgs, 4, values.length - extraStart);
        }
        long start = System.nanoTime();
        try {
          Object returned = HaraBox.unwrap(invokeCallable(function, callArgs));
          if (returned instanceof ILinearType<?> pair && pair.count() == 2) {
            Object resultValue = pair.nth(1);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            if (resultValue instanceof IMapType<?, ?> resultMap) {
              return hara.lang.data.List.Standard.from(null, pair.nth(0),
                  ((IMapType) resultMap).assoc(Keyword.create("time"), elapsed));
            }
            if (resultValue instanceof java.util.Map<?, ?> resultMap) {
              java.util.Map<Object, Object> withTime = new LinkedHashMap<>(resultMap);
              withTime.put("time", elapsed);
              return hara.lang.data.List.Standard.from(null, pair.nth(0), withTime);
            }
            return returned;
          }
          java.util.Map<String, Object> result = new LinkedHashMap<>();
          result.put("status", "RETURN");
          result.put("data", returned);
          result.put("time", (System.nanoTime() - start) / 1_000_000L);
          return hara.lang.data.List.Standard.from(null, input, result);
        } catch (Throwable error) {
          java.util.Map<String, Object> result = new LinkedHashMap<>();
          result.put("status", "ERROR");
          result.put("data", "errored");
          result.put("error", String.valueOf(error.getMessage()));
          result.put("time", (System.nanoTime() - start) / 1_000_000L);
          return hara.lang.data.List.Standard.from(null, input, result);
        }
      };
      if (parallel) {
        return inputs.parallelStream().map(process).toList();
      }
      return inputs.stream().map(process).toList();
    }
    if (values.length < 2 || !(values[0] instanceof Task)) throw new HaraException("bulk-items expects a task and inputs");
    Task task = (Task) values[0];
    Object inputValue;
    if (values.length >= 3 && (values[1] instanceof HaraFunction || values[1] instanceof TaskFunction)) {
      task = new Task(task.type(), task.name(), toTaskFunction(values[1]), task.arglists(), task.config());
      inputValue = values[2];
    } else {
      inputValue = values[1];
    }
    java.util.List<Object> inputs = asObjects(inputValue);
    return parallel ? TaskBulk.itemsParallel(task, inputs) : TaskBulk.items(task, inputs);
  }

  private java.util.List<Object> asObjects(Object value) {
    java.util.List<Object> objects = new ArrayList<>();
    if (value instanceof Iterable<?> iterable) iterable.forEach(objects::add);
    else objects.add(value);
    return objects;
  }

  @SuppressWarnings("unchecked")
  private java.util.List<Map<String, Object>> asBulkMaps(Object value) {
    java.util.List<Map<String, Object>> maps = new ArrayList<>();
    for (Object item : asObjects(value)) {
      Map<String, Object> converted = new LinkedHashMap<>();
      if (item instanceof Map<?, ?> map) {
        map.forEach((key, nested) -> converted.put(keyName(key), nested));
      } else if (item instanceof ILinearType<?> pair && pair.count() == 2) {
        Object result = pair.nth(1);
        converted.put("input", pair.nth(0));
        if (result instanceof IMapType<?, ?> haraMap) {
          for (Object entryObject : haraMap) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
            converted.put(keyName(entry.getKey()), entry.getValue());
          }
        } else if (result instanceof Map<?, ?> map) {
          map.forEach((key, nested) -> converted.put(keyName(key), nested));
        }
      } else continue;
      maps.add(converted);
    }
    return maps;
  }

  private boolean matcherOption(Object[] values, String name) {
    for (int i = 1; i < values.length; i++) {
      if (values[i] instanceof Keyword keyword && name.equals(keyword.getName())) return true;
      if (values[i] instanceof Symbol symbol && name.equals(symbol.getName())) return true;
    }
    return false;
  }

  private static String keyName(Object key) {
    if (key instanceof Keyword) return ((Keyword) key).getName();
    return String.valueOf(key).replaceFirst("^:", "");
  }

  private Object wrapExecuteFunction(Object function, Task task) {
    return new VariadicBuiltin("std.task.process/wrapped-execute", values -> {
      if (values.length < 4) throw new HaraException("wrapped task expects input, params, lookup, and env");
      Object input = values[0];
      Object params = values[1];
      Object lookup = values[2];
      Object env = values[3];
      Object pre = taskConfig(task, "item", "pre");
      Object post = taskConfig(task, "item", "post");
      Object output = taskConfig(task, "item", "output");
      if (pre != null) input = invokeCallable(pre, new Object[] {input});
      Object[] callArgs = new Object[values.length];
      System.arraycopy(values, 0, callArgs, 0, values.length);
      callArgs[0] = input;
      Object result = invokeCallable(function, callArgs);
      if (post != null) result = invokeCallable(post, new Object[] {result});
      boolean bulk = params instanceof IMapType<?, ?> haraParams
          ? Boolean.TRUE.equals(lookupValue(haraParams, Keyword.create("bulk")))
          : params instanceof Map<?, ?> javaParams && Boolean.TRUE.equals(javaParams.get("bulk"));
      if (bulk) {
        java.util.Map<String, Object> packaged = new LinkedHashMap<>();
        packaged.put("status", "RETURN");
        packaged.put("data", result);
        return hara.lang.data.List.Standard.from(null, input, packaged);
      }
      if (output != null) result = invokeCallable(output, new Object[] {result});
      return result;
    });
  }

  private Object taskConfig(Task task, String first, String second) {
    Object group = task.config().get(first);
    return group instanceof java.util.Map<?, ?> map ? map.get(second) : null;
  }

  private Object createTask(Object[] values) {
    if (values.length == 1 && values[0] instanceof IMapType<?, ?> map) {
      Object type = lookupValue(map, Keyword.create("type"));
      Object name = lookupValue(map, Keyword.create("name"));
      Object main = lookupValue(map, Keyword.create("main"));
      Object function = main instanceof IMapType<?, ?> mainMap
          ? lookupValue(mainMap, Keyword.create("fn")) : main;
      return newTask(type, name, function, toJavaMap(map));
    }
    if (values.length != 3) throw new HaraException("std.task/task expects type, name, and function/config");
    Object config = values[2];
    Object function = config instanceof IMapType<?, ?> map
        ? lookupValue(map, Keyword.create("main")) == null
            ? null : lookupValue((IMapType<?, ?>) lookupValue(map, Keyword.create("main")), Keyword.create("fn"))
        : config;
    return newTask(values[0], values[1], function, config instanceof IMapType<?, ?> map ? toJavaMap(map) : java.util.Map.of());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object singleFunctionPrint(Object value) {
    if (!(value instanceof IMapType<?, ?> map)) throw new HaraException("single-function-print expects a map");
    Object bulk = lookupValue(map, Keyword.create("bulk"));
    Object print = lookupValue(map, Keyword.create("print"));
    if (Boolean.TRUE.equals(bulk)) return value;
    if (print instanceof IMapType<?, ?> printMap) {
      if (lookupValue(printMap, Keyword.create("function")) != null) return value;
      return ((IMapType) map).assoc(Keyword.create("print"),
          ((IMapType) printMap).assoc(Keyword.create("function"), Boolean.TRUE));
    }
    return ((IMapType) map).assoc(
        Keyword.create("print"),
        hara.lang.data.Map.Standard.from(null, Keyword.create("function"), Boolean.TRUE));
  }

  private Task newTask(Object type, Object name, Object function, java.util.Map<String, Object> config) {
    if (name == null || function == null) throw new HaraException("std.task/task requires a name and function");
    String taskType = type instanceof Keyword ? ((Keyword) type).getName() : String.valueOf(type);
    String taskName = String.valueOf(name);
    Object arglists = config.get("arglists");
    Object main = config.get("main");
    if (arglists == null && main instanceof java.util.Map<?, ?> mainMap) arglists = mainMap.get("arglists");
    if (arglists == null) {
      arglists = hara.lang.data.List.Standard.from(null,
          hara.lang.data.Vector.Standard.EMPTY,
          hara.lang.data.Vector.Standard.from(null, Symbol.create("entry")));
    }
    return new Task(taskType, taskName, toTaskFunction(function), arglists, config);
  }

  private TaskFunction toTaskFunction(Object function) {
    if (function instanceof TaskFunction taskFunction) return taskFunction;
    if (!(function instanceof HaraFunction haraFunction)) {
      throw new HaraException("std.task requires a Hara function");
    }
    return new TaskFunction() {
      @Override
      public Object apply(Object[] arguments) {
        return haraFunction.callTarget().call(haraFunction.callArguments(arguments));
      }

      @Override
      public int minimumArity() { return haraFunction.arity() < 0 ? 4 : Math.max(1, haraFunction.arity()); }

      @Override
      public boolean variadic() { return haraFunction.variadic(); }
    };
  }

  private java.util.Map<String, Object> toJavaMap(IMapType<?, ?> map) {
    java.util.Map<String, Object> result = new LinkedHashMap<>();
    for (Object object : map) {
      java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) object;
      String key = entry.getKey() instanceof Keyword
          ? ((Keyword) entry.getKey()).getName() : String.valueOf(entry.getKey());
      Object value = entry.getValue();
      result.put(key, value instanceof IMapType<?, ?> nested ? toJavaMap(nested) : value);
    }
    return result;
  }

  private Object expandDeftask(List<?> invocation) {
    if (invocation.count() < 3 || !(invocation.nth(1) instanceof Symbol)) {
      throw new HaraException("deftask expects a name and configuration");
    }
    Symbol name = (Symbol) invocation.nth(1);
    Object config = invocation.nth(2);
    Object type = config instanceof IMapType<?, ?> map ? lookupValue(map, Keyword.create("template")) : Keyword.create("default");
    Object main = config instanceof IMapType<?, ?> map ? lookupValue(map, Keyword.create("main")) : null;
    if (main instanceof IMapType<?, ?> map) main = lookupValue(map, Keyword.create("fn"));
    if (main == null) throw new HaraException("deftask configuration requires :main");
    Symbol definedName = name;
    if (config instanceof IMapType<?, ?> configMap) {
      Object doc = lookupValue(configMap, Keyword.create("doc"));
      Object arglists = lookupValue(configMap, Keyword.create("arglists"));
      Object mainConfig = lookupValue(configMap, Keyword.create("main"));
      if (arglists == null) {
        arglists = mainConfig instanceof IMapType<?, ?> mainMap
            ? lookupValue(mainMap, Keyword.create("arglists")) : null;
      }
      if (arglists == null) {
        arglists = hara.lang.data.List.Standard.from(null,
            hara.lang.data.Vector.Standard.EMPTY,
            hara.lang.data.Vector.Standard.from(null, Symbol.create("entry")));
      }
      if (doc != null || arglists != null) {
        java.util.ArrayList<Object> metadata = new java.util.ArrayList<>();
        if (doc != null) { metadata.add(Keyword.create("doc")); metadata.add(doc); }
        if (arglists != null) { metadata.add(Keyword.create("arglists")); metadata.add(arglists); }
        definedName = name.withMeta(hara.lang.data.Map.Standard.from(null, metadata.toArray()));
      }
    }
    return List.Standard.from(null, Symbol.create("def"), definedName,
        List.Standard.from(null, Symbol.create("std.task", "task"), type, name.getName(), config));
  }

  private Object expandFact(List<?> invocation) {
    if (invocation.count() < 3) throw new HaraException("fact expects a name and body");
    Object name = invocation.nth(1);
    ArrayList<Object> body = new ArrayList<>();
    for (int i = 2; i < invocation.count(); i++) {
      Object form = invocation.nth(i);
      if (i + 2 < invocation.count()
          && form instanceof Symbol
          && "=>".equals(((Symbol) form).getName())) {
        throw new HaraException("fact assertion is missing an actual expression");
      }
      if (i + 2 < invocation.count()
          && invocation.nth(i + 1) instanceof Symbol
          && "=>".equals(((Symbol) invocation.nth(i + 1)).getName())) {
        Object expected = invocation.nth(i + 2);
        if (expected instanceof List<?> expectedForm
            && expectedForm.count() > 0
            && expectedForm.nth(0) instanceof Symbol
            && "throws".equals(((Symbol) expectedForm.nth(0)).getName())) {
          body.add(List.Standard.from(null, Symbol.create("code.test", "assert-throws!"),
              List.Standard.from(null, Symbol.create("fn"), hara.lang.data.Vector.Standard.EMPTY, form), expected));
        } else {
          body.add(List.Standard.from(null, Symbol.create("code.test", "assert!"), form, expected));
        }
        i += 2;
      } else {
        body.add(form);
      }
    }
    Object[] fnBody = new Object[body.size() + 1];
    fnBody[0] = Symbol.create("fn");
    fnBody[1] = hara.lang.data.Vector.Standard.EMPTY;
    if (body.size() == 1) {
      fnBody = new Object[] {Symbol.create("fn"), hara.lang.data.Vector.Standard.EMPTY, body.get(0)};
    } else {
      ArrayList<Object> forms = new ArrayList<>();
      forms.add(Symbol.create("fn"));
      forms.add(hara.lang.data.Vector.Standard.EMPTY);
      forms.add(List.Standard.from(null, prepend(Symbol.create("do"), body)));
      fnBody = forms.toArray();
    }
    IMetadata metadata = invocation instanceof hara.lang.protocol.IObjType
        ? ((hara.lang.protocol.IObjType) invocation).meta() : null;
    if (metadata != null) {
      return List.Standard.from(
          null,
          Symbol.create("code.test", "register!"),
          name,
          List.Standard.from(null, fnBody),
          metadata);
    }
    return List.Standard.from(
        null,
        Symbol.create("code.test", "register!"),
        name,
        List.Standard.from(null, fnBody));
  }

  private boolean testSelector(Object selector, hara.lang.test.HaraTestCase test) {
    if (selector == null) return true;
    String id = test.namespace() + "/" + test.name();
    return testSelector(selector, id);
  }

  private boolean metadataMatches(Object expected, Object metadata) {
    if (expected == null) return true;
    if (expected instanceof HaraMatcher matcher) return matcher.matches(metadata);
    if (expected instanceof HaraFunction function) {
      return Boolean.TRUE.equals(function.callTarget().call(function.callArguments(new Object[] {metadata})));
    }
    if (expected instanceof IMapType<?, ?> expectedMap && metadata instanceof IMapType<?, ?> actualMap) {
      for (Object object : expectedMap) {
        java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) object;
        Object actual = lookupValue(actualMap, entry.getKey());
        if (actual == null) return false;
        if (!Eq.eq(actual, entry.getValue())) return false;
      }
      return true;
    }
    if (expected instanceof java.util.Map<?, ?> expectedMap && metadata instanceof java.util.Map<?, ?> actualMap) {
      for (java.util.Map.Entry<?, ?> entry : expectedMap.entrySet()) {
        if (!actualMap.containsKey(entry.getKey()) || !Eq.eq(actualMap.get(entry.getKey()), entry.getValue())) {
          return false;
        }
      }
      return true;
    }
    return Eq.eq(expected, metadata);
  }

  private String testNamespace(Object value) {
    if (value instanceof Symbol symbol) return symbol.display();
    return String.valueOf(value);
  }

  private java.util.Map<String, Object> testRecord(hara.lang.test.HaraTestCase test) {
    java.util.Map<String, Object> record = new LinkedHashMap<>();
    record.put("namespace", test.namespace());
    record.put("name", test.name());
    record.put("metadata", test.metadata());
    return record;
  }

  private java.util.Map<String, Object> summariseTestResults(Object value) {
    long passed = 0;
    long failed = 0;
    long errored = 0;
    long total = 0;
    if (value instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        total++;
        Object status = null;
        if (item instanceof IMapType<?, ?> map) status = lookupValue(map, Keyword.create("status"));
        else if (item instanceof java.util.Map<?, ?> map) status = map.get("status");
        String name = status instanceof Keyword ? ((Keyword) status).getName() : String.valueOf(status);
        if ("pass".equalsIgnoreCase(name) || "success".equalsIgnoreCase(name)) passed++;
        else if ("fail".equalsIgnoreCase(name) || "failed".equalsIgnoreCase(name)) failed++;
        else errored++;
      }
    }
    java.util.Map<String, Object> result = new LinkedHashMap<>();
    result.put("total", total);
    result.put("passed", passed);
    result.put("failed", failed);
    result.put("errored", errored);
    result.put("success", failed == 0 && errored == 0);
    return result;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private java.util.Map<String, Object> asStringMap(Object value) {
    java.util.Map<String, Object> result = new LinkedHashMap<>();
    if (value instanceof java.util.Map<?, ?> map) {
      map.forEach((key, nested) -> result.put(keyName(key), nested));
    } else if (value instanceof IMapType<?, ?> map) {
      for (Object object : map) {
        java.util.Map.Entry entry = (java.util.Map.Entry) object;
        result.put(keyName(entry.getKey()), entry.getValue());
      }
    }
    return result;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private boolean metadataFlag(Object metadata, String name) {
    if (!(metadata instanceof IMapType<?, ?> map)) return false;
    return Boolean.TRUE.equals(((IMapType) map).lookup(Keyword.create(name)));
  }

  private boolean testSelector(Object selector, String id) {
    if (selector == null) return true;
    if (selector instanceof java.util.function.Predicate<?> predicate) {
      @SuppressWarnings("unchecked") java.util.function.Predicate<Object> test =
          (java.util.function.Predicate<Object>) predicate;
      return test.test(id);
    }
    if (selector instanceof HaraFunction function) {
      return Boolean.TRUE.equals(function.callTarget().call(function.callArguments(new Object[] {id})));
    }
    if (selector instanceof java.util.regex.Pattern pattern) return pattern.matcher(id).find();
    if (selector instanceof hara.lang.data.types.ISetType<?> set) {
      @SuppressWarnings("rawtypes") hara.lang.data.types.ISetType rawSet = (hara.lang.data.types.ISetType) set;
      return rawSet.find(id) != null;
    }
    if (selector instanceof hara.lang.data.Vector<?> vector) {
      for (Object item : vector) if (testSelector(item, id)) return true;
      return false;
    }
    if (selector instanceof hara.lang.data.List<?> list) {
      for (Object item : list) if (!testSelector(item, id)) return false;
      return true;
    }
    if (selector instanceof String) return id.startsWith((String) selector);
    if (selector instanceof Symbol) return id.startsWith(((Symbol) selector).display());
    if (selector instanceof Keyword) return id.startsWith(((Keyword) selector).getName());
    return false;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Object lookupValue(IMapType<?, ?> map, Object key) {
    return ((IMapType) map).lookup(key);
  }

  private static Object[] prepend(Object first, java.util.List<Object> rest) {
    Object[] values = new Object[rest.size() + 1];
    values[0] = first;
    for (int i = 0; i < rest.size(); i++) values[i + 1] = rest.get(i);
    return values;
  }

  private Object conjoin(Object[] values) {
    Object result;
    int firstValue;
    if (values.length < 2) {
      result = BuiltinStruct.vector(new Object[0]);
      firstValue = 0;
    } else {
      result = values[0];
      firstValue = 1;
    }
    for (int i = firstValue; i < values.length; i++) {
      result = protocolCall("IConj", "conj", new Object[] {result, values[i]});
    }
    return result;
  }

  private static String concatenateStrings(Object[] values) {
    StringBuilder result = new StringBuilder();
    for (Object value : values) {
      Object unwrapped = HaraBox.unwrap(value);
      if (unwrapped == null || unwrapped == HaraNull.SINGLETON) {
        continue;
      }
      if (unwrapped instanceof IDisplay) {
        result.append(((IDisplay) unwrapped).display());
      } else {
        result.append(unwrapped);
      }
    }
    return result.toString();
  }

  private void installCoreBuiltins(HaraNamespace target) {
    target.define("str", new VariadicBuiltin("str", HaraContext::concatenateStrings));
    target.define("promise", new UnaryBuiltin("promise", this::promiseRun));
    target.define("bytes", new VariadicBuiltin("bytes", this::createBytes));
    target.define("array", new VariadicBuiltin("array", HaraArray::new));
    target.define("object", new VariadicBuiltin("object", HaraObject::new));
    target.define("bit-and", new VariadicBuiltin("bit-and", values -> bitOperation("and", values)));
    target.define("bit-or", new VariadicBuiltin("bit-or", values -> bitOperation("or", values)));
    target.define("bit-xor", new VariadicBuiltin("bit-xor", values -> bitOperation("xor", values)));
    target.define("bit-not", new UnaryBuiltin("bit-not", value -> (long) ~int32(value, "bit-not")));
    target.define(
        "bit-shift-left", new VariadicBuiltin("bit-shift-left", values -> bitShift(values, true)));
    target.define(
        "bit-shift-right",
        new VariadicBuiltin("bit-shift-right", values -> bitShift(values, false)));
  }

  private Object createBytes(Object[] values) {
    byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) byteNumber(values[i], "bytes");
    }
    return result;
  }

  private void installNativeLibraries() {
    HaraNamespace jvm = namespace("hara.native.jvm");
    jvm.define(
        "set!",
        new VariadicBuiltin(
            "hara.native.jvm/set!",
            values -> {
              if (values.length != 3)
                throw new HaraException("hara.native.jvm/set! expects 3 arguments");
              String member;
              if (values[1] instanceof Symbol) member = ((Symbol) values[1]).getName();
              else if (values[1] instanceof Keyword) member = ((Keyword) values[1]).getName();
              else if (values[1] instanceof String) member = (String) values[1];
              else throw new HaraException("JVM member must be a symbol, keyword, or string");
              NativeFlavorProvider provider = nativeProvider();
              if (provider == null) {
                throw new HaraException("hara.native.jvm/set! requires an ns :flavor declaration");
              }
              return provider.writeMember(
                  HaraBox.unwrap(values[0]), member, HaraBox.unwrap(values[2]), nativeAccess());
            }));
    namespace("hara.native.jvm.reflect");
    namespace("hara.native.jvm.classpath");
    namespace("hara.native.jvm.compiler");
    installJvmNativeLibraries();
  }

  private void installJvmNativeLibraries() {
    HaraNamespace reflect = namespace("hara.native.jvm.reflect");
    reflect.define(
        "type",
        new UnaryBuiltin(
            "reflect/type", value -> jvmProvider().type(HaraBox.unwrap(value), nativeAccess())));
    reflect.define(
        "name",
        new UnaryBuiltin(
            "reflect/name",
            value -> jvmProvider().typeName(HaraBox.unwrap(value), nativeAccess())));
    reflect.define(
        "instance?",
        new VariadicBuiltin(
            "reflect/instance?",
            values -> {
              requireMethodArity("reflect/instance?", values, 2);
              return jvmProvider()
                  .isInstance(HaraBox.unwrap(values[0]), HaraBox.unwrap(values[1]), nativeAccess());
            }));
    reflect.define(
        "fields",
        new UnaryBuiltin(
            "reflect/fields",
            value -> jvmProvider().fields(HaraBox.unwrap(value), nativeAccess())));
    reflect.define(
        "methods",
        new UnaryBuiltin(
            "reflect/methods",
            value -> jvmProvider().methods(HaraBox.unwrap(value), nativeAccess())));

    HaraNamespace classpath = namespace("hara.native.jvm.classpath");
    classpath.define(
        "paths",
        new VariadicBuiltin(
            "classpath/paths",
            values -> {
              requireMethodArity("classpath/paths", values, 0);
              return jvmProvider().classPath(nativeAccess());
            }));
    classpath.define(
        "add!",
        new UnaryBuiltin(
            "classpath/add!",
            value ->
                jvmProvider().addClassPath(String.valueOf(HaraBox.unwrap(value)), nativeAccess())));

    HaraNamespace compiler = namespace("hara.native.jvm.compiler");
    compiler.define(
        "compile",
        new UnaryBuiltin(
            "compiler/compile",
            value -> jvmProvider().compile(HaraBox.unwrap(value), nativeAccess())));
    compiler.define(
        "define!",
        new UnaryBuiltin(
            "compiler/define!",
            value -> {
              Object bytecode = HaraBox.unwrap(value);
              if (!(bytecode instanceof byte[])) {
                throw new HaraException("compiler/define! expects bytes");
              }
              return jvmProvider().defineClass((byte[]) bytecode, nativeAccess());
            }));
    compiler.define(
        "compile!",
        new UnaryBuiltin(
            "compiler/compile!",
            value -> {
              JvmFlavorProvider provider = jvmProvider();
              return provider.defineClass(
                  provider.compile(HaraBox.unwrap(value), nativeAccess()), nativeAccess());
            }));
  }

  private void installGeneratedLibraries() {
    HaraNamespace string = namespace("hara.lib.string");
    string.define(
        "len", new UnaryBuiltin("str/len", value -> (long) stringValue(value, "str/len").length()));
    string.define("comp", new VariadicBuiltin("str/comp", this::stringCompare));
    string.define(
        "lt?", new VariadicBuiltin("str/lt?", values -> ((Long) stringCompare(values)) < 0));
    string.define(
        "gt?", new VariadicBuiltin("str/gt?", values -> ((Long) stringCompare(values)) > 0));
    string.define(
        "pad-left", new VariadicBuiltin("str/pad-left", values -> padString(values, true)));
    string.define(
        "pad-right", new VariadicBuiltin("str/pad-right", values -> padString(values, false)));
    string.define(
        "starts-with?",
        new VariadicBuiltin(
            "str/starts-with?",
            values -> {
              String[] pair = stringPair(values, "str/starts-with?");
              return pair[0].startsWith(pair[1]);
            }));
    string.define(
        "ends-with?",
        new VariadicBuiltin(
            "str/ends-with?",
            values -> {
              String[] pair = stringPair(values, "str/ends-with?");
              return pair[0].endsWith(pair[1]);
            }));
    string.define("char", new VariadicBuiltin("str/char", this::stringChar));
    string.define("split", new VariadicBuiltin("str/split", this::stringSplit));
    string.define("join", new VariadicBuiltin("str/join", this::stringJoin));
    string.define("index-of", new VariadicBuiltin("str/index-of", this::stringIndexOf));
    string.define("substring", new VariadicBuiltin("str/substring", this::stringSubstring));
    string.define(
        "to-upper",
        new UnaryBuiltin(
            "str/to-upper",
            value -> stringValue(value, "str/to-upper").toUpperCase(java.util.Locale.ROOT)));
    string.define(
        "to-lower",
        new UnaryBuiltin(
            "str/to-lower",
            value -> stringValue(value, "str/to-lower").toLowerCase(java.util.Locale.ROOT)));
    string.define("to-fixed", new VariadicBuiltin("str/to-fixed", this::stringToFixed));
    string.define("replace", new VariadicBuiltin("str/replace", this::stringReplace));
    string.define(
        "trim", new UnaryBuiltin("str/trim", value -> stringValue(value, "str/trim").trim()));
    string.define(
        "trim-left",
        new UnaryBuiltin(
            "str/trim-left", value -> stringValue(value, "str/trim-left").stripLeading()));
    string.define(
        "trim-right",
        new UnaryBuiltin(
            "str/trim-right", value -> stringValue(value, "str/trim-right").stripTrailing()));
    string.define(
        "encode",
        new UnaryBuiltin(
            "str/encode",
            value -> stringValue(value, "str/encode").getBytes(StandardCharsets.UTF_8)));
    string.define(
        "decode",
        new UnaryBuiltin(
            "str/decode",
            value -> new String(bytesValue(value, "str/decode"), StandardCharsets.UTF_8)));

    HaraNamespace bytes = namespace("hara.lib.bytes");
    bytes.define(
        "count",
        new UnaryBuiltin("bytes/count", value -> (long) bytesValue(value, "bytes/count").length));
    bytes.define("get", new VariadicBuiltin("bytes/get", this::bytesGet));
    bytes.define("set", new VariadicBuiltin("bytes/set", this::bytesSet));
    bytes.define(
        "copy", new UnaryBuiltin("bytes/copy", value -> bytesValue(value, "bytes/copy").clone()));
    bytes.define("slice", new VariadicBuiltin("bytes/slice", this::bytesSlice));
    bytes.define(
        "u8", new UnaryBuiltin("bytes/u8", value -> (long) (byteNumber(value, "bytes/u8") & 0xff)));
    bytes.define(
        "s8", new UnaryBuiltin("bytes/s8", value -> (long) (byte) byteNumber(value, "bytes/s8")));

    HaraNamespace promise = namespace("hara.lib.promise");
    promise.define("run", new UnaryBuiltin("promise/run", this::promiseRun));
    promise.define("new", new UnaryBuiltin("promise/new", this::promiseNew));
    promise.define("all", new UnaryBuiltin("promise/all", this::promiseAll));
    promise.define(
        "then", new VariadicBuiltin("promise/then", values -> promiseThen(values, false)));
    promise.define(
        "catch", new VariadicBuiltin("promise/catch", values -> promiseThen(values, true)));
    promise.define("finally", new VariadicBuiltin("promise/finally", this::promiseFinally));
    promise.define(
        "state",
        new UnaryBuiltin("promise/state", value -> requirePromise(value, "promise/state").state()));
    promise.define(
        "value",
        new UnaryBuiltin("promise/value", value -> requirePromise(value, "promise/value").value()));
    promise.define(
        "cancel",
        new UnaryBuiltin(
            "promise/cancel", value -> requirePromise(value, "promise/cancel").cancel()));
    promise.define(
        "native?",
        new UnaryBuiltin("promise/native?", value -> HaraBox.unwrap(value) instanceof HaraPromise));
    promise.define("delay", new VariadicBuiltin("promise/delay", this::promiseDelay));

    HaraNamespace handle = namespace("hara.lib.handle");
    handle.define(
        "release",
        new UnaryBuiltin(
            "handle/release",
            value -> {
              Object input = HaraBox.unwrap(value);
              if (!(input instanceof HtaHandle))
                throw new HaraException("handle/release expects an HTA handle");
              ((HtaHandle) input).close();
              return HaraNull.SINGLETON;
            }));

    HaraNamespace file = namespace("hara.lib.file");
    file.define("resolve", new VariadicBuiltin("file/resolve", this::fileResolve));
    file.define("read", new UnaryBuiltin("file/read", this::fileRead));
    file.define("write", new VariadicBuiltin("file/write", this::fileWrite));

    HaraNamespace socket = namespace("hara.lib.socket");
    socket.define("connect", new VariadicBuiltin("socket/connect", this::socketConnect));
    socket.define("send", new VariadicBuiltin("socket/send", this::socketSend));
    socket.define("close", new UnaryBuiltin("socket/close", this::socketClose));
  }

  private static int int32(Object value, String operation) {
    Object input = HaraBox.unwrap(value);
    if (!(input instanceof Number)) throw new HaraException(operation + " expects integers");
    long number = ((Number) input).longValue();
    if (number < Integer.MIN_VALUE || number > 0xffffffffL) {
      throw new HaraException(operation + " expects a signed 32-bit value");
    }
    return (int) number;
  }

  private static Object bitOperation(String operation, Object[] values) {
    if (values.length != 2) throw new HaraException("bit-" + operation + " expects two integers");
    int left = int32(values[0], "bit-" + operation);
    int right = int32(values[1], "bit-" + operation);
    if ("and".equals(operation)) return (long) (left & right);
    if ("or".equals(operation)) return (long) (left | right);
    return (long) (left ^ right);
  }

  private static Object bitShift(Object[] values, boolean left) {
    String name = left ? "bit-shift-left" : "bit-shift-right";
    if (values.length != 2) throw new HaraException(name + " expects two integers");
    int value = int32(values[0], name);
    int distance = int32(values[1], name);
    if (distance < 0 || distance > 31) {
      throw new HaraException(name + " distance must be in the range 0..31");
    }
    return (long) (left ? value << distance : value >> distance);
  }

  private static String stringValue(Object value, String operation) {
    Object input = HaraBox.unwrap(value);
    if (!(input instanceof String)) throw new HaraException(operation + " expects a string");
    return (String) input;
  }

  private static String[] stringPair(Object[] values, String operation) {
    if (values.length != 2) throw new HaraException(operation + " expects two strings");
    return new String[] {stringValue(values[0], operation), stringValue(values[1], operation)};
  }

  private Object stringCompare(Object[] values) {
    String[] pair = stringPair(values, "str/comp");
    return (long) Integer.signum(pair[0].compareTo(pair[1]));
  }

  private Object padString(Object[] values, boolean left) {
    String operation = left ? "str/pad-left" : "str/pad-right";
    if (values.length != 3 || !(HaraBox.unwrap(values[1]) instanceof Number)) {
      throw new HaraException(operation + " expects a string, length, and padding string");
    }
    String input = stringValue(values[0], operation);
    int length = ((Number) HaraBox.unwrap(values[1])).intValue();
    String padding = stringValue(values[2], operation);
    if (padding.isEmpty() || input.length() >= length) return input;
    StringBuilder fill = new StringBuilder();
    while (fill.length() < length - input.length()) fill.append(padding);
    String clipped = fill.substring(0, length - input.length());
    return left ? clipped + input : input + clipped;
  }

  private Object stringChar(Object[] values) {
    if (values.length != 2 || !(HaraBox.unwrap(values[1]) instanceof Number)) {
      throw new HaraException("str/char expects a string and index");
    }
    String input = stringValue(values[0], "str/char");
    int index = ((Number) HaraBox.unwrap(values[1])).intValue();
    if (index < 0 || index >= input.length())
      throw new HaraException("str/char index out of bounds");
    return String.valueOf(input.charAt(index));
  }

  private Object stringSplit(Object[] values) {
    String[] pair = stringPair(values, "str/split");
    String[] parts = pair[0].split(java.util.regex.Pattern.quote(pair[1]), -1);
    return new HaraArray(parts);
  }

  private Object stringJoin(Object[] values) {
    if (values.length != 2) throw new HaraException("str/join expects a separator and collection");
    String separator = stringValue(values[0], "str/join");
    Iterator<?> iterator = (Iterator<?>) iterValue(values[1]);
    StringBuilder output = new StringBuilder();
    while (iterator.hasNext()) {
      if (output.length() > 0) output.append(separator);
      output.append(stringValue(iterator.next(), "str/join"));
    }
    return output.toString();
  }

  private Object stringIndexOf(Object[] values) {
    if (values.length < 2 || values.length > 3) {
      throw new HaraException("str/index-of expects a string, substring, and optional offset");
    }
    String input = stringValue(values[0], "str/index-of");
    String part = stringValue(values[1], "str/index-of");
    int offset = values.length == 2 ? 0 : ((Number) HaraBox.unwrap(values[2])).intValue();
    return (long) input.indexOf(part, offset);
  }

  private Object stringSubstring(Object[] values) {
    if (values.length < 2 || values.length > 3) {
      throw new HaraException("str/substring expects a string, start, and optional end");
    }
    String input = stringValue(values[0], "str/substring");
    int start = ((Number) HaraBox.unwrap(values[1])).intValue();
    int end = values.length == 3 ? ((Number) HaraBox.unwrap(values[2])).intValue() : input.length();
    try {
      return input.substring(start, end);
    } catch (IndexOutOfBoundsException error) {
      throw new HaraException("str/substring range is out of bounds");
    }
  }

  private Object stringToFixed(Object[] values) {
    if (values.length != 2
        || !(HaraBox.unwrap(values[0]) instanceof Number)
        || !(HaraBox.unwrap(values[1]) instanceof Number)) {
      throw new HaraException("str/to-fixed expects a number and precision");
    }
    int precision = ((Number) HaraBox.unwrap(values[1])).intValue();
    if (precision < 0 || precision > 100) {
      throw new HaraException("str/to-fixed precision must be in the range 0..100");
    }
    return String.format(
        java.util.Locale.ROOT,
        "%." + precision + "f",
        ((Number) HaraBox.unwrap(values[0])).doubleValue());
  }

  private Object stringReplace(Object[] values) {
    if (values.length != 3) {
      throw new HaraException("str/replace expects a string, match, and replacement");
    }
    return stringValue(values[0], "str/replace")
        .replace(stringValue(values[1], "str/replace"), stringValue(values[2], "str/replace"));
  }

  private static byte[] bytesValue(Object value, String operation) {
    Object input = HaraBox.unwrap(value);
    if (!(input instanceof byte[])) throw new HaraException(operation + " expects bytes");
    return (byte[]) input;
  }

  private static int byteNumber(Object value, String operation) {
    Object input = HaraBox.unwrap(value);
    if (!(input instanceof Number)) throw new HaraException(operation + " expects a byte value");
    long number = ((Number) input).longValue();
    if (number < -128 || number > 255) {
      throw new HaraException(operation + " expects a value in the range -128..255");
    }
    return (int) number;
  }

  private Object bytesGet(Object[] values) {
    if (values.length < 2 || values.length > 3) {
      throw new HaraException("bytes/get expects bytes, index, and optional fallback");
    }
    byte[] input = bytesValue(values[0], "bytes/get");
    int index = ((Number) HaraBox.unwrap(values[1])).intValue();
    if (index < 0 || index >= input.length) {
      if (values.length == 3) return values[2];
      throw new HaraException("bytes/get index out of bounds: " + index);
    }
    return (long) Byte.toUnsignedInt(input[index]);
  }

  private Object bytesSet(Object[] values) {
    if (values.length != 3) throw new HaraException("bytes/set expects bytes, index, and value");
    byte[] input = bytesValue(values[0], "bytes/set");
    int index = ((Number) HaraBox.unwrap(values[1])).intValue();
    if (index < 0 || index >= input.length) {
      throw new HaraException("bytes/set index out of bounds: " + index);
    }
    int value = byteNumber(values[2], "bytes/set");
    input[index] = (byte) value;
    return input;
  }

  private Object bytesSlice(Object[] values) {
    if (values.length < 2 || values.length > 3) {
      throw new HaraException("bytes/slice expects bytes, start, and optional end");
    }
    byte[] input = bytesValue(values[0], "bytes/slice");
    int start = ((Number) HaraBox.unwrap(values[1])).intValue();
    int end = values.length == 3 ? ((Number) HaraBox.unwrap(values[2])).intValue() : input.length;
    if (start < 0 || end < start || end > input.length) {
      throw new HaraException("bytes/slice range is out of bounds");
    }
    return java.util.Arrays.copyOfRange(input, start, end);
  }

  private Object promiseRun(Object thunk) {
    CompletableFuture<Object> future =
        CompletableFuture.supplyAsync(
                () -> {
                  try {
                    return invokeInContext(() -> invokeCallable(thunk, new Object[0]));
                  } catch (RuntimeException error) {
                    throw new CompletionException(error);
                  }
                })
            .thenCompose(this::flatten);
    return new HaraPromise(future);
  }

  private Object promiseNew(Object thunk) {
    CompletableFuture<Object> future = new CompletableFuture<>();
    Object resolve =
        new UnaryBuiltin(
            "promise-resolve",
            value -> {
              flatten(value)
                  .whenComplete(
                      (resolved, error) -> {
                        if (error == null) future.complete(resolved);
                        else future.completeExceptionally(error);
                      });
              return value;
            });
    Object reject =
        new UnaryBuiltin(
            "promise-reject",
            value -> {
              future.completeExceptionally(new HaraException(String.valueOf(value)));
              return value;
            });
    try {
      invokeCallable(thunk, new Object[] {resolve, reject});
    } catch (RuntimeException error) {
      future.completeExceptionally(error);
    }
    return new HaraPromise(future);
  }

  private HaraPromise requirePromise(Object value, String operation) {
    Object input = HaraBox.unwrap(value);
    if (!(input instanceof HaraPromise)) throw new HaraException(operation + " expects a promise");
    return (HaraPromise) input;
  }

  private CompletableFuture<Object> flatten(Object value) {
    Object input = HaraBox.unwrap(value);
    return input instanceof HaraPromise
        ? ((HaraPromise) input).future
        : CompletableFuture.completedFuture(input);
  }

  private Object promiseAll(Object value) {
    ArrayList<CompletableFuture<Object>> promises = new ArrayList<>();
    Iterator<?> iterator = (Iterator<?>) iterValue(value);
    while (iterator.hasNext()) promises.add(flatten(iterator.next()));
    CompletableFuture<?>[] futures = promises.toArray(new CompletableFuture[0]);
    CompletableFuture<Object> result =
        CompletableFuture.allOf(futures)
            .thenApply(
                ignored -> new HaraArray(promises.stream().map(CompletableFuture::join).toArray()));
    return new HaraPromise(result);
  }

  private Object promiseThen(Object[] values, boolean failure) {
    String operation = failure ? "promise/catch" : "promise/then";
    if (values.length != 2) throw new HaraException(operation + " expects a promise and function");
    HaraPromise promise = requirePromise(values[0], operation);
    CompletableFuture<Object> result;
    if (failure) {
      result =
          promise
              .future
              .handle(
                  (value, error) ->
                      error == null
                          ? CompletableFuture.completedFuture(value)
                          : flatten(
                              invokeInContext(
                                  () ->
                                      invokeCallable(
                                          values[1],
                                          new Object[] {
                                            error.getCause() == null ? error : error.getCause()
                                          }))))
              .thenCompose(Function.identity());
    } else {
      result =
          promise
              .future
              .thenApply(
                  value ->
                      flatten(
                          invokeInContext(() -> invokeCallable(values[1], new Object[] {value}))))
              .thenCompose(Function.identity());
    }
    return new HaraPromise(result);
  }

  private Object promiseFinally(Object[] values) {
    if (values.length != 2) {
      throw new HaraException("promise/finally expects a promise and function");
    }
    HaraPromise promise = requirePromise(values[0], "promise/finally");
    CompletableFuture<Object> result =
        promise
            .future
            .handle(
                (value, error) ->
                    flatten(invokeInContext(() -> invokeCallable(values[1], new Object[0])))
                        .thenApply(
                            ignored -> {
                              if (error != null) throw new CompletionException(error);
                              return value;
                            }))
            .thenCompose(Function.identity());
    return new HaraPromise(result);
  }

  private Object promiseDelay(Object[] values) {
    if (values.length != 2 || !(HaraBox.unwrap(values[0]) instanceof Number)) {
      throw new HaraException("promise/delay expects milliseconds and a function");
    }
    long millis = ((Number) HaraBox.unwrap(values[0])).longValue();
    if (millis < 0) throw new HaraException("promise/delay expects non-negative milliseconds");
    CompletableFuture<Object> future =
        CompletableFuture.supplyAsync(
                () -> invokeInContext(() -> invokeCallable(values[1], new Object[0])),
                CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS))
            .thenCompose(this::flatten);
    return new HaraPromise(future);
  }

  private <T> T invokeInContext(Supplier<T> operation) {
    Object previous = environment.getContext().enter(null);
    try {
      return operation.get();
    } finally {
      environment.getContext().leave(null, previous);
    }
  }

  private void requireFileIO(String operation) {
    if (!environment.isFileIOAllowed()) {
      throw new HaraException(operation + " is unsupported or file access is denied");
    }
  }

  private void requireSocketIO(String operation) {
    if (!environment.isSocketIOAllowed()) {
      throw new HaraException(operation + " is unsupported or network access is denied");
    }
  }

  private Object fileResolve(Object[] values) {
    requireFileIO("file/resolve");
    if (values.length != 2) throw new HaraException("file/resolve expects a root and path");
    TruffleFile root = environment.getPublicTruffleFile(stringValue(values[0], "file/resolve"));
    return root.resolve(stringValue(values[1], "file/resolve")).normalize().getPath();
  }

  private Object fileRead(Object value) {
    requireFileIO("file/read");
    String path = stringValue(value, "file/read");
    return new HaraPromise(
        CompletableFuture.supplyAsync(
            () ->
                invokeInContext(
                    () -> {
                      try {
                        return environment.getPublicTruffleFile(path).readAllBytes();
                      } catch (IOException error) {
                        throw new CompletionException(error);
                      }
                    })));
  }

  private Object fileWrite(Object[] values) {
    requireFileIO("file/write");
    if (values.length != 2) throw new HaraException("file/write expects a path and bytes");
    String path = stringValue(values[0], "file/write");
    byte[] contents = bytesValue(values[1], "file/write").clone();
    return new HaraPromise(
        CompletableFuture.supplyAsync(
            () ->
                invokeInContext(
                    () -> {
                      try {
                        try (OutputStream output =
                            environment.getPublicTruffleFile(path).newOutputStream()) {
                          output.write(contents);
                        }
                        return null;
                      } catch (IOException error) {
                        throw new CompletionException(error);
                      }
                    })));
  }

  private Object socketConnect(Object[] values) {
    requireSocketIO("socket/connect");
    if (values.length != 4) {
      throw new HaraException("socket/connect expects a host, port, options, and callback");
    }
    String host = stringValue(values[0], "socket/connect");
    int port = ((Number) HaraBox.unwrap(values[1])).intValue();
    try {
      Socket socket = new Socket();
      socket.connect(new InetSocketAddress(host, port));
      return invokeCallable(values[3], new Object[] {null, new HaraSocket(socket)});
    } catch (IOException error) {
      return invokeCallable(values[3], new Object[] {error.getMessage(), null});
    }
  }

  private Object socketSend(Object[] values) {
    requireSocketIO("socket/send");
    if (values.length != 2 || !(HaraBox.unwrap(values[0]) instanceof HaraSocket)) {
      throw new HaraException("socket/send expects a socket connection and bytes");
    }
    HaraSocket connection = (HaraSocket) HaraBox.unwrap(values[0]);
    byte[] contents = bytesValue(values[1], "socket/send").clone();
    try {
      connection.socket.getOutputStream().write(contents);
      connection.socket.getOutputStream().flush();
      return (long) contents.length;
    } catch (IOException error) {
      throw new HaraException("socket/send failed: " + error.getMessage());
    }
  }

  private Object socketClose(Object value) {
    requireSocketIO("socket/close");
    Object input = HaraBox.unwrap(value);
    if (!(input instanceof HaraSocket)) {
      throw new HaraException("socket/close expects a socket connection");
    }
    HaraSocket connection = (HaraSocket) input;
    try {
      connection.socket.close();
      return null;
    } catch (IOException error) {
      throw new HaraException("socket/close failed: " + error.getMessage());
    }
  }

  public Object invokeMarkerMethod(Object receiverValue, String method, Object[] arguments) {
    Object receiver = HaraBox.unwrap(receiverValue);
    if (receiver instanceof HaraArray) {
      return invokeArrayMethod((HaraArray) receiver, method, arguments);
    }
    if (receiver instanceof HaraObject) {
      return invokeObjectMethod((HaraObject) receiver, method, arguments);
    }
    NativeFlavorProvider provider = nativeProvider();
    if (provider == null) {
      throw new HaraException(
          "Dot calls are only supported on values created by array or object unless the namespace selects a native flavor");
    }
    return provider.invokeMember(receiver, method, arguments, nativeAccess());
  }

  private Object invokeArrayMethod(HaraArray array, String method, Object[] arguments) {
    switch (method) {
      case "get":
        requireMethodArity(method, arguments, 1);
        return array.get(arrayIndex(arguments[0], array.size(), false, method));
      case "set":
        requireMethodArity(method, arguments, 2);
        array.set(arrayIndex(arguments[0], array.size(), false, method), arguments[1]);
        return array;
      case "push-last":
        requireMethodArity(method, arguments, 1);
        array.add(arguments[0]);
        return array;
      case "pop-last":
        requireMethodArity(method, arguments, 0);
        if (array.isEmpty()) return null;
        return array.remove(array.size() - 1);
      case "push-first":
        requireMethodArity(method, arguments, 1);
        array.add(0, arguments[0]);
        return array;
      case "pop-first":
        requireMethodArity(method, arguments, 0);
        if (array.isEmpty()) return null;
        return array.remove(0);
      case "insert":
        requireMethodArity(method, arguments, 2);
        array.add(arrayIndex(arguments[0], array.size(), true, method), arguments[1]);
        return array;
      case "remove":
        requireMethodArity(method, arguments, 1);
        return array.remove(arrayIndex(arguments[0], array.size(), false, method));
      case "clone":
        requireMethodArity(method, arguments, 0);
        return new HaraArray(array.toArray());
      case "slice":
        {
          if (arguments.length < 1 || arguments.length > 2) {
            throw new HaraException("array.slice expects a start and optional end");
          }
          int start = arrayIndex(arguments[0], array.size(), true, method);
          int end =
              arguments.length == 2
                  ? arrayIndex(arguments[1], array.size(), true, method)
                  : array.size();
          if (end < start) throw new HaraException("array.slice range is out of bounds");
          return new HaraArray(array.subList(start, end).toArray());
        }
      case "map":
        {
          requireMethodArity(method, arguments, 1);
          HaraArray output = new HaraArray();
          for (Object value : array) output.add(invokeCallable(arguments[0], new Object[] {value}));
          return output;
        }
      case "filter":
        {
          requireMethodArity(method, arguments, 1);
          HaraArray output = new HaraArray();
          for (Object value : array) {
            if (truthy(invokeCallable(arguments[0], new Object[] {value}))) output.add(value);
          }
          return output;
        }
      case "fold-left":
      case "fold-right":
        requireMethodArity(method, arguments, 2);
        Object result = arguments[1];
        if ("fold-left".equals(method)) {
          for (Object value : array) {
            result = invokeCallable(arguments[0], new Object[] {result, value});
          }
        } else {
          for (int i = array.size() - 1; i >= 0; i--) {
            result = invokeCallable(arguments[0], new Object[] {array.get(i), result});
          }
        }
        return result;
      default:
        throw new HaraException("Unsupported array method: " + method);
    }
  }

  private Object invokeObjectMethod(HaraObject object, String method, Object[] arguments) {
    switch (method) {
      case "has?":
        requireMethodArity(method, arguments, 1);
        return object.containsKey(objectKey(arguments[0], method));
      case "get":
        if (arguments.length < 1 || arguments.length > 2) {
          throw new HaraException("object.get expects a key and optional fallback");
        }
        String key = objectKey(arguments[0], method);
        return object.containsKey(key)
            ? object.get(key)
            : arguments.length == 2 ? arguments[1] : null;
      case "set":
        requireMethodArity(method, arguments, 2);
        object.put(objectKey(arguments[0], method), arguments[1]);
        return object;
      case "delete":
        requireMethodArity(method, arguments, 1);
        return object.remove(objectKey(arguments[0], method));
      case "clone":
        requireMethodArity(method, arguments, 0);
        return new HaraObject(object);
      case "assign":
        requireMethodArity(method, arguments, 1);
        Object source = HaraBox.unwrap(arguments[0]);
        if (!(source instanceof HaraObject)) {
          throw new HaraException("object.assign expects an object marker");
        }
        object.putAll((HaraObject) source);
        return object;
      case "keys":
        requireMethodArity(method, arguments, 0);
        return new HaraArray(object.keySet().toArray());
      case "vals":
        requireMethodArity(method, arguments, 0);
        return new HaraArray(object.values().toArray());
      case "pairs":
        {
          requireMethodArity(method, arguments, 0);
          HaraArray pairs = new HaraArray();
          for (Map.Entry<String, Object> entry : object.entrySet()) {
            pairs.add(new HaraArray(new Object[] {entry.getKey(), entry.getValue()}));
          }
          return pairs;
        }
      default:
        throw new HaraException("Unsupported object method: " + method);
    }
  }

  private static void requireMethodArity(String method, Object[] arguments, int expected) {
    if (arguments.length != expected) {
      throw new HaraException(method + " expects " + expected + " arguments");
    }
  }

  private static int arrayIndex(Object value, int size, boolean allowEnd, String operation) {
    Object input = HaraBox.unwrap(value);
    if (!(input instanceof Number)) throw new HaraException(operation + " expects a numeric index");
    int index = ((Number) input).intValue();
    if (index < 0 || index > size || (!allowEnd && index == size)) {
      throw new HaraException(operation + " index out of bounds: " + index);
    }
    return index;
  }

  private static String objectKey(Object value, String operation) {
    Object input = HaraBox.unwrap(value);
    if (!(input instanceof String)) {
      throw new HaraException("object." + operation + " expects a string key");
    }
    return (String) input;
  }

  @TruffleBoundary
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

  @TruffleBoundary
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

  private void requireHalPath(String path, String operation) {
    String sourcePath = path.startsWith("classpath:") ? path.substring(10) : path;
    if (!sourcePath.endsWith(".hal") && !sourcePath.endsWith(".hrl")) {
      throw new HaraException(operation + " accepts only .hal or .hrl executable source files");
    }
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
    requireHalPath((String) value, "load-file");
    requireFileIO("load-file");
    ContextSnapshot snapshot = snapshot();
    try {
      String path = canonicalPath((String) value);
      Object result =
          parseAndExecute(
              new String(
                  environment.getPublicTruffleFile(path).readAllBytes(), StandardCharsets.UTF_8),
              path);
      registerModule(path);
      return result;
    } catch (IOException | RuntimeException error) {
      restore(snapshot);
      throw new HaraException(
          "Unable to load Hara file: " + value + " (" + error.getMessage() + ")");
    }
  }

  @TruffleBoundary
  private Object loadResource(Object value) {
    if (!(value instanceof String) || ((String) value).isEmpty()) {
      throw new HaraException("load-resource expects a non-empty resource name");
    }
    requireHalPath((String) value, "load-resource");
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

  @TruffleBoundary
  public Object requireModule(Object[] arguments) {
    if (arguments.length == 1 || arguments.length == 2) {
      Object requestedNamespace = unwrapQuoted(arguments[0]);
      if (requestedNamespace instanceof Symbol) {
        return requireNamespace((Symbol) requestedNamespace, arguments.length == 2 ? arguments[1] : null);
      }
    }
    if (arguments.length < 1 || arguments.length > 2 || !(arguments[0] instanceof String)) {
      throw new HaraException("require expects a path string or namespace symbol");
    }
    String callerNamespace = currentNamespace.name();
    try {
      String requested = (String) arguments[0];
      requireHalPath(requested, "require");
      boolean classpath = requested.startsWith("classpath:") || getResource(requested) != null;
      String resourceName =
          requested.startsWith("classpath:") ? requested.substring(10) : requested;
      String key = classpath ? "classpath:" + resourceName : canonicalPath(requested);
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
      initializeUserNamespace(currentNamespace);
      if (arguments.length == 2) {
        applyRequireOptions(arguments[1], modules.get(key));
      }
      return null;
    } finally {
      currentNamespace = namespace(callerNamespace);
      initializeUserNamespace(currentNamespace);
    }
  }

  @TruffleBoundary
  private Object requireNamespace(Symbol symbol, Object options) {
    if (symbol.getNamespace() != null) {
      throw new HaraException("require expects an unqualified namespace symbol");
    }
    String target = symbol.display();
    HaraNamespace required = requiredNamespace(target);
    if (required == null) {
      throw new HaraException("Cannot require missing namespace: " + target);
    }
    if (options != null) applyNamespaceOptions(target, required, options);
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void applyNamespaceOptions(String target, HaraNamespace required, Object options) {
    if (!(options instanceof IMapType<?, ?> map)) throw new HaraException("require options expect a map");
    Object alias = unwrapQuoted(((IMapType) map).lookup(Keyword.create("as")));
    if (alias != null) {
      if (!(alias instanceof Symbol) || ((Symbol) alias).getNamespace() != null) {
        throw new HaraException("require :as expects an unqualified symbol");
      }
      defineAlias((Symbol) alias, Symbol.create(target));
    }
    Object refer = unwrapQuoted(((IMapType) map).lookup(Keyword.create("refer")));
    if (refer != null) {
      java.util.List<Object> symbols = new ArrayList<>();
      if (refer instanceof Keyword keyword && "all".equals(keyword.getName())) {
        symbols.addAll(required.symbolNames());
      } else if (refer instanceof ILinearType<?>) {
        for (Object value : (ILinearType<?>) refer) symbols.add(value);
      } else {
        throw new HaraException("require :refer expects a sequential collection of symbols or :all");
      }
      for (Object value : symbols) {
        if (value instanceof String) value = Symbol.create((String) value);
        if (!(value instanceof Symbol) || ((Symbol) value).getNamespace() != null) {
          throw new HaraException("require :refer expects unqualified symbols");
        }
        String name = ((Symbol) value).getName();
        HaraVar variable = required.lookup(name);
        if (variable == null) throw new HaraException("Cannot refer missing var " + name + " from " + target);
        currentNamespace.refer(name, variable);
        HaraMacro macro = macros.getOrDefault(target, Map.of()).get(name);
        if (macro != null) macros.computeIfAbsent(currentNamespace.name(), ignored -> new ConcurrentHashMap<>()).put(name, macro);
      }
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

  @TruffleBoundary
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
                ? canonicalPath(requested)
                : "classpath:" + requested);
    ModuleRecord module = modules.get(key);
    return module == null ? 0L : module.revision;
  }

  @TruffleBoundary
  private Object moduleDependencies(Object value) {
    if (!(value instanceof String)) {
      throw new HaraException("module-dependencies expects a path string");
    }
    String requested = (String) value;
    String key =
        requested.startsWith("classpath:")
            ? requested
            : (getResource(requested) == null
                ? canonicalPath(requested)
                : "classpath:" + requested);
    Set<String> dependencies = moduleDependencies.getOrDefault(key, Set.of());
    return BuiltinStruct.vector(new LinkedHashSet<>(dependencies).toArray());
  }

  @TruffleBoundary
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

  @TruffleBoundary
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

  @TruffleBoundary
  private Object seqValue(Object[] values) {
    if (values.length != 1 && values.length != 2) {
      throw new HaraException("seq expects a source, or a transform and source");
    }
    Object source = values.length == 1 ? values[0] : values[1];
    Object lazySource =
        HaraBox.unwrap(source) instanceof HaraSeq
            ? HaraBox.unwrap(source)
            : new HaraSeq((Iterator<?>) snapshotOrIterator(source));
    if (values.length == 1) return lazySource;
    Object result = invokeCallable(values[0], new Object[] {lazySource});
    Object unwrapped = HaraBox.unwrap(result);
    return unwrapped instanceof HaraSeq
        ? unwrapped
        : new HaraSeq((Iterator<?>) iterValue(unwrapped));
  }

  @TruffleBoundary
  private Object isSeq(Object value) {
    return HaraBox.unwrap(value) instanceof HaraSeq;
  }

  @TruffleBoundary
  private Object isIterator(Object value) {
    return HaraBox.unwrap(value) instanceof Iterator<?>;
  }

  private Object snapshotOrIterator(Object value) {
    Object target = HaraBox.unwrap(value);
    if (target instanceof HaraSeq) return target;
    if (target instanceof Iterator<?>) return target;
    if (target instanceof HaraArray) return Iter.objects(((HaraArray) target).toArray());
    return iterValue(target);
  }

  @TruffleBoundary
  private Object iterHasNext(Object value) {
    Iterator<?> iterator = requireIterator(value, "iter-has?");
    return iterator.hasNext();
  }

  @TruffleBoundary
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
    return Iter.concat(Iter.map((Iterator) Iter.objects(values), value -> Iter.iter(value)));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterMap(Object[] values) {
    if (values.length < 2) {
      throw new HaraException("iter-map expects a function and at least one source");
    }
    Object function = values[0];
    Object[] sourceValues = java.util.Arrays.copyOfRange(values, 1, values.length);
    Iterator zipped = iterZipArrays(sourceValues);
    return closeable(Iter.map(zipped, value -> invokeCallable(function, (Object[]) value)), zipped);
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
  private Object iterInterpose(Object[] values) {
    requireIteratorArity(values, 2, "iter-interpose");
    Object separator = values[0];
    Iterator source = (Iterator) iterValue(values[1]);
    return closeable(
        new CloseableIterator<Object>() {
          private boolean first = true;
          private boolean ready;
          private boolean emitSeparator;
          private boolean done;
          private Object next;

          private void prime() {
            if (done || ready) return;
            if (emitSeparator) {
              if (!source.hasNext()) {
                done = true;
                Iter.close(source);
                return;
              }
              next = separator;
              emitSeparator = false;
              ready = true;
              return;
            }
            if (!source.hasNext()) {
              done = true;
              Iter.close(source);
              return;
            }
            next = source.next();
            emitSeparator = source.hasNext();
            first = false;
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object iterInterleave(Object[] values) {
    if (values.length == 0) {
      throw new HaraException("iter-interleave expects at least one source");
    }
    Iterator[] sources = new Iterator[values.length];
    for (int i = 0; i < values.length; i++) sources[i] = (Iterator) iterValue(values[i]);
    return new CloseableIterator<Object>() {
      private int index;
      private boolean closed;

      @Override
      public boolean hasNext() {
        if (closed) return false;
        for (Iterator source : sources) {
          if (!source.hasNext()) {
            close();
            return false;
          }
        }
        return true;
      }

      @Override
      public Object next() {
        if (!hasNext()) throw new NoSuchElementException();
        Object result = sources[index].next();
        index = (index + 1) % sources.length;
        return result;
      }

      @Override
      public void close() {
        if (closed) return;
        closed = true;
        for (Iterator source : sources) Iter.close(source);
      }
    };
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
    CloseableIterator<Object[]> zipped = iterZipArrays(values);
    return closeable(Iter.map(zipped, BuiltinStruct::vector), zipped);
  }

  private CloseableIterator<Object[]> iterZipArrays(Object[] values) {
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

  @TruffleBoundary
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

  @TruffleBoundary
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

  @TruffleBoundary
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

  private String canonicalPath(String value) {
    return environment.getPublicTruffleFile(value).getAbsoluteFile().normalize().getPath();
  }

  private void registerModule(String path) {
    String key = path;
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

  @TruffleBoundary
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

  @TruffleBoundary
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

  @TruffleBoundary
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

  private static final class HaraArray extends ArrayList<Object> implements ICount, INth<Object> {
    private HaraArray() {}

    private HaraArray(Object[] values) {
      super(java.util.Arrays.asList(values));
    }

    @Override
    public long count() {
      return size();
    }

    @Override
    public Object nth(long index) {
      if (index < 0 || index >= size()) {
        throw new HaraException("nth index out of bounds: " + index);
      }
      return get((int) index);
    }
  }

  private static final class HaraSeq implements CloseableIterator<Object> {
    private final Iterator<?> source;
    private boolean closed;

    private HaraSeq(Iterator<?> source) {
      this.source = source;
    }

    @Override
    public boolean hasNext() {
      return !closed && source.hasNext();
    }

    @Override
    public Object next() {
      if (!hasNext()) throw new NoSuchElementException();
      return source.next();
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      Iter.close(source);
    }

    @Override
    public String toString() {
      return "#<seq>";
    }
  }

  private static final class HaraObject extends LinkedHashMap<String, Object> {
    private HaraObject(Object[] values) {
      if ((values.length & 1) != 0) {
        throw new HaraException("object expects an even number of string key/value arguments");
      }
      for (int i = 0; i < values.length; i += 2) {
        put(objectKey(values[i], "constructor"), values[i + 1]);
      }
    }

    private HaraObject(HaraObject source) {
      super(source);
    }
  }

  private static final class HaraPromise implements IDeref<Object>, IDerefTimeout<Object> {
    private final CompletableFuture<Object> future;

    private HaraPromise(CompletableFuture<Object> future) {
      this.future = future;
    }

    private Object state() {
      if (future.isCancelled()) return Keyword.create("cancelled");
      if (!future.isDone()) return Keyword.create("pending");
      return future.isCompletedExceptionally()
          ? Keyword.create("rejected")
          : Keyword.create("fulfilled");
    }

    private Object value() {
      if (!future.isDone()) throw new HaraException("promise/value: promise is pending");
      return deref();
    }

    private Object cancel() {
      future.cancel(false);
      return this;
    }

    @Override
    public Object deref() {
      try {
        return future.join();
      } catch (CompletionException error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof HaraException) throw (HaraException) cause;
        throw new HaraException("Promise rejected: " + cause.getMessage());
      } catch (java.util.concurrent.CancellationException error) {
        throw new HaraException("Promise cancelled");
      }
    }

    @Override
    public Object derefTimeout(long milliseconds, Object timeoutValue) {
      try {
        return future.get(milliseconds, TimeUnit.MILLISECONDS);
      } catch (java.util.concurrent.TimeoutException error) {
        return timeoutValue;
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new HaraException("Promise wait interrupted");
      } catch (java.util.concurrent.ExecutionException error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof HaraException) throw (HaraException) cause;
        throw new HaraException("Promise rejected: " + cause.getMessage());
      } catch (java.util.concurrent.CancellationException error) {
        throw new HaraException("Promise cancelled");
      }
    }

    @Override
    public String toString() {
      return future.isDone() ? "#<promise realized>" : "#<promise pending>";
    }
  }

  private static final class HaraSocket {
    private final Socket socket;

    private HaraSocket(Socket socket) {
      this.socket = socket;
    }

    @Override
    public String toString() {
      return "#<socket " + socket.getRemoteSocketAddress() + ">";
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
    public Supplier<Object> getArg0() {
      return () -> implementation.apply(new Object[0]);
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

    private java.util.List<String> symbolNames() {
      return new java.util.ArrayList<>(vars.keySet());
    }

    @TruffleBoundary
    private HaraVar define(String symbolName, Object value) {
      return define(symbolName, value, null);
    }

    @TruffleBoundary
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
